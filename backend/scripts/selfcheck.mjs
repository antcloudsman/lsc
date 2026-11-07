#!/usr/bin/env node
/**
 * LC backend v2 self-check.
 *
 * Runs inside the container (or on any host that has Node 22) against a live
 * server and exercises the account chains from backend/api.md v2:
 *   register -> login -> me -> sessions -> password -> favorites -> feedback -> logout
 * plus expiry, authorization isolation, rate limiting and the /v1 -> 410 switch.
 *
 * Usage: docker compose exec api node --no-warnings scripts/selfcheck.mjs
 *        BASE_URL=http://127.0.0.1:8787 node --no-warnings scripts/selfcheck.mjs
 * Exit code 0 = PASS, 1 = FAIL. Tokens are never printed.
 */
import { DatabaseSync } from 'node:sqlite';

const BASE = (process.env.BASE_URL || 'http://127.0.0.1:8787').replace(/\/+$/, '');
const DB_PATH = process.env.DB_PATH || '/data/lc.sqlite';
const stamp = Date.now().toString(36);

let checks = 0;
let failures = 0;

function pass(message) {
  checks += 1;
  console.log(`[PASS] ${message}`);
}

function fail(message) {
  checks += 1;
  failures += 1;
  console.error(`[FAIL] ${message}`);
}

function check(condition, message) {
  if (condition) pass(message);
  else fail(message);
}

async function call(method, pathname, { body, token, headers = {}, raw } = {}) {
  const merged = { ...headers };
  if (body !== undefined || raw !== undefined) merged['content-type'] = merged['content-type'] || 'application/json';
  if (token) merged.token = token;
  const res = await fetch(BASE + pathname, {
    method,
    headers: merged,
    body: raw !== undefined ? raw : body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-JSON */ }
  return { status: res.status, json, text, headers: res.headers };
}

const envelopeOk = (res, status = 200) =>
  res.status === status && res.json?.code === 200 && res.json?.msg === 'ok';

const envelopeError = (res, status) =>
  res.status === status && res.json?.code === status && typeof res.json?.msg === 'string' && res.json?.data === null;

async function register(username, password, displayName) {
  return call('POST', '/v2/auth/register', { body: { username, password, display_name: displayName } });
}

async function main() {
  console.log(`LC backend v2 self-check against ${BASE} (run ${stamp})`);

  // ---- 0. health / envelope --------------------------------------------
  const health = await call('GET', '/health');
  check(health.status === 200 && health.json?.status === 'ok', 'GET /health -> 200 status=ok');
  check(health.json?.db === 'ok', 'health.db == ok');
  check(typeof health.json?.schema === 'string' && health.json.schema.startsWith('00'), `schema migrated (${health.json?.schema})`);

  const root = await call('GET', '/');
  check(root.status === 200 && root.json?.api === '/v2', 'GET / advertises api=/v2');

  // ---- 1. register -----------------------------------------------------
  const userA = `lc${stamp}a`;
  const passA = 'SelfCheck-A-1';
  const regA = await register(userA, passA, '自检甲');
  check(envelopeOk(regA, 201), `register ${userA} -> 201 envelope code=200`);
  check(typeof regA.json?.data?.token === 'string' && regA.json.data.token.length === 43, 'register returns a 43-char opaque token');
  check(regA.json?.data?.user?.username === userA, 'register returns the user');
  check(regA.json?.data?.user?.password_hash === undefined, 'user payload never exposes password_hash');
  check(typeof regA.json?.data?.expires_at === 'string', 'register returns expires_at');
  let tokenA1 = regA.json?.data?.token;

  const dup = await register(userA.toUpperCase(), 'SelfCheck-A-2');
  check(envelopeError(dup, 409), 'duplicate username (case-insensitive) -> 409');
  const weak = await register(`lc${stamp}weak`, 'short');
  check(envelopeError(weak, 400), 'password shorter than 8 -> 400');
  const badName = await register('ab', 'SelfCheck-A-3');
  check(envelopeError(badName, 400), 'username shorter than 3 -> 400');

  // ---- 2. login --------------------------------------------------------
  const login1 = await call('POST', '/v2/auth/login', { body: { username: userA, password: passA } });
  check(envelopeOk(login1), 'login with correct password -> 200');
  tokenA1 = login1.json?.data?.token ?? tokenA1;
  const wrong = await call('POST', '/v2/auth/login', { body: { username: userA, password: 'Wrong-Pass-1' } });
  check(envelopeError(wrong, 401), 'login with wrong password -> 401');
  check(typeof wrong.json?.msg === 'string' && wrong.json.msg.length > 0, 'error envelope carries a msg');

  const noToken = await call('GET', '/v2/auth/me');
  check(envelopeError(noToken, 401), 'GET /v2/auth/me without token -> 401');
  const badToken = await call('GET', '/v2/auth/me', { token: 'x'.repeat(43) });
  check(envelopeError(badToken, 401), 'GET /v2/auth/me with unknown token -> 401');

  const me = await call('GET', '/v2/auth/me', { token: tokenA1 });
  check(envelopeOk(me) && me.json?.data?.user?.username === userA, 'GET /v2/auth/me returns the current user');
  check(me.json?.data?.user?.uid > 0, 'user.uid is a positive integer (original CollectPointModel userId type)');

  const patched = await call('PATCH', '/v2/auth/me', { token: tokenA1, body: { display_name: '自检甲·改' } });
  check(envelopeOk(patched) && patched.json?.data?.user?.display_name === '自检甲·改', 'PATCH /v2/auth/me updates display_name');

  // ---- 3. sessions -----------------------------------------------------
  const login2 = await call('POST', '/v2/auth/login', { body: { username: userA, password: passA } });
  const tokenA2 = login2.json?.data?.token;
  check(envelopeOk(login2) && typeof tokenA2 === 'string', 'second login issues a second session');
  const sessions = await call('GET', '/v2/auth/sessions', { token: tokenA1 });
  // register + two logins = three sessions (each issuance is a session)
  check(envelopeOk(sessions) && sessions.json?.data?.count === 3, 'session list shows 3 active sessions (register + 2 logins)');
  check(sessions.json?.data?.items?.every((s) => s.token === undefined), 'session list never returns tokens');
  const current = sessions.json?.data?.items?.find((s) => s.current === true);
  check(Boolean(current), 'session list marks the current session');
  const target = sessions.json?.data?.items?.find((s) => s.id !== current?.id);
  const revokedOther = await call('DELETE', `/v2/auth/sessions/${encodeURIComponent(target?.id ?? 'sess_missing')}`, { token: tokenA1 });
  check(envelopeOk(revokedOther), 'DELETE /v2/auth/sessions/{id} revokes a session');
  const deadA2 = await call('GET', '/v2/auth/me', { token: tokenA2 });
  check(envelopeError(deadA2, 401), 'revoked session token -> 401');

  // ---- 4. password change ---------------------------------------------
  const badCurrent = await call('POST', '/v2/auth/password', {
    token: tokenA1, body: { current_password: 'Wrong-Current-1', new_password: 'SelfCheck-A-9' },
  });
  check(envelopeError(badCurrent, 401), 'password change with wrong current password -> 401');
  const passA2 = 'SelfCheck-A-2';
  const changed = await call('POST', '/v2/auth/password', {
    token: tokenA1, body: { current_password: passA, new_password: passA2 },
  });
  check(envelopeOk(changed) && changed.json?.data?.changed === true, 'password change -> 200 changed=true');
  const oldPass = await call('POST', '/v2/auth/login', { body: { username: userA, password: passA } });
  check(envelopeError(oldPass, 401), 'login with the old password -> 401');
  const newPass = await call('POST', '/v2/auth/login', { body: { username: userA, password: passA2 } });
  check(envelopeOk(newPass), 'login with the new password -> 200');
  let tokenA3 = newPass.json?.data?.token;
  check(typeof tokenA3 === 'string', 'post-change login issues a token');

  // ---- 5. favorites ----------------------------------------------------
  const p1 = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3,
    body: {
      name: '自检·西湖', address: '杭州市西湖区', latitude: 30.2501, longitude: 120.1502,
      des: '坐向：巽山乾向', images: '[]', icon: 'default', style: '0', overlay: 1, dbId: 12,
      userId: 999999, // must be ignored: the server uses the token's user
    },
  });
  check(envelopeOk(p1, 201), 'POST /v2/userCollectPoint/save creates a point -> 201');
  check(p1.json?.data?.point?.userId !== 999999, 'client-sent userId is ignored (server overrides it)');
  check(p1.json?.data?.point?.name === '自检·西湖' && p1.json?.data?.point?.overlay === 1, 'favorite round-trips original fields');
  const p1id = p1.json?.data?.point?.id;
  const p1rev = p1.json?.data?.point?.rev;

  const p2 = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, body: { name: '自检·第二处', latitude: 31.1, longitude: 121.2 },
  });
  const p2id = p2.json?.data?.point?.id;
  check(envelopeOk(p2, 201), 'second favorite created -> 201');

  const list = await call('GET', '/v2/userCollectPoint/list', { token: tokenA3 });
  check(envelopeOk(list) && list.json?.data?.count === 2, 'favorite list returns 2 points');
  check(list.json?.data?.items?.every((p) => typeof p.rev === 'number' && p.deleted === false), 'list items carry rev/deleted extensions');
  const cursor = list.json?.data?.cursor;

  const incremental = await call('GET', `/v2/userCollectPoint/list?since=${cursor}`, { token: tokenA3 });
  check(envelopeOk(incremental) && incremental.json?.data?.count === 0, 'since=cursor returns no changes (incremental sync)');

  // ---- 5b. idempotent create (verifier §27 ①) --------------------------
  // Same Idempotency-Key twice -> same id, second call 200 (not 201), one row.
  const idemKey = `idem-fav-${stamp}`;
  const idemFirst = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, headers: { 'idempotency-key': idemKey }, body: { name: '自检·幂等', des: '坐90° 向270°' },
  });
  check(envelopeOk(idemFirst, 201) && idemFirst.json?.data?.deduplicated === false, 'idempotent create: first save -> 201 deduplicated=false');
  const idemId = idemFirst.json?.data?.point?.id;
  const idemSecond = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, headers: { 'idempotency-key': idemKey }, body: { name: '自检·幂等', des: '坐90° 向270°' },
  });
  check(
    idemSecond.status === 200 && envelopeOk(idemSecond, 200) && idemSecond.json?.data?.point?.id === idemId,
    'idempotent create: same key twice -> same id, second call 200'
  );
  check(idemSecond.json?.data?.deduplicated === true, 'idempotent create: replay is flagged deduplicated=true');
  const idemDifferent = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, headers: { 'idempotency-key': idemKey }, body: { name: '同 key 但内容不同', des: '坐1° 向2°' },
  });
  check(
    idemDifferent.status === 200 && idemDifferent.json?.data?.point?.id === idemId
      && idemDifferent.json?.data?.point?.name === '自检·幂等'
      && idemDifferent.json?.data?.point?.des === '坐90° 向270°',
    'idempotent create: same key with different body returns the original row untouched'
  );
  const idemList = await call('GET', '/v2/userCollectPoint/list', { token: tokenA3 });
  const idemRows = idemList.json?.data?.items?.filter((p) => p.name === '自检·幂等' || p.name === '同 key 但内容不同') ?? [];
  check(idemRows.length === 1 && idemRows[0].id === idemId, 'idempotent create: exactly one row exists for the key');
  // Verifier §27 judge is DB-level: the key must appear exactly once in the table.
  {
    let counter = null;
    try {
      counter = new DatabaseSync(DB_PATH);
      const row = counter.prepare('SELECT COUNT(*) AS n FROM favorites WHERE idem_key = ?').get(idemKey);
      check(Number(row.n) === 1, `idempotent create: DB holds exactly 1 row for the key (got ${row.n})`);
    } catch (error) {
      fail(`could not count rows for ${idemKey} in ${DB_PATH}: ${error.message}`);
    } finally {
      counter?.close();
    }
  }
  const idemOtherKey = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, headers: { 'idempotency-key': `${idemKey}-b` }, body: { name: '自检·幂等', des: '坐90° 向270°' },
  });
  check(
    envelopeOk(idemOtherKey, 201) && idemOtherKey.json?.data?.point?.id !== idemId,
    'idempotent create: a different key creates a new row'
  );
  const noKeyTwiceA = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, body: { name: '自检·无键' },
  });
  const noKeyTwiceB = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, body: { name: '自检·无键' },
  });
  check(
    noKeyTwiceA.json?.data?.point?.id !== noKeyTwiceB.json?.data?.point?.id,
    'without Idempotency-Key two creates stay two rows (no silent dedupe)'
  );
  // An UPDATE that carries the same key must still apply (the key belongs to the create).
  const idemUpdate = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, headers: { 'idempotency-key': idemKey }, body: { id: idemId, des: '坐90° 向270°（已改）' },
  });
  check(
    envelopeOk(idemUpdate) && idemUpdate.json?.data?.point?.des === '坐90° 向270°（已改）',
    'update with the row key still applies (key is not treated as a replay)'
  );
  // Clean up the extra rows so later count assertions keep their original meaning.
  for (const extraId of [
    idemId,
    idemOtherKey.json?.data?.point?.id,
    noKeyTwiceA.json?.data?.point?.id,
    noKeyTwiceB.json?.data?.point?.id,
  ]) {
    if (extraId) await call('DELETE', `/v2/userCollectPoint/delete?id=${extraId}`, { token: tokenA3 });
  }
  const afterIdemCleanup = await call('GET', '/v2/userCollectPoint/list', { token: tokenA3 });
  check(afterIdemCleanup.json?.data?.count === 2, 'idempotency rows cleaned up (2 originals remain)');
  // A deleted keyed row keeps its key: replay returns it (deleted) instead of resurrecting it.
  const idemAfterDelete = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, headers: { 'idempotency-key': idemKey }, body: { name: '自检·幂等' },
  });
  check(
    idemAfterDelete.status === 200 && idemAfterDelete.json?.data?.point?.id === idemId
      && idemAfterDelete.json?.data?.point?.deleted === true,
    'replay of a deleted keyed create returns the deleted row (no resurrection)'
  );
  const afterDeleteReplay = await call('GET', '/v2/userCollectPoint/list', { token: tokenA3 });
  check(afterDeleteReplay.json?.data?.count === 2, 'replaying a deleted key does not create a row');

  const conflict = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, body: { id: p1id, rev: p1rev + 5, name: 'stale' },
  });
  check(
    conflict.status === 409 && conflict.json?.code === 409 && conflict.json?.data?.server?.id === p1id,
    'stale rev on save -> 409 with server snapshot'
  );
  const updated = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, body: { id: p1id, rev: p1rev, des: '坐向：巽山乾向（已改）' },
  });
  check(envelopeOk(updated) && updated.json?.data?.point?.des.includes('已改'), 'save with matching rev updates the point');
  check(updated.json?.data?.point?.name === '自检·西湖', 'update keeps fields that were not sent');

  const del = await call('DELETE', `/v2/userCollectPoint/delete?id=${p2id}`, { token: tokenA3 });
  check(envelopeOk(del) && del.json?.data?.deleted === true, 'DELETE /v2/userCollectPoint/delete soft-deletes');
  const afterDel = await call('GET', '/v2/userCollectPoint/list', { token: tokenA3 });
  check(envelopeOk(afterDel) && afterDel.json?.data?.count === 1, 'deleted point disappears from the list');
  const resurrect = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenA3, body: { id: p2id, name: '复活' },
  });
  check(envelopeError(resurrect, 404), 'saving a deleted point id -> 404 (no resurrection)');

  // ---- 6. feedback -----------------------------------------------------
  const fb = await call('POST', '/v2/feedback/add', {
    token: tokenA3,
    headers: { 'idempotency-key': `idem-${stamp}` },
    body: { contactWay: 'selfcheck', orderNum: '', reasons: '自检反馈', remark: '', type: 2 },
  });
  check(envelopeOk(fb, 201) && fb.json?.data?.feedback?.type === 2, 'POST /v2/feedback/add accepts type=2');
  const fbId = fb.json?.data?.feedback?.id;
  const fbAgain = await call('POST', '/v2/feedback/add', {
    token: tokenA3,
    headers: { 'idempotency-key': `idem-${stamp}` },
    body: { reasons: '自检反馈重复提交', type: 2 },
  });
  check(envelopeOk(fbAgain) && fbAgain.json?.data?.feedback?.id === fbId, 'same Idempotency-Key returns the same feedback');

  const fbBadType = await call('POST', '/v2/feedback/add', {
    token: tokenA3, body: { reasons: 'type 1', type: 1 },
  });
  check(envelopeError(fbBadType, 400), 'feedback type other than 2 -> 400 (original value range not proven)');
  const fbAttach = await call('POST', '/v2/feedback/add', {
    token: tokenA3, body: { reasons: 'attachment', type: 2, attachments: '[{"url":"x"}]' },
  });
  check(envelopeError(fbAttach, 400), 'non-empty attachments -> 400 (image upload is DI)');
  const fbList = await call('GET', '/v2/feedback/list', { token: tokenA3 });
  check(envelopeOk(fbList) && fbList.json?.data?.count >= 1, 'GET /v2/feedback/list returns own feedback');

  // ---- 7. authorization isolation (A's token must not touch B) ----------
  const userB = `lc${stamp}b`;
  const regB = await register(userB, 'SelfCheck-B-1', '自检乙');
  const tokenB = regB.json?.data?.token;
  check(envelopeOk(regB, 201), `register ${userB} -> 201`);
  const listB = await call('GET', '/v2/userCollectPoint/list', { token: tokenB });
  check(envelopeOk(listB) && listB.json?.data?.count === 0, "B's list does not contain A's favorites");
  const bDeletesA = await call('DELETE', `/v2/userCollectPoint/delete?id=${p1id}`, { token: tokenB });
  check(envelopeError(bDeletesA, 404), "B deleting A's favorite -> 404 (ownership enforced)");
  const bSavesA = await call('POST', '/v2/userCollectPoint/save', {
    token: tokenB, body: { id: p1id, name: '被乙改' },
  });
  check(envelopeError(bSavesA, 404), "B saving A's favorite id -> 404 (ownership enforced)");
  const bFbList = await call('GET', '/v2/feedback/list', { token: tokenB });
  check(envelopeOk(bFbList) && bFbList.json?.data?.count === 0, "B's feedback list does not contain A's feedback");

  // ---- 8. session expiry ------------------------------------------------
  const userC = `lc${stamp}c`;
  const regC = await register(userC, 'SelfCheck-C-1');
  const tokenC = regC.json?.data?.token;
  check(envelopeOk(regC, 201), `register ${userC} -> 201`);
  const sessionsC = await call('GET', '/v2/auth/sessions', { token: tokenC });
  const sessionCId = sessionsC.json?.data?.items?.find((s) => s.current)?.id;
  let db = null;
  try {
    db = new DatabaseSync(DB_PATH);
    db.prepare('UPDATE sessions SET expires_at = ? WHERE id = ?')
      .run(new Date(Date.now() - 60_000).toISOString(), sessionCId);
  } catch (error) {
    fail(`could not expire a session in ${DB_PATH}: ${error.message}`);
  } finally {
    db?.close();
  }
  const expired = await call('GET', '/v2/auth/me', { token: tokenC });
  check(envelopeError(expired, 401), 'expired session token -> 401');

  // ---- 9. protocol edges ------------------------------------------------
  const v1 = await call('GET', '/v1/cases?owner_id=x');
  check(v1.status === 410 && v1.json?.code === 410 && v1.json?.data?.replacement === '/v2', '/v1/* -> 410 with replacement /v2');
  const notJson = await call('POST', '/v2/auth/login', { raw: 'username=a&password=b', headers: { 'content-type': 'text/plain' } });
  check(notJson.status === 415, `non-JSON content-type -> 415 (got ${notJson.status})`);
  const tooBig = await call('POST', '/v2/feedback/add', {
    token: tokenA3, body: { reasons: 'x'.repeat(300 * 1024), type: 2 },
  });
  check(tooBig.status === 413, `oversized body -> 413 (got ${tooBig.status})`);
  const evilCors = await call('GET', '/health', { headers: { origin: 'http://evil.example' } });
  check(evilCors.headers.get('access-control-allow-origin') === null, 'CORS: unknown origin gets no Allow-Origin header');

  // ---- 10. logout -------------------------------------------------------
  const logout = await call('POST', '/v2/auth/logout', { token: tokenA3 });
  check(envelopeOk(logout), 'POST /v2/auth/logout -> 200');
  const afterLogout = await call('GET', '/v2/auth/me', { token: tokenA3 });
  check(envelopeError(afterLogout, 401), 'logged-out token -> 401');
  const meA1 = await call('GET', '/v2/auth/me', { token: tokenA1 });
  check(envelopeOk(meA1), 'other session of the same user stays valid');
  const logoutAll = await call('POST', '/v2/auth/logout-all', { token: tokenA1 });
  check(envelopeOk(logoutAll) && logoutAll.json?.data?.revoked >= 1, 'POST /v2/auth/logout-all revokes remaining sessions');
  const afterLogoutAll = await call('GET', '/v2/auth/me', { token: tokenA1 });
  check(envelopeError(afterLogoutAll, 401), 'logout-all invalidates the token used for the call');

  // ---- 11. login rate limit (last: it burns the per-user budget) --------
  const userD = `lc${stamp}d`;
  await register(userD, 'SelfCheck-D-1');
  let limited = null;
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const res = await call('POST', '/v2/auth/login', { body: { username: userD, password: 'Wrong-Pass-9' } });
    if (res.status === 429) { limited = res; break; }
  }
  check(limited !== null, 'six wrong logins in a minute -> 429');
  check(limited?.json?.msg?.includes('频繁'), '429 message says the request is too frequent');
  check(Number(limited?.headers.get('retry-after') ?? 0) > 0, '429 carries Retry-After');

  console.log('');
  if (failures === 0) {
    console.log(`SELFCHECK: PASS (${checks} checks)`);
    process.exit(0);
  }
  console.error(`SELFCHECK: FAIL (${failures}/${checks} checks failed)`);
  process.exit(1);
}

main().catch((error) => {
  console.error(`SELFCHECK: FAIL (${error.message})`);
  process.exit(1);
});
