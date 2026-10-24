#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const SOURCE_KEY = 'queuego_verified';
const SOURCE_LICENSE = 'QueueGo-Internal';
const THAILAND_BOUNDS = { minLat: 5, maxLat: 21, minLon: 97, maxLon: 106 };
const SENSITIVE_KEYS = [
  'phone','email','user_id','auth_user_id','owner_id','owner_name','contact_name',
  'contact_phone','line_id','id_card','national_id'
];

function clean(value) { return value == null ? '' : String(value).trim(); }
function sqlLiteral(value) { return "'" + String(value).replace(/'/g, "''") + "'"; }
function sha256Text(value) { return crypto.createHash('sha256').update(value).digest('hex'); }
function isUuid(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(clean(value));
}
function normalizedTimestamp(value, fallback) {
  const raw = clean(value) || fallback;
  if (!raw || !Number.isFinite(Date.parse(raw))) throw new Error('invalid operational place timestamp');
  return new Date(raw).toISOString();
}
function publicText(value, label, max = 500) {
  const v = clean(value);
  if (v.length > max) throw new Error(`${label} exceeds ${max} characters`);
  return v || null;
}
function assertNoSensitiveFields(row, label) {
  for (const key of SENSITIVE_KEYS) {
    if (row && row[key] != null && clean(row[key]) !== '') {
      throw new Error(`${label} contains prohibited sensitive field ${key}`);
    }
  }
}
function coordinates(row, label) {
  const hasLat = row?.latitude != null && clean(row.latitude) !== '';
  const hasLon = row?.longitude != null && clean(row.longitude) !== '';
  if (hasLat !== hasLon) throw new Error(`${label} has an incomplete coordinate pair`);
  if (!hasLat) return { latitude: null, longitude: null };
  const latitude = Number(row.latitude), longitude = Number(row.longitude);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
    || latitude < THAILAND_BOUNDS.minLat || latitude > THAILAND_BOUNDS.maxLat
    || longitude < THAILAND_BOUNDS.minLon || longitude > THAILAND_BOUNDS.maxLon) {
    throw new Error(`${label} has coordinates outside Thailand bounds`);
  }
  return { latitude, longitude };
}
function shopCategory(publicCategory) {
  const value = clean(publicCategory).toLowerCase();
  const map = {
    food: { category: 'food', subcategory: 'restaurant' },
    cafe: { category: 'food', subcategory: 'cafe' },
    grocery: { category: 'grocery', subcategory: 'grocery' },
    laundry: { category: 'laundry', subcategory: 'laundry' },
    market: { category: 'market', subcategory: 'fresh_market_shop' },
    shopping: { category: 'shopping', subcategory: 'shopping' },
    other: { category: 'place', subcategory: 'shop' }
  };
  return map[value] || { category: 'place', subcategory: 'shop' };
}
function hashRow(row) {
  const publicPayload = {
    source_record_id: row.source_record_id,
    canonical_name: row.canonical_name,
    category: row.category,
    subcategory: row.subcategory,
    latitude: row.latitude,
    longitude: row.longitude,
    address: row.address,
    subdistrict: row.subdistrict,
    district: row.district,
    province: row.province,
    postcode: row.postcode,
    source_updated_at: row.source_updated_at
  };
  return sha256Text(JSON.stringify(publicPayload));
}
function makeShop(row, generatedAt) {
  assertNoSensitiveFields(row, 'shop');
  if (!isUuid(row?.id)) throw new Error('shop id must be a UUID');
  const status = clean(row.status).toLowerCase();
  const onboarding = clean(row.onboarding_status).toLowerCase();
  const archived = clean(row.archived_at);
  if (status !== 'active' || onboarding !== 'approved' || archived) return null;
  const publicCategory = clean(row.public_category).toLowerCase() || 'other';
  if (publicCategory === 'market' && clean(row.market_membership_status).toLowerCase() !== 'approved') return null;
  const name = publicText(row.shop_name, 'shop name', 240);
  if (!name) return null;
  const point = coordinates(row, `shop ${row.id}`);
  if (point.latitude == null) return null;
  const mapped = shopCategory(publicCategory);
  const sourceUpdatedAt = normalizedTimestamp(row.updated_at, generatedAt);
  const output = {
    source_record_id: 'shop:' + clean(row.id).toLowerCase(),
    dedupe_key: 'queuego:shop:' + clean(row.id).toLowerCase(),
    canonical_name: name,
    category: mapped.category,
    subcategory: mapped.subcategory,
    latitude: point.latitude,
    longitude: point.longitude,
    address: publicText(row.address, 'shop address'),
    subdistrict: publicText(row.subdistrict, 'shop subdistrict', 120),
    district: publicText(row.district, 'shop district', 120),
    province: publicText(row.province, 'shop province', 120),
    postcode: publicText(row.postcode, 'shop postcode', 20),
    source_updated_at: sourceUpdatedAt,
    verification_method: 'queuego_active_shop_profile'
  };
  output.source_payload_hash = hashRow(output);
  return output;
}
function makeMarket(row, generatedAt) {
  assertNoSensitiveFields(row, 'market');
  if (!isUuid(row?.id)) throw new Error('market id must be a UUID');
  if (row.active !== true || row.verified !== true) return null;
  const name = publicText(row.name, 'market name', 240);
  if (!name) return null;
  const point = coordinates(row, `market ${row.id}`);
  if (point.latitude == null) return null;
  const sourceUpdatedAt = normalizedTimestamp(row.updated_at, generatedAt);
  const output = {
    source_record_id: 'market:' + clean(row.id).toLowerCase(),
    dedupe_key: 'queuego:market:' + clean(row.id).toLowerCase(),
    canonical_name: name,
    category: 'market',
    subcategory: 'fresh_market',
    latitude: point.latitude,
    longitude: point.longitude,
    address: publicText(row.address, 'market address'),
    subdistrict: publicText(row.subdistrict, 'market subdistrict', 120),
    district: publicText(row.district, 'market district', 120),
    province: publicText(row.province, 'market province', 120),
    postcode: publicText(row.postcode, 'market postcode', 20),
    source_updated_at: sourceUpdatedAt,
    verification_method: 'queuego_verified_market'
  };
  output.source_payload_hash = hashRow(output);
  return output;
}
function parseOperationalSnapshot(text) {
  let parsed;
  try { parsed = JSON.parse(text); } catch { throw new Error('invalid QueueGo operational places JSON'); }
  if (!parsed || parsed.snapshot_complete !== true) throw new Error('snapshot_complete=true is required before stale-place reconciliation');
  const generatedAt = normalizedTimestamp(parsed.generated_at, null);
  if (!Array.isArray(parsed.shops) || !Array.isArray(parsed.markets)) throw new Error('snapshot must contain shops and markets arrays');
  const rows = [];
  let inactiveShops = 0, inactiveMarkets = 0;
  for (const shop of parsed.shops) {
    const row = makeShop(shop, generatedAt);
    if (row) rows.push(row); else inactiveShops++;
  }
  for (const market of parsed.markets) {
    const row = makeMarket(market, generatedAt);
    if (row) rows.push(row); else inactiveMarkets++;
  }
  rows.sort((a, b) => a.source_record_id.localeCompare(b.source_record_id));
  const seen = new Set();
  for (const row of rows) {
    if (seen.has(row.source_record_id)) throw new Error('duplicate operational source record ' + row.source_record_id);
    seen.add(row.source_record_id);
  }
  return {
    generatedAt,
    rows,
    snapshotHash: sha256Text(JSON.stringify(rows)),
    summary: {
      shops_input: parsed.shops.length,
      markets_input: parsed.markets.length,
      imported: rows.length,
      inactive_or_unapproved_shops: inactiveShops,
      inactive_or_unverified_markets: inactiveMarkets,
      without_coordinates: [...parsed.shops,...parsed.markets].filter(row => (row?.latitude == null || clean(row.latitude) === '') && (row?.longitude == null || clean(row.longitude) === '')).length
    }
  };
}
function renderOperationalPlacesSql(snapshot) {
  if (!snapshot || !Array.isArray(snapshot.rows) || !snapshot.generatedAt || !snapshot.snapshotHash) throw new Error('validated snapshot is required');
  const json = JSON.stringify(snapshot.rows).replace(/'/g, "''");
  const generatedAt = sqlLiteral(snapshot.generatedAt);
  return `-- QueueGo-owned verified operational places bootstrap/audit snapshot.
-- Generated from a complete, PII-free, map-addressable snapshot at ${snapshot.generatedAt}.
-- Requires the approved QueueGo operational runtime sync authority before execution.
-- Runtime and bootstrap share qg_private.queuego_upsert_operational_place; there is no second upsert implementation.
BEGIN;
WITH incoming AS (
 SELECT * FROM jsonb_to_recordset('${json}'::jsonb) AS r(
  source_record_id text,dedupe_key text,canonical_name text,category text,subcategory text,
  latitude double precision,longitude double precision,address text,subdistrict text,district text,
  province text,postcode text,source_updated_at timestamptz,verification_method text,source_payload_hash text
 )
)
SELECT qg_private.queuego_upsert_operational_place(
 i.source_record_id,i.canonical_name,i.category,i.subcategory,
 i.latitude,i.longitude,i.address,i.subdistrict,i.district,i.province,i.postcode,
 i.source_updated_at,i.verification_method,i.source_payload_hash
)
FROM incoming i
WHERE i.dedupe_key='queuego:'||i.source_record_id;

WITH incoming AS (
 SELECT * FROM jsonb_to_recordset('${json}'::jsonb) AS r(source_record_id text)
)
SELECT qg_private.queuego_retire_operational_place(
 p.source_id,'queuego_operational_snapshot_absent'
)
FROM public.queuego_places p
WHERE p.source='queuego_verified'
  AND (p.source_id LIKE 'shop:%' OR p.source_id LIKE 'market:%')
  AND NOT EXISTS (SELECT 1 FROM incoming i WHERE i.source_record_id=p.source_id);

UPDATE public.queuego_place_sources
SET metadata=jsonb_set(
      jsonb_set(
        jsonb_set(coalesce(metadata,'{}'::jsonb),'{last_operational_snapshot_at}',to_jsonb(${generatedAt}::timestamptz),true),
        '{last_operational_snapshot_count}',to_jsonb(${snapshot.rows.length}),true
      ),
      '{last_operational_snapshot_sha256}',to_jsonb(${sqlLiteral(snapshot.snapshotHash)}::text),true
    ),
    updated_at=now()
WHERE source_key='queuego_verified';
COMMIT;
`;
}

if (require.main === module) {
  const [input, output] = process.argv.slice(2);
  if (!input) {
    console.error('Usage: node scripts/import-queuego-verified-places.cjs <complete-public-snapshot.json> [output.sql]');
    process.exit(2);
  }
  const snapshot = parseOperationalSnapshot(fs.readFileSync(path.resolve(input), 'utf8'));
  const sql = renderOperationalPlacesSql(snapshot);
  if (output) fs.writeFileSync(path.resolve(output), sql, 'utf8'); else process.stdout.write(sql);
  console.error(`validated ${snapshot.rows.length} map-addressable QueueGo operational places; ${snapshot.summary.without_coordinates} source rows without coordinates excluded`);
}

module.exports = {
  SOURCE_KEY,
  SOURCE_LICENSE,
  THAILAND_BOUNDS,
  SENSITIVE_KEYS,
  parseOperationalSnapshot,
  renderOperationalPlacesSql,
  shopCategory
};
