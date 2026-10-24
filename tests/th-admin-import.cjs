'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { parseDataset, renderImportSql, gitBlobSha, PINNED_GIT_BLOB_SHA, EXPECTED_COUNTS } = require('../scripts/import-th-admin-places.cjs');
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
console.log(JSON.stringify({checks:29, failures:0, scope:'pinned Thai administrative import and DOPA village source registry contracts'}));
