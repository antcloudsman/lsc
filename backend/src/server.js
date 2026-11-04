import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { openDb } from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SERVICE = 'lc-backend';
const VERSION = '0.1.0';
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '0.0.0.0';
const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'lc.sqlite');
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || `http://127.0.0.1:${PORT}`).replace(/\/+$/, '');
const CORS_ORIGIN = process.env.CORS_ORIGIN || '*';
const BODY_LIMIT = 1024 * 1024;
const OWNER_RE = /^[A-Za-z0-9._:-]{1,128}$/;
const CASE_ID_RE = /^[A-Za-z0-9_-]{6,64}$/;

const store = openDb(DB_PATH);
const startedAt = Date.now();

class HttpError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

function fail(status, code, message, details) {
  throw new HttpError(status, code, message, details);
}

function baseHeaders() {
  return {
    'access-control-allow-origin': CORS_ORIGIN,
    'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'access-control-allow-headers': 'content-type',
    'access-control-max-age': '600',
    'x-content-type-options': 'nosniff',
  };
}

function sendJson(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    ...baseHeaders(),
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(data),
    'cache-control': 'no-store',
  });
  res.end(data);
}

function sendHtml(res, status, html) {
  res.writeHead(status, {
    ...baseHeaders(),
    'content-type': 'text/html; charset=utf-8',
    'content-length': Buffer.byteLength(html),
    'cache-control': 'no-store',
  });
  res.end(html);
}

function readBody(req, limit = BODY_LIMIT) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new HttpError(413, 'payload_too_large', `request body exceeds ${limit} bytes`));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', (error) => {
      if (error instanceof HttpError) reject(error);
      else reject(new HttpError(400, 'bad_request', 'could not read request body'));
    });
  });
}

async function readJson(req) {
  const raw = await readBody(req);
  if (!raw.trim()) return {};
  const type = String(req.headers['content-type'] || '');
  if (type && !type.includes('application/json')) {
    fail(415, 'unsupported_media_type', 'Content-Type must be application/json');
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    fail(400, 'invalid_json', 'request body is not valid JSON');
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    fail(400, 'invalid_json', 'request body must be a JSON object');
  }
  return parsed;
}

function requireOwner(value) {
  if (typeof value !== 'string' || !OWNER_RE.test(value)) {
    fail(400, 'invalid_owner_id', 'owner_id is required (1-128 chars: A-Z a-z 0-9 . _ : -)');
  }
  return value;
}

function asString(value, field, maxLength, { allowEmpty = false } = {}) {
  if (typeof value !== 'string') fail(400, 'invalid_field', `${field} must be a string`);
  const trimmed = value.trim();
  if (!allowEmpty && trimmed.length === 0) fail(400, 'invalid_field', `${field} must not be empty`);
  if (trimmed.length > maxLength) fail(400, 'invalid_field', `${field} exceeds ${maxLength} chars`);
  return trimmed;
}

function asDegree(value, field) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 360) {
    fail(400, 'invalid_field', `${field} must be a number in [0, 360]`);
  }
  return value === 360 ? 0 : value;
}

function asTags(value) {
  if (!Array.isArray(value)) fail(400, 'invalid_field', 'tags must be an array of strings');
  if (value.length > 32) fail(400, 'invalid_field', 'tags may contain at most 32 items');
  return value.map((tag, index) => {
    if (typeof tag !== 'string') fail(400, 'invalid_field', `tags[${index}] must be a string`);
    const trimmed = tag.trim();
    if (trimmed.length > 32) fail(400, 'invalid_field', `tags[${index}] exceeds 32 chars`);
    return trimmed;
  }).filter(Boolean);
}

function asLocation(value) {
  if (value === null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) fail(400, 'invalid_field', 'location must be an object or null');
  const out = {};
  if (value.lat !== undefined && value.lat !== null) {
    if (typeof value.lat !== 'number' || value.lat < -90 || value.lat > 90) fail(400, 'invalid_field', 'location.lat must be in [-90, 90]');
    out.lat = value.lat;
  }
  if (value.lng !== undefined && value.lng !== null) {
    if (typeof value.lng !== 'number' || value.lng < -180 || value.lng > 180) fail(400, 'invalid_field', 'location.lng must be in [-180, 180]');
    out.lng = value.lng;
  }
  for (const key of ['name', 'address', 'coord_system']) {
    if (value[key] !== undefined && value[key] !== null) out[key] = asString(value[key], `location.${key}`, 200);
  }
  return out;
}

function asData(value) {
  if (value === null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) fail(400, 'invalid_field', 'data must be a JSON object or null');
  const encoded = JSON.stringify(value);
  if (Buffer.byteLength(encoded) > 256 * 1024) fail(400, 'invalid_field', 'data exceeds 256KB');
  return value;
}

function normalizeCaseFields(body, { requireTitle = false } = {}) {
  const out = {};
  if (body.title !== undefined || requireTitle) {
    out.title = asString(body.title, 'title', 200);
  }
  if (body.sit_degree !== undefined && body.sit_degree !== null) out.sit_degree = asDegree(body.sit_degree, 'sit_degree');
  if (body.facing_degree !== undefined && body.facing_degree !== null) out.facing_degree = asDegree(body.facing_degree, 'facing_degree');
  if (body.compass_type !== undefined && body.compass_type !== null) out.compass_type = asString(body.compass_type, 'compass_type', 64);
  if (body.location !== undefined) out.location = asLocation(body.location);
  if (body.tags !== undefined) out.tags = asTags(body.tags);
  if (body.notes !== undefined && body.notes !== null) out.notes = asString(body.notes, 'notes', 20000, { allowEmpty: true });
  if (body.data !== undefined) out.data = asData(body.data);
  return out;
}

function parseLimit(url, fallback = 100, max = 200) {
  const raw = url.searchParams.get('limit');
  if (!raw) return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1 || value > max) {
    fail(400, 'invalid_limit', `limit must be an integer in [1, ${max}]`);
  }
  return value;
}

function parseSince(url) {
  const raw = url.searchParams.get('since') ?? url.searchParams.get('since_rev');
  if (raw === null || raw === '') return 0;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 0) fail(400, 'invalid_since', 'since must be a non-negative integer');
  return value;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function sharePage(share) {
  const c = share.case;
  const title = escapeHtml(c.title);
  const rows = [
    c.sit_degree === null || c.sit_degree === undefined ? null : `坐向度数：${escapeHtml(c.sit_degree)}°`,
    c.facing_degree === null || c.facing_degree === undefined ? null : `朝向：${escapeHtml(c.facing_degree)}°`,
    c.compass_type ? `盘面：${escapeHtml(c.compass_type)}` : null,
    Array.isArray(c.tags) && c.tags.length ? `标签：${c.tags.map(escapeHtml).join('、')}` : null,
    c.location && (c.location.name || c.location.address)
      ? `位置：${escapeHtml(c.location.name || c.location.address)}`
      : null,
  ].filter(Boolean);
  const notes = c.notes ? `<p class="notes">${escapeHtml(c.notes)}</p>` : '';
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} · 山水罗盘</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #181410; color: #ebe3d6; font: 16px/1.7 "Noto Sans CJK SC", "Microsoft YaHei", sans-serif; }
  main { max-width: 720px; margin: 0 auto; padding: 32px 20px 56px; }
  h1 { font-size: 24px; color: #dcc292; margin: 0 0 4px; }
  .meta { color: #a99475; font-size: 14px; margin-bottom: 24px; }
  ul { list-style: none; padding: 0; margin: 0 0 24px; }
  li { padding: 8px 0; border-bottom: 1px solid #2e271f; }
  .notes { white-space: pre-wrap; background: #221c16; border-radius: 8px; padding: 16px; }
  footer { margin-top: 40px; color: #7c6b54; font-size: 13px; }
</style>
</head>
<body>
<main>
  <h1>${title}</h1>
  <div class="meta">山水罗盘 · 风水案例分享 · ${escapeHtml(share.created_at)}</div>
  <ul>${rows.map((row) => `<li>${row}</li>`).join('')}</ul>
  ${notes}
  <footer>分享链接有效期至 ${escapeHtml(share.expires_at || '长期')}；由山水罗盘生成。</footer>
</main>
</body>
</html>`;
}

async function route(req, res) {
  const url = new URL(req.url, 'http://localhost');
  const pathname = url.pathname.replace(/\/+$/, '') || '/';
  const method = req.method || 'GET';

  if (method === 'OPTIONS') {
    res.writeHead(204, baseHeaders());
    res.end();
    return;
  }

  if (method === 'GET' && (pathname === '/health' || pathname === '/v1/health')) {
    const health = store.health();
    sendJson(res, 200, {
      status: health.db === 'ok' ? 'ok' : 'degraded',
      service: SERVICE,
      version: VERSION,
      time: new Date().toISOString(),
      uptime_s: Math.round((Date.now() - startedAt) / 1000),
      db: health.db,
      cases: health.cases,
      shares: health.shares,
    });
    return;
  }

  if (method === 'GET' && pathname === '/') {
    sendJson(res, 200, {
      service: SERVICE,
      version: VERSION,
      api: '/v1',
      docs: 'backend/api.md',
      health: '/health',
    });
    return;
  }

  // ----- cases -----------------------------------------------------------
  if (pathname === '/v1/cases' && method === 'GET') {
    const ownerId = requireOwner(url.searchParams.get('owner_id'));
    const includeDeleted = url.searchParams.get('include_deleted') === '1';
    const items = store.listCases(ownerId, {
      sinceRev: parseSince(url),
      limit: parseLimit(url),
      includeDeleted,
    });
    sendJson(res, 200, { items, count: items.length });
    return;
  }

  if (pathname === '/v1/cases' && method === 'POST') {
    const body = await readJson(req);
    const ownerId = requireOwner(body.owner_id);
    const fields = normalizeCaseFields(body, { requireTitle: true });
    if (body.id !== undefined) {
      if (typeof body.id !== 'string' || !CASE_ID_RE.test(body.id)) {
        fail(400, 'invalid_case_id', 'id must match [A-Za-z0-9_-]{6,64}');
      }
      if (store.getCase(body.id, ownerId, true)) fail(409, 'case_exists', `case ${body.id} already exists`);
    }
    const created = store.createCase(ownerId, fields, { id: body.id });
    sendJson(res, 201, { case: created });
    return;
  }

  const caseMatch = pathname.match(/^\/v1\/cases\/([^/]+)$/);
  if (caseMatch) {
    const caseId = decodeURIComponent(caseMatch[1]);
    if (method === 'GET') {
      const ownerId = requireOwner(url.searchParams.get('owner_id'));
      const found = store.getCase(caseId, ownerId);
      if (!found) fail(404, 'case_not_found', `case ${caseId} not found`);
      sendJson(res, 200, { case: found });
      return;
    }
    if (method === 'PUT') {
      const body = await readJson(req);
      const ownerId = requireOwner(body.owner_id);
      const patch = normalizeCaseFields(body);
      const updated = store.updateCase(ownerId, caseId, patch);
      if (!updated) fail(404, 'case_not_found', `case ${caseId} not found or deleted`);
      sendJson(res, 200, { case: updated });
      return;
    }
    if (method === 'DELETE') {
      const ownerId = requireOwner(url.searchParams.get('owner_id'));
      const deleted = store.deleteCase(ownerId, caseId);
      if (!deleted) fail(404, 'case_not_found', `case ${caseId} not found`);
      sendJson(res, 200, { deleted: true, id: caseId });
      return;
    }
    fail(405, 'method_not_allowed', `${method} is not supported for ${pathname}`);
  }

  // ----- settings --------------------------------------------------------
  const settingsMatch = pathname.match(/^\/v1\/settings\/([^/]+)$/);
  if (settingsMatch) {
    const ownerId = requireOwner(decodeURIComponent(settingsMatch[1]));
    if (method === 'GET') {
      sendJson(res, 200, store.getSettings(ownerId));
      return;
    }
    if (method === 'PUT') {
      const body = await readJson(req);
      if (body.settings === undefined) fail(400, 'invalid_field', 'settings is required');
      const settings = asData(body.settings);
      if (settings === null) fail(400, 'invalid_field', 'settings must be a JSON object');
      sendJson(res, 200, store.putSettings(ownerId, settings));
      return;
    }
    fail(405, 'method_not_allowed', `${method} is not supported for ${pathname}`);
  }

  // ----- sync ------------------------------------------------------------
  if (pathname === '/v1/sync/pull' && method === 'GET') {
    const ownerId = requireOwner(url.searchParams.get('owner_id'));
    const since = parseSince(url);
    const result = store.pull(ownerId, since, parseLimit(url));
    sendJson(res, 200, { sync: result });
    return;
  }

  if (pathname === '/v1/sync/push' && method === 'POST') {
    const body = await readJson(req);
    const ownerId = requireOwner(body.owner_id);
    const cases = body.cases === undefined ? [] : body.cases;
    if (!Array.isArray(cases)) fail(400, 'invalid_field', 'cases must be an array');
    if (cases.length > 500) fail(400, 'invalid_field', 'cases may contain at most 500 items');

    const applied = [];
    const conflicts = [];
    for (let index = 0; index < cases.length; index += 1) {
      const item = cases[index];
      if (item === null || typeof item !== 'object' || Array.isArray(item)) {
        fail(400, 'invalid_field', `cases[${index}] must be an object`);
      }
      const fields = normalizeCaseFields(item, { requireTitle: true });
      const existing = item.id ? store.getCase(item.id, ownerId, true) : null;
      if (item.id !== undefined && (typeof item.id !== 'string' || !CASE_ID_RE.test(item.id))) {
        fail(400, 'invalid_case_id', `cases[${index}].id must match [A-Za-z0-9_-]{6,64}`);
      }
      if (existing && existing.deleted) {
        conflicts.push({ id: item.id, reason: 'deleted_on_server', server_rev: existing.rev });
        continue;
      }
      if (existing) {
        applied.push(store.updateCase(ownerId, item.id, fields));
      } else {
        applied.push(store.createCase(ownerId, fields, { id: item.id }));
      }
    }
    let settings = null;
    if (body.settings !== undefined) {
      if (body.settings === null || typeof body.settings !== 'object' || Array.isArray(body.settings)) {
        fail(400, 'invalid_field', 'settings must be a JSON object');
      }
      settings = store.putSettings(ownerId, body.settings);
    }
    sendJson(res, 200, { sync: { applied, conflicts, settings } });
    return;
  }

  // ----- shares ----------------------------------------------------------
  if (pathname === '/v1/shares' && method === 'POST') {
    const body = await readJson(req);
    const ownerId = requireOwner(body.owner_id);
    if (typeof body.case_id !== 'string') fail(400, 'invalid_field', 'case_id is required');
    const ttlDays = body.expires_in_days === undefined ? 30 : Number(body.expires_in_days);
    if (!Number.isInteger(ttlDays) || ttlDays < 1 || ttlDays > 365) {
      fail(400, 'invalid_field', 'expires_in_days must be an integer in [1, 365]');
    }
    const share = store.createShare(ownerId, body.case_id, ttlDays);
    if (!share) fail(404, 'case_not_found', `case ${body.case_id} not found`);
    sendJson(res, 201, {
      share: {
        token: share.token,
        url: `${PUBLIC_BASE_URL}/s/${share.token}`,
        case_id: share.case_id,
        created_at: share.created_at,
        expires_at: share.expires_at,
      },
    });
    return;
  }

  const shareMatch = pathname.match(/^\/v1\/shares\/([^/]+)$/);
  if (shareMatch) {
    const token = decodeURIComponent(shareMatch[1]);
    if (method === 'GET') {
      const share = store.getShare(token);
      if (!share) fail(404, 'share_not_found', 'share link is invalid, revoked or expired');
      sendJson(res, 200, { share });
      return;
    }
    if (method === 'DELETE') {
      const ownerId = requireOwner(url.searchParams.get('owner_id'));
      if (!store.revokeShare(ownerId, token)) fail(404, 'share_not_found', 'share not found');
      sendJson(res, 200, { revoked: true, token });
      return;
    }
    fail(405, 'method_not_allowed', `${method} is not supported for ${pathname}`);
  }

  const pageMatch = pathname.match(/^\/s\/([^/]+)$/);
  if (pageMatch && method === 'GET') {
    const share = store.getShare(decodeURIComponent(pageMatch[1]));
    if (!share) {
      sendHtml(res, 404, '<!doctype html><meta charset="utf-8"><title>链接不可用</title><p>分享链接无效、已撤销或已过期。</p>');
      return;
    }
    sendHtml(res, 200, sharePage(share));
    return;
  }

  fail(404, 'not_found', `no route for ${method} ${pathname}`);
}

const server = http.createServer((req, res) => {
  const started = Date.now();
  res.on('finish', () => {
    console.log(`${req.method} ${req.url} -> ${res.statusCode} ${Date.now() - started}ms`);
  });
  route(req, res).catch((error) => {
    if (res.headersSent) {
      res.end();
      return;
    }
    if (error instanceof HttpError) {
      sendJson(res, error.status, {
        error: { code: error.code, message: error.message, ...(error.details ? { details: error.details } : {}) },
      });
      return;
    }
    console.error('unhandled error:', error);
    sendJson(res, 500, { error: { code: 'internal_error', message: 'internal server error' } });
  });
});

server.listen(PORT, HOST, () => {
  console.log(`${SERVICE} v${VERSION} listening on http://${HOST}:${PORT} (db=${DB_PATH})`);
});

function shutdown(signal) {
  console.log(`${signal} received, shutting down`);
  server.close(() => {
    try { store.db.close(); } catch { /* ignore */ }
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 5000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
