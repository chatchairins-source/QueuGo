'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const sql = fs.readFileSync(path.join(__dirname, '../scripts/sql/audit-pos-payment-rpc-signals.sql'), 'utf8');
assert.match(sql, /FROM pg_proc p\s+JOIN pg_namespace n ON n\.oid=p\.pronamespace/i, 'audit must read PostgreSQL catalog');
assert.match(sql, /n\.nspname='public'/i, 'audit must restrict to public schema');
for (const fn of ['pos_take_payment', 'pos_refund_bill']) {
  assert.ok(sql.includes("'" + fn + "'"), fn + ' must be included');
}
for (const role of ['anon', 'authenticated']) {
  assert.ok(sql.includes("has_function_privilege('" + role + "',oid,'EXECUTE')"), role + ' grant must be checked');
}
for (const signal of ['security_definer', 'has_fixed_search_path', 'row_lock_signal', 'auth_uid_signal', 'explicit_idempotency_signal', 'payment_status_signal']) {
  assert.ok(sql.includes(signal), signal + ' must remain visible in audit output');
}
assert.doesNotMatch(sql, /\b(insert|update|delete|alter|drop|truncate|grant|revoke)\s+(?:into\s+|table\s+|function\s+|on\s+)?(?:public\.)?[a-z_]+/i, 'audit must not mutate database');
assert.doesNotMatch(sql, /SELECT\s+\*/i, 'audit must not expose unrestricted records');
console.log('POS payment RPC read-only audit contract: passed');
