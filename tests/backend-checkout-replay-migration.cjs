'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sql = fs.readFileSync(path.join(__dirname, '../supabase/migrations/20261113123000_qg_table_checkout_replay_session_guard.sql'), 'utf8');
const lock = sql.indexOf('for update;');
const missingSession = sql.indexOf("if not found then raise exception");
const validity = sql.indexOf('if v.revoked_at is not null or now()>=v.expires_at');
const replayLookup = sql.indexOf('from public.qg_table_requests');
const replayReturn = sql.indexOf("'reason','same_request'");
const recentCartReturn = sql.indexOf("'reason','recent_same_cart'");
assert.ok(lock > -1 && missingSession > lock, 'session row must be locked and identified');
assert.ok(validity > missingSession && validity < replayLookup, 'session validity must precede replay lookup');
assert.ok(replayReturn > validity && recentCartReturn > validity, 'both duplicate responses must follow session validity');
assert.equal((sql.match(/if v\.revoked_at is not null or now\(\)>=v\.expires_at/g) || []).length, 1, 'only one validity guard');
assert.match(sql, /SECURITY DEFINER/);
assert.match(sql, /SET search_path TO 'public', 'pg_temp'/);
assert.match(sql, /v_old\.payload_hash<>v_hash/);
assert.match(sql, /perform set_config\('queuego\.pos_rpc','on',true\)/);

// Fail closed: no checkout result may be returned before session validity is checked.
// This guards against future replay fast paths that skip revoked/expired-session checks.
const preValidity = sql.slice(sql.indexOf('begin'), validity);
assert.doesNotMatch(preValidity, /\breturn\b/i, 'checkout must not return before session authorization');
const postValidity = sql.slice(validity);
assert.match(postValidity, /where session_id=p_session and request_id=p_request/, 'replay lookup must remain scoped to the authorized session');
assert.match(postValidity, /if v_old\.payload_hash<>v_hash then/, 'same request ID with a changed payload must be rejected');
assert.match(postValidity, /where session_id=p_session\s+and payload_hash=v_hash/, 'recent-cart replay must remain scoped to the authorized session');

console.log('checkout replay migration ordering: static regression passed');
