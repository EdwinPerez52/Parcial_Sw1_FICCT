/**
 * Runner — executes a closed set of Flutter/ADB commands as child processes.
 * Streams stdout/stderr via callback for SSE delivery.
 * NEVER executes arbitrary commands from the network.
 */
import { spawn, ChildProcess } from 'child_process';
import * as path from 'path';

export type RunnerEvent =
  | { type: 'stdout'; data: string }
  | { type: 'stderr'; data: string }
  | { type: 'exit'; code: number | null }
  | { type: 'error'; message: string };

/** Allowed commands — closed set, no arbitrary execution. */
const ALLOWED_COMMANDS: Record<string, string[][]> = {
  'flutter-pub-get': [['flutter', 'pub', 'get']],
  'flutter-analyze': [['flutter', 'analyze', '--no-fatal-infos']],
  'flutter-run': [['flutter', 'run']],  // device appended dynamically
  'flutter-build-apk': [['flutter', 'build', 'apk', '--release']],
  'adb-reverse': [['adb', 'reverse', 'tcp:8080', 'tcp:8080']],
  'adb-devices': [['adb', 'devices', '-l']],
};

export interface RunOptions {
  command: string;
  projectDir: string;
  deviceId?: string;
  apiBaseUrl?: string;
  onEvent: (event: RunnerEvent) => void;
}

/**
 * Executes one of the allowed commands in the given project directory.
 * Returns a promise that resolves when the process exits.
 */
export function runCommand(options: RunOptions): { process: ChildProcess; promise: Promise<number | null> } {
  const { command, projectDir, deviceId, apiBaseUrl, onEvent } = options;

  const template = ALLOWED_COMMANDS[command];
  if (!template) {
    onEvent({ type: 'error', message: `Comando no permitido: ${command}` });
    return {
      process: null as unknown as ChildProcess,
      promise: Promise.resolve(1),
    };
  }

  // Build actual command args
  const args = [...template[0]];

  // For flutter-run, append device and dart-define
  if (command === 'flutter-run' && deviceId) {
    args.push('-d', deviceId);
  }
  if ((command === 'flutter-run' || command === 'flutter-build-apk') && apiBaseUrl) {
    args.push(`--dart-define=API_BASE_URL=${apiBaseUrl}`);
  } else if (command === 'flutter-run' || command === 'flutter-build-apk') {
    args.push('--dart-define=API_BASE_URL=http://localhost:8080');
  }

  const executable = args[0];
  const spawnArgs = args.slice(1);

  onEvent({ type: 'stdout', data: `> ${args.join(' ')}\n` });

  const child = spawn(executable, spawnArgs, {
    cwd: projectDir,
    shell: true,
    env: { ...process.env },
    stdio: ['pipe', 'pipe', 'pipe'],
  });

  const promise = new Promise<number | null>((resolve) => {
    child.stdout?.on('data', (chunk: Buffer) => {
      onEvent({ type: 'stdout', data: chunk.toString('utf8') });
    });

    child.stderr?.on('data', (chunk: Buffer) => {
      onEvent({ type: 'stderr', data: chunk.toString('utf8') });
    });

    child.on('error', (err) => {
      onEvent({ type: 'error', message: err.message });
      resolve(1);
    });

    child.on('close', (code) => {
      onEvent({ type: 'exit', code });
      resolve(code);
    });
  });

  return { process: child, promise };
}

/**
 * Runs `adb reverse tcp:8080 tcp:8080` so that a USB-connected device
 * can reach the backend at localhost:8080 via the host machine.
 */
export async function setupAdbReverse(onEvent: (event: RunnerEvent) => void): Promise<boolean> {
  const { promise } = runCommand({
    command: 'adb-reverse',
    projectDir: '.',
    onEvent,
  });
  const code = await promise;
  return code === 0;
}
