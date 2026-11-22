'use strict';
// Static fail-closed contract only; actual PostgreSQL role/concurrency tests remain mandatory.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const sql = fs.readFileSync('ops/security/qg-table-checkout-revoked-replay-review-20261123.sql','utf8');
function validate(source) {
  assert.match(source,/SECURITY DEFINER\s+SET search_path TO ''/i,
    'SECURITY DEFINER must pin empty search_path');
  const body = source.slice(source.indexOf('AS $function$'));
  const lock = body.indexOf('for update;');
  const gate = body.indexOf('if v.revoked_at is not null or now()>=v.expires_at then');
  const replay = body.indexOf('where session_id=p_session and request_id=p_request;');
  const recentCart = body.indexOf('and payload_hash=v_hash');
  const requestWrite = body.indexOf('insert into public.qg_table_requests(');
  const orderWrite = body.indexOf('insert into public.orders(');
  assert.ok(lock >= 0 && gate > lock && replay > gate && recentCart > gate &&
    requestWrite > gate && orderWrite > gate,
    'session must be row-locked and checked before all replay and write paths');
  assert.equal(body.match(/if v.revoked_at is not null or now\(\)>=v.expires_at then/g)?.length,1,
    'exactly one revocation gate');
}
validate(sql);
// Mutation controls prove the guard actually rejects the dangerous historical ordering.
const gateText = 'if v.revoked_at is not null or now()>=v.expires_at then';
const oldGate = sql.indexOf(gateText);
const blockEnd = sql.indexOf('end if;', oldGate) + 'end if;'.length;
const block = sql.slice(oldGate, blockEnd);
const moved = sql.slice(0,oldGate) + sql.slice(blockEnd);
const replayMarker = '  v_hash:=encode(extensions.digest(p_items::text';
const mutated = moved.replace(replayMarker, block + '\n' + replayMarker);
// Move the gate after the first replay query to model a real regression.
const replayEnd = moved.indexOf('  select * into t');
const late = moved.slice(0,replayEnd) + block + '\n' + moved.slice(replayEnd);
assert.throws(()=>validate(late),/row-locked and checked/);
assert.throws(()=>validate(sql.replace("SET search_path TO ''","SET search_path TO 'public', 'pg_temp'")),/empty search_path/);
console.log('PASS checkout source ordering and negative mutation controls (NOT PostgreSQL E2E)');
