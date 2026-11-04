import fs from 'node:fs';
import path from 'node:path';
import { randomBytes, randomUUID } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';

const SCHEMA = `
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
`;

const INSERT_REV = 'UPDATE meta SET value = CAST(value AS INTEGER) + 1 WHERE key = \'rev\'';

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

export class Store {
  constructor(db, dbPath) {
    this.db = db;
    this.dbPath = dbPath;
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
    const row = this.db.prepare('SELECT value FROM meta WHERE key = \'rev\'').get();
    return Number(row.value);
  }

  recordChange(ownerId, entity, entityId, op, payload, rev) {
    this.db.prepare(
      'INSERT INTO changes (owner_id, entity, entity_id, op, payload, rev, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
    ).run(ownerId, entity, entityId, op, payload === null ? null : JSON.stringify(payload), rev, nowIso());
  }

  health() {
    const row = this.db.prepare('SELECT 1 AS ok').get();
    const cases = this.db.prepare('SELECT COUNT(*) AS n FROM cases WHERE deleted = 0').get();
    const shares = this.db.prepare('SELECT COUNT(*) AS n FROM shares WHERE revoked = 0').get();
    return { db: row.ok === 1 ? 'ok' : 'error', cases: Number(cases.n), shares: Number(shares.n) };
  }

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

  updateCase(ownerId, id, patch) {
    return this.tx(() => {
      const current = this.getCase(id, ownerId, true);
      if (!current || current.deleted) return null;

      const next = {
        title: patch.title !== undefined ? patch.title : current.title,
        sit_degree: patch.sit_degree !== undefined ? patch.sit_degree : current.sit_degree,
        facing_degree: patch.facing_degree !== undefined ? patch.facing_degree : current.facing_degree,
        compass_type: patch.compass_type !== undefined ? patch.compass_type : current.compass_type,
        location: patch.location !== undefined ? patch.location : current.location,
        tags: patch.tags !== undefined ? patch.tags : current.tags,
        notes: patch.notes !== undefined ? patch.notes : current.notes,
        data: patch.data !== undefined ? patch.data : current.data,
      };
      const now = nowIso();
      const rev = this.bumpRev();
      this.db.prepare(
        `UPDATE cases SET title = ?, sit_degree = ?, facing_degree = ?, compass_type = ?, location = ?, tags = ?, notes = ?, data = ?, updated_at = ?, rev = ?
         WHERE id = ? AND owner_id = ? AND deleted = 0`
      ).run(
        next.title,
        next.sit_degree ?? null,
        next.facing_degree ?? null,
        next.compass_type ?? null,
        next.location === null ? null : JSON.stringify(next.location),
        JSON.stringify(next.tags || []),
        next.notes ?? null,
        next.data === null ? null : JSON.stringify(next.data),
        now, rev, id, ownerId
      );
      const snapshot = this.getCase(id, ownerId);
      this.recordChange(ownerId, 'case', id, 'update', snapshot, rev);
      return snapshot;
    });
  }

  deleteCase(ownerId, id) {
    return this.tx(() => {
      const current = this.getCase(id, ownerId);
      if (!current) return false;
      const rev = this.bumpRev();
      this.db.prepare('UPDATE cases SET deleted = 1, updated_at = ?, rev = ? WHERE id = ? AND owner_id = ?')
        .run(nowIso(), rev, id, ownerId);
      this.recordChange(ownerId, 'case', id, 'delete', null, rev);
      return true;
    });
  }

  getSettings(ownerId) {
    const row = this.db.prepare('SELECT * FROM settings WHERE owner_id = ?').get(ownerId);
    if (!row) return { owner_id: ownerId, settings: {}, rev: 0, updated_at: null };
    return {
      owner_id: ownerId,
      settings: parseJson(row.payload, {}),
      rev: row.rev,
      updated_at: row.updated_at,
    };
  }

  putSettings(ownerId, settings) {
    return this.tx(() => {
      const now = nowIso();
      const rev = this.bumpRev();
      this.db.prepare(
        `INSERT INTO settings (owner_id, payload, updated_at, rev) VALUES (?, ?, ?, ?)
         ON CONFLICT(owner_id) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at, rev = excluded.rev`
      ).run(ownerId, JSON.stringify(settings), now, rev);
      this.recordChange(ownerId, 'settings', ownerId, 'update', { settings, rev }, rev);
      return { owner_id: ownerId, settings, rev, updated_at: now };
    });
  }

  createShare(ownerId, caseId, ttlDays) {
    const existing = this.getCase(caseId, ownerId);
    if (!existing) return null;
    const token = randomBytes(16).toString('hex');
    const created = nowIso();
    const expires = new Date(Date.now() + ttlDays * 24 * 60 * 60 * 1000).toISOString();
    this.db.prepare(
      'INSERT INTO shares (token, case_id, owner_id, created_at, expires_at, revoked) VALUES (?, ?, ?, ?, ?, 0)'
    ).run(token, caseId, ownerId, created, expires);
    return { token, case_id: caseId, created_at: created, expires_at: expires };
  }

  getShare(token) {
    const row = this.db.prepare('SELECT * FROM shares WHERE token = ?').get(token);
    if (!row || row.revoked === 1) return null;
    if (row.expires_at && Date.parse(row.expires_at) < Date.now()) return null;
    const shared = this.getCase(row.case_id, row.owner_id);
    if (!shared) return null;
    return {
      token: row.token,
      created_at: row.created_at,
      expires_at: row.expires_at,
      case: rowToCase(this.db.prepare('SELECT * FROM cases WHERE id = ?').get(row.case_id), { includeOwner: false }),
    };
  }

  revokeShare(ownerId, token) {
    const result = this.db.prepare('UPDATE shares SET revoked = 1 WHERE token = ? AND owner_id = ?').run(token, ownerId);
    return Number(result.changes) > 0;
  }

  pull(ownerId, sinceSeq, limit = 200) {
    const rows = this.db.prepare(
      'SELECT seq, entity, entity_id, op, payload, rev, changed_at FROM changes WHERE owner_id = ? AND seq > ? ORDER BY seq ASC LIMIT ?'
    ).all(ownerId, sinceSeq, limit);
    const changes = rows.map((row) => ({
      seq: row.seq,
      entity: row.entity,
      entity_id: row.entity_id,
      op: row.op,
      payload: parseJson(row.payload, null),
      rev: row.rev,
      changed_at: row.changed_at,
    }));
    return {
      cursor: changes.length ? changes[changes.length - 1].seq : sinceSeq,
      changes,
    };
  }
}

export function openDb(dbPath) {
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA busy_timeout = 5000');
  db.exec('PRAGMA foreign_keys = ON');
  db.exec(SCHEMA);
  db.prepare('INSERT OR IGNORE INTO meta (key, value) VALUES (\'rev\', \'0\')').run();
  return new Store(db, dbPath);
}
