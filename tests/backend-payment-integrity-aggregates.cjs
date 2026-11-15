'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sql = fs.readFileSync(path.join(__dirname, '../scripts/sql/audit-payment-integrity-aggregates.sql'), 'utf8');
assert.match(sql, /WITH per_reference AS/i);
assert.match(sql, /GROUP BY btrim\(transaction_reference\)/i);
assert.match(sql, /transaction_reference IS NOT NULL AND btrim\(transaction_reference\) <> ''/i);
// Case and surrounding whitespace must not hide successful payment attempts.
assert.match(sql, /lower\(btrim\(p\.status\)\)\s+IN\s*\('paid','succeeded','success','completed','captured'\)/i);
assert.match(sql, /FROM public\.payments/i);
assert.match(sql, /LEFT JOIN public\.orders/i);
assert.match(sql, /p\.order_id IS NOT NULL AND o\.id IS NULL/i);
for (const signal of ['payment_rows','payments_with_nonblank_transaction_reference','payments_without_transaction_reference','payments_without_order_id','payments_without_status','payments_with_nonpositive_amount','payments_without_method','orders_with_multiple_payment_rows','duplicate_nonblank_transaction_references','orders_with_multiple_successful_payments','payment_orphans']) {
  assert.match(sql, new RegExp('AS\\s+' + signal + '\\b', 'i'));
}
assert.doesNotMatch(sql, /\b(insert\s+into|update\s+\w+\s+set|delete\s+from|alter\s+table|drop\s+table|truncate\s+table|grant\s+|revoke\s+)\b/i);
assert.doesNotMatch(sql, /SELECT\s+\*/i);
assert.match(sql, /FROM public\.payments WHERE transaction_reference IS NOT NULL AND btrim\(transaction_reference\) <> ''/i);
assert.match(sql, /FROM public\.payments WHERE transaction_reference IS NULL OR btrim\(transaction_reference\) = ''/i);
assert.match(sql, /FROM public\.payments WHERE order_id IS NULL/i);
assert.match(sql, /FROM public\.payments WHERE status IS NULL OR btrim\(status\) = ''/i);
assert.match(sql, /FROM public\.payments WHERE amount IS NULL OR amount <= 0/i);
assert.match(sql, /FROM public\.payments WHERE payment_method IS NULL OR btrim\(payment_method\) = ''/i);
// Per-order grouping inside the CTE is required; only the outward projection must be aggregate-only.
const outerSelect = sql.slice(sql.lastIndexOf('\nSELECT\n') + 1);
assert.match(outerSelect, /^SELECT\s*\n/i);
assert.doesNotMatch(outerSelect, /\b(?:SELECT|GROUP BY)\s+p\.order_id\b/i);
assert.doesNotMatch(outerSelect, /\bAS\s+(?:order_id|customer_id|user_id)\b/i);
console.log('Payment integrity aggregate audit contract: passed (static checks only)');
