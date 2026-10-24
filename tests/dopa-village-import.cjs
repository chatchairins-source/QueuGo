'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { parseDopaVillageDataset, renderDopaVillageImport, sha256, SOURCE_KEY } = require('../scripts/import-dopa-villages.cjs');
const { stageDopaVillageBatch, validateManifest } = require('../scripts/stage-dopa-village-batch.cjs');
const manifest = JSON.parse(fs.readFileSync(path.join(__dirname,'../data/maps/dopa-gis01-resources.json'),'utf8'));
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
assert.equal(manifest.dataset_id, '705402d4-2715-4ece-9166-22b54c6c3476');
assert.equal(manifest.license, 'Open Data Common');
assert.equal(manifest.resources.length, 76);
assert.equal(new Set(manifest.resources.map(r => r.province_code)).size, 76);
assert.equal(new Set(manifest.resources.map(r => r.resource_id)).size, 76);
assert.ok(!manifest.resources.some(r => r.province_code === '10'));
assert.ok(manifest.resources.every(r => r.import_eligible === false && r.sha256 === null && r.validated_row_count === null));
assert.ok(manifest.resources.every(r => r.official_download_url.includes('/resource/'+r.resource_id+'/download/.json')));
const buriram = manifest.resources.find(r => r.province_code === '31');
assert.ok(buriram);
assert.equal(buriram.resource_id, '2e6697b4-6efe-46b5-940a-0d7b2caf771f');
assert.equal(buriram.sha256, null);
assert.equal(buriram.import_eligible, false);
const resourceUrl = buriram.official_download_url;
const sql = renderDopaVillageImport(data, { resourceUrl, retrievedAt: '2026-10-24T00:00:00Z', checksum: sha256(raw) });
assert.match(sql, /Open Data Common/);
assert.match(sql, /DOPA attribution required/);
assert.match(sql, /queuego_place_source_records/);
assert.match(sql, /queuego_place_field_provenance/);
assert.match(sql, /resource_imports/);
assert.match(sql, /'Open Data Common',NULL,'verified'/);
assert.match(sql, /resource_import_history/);
assert.match(sql, /2e6697b4-6efe-46b5-940a-0d7b2caf771f/);
assert.match(sql, /i\.subdistrict_code/);
assert.doesNotMatch(sql, /population|q01/);
assert.throws(() => parseDopaVillageDataset(raw.toString(), { expectedProvinceCode: '10', expectedProvinceName: 'กรุงเทพมหานคร' }), /province code mismatch/);
assert.throws(() => parseDopaVillageDataset(JSON.stringify([sample[0], sample[0]]), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์' }), /duplicate village code/);
assert.throws(() => parseDopaVillageDataset(JSON.stringify([{ ...sample[0], lat: 99 }]), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์' }), /coordinates/);
assert.throws(() => parseDopaVillageDataset(raw.toString(), { expectedProvinceCode: '31', expectedProvinceName: 'บุรีรัมย์', rawBuffer: raw, expectedSha256: '0'.repeat(64) }), /SHA-256 mismatch/);
assert.throws(() => renderDopaVillageImport(data, { resourceUrl: 'https://example.org/resource/not-dopa', retrievedAt: '2026-10-24T00:00:00Z', checksum: sha256(raw) }), /resource UUID/);
assert.equal(SOURCE_KEY, 'th_dopa_village_gis');

const batchRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'queuego-dopa-stage-'));
const batchInput = path.join(batchRoot, 'input');
const batchOutput = path.join(batchRoot, 'output');
fs.mkdirSync(batchInput, { recursive: true });
const missingProvince = manifest.resources.find(r => r.province_code !== '31');
const batchManifest = { ...manifest, resources: [buriram, missingProvince] };
fs.writeFileSync(path.join(batchInput, buriram.resource_id + '.json'), raw);
fs.writeFileSync(path.join(batchInput, buriram.resource_id + '.meta.json'), JSON.stringify({
  resource_id: buriram.resource_id,
  resource_url: buriram.official_download_url,
  retrieved_at: '2026-10-24T00:00:00Z'
}));
const batch = stageDopaVillageBatch({ manifest: batchManifest, inputDir: batchInput, outputDir: batchOutput });
assert.deepEqual(batch.summary, { total: 2, staged: 1, missing: 1, blocked: 0 });
const staged = batch.resources.find(r => r.resource_id === buriram.resource_id);
assert.equal(staged.status, 'staged');
assert.equal(staged.sha256, sha256(raw));
assert.equal(staged.validated_row_count, 2);
assert.equal(staged.import_candidate, true);
assert.ok(fs.existsSync(path.join(batchOutput, staged.sql_file)));
assert.equal(buriram.sha256, null);
assert.equal(buriram.import_eligible, false);
assert.doesNotThrow(() => validateManifest(batchManifest));

const blockedOutput = path.join(batchRoot, 'blocked');
fs.writeFileSync(path.join(batchInput, buriram.resource_id + '.meta.json'), JSON.stringify({
  resource_id: buriram.resource_id,
  resource_url: 'https://example.org/not-official.json',
  retrieved_at: '2026-10-24T00:00:00Z'
}));
const blocked = stageDopaVillageBatch({ manifest: batchManifest, inputDir: batchInput, outputDir: blockedOutput });
assert.equal(blocked.summary.blocked, 1);
assert.equal(blocked.resources.find(r => r.resource_id === buriram.resource_id).status, 'blocked_invalid_payload');
assert.equal(fs.readdirSync(blockedOutput).filter(name => name.endsWith('.sql')).length, 0);
fs.rmSync(batchRoot, { recursive: true, force: true });

console.log(JSON.stringify({ checks: 43, failures: 0, scope: 'DOPA GIS-01 importer schema, nationwide resource registry, provenance, dedupe, license, batch staging and source integrity contracts' }));
