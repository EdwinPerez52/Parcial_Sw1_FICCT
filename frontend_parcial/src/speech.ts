interface SpeechRecognitionResultEvent extends Event {
  results: { 0: { 0: { transcript: string } } };
}

interface SpeechRecognitionErrorEvent extends Event { error: string; }

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((event: SpeechRecognitionResultEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  start(): void;
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

export function dictate(onText: (text: string) => void, onError: (message: string) => void): void {
  const browserWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  };
  const Constructor = browserWindow.SpeechRecognition ?? browserWindow.webkitSpeechRecognition;
  if (!Constructor) {
    onError('El navegador no ofrece reconocimiento de voz. Puedes escribir el mismo comando.');
    return;
  }
  const recognition = new Constructor();
  recognition.lang = 'es-BO';
  recognition.interimResults = false;
  recognition.continuous = false;
  recognition.onresult = (event) => onText(event.results[0][0].transcript);
  recognition.onerror = (event) => onError(`No se pudo reconocer la voz: ${event.error}`);
  recognition.start();
}
