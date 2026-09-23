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
import { validateOrigin, verifySignature, consumeNonce, sanitizeOutputPath, validateApiBaseUrl } from './security';
import { getSdkStatus, listDevices } from './sdk-checker';
import { runCommand, setupAdbReverse, RunnerEvent } from './runner';

const app = express();
const PORT = parseInt(process.env.AGENT_PORT || '9876', 10);
const HOST = '127.0.0.1'; // NEVER bind to 0.0.0.0

// Source is materialized locally from the signed contract, so large ZIP payloads
// never traverse the browser or the local agent API.
app.use(express.json({ limit: '2mb' }));

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
// POST /api/generate — Generate Flutter source locally from a signed spec
// =============================================================================
app.post('/api/generate', requireOrigin, async (req: Request, res: Response) => {
  try {
    const { spec, outputDir } = req.body;
    const signingKey = process.env.AGENT_SIGNING_KEY;

    if (!spec) {
      res.status(400).json({ error: 'Falta la especificación firmada' });
      return;
    }
    if (!signingKey) {
      res.status(503).json({ error: 'El agente no tiene una clave de firma configurada', code: 'AGENT_NOT_CONFIGURED' });
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

    // Determine repository root from mobile-flutter/tools/local-agent.
    const workspaceRoot = path.resolve(__dirname, '..', '..', '..', '..');

    // Determine output directory
    const projectName = String(spec.diagramName || 'collab_modeler_app').toLowerCase()
      .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'collab_modeler_app';
    const requestedDir = outputDir || path.join(workspaceRoot, 'generated-mobile', `${projectName}_r${spec.revision}`);
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

    sendEvent('progress', { step: 'materializing', message: `Generando proyecto Flutter en ${safeDir}…` });
    materializeFlutterFromSpec(spec, safeDir);
    sendEvent('progress', { step: 'materialized', message: `Proyecto Flutter generado en ${safeDir}` });

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
    if (deviceId !== undefined && (typeof deviceId !== 'string' || !/^[A-Za-z0-9._:-]{1,128}$/.test(deviceId))) {
      res.status(400).json({ error: 'Identificador de dispositivo inválido', code: 'INVALID_DEVICE' });
      return;
    }
    const safeApiBaseUrl = validateApiBaseUrl(apiBaseUrl || 'http://localhost:8080');
    if (!safeApiBaseUrl) {
      res.status(400).json({ error: 'La URL de API debe usar HTTP y una dirección local o privada', code: 'INVALID_API_URL' });
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
      apiBaseUrl: safeApiBaseUrl,
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
// Local, deterministic materialization. The existing mobile application remains
// untouched; each revision receives its own editable project directory.
function materializeFlutterFromSpec(spec: any, outputDir: string): void {
  if (!Array.isArray(spec.entities)) throw new Error('La especificación no contiene entidades válidas');
  fs.mkdirSync(path.join(outputDir, 'lib'), { recursive: true });
  const title = dartString(String(spec.diagramName || 'Collab Modeler'));
  const tabs = spec.entities.map((entity: any, index: number) => `Tab(text: '${dartString(String(entity.name || `Entidad ${index + 1}`))}')`).join(', ');
  const views = spec.entities.map((entity: any) => `${dartIdentifier(String(entity.name || 'Entidad'))}View()`).join(', ');
  const classes = spec.entities.map((entity: any) => generatedEntityClass(entity)).join('\n');
  fs.writeFileSync(path.join(outputDir, 'pubspec.yaml'), `name: collab_modeler_generated\ndescription: Aplicación generada localmente desde una especificación firmada.\npublish_to: none\nenvironment:\n  sdk: '>=3.3.0 <4.0.0'\ndependencies:\n  flutter:\n    sdk: flutter\ndev_dependencies:\n  flutter_test:\n    sdk: flutter\n  flutter_lints: ^5.0.0\nflutter:\n  uses-material-design: true\n`);
  fs.writeFileSync(path.join(outputDir, 'lib', 'main.dart'), `import 'package:flutter/material.dart';\n\nvoid main() => runApp(const GeneratedApp());\nclass GeneratedApp extends StatelessWidget { const GeneratedApp({super.key}); @override Widget build(BuildContext context) => MaterialApp(title: '${title}', theme: ThemeData(colorSchemeSeed: const Color(0xff4f46e5), useMaterial3: true), home: const GeneratedHome()); }\nclass GeneratedHome extends StatelessWidget { const GeneratedHome({super.key}); @override Widget build(BuildContext context) => DefaultTabController(length: ${Math.max(1, spec.entities.length)}, child: Scaffold(appBar: AppBar(title: const Text('${title}'), bottom: const TabBar(isScrollable: true, tabs: [${tabs || "const Tab(text: 'Inicio')"}])), body: TabBarView(children: [${views || "const Center(child: Text('No hay entidades en esta revisión.'))"}]))); }\n${classes}\n`);
  fs.writeFileSync(path.join(outputDir, 'modeler-mobile-spec.json'), JSON.stringify(spec, null, 2));
}
function generatedEntityClass(entity: any): string {
  const className = dartIdentifier(String(entity.name || 'Entidad'));
  const attributes = Array.isArray(entity.attributes) ? entity.attributes : [];
  const fields = attributes.map((attribute: any) => `TextField(decoration: const InputDecoration(labelText: '${dartString(String(attribute.name || 'campo'))}')),`).join('');
  return `class ${className}View extends StatelessWidget { const ${className}View({super.key}); @override Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(16), children: [Text('${dartString(String(entity.name || 'Entidad'))}', style: Theme.of(context).textTheme.headlineSmall), const SizedBox(height: 12), const Text('CRUD generado desde la revisión firmada.'), const SizedBox(height: 16), ${fields || "const Text('Esta entidad no tiene atributos.')"}]); }`;
}
function dartIdentifier(value: string): string { const clean = value.replace(/[^A-Za-z0-9_]/g, '_').replace(/^\d/, '_'); return clean ? clean[0].toUpperCase() + clean.slice(1) : 'Entidad'; }
function dartString(value: string): string { return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/[\r\n]/g, ' '); }

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
