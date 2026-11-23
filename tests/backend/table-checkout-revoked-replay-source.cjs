'use strict';
// Static source-order gate only; real PostgreSQL ACL/concurrency tests are separate release gates.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const sql = fs.readFileSync('ops/security/qg-table-checkout-revoked-replay-review-20261123.sql', 'utf8');
const gateText = 'if v.revoked_at is not null or now()>=v.expires_at then';
function validate(source) {
  assert.match(source, /SECURITY DEFINER\s+SET search_path TO ''/i, 'SECURITY DEFINER must pin empty search_path');
  const start = source.indexOf('AS $function$');
  assert.ok(start >= 0, 'function body must exist');
  const body = source.slice(start);
  const markers = [
    'for update;',
    gateText,
    'where session_id=p_session and request_id=p_request;',
    'and payload_hash=v_hash',
    'insert into public.qg_table_requests(',
    'insert into public.orders('
  ];
  const positions = markers.map(marker => body.indexOf(marker));
  assert.ok(positions.every(p => p >= 0), 'all lock, gate, replay and write markers must exist');
  assert.ok(positions[0] < positions[1] && positions.slice(2).every(p => p > positions[1]),
    'session must be row-locked and checked before every replay and write');
  assert.equal(body.split(gateText).length - 1, 1, 'exactly one revocation gate');
}
validate(sql);
const gateStart = sql.indexOf(gateText);
const gateEnd = sql.indexOf('end if;', gateStart) + 'end if;'.length;
assert.ok(gateStart >= 0 && gateEnd > gateStart, 'revocation block must exist');
const block = sql.slice(gateStart, gateEnd);
const removed = sql.slice(0, gateStart) + sql.slice(gateEnd);
const latePosition = removed.indexOf('  select * into t');
assert.ok(latePosition >= 0, 'replay section boundary must exist');
const late = removed.slice(0, latePosition) + block + '\n' + removed.slice(latePosition);
assert.throws(() => validate(late), /row-locked and checked/);
assert.throws(() => validate(removed), /all lock, gate, replay and write markers/);
assert.throws(() => validate(sql.replace('for update;', 'for share;')), /all lock, gate, replay and write markers/);
assert.throws(() => validate(sql.replace("SET search_path TO ''", "SET search_path TO 'public', 'pg_temp'")), /empty search_path/);
assert.throws(() => validate(sql.replace(gateText, gateText + '\n  ' + gateText)), /exactly one revocation gate/);
console.log('PASS: checkout source ordering + five negative mutation controls (NOT PostgreSQL E2E)');
