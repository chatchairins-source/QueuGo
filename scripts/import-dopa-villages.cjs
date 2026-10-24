#!/usr/bin/env node
'use strict';

// Generate a reviewable SQL import from one licensed DOPA GIS-01 province JSON.
// The importer deliberately accepts a local file only: download through the
// reviewed source URL, record its SHA-256, then pass that checksum explicitly.
const fs = require('node:fs');
const crypto = require('node:crypto');
const path = require('node:path');

const SOURCE_KEY = 'th_dopa_village_gis';
const THAILAND_BOUNDS = { minLat: 5, maxLat: 21, minLon: 97, maxLon: 106 };

function sha256(buffer) { return crypto.createHash('sha256').update(buffer).digest('hex'); }
function clean(value) { return value == null ? '' : String(value).trim(); }
function code(value, label, line) {
  const result = clean(value).replace(/\s+/g, '');
  if (!/^\d{1,12}$/.test(result)) throw new Error(`invalid ${label} at record ${line}`);
  return result;
}

function extractRecords(parsed) {
  if (Array.isArray(parsed)) return parsed;
  if (parsed && Array.isArray(parsed.features)) return parsed.features.map(feature => ({
    ...(feature.properties || {}),
    ...(feature.geometry?.type === 'Point' && Array.isArray(feature.geometry.coordinates)
      ? { long: feature.geometry.coordinates[0], lat: feature.geometry.coordinates[1] } : {})
  }));
  if (parsed && Array.isArray(parsed.data)) return parsed.data;
  throw new Error('expected a JSON array, FeatureCollection, or {data: []}');
}

function parseDopaVillageDataset(text, { expectedProvinceCode, expectedProvinceName, rawBuffer, expectedSha256 } = {}) {
  if (!expectedProvinceCode || !expectedProvinceName) throw new Error('province code and name are required');
  if (rawBuffer && expectedSha256 && sha256(rawBuffer) !== expectedSha256.toLowerCase()) throw new Error(`source SHA-256 mismatch: got ${sha256(rawBuffer)}`);
  let parsed;
  try { parsed = JSON.parse(text); } catch { throw new Error('invalid DOPA JSON'); }
  const records = extractRecords(parsed);
  if (!records.length) throw new Error('DOPA resource has no records');
  const seen = new Set();
  const rows = records.map((record, index) => {
    const line = index + 1;
    const row = record?.properties && typeof record.properties === 'object' ? { ...record.properties, ...record } : record;
    const pcode = code(row.pcode, 'pcode', line);
    const acode = code(row.acode, 'acode', line);
    const tcode = code(row.tcode, 'tcode', line);
    const mcode = code(row.mcode, 'mcode', line);
    const province = clean(row.pname), district = clean(row.aname), subdistrict = clean(row.tname), village = clean(row.mname);
    if (pcode !== clean(expectedProvinceCode)) throw new Error(`province code mismatch at record ${line}: ${pcode}`);
    if (!province || !district || !subdistrict || !village) throw new Error(`missing administrative name at record ${line}`);
    if (province !== expectedProvinceName) throw new Error(`province name mismatch at record ${line}: ${province}`);
    const identity = `${pcode}:${mcode}`;
    if (seen.has(identity)) throw new Error(`duplicate village code ${identity}`);
    seen.add(identity);
    const latText = clean(row.lat), lonText = clean(row.long ?? row.lon);
    let latitude = null, longitude = null;
    if (latText || lonText) {
      latitude = Number(latText); longitude = Number(lonText);
      if (!latText || !lonText || !Number.isFinite(latitude) || !Number.isFinite(longitude)
        || latitude < THAILAND_BOUNDS.minLat || latitude > THAILAND_BOUNDS.maxLat
        || longitude < THAILAND_BOUNDS.minLon || longitude > THAILAND_BOUNDS.maxLon) {
        throw new Error(`invalid Thailand coordinates at record ${line}`);
      }
    }
    return {
      admin_code: `TH-VILLAGE:${identity}`, source_record_id: identity,
      name_th: village, province, district, subdistrict, subdistrict_code: tcode,
      latitude, longitude,
      source_payload_hash: crypto.createHash('sha256').update(JSON.stringify(row)).digest('hex')
    };
  });
  return { rows, provinceCode: clean(expectedProvinceCode), provinceName: expectedProvinceName };
}

function sqlLiteral(value) { return `'${String(value).replace(/'/g, "''")}'`; }
function renderDopaVillageImport(data, { resourceUrl, retrievedAt, checksum } = {}) {
  if (!resourceUrl || !/^https:\/\//.test(resourceUrl)) throw new Error('an HTTPS DOPA resource URL is required');
  if (!retrievedAt || !Number.isFinite(Date.parse(retrievedAt))) throw new Error('a valid retrieval timestamp is required');
  if (!/^[a-f0-9]{64}$/i.test(checksum || '')) throw new Error('verified SHA-256 checksum is required');
  const json = JSON.stringify(data.rows).replace(/'/g, "''");
  const at = sqlLiteral(new Date(retrievedAt).toISOString());
  const url = sqlLiteral(resourceUrl);
  const fileHash = sqlLiteral(checksum.toLowerCase());
  const parsedResourceUrl = new URL(resourceUrl);
  const resourceId = /\/resource\/([0-9a-f-]{36})(?:\/|$)/i.exec(parsedResourceUrl.pathname)?.[1];
  if (parsedResourceUrl.protocol !== 'https:' || parsedResourceUrl.hostname !== 'catalog.dopa.go.th' || !resourceId) throw new Error('an official DOPA catalog resource URL with its resource UUID is required');
  const sourceRecordExpr = `(${sqlLiteral(resourceId)}||':'||i.source_record_id)`;
  const resourceEntry = `jsonb_build_object('province_name',${sqlLiteral(data.provinceName)},'resource_id',${sqlLiteral(resourceId)},'resource_url',${url},'retrieved_at',${at},'sha256',${fileHash})`;
  const resourceRegistry = `jsonb_build_object(${sqlLiteral(data.provinceCode)},${resourceEntry})`;
  return `-- DOPA GIS-01 village subset; Open Data Common; DOPA attribution required.\n-- Resource: ${resourceUrl}\n-- Retrieval: ${new Date(retrievedAt).toISOString()}\n-- Resource SHA-256: ${checksum.toLowerCase()}\n-- Only village/admin names, administrative codes and public coordinates are imported.\nBEGIN;\nWITH incoming AS (\n SELECT * FROM jsonb_to_recordset(${sqlLiteral(json)}::jsonb) AS r(\n  admin_code text,source_record_id text,name_th text,province text,district text,subdistrict text,subdistrict_code text,latitude double precision,longitude double precision,source_payload_hash text\n )\n), upserted AS (\n INSERT INTO public.queuego_places(canonical_name,name_th,name_en,aliases,category,subcategory,latitude,longitude,address,village,moo,subdistrict,district,province,postcode,country,country_code,source,source_id,source_license,source_updated_at,verification_status,verification_method,confidence,dedupe_key,parent_place_id,admin_level,admin_code)\n SELECT i.name_th,i.name_th,NULL,'{}'::text[],'administrative_area','village',i.latitude,i.longitude,NULL,i.name_th,NULL,i.subdistrict,i.district,i.province,NULL,'Thailand','TH','${SOURCE_KEY}',${sourceRecordExpr},'Open Data Common',${at}::timestamptz,'verified','licensed_dopa_gis01',0.980,'dopa-village:'||i.admin_code,p.place_id,'village',i.admin_code\n FROM incoming i LEFT JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.subdistrict_code\n ON CONFLICT(country,admin_code) WHERE admin_code IS NOT NULL DO UPDATE SET canonical_name=excluded.canonical_name,name_th=excluded.name_th,latitude=excluded.latitude,longitude=excluded.longitude,subdistrict=excluded.subdistrict,district=excluded.district,province=excluded.province,source=excluded.source,source_id=excluded.source_id,source_license=excluded.source_license,source_updated_at=excluded.source_updated_at,verification_status=excluded.verification_status,verification_method=excluded.verification_method,confidence=excluded.confidence,parent_place_id=excluded.parent_place_id,updated_at=now()\n RETURNING place_id,admin_code\n)\nINSERT INTO public.queuego_place_source_records(source,source_record_id,place_id,source_license,source_updated_at,source_payload_hash)\nSELECT '${SOURCE_KEY}',${sourceRecordExpr},p.place_id,'Open Data Common',${at}::timestamptz,i.source_payload_hash FROM incoming i JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.admin_code\nON CONFLICT(source,source_record_id) DO UPDATE SET place_id=excluded.place_id,source_license=excluded.source_license,source_updated_at=excluded.source_updated_at,source_payload_hash=excluded.source_payload_hash,imported_at=now();\n\nWITH incoming AS (SELECT * FROM jsonb_to_recordset(${sqlLiteral(json)}::jsonb) AS r(admin_code text,source_record_id text,name_th text,province text,district text,subdistrict text,subdistrict_code text,latitude double precision,longitude double precision,source_payload_hash text))\nINSERT INTO public.queuego_place_field_provenance(place_id,field_name,source,source_record_id,observed_at,confidence,raw_value)\nSELECT p.place_id,v.field_name,'${SOURCE_KEY}',${sourceRecordExpr},${at}::timestamptz,0.980,v.raw_value FROM incoming i JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.admin_code\nCROSS JOIN LATERAL (VALUES ('canonical_name',to_jsonb(i.name_th)),('name_th',to_jsonb(i.name_th)),('village',to_jsonb(i.name_th)),('province',to_jsonb(i.province)),('district',to_jsonb(i.district)),('subdistrict',to_jsonb(i.subdistrict)),('latitude',to_jsonb(i.latitude)),('longitude',to_jsonb(i.longitude)),('admin_level',to_jsonb('village'::text)),('admin_code',to_jsonb(i.admin_code)),('source_id',to_jsonb(i.source_record_id)),('source_license',to_jsonb('Open Data Common'::text)),('source_updated_at',to_jsonb(${at}::timestamptz)),('verification_status',to_jsonb('verified'::text)),('verification_method',to_jsonb('licensed_dopa_gis01'::text)),('confidence',to_jsonb(0.980::numeric))) AS v(field_name,raw_value)\nON CONFLICT(place_id,field_name,source,source_record_id) WHERE source_record_id IS NOT NULL DO UPDATE SET observed_at=excluded.observed_at,confidence=excluded.confidence,raw_value=excluded.raw_value;\n\nWITH incoming AS (SELECT * FROM jsonb_to_recordset(${sqlLiteral(json)}::jsonb) AS r(admin_code text,name_th text))\nINSERT INTO public.queuego_place_aliases(place_id,alias,language_code,source)\nSELECT p.place_id,i.name_th,'th','${SOURCE_KEY}' FROM incoming i JOIN public.queuego_places p ON p.country_code='TH' AND p.admin_code=i.admin_code\nON CONFLICT(place_id,normalized_alias) DO UPDATE SET language_code=excluded.language_code,source=excluded.source;\n\nUPDATE public.queuego_place_sources SET metadata=jsonb_set(jsonb_set(metadata,'{resource_imports}',coalesce(metadata->'resource_imports','{}'::jsonb)||${resourceRegistry},true),'{resource_import_history}',coalesce(metadata->'resource_import_history','[]'::jsonb)||jsonb_build_array(${resourceEntry}),true),updated_at=now() WHERE source_key='${SOURCE_KEY}';\nCOMMIT;\n`;
}

if (require.main === module) {
  const [input, output, provinceCode, provinceName, resourceUrl, retrievedAt, expectedSha256] = process.argv.slice(2);
  if (!input || !provinceCode || !provinceName || !resourceUrl || !retrievedAt || !expectedSha256) {
    console.error('Usage: node scripts/import-dopa-villages.cjs <province.json> <output.sql> <province-code> <province-name-th> <resource-url> <retrieved-at-ISO> <sha256>');
    process.exit(2);
  }
  const buffer = fs.readFileSync(path.resolve(input));
  const actualSha = sha256(buffer);
  if (actualSha !== expectedSha256.toLowerCase()) throw new Error(`source SHA-256 mismatch: got ${actualSha}`);
  const data = parseDopaVillageDataset(buffer.toString('utf8'), { expectedProvinceCode: provinceCode, expectedProvinceName: provinceName, rawBuffer: buffer, expectedSha256 });
  fs.writeFileSync(path.resolve(output), renderDopaVillageImport(data, { resourceUrl, retrievedAt, checksum: actualSha }), 'utf8');
  console.error(`validated ${data.rows.length} villages for ${provinceName}; resource SHA-256 ${actualSha}`);
}

module.exports = { parseDopaVillageDataset, renderDopaVillageImport, sha256, extractRecords, SOURCE_KEY };
