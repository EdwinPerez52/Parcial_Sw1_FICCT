export type SpeechPhase = 'idle' | 'permission' | 'recording' | 'transcribing' | 'error';
export interface SpeechStatus { phase: SpeechPhase; message?: string }

interface RecognitionEvent extends Event { results: { 0: { 0: { transcript: string } } } }
interface RecognitionError extends Event { error: string }
interface Recognition {
  lang: string; interimResults: boolean; continuous: boolean;
  onresult: ((event: RecognitionEvent) => void) | null;
  onerror: ((event: RecognitionError) => void) | null;
  onend: (() => void) | null;
  start(): void; stop(): void;
}
type RecognitionConstructor = new () => Recognition;

export class SpeechSession {
  private recognition?: Recognition;
  private recorder?: MediaRecorder;
  private stream?: MediaStream;
  private timer?: ReturnType<typeof setTimeout>;
  private completed = false;
  constructor(private readonly status: (status: SpeechStatus) => void,
    private readonly transcript: (text: string) => void,
    private readonly transcribeAudio: (audio: Blob) => Promise<string>,
    private readonly preferBrowser: boolean = false) {}

  async start(): Promise<void> {
    this.stop(); this.completed = false;
    this.status({ phase: 'permission', message: 'Solicitando permiso de micrófono…' });
    const browserWindow = window as Window & { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor };
    if (this.preferBrowser && (browserWindow.SpeechRecognition || browserWindow.webkitSpeechRecognition)) {
      this.startBrowserRecognition();
      return;
    }
    if (navigator.mediaDevices?.getUserMedia && typeof MediaRecorder !== 'undefined') {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        this.stream = stream;
        const mime = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4']
          .find(value => MediaRecorder.isTypeSupported(value));
        if (!mime) { stream.getTracks().forEach(track => track.stop()); this.stream = undefined; this.startBrowserRecognition(); return; }
        const recorder = new MediaRecorder(stream, { mimeType: mime });
        this.recorder = recorder; const chunks: Blob[] = [];
        recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
        recorder.onerror = () => this.fail('No se pudo grabar el audio. Reintenta.');
        recorder.onstop = () => {
          this.releaseStream();
          if (this.completed) return;
          const audio = new Blob(chunks, { type: mime.split(';')[0] });
          if (!audio.size) { this.fail('No se grabó audio. Reintenta.'); return; }
          this.status({ phase: 'transcribing', message: 'Transcribiendo audio…' });
          void this.transcribeAudio(audio).then(text => {
            if (this.completed) return;
            if (!text.trim()) { this.fail('No se detectó una instrucción. Reintenta.'); return; }
            this.completed = true; this.transcript(text.trim());
          }).catch(cause => {
            if (this.completed) return;
            const browser = window as Window & { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor };
            if (!this.completed && (cause as { status?: number }).status === 503 && (browser.SpeechRecognition || browser.webkitSpeechRecognition)) {
              this.status({ phase: 'permission', message: 'La transcripción del servidor no está disponible; repite la instrucción en el navegador…' });
              this.startBrowserRecognition(true);
            } else this.fail(cause instanceof Error ? cause.message : 'No se pudo transcribir el audio.');
          });
        };
        recorder.start();
        this.status({ phase: 'recording', message: 'Grabando. Habla y pulsa «Transcribir» al terminar.' });
        this.timer = setTimeout(() => this.finish(), 15_000);
        return;
      } catch (cause) {
        if (this.stream) this.releaseStream();
        if (cause instanceof DOMException && cause.name === 'NotAllowedError') { this.fail('Permiso de micrófono denegado. Actívalo en el navegador y reintenta.'); return; }
      }
    }
    this.startBrowserRecognition();
  }

  finish(): void {
    if (this.timer) clearTimeout(this.timer);
    if (this.recorder?.state === 'recording') this.recorder.stop();
    else if (this.recognition) this.recognition.stop();
  }

  stop(): void {
    this.completed = true;
    if (this.timer) clearTimeout(this.timer);
    const recognition = this.recognition; this.recognition = undefined;
    if (recognition) { recognition.onend = null; recognition.onerror = null; recognition.onresult = null; recognition.stop(); }
    if (this.recorder) { this.recorder.onstop = null; if (this.recorder.state === 'recording') this.recorder.stop(); this.recorder = undefined; }
    this.releaseStream();
  }

  private startBrowserRecognition(repeatInstruction = false): void {
    const browserWindow = window as Window & { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor };
    const Constructor = browserWindow.SpeechRecognition ?? browserWindow.webkitSpeechRecognition;
    if (!Constructor) { this.fail('No hay grabación ni reconocimiento de voz disponible en este navegador.'); return; }
    try {
      const recognition = new Constructor(); this.recognition = recognition;
      recognition.lang = 'es-ES'; recognition.interimResults = false; recognition.continuous = false;
      let lastTranscript = '';
      recognition.onresult = event => {
        const value = event.results?.[0]?.[0]?.transcript?.trim();
        if (value) {
          lastTranscript = value;
          this.completed = true;
          this.status({ phase: 'transcribing', message: 'Transcripción recibida; validando…' });
          this.transcript(value);
        }
      };
      recognition.onerror = event => {
        if (lastTranscript) return;
        this.fail(event.error === 'not-allowed' ? 'Permiso de micrófono denegado.' : `No se pudo reconocer la voz: ${event.error}`);
      };
      recognition.onend = () => {
        if (!this.completed && !lastTranscript) this.fail('No se recibió ninguna transcripción. Reintenta.');
      };
      recognition.start(); this.status({ phase: 'recording', message: repeatInstruction
        ? 'Repite la instrucción: escuchando con el reconocimiento del navegador…' : 'Escuchando…' });
    } catch (cause) { this.fail(cause instanceof Error ? cause.message : 'No se pudo iniciar el reconocimiento.'); }
  }

  private releaseStream(): void { this.stream?.getTracks().forEach(track => track.stop()); this.stream = undefined; }
  private fail(message: string): void { this.stop(); this.status({ phase: 'error', message }); }
}
