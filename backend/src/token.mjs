/**
 * Opaque session tokens — LC v2.
 *
 * Tokens are 32 random bytes (base64url, 43 chars), never JWTs: the original app
 * (ApiResult / HeaderInterceptor) sends an opaque `token` header, and an opaque
 * token can be revoked server-side, which a self-contained JWT cannot.
 *
 * The database only ever stores sha256(token + pepper); a DB leak therefore does
 * not yield usable sessions. The pepper comes from (in order):
 *   1. env LC_TOKEN_PEPPER            (>= 16 chars)
 *   2. file $LC_TOKEN_PEPPER_FILE     (default /run/secrets/lc_token_pepper)
 *   3. file /data/.token-pepper       (generated 0600 on first boot; demo fallback)
 * The pepper is never written to SQLite.
 */
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const PEPPER_MIN_LENGTH = 16;
const SECRETS_FILE = process.env.LC_TOKEN_PEPPER_FILE || '/run/secrets/lc_token_pepper';
const FALLBACK_FILE = process.env.LC_TOKEN_PEPPER_FALLBACK || '/data/.token-pepper';

const TOKEN_BYTES = 32;

function readTrimmedFile(file) {
  try {
    const text = fs.readFileSync(file, 'utf8').trim();
    return text.length > 0 ? text : null;
  } catch {
    return null;
  }
}

/**
 * Resolve the token pepper. Returns { pepper, source } where source is one of
 * 'env' | 'secrets' | 'file'. Never logs the pepper itself.
 */
export function loadPepper() {
  const fromEnv = (process.env.LC_TOKEN_PEPPER || '').trim();
  if (fromEnv) {
    if (fromEnv.length < PEPPER_MIN_LENGTH) {
      throw new Error('LC_TOKEN_PEPPER must be at least 16 characters');
    }
    return { pepper: fromEnv, source: 'env' };
  }

  const fromSecret = readTrimmedFile(SECRETS_FILE);
  if (fromSecret) {
    if (fromSecret.length < PEPPER_MIN_LENGTH) {
      throw new Error(`${SECRETS_FILE} must contain at least 16 characters`);
    }
    return { pepper: fromSecret, source: 'secrets' };
  }

  const existing = readTrimmedFile(FALLBACK_FILE);
  if (existing) return { pepper: existing, source: 'file' };

  fs.mkdirSync(path.dirname(FALLBACK_FILE), { recursive: true });
  const generated = randomBytes(32).toString('base64url');
  fs.writeFileSync(FALLBACK_FILE, `${generated}\n`, { mode: 0o600 });
  return { pepper: generated, source: 'file' };
}

export function generateToken() {
  return randomBytes(TOKEN_BYTES).toString('base64url');
}

export function hashToken(token, pepper) {
  return createHash('sha256').update(token).update(pepper).digest('hex');
}

/** Constant-time comparison of two hex digests of equal length. */
export function safeEqualHex(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = Buffer.from(a, 'hex');
  const right = Buffer.from(b, 'hex');
  if (left.length === 0 || left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}
