'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sql = fs.readFileSync(path.join(__dirname, '../supabase/migrations/20261113123000_qg_table_checkout_replay_session_guard.sql'), 'utf8');
const lock = sql.indexOf('for update;');
const missingSession = sql.indexOf("if not found then raise exception");
const validity = sql.indexOf('if v.revoked_at is not null or now()>=v.expires_at');
const coordinateGuard = sql.indexOf('if p_lat is null or p_lng is null');
const coordinateBounds = sql.indexOf('p_lat not between -90 and 90');
const longitudeBounds = sql.indexOf('p_lng not between -180 and 180');
const replayLookup = sql.indexOf('from public.qg_table_requests');
const replayReturn = sql.indexOf("'reason','same_request'");
const recentCartReturn = sql.indexOf("'reason','recent_same_cart'");
assert.ok(lock > -1 && missingSession > lock, 'session row must be locked and identified');
assert.ok(coordinateGuard > -1 && coordinateBounds > coordinateGuard && longitudeBounds > coordinateBounds && coordinateGuard < lock,
  'missing or out-of-range coordinates must be rejected before session lock and replay');
assert.match(sql, /jsonb_array_length\(p_items\) not between 1 and 50/, 'cart size must be bounded before replay');
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


const activeTable = sql.indexOf('from public.pos_tables');
const allowedShop = sql.indexOf('from public.shop_profiles');
const proximity = sql.indexOf('if public.qg_table_distance_m(');
assert.ok(activeTable > validity && allowedShop > activeTable, 'active table and shop must be checked after session validity');
assert.ok(proximity > allowedShop && proximity < replayLookup, 'proximity authorization must precede exact-request replay');
assert.ok(replayLookup < recentCartReturn, 'exact-request replay and recent-cart replay must follow authorization');
assert.match(sql.slice(activeTable, replayLookup), /where id=v\.table_id and shop_id=v\.shop_id and active/, 'table must remain active');
assert.match(sql.slice(allowedShop, replayLookup), /status not in \('suspended','rejected'\)/, 'shop must remain eligible');

console.log('checkout replay migration ordering: static regression passed');
