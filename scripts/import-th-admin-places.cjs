#!/usr/bin/env node
'use strict';

// Build a repeatable SQL import from the pinned Open Admin Data snapshot.
// Download data/all-flat.ndjson from the reviewed release and pass its path.
const fs = require('node:fs');
const crypto = require('node:crypto');
const path = require('node:path');

const PINNED_GIT_BLOB_SHA = 'baa3eb18fb79412e85dcef08059d16f8cf47b317';
const EXPECTED_COUNTS = { 1: 77, 2: 928, 3: 7364 };
const LEVELS = { 1: 'province', 2: 'district', 3: 'subdistrict' };

function gitBlobSha(buffer) {
  return crypto.createHash('sha1').update(`blob ${buffer.length}\0`).update(buffer).digest('hex');
}

function parseDataset(text, { expectedCounts = EXPECTED_COUNTS, pinnedSha = PINNED_GIT_BLOB_SHA, rawBuffer } = {}) {
  if (rawBuffer && gitBlobSha(rawBuffer) !== pinnedSha) throw new Error(`snapshot blob SHA mismatch: got ${gitBlobSha(rawBuffer)}`);
  const rows = text.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
    let row;
    try { row = JSON.parse(line); } catch { throw new Error(`invalid NDJSON at line ${index + 1}`); }
    const level = Number(row.level), id = String(row.id ?? ''), th = String(row.name?.local ?? '').trim();
    const en = String(row.name?.en ?? '').trim();
    if (!LEVELS[level] || !id || !th || !en || String(row.code?.id ?? '') !== id) throw new Error(`invalid administrative record at line ${index + 1}`);
    const lat = Number(row.geo?.lat), lon = Number(row.geo?.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < 5 || lat > 21 || lon < 97 || lon > 106) throw new Error(`invalid Thailand coordinates for admin code ${id}`);
    return { id, level, th, en, parent: row.parent?.id == null ? null : String(row.parent.id), lat, lon,
      postcode: Array.isArray(row.zip_codes) ? String(row.zip_codes.find(x => /^\d{5}$/.test(String(x))) || '') : '',
      slug: String(row.name?.slug || ''), raw: row };
  });
  const byCode = new Map();
  for (const row of rows) {
    if (byCode.has(row.id)) throw new Error(`duplicate administrative code ${row.id}`);
    byCode.set(row.id, row);
  }
  const counts = Object.fromEntries(Object.keys(LEVELS).map(level => [level, rows.filter(row => row.level === Number(level)).length]));
  for (const [level, expected] of Object.entries(expectedCounts)) if (counts[level] !== expected) throw new Error(`level ${LEVELS[level]} count mismatch: expected ${expected}, got ${counts[level] || 0}`);
  for (const row of rows) {
    if (row.level === 1 && row.parent !== null) throw new Error(`province ${row.id} unexpectedly has a parent`);
    if (row.level > 1 && (!row.parent || !byCode.has(row.parent) || byCode.get(row.parent).level !== row.level - 1)) throw new Error(`missing or invalid parent for admin code ${row.id}`);
  }
  const materialized = rows.map(row => {
    let province = row.level === 1 ? row.th : '', district = row.level === 2 ? row.th : '', subdistrict = row.level === 3 ? row.th : '';
    let parent = row.parent;
    while (parent) {
      const p = byCode.get(parent);
      if (p.level === 1) province = p.th;
      if (p.level === 2) district = p.th;
      parent = p.parent;
    }
    return { id: row.id, level: row.level, admin_level: LEVELS[row.level], name_th: row.th, name_en: row.en,
      canonical_name: row.th, alias: row.slug, latitude: row.lat, longitude: row.lon, parent_code: row.parent,
      province, district, subdistrict, postcode: row.postcode, source_record_hash: crypto.createHash('sha256').update(JSON.stringify(row.raw)).digest('hex') };
  });
  return { rows: materialized, counts };
}

function sqlLiteral(value) { return `'${String(value).replace(/'/g, "''")}'`; }
function renderImportSql(data, { snapshotDate = '2026-06-01' } = {}) {
  const { rows, counts } = data;
  let sql = `-- QueueGo Places import from Open Admin Data Thailand Administrative Divisions (2026.06).\n-- CC BY 4.0; attribution: Open Admin Data; modified for QueueGo Places.\n-- Reviewed all-flat.ndjson Git blob SHA: ${PINNED_GIT_BLOB_SHA}\n-- Counts verified before generation: ${JSON.stringify(counts)}. Villages are not present in this source.\n-- Review this output, source release, and checksum before applying.\nBEGIN;\n`;
  for (const level of [1, 2, 3]) {
    const batch = rows.filter(row => row.level === level);
    const json = JSON.stringify(batch).replace(/'/g, "''");
    sql += `\n-- ${LEVELS[level]} records\nWITH incoming AS (\n SELECT * FROM jsonb_to_recordset(${sqlLiteral(json)}::jsonb) AS r(\n  id text, level integer, admin_level text, canonical_name text, name_th text, name_en text, alias text, latitude double precision, longitude double precision, parent_code text, province text, district text, subdistrict text, postcode text, source_record_hash text\n )\n), upserted AS (\n INSERT INTO public.queuego_places(canonical_name,name_th,name_en,aliases,category,subcategory,latitude,longitude,address,village,moo,subdistrict,district,province,postcode,country,country_code,source,source_id,source_license,source_updated_at,verification_status,verification_method,confidence,dedupe_key,parent_place_id,admin_level,admin_code)\n SELECT i.canonical_name,i.name_th,i.name_en,CASE WHEN i.alias<>'' THEN ARRAY[i.alias] ELSE '{}'::text[] END,'administrative_area',i.admin_level,i.latitude,i.longitude,NULL,NULL,NULL,NULLIF(i.subdistrict,''),NULLIF(i.district,''),NULLIF(i.province,''),NULLIF(i.postcode,''),'Thailand','TH','th_admin_open_data',i.id,'CC-BY-4.0',${sqlLiteral(snapshotDate)}::date::timestamptz,'verified','licensed_open_admin_dataset',0.990,'th_admin:'||i.id,p.place_id,i.admin_level,i.id\n FROM incoming i LEFT JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.parent_code\n ON CONFLICT(country,admin_code) WHERE admin_code IS NOT NULL DO UPDATE SET canonical_name=excluded.canonical_name,name_th=excluded.name_th,name_en=excluded.name_en,aliases=excluded.aliases,subcategory=excluded.subcategory,latitude=excluded.latitude,longitude=excluded.longitude,province=excluded.province,district=excluded.district,subdistrict=excluded.subdistrict,postcode=excluded.postcode,source=excluded.source,source_id=excluded.source_id,source_license=excluded.source_license,source_updated_at=excluded.source_updated_at,verification_status=excluded.verification_status,verification_method=excluded.verification_method,confidence=excluded.confidence,parent_place_id=excluded.parent_place_id,admin_level=excluded.admin_level,updated_at=now()\n RETURNING place_id,admin_code\n)\nINSERT INTO public.queuego_place_source_records(source,source_record_id,place_id,source_license,source_updated_at,source_payload_hash)\nSELECT 'th_admin_open_data',i.id,p.place_id,'CC-BY-4.0',${sqlLiteral(snapshotDate)}::date::timestamptz,i.source_record_hash FROM incoming i JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.id\nON CONFLICT(source,source_record_id) DO UPDATE SET place_id=excluded.place_id,source_license=excluded.source_license,source_updated_at=excluded.source_updated_at,source_payload_hash=excluded.source_payload_hash,imported_at=now();\n`;
    sql += `\nWITH incoming AS (SELECT * FROM jsonb_to_recordset(${sqlLiteral(json)}::jsonb) AS r(id text,level integer,admin_level text,parent_code text,canonical_name text,name_th text,name_en text,alias text,latitude double precision,longitude double precision,province text,district text,subdistrict text,postcode text,source_record_hash text))\nINSERT INTO public.queuego_place_field_provenance(place_id,field_name,source,source_record_id,observed_at,confidence,raw_value)\nSELECT p.place_id,v.field_name,'th_admin_open_data',i.id,${sqlLiteral(snapshotDate)}::date::timestamptz,0.990,v.raw_value\nFROM incoming i JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.id\nCROSS JOIN LATERAL (VALUES\n ('canonical_name',to_jsonb(i.canonical_name)),('name_th',to_jsonb(i.name_th)),('name_en',to_jsonb(i.name_en)),('aliases',to_jsonb(i.alias)),('admin_code',to_jsonb(i.id)),('admin_level',to_jsonb(i.admin_level)),('category',to_jsonb('administrative_area'::text)),('subcategory',to_jsonb(i.admin_level)),('parent_place_id',to_jsonb(i.parent_code)),('source_id',to_jsonb(i.id)),('source_license',to_jsonb('CC-BY-4.0'::text)),('source_updated_at',to_jsonb(${sqlLiteral(snapshotDate)}::date)),('verification_status',to_jsonb('verified'::text)),('verification_method',to_jsonb('licensed_open_admin_dataset'::text)),('confidence',to_jsonb(0.990::numeric)),('latitude',to_jsonb(i.latitude)),('longitude',to_jsonb(i.longitude)),('province',to_jsonb(i.province)),('district',to_jsonb(i.district)),('subdistrict',to_jsonb(i.subdistrict)),('postcode',to_jsonb(i.postcode))\n) AS v(field_name,raw_value)\nON CONFLICT(place_id,field_name,source,source_record_id) WHERE source_record_id IS NOT NULL DO UPDATE SET observed_at=excluded.observed_at,confidence=excluded.confidence,raw_value=excluded.raw_value;\n\nWITH incoming AS (SELECT * FROM jsonb_to_recordset(${sqlLiteral(json)}::jsonb) AS r(id text,alias text))\nINSERT INTO public.queuego_place_aliases(place_id,alias,language_code,source)\nSELECT p.place_id,i.alias,'und','th_admin_open_data' FROM incoming i JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.id WHERE i.alias<>''\nON CONFLICT(place_id,normalized_alias) DO UPDATE SET language_code=excluded.language_code,source=excluded.source;\n`;
  }
  sql += `\nCOMMIT;\n`;
  return sql;
}

if (require.main === module) {
  const input = process.argv[2];
  if (!input) { console.error('Usage: node scripts/import-th-admin-places.cjs <all-flat.ndjson> [output.sql]'); process.exit(2); }
  const buffer = fs.readFileSync(path.resolve(input));
  const parsed = parseDataset(buffer.toString('utf8'), { rawBuffer: buffer });
  const output = renderImportSql(parsed);
  const target = process.argv[3] ? path.resolve(process.argv[3]) : null;
  if (target) fs.writeFileSync(target, output, 'utf8'); else process.stdout.write(output);
  console.error(`validated ${JSON.stringify(parsed.counts)}; source Git blob SHA ${gitBlobSha(buffer)}`);
}

module.exports = { parseDataset, renderImportSql, gitBlobSha, PINNED_GIT_BLOB_SHA, EXPECTED_COUNTS };
