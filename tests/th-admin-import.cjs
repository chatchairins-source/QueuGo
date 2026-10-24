'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');
const { parseDataset, renderImportSql, gitBlobSha, PINNED_GIT_BLOB_SHA, PINNED_SUBDISTRICT_GIT_BLOB_SHA, PINNED_SOURCE_COMMIT_SHA, EXPECTED_COUNTS } = require('../scripts/import-th-admin-places.cjs');
const registrySql = fs.readFileSync(path.join(__dirname, '../supabase/migrations/20261023195500_queuego_th_admin_source_registry.sql'), 'utf8');

const records = [
  { id:'10', level:1, code:{id:'10'}, name:{local:'กรุงเทพมหานคร',en:'Bangkok',slug:'bangkok-10'}, parent:null, geo:{lat:'13.753',lon:'100.5'}, zip_codes:[] },
  { id:'1001', level:2, code:{id:'1001'}, name:{local:'พระนคร',en:'Phra Nakhon',slug:'phra-nakhon-1001'}, parent:{id:'10'}, geo:{lat:'13.753',lon:'100.5'}, zip_codes:[] },
  { id:'100101', level:3, code:{id:'100101'}, name:{local:'พระบรมมหาราชวัง',en:'Phra Borom Maha Ratchawang',slug:'phra-borom-maha-ratchawang-100101'}, parent:{id:'1001'}, geo:{lat:'13.75',lon:'100.49'}, zip_codes:['10200'] }
];
const input = records.map(row => JSON.stringify(row)).join('\n');
const data = parseDataset(input, { expectedCounts:{1:1,2:1,3:1} });
assert.deepEqual(data.counts, {1:1,2:1,3:1});
assert.equal(data.rows[2].province, 'กรุงเทพมหานคร');
assert.equal(data.rows[2].district, 'พระนคร');
assert.equal(data.rows[2].postcode, '10200');
assert.equal(data.rows[2].latitude, 13.75);
assert.equal(data.rows[2].admin_level, 'subdistrict');
assert.equal(EXPECTED_COUNTS[1], 77);
assert.equal(EXPECTED_COUNTS[2], 928);
assert.equal(EXPECTED_COUNTS[3], 7364);
assert.match(PINNED_GIT_BLOB_SHA, /^[a-f0-9]{40}$/);
assert.match(PINNED_SUBDISTRICT_GIT_BLOB_SHA, /^[a-f0-9]{40}$/);
assert.match(PINNED_SOURCE_COMMIT_SHA, /^[a-f0-9]{40}$/);
assert.match(gitBlobSha(Buffer.from('test')), /^[a-f0-9]{40}$/);
assert.throws(() => parseDataset(input, {expectedCounts:{1:77,2:928,3:7364}}), /count mismatch/);
assert.throws(() => parseDataset(input.replace('"lon":"100.49"','"lon":"999"'), {expectedCounts:{1:1,2:1,3:1}}), /coordinates/);
assert.throws(() => parseDataset(input.replace('"id":"1001"','"id":"1001"').replace('"parent":{"id":"10"}','"parent":{"id":"missing"}'), {expectedCounts:{1:1,2:1,3:1}}), /parent/);
const sql = renderImportSql(data);
assert.match(sql, /CC BY 4\.0/);
assert.match(sql, /ON CONFLICT\(country,admin_code\)/);
assert.match(sql, /ON CONFLICT\(place_id,field_name,source,source_record_id\) WHERE source_record_id IS NOT NULL/);
assert.match(registrySql, /unique index if not exists queuego_place_field_provenance_source_field_uidx[\s\S]*?\(place_id,field_name,source,source_record_id\)[\s\S]*?where source_record_id is not null/i);
assert.match(registrySql, /'th_dopa_village_gis','Department of Provincial Administration Village GIS','Open Data Common'/);
assert.match(registrySql, /'th_dopa_village_gis'[\s\S]*?\n true,true,\n '\{"catalog_url":"https:\/\/catalog\.dopa\.go\.th\/dataset\/gis-01","dataset_id":"gis-01","published_province_count":76/s);
assert.match(registrySql, /Credit the Department of Provincial Administration[\s\S]*?record each resource URL, retrieval time, checksum, and source record ID/i);
assert.match(registrySql, /Import village name, hierarchy, and coordinates only; do not ingest population attributes/i);
assert.match(sql, /th_admin_open_data/);
assert.match(sql, /'province'/);
assert.match(sql, /'subdistrict'/);
assert.match(sql, /Bangkok/);
assert.match(sql, /\u0e1e\u0e23\u0e30/);
assert.match(sql, /source_payload_hash/);
assert.match(sql, /\('admin_code',to_jsonb\(i\.id\)\)/);
assert.match(sql, /\('parent_place_id',to_jsonb\(i\.parent_code\)\)/);
assert.match(sql, /all-subdistrict\.json Git blob SHA/);
assert.match(sql, /repository commit: eef739a534e26181c86073d69e9aeb527110cf1a/);
const flatBuffer = Buffer.from(records.slice(0, 2).map(row => JSON.stringify(row)).join('\n'));
const subdistrictBuffer = Buffer.from(JSON.stringify([records[2]]));
const separateFiles = parseDataset(flatBuffer.toString('utf8'), {
  rawBuffer: flatBuffer, pinnedSha: gitBlobSha(flatBuffer),
  subdistrictText: subdistrictBuffer.toString('utf8'), subdistrictBuffer,
  pinnedSubdistrictSha: gitBlobSha(subdistrictBuffer), expectedCounts: {1:1,2:1,3:1}
});
assert.deepEqual(separateFiles.counts, {1:1,2:1,3:1});
assert.equal(separateFiles.rows[2].province, 'กรุงเทพมหานคร');
assert.equal(separateFiles.rows[2].district, 'พระนคร');
assert.throws(() => parseDataset(flatBuffer.toString('utf8'), {
  rawBuffer: flatBuffer, pinnedSha: gitBlobSha(flatBuffer),
  subdistrictText: subdistrictBuffer.toString('utf8'), subdistrictBuffer,
  pinnedSubdistrictSha: 'f'.repeat(40), expectedCounts: {1:1,2:1,3:1}
}), /subdistrict snapshot blob SHA mismatch/);
assert.throws(() => parseDataset(records.map(row => JSON.stringify(row)).join('\n'), {
  rawBuffer: flatBuffer, pinnedSha: gitBlobSha(flatBuffer),
  subdistrictText: subdistrictBuffer.toString('utf8'), subdistrictBuffer,
  pinnedSubdistrictSha: gitBlobSha(subdistrictBuffer), expectedCounts: {1:1,2:1,3:1}
}), /all-flat source must contain province and district records only/);
assert.match(registrySql, /repository_commit":"eef739a534e26181c86073d69e9aeb527110cf1a/);
assert.match(registrySql, /all-subdistrict\.json","git_blob_sha":"bd5c49a7e1af578c9e34e32020ddd90eabdc167f/);

(async () => {
  const db = new PGlite();
  try {
    await db.exec(`create table public.queuego_places(place_id bigserial primary key,canonical_name text not null,name_th text,name_en text,aliases text[] not null,category text,subcategory text,latitude double precision,longitude double precision,address text,village text,moo text,subdistrict text,district text,province text,postcode text,country text,country_code text,source text,source_id text,source_license text,source_updated_at timestamptz,verification_status text,verification_method text,confidence numeric,dedupe_key text,parent_place_id bigint,admin_level text,admin_code text,updated_at timestamptz not null default now());
      create unique index queuego_places_admin_uq on public.queuego_places(country,admin_code) where admin_code is not null;
      create table public.queuego_place_source_records(source text,source_record_id text,place_id bigint,source_license text,source_updated_at timestamptz,source_payload_hash text,imported_at timestamptz default now(),unique(source,source_record_id));
      create table public.queuego_place_field_provenance(place_id bigint,field_name text,source text,source_record_id text,observed_at timestamptz,confidence numeric,raw_value jsonb);
      create unique index queuego_prov_uq on public.queuego_place_field_provenance(place_id,field_name,source,source_record_id) where source_record_id is not null;
      create table public.queuego_place_aliases(place_id bigint,alias text,language_code text,source text,normalized_alias text generated always as (lower(alias)) stored);
      create unique index queuego_alias_uq on public.queuego_place_aliases(place_id,normalized_alias);`);
    const importSql = renderImportSql(separateFiles);
    await db.exec(importSql);
    const first = await db.query(`select (select count(*)::int from public.queuego_places) places,(select count(*)::int from public.queuego_place_source_records) source_rows,(select count(*)::int from public.queuego_place_field_provenance) provenance_rows,(select count(*)::int from public.queuego_place_aliases) aliases`);
    const sourceIds = await db.query(`select array_agg(source_record_id order by source_record_id) ids from public.queuego_place_source_records`);
    await db.exec(importSql);
    const second = await db.query(`select (select count(*)::int from public.queuego_places) places,(select count(*)::int from public.queuego_place_source_records) source_rows,(select count(*)::int from public.queuego_place_field_provenance) provenance_rows,(select count(*)::int from public.queuego_place_aliases) aliases`);
    assert.equal(first.rows[0].places, 3);
    assert.equal(first.rows[0].source_rows, 3);
    assert.ok(first.rows[0].provenance_rows > 0);
    assert.equal(first.rows[0].aliases, 3);
    assert.deepEqual(sourceIds.rows[0].ids, ['10', '1001', '100101']);
    assert.deepEqual(second.rows[0], first.rows[0]);
    console.log(JSON.stringify({checks:47, failures:0, scope:'pinned two-file Thai hierarchy, first-run source provenance, and idempotent review SQL in isolated PGlite mock schema; no PostGIS or Production schema/data'}));
  } finally {
    await db.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
