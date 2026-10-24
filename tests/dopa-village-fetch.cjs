'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const manifest = require('../data/maps/dopa-gis01-resources.json');
const { downloadDopaVillageResource, assertOfficialResource } = require('../scripts/fetch-dopa-village.cjs');
const { stageDopaVillageBatch } = require('../scripts/stage-dopa-village-batch.cjs');

const row = { pcode:'31',pname:'บุรีรัมย์',acode:'3101',aname:'เมืองบุรีรัมย์',tcode:'310101',tname:'ในเมือง',mcode:'31010101',mname:'บ้านหลักเมือง',lat:14.99,long:103.1 };
const bytes = Buffer.from(JSON.stringify([row]));
const fakeResponse = (status, body = null, headers = {}) => new Response(body, { status, headers });
const buriram = manifest.resources.find(item => item.province_code === '31');
let checks = 0;
const ok = (condition, message) => { assert.ok(condition, message); checks++; };

(async () => {
  const validated = assertOfficialResource(buriram, manifest);
  ok(validated.hostname === 'catalog.dopa.go.th', 'registered resource uses the official HTTPS DOPA host');
  for (const url of ['http://catalog.dopa.go.th/dataset/x/resource/y/download/.json','https://example.org/dataset/x/resource/y/download/.json']) {
    assert.throws(() => assertOfficialResource({ ...buriram, official_download_url:url }, manifest), /official DOPA HTTPS host/); checks++;
  }
  assert.throws(() => assertOfficialResource({ ...buriram, official_download_url:buriram.official_download_url.replace(manifest.dataset_id,'other') }, manifest), /does not match/); checks++;

  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'qg-dopa-fetch-'));
  try {
    const result = await downloadDopaVillageResource({ provinceCode:'31',outputDirectory:dir,manifest,fetchImpl:async (_url, options) => {
      ok(options.redirect === 'manual' && options.method === 'GET', 'download refuses automatic redirects');
      return fakeResponse(200, bytes, { 'content-type':'application/json', 'content-length':String(bytes.length) });
    } });
    ok(result.import_eligible === false, 'acquisition never enables importing');
    ok(result.validated_row_count === 1 && /^[a-f0-9]{64}$/.test(result.sha256), 'receipt records validated rows and exact-byte SHA-256');
    ok((await fs.readFile(result.filePath)).equals(bytes), 'verified source bytes are saved without rewriting');
    assert.equal(result.filePath,path.join(dir,buriram.resource_id+'.json'));checks++;
    assert.equal(result.metadataPath,path.join(dir,buriram.resource_id+'.meta.json'));checks++;
    const receipt = JSON.parse(await fs.readFile(result.metadataPath, 'utf8'));
    ok(receipt.resource_id === buriram.resource_id && receipt.license === 'Open Data Common' && receipt.resource_url === buriram.official_download_url, 'sidecar preserves source identity, registered URL and license');
    const staged = stageDopaVillageBatch({ manifest, inputDir:dir, outputDir:path.join(dir,'staged') });
    ok(staged.summary.total === 76 && staged.summary.staged === 1 && staged.summary.missing === 75 && staged.summary.blocked === 0, 'verified fetch output feeds existing nationwide batch staging');
    ok(staged.resources.find(item => item.resource_id === buriram.resource_id).import_candidate === true
      && manifest.resources.find(item => item.resource_id === buriram.resource_id).import_eligible === false,
    'batch SQL remains a review candidate and never enables the master manifest');

    await assert.rejects(() => downloadDopaVillageResource({ provinceCode:'31',outputDirectory:dir,manifest,fetchImpl:async () => fakeResponse(302, null, { location:'https://evil.example/payload.json' }) }), /redirect left the official source host/);
    await assert.rejects(() => downloadDopaVillageResource({ provinceCode:'31',outputDirectory:dir,manifest,maxBytes:10,fetchImpl:async () => fakeResponse(200, bytes) }), /exceeds 10 bytes/);
    await assert.rejects(() => downloadDopaVillageResource({ provinceCode:'31',outputDirectory:dir,manifest,fetchImpl:async () => fakeResponse(502) }), /HTTP 502/);
    await assert.rejects(() => downloadDopaVillageResource({ provinceCode:'31',outputDirectory:dir,manifest,fetchImpl:async () => fakeResponse(200, Buffer.from(JSON.stringify([{...row,pcode:'10',pname:'กรุงเทพมหานคร'}]))) }), /province code mismatch/);
    await assert.rejects(() => downloadDopaVillageResource({ provinceCode:'31',outputDirectory:dir,manifest,fetchImpl:async () => fakeResponse(200, Buffer.from('<html>maintenance</html>')) }), /invalid DOPA JSON/);
    ok(true, 'redirect, HTTP, size, province and malformed JSON failures stop ingestion');
  } finally { await fs.rm(dir, { recursive:true, force:true }); }
  console.log(JSON.stringify({ checks, failures:0, scope:'official-host DOPA acquisition into UUID batch contract, redirect/size/HTTP/schema/hash validation and review-only SQL staging; mocked network only' }));
})().catch(error => { console.error(error); process.exitCode = 1; });
