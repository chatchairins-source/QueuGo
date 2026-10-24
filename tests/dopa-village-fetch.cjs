'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  readManifest,
  getProvinceResource,
  assertAllowedDopaUrl,
  fetchProvinceResource,
  writeCandidate
} = require('../scripts/fetch-dopa-village-resource.cjs');

const manifest = readManifest();
assert.equal(manifest.resources.length, 76);
const buriram = getProvinceResource(manifest, '31');
assert.equal(buriram.province_name_th, 'บุรีรัมย์');
assert.equal(buriram.resource_id, '2e6697b4-6efe-46b5-940a-0d7b2caf771f');
assert.throws(() => getProvinceResource(manifest, '10'), /unknown DOPA province code/);
assert.equal(assertAllowedDopaUrl(buriram.official_download_url).hostname, 'catalog.dopa.go.th');
assert.equal(assertAllowedDopaUrl('https://opendata_tst.dopa.go.th/downloads/15/province.json').hostname, 'opendata_tst.dopa.go.th');
assert.throws(() => assertAllowedDopaUrl('http://catalog.dopa.go.th/file.json'), /approved DOPA HTTPS host/);
assert.throws(() => assertAllowedDopaUrl('https://example.org/file.json'), /approved DOPA HTTPS host/);

const sample = Buffer.from(JSON.stringify([
  { pcode: 31, pname: 'บุรีรัมย์', acode: 3101, aname: 'เมืองบุรีรัมย์', tcode: 310101, tname: 'ในเมือง', mcode: 31010101, mname: 'บ้านหลักเมือง', lat: 14.99, long: 103.1 },
  { pcode: 31, pname: 'บุรีรัมย์', acode: 3101, aname: 'เมืองบุรีรัมย์', tcode: 310101, tname: 'ในเมือง', mcode: 31010102, mname: 'บ้านสถานี', lat: 15.0, long: 103.11 }
]));
const asArrayBuffer = buffer => buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
const headers = values => ({ get: name => values[String(name).toLowerCase()] ?? null });

(async () => {
  const result = await fetchProvinceResource(buriram, {
    now: () => new Date('2026-10-24T03:30:00Z'),
    fetchImpl: async (url, options) => {
      assert.equal(url, buriram.official_download_url);
      assert.equal(options.method, 'GET');
      assert.equal(options.redirect, 'follow');
      return {
        ok: true,
        status: 200,
        url: 'https://opendata_tst.dopa.go.th/downloads/15/province.json',
        headers: headers({ 'content-length': String(sample.length) }),
        arrayBuffer: async () => asArrayBuffer(sample)
      };
    }
  });
  assert.equal(result.rowCount, 2);
  assert.match(result.checksum, /^[a-f0-9]{64}$/);
  assert.equal(result.finalUrl, 'https://opendata_tst.dopa.go.th/downloads/15/province.json');
  assert.equal(result.retrievedAt, '2026-10-24T03:30:00.000Z');

  await assert.rejects(() => fetchProvinceResource(buriram, {
    maxBytes: 8,
    fetchImpl: async () => ({
      ok: true,
      status: 200,
      url: buriram.official_download_url,
      headers: headers({ 'content-length': '99' }),
      arrayBuffer: async () => asArrayBuffer(sample)
    })
  }), /maximum allowed size/);

  await assert.rejects(() => fetchProvinceResource(buriram, {
    fetchImpl: async () => ({
      ok: true,
      status: 200,
      url: 'https://example.org/redirected.json',
      headers: headers({}),
      arrayBuffer: async () => asArrayBuffer(sample)
    })
  }), /approved DOPA HTTPS host/);

  await assert.rejects(() => fetchProvinceResource(buriram, {
    fetchImpl: async () => ({
      ok: false,
      status: 503,
      url: buriram.official_download_url,
      headers: headers({}),
      arrayBuffer: async () => asArrayBuffer(sample)
    })
  }), /HTTP 503/);

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'qg-dopa-fetch-'));
  try {
    const files = writeCandidate(dir, buriram, result);
    assert.ok(fs.existsSync(files.jsonPath));
    assert.ok(fs.existsSync(files.metadataPath));
    const metadata = JSON.parse(fs.readFileSync(files.metadataPath, 'utf8'));
    assert.equal(metadata.sha256, result.checksum);
    assert.equal(metadata.validated_row_count, 2);
    assert.equal(metadata.import_eligible, false);
    assert.equal(metadata.manifest_update_required, true);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }

  console.log(JSON.stringify({
    checks: 24,
    failures: 0,
    scope: 'guarded one-province DOPA download, host allowlist, checksum, schema validation and candidate evidence'
  }));
})().catch(error => {
  console.error(error?.stack || error);
  process.exit(1);
});
