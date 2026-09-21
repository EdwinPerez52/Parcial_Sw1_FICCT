/**
 * Security module for the Collab Modeler Local Agent.
 * Handles origin validation, HMAC-SHA256 signature verification,
 * nonce tracking for single-use specs, and path sanitization.
 */
import * as crypto from 'crypto';
import * as path from 'path';
import * as os from 'os';

/** Allowed origins that may talk to the local agent. */
const ALLOWED_ORIGINS = new Set([
  'http://localhost:5173',
  'http://127.0.0.1:5173',
  'http://localhost:3000',
  'http://127.0.0.1:3000',
]);

/** Set of nonces already consumed — prevents replay of the same spec. */
const usedNonces = new Set<string>();

/**
 * Validates that the request origin is from a known local frontend.
 * Returns true if valid, false otherwise.
 */
export function validateOrigin(origin: string | undefined, referer: string | undefined): boolean {
  if (origin && ALLOWED_ORIGINS.has(origin)) return true;
  if (referer) {
    try {
      const refOrigin = new URL(referer).origin;
      return ALLOWED_ORIGINS.has(refOrigin);
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * Verifies the HMAC-SHA256 signature of a spec object.
 * The signature field is removed before computing the HMAC over the remaining JSON.
 */
export function verifySignature(spec: Record<string, unknown>, signingKey: string): boolean {
  if (!spec.signature || typeof spec.signature !== 'string') return false;
  const signature = spec.signature as string;

  const copy = { ...spec };
  delete copy.signature;

  const payload = JSON.stringify(copy);
  const expected = crypto
    .createHmac('sha256', signingKey)
    .update(payload, 'utf8')
    .digest('hex');

  const sigBuf = Buffer.from(signature, 'utf8');
  const expBuf = Buffer.from(expected, 'utf8');

  if (sigBuf.length !== expBuf.length) {
    return false;
  }

  return crypto.timingSafeEqual(sigBuf, expBuf);
}

/**
 * Checks and consumes a nonce. Returns true if the nonce is fresh (not used before).
 * A consumed nonce cannot be reused.
 */
export function consumeNonce(nonce: string | undefined): boolean {
  if (!nonce || typeof nonce !== 'string' || nonce.length < 10) return false;
  if (usedNonces.has(nonce)) return false;
  usedNonces.add(nonce);
  return true;
}

/** Resets the nonce store — useful for testing. */
export function resetNonces(): void {
  usedNonces.clear();
}

/**
 * Validates that the output path is safe:
 * - Must be under the user's home directory or the workspace root.
 * - Must not contain `..` traversal segments.
 * - Must not be a system directory.
 * Returns the resolved absolute path, or null if invalid.
 */
export function sanitizeOutputPath(requestedPath: string, workspaceRoot: string): string | null {
  if (!requestedPath || typeof requestedPath !== 'string') return null;

  // Reject path traversal
  if (requestedPath.includes('..')) return null;

  const resolved = path.resolve(requestedPath);
  const home = os.homedir();
  const normalizedWorkspace = path.resolve(workspaceRoot);

  // Must be under home or workspace
  if (!resolved.startsWith(home + path.sep) && !resolved.startsWith(normalizedWorkspace + path.sep) &&
      resolved !== normalizedWorkspace) {
    return null;
  }

  // Block system directories
  const blockedPrefixes = [
    path.join(home, 'AppData', 'Local'),
    path.join(home, 'AppData', 'Roaming'),
    'C:\\Windows',
    'C:\\Program Files',
    'C:\\Program Files (x86)',
    '/usr',
    '/etc',
    '/var',
    '/bin',
    '/sbin',
  ];
  for (const blocked of blockedPrefixes) {
    if (resolved.startsWith(blocked)) return null;
  }

  return resolved;
}
