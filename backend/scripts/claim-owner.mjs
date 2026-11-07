#!/usr/bin/env node
/**
 * One-off migration helper: attach legacy v1 rows (keyed by the client-generated
 * opaque `owner_id`) to a real user account.
 *
 * v2 never auto-claims: a v1 owner_id is not proof of identity, so claiming must
 * be explicit. Usage (inside the container):
 *
 *   docker compose exec api node --no-warnings scripts/claim-owner.mjs dev_L2E0222531014833 laoliu
 *
 * The second argument is the username of the account that should own the rows.
 */
import { openDb } from '../src/db.js';

const [, , ownerId, username] = process.argv;
const DB_PATH = process.env.DB_PATH || '/data/lc.sqlite';

if (!ownerId || !username) {
  console.error('usage: node scripts/claim-owner.mjs <owner_id> <username>');
  process.exit(2);
}

const store = openDb(DB_PATH);
const user = store.getUserByUsernameLc(username.trim().toLowerCase());
if (!user) {
  console.error(`CLAIM: FAIL user ${username} not found`);
  process.exit(1);
}

const claimed = store.claimLegacyRows(ownerId, user.id);
console.log(`CLAIM: ok owner_id=${ownerId} user=${user.id} cases=${claimed.cases} settings=${claimed.settings} shares=${claimed.shares}`);
store.db.close();
