'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sql = fs.readFileSync(path.join(__dirname, '../scripts/sql/audit-payment-integrity-aggregates.sql'), 'utf8');
assert.match(sql, /WITH per_order AS/i);
assert.match(sql, /FROM public\.payments/i);
assert.match(sql, /LEFT JOIN public\.orders/i);
assert.match(sql, /p\.order_id IS NOT NULL AND o\.id IS NULL/i);
for (const signal of ['payment_rows','orders_with_multiple_payment_rows','orders_with_multiple_successful_payments','payment_orphans']) {
  assert.match(sql, new RegExp('AS\\s+' + signal + '\\b', 'i'));
}
assert.doesNotMatch(sql, /\b(insert\s+into|update\s+\w+\s+set|delete\s+from|alter\s+table|drop\s+table|truncate\s+table|grant\s+|revoke\s+)\b/i);
assert.doesNotMatch(sql, /SELECT\s+\*/i);
assert.doesNotMatch(sql, /\b(group by|select)\s+p\.order_id\b/i);
console.log('Payment integrity aggregate audit contract: passed (static checks only)');
