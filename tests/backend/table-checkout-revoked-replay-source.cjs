'use strict';
// Source-order regression only. This does not substitute for isolated PostgreSQL
// revoked/expired/replay/concurrency and RLS tests before deployment.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const sql = fs.readFileSync('ops/security/qg-table-checkout-revoked-replay-review-20261123.sql','utf8');
assert.match(sql,/SET search_path TO ''/i,'SECURITY DEFINER must use an empty search_path');
const body = sql.slice(sql.indexOf('AS $function$'));
const gate = body.indexOf('if v.revoked_at is not null or now()>=v.expires_at then');
const replay = body.indexOf('where session_id=p_session and request_id=p_request;');
const recentCart = body.indexOf('and payload_hash=v_hash');
const write = body.indexOf('insert into public.orders(');
assert.ok(gate>=0 && replay>gate && recentCart>gate && write>gate,
  'revoked/expired session must be checked before replay and order creation');
assert.equal(body.match(/if v.revoked_at is not null or now\(\)>=v.expires_at then/g)?.length,1,
  'exactly one revocation gate');
assert.match(body,/for update;/i,'session row must remain locked across replay check');
console.log('PASS source-order revoked/expired checkout replay contract (not E2E)');
