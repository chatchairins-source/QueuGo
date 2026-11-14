'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sql = fs.readFileSync(path.join(__dirname, '../supabase/migrations/20261114023000_qg_table_catalog_pos_eligibility.sql'), 'utf8');
assert.match(sql, /CREATE OR REPLACE FUNCTION public\.qg_table_catalog\(p_session uuid, p_device_key uuid\)/);
assert.match(sql, /SECURITY DEFINER\s+SET search_path TO ''/);
assert.doesNotMatch(sql, /SET search_path TO 'public'/, 'Definer must not resolve untrusted public objects');
assert.match(sql, /device_hash=encode\(extensions\.digest\(p_device_key::text,'sha256'\),'hex'\)/);
assert.match(sql, /revoked_at is null\s+and expires_at>now\(\)/);
assert.match(sql, /from public\.pos_tables where id=v\.table_id and shop_id=v\.shop_id and active/);
assert.match(sql, /where p\.shop_id=v\.shop_id\s+and p\.available\s+and p\.pos_available/);
assert.doesNotMatch(sql, /p\.delivery_available/, 'QR dine-in catalog must not depend on delivery eligibility');
assert.match(sql, /coalesce\(p\.pos_price,p\.price\)/);
assert.match(sql, /from public\.shop_profiles\s+where id=v\.shop_id and status not in \('suspended','rejected'\)/,
  'Catalog must deny suspended/rejected shops, matching checkout');
// Verify authorization gates precede the catalog return, not merely occur somewhere in the file.
const sessionGuard = sql.indexOf('select * into v');
const revocationGuard = sql.indexOf('revoked_at is null');
const activeTableGuard = sql.indexOf('from public.pos_tables where id=v.table_id and shop_id=v.shop_id and active');
const shopGuard = sql.indexOf('from public.shop_profiles');
const catalogReturn = sql.indexOf('return (');
assert.ok(sessionGuard >= 0 && sessionGuard < revocationGuard && revocationGuard < activeTableGuard && activeTableGuard < shopGuard && shopGuard < catalogReturn,
  'Session expiry/revocation and shop-scoped active-table checks must precede catalog return');
assert.match(sql, /if not found or not exists\([\s\S]*?\) then\s+raise exception/,
  'Invalid or cross-shop sessions must raise before product disclosure');
assert.match(sql, /from public\.products p[\s\S]*?where p\.shop_id=v\.shop_id/,
  'Catalog must be restricted to the session shop');
for (const available of [false,true]) {
  for (const posAvailable of [false,true]) {
    for (const deliveryAvailable of [false,true]) {
      const catalog = available && posAvailable;
      const checkout = available && posAvailable;
      assert.equal(catalog, checkout, `catalog/checkout mismatch for available=${available}, pos=${posAvailable}, delivery=${deliveryAvailable}`);
    }
  }
}
console.log('QR table POS catalog eligibility static regression passed');
