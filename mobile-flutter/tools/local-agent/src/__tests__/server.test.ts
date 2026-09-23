import { app } from '../server';
import * as http from 'http';
import * as crypto from 'crypto';
import { resetNonces, consumeNonce } from '../security';

// Helper to make HTTP requests to the test server
function request(
  server: http.Server,
  method: string,
  path: string,
  body?: unknown,
  headers?: Record<string, string>,
): Promise<{ status: number; body: string; headers: http.IncomingHttpHeaders }> {
  return new Promise((resolve, reject) => {
    const address = server.address() as { port: number };
    const data = body ? JSON.stringify(body) : undefined;

    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: address.port,
        path,
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(data ? { 'Content-Length': Buffer.byteLength(data).toString() } : {}),
          ...headers,
        },
      },
      (res) => {
        let responseBody = '';
        res.on('data', (chunk) => (responseBody += chunk));
        res.on('end', () => resolve({ status: res.statusCode!, body: responseBody, headers: res.headers }));
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

describe('Local Agent Server', () => {
  let server: http.Server;

  beforeAll((done) => {
    server = app.listen(0, '127.0.0.1', () => done());
  });

  afterAll((done) => {
    if (typeof (server as any).closeAllConnections === 'function') {
      (server as any).closeAllConnections();
    }
    server.close(done);
  });

  beforeEach(() => {
    resetNonces();
    process.env.AGENT_SIGNING_KEY = 'test-secret-key-32bytes-for-hmac';
  });

  // =========================================================================
  // GET /api/status
  // =========================================================================
  describe('GET /api/status', () => {
    it('returns agent status', async () => {
      const res = await request(server, 'GET', '/api/status');
      expect(res.status).toBe(200);
      const data = JSON.parse(res.body);
      expect(data.agent).toBe('collab-modeler-local-agent');
      expect(data.version).toBe('1.0.0');
      expect(typeof data.flutterAvailable).toBe('boolean');
      expect(typeof data.adbAvailable).toBe('boolean');
    }, 20000);
  });

  // =========================================================================
  // GET /api/devices
  // =========================================================================
  describe('GET /api/devices', () => {
    it('returns devices array', async () => {
      const res = await request(server, 'GET', '/api/devices');
      expect(res.status).toBe(200);
      const data = JSON.parse(res.body);
      expect(Array.isArray(data.devices)).toBe(true);
    });
  });

  // =========================================================================
  // POST /api/generate — security checks
  // =========================================================================
  describe('POST /api/generate', () => {
    it('rejects request without Origin header', async () => {
      const res = await request(server, 'POST', '/api/generate', { spec: {} });
      expect(res.status).toBe(403);
      const data = JSON.parse(res.body);
      expect(data.code).toBe('FORBIDDEN_ORIGIN');
    });

    it('rejects request from external origin', async () => {
      const res = await request(server, 'POST', '/api/generate', { spec: {} }, {
        Origin: 'http://evil.com',
      });
      expect(res.status).toBe(403);
    });

    it('rejects request with missing fields', async () => {
      const res = await request(server, 'POST', '/api/generate', {}, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(400);
    });

    it('rejects request with invalid signature', async () => {
      const spec = {
        specVersion: '1.0.0',
        diagramId: 'test-id',
        nonce: 'test-nonce-uuid-12345678',
        revision: 1,
        signature: 'invalid-signature-hex',
      };
      const res = await request(server, 'POST', '/api/generate', {
        spec,
      }, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(403);
      const data = JSON.parse(res.body);
      expect(data.code).toBe('INVALID_SIGNATURE');
    });

    it('rejects request with consumed nonce', async () => {
      const signingKey = 'test-secret-key-32bytes-for-hmac';
      const payload = {
        specVersion: '1.0.0',
        diagramId: 'test-id',
        nonce: 'nonce-replay-test-uuid',
        revision: 1,
      };
      const signature = crypto
        .createHmac('sha256', signingKey)
        .update(JSON.stringify(payload), 'utf8')
        .digest('hex');
      const spec = { ...payload, signature };

      // Pre-consume the nonce to simulate replay
      consumeNonce(spec.nonce);

      // Request with consumed nonce — should be rejected immediately with 409
      const res = await request(server, 'POST', '/api/generate', {
        spec,
      }, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(409);
      const data = JSON.parse(res.body);
      expect(data.code).toBe('NONCE_CONSUMED');
    });
  });

  // =========================================================================
  // POST /api/run — security checks
  // =========================================================================
  describe('POST /api/run', () => {
    it('rejects request without Origin header', async () => {
      const res = await request(server, 'POST', '/api/run', { projectDir: '.', action: 'flutter-run' });
      expect(res.status).toBe(403);
    });

    it('rejects invalid action', async () => {
      const res = await request(server, 'POST', '/api/run', {
        projectDir: '.',
        action: 'rm -rf /',
      }, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(400);
      const data = JSON.parse(res.body);
      expect(data.code).toBe('INVALID_ACTION');
    });

    it('rejects request with missing fields', async () => {
      const res = await request(server, 'POST', '/api/run', {}, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(400);
    });

    it('rejects path traversal in projectDir', async () => {
      const res = await request(server, 'POST', '/api/run', {
        projectDir: 'C:\\Windows\\..\\..\\etc',
        action: 'flutter-run',
      }, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(400);
      const data = JSON.parse(res.body);
      expect(data.code).toBe('INVALID_PATH');
    });

    it('rejects a public API URL and a shell-like device id', async () => {
      const headers = { Origin: 'http://localhost:5173' };
      const publicUrl = await request(server, 'POST', '/api/run', { projectDir: '.', action: 'flutter-run', apiBaseUrl: 'https://example.com' }, headers);
      expect(publicUrl.status).toBe(400);
      expect(JSON.parse(publicUrl.body).code).toBe('INVALID_API_URL');
      const dangerousDevice = await request(server, 'POST', '/api/run', { projectDir: '.', action: 'flutter-run', deviceId: 'x & whoami' }, headers);
      expect(dangerousDevice.status).toBe(400);
      expect(JSON.parse(dangerousDevice.body).code).toBe('INVALID_DEVICE');
    });
  });

  // =========================================================================
  // CORS preflight
  // =========================================================================
  describe('OPTIONS preflight', () => {
    it('responds to preflight with CORS headers', async () => {
      const res = await request(server, 'OPTIONS', '/api/status', undefined, {
        Origin: 'http://localhost:5173',
      });
      expect(res.status).toBe(204);
      expect(res.headers['access-control-allow-methods']).toContain('POST');
    });
  });
});
