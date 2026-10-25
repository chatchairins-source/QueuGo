#!/usr/bin/env node
'use strict';

// Fetch one reviewed DOPA GIS-01 resource to a local review directory.
// This never changes the source manifest or writes to QueueGo/Supabase.
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const { parseDopaVillageDataset, sha256 } = require('./import-dopa-villages.cjs');

const MANIFEST_PATH = path.resolve(__dirname, '../data/maps/dopa-gis01-resources.json');
const OFFICIAL_HOST = 'catalog.dopa.go.th';
const DEFAULT_MAX_BYTES = 100 * 1024 * 1024;

function assertOfficialResource(resource, manifest) {
  if (!resource || !manifest || manifest.license !== 'Open Data Common') throw new Error('licensed DOPA GIS-01 resource is required');
  const url = new URL(resource.official_download_url);
  if (url.protocol !== 'https:' || url.hostname !== OFFICIAL_HOST) throw new Error('download URL must use the official DOPA HTTPS host');
  if (!/^[0-9a-f-]{36}$/i.test(resource.resource_id || '')) throw new Error('DOPA resource UUID is required');
  const expectedPath = `/dataset/${manifest.dataset_id}/resource/${resource.resource_id}/download/.json`;
  if (url.pathname !== expectedPath) throw new Error('download URL does not match the registered DOPA dataset/resource IDs');
  if (!/^\d{1,2}$/.test(resource.province_code || '') || !resource.province_name_th) throw new Error('province code and name are required');
  return url;
}

async function readBoundedBody(response, maxBytes) {
  const declared = Number(response.headers?.get?.('content-length'));
  if (Number.isFinite(declared) && declared > maxBytes) throw new Error(`DOPA payload exceeds ${maxBytes} bytes`);
  if (!response.body?.getReader) throw new Error('DOPA response body is unavailable');
  const reader = response.body.getReader(), chunks = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) {
        await reader.cancel();
        throw new Error(`DOPA payload exceeds ${maxBytes} bytes`);
      }
      chunks.push(Buffer.from(value));
    }
  } finally {
    reader.releaseLock?.();
  }
  return Buffer.concat(chunks, size);
}

async function fetchOfficialBytes(url, fetchImpl, { maxBytes, timeoutMs }) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  let current = url;
  try {
    for (let redirects = 0; redirects <= 3; redirects++) {
      const response = await fetchImpl(current.href, { method: 'GET', redirect: 'manual', signal: controller.signal, headers: { accept: 'application/json' } });
      if ([301, 302, 303, 307, 308].includes(response.status)) {
        if (redirects === 3) throw new Error('too many redirects from DOPA resource');
        const location = response.headers?.get?.('location');
        if (!location) throw new Error('DOPA redirect is missing a location');
        const next = new URL(location, current);
        if (next.protocol !== 'https:' || next.hostname !== OFFICIAL_HOST) throw new Error('DOPA redirect left the official source host');
        current = next;
        continue;
      }
      if (!response.ok) throw new Error(`DOPA download failed with HTTP ${response.status}`);
      const bytes = await readBoundedBody(response, maxBytes);
      return { bytes, finalUrl: current.href };
    }
    throw new Error('DOPA redirect limit exceeded');
  } catch (error) {
    if (controller.signal.aborted) throw new Error(`DOPA download timed out after ${timeoutMs}ms`);
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

async function writeExclusiveOrVerify(filePath, bytes, digest) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  const temporary = `${filePath}.${process.pid}.${crypto.randomBytes(6).toString('hex')}.tmp`;
  await fs.writeFile(temporary, bytes, { flag: 'wx' });
  try {
    await fs.copyFile(temporary, filePath, require('node:fs').constants.COPYFILE_EXCL);
    await fs.unlink(temporary);
  } catch (error) {
    if (error.code !== 'EEXIST' && error.code !== 'ENOTEMPTY') throw error;
    const existing = await fs.readFile(filePath);
    if (sha256(existing) !== digest) throw new Error('existing DOPA file has different bytes');
    await fs.unlink(temporary).catch(() => {});
  }
}

async function downloadDopaVillageResource({ provinceCode, outputDirectory, fetchImpl = globalThis.fetch, manifest, maxBytes = DEFAULT_MAX_BYTES, timeoutMs = 30000 }) {
  if (typeof fetchImpl !== 'function') throw new Error('Fetch API is unavailable');
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1) throw new Error('maxBytes must be a positive integer');
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1) throw new Error('timeoutMs must be a positive integer');
  const sourceManifest = manifest || JSON.parse(await fs.readFile(MANIFEST_PATH, 'utf8'));
  const resource = sourceManifest.resources?.find(row => String(row.province_code) === String(provinceCode));
  if (!resource) throw new Error(`no DOPA GIS-01 resource registered for province ${provinceCode}`);
  const url = assertOfficialResource(resource, sourceManifest);
  const { bytes, finalUrl } = await fetchOfficialBytes(url, fetchImpl, { maxBytes, timeoutMs });
  const digest = sha256(bytes);
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  const parsed = parseDopaVillageDataset(text, {
    expectedProvinceCode: String(resource.province_code),
    expectedProvinceName: resource.province_name_th,
    rawBuffer: bytes,
    expectedSha256: digest
  });
  const retrievedAt = new Date().toISOString();
  // Match the UUID-based input contract of stage-dopa-village-batch.cjs.
  const fileName = `${resource.resource_id}.json`;
  const filePath = path.resolve(outputDirectory, fileName);
  await writeExclusiveOrVerify(filePath, bytes, digest);
  const metadataPath = `${filePath.slice(0, -5)}.meta.json`;
  const receipt = {
    dataset_id: sourceManifest.dataset_id,
    resource_id: resource.resource_id,
    province_code: String(resource.province_code),
    province_name_th: resource.province_name_th,
    source_url: url.href,
    resource_url: url.href,
    final_url: finalUrl,
    license: sourceManifest.license,
    retrieved_at: retrievedAt,
    sha256: digest,
    validated_row_count: parsed.rows.length,
    payload_path: fileName,
    import_eligible: false
  };
  await fs.writeFile(metadataPath, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }).catch(async error => {
    if (error.code !== 'EEXIST') throw error;
    const existing = JSON.parse(await fs.readFile(metadataPath, 'utf8'));
    if (existing.resource_id !== resource.resource_id || existing.resource_url !== url.href
      || existing.sha256 !== digest || existing.validated_row_count !== parsed.rows.length) {
      throw new Error('existing DOPA receipt conflicts with verified bytes');
    }
  });
  return { ...receipt, filePath, metadataPath };
}

async function main(argv) {
  const args = Object.fromEntries(argv.reduce((pairs, value, index) => value.startsWith('--') ? [...pairs, [value.slice(2), argv[index + 1]]] : pairs, []));
  if (!args['province-code'] || !args['output-directory']) throw new Error('usage: node scripts/fetch-dopa-village.cjs --province-code <code> --output-directory <dir>');
  const result = await downloadDopaVillageResource({ provinceCode: args['province-code'], outputDirectory: args['output-directory'] });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

if (require.main === module) main(process.argv.slice(2)).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });

module.exports = { DEFAULT_MAX_BYTES, OFFICIAL_HOST, assertOfficialResource, downloadDopaVillageResource, fetchOfficialBytes };
