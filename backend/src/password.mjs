/**
 * Password hashing — LC v2 security baseline.
 *
 * scrypt N=32768,r=8,p=1, 16-byte salt, 64-byte key, verified with
 * timingSafeEqual. Stored format:
 *   scrypt$N=32768,r=8,p=1$<salt base64>$<key base64>
 * Parameters are read back from the stored string, so a future cost bump can
 * verify old hashes and re-hash on next login.
 */
import { randomBytes, scrypt as scryptCallback, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

const scrypt = promisify(scryptCallback);

const SALT_BYTES = 16;
const KEY_BYTES = 64;
const MAXMEM = 64 * 1024 * 1024; // 128 * N * r = 32 MiB, plus headroom
const DEFAULT_PARAMS = { N: 32768, r: 8, p: 1 };

function formatParams(params) {
  return `N=${params.N},r=${params.r},p=${params.p}`;
}

export async function hashPassword(password, params = DEFAULT_PARAMS) {
  const salt = randomBytes(SALT_BYTES);
  const key = await scrypt(password, salt, KEY_BYTES, { ...params, maxmem: MAXMEM });
  return `scrypt$${formatParams(params)}$${salt.toString('base64')}$${key.toString('base64')}`;
}

function parseStored(stored) {
  if (typeof stored !== 'string') return null;
  const parts = stored.split('$');
  if (parts.length !== 4 || parts[0] !== 'scrypt') return null;
  const params = {};
  for (const pair of parts[1].split(',')) {
    const [key, value] = pair.split('=');
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed <= 0) return null;
    params[key] = parsed;
  }
  if (!params.N || !params.r || !params.p) return null;
  const salt = Buffer.from(parts[2], 'base64');
  const key = Buffer.from(parts[3], 'base64');
  if (salt.length === 0 || key.length === 0) return null;
  return { params, salt, key };
}

export async function verifyPassword(password, stored) {
  const parsed = parseStored(stored);
  if (!parsed) return false;
  const derived = await scrypt(password, parsed.salt, parsed.key.length, {
    N: parsed.params.N,
    r: parsed.params.r,
    p: parsed.params.p,
    maxmem: MAXMEM,
  });
  return derived.length === parsed.key.length && timingSafeEqual(derived, parsed.key);
}

/** Fixed-cost dummy verify so unknown usernames do not return measurably faster. */
export async function burnPasswordTime(password, dummyHash) {
  try {
    await verifyPassword(password, dummyHash);
  } catch {
    /* timing only */
  }
}
