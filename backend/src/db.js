/**
 * SQLite storage — LC backend v2.
 *
 * Schema is managed by idempotent migrations recorded in `schema_migrations`.
 * 001 is the v1 schema (cases/settings/shares/changes), kept verbatim so an
 * existing v1 volume upgrades in place; 002-005 add the account system.
 *
 * Node 22 built-in `node:sqlite` — no npm dependencies, host stays source-only.
 */
import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';

const V1_SCHEMA = `
CREATE TABLE IF NOT EXISTS meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS cases (
  id            TEXT PRIMARY KEY,
  owner_id      TEXT NOT NULL,
  title         TEXT NOT NULL,
  sit_degree    REAL,
  facing_degree REAL,
  compass_type  TEXT,
  location      TEXT,
  tags          TEXT,
  notes         TEXT,
  data          TEXT,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  rev           INTEGER NOT NULL,
  deleted       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_cases_owner_rev ON cases(owner_id, rev);
CREATE TABLE IF NOT EXISTS settings (
  owner_id   TEXT PRIMARY KEY,
  payload    TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  rev        INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS shares (
  token      TEXT PRIMARY KEY,
  case_id    TEXT NOT NULL,
  owner_id   TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT,
  revoked    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_shares_case ON shares(case_id);
CREATE TABLE IF NOT EXISTS changes (
  seq        INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_id   TEXT NOT NULL,
  entity     TEXT NOT NULL,
  entity_id  TEXT NOT NULL,
  op         TEXT NOT NULL,
  payload    TEXT,
  rev        INTEGER NOT NULL,
  changed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_changes_owner_seq ON changes(owner_id, seq);
INSERT OR IGNORE INTO meta (key, value) VALUES ('rev', '0');
`;

const USERS_SESSIONS_SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  uid             INTEGER PRIMARY KEY AUTOINCREMENT,
  id              TEXT NOT NULL UNIQUE,
  username        TEXT NOT NULL,
  username_lc     TEXT NOT NULL UNIQUE,
  display_name    TEXT,
  phone           TEXT,
  avatar          TEXT,
  password_hash   TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'active',
  created_at      TEXT NOT NULL,
  last_login_at   TEXT,
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until    TEXT
);
CREATE TABLE IF NOT EXISTS sessions (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash   TEXT NOT NULL UNIQUE,
  device       TEXT,
  created_at   TEXT NOT NULL,
  last_used_at TEXT,
  expires_at   TEXT NOT NULL,
  revoked      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id, created_at);
`;

const FAVORITES_SCHEMA = `
CREATE TABLE IF NOT EXISTS favorites (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT,
  address     TEXT,
  latitude    REAL,
  longitude   REAL,
  des         TEXT,
  images      TEXT,
  icon        TEXT,
  style       TEXT,
  overlay     INTEGER,
  db_id       INTEGER,
  create_time TEXT,
  rev         INTEGER NOT NULL,
  deleted     INTEGER NOT NULL DEFAULT 0,
  updated_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_favorites_user_rev ON favorites(user_id, rev);
CREATE INDEX IF NOT EXISTS idx_favorites_user_live ON favorites(user_id, deleted, rev);
`;

const FEEDBACK_SCHEMA = `
CREATE TABLE IF NOT EXISTS feedback (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  attachments TEXT,
  contact_way TEXT,
  order_num   TEXT,
  reasons     TEXT NOT NULL,
  remark      TEXT,
  type        INTEGER NOT NULL,
  status      TEXT NOT NULL DEFAULT 'open',
  created_at  TEXT NOT NULL,
  idem_key    TEXT
);
CREATE INDEX IF NOT EXISTS idx_feedback_user_created ON feedback(user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_feedback_user_idem ON feedback(user_id, idem_key) WHERE idem_key IS NOT NULL;
`;

const LEGACY_CLAIM_SCHEMA = `
ALTER TABLE cases ADD COLUMN user_id TEXT;
ALTER TABLE settings ADD COLUMN user_id TEXT;
ALTER TABLE shares ADD COLUMN user_id TEXT;
`;

const MIGRATIONS = [
  { id: '001_init', sql: V1_SCHEMA },
  { id: '002_users_sessions', sql: USERS_SESSIONS_SCHEMA },
  { id: '003_favorites', sql: FAVORITES_SCHEMA },
  { id: '004_feedback', sql: FEEDBACK_SCHEMA },
  { id: '005_legacy_user_id', sql: LEGACY_CLAIM_SCHEMA },
];

const INSERT_REV = "UPDATE meta SET value = CAST(value AS INTEGER) + 1 WHERE key = 'rev'";

function nowIso() {
  return new Date().toISOString();
}

function parseJson(text, fallback) {
  if (text === null || text === undefined || text === '') return fallback;
  try {
    return JSON.parse(text);
  } catch {
    return fallback;
  }
}

function rowToCase(row, { includeOwner = true } = {}) {
  if (!row) return null;
  const out = {
    id: row.id,
    title: row.title,
    sit_degree: row.sit_degree,
    facing_degree: row.facing_degree,
    compass_type: row.compass_type,
    location: parseJson(row.location, null),
    tags: parseJson(row.tags, []),
    notes: row.notes,
    data: parseJson(row.data, null),
    created_at: row.created_at,
    updated_at: row.updated_at,
    rev: row.rev,
    deleted: row.deleted === 1,
  };
  if (includeOwner) out.owner_id = row.owner_id;
  return out;
}

function rowToUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    uid: row.uid,
    username: row.username,
    display_name: row.display_name,
    phone: row.phone,
    avatar: row.avatar,
    status: row.status,
    created_at: row.created_at,
    last_login_at: row.last_login_at,
  };
}

function rowToSession(row, { current = false } = {}) {
  if (!row) return null;
  return {
    id: row.id,
    device: row.device,
    created_at: row.created_at,
    last_used_at: row.last_used_at,
    expires_at: row.expires_at,
    current,
  };
}

function rowToFavorite(row, uid) {
  if (!row) return null;
  return {
    id: row.id,
    userId: uid,
    name: row.name,
    address: row.address,
    latitude: row.latitude,
    longitude: row.longitude,
    des: row.des,
    images: row.images,
    icon: row.icon,
    style: row.style,
    overlay: row.overlay,
    dbId: row.db_id,
    createTime: row.create_time,
    // 【我方扩展，原版无此字段】
    rev: row.rev,
    deleted: row.deleted === 1,
    updatedAt: row.updated_at,
  };
}

function rowToFeedback(row) {
  if (!row) return null;
  return {
    id: row.id,
    attachments: row.attachments,
    contactWay: row.contact_way,
    orderNum: row.order_num,
    reasons: row.reasons,
    remark: row.remark,
    type: row.type,
    status: row.status,
    createdAt: row.created_at,
  };
}

export class Store {
  constructor(db, dbPath, appliedMigrations) {
    this.db = db;
    this.dbPath = dbPath;
    this.appliedMigrations = appliedMigrations;
  }

  tx(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const result = fn();
      this.db.exec('COMMIT');
      return result;
    } catch (error) {
      try { this.db.exec('ROLLBACK'); } catch { /* already rolled back */ }
      throw error;
    }
  }

  bumpRev() {
    this.db.prepare(INSERT_REV).run();
    const row = this.db.prepare("SELECT value FROM meta WHERE key = 'rev'").get();
    return Number(row.value);
  }

  recordChange(ownerId, entity, entityId, op, payload, rev) {
    this.db.prepare(
      'INSERT INTO changes (owner_id, entity, entity_id, op, payload, rev, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
    ).run(ownerId, entity, entityId, op, payload === null ? null : JSON.stringify(payload), rev, nowIso());
  }

  // ----- health ----------------------------------------------------------

  health() {
    const one = this.db.prepare('SELECT 1 AS ok').get();
    const counts = this.db.prepare(`
      SELECT
        (SELECT COUNT(*) FROM cases WHERE deleted = 0) AS cases,
        (SELECT COUNT(*) FROM shares WHERE revoked = 0) AS shares,
        (SELECT COUNT(*) FROM users) AS users,
        (SELECT COUNT(*) FROM sessions WHERE revoked = 0 AND expires_at > ?) AS sessions,
        (SELECT COUNT(*) FROM favorites WHERE deleted = 0) AS favorites,
        (SELECT COUNT(*) FROM feedback) AS feedback
    `).get(nowIso());
    return {
      db: one.ok === 1 ? 'ok' : 'error',
      cases: Number(counts.cases),
      shares: Number(counts.shares),
      users: Number(counts.users),
      sessions: Number(counts.sessions),
      favorites: Number(counts.favorites),
      feedback: Number(counts.feedback),
      schema: this.appliedMigrations.length > 0 ? this.appliedMigrations[this.appliedMigrations.length - 1] : null,
    };
  }

  // ----- users -----------------------------------------------------------

  createUser({ username, usernameLc, displayName, passwordHash }) {
    const id = `user_${randomUUID().replace(/-/g, '')}`;
    const now = nowIso();
    this.db.prepare(
      `INSERT INTO users (id, username, username_lc, display_name, password_hash, status, created_at)
       VALUES (?, ?, ?, ?, ?, 'active', ?)`
    ).run(id, username, usernameLc, displayName ?? null, passwordHash, now);
    return this.getUserById(id);
  }

  getUserById(id) {
    return rowToUser(this.db.prepare('SELECT * FROM users WHERE id = ?').get(id));
  }

  getUserByUsernameLc(usernameLc) {
    return rowToUser(this.db.prepare('SELECT * FROM users WHERE username_lc = ?').get(usernameLc));
  }

  getPasswordHash(userId) {
    const row = this.db.prepare('SELECT password_hash FROM users WHERE id = ?').get(userId);
    return row ? row.password_hash : null;
  }

  touchLogin(userId) {
    this.db.prepare('UPDATE users SET last_login_at = ?, failed_attempts = 0 WHERE id = ?').run(nowIso(), userId);
  }

  recordFailedLogin(userId) {
    this.db.prepare('UPDATE users SET failed_attempts = failed_attempts + 1 WHERE id = ?').run(userId);
  }

  updatePassword(userId, passwordHash) {
    this.db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(passwordHash, userId);
  }

  updateDisplayName(userId, displayName) {
    this.db.prepare('UPDATE users SET display_name = ? WHERE id = ?').run(displayName, userId);
    return this.getUserById(userId);
  }

  // ----- sessions --------------------------------------------------------

  createSession({ userId, tokenHash, device, expiresAt }) {
    const id = `sess_${randomUUID().replace(/-/g, '')}`;
    const now = nowIso();
    this.db.prepare(
      `INSERT INTO sessions (id, user_id, token_hash, device, created_at, last_used_at, expires_at, revoked)
       VALUES (?, ?, ?, ?, ?, ?, ?, 0)`
    ).run(id, userId, tokenHash, device ?? null, now, now, expiresAt);
    return this.db.prepare('SELECT * FROM sessions WHERE id = ?').get(id);
  }

  findSessionByTokenHash(tokenHash) {
    const row = this.db.prepare(
      `SELECT s.*, u.id AS u_id, u.uid, u.username, u.display_name, u.phone, u.avatar, u.status, u.created_at AS u_created_at, u.last_login_at
       FROM sessions s JOIN users u ON u.id = s.user_id
       WHERE s.token_hash = ? AND s.revoked = 0`
    ).get(tokenHash);
    if (!row) return null;
    return {
      session: rowToSession(row),
      user: rowToUser({
        uid: row.uid,
        id: row.u_id,
        username: row.username,
        display_name: row.display_name,
        phone: row.phone,
        avatar: row.avatar,
        status: row.status,
        created_at: row.u_created_at,
        last_login_at: row.last_login_at,
      }),
    };
  }

  touchSession(sessionId) {
    this.db.prepare('UPDATE sessions SET last_used_at = ? WHERE id = ?').run(nowIso(), sessionId);
  }

  listSessions(userId, currentSessionId) {
    const rows = this.db.prepare(
      `SELECT * FROM sessions WHERE user_id = ? AND revoked = 0 AND expires_at > ? ORDER BY created_at DESC`
    ).all(userId, nowIso());
    return rows.map((row) => rowToSession(row, { current: row.id === currentSessionId }));
  }

  getSessionForUser(userId, sessionId) {
    return this.db.prepare('SELECT * FROM sessions WHERE id = ? AND user_id = ? AND revoked = 0').get(sessionId) ?? null;
  }

  revokeSession(userId, sessionId) {
    const result = this.db.prepare('UPDATE sessions SET revoked = 1 WHERE id = ? AND user_id = ? AND revoked = 0')
      .run(sessionId, userId);
    return Number(result.changes) > 0;
  }

  revokeAllSessions(userId, { exceptSessionId = null } = {}) {
    const result = exceptSessionId
      ? this.db.prepare('UPDATE sessions SET revoked = 1 WHERE user_id = ? AND revoked = 0 AND id <> ?')
        .run(userId, exceptSessionId)
      : this.db.prepare('UPDATE sessions SET revoked = 1 WHERE user_id = ? AND revoked = 0').run(userId);
    return Number(result.changes);
  }

  /** Keep the newest `limit` active sessions for a user, revoke the rest. */
  trimSessions(userId, limit) {
    const result = this.db.prepare(
      `UPDATE sessions SET revoked = 1
       WHERE user_id = ? AND revoked = 0
         AND id NOT IN (
           SELECT id FROM sessions WHERE user_id = ? AND revoked = 0 ORDER BY created_at DESC LIMIT ?
         )`
    ).run(userId, userId, limit);
    return Number(result.changes);
  }

  // ----- favorites (original CollectPointModel) --------------------------

  getFavorite(userId, uid, id, { includeDeleted = false } = {}) {
    const row = includeDeleted
      ? this.db.prepare('SELECT * FROM favorites WHERE id = ? AND user_id = ?').get(id, userId)
      : this.db.prepare('SELECT * FROM favorites WHERE id = ? AND user_id = ? AND deleted = 0').get(id, userId);
    return rowToFavorite(row, uid);
  }

  listFavorites(userId, uid, { sinceRev = 0, limit = 100, overlay = null, offset = 0 } = {}) {
    const rows = this.db.prepare(
      `SELECT * FROM favorites
       WHERE user_id = ? AND deleted = 0 AND rev > ?
         AND (? IS NULL OR overlay = ?)
       ORDER BY rev ASC LIMIT ? OFFSET ?`
    ).all(userId, sinceRev, overlay, overlay, limit, offset);
    return rows.map((row) => rowToFavorite(row, uid));
  }

  createFavorite(userId, uid, fields) {
    return this.tx(() => {
      const now = nowIso();
      const rev = this.bumpRev();
      const createTime = fields.createTime || now;
      const result = this.db.prepare(
        `INSERT INTO favorites (user_id, name, address, latitude, longitude, des, images, icon, style, overlay, db_id, create_time, rev, deleted, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)`
      ).run(
        userId, fields.name ?? null, fields.address ?? null, fields.latitude ?? null, fields.longitude ?? null,
        fields.des ?? null, fields.images ?? null, fields.icon ?? null, fields.style ?? null,
        fields.overlay ?? 0, fields.dbId ?? null, createTime, rev, now
      );
      return this.getFavorite(userId, uid, Number(result.lastInsertRowid));
    });
  }

  updateFavorite(userId, uid, id, fields) {
    return this.tx(() => {
      const current = this.getFavorite(userId, uid, id);
      if (!current) return null;
      const now = nowIso();
      const rev = this.bumpRev();
      this.db.prepare(
        `UPDATE favorites SET name = ?, address = ?, latitude = ?, longitude = ?, des = ?, images = ?, icon = ?, style = ?,
           overlay = ?, db_id = ?, create_time = ?, rev = ?, updated_at = ?
         WHERE id = ? AND user_id = ? AND deleted = 0`
      ).run(
        fields.name !== undefined ? fields.name : current.name,
        fields.address !== undefined ? fields.address : current.address,
        fields.latitude !== undefined ? fields.latitude : current.latitude,
        fields.longitude !== undefined ? fields.longitude : current.longitude,
        fields.des !== undefined ? fields.des : current.des,
        fields.images !== undefined ? fields.images : current.images,
        fields.icon !== undefined ? fields.icon : current.icon,
        fields.style !== undefined ? fields.style : current.style,
        fields.overlay !== undefined ? fields.overlay : current.overlay,
        fields.dbId !== undefined ? fields.dbId : current.dbId,
        fields.createTime !== undefined ? fields.createTime : current.createTime,
        rev, now, id, userId
      );
      return this.getFavorite(userId, uid, id);
    });
  }

  deleteFavorite(userId, id) {
    return this.tx(() => {
      const current = this.getFavorite(userId, null, id);
      if (!current) return false;
      const rev = this.bumpRev();
      this.db.prepare('UPDATE favorites SET deleted = 1, rev = ?, updated_at = ? WHERE id = ? AND user_id = ?')
        .run(rev, nowIso(), id, userId);
      return true;
    });
  }

  // ----- feedback (original AddFeedbackReqModel) -------------------------

  findFeedbackByIdem(userId, idemKey) {
    const row = this.db.prepare('SELECT * FROM feedback WHERE user_id = ? AND idem_key = ?').get(userId, idemKey);
    return rowToFeedback(row);
  }

  createFeedback(userId, fields, idemKey) {
    const id = `fb_${randomUUID().replace(/-/g, '')}`;
    const now = nowIso();
    try {
      this.db.prepare(
        `INSERT INTO feedback (id, user_id, attachments, contact_way, order_num, reasons, remark, type, status, created_at, idem_key)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'open', ?, ?)`
      ).run(
        id, userId, fields.attachments ?? null, fields.contactWay ?? null, fields.orderNum ?? null,
        fields.reasons, fields.remark ?? '', fields.type, now, idemKey
      );
    } catch (error) {
      if (idemKey && String(error.message).includes('UNIQUE')) {
        const existing = this.findFeedbackByIdem(userId, idemKey);
        if (existing) return { feedback: existing, deduplicated: true };
      }
      throw error;
    }
    return { feedback: rowToFeedback(this.db.prepare('SELECT * FROM feedback WHERE id = ?').get(id)), deduplicated: false };
  }

  listFeedback(userId, limit = 20) {
    const rows = this.db.prepare('SELECT * FROM feedback WHERE user_id = ? ORDER BY created_at DESC LIMIT ?')
      .all(userId, limit);
    return rows.map(rowToFeedback);
  }

  // ----- legacy v1 case library (kept for claim-owner.mjs) ---------------

  getCase(id, ownerId, includeDeleted = false) {
    const row = includeDeleted
      ? this.db.prepare('SELECT * FROM cases WHERE id = ? AND owner_id = ?').get(id, ownerId)
      : this.db.prepare('SELECT * FROM cases WHERE id = ? AND owner_id = ? AND deleted = 0').get(id, ownerId);
    return rowToCase(row);
  }

  listCases(ownerId, { sinceRev = 0, limit = 100, includeDeleted = false } = {}) {
    const rows = this.db.prepare(
      'SELECT * FROM cases WHERE owner_id = ? AND rev > ? AND (? = 1 OR deleted = 0) ORDER BY rev ASC LIMIT ?'
    ).all(ownerId, sinceRev, includeDeleted ? 1 : 0, limit);
    return rows.map((row) => rowToCase(row));
  }

  createCase(ownerId, input, { id, createdAt } = {}) {
    return this.tx(() => {
      const caseId = id || `case_${randomUUID().replace(/-/g, '')}`;
      const now = createdAt || nowIso();
      const rev = this.bumpRev();
      this.db.prepare(
        `INSERT INTO cases (id, owner_id, title, sit_degree, facing_degree, compass_type, location, tags, notes, data, created_at, updated_at, rev, deleted)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`
      ).run(
        caseId, ownerId, input.title,
        input.sit_degree ?? null, input.facing_degree ?? null, input.compass_type ?? null,
        input.location === undefined || input.location === null ? null : JSON.stringify(input.location),
        JSON.stringify(input.tags || []), input.notes ?? null,
        input.data === undefined || input.data === null ? null : JSON.stringify(input.data),
        now, now, rev
      );
      const snapshot = this.getCase(caseId, ownerId);
      this.recordChange(ownerId, 'case', caseId, 'create', snapshot, rev);
      return snapshot;
    });
  }

  getSettings(ownerId) {
    const row = this.db.prepare('SELECT * FROM settings WHERE owner_id = ?').get(ownerId);
    if (!row) return { owner_id: ownerId, settings: {}, rev: 0, updated_at: null };
    return { owner_id: ownerId, settings: parseJson(row.payload, {}), rev: row.rev, updated_at: row.updated_at };
  }

  /** Attach legacy owner_id rows to a user account (explicit, one-off). */
  claimLegacyRows(ownerId, userId) {
    return this.tx(() => {
      const cases = this.db.prepare('UPDATE cases SET user_id = ? WHERE owner_id = ? AND user_id IS NULL').run(userId, ownerId);
      const settings = this.db.prepare('UPDATE settings SET user_id = ? WHERE owner_id = ? AND user_id IS NULL').run(userId, ownerId);
      const shares = this.db.prepare('UPDATE shares SET user_id = ? WHERE owner_id = ? AND user_id IS NULL').run(userId, ownerId);
      return {
        cases: Number(cases.changes),
        settings: Number(settings.changes),
        shares: Number(shares.changes),
      };
    });
  }
}

function runMigrations(db) {
  db.exec(`CREATE TABLE IF NOT EXISTS schema_migrations (
    id         TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL
  );`);
  const applied = new Set(db.prepare('SELECT id FROM schema_migrations').all().map((row) => row.id));
  for (const migration of MIGRATIONS) {
    if (applied.has(migration.id)) continue;
    db.exec('BEGIN IMMEDIATE');
    try {
      db.exec(migration.sql);
      db.prepare('INSERT INTO schema_migrations (id, applied_at) VALUES (?, ?)').run(migration.id, nowIso());
      db.exec('COMMIT');
    } catch (error) {
      try { db.exec('ROLLBACK'); } catch { /* already rolled back */ }
      throw new Error(`migration ${migration.id} failed: ${error.message}`);
    }
  }
  return db.prepare('SELECT id FROM schema_migrations ORDER BY id').all().map((row) => row.id);
}

export function openDb(dbPath) {
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA busy_timeout = 5000');
  db.exec('PRAGMA foreign_keys = ON');
  const appliedMigrations = runMigrations(db);
  return new Store(db, dbPath, appliedMigrations);
}
