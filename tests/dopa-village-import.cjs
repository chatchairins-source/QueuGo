'use strict';
const assert = require('node:assert/strict');
const { parseDopaVillageDataset, renderDopaVillageImport, sha256, SOURCE_KEY } = require('../scripts/import-dopa-villages.cjs');
const sample = [
  { pcode: 31, pname: 'บุรีรัมย์', acode: 3101, aname: 'เมืองบุรีรัมย์', tcode: 310101, tname: 'ในเมือง', mcode: 31010101, mname: 'บ้านหลักเมือง', lat: 14.99, long: 103.1, q01: 'ignored' },
  { pcode: '31', pname: 'บุรีรัมย์', acode: '3101', aname: 'เมืองบุรีรัมย์', tcode: '310101', tname: 'ในเมือง', mcode: '31010102', mname: 'บ้านสถานี', lat: null, long: null, population: 1234 }
];
const raw = Buffer.from(JSON.stringify(sample));
const data = parseDopaVillageDataset(raw.toString(), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์', rawBuffer: raw, expectedSha256: sha256(raw) });
assert.equal(data.rows.length, 2);
assert.equal(data.rows[0].admin_code, 'TH-VILLAGE:31:31010101');
assert.equal(data.rows[0].source_record_id, '31:31010101');
assert.equal(data.rows[0].subdistrict_code, '310101');
assert.equal(data.rows[0].latitude, 14.99);
assert.equal(data.rows[1].latitude, null);
const resourceUrl = 'https://catalog.dopa.go.th/dataset/example/resource/01234567-89ab-cdef-0123-456789abcdef/download/.json';
const sql = renderDopaVillageImport(data, { resourceUrl, retrievedAt: '2026-10-24T00:00:00Z', checksum: sha256(raw) });
assert.match(sql, /Open Data Common/);
assert.match(sql, /DOPA attribution required/);
assert.match(sql, /queuego_place_source_records/);
assert.match(sql, /queuego_place_field_provenance/);
assert.match(sql, /resource_imports/);
assert.match(sql, /01234567-89ab-cdef-0123-456789abcdef/);
assert.match(sql, /i\.subdistrict_code/);
assert.doesNotMatch(sql, /population|q01/);
assert.throws(() => parseDopaVillageDataset(raw.toString(), { expectedProvinceCode: '10', expectedProvinceName: 'กรุงเทพมหานคร' }), /province code mismatch/);
assert.throws(() => parseDopaVillageDataset(JSON.stringify([sample[0], sample[0]]), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์' }), /duplicate village code/);
assert.throws(() => parseDopaVillageDataset(JSON.stringify([{ ...sample[0], lat: 99 }]), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์' }), /coordinates/);
assert.throws(() => parseDopaVillageDataset(raw.toString(), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์', rawBuffer: raw, expectedSha256: '0'.repeat(64) }), /SHA-256 mismatch/);
assert.throws(() => renderDopaVillageImport(data, { resourceUrl: 'https://example.org/resource/not-dopa', retrievedAt: '2026-10-24T00:00:00Z', checksum: sha256(raw) }), /resource UUID/);
assert.equal(SOURCE_KEY, 'th_dopa_village_gis');
console.log(JSON.stringify({ checks: 17, failures: 0, scope: 'DOPA GIS-01 importer schema, provenance, dedupe, license and source integrity contracts' }));
