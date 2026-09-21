import {
  validateOrigin,
  verifySignature,
  consumeNonce,
  resetNonces,
  sanitizeOutputPath,
} from '../security';
import * as crypto from 'crypto';
import * as os from 'os';
import * as path from 'path';

describe('security', () => {
  // =========================================================================
  // Origin validation
  // =========================================================================
  describe('validateOrigin', () => {
    it('accepts http://localhost:5173', () => {
      expect(validateOrigin('http://localhost:5173', undefined)).toBe(true);
    });

    it('accepts http://127.0.0.1:5173', () => {
      expect(validateOrigin('http://127.0.0.1:5173', undefined)).toBe(true);
    });

    it('rejects external origin', () => {
      expect(validateOrigin('http://evil.com', undefined)).toBe(false);
    });

    it('rejects null origin without referer', () => {
      expect(validateOrigin(undefined, undefined)).toBe(false);
    });

    it('accepts valid referer when origin is absent', () => {
      expect(validateOrigin(undefined, 'http://localhost:5173/some/page')).toBe(true);
    });

    it('rejects invalid referer', () => {
      expect(validateOrigin(undefined, 'http://attacker.com/page')).toBe(false);
    });

    it('rejects https on non-https allowed origin', () => {
      expect(validateOrigin('https://localhost:5173', undefined)).toBe(false);
    });

    it('rejects empty string origin', () => {
      expect(validateOrigin('', undefined)).toBe(false);
    });
  });

  // =========================================================================
  // HMAC-SHA256 signature verification
  // =========================================================================
  describe('verifySignature', () => {
    const signingKey = 'test-secret-key-32bytes-for-hmac-sha256';

    function createSignedSpec(payload: Record<string, unknown>): Record<string, unknown> {
      const json = JSON.stringify(payload);
      const signature = crypto
        .createHmac('sha256', signingKey)
        .update(json, 'utf8')
        .digest('hex');
      return { ...payload, signature };
    }

    it('verifies a valid signature', () => {
      const spec = createSignedSpec({
        specVersion: '1.0.0',
        diagramId: 'test-id',
        nonce: 'test-nonce-123456',
        revision: 1,
      });
      expect(verifySignature(spec, signingKey)).toBe(true);
    });

    it('rejects a tampered spec', () => {
      const spec = createSignedSpec({
        specVersion: '1.0.0',
        diagramId: 'test-id',
        nonce: 'test-nonce-123456',
        revision: 1,
      });
      spec.revision = 999; // Tamper
      expect(verifySignature(spec, signingKey)).toBe(false);
    });

    it('rejects wrong signing key', () => {
      const spec = createSignedSpec({
        specVersion: '1.0.0',
        diagramId: 'test-id',
        nonce: 'test-nonce-123456',
      });
      expect(verifySignature(spec, 'wrong-key')).toBe(false);
    });

    it('rejects spec without signature field', () => {
      expect(verifySignature({ specVersion: '1.0.0' }, signingKey)).toBe(false);
    });

    it('rejects spec with empty signature', () => {
      expect(verifySignature({ specVersion: '1.0.0', signature: '' }, signingKey)).toBe(false);
    });
  });

  // =========================================================================
  // Nonce consumption
  // =========================================================================
  describe('consumeNonce', () => {
    beforeEach(() => {
      resetNonces();
    });

    it('accepts a fresh nonce', () => {
      expect(consumeNonce('nonce-abc-12345678')).toBe(true);
    });

    it('rejects a previously consumed nonce', () => {
      consumeNonce('nonce-abc-12345678');
      expect(consumeNonce('nonce-abc-12345678')).toBe(false);
    });

    it('rejects undefined nonce', () => {
      expect(consumeNonce(undefined)).toBe(false);
    });

    it('rejects short nonce', () => {
      expect(consumeNonce('short')).toBe(false);
    });

    it('rejects empty string nonce', () => {
      expect(consumeNonce('')).toBe(false);
    });

    it('accepts different nonces independently', () => {
      expect(consumeNonce('nonce-one-1234567')).toBe(true);
      expect(consumeNonce('nonce-two-1234567')).toBe(true);
    });
  });

  // =========================================================================
  // Path sanitization
  // =========================================================================
  describe('sanitizeOutputPath', () => {
    const home = os.homedir();
    const workspace = path.join(home, 'projects', 'collab-modeler');

    it('accepts path under home', () => {
      const result = sanitizeOutputPath(path.join(home, 'flutter-output'), workspace);
      expect(result).toBe(path.resolve(path.join(home, 'flutter-output')));
    });

    it('accepts path under workspace', () => {
      const result = sanitizeOutputPath(path.join(workspace, 'mobile_parcial'), workspace);
      expect(result).toBe(path.resolve(path.join(workspace, 'mobile_parcial')));
    });

    it('rejects path traversal with ..', () => {
      expect(sanitizeOutputPath(workspace + '/../../etc', workspace)).toBeNull();
      expect(sanitizeOutputPath('../../../etc', workspace)).toBeNull();
      expect(sanitizeOutputPath(path.join(workspace, '..', '..', '..', '..', 'etc'), workspace)).toBeNull();
    });

    it('rejects empty path', () => {
      expect(sanitizeOutputPath('', workspace)).toBeNull();
    });

    it('rejects null-like path', () => {
      expect(sanitizeOutputPath(null as unknown as string, workspace)).toBeNull();
    });

    it('rejects system directories', () => {
      if (process.platform === 'win32') {
        expect(sanitizeOutputPath('C:\\Windows\\System32', workspace)).toBeNull();
        expect(sanitizeOutputPath('C:\\Program Files\\test', workspace)).toBeNull();
      } else {
        expect(sanitizeOutputPath('/usr/local/bin', workspace)).toBeNull();
        expect(sanitizeOutputPath('/etc/config', workspace)).toBeNull();
      }
    });
  });
});
