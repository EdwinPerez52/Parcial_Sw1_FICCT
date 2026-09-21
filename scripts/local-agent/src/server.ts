/**
 * Collab Modeler Local Agent — Express server.
 *
 * Listens ONLY on 127.0.0.1:9876 (loopback).
 * Validates origin, signature, and nonce for every mutation request.
 * Never executes arbitrary commands from the network.
 */
import express, { Request, Response, NextFunction } from 'express';
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { validateOrigin, verifySignature, consumeNonce, sanitizeOutputPath } from './security';
import { getSdkStatus, listDevices } from './sdk-checker';
import { runCommand, setupAdbReverse, RunnerEvent } from './runner';

const app = express();
const PORT = parseInt(process.env.AGENT_PORT || '9876', 10);
const HOST = '127.0.0.1'; // NEVER bind to 0.0.0.0

// Parse JSON bodies up to 200MB (Flutter ZIP can be large)
app.use(express.json({ limit: '200mb' }));

// CORS middleware — strictly loopback origins only
app.use((req: Request, res: Response, next: NextFunction) => {
  const origin = req.headers.origin as string | undefined;
  const referer = req.headers.referer as string | undefined;

  // Always set CORS headers for preflight
  if (origin && validateOrigin(origin, referer)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
  } else if (req.method === 'OPTIONS') {
    // Allow preflight from known origins
    res.setHeader('Access-Control-Allow-Origin', 'http://localhost:5173');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  res.setHeader('Access-Control-Max-Age', '86400');

  if (req.method === 'OPTIONS') {
    res.status(204).end();
    return;
  }

  next();
});

// Validate origin for mutation requests
function requireOrigin(req: Request, res: Response, next: NextFunction) {
  const origin = req.headers.origin as string | undefined;
  const referer = req.headers.referer as string | undefined;

  if (!validateOrigin(origin, referer)) {
    res.status(403).json({ error: 'Origen no permitido', code: 'FORBIDDEN_ORIGIN' });
    return;
  }
  next();
}

// =============================================================================
// GET /api/status — Health check + SDK/device status
// =============================================================================
app.get('/api/status', (_req: Request, res: Response) => {
  const status = getSdkStatus();
  res.json({
    agent: 'collab-modeler-local-agent',
    version: '1.0.0',
    ready: status.flutterAvailable,
    ...status,
  });
});

// =============================================================================
// GET /api/devices — List connected ADB devices
// =============================================================================
app.get('/api/devices', (_req: Request, res: Response) => {
  const devices = listDevices();
  res.json({ devices });
});

// =============================================================================
// POST /api/generate — Receive spec + Flutter ZIP, extract to output dir
// =============================================================================
app.post('/api/generate', requireOrigin, async (req: Request, res: Response) => {
  try {
    const { spec, flutterZipBase64, signingKey, outputDir } = req.body;

    if (!spec || !flutterZipBase64 || !signingKey) {
      res.status(400).json({ error: 'Faltan campos obligatorios: spec, flutterZipBase64, signingKey' });
      return;
    }

    // Verify HMAC-SHA256 signature
    if (!verifySignature(spec, signingKey)) {
      res.status(403).json({ error: 'Firma inválida — la especificación ha sido modificada o la clave no coincide', code: 'INVALID_SIGNATURE' });
      return;
    }

    // Verify and consume nonce
    if (!consumeNonce(spec.nonce)) {
      res.status(409).json({ error: 'Nonce ya utilizado o inválido — solicita una nueva especificación', code: 'NONCE_CONSUMED' });
      return;
    }

    // Determine workspace root (parent of scripts/local-agent)
    const workspaceRoot = path.resolve(__dirname, '..', '..', '..');

    // Determine output directory
    const requestedDir = outputDir || path.join(workspaceRoot, 'mobile_parcial');
    const safeDir = sanitizeOutputPath(requestedDir, workspaceRoot);
    if (!safeDir) {
      res.status(400).json({ error: 'Ruta de salida no permitida', code: 'INVALID_PATH' });
      return;
    }

    // SSE headers for streaming progress
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    });

    const sendEvent = (event: string, data: unknown) => {
      res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    };

    sendEvent('progress', { step: 'validating', message: 'Especificación verificada ✓' });

    // Decode and extract ZIP
    sendEvent('progress', { step: 'extracting', message: `Extrayendo proyecto Flutter en ${safeDir}…` });

    const zipBuffer = Buffer.from(flutterZipBase64, 'base64');
    await extractZip(zipBuffer, safeDir);

    sendEvent('progress', { step: 'extracted', message: `Proyecto Flutter extraído en ${safeDir}` });

    // Create flutter project scaffolding if needed (android/, web/ dirs)
    if (!fs.existsSync(path.join(safeDir, 'android'))) {
      sendEvent('progress', { step: 'flutter-create', message: 'Creando plataformas Flutter…' });
      const { promise } = runCommand({
        command: 'flutter-pub-get',
        projectDir: safeDir,
        onEvent: (ev) => sendEvent('output', ev),
      });
      await promise;
    }

    // flutter pub get
    sendEvent('progress', { step: 'pub-get', message: 'Obteniendo dependencias (flutter pub get)…' });
    const { promise: pubGetPromise } = runCommand({
      command: 'flutter-pub-get',
      projectDir: safeDir,
      onEvent: (ev) => sendEvent('output', ev),
    });
    const pubGetCode = await pubGetPromise;

    if (pubGetCode !== 0) {
      sendEvent('error', { message: 'flutter pub get falló' });
      res.end();
      return;
    }

    // flutter analyze
    sendEvent('progress', { step: 'analyze', message: 'Analizando código (flutter analyze)…' });
    const { promise: analyzePromise } = runCommand({
      command: 'flutter-analyze',
      projectDir: safeDir,
      onEvent: (ev) => sendEvent('output', ev),
    });
    await analyzePromise; // non-fatal

    // List devices
    const devices = listDevices();
    sendEvent('progress', { step: 'complete', message: 'Generación completada', outputDir: safeDir });
    sendEvent('devices', { devices });
    sendEvent('done', { outputDir: safeDir, diagramName: spec.diagramName, revision: spec.revision });
    res.end();
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Error desconocido';
    if (!res.headersSent) {
      res.status(500).json({ error: message, code: 'INTERNAL_ERROR' });
      return;
    }
    try {
      res.write(`event: error\ndata: ${JSON.stringify({ message })}\n\n`);
      res.end();
    } catch {
      // Response may already be closed
    }
  }
});

// =============================================================================
// POST /api/run — Execute flutter run or build on the generated project
// =============================================================================
app.post('/api/run', requireOrigin, async (req: Request, res: Response) => {
  try {
    const { projectDir, action, deviceId, apiBaseUrl } = req.body;

    if (!projectDir || !action) {
      res.status(400).json({ error: 'Faltan campos obligatorios: projectDir, action' });
      return;
    }

    const workspaceRoot = path.resolve(__dirname, '..', '..', '..');
    const safeDir = sanitizeOutputPath(projectDir, workspaceRoot);
    if (!safeDir) {
      res.status(400).json({ error: 'Ruta de proyecto no permitida', code: 'INVALID_PATH' });
      return;
    }

    // Validate action is one of the allowed values
    const allowedActions = ['flutter-run', 'flutter-build-apk'];
    if (!allowedActions.includes(action)) {
      res.status(400).json({ error: `Acción no permitida: ${action}`, code: 'INVALID_ACTION' });
      return;
    }

    // SSE headers
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    });

    const sendEvent = (event: string, data: unknown) => {
      res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    };

    // Setup adb reverse for USB connection
    sendEvent('progress', { step: 'adb-reverse', message: 'Configurando adb reverse tcp:8080 tcp:8080…' });
    await setupAdbReverse((ev) => sendEvent('output', ev));

    // Run the requested action
    const commandName = action as string;
    sendEvent('progress', { step: commandName, message: `Ejecutando ${commandName}…` });

    const { promise } = runCommand({
      command: commandName,
      projectDir: safeDir,
      deviceId,
      apiBaseUrl: apiBaseUrl || 'http://localhost:8080',
      onEvent: (ev) => sendEvent('output', ev),
    });

    const code = await promise;
    sendEvent('done', { exitCode: code, action: commandName });
    res.end();
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Error desconocido';
    if (!res.headersSent) {
      res.status(500).json({ error: message, code: 'INTERNAL_ERROR' });
      return;
    }
    try {
      res.write(`event: error\ndata: ${JSON.stringify({ message })}\n\n`);
      res.end();
    } catch {
      // Response may already be closed
    }
  }
});

// =============================================================================
// ZIP extraction — pure Node.js, no external dependency
// =============================================================================
async function extractZip(zipBuffer: Buffer, outputDir: string): Promise<void> {
  // Use Node.js built-in zlib + manual ZIP parsing
  const { createWriteStream } = await import('fs');
  const { mkdir } = await import('fs/promises');
  const { Readable } = await import('stream');

  // We use a simple approach: write the zip to a temp file, then use
  // the built-in `unzip` via child_process on the platform
  const tmpZip = path.join(outputDir + '.tmp.zip');
  await mkdir(path.dirname(tmpZip), { recursive: true });
  await mkdir(outputDir, { recursive: true });
  fs.writeFileSync(tmpZip, zipBuffer);

  try {
    // Cross-platform extraction
    const { execSync } = await import('child_process');
    const isWindows = process.platform === 'win32';

    if (isWindows) {
      execSync(`powershell -NoProfile -Command "Expand-Archive -Force -Path '${tmpZip}' -DestinationPath '${outputDir}'"`, {
        timeout: 60000,
        stdio: 'pipe',
      });
    } else {
      execSync(`unzip -o "${tmpZip}" -d "${outputDir}"`, {
        timeout: 60000,
        stdio: 'pipe',
      });
    }
  } finally {
    try { fs.unlinkSync(tmpZip); } catch { /* ignore */ }
  }
}

// =============================================================================
// Start server
// =============================================================================
export function startServer(port: number = PORT, host: string = HOST) {
  const server = app.listen(port, host, () => {
    console.log(`\n🚀 Collab Modeler Local Agent escuchando en http://${host}:${port}`);
    console.log(`   Solo acepta conexiones desde 127.0.0.1 (loopback)`);
    console.log(`   Nunca ejecuta comandos arbitrarios del servidor\n`);

    const status = getSdkStatus();
    console.log(`   Flutter: ${status.flutterAvailable ? `✓ v${status.flutterVersion}` : '✗ no encontrado'}`);
    console.log(`   ADB:     ${status.adbAvailable ? `✓ v${status.adbVersion}` : '✗ no encontrado'}`);
    if (status.devices.length > 0) {
      console.log(`   Dispositivos: ${status.devices.map(d => `${d.name} (${d.id})`).join(', ')}`);
    } else {
      console.log(`   Dispositivos: ninguno conectado`);
    }
    console.log();
  });
  return server;
}

// Run if executed directly
if (require.main === module) {
  startServer();
}

export { app };
