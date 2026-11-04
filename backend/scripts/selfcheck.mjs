#!/usr/bin/env node
/**
 * LC backend self-check: exercises every endpoint of the v0.1 contract.
 * Usage: node --no-warnings scripts/selfcheck.mjs            (default http://127.0.0.1:8787)
 *        BASE_URL=http://host:port node --no-warnings scripts/selfcheck.mjs
 * Exit code 0 = PASS, 1 = FAIL.
 */
const BASE = (process.env.BASE_URL || 'http://127.0.0.1:8787').replace(/\/+$/, '');
const stamp = Date.now();
const owner = `selfcheck_${stamp}`;

let failures = 0;
let checks = 0;

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

async function call(method, pathName, body) {
  const headers = {};
  if (body !== undefined) headers['content-type'] = 'application/json';
  const res = await fetch(BASE + pathName, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-JSON (HTML page) */ }
  return { status: res.status, json, text };
}

async function main() {
  console.log(`LC backend self-check against ${BASE}`);

  const health = await call('GET', '/health');
  check(health.status === 200, `GET /health -> 200 (got ${health.status})`);
  check(health.json?.status === 'ok', `health.status == ok (got ${health.json?.status})`);
  check(health.json?.db === 'ok', 'health.db == ok');

  const created = await call('POST', '/v1/cases', {
    owner_id: owner,
    title: 'Selfcheck Case',
    sit_degree: 135,
    facing_degree: 315,
    compass_type: 'sanyuan',
    tags: ['selfcheck', '135'],
    notes: 'created by selfcheck.mjs',
    location: { lat: 30.25, lng: 120.15, name: 'Hangzhou' },
    data: { payload: { keys: ['a', 'b'] } },
  });
  check(created.status === 201, `POST /v1/cases -> 201 (got ${created.status})`);
  const caseId = created.json?.case?.id;
  check(typeof caseId === 'string' && caseId.length > 0, 'created case has an id');
  check(created.json?.case?.facing_degree === 315, 'facing_degree round-trips');

  const fetched = await call('GET', `/v1/cases/${encodeURIComponent(caseId)}?owner_id=${owner}`);
  check(fetched.status === 200 && fetched.json?.case?.title === 'Selfcheck Case', 'GET /v1/cases/{id} returns the case');

  const updated = await call('PUT', `/v1/cases/${encodeURIComponent(caseId)}`, {
    owner_id: owner,
    notes: 'updated by selfcheck.mjs',
    sit_degree: 136,
  });
  check(updated.status === 200 && updated.json?.case?.notes === 'updated by selfcheck.mjs', 'PUT /v1/cases/{id} merges fields');
  check(updated.json?.case?.title === 'Selfcheck Case', 'PUT keeps fields that were not sent');

  const listed = await call('GET', `/v1/cases?owner_id=${owner}`);
  check(listed.status === 200 && listed.json?.items?.length === 1, 'GET /v1/cases lists 1 case');

  const settingsPut = await call('PUT', `/v1/settings/${owner}`, {
    settings: { theme: 'dark', active_compass: 3, sound: false },
  });
  check(settingsPut.status === 200 && settingsPut.json?.rev > 0, 'PUT /v1/settings/{owner} stores settings');
  const settingsGet = await call('GET', `/v1/settings/${owner}`);
  check(settingsGet.status === 200 && settingsGet.json?.settings?.active_compass === 3, 'GET /v1/settings/{owner} returns settings');

  const pullBefore = await call('GET', `/v1/sync/pull?owner_id=${owner}&since=0`);
  check(pullBefore.status === 200 && pullBefore.json?.sync?.changes?.length >= 3, 'GET /v1/sync/pull returns change log');
  const cursor = pullBefore.json?.sync?.cursor;

  const pushed = await call('POST', '/v1/sync/push', {
    owner_id: owner,
    cases: [{
      id: `case_push_${stamp}`,
      title: 'Pushed Case',
      sit_degree: 90,
      facing_degree: 270,
    }],
    settings: { theme: 'light' },
  });
  check(pushed.status === 200 && pushed.json?.sync?.applied?.length === 1, 'POST /v1/sync/push applies one case');
  const pulledAfter = await call('GET', `/v1/sync/pull?owner_id=${owner}&since=${cursor}`);
  check(pulledAfter.status === 200 && pulledAfter.json?.sync?.changes?.some((c) => c.entity_id === `case_push_${stamp}`), 'pull after cursor sees the pushed case');

  const share = await call('POST', '/v1/shares', { owner_id: owner, case_id: caseId, expires_in_days: 7 });
  check(share.status === 201 && typeof share.json?.share?.token === 'string', 'POST /v1/shares creates a link');
  const token = share.json?.share?.token;
  const shareGet = await call('GET', `/v1/shares/${token}`);
  check(shareGet.status === 200 && shareGet.json?.share?.case?.title === 'Selfcheck Case', 'GET /v1/shares/{token} returns the shared case');
  check(shareGet.json?.share?.case?.owner_id === undefined, 'shared case does not leak owner_id');
  const page = await call('GET', `/s/${token}`);
  check(page.status === 200 && page.text.includes('Selfcheck Case') && page.text.includes('山水罗盘'), 'GET /s/{token} serves the HTML share page');

  const invalid = await call('POST', '/v1/cases', { owner_id: owner });
  check(invalid.status === 400 && invalid.json?.error?.code === 'invalid_field', 'missing title -> 400 invalid_field');
  const notFound = await call('GET', `/v1/cases/case_missing_000?owner_id=${owner}`);
  check(notFound.status === 404 && notFound.json?.error?.code === 'case_not_found', 'unknown case -> 404 case_not_found');

  const deleted = await call('DELETE', `/v1/cases/${encodeURIComponent(caseId)}?owner_id=${owner}`);
  check(deleted.status === 200 && deleted.json?.deleted === true, 'DELETE /v1/cases/{id} soft-deletes');
  const afterDelete = await call('GET', `/v1/cases/${encodeURIComponent(caseId)}?owner_id=${owner}`);
  check(afterDelete.status === 404, 'deleted case is no longer visible');
  const pullDeleted = await call('GET', `/v1/sync/pull?owner_id=${owner}&since=${pulledAfter.json?.sync?.cursor}`);
  check(pullDeleted.status === 200 && pullDeleted.json?.sync?.changes?.some((c) => c.op === 'delete'), 'change log records the delete');

  const revoked = await call('DELETE', `/v1/shares/${token}?owner_id=${owner}`);
  check(revoked.status === 200 && revoked.json?.revoked === true, 'DELETE /v1/shares/{token} revokes the link');
  const shareAfterRevoke = await call('GET', `/v1/shares/${token}`);
  check(shareAfterRevoke.status === 404, 'revoked share link returns 404');

  console.log('');
  if (failures === 0) {
    console.log(`SELFCHECK: PASS (${checks} checks)`);
    process.exit(0);
  } else {
    console.error(`SELFCHECK: FAIL (${failures}/${checks} checks failed)`);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(`SELFCHECK: FAIL (${error.message})`);
  process.exit(1);
});
