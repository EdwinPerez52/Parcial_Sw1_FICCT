// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { SpeechSession, SpeechStatus } from './speech';

class FakeRecognition {
  lang = ''; interimResults = true; continuous = true;
  onresult: ((event: any) => void) | null = null;
  onerror: ((event: any) => void) | null = null;
  onend: (() => void) | null = null;
  start() { /* browser starts recording */ }
  stop() { /* browser stops recording */ }
}

describe('speech input', () => {
  it('passes the exact transcript to the text command callback', async () => {
    let instance: FakeRecognition | undefined;
    (window as any).SpeechRecognition = class extends FakeRecognition { constructor() { super(); instance = this; } };
    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    const session = new SpeechSession(value => statuses.push(value), submit, vi.fn());
    await session.start();
    expect(instance!.lang).toBe('es-ES');
    instance!.onresult!({ results: { 0: { 0: { transcript: 'crea una clase Producto' } } } });
    expect(submit).toHaveBeenCalledExactlyOnceWith('crea una clase Producto');
    expect(statuses.map(value => value.phase)).toContain('recording');
    expect(statuses.at(-1)?.phase).toBe('transcribing');
  });

  it('reports permission errors without submitting', async () => {
    (window as any).SpeechRecognition = FakeRecognition;
    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    const session = new SpeechSession(value => statuses.push(value), submit, vi.fn());
    await session.start();
    // The implementation supports the browser's explicit denial event as well as Permissions API denial.
    const instance = (session as any).recognition as FakeRecognition;
    instance.onerror!({ error: 'not-allowed' });
    expect(statuses.at(-1)?.phase).toBe('error'); expect(submit).not.toHaveBeenCalled();
  });

  it('records audio, transcribes it and submits the resulting text once', async () => {
    const track = { stop: vi.fn() };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) } });
    class FakeRecorder {
      static isTypeSupported = (mime: string) => mime.startsWith('audio/webm');
      state = 'inactive'; ondataavailable: ((event: { data: Blob }) => void) | null = null;
      onstop: (() => void) | null = null; onerror: (() => void) | null = null;
      constructor(_stream: MediaStream, _options: MediaRecorderOptions) {}
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['audio']) }); this.onstop?.(); }
    }
    (globalThis as any).MediaRecorder = FakeRecorder;
    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    const transcribe = vi.fn().mockResolvedValue('crea una clase Producto');
    const session = new SpeechSession(value => statuses.push(value), submit, transcribe);
    await session.start(); session.finish();
    await vi.waitFor(() => expect(submit).toHaveBeenCalledExactlyOnceWith('crea una clase Producto'));
    expect(transcribe).toHaveBeenCalledOnce();
    expect(track.stop).toHaveBeenCalled();
    expect(statuses.map(value => value.phase)).toContain('transcribing');
    session.stop();
    delete (globalThis as any).MediaRecorder;
    delete (navigator as any).mediaDevices;
  });

  it('falls back to browser recognition when server returns 503', async () => {
    const track = { stop: vi.fn() };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) } });
    class FakeRecorder {
      static isTypeSupported = () => true;
      state = 'inactive'; ondataavailable: any = null; onstop: any = null;
      constructor() {}
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['audio']) }); this.onstop?.(); }
    }
    (globalThis as any).MediaRecorder = FakeRecorder;
    (window as any).SpeechRecognition = class extends FakeRecognition { constructor() { super(); (window as any).recognitionInstance = this; } };

    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    const transcribe = vi.fn().mockRejectedValue({ status: 503 });
    const session = new SpeechSession(value => statuses.push(value), submit, transcribe);
    await session.start();
    session.finish(); // this stops the recorder

    await vi.waitFor(() => expect(transcribe).toHaveBeenCalled());
    // After 503, the session shows a fallback message then starts browser recognition
    await vi.waitFor(() => expect(statuses.some(s => s.message?.includes('navegador'))).toBe(true));
    // Browser recognition sets phase to 'recording' with 'Escuchando…'
    expect(statuses.at(-1)?.phase).toBe('recording');
    expect(statuses.at(-1)?.message).toContain('Repite la instrucción');

    delete (globalThis as any).MediaRecorder;
    delete (navigator as any).mediaDevices;
  });

  it('falls back to browser recognition when the API gateway returns 502', async () => {
    const track = { stop: vi.fn() };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) } });
    class FakeRecorder {
      static isTypeSupported = () => true;
      state = 'inactive'; ondataavailable: any = null; onstop: any = null;
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['audio']) }); this.onstop?.(); }
    }
    (globalThis as any).MediaRecorder = FakeRecorder;
    (window as any).SpeechRecognition = FakeRecognition;
    const statuses: SpeechStatus[] = [];
    const session = new SpeechSession(value => statuses.push(value), vi.fn(), vi.fn().mockRejectedValue({ status: 502 }));

    await session.start(); session.finish();

    await vi.waitFor(() => expect(statuses.at(-1)?.phase).toBe('recording'));
    expect(statuses.at(-1)?.message).toContain('Repite la instrucción');
    session.stop();
    delete (globalThis as any).MediaRecorder;
    delete (navigator as any).mediaDevices;
  });

  it('shows error when transcription fails with non-503 status', async () => {
    const track = { stop: vi.fn() };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) } });
    class FakeRecorder {
      static isTypeSupported = () => true;
      state = 'inactive'; ondataavailable: any = null; onstop: any = null;
      constructor() {}
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['audio']) }); this.onstop?.(); }
    }
    (globalThis as any).MediaRecorder = FakeRecorder;

    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    const transcribe = vi.fn().mockRejectedValue(new Error('Network error'));
    const session = new SpeechSession(value => statuses.push(value), submit, transcribe);
    await session.start();
    session.finish(); // stop recorder

    await vi.waitFor(() => expect(transcribe).toHaveBeenCalled());
    await vi.waitFor(() => expect(statuses.at(-1)?.phase).toBe('error'));
    expect(statuses.at(-1)?.message).toBe('Network error');

    delete (globalThis as any).MediaRecorder;
    delete (navigator as any).mediaDevices;
  });

  it('shows retry message when server returns empty transcript', async () => {
    const track = { stop: vi.fn() };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) } });
    class FakeRecorder {
      static isTypeSupported = () => true;
      state = 'inactive'; ondataavailable: any = null; onstop: any = null;
      constructor() {}
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['audio']) }); this.onstop?.(); }
    }
    (globalThis as any).MediaRecorder = FakeRecorder;

    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    const transcribe = vi.fn().mockResolvedValue('   ');
    const session = new SpeechSession(value => statuses.push(value), submit, transcribe);
    await session.start();
    session.finish();

    await vi.waitFor(() => expect(transcribe).toHaveBeenCalled());
    await vi.waitFor(() => expect(statuses.at(-1)?.phase).toBe('error'));
    expect(statuses.at(-1)?.message).toMatch(/Reintenta/);

    delete (globalThis as any).MediaRecorder;
    delete (navigator as any).mediaDevices;
  });

  it('prevents delivery if stopped during transcription', async () => {
    const track = { stop: vi.fn() };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) } });
    class FakeRecorder {
      static isTypeSupported = () => true;
      state = 'inactive'; ondataavailable: any = null; onstop: any = null;
      constructor() {}
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['audio']) }); this.onstop?.(); }
    }
    (globalThis as any).MediaRecorder = FakeRecorder;

    const statuses: SpeechStatus[] = []; const submit = vi.fn();
    let resolveTranscribe!: (val: string) => void;
    const transcribe = vi.fn().mockReturnValue(new Promise(resolve => { resolveTranscribe = resolve; }));
    const session = new SpeechSession(value => statuses.push(value), submit, transcribe);
    await session.start();
    session.finish();

    await vi.waitFor(() => expect(transcribe).toHaveBeenCalled());
    session.stop();
    resolveTranscribe('crea una clase Producto');
    
    await new Promise(r => setTimeout(r, 10));
    expect(submit).not.toHaveBeenCalled();

    delete (globalThis as any).MediaRecorder;
    delete (navigator as any).mediaDevices;
  });
});
