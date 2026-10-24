#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { parseDopaVillageDataset, sha256 } = require('./import-dopa-villages.cjs');

const DEFAULT_MANIFEST = path.join(__dirname, '../data/maps/dopa-gis01-resources.json');
const DEFAULT_TIMEOUT_MS = 45000;
const DEFAULT_MAX_BYTES = 20 * 1024 * 1024;
const ALLOWED_DOPA_HOSTS = new Set(['catalog.dopa.go.th', 'opendata_tst.dopa.go.th']);

function readManifest(file = DEFAULT_MANIFEST) {
  const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!Array.isArray(manifest.resources) || manifest.resources.length !== 76) {
    throw new Error('DOPA manifest must contain exactly 76 province resources');
  }
  return manifest;
}

function getProvinceResource(manifest, provinceCode) {
  const code = String(provinceCode || '').trim();
  const resource = manifest.resources.find(row => row.province_code === code);
  if (!resource) throw new Error(`unknown DOPA province code: ${code || '(empty)'}`);
  return resource;
}

function assertAllowedDopaUrl(value, label = 'DOPA URL') {
  const url = new URL(value);
  if (url.protocol !== 'https:' || !ALLOWED_DOPA_HOSTS.has(url.hostname)) {
    throw new Error(`${label} must use an approved DOPA HTTPS host`);
  }
  return url;
}

async function fetchProvinceResource(resource, {
  fetchImpl = globalThis.fetch,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  maxBytes = DEFAULT_MAX_BYTES,
  now = () => new Date()
} = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('fetch implementation is unavailable');
  const requested = assertAllowedDopaUrl(resource.official_download_url, 'resource URL');
  if (requested.hostname !== 'catalog.dopa.go.th') throw new Error('resource URL must start from catalog.dopa.go.th');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new Error('DOPA download timeout')), timeoutMs);
  try {
    const response = await fetchImpl(requested.toString(), {
      method: 'GET',
      redirect: 'follow',
      signal: controller.signal,
      headers: { accept: 'application/json' }
    });
    if (!response || !response.ok) throw new Error(`DOPA download failed: HTTP ${response?.status ?? 'unknown'}`);
    const finalUrl = assertAllowedDopaUrl(response.url || requested.toString(), 'final DOPA URL').toString();
    const contentLength = Number(response.headers?.get?.('content-length') || 0);
    if (Number.isFinite(contentLength) && contentLength > maxBytes) throw new Error('DOPA resource exceeds maximum allowed size');
    const buffer = Buffer.from(await response.arrayBuffer());
    if (!buffer.length) throw new Error('DOPA resource is empty');
    if (buffer.length > maxBytes) throw new Error('DOPA resource exceeds maximum allowed size');
    const checksum = sha256(buffer);
    const parsed = parseDopaVillageDataset(buffer.toString('utf8'), {
      expectedProvinceCode: resource.province_code,
      expectedProvinceName: resource.province_name_th,
      rawBuffer: buffer,
      expectedSha256: checksum
    });
    return {
      buffer,
      checksum,
      rowCount: parsed.rows.length,
      requestedUrl: requested.toString(),
      finalUrl,
      retrievedAt: now().toISOString()
    };
  } finally {
    clearTimeout(timer);
  }
}

function writeCandidate(outputDir, resource, result) {
  fs.mkdirSync(outputDir, { recursive: true });
  const stem = `${resource.province_code}-${resource.resource_id}`;
  const jsonPath = path.join(outputDir, stem + '.json');
  const metadataPath = path.join(outputDir, stem + '.metadata.json');
  fs.writeFileSync(jsonPath, result.buffer);
  fs.writeFileSync(metadataPath, JSON.stringify({
    province_code: resource.province_code,
    province_name_th: resource.province_name_th,
    resource_id: resource.resource_id,
    requested_url: result.requestedUrl,
    final_url: result.finalUrl,
    retrieved_at: result.retrievedAt,
    sha256: result.checksum,
    validated_row_count: result.rowCount,
    manifest_update_required: true,
    import_eligible: false
  }, null, 2) + '\n', 'utf8');
  return { jsonPath, metadataPath };
}

if (require.main === module) {
  const [provinceCode, outputDir, manifestFile] = process.argv.slice(2);
  if (!provinceCode || !outputDir) {
    console.error('Usage: node scripts/fetch-dopa-village-resource.cjs <province-code> <output-dir> [manifest.json]');
    process.exit(2);
  }
  (async () => {
    const manifest = readManifest(manifestFile ? path.resolve(manifestFile) : DEFAULT_MANIFEST);
    const resource = getProvinceResource(manifest, provinceCode);
    const result = await fetchProvinceResource(resource);
    const files = writeCandidate(path.resolve(outputDir), resource, result);
    console.error(`validated ${result.rowCount} villages for ${resource.province_name_th}; SHA-256 ${result.checksum}`);
    console.error(`candidate files: ${files.jsonPath} · ${files.metadataPath}`);
  })().catch(error => {
    console.error(error?.stack || error);
    process.exit(1);
  });
}

module.exports = {
  ALLOWED_DOPA_HOSTS,
  DEFAULT_MANIFEST,
  DEFAULT_MAX_BYTES,
  DEFAULT_TIMEOUT_MS,
  readManifest,
  getProvinceResource,
  assertAllowedDopaUrl,
  fetchProvinceResource,
  writeCandidate
};
