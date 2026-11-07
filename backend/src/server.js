/**
 * LC backend v2 — 127.0.0.1-only JSON API.
 *
 * Contract: backend/api.md (v2). Envelope `{code,msg,data}` with success code 200
 * (matching the original ApiResult), opaque `token` header, scrypt password
 * hashes, hashed session tokens, in-memory rate limits, redacted logs.
 *
 * All dependencies are inside the container: node:22 + node:http + node:sqlite.
 */
import http from 'node:http';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { openDb } from './db.js';
import { hashPassword, verifyPassword, burnPasswordTime } from './password.mjs';
import { loadPepper, generateToken, hashToken } from './token.mjs';
import { SlidingWindowLimiter } from './ratelimit.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SERVICE = 'lc-backend';
const VERSION = '0.2.0';
const PORT = Number(process.env.PORT || 8787);
// Inside the container we listen on 0.0.0.0 so compose can map 127.0.0.1:8787.
const HOST = process.env.HOST || '0.0.0.0';
const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'lc.sqlite');

const TOKEN_TTL_DAYS = clampInt(process.env.LC_TOKEN_TTL_DAYS, 30, 1, 365);
const MAX_SESSIONS_PER_USER = 10;
const LIMIT_AUTH_BYTES = 16 * 1024;
const LIMIT_BUSINESS_BYTES = 256 * 1024;
const RATE_LIMIT_ENABLED = process.env.LC_RATE_LIMIT !== '0';
const CORS_ORIGINS = (process.env.LC_CORS_ORIGIN || '')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

const USERNAME_RE = /^[A-Za-z0-9._-]{3,32}$/;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;
const FEEDBACK_TYPE_KNOWN = 2; // 原版取证到的唯一值（FeedbackViewModel.P）

const RATE_LIMITS = {
  // Per-IP budgets are loose enough to run selfcheck repeatedly; the per-user
  // login window (5/min) is the actual brute-force control.
  loginIp: { limit: 60, windowMs: 60_000 },
  loginIpHour: { limit: 600, windowMs: 60 * 60_000 },
  loginUser: { limit: 5, windowMs: 60_000 },
  registerIp: { limit: 100, windowMs: 60 * 60_000 },
  passwordUser: { limit: 5, windowMs: 60 * 60_000 },
  feedbackUser: { limit: 5, windowMs: 60 * 60_000 },
};

function clampInt(raw, fallback, min, max) {
  const value = Number(raw);
  if (!Number.isInteger(value)) return fallback;
  return Math.min(max, Math.max(min, value));
}

const store = openDb(DB_PATH);
const pepper = loadPepper();
const limiter = new SlidingWindowLimiter();
const startedAt = Date.now();

// Unknown-username logins must cost the same as real ones (no user enumeration by timing).
const dummyHash = await hashPassword(randomUUID());

if (pepper.source === 'file') {
  logSafe('warn', { event: 'pepper_generated', note: 'set LC_TOKEN_PEPPER or mount /run/secrets/lc_token_pepper' });
}

class ApiError extends Error {
  constructor(status, message, { data = null, headers = {} } = {}) {
    super(message);
    this.status = status;
    this.data = data;
    this.headers = headers;
  }
}

function fail(status, message, options) {
  throw new ApiError(status, message, options);
}

function logSafe(level, fields) {
  const line = { t: new Date().toISOString(), level, ...fields };
  const text = JSON.stringify(line);
  if (level === 'error') console.error(text);
  else console.log(text);
}

function corsHeaders(req) {
  const origin = req.headers.origin;
  if (!origin || !CORS_ORIGINS.includes(origin)) return {};
  return {
    'access-control-allow-origin': origin,
    'access-control-allow-methods': 'GET, POST, PATCH, DELETE, OPTIONS',
    'access-control-allow-headers': 'content-type, token, authorization, idempotency-key',
    'access-control-max-age': '600',
    vary: 'Origin',
  };
}

function send(res, req, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    ...corsHeaders(req),
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    ...extraHeaders,
  });
  res.end(body);
}

function ok(res, req, data, status = 200) {
  send(res, req, status, { code: 200, msg: 'ok', data });
}

function sendError(res, req, status, message, { data = null, headers = {} } = {}) {
  send(res, req, status, { code: status, msg: message, data }, headers);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let aborted = false;
    req.on('data', (chunk) => {
      if (aborted) return;
      size += chunk.length;
      if (size > limit) {
        aborted = true;
        reject(new ApiError(413, '请求体过大'));
        req.resume(); // drain the rest so the 413 response can still be written
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (!aborted) resolve(Buffer.concat(chunks).toString('utf8'));
    });
    req.on('error', () => {
      if (!aborted) reject(new ApiError(400, '读取请求体失败'));
    });
  });
}

async function readJson(req, limit) {
  const raw = await readBody(req, limit);
  if (!raw.trim()) return {};
  const type = String(req.headers['content-type'] || '');
  if (type && !type.includes('application/json')) fail(415, '仅接受 application/json');
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    fail(400, '请求体不是合法 JSON');
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    fail(400, '请求体必须是 JSON 对象');
  }
  return parsed;
}

// ----- validation --------------------------------------------------------

function asString(value, field, maxLength, { allowEmpty = false, required = false } = {}) {
  if (value === undefined || value === null) {
    if (required) fail(400, `参数不合法：${field}`);
    return undefined;
  }
  if (typeof value !== 'string') fail(400, `参数不合法：${field}`);
  const text = value.trim();
  if (!allowEmpty && text.length === 0) fail(400, `参数不合法：${field}`);
  if (text.length > maxLength) fail(400, `参数不合法：${field} 超过 ${maxLength} 字符`);
  return text;
}

function asUsername(value) {
  const text = asString(value, 'username', 32, { required: true });
  if (!USERNAME_RE.test(text)) {
    fail(400, '用户名需为 3–32 位字母、数字、点、下划线或连字符');
  }
  return text;
}

function asPassword(value, field = 'password') {
  if (typeof value !== 'string') fail(400, `参数不合法：${field}`);
  if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX) {
    fail(400, `密码长度需为 ${PASSWORD_MIN}–${PASSWORD_MAX} 位`);
  }
  if (value.trim().length === 0) fail(400, '密码不能全为空白字符');
  return value;
}

function asInt(value, field, { min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER, fallback } = {}) {
  if (value === undefined || value === null || value === '') {
    if (fallback !== undefined) return fallback;
    fail(400, `参数不合法：${field}`);
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) fail(400, `参数不合法：${field}`);
  return parsed;
}

function parseQueryInt(raw, field, { min = 0, max = Number.MAX_SAFE_INTEGER, fallback } = {}) {
  if (raw === null || raw === undefined || raw === '') return fallback;
  return asInt(raw, field, { min, max, fallback });
}

function clientIp(req) {
  return req.socket.remoteAddress || 'unknown';
}

function guard(key, limit, windowMs) {
  if (!RATE_LIMIT_ENABLED) return;
  const result = limiter.hit(key, limit, windowMs);
  if (!result.allowed) {
    fail(429, '请求过于频繁，请稍后再试', { headers: { 'retry-after': String(result.retryAfter) } });
  }
}

// ----- auth --------------------------------------------------------------

function extractToken(req) {
  const header = req.headers.token;
  if (typeof header === 'string' && header.trim()) return header.trim();
  const auth = req.headers.authorization;
  if (typeof auth === 'string' && auth.toLowerCase().startsWith('bearer ')) return auth.slice(7).trim();
  return null;
}

function requireAuth(req, ctx) {
  const token = extractToken(req);
  if (!token) fail(401, 'token 无效或已过期');
  const found = store.findSessionByTokenHash(hashToken(token, pepper.pepper));
  if (!found) fail(401, 'token 无效或已过期');
  if (found.user.status !== 'active') fail(401, '账号已停用');
  if (Date.parse(found.session.expires_at) <= Date.now()) {
    store.revokeSession(found.user.id, found.session.id);
    fail(401, 'token 无效或已过期');
  }
  const lastUsed = found.session.last_used_at ? Date.parse(found.session.last_used_at) : 0;
  if (Date.now() - lastUsed > 120_000) store.touchSession(found.session.id);
  ctx.userId = found.user.id;
  ctx.sessionId = found.session.id;
  return found;
}

function publicUser(user) {
  return {
    id: user.id,
    // Numeric id: the original CollectPointModel.userId is a Long, so favorites
    // echo this value while ownership is enforced through the text id/token.
    uid: user.uid,
    username: user.username,
    display_name: user.display_name,
    phone: user.phone,
    avatar: user.avatar,
    created_at: user.created_at,
    last_login_at: user.last_login_at,
  };
}

function issueSession(user, req, device) {
  const token = generateToken();
  const expiresAt = new Date(Date.now() + TOKEN_TTL_DAYS * 24 * 60 * 60 * 1000).toISOString();
  const session = store.createSession({
    userId: user.id,
    tokenHash: hashToken(token, pepper.pepper),
    device: typeof device === 'string' ? device.slice(0, 64) : null,
    expiresAt,
  });
  store.trimSessions(user.id, MAX_SESSIONS_PER_USER);
  return { token, session };
}

// ----- request handler ---------------------------------------------------

async function route(req, res, ctx) {
  const url = new URL(req.url, 'http://localhost');
  const pathname = url.pathname.replace(/\/+$/, '') || '/';
  const method = req.method || 'GET';
  ctx.path = pathname;

  if (method === 'OPTIONS') {
    res.writeHead(204, corsHeaders(req));
    res.end();
    return;
  }

  if (method === 'GET' && pathname === '/health') {
    const health = store.health();
    send(res, req, 200, {
      status: health.db === 'ok' ? 'ok' : 'degraded',
      service: SERVICE,
      version: VERSION,
      time: new Date().toISOString(),
      uptime_s: Math.round((Date.now() - startedAt) / 1000),
      db: health.db,
      schema: health.schema,
      users: health.users,
      sessions: health.sessions,
      favorites: health.favorites,
      feedback: health.feedback,
      cases: health.cases,
      shares: health.shares,
    });
    return;
  }

  if (method === 'GET' && pathname === '/') {
    send(res, req, 200, {
      service: SERVICE,
      version: VERSION,
      api: '/v2',
      docs: 'backend/api.md',
      health: '/health',
    });
    return;
  }

  if (pathname === '/v1' || pathname.startsWith('/v1/')) {
    sendError(res, req, 410, 'deprecated', {
      data: { replacement: pathname === '/v1/health' ? '/health' : '/v2', doc: 'backend/api.md' },
    });
    return;
  }

  // ----- auth ------------------------------------------------------------
  if (pathname === '/v2/auth/register' && method === 'POST') {
    guard(`register:ip:${clientIp(req)}`, RATE_LIMITS.registerIp.limit, RATE_LIMITS.registerIp.windowMs);
    const body = await readJson(req, LIMIT_AUTH_BYTES);
    const username = asUsername(body.username);
    const password = asPassword(body.password);
    const displayName = asString(body.display_name, 'display_name', 32, { allowEmpty: true }) ?? null;
    const usernameLc = username.toLowerCase();
    if (store.getUserByUsernameLc(usernameLc)) fail(409, '用户名已被占用');
    const passwordHash = await hashPassword(password);
    let user;
    try {
      user = store.createUser({ username, usernameLc, displayName, passwordHash });
    } catch (error) {
      if (String(error.message).includes('UNIQUE')) fail(409, '用户名已被占用');
      throw error;
    }
    const { token, session } = issueSession(user, req, body.device);
    ok(res, req, { user: publicUser(user), token, expires_at: session.expires_at }, 201);
    return;
  }

  if (pathname === '/v2/auth/login' && method === 'POST') {
    const ip = clientIp(req);
    guard(`login:ip:${ip}`, RATE_LIMITS.loginIp.limit, RATE_LIMITS.loginIp.windowMs);
    guard(`login:iphour:${ip}`, RATE_LIMITS.loginIpHour.limit, RATE_LIMITS.loginIpHour.windowMs);
    const body = await readJson(req, LIMIT_AUTH_BYTES);
    const rawUsername = asString(body.username, 'username', 64, { required: true });
    const password = asPassword(body.password);
    const usernameLc = rawUsername.toLowerCase();
    guard(`login:user:${usernameLc}`, RATE_LIMITS.loginUser.limit, RATE_LIMITS.loginUser.windowMs);
    const user = store.getUserByUsernameLc(usernameLc);
    if (!user) {
      await burnPasswordTime(password, dummyHash);
      fail(401, '账号或密码不正确');
    }
    const passwordHash = store.getPasswordHash(user.id);
    const valid = await verifyPassword(password, passwordHash);
    if (!valid) {
      store.recordFailedLogin(user.id);
      fail(401, '账号或密码不正确');
    }
    store.touchLogin(user.id);
    const refreshed = store.getUserById(user.id);
    const { token, session } = issueSession(refreshed, req, body.device);
    ok(res, req, { user: publicUser(refreshed), token, expires_at: session.expires_at });
    return;
  }

  if (pathname === '/v2/auth/logout' && method === 'POST') {
    const auth = requireAuth(req, ctx);
    store.revokeSession(auth.user.id, auth.session.id);
    ok(res, req, { revoked: true });
    return;
  }

  if (pathname === '/v2/auth/logout-all' && method === 'POST') {
    const auth = requireAuth(req, ctx);
    const revoked = store.revokeAllSessions(auth.user.id);
    ok(res, req, { revoked, count: revoked });
    return;
  }

  if (pathname === '/v2/auth/password' && method === 'POST') {
    const auth = requireAuth(req, ctx);
    guard(`password:user:${auth.user.id}`, RATE_LIMITS.passwordUser.limit, RATE_LIMITS.passwordUser.windowMs);
    const body = await readJson(req, LIMIT_AUTH_BYTES);
    const current = asPassword(body.current_password, 'current_password');
    const next = asPassword(body.new_password, 'new_password');
    if (current === next) fail(400, '新密码不能与当前密码相同');
    const stored = store.getPasswordHash(auth.user.id);
    if (!(await verifyPassword(current, stored))) fail(401, '当前密码不正确');
    store.updatePassword(auth.user.id, await hashPassword(next));
    const revoked = store.revokeAllSessions(auth.user.id, { exceptSessionId: auth.session.id });
    ok(res, req, { changed: true, revoked_sessions: revoked });
    return;
  }

  if (pathname === '/v2/auth/me' && method === 'GET') {
    const auth = requireAuth(req, ctx);
    ok(res, req, { user: publicUser(auth.user) });
    return;
  }

  if (pathname === '/v2/auth/me' && method === 'PATCH') {
    const auth = requireAuth(req, ctx);
    const body = await readJson(req, LIMIT_AUTH_BYTES);
    const displayName = asString(body.display_name, 'display_name', 32, { allowEmpty: true }) ?? null;
    ok(res, req, { user: publicUser(store.updateDisplayName(auth.user.id, displayName)) });
    return;
  }

  if (pathname === '/v2/auth/sessions' && method === 'GET') {
    const auth = requireAuth(req, ctx);
    const items = store.listSessions(auth.user.id, auth.session.id);
    ok(res, req, { items, count: items.length });
    return;
  }

  const sessionMatch = pathname.match(/^\/v2\/auth\/sessions\/([^/]+)$/);
  if (sessionMatch) {
    if (method !== 'DELETE') fail(405, '方法不支持');
    const auth = requireAuth(req, ctx);
    const sessionId = decodeURIComponent(sessionMatch[1]);
    if (!store.revokeSession(auth.user.id, sessionId)) fail(404, '会话不存在');
    ok(res, req, { revoked: true, id: sessionId });
    return;
  }

  // ----- favorites (original CollectPointModel) --------------------------
  if (pathname === '/v2/userCollectPoint/list' && method === 'GET') {
    const auth = requireAuth(req, ctx);
    const since = parseQueryInt(url.searchParams.get('since'), 'since', { fallback: 0 });
    const limit = parseQueryInt(url.searchParams.get('limit'), 'limit', { min: 1, max: 200, fallback: 100 });
    const pageNum = parseQueryInt(url.searchParams.get('pageNum'), 'pageNum', { min: 1, fallback: 1 });
    const overlayRaw = url.searchParams.get('overlay');
    let overlay = null;
    if (overlayRaw !== null && overlayRaw !== '') {
      if (['1', 'true'].includes(overlayRaw.toLowerCase())) overlay = 1;
      else if (['0', 'false'].includes(overlayRaw.toLowerCase())) overlay = 0;
      else fail(400, '参数不合法：overlay');
    }
    const offset = since > 0 ? 0 : (pageNum - 1) * limit;
    const items = store.listFavorites(auth.user.id, auth.user.uid, { sinceRev: since, limit, overlay, offset });
    ok(res, req, {
      items,
      count: items.length,
      cursor: items.length > 0 ? items[items.length - 1].rev : since,
    });
    return;
  }

  if (pathname === '/v2/userCollectPoint/save' && method === 'POST') {
    const auth = requireAuth(req, ctx);
    const body = await readJson(req, LIMIT_BUSINESS_BYTES);
    const fields = normalizeFavorite(body);
    if (body.id !== undefined && body.id !== null && body.id !== '') {
      const id = asInt(body.id, 'id', { min: 1 });
      const existing = store.getFavorite(auth.user.id, auth.user.uid, id);
      if (!existing) fail(404, '收藏点不存在');
      if (body.rev !== undefined && body.rev !== null) {
        const clientRev = asInt(body.rev, 'rev', { min: 0 });
        if (clientRev !== existing.rev) {
          fail(409, '版本冲突', { data: { server: existing } });
        }
      }
      ok(res, req, { point: store.updateFavorite(auth.user.id, auth.user.uid, id, fields) });
      return;
    }
    ok(res, req, { point: store.createFavorite(auth.user.id, auth.user.uid, fields) }, 201);
    return;
  }

  if (pathname === '/v2/userCollectPoint/delete' && method === 'DELETE') {
    const auth = requireAuth(req, ctx);
    const id = asInt(url.searchParams.get('id'), 'id', { min: 1 });
    if (!store.deleteFavorite(auth.user.id, id)) fail(404, '收藏点不存在');
    ok(res, req, { deleted: true, id });
    return;
  }

  // ----- feedback (original AddFeedbackReqModel) -------------------------
  if (pathname === '/v2/feedback/add' && method === 'POST') {
    const auth = requireAuth(req, ctx);
    guard(`feedback:user:${auth.user.id}`, RATE_LIMITS.feedbackUser.limit, RATE_LIMITS.feedbackUser.windowMs);
    const body = await readJson(req, LIMIT_BUSINESS_BYTES);
    const type = asInt(body.type, 'type', { fallback: FEEDBACK_TYPE_KNOWN });
    if (type !== FEEDBACK_TYPE_KNOWN) {
      fail(400, `type 仅支持 ${FEEDBACK_TYPE_KNOWN}（原版其余取值域未取证）`);
    }
    const attachments = body.attachments;
    if (attachments !== undefined && attachments !== null && attachments !== '') {
      fail(400, 'attachments 本轮仅支持空值（图片上传属 DI）');
    }
    const idemKey = typeof req.headers['idempotency-key'] === 'string'
      ? req.headers['idempotency-key'].trim().slice(0, 128) || null
      : null;
    if (idemKey) {
      const existing = store.findFeedbackByIdem(auth.user.id, idemKey);
      if (existing) {
        ok(res, req, { feedback: existing, deduplicated: true });
        return;
      }
    }
    const fields = {
      attachments: null,
      contactWay: asString(body.contactWay, 'contactWay', 100, { allowEmpty: true }) ?? null,
      orderNum: asString(body.orderNum, 'orderNum', 64, { allowEmpty: true }) ?? null,
      reasons: asString(body.reasons, 'reasons', 500, { required: true }),
      remark: asString(body.remark, 'remark', 500, { allowEmpty: true }) ?? '',
      type,
    };
    const created = store.createFeedback(auth.user.id, fields, idemKey);
    ok(res, req, { feedback: created.feedback, deduplicated: created.deduplicated }, 201);
    return;
  }

  if (pathname === '/v2/feedback/list' && method === 'GET') {
    const auth = requireAuth(req, ctx);
    const limit = parseQueryInt(url.searchParams.get('limit'), 'limit', { min: 1, max: 100, fallback: 20 });
    const items = store.listFeedback(auth.user.id, limit);
    ok(res, req, { items, count: items.length });
    return;
  }

  fail(404, '资源不存在');
}

function normalizeFavorite(body) {
  const fields = {};
  const strings = [
    ['name', 200], ['address', 500], ['des', 5000], ['images', 20000], ['icon', 64], ['style', 64],
  ];
  for (const [field, max] of strings) {
    if (body[field] !== undefined) {
      fields[field] = asString(body[field], field, max, { allowEmpty: true }) ?? null;
    }
  }
  if (body.latitude !== undefined && body.latitude !== null) {
    if (typeof body.latitude !== 'number' || body.latitude < -90 || body.latitude > 90) {
      fail(400, '参数不合法：latitude');
    }
    fields.latitude = body.latitude;
  }
  if (body.longitude !== undefined && body.longitude !== null) {
    if (typeof body.longitude !== 'number' || body.longitude < -180 || body.longitude > 180) {
      fail(400, '参数不合法：longitude');
    }
    fields.longitude = body.longitude;
  }
  if (body.overlay !== undefined && body.overlay !== null) {
    fields.overlay = asInt(body.overlay, 'overlay', { min: -2_147_483_648, max: 2_147_483_647 });
  }
  if (body.dbId !== undefined && body.dbId !== null) {
    fields.dbId = asInt(body.dbId, 'dbId', { min: -2_147_483_648, max: 2_147_483_647 });
  }
  if (body.createTime !== undefined && body.createTime !== null) {
    const text = asString(body.createTime, 'createTime', 64);
    if (Number.isNaN(Date.parse(text))) fail(400, '参数不合法：createTime');
    fields.createTime = text;
  }
  return fields;
}

const server = http.createServer((req, res) => {
  const started = Date.now();
  const ctx = { requestId: randomUUID(), userId: null, path: null };
  res.setHeader('x-request-id', ctx.requestId);

  res.on('finish', () => {
    logSafe('info', {
      event: 'http',
      rid: ctx.requestId,
      method: req.method,
      path: ctx.path, // never the query string: tokens must not reach logs
      status: res.statusCode,
      ms: Date.now() - started,
      user: ctx.userId,
    });
  });

  route(req, res, ctx).catch((error) => {
    if (res.headersSent) {
      res.end();
      return;
    }
    if (error instanceof ApiError) {
      sendError(res, req, error.status, error.message, { data: error.data, headers: error.headers });
      return;
    }
    logSafe('error', {
      event: 'unhandled',
      rid: ctx.requestId,
      method: req.method,
      path: ctx.path,
      error: error.name,
      message: String(error.message).slice(0, 300),
    });
    sendError(res, req, 500, '服务端内部错误');
  });
});

server.listen(PORT, HOST, () => {
  logSafe('info', {
    event: 'listening',
    service: SERVICE,
    version: VERSION,
    host: HOST,
    port: PORT,
    db: DB_PATH,
    schema: store.appliedMigrations,
    pepper_source: pepper.source,
    rate_limit: RATE_LIMIT_ENABLED,
    token_ttl_days: TOKEN_TTL_DAYS,
    cors_origins: CORS_ORIGINS.length,
  });
});

function shutdown(signal) {
  logSafe('info', { event: 'shutdown', signal });
  server.close(() => {
    try { store.db.close(); } catch { /* ignore */ }
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 5000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
