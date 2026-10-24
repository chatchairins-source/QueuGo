#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const {
  parseDopaVillageDataset,
  renderDopaVillageImport,
  sha256
} = require('./import-dopa-villages.cjs');

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function candidatePaths(inputDir, resourceId) {
  return {
    payload: path.join(inputDir, resourceId + '.json'),
    metadata: path.join(inputDir, resourceId + '.meta.json')
  };
}

function validateManifest(manifest) {
  if (!manifest || !Array.isArray(manifest.resources) || !manifest.resources.length) {
    throw new Error('DOPA resource manifest must contain resources');
  }
  const resourceIds = new Set();
  const provinceCodes = new Set();
  for (const resource of manifest.resources) {
    if (!/^\d{2}$/.test(String(resource.province_code || ''))) throw new Error('invalid DOPA province code in manifest');
    if (!resource.province_name_th) throw new Error('missing DOPA province name in manifest');
    if (!/^[0-9a-f-]{36}$/i.test(String(resource.resource_id || ''))) throw new Error('invalid DOPA resource UUID in manifest');
    if (resourceIds.has(resource.resource_id)) throw new Error('duplicate DOPA resource UUID in manifest');
    if (provinceCodes.has(resource.province_code)) throw new Error('duplicate DOPA province code in manifest');
    resourceIds.add(resource.resource_id);
    provinceCodes.add(resource.province_code);
    const url = new URL(resource.official_download_url);
    if (url.protocol !== 'https:' || url.hostname !== 'catalog.dopa.go.th'
      || !url.pathname.includes('/resource/' + resource.resource_id + '/download/')) {
      throw new Error('invalid official DOPA download URL in manifest');
    }
  }
  return manifest;
}

function stageDopaVillageBatch({ manifest, inputDir, outputDir }) {
  validateManifest(manifest);
  if (!inputDir || !outputDir) throw new Error('inputDir and outputDir are required');
  fs.mkdirSync(outputDir, { recursive: true });

  const report = {
    dataset_id: manifest.dataset_id || null,
    generated_at: new Date().toISOString(),
    source: 'catalog.dopa.go.th',
    manifest_mutated: false,
    resources: []
  };

  for (const resource of [...manifest.resources].sort((a, b) => String(a.province_code).localeCompare(String(b.province_code)))) {
    const paths = candidatePaths(inputDir, resource.resource_id);
    const item = {
      province_code: resource.province_code,
      province_name_th: resource.province_name_th,
      resource_id: resource.resource_id,
      status: 'missing',
      sql_file: null,
      sha256: null,
      validated_row_count: null,
      import_candidate: false
    };

    if (!fs.existsSync(paths.payload)) {
      report.resources.push(item);
      continue;
    }
    if (!fs.existsSync(paths.metadata)) {
      item.status = 'blocked_missing_metadata';
      report.resources.push(item);
      continue;
    }

    try {
      const meta = readJson(paths.metadata);
      if (meta.resource_id !== resource.resource_id) throw new Error('resource metadata UUID mismatch');
      if (meta.resource_url !== resource.official_download_url) throw new Error('resource metadata URL mismatch');
      if (!meta.retrieved_at || !Number.isFinite(Date.parse(meta.retrieved_at))) throw new Error('resource metadata retrieval timestamp invalid');

      const raw = fs.readFileSync(paths.payload);
      const checksum = sha256(raw);
      if (resource.sha256 && checksum !== String(resource.sha256).toLowerCase()) {
        throw new Error('manifest SHA-256 mismatch');
      }

      const parsed = parseDopaVillageDataset(raw.toString('utf8'), {
        expectedProvinceCode: String(resource.province_code),
        expectedProvinceName: resource.province_name_th,
        rawBuffer: raw,
        expectedSha256: resource.sha256 || checksum
      });

      if (resource.validated_row_count != null && Number(resource.validated_row_count) !== parsed.rows.length) {
        throw new Error('manifest row-count mismatch');
      }

      const sql = renderDopaVillageImport(parsed, {
        resourceUrl: resource.official_download_url,
        retrievedAt: meta.retrieved_at,
        checksum
      });
      const sqlName = String(resource.province_code) + '-' + resource.resource_id + '.sql';
      fs.writeFileSync(path.join(outputDir, sqlName), sql, 'utf8');

      item.status = 'staged';
      item.sql_file = sqlName;
      item.sha256 = checksum;
      item.validated_row_count = parsed.rows.length;
      item.import_candidate = true;
      item.retrieved_at = new Date(meta.retrieved_at).toISOString();
      item.official_download_url = resource.official_download_url;
    } catch (error) {
      item.status = 'blocked_invalid_payload';
      item.error = error.message;
    }
    report.resources.push(item);
  }

  report.summary = {
    total: report.resources.length,
    staged: report.resources.filter(x => x.status === 'staged').length,
    missing: report.resources.filter(x => x.status === 'missing').length,
    blocked: report.resources.filter(x => x.status.startsWith('blocked_')).length
  };
  fs.writeFileSync(path.join(outputDir, 'dopa-gis01-stage-report.json'), JSON.stringify(report, null, 2) + '\n', 'utf8');
  return report;
}

if (require.main === module) {
  const [inputDir, outputDir, manifestPath = path.join(__dirname, '../data/maps/dopa-gis01-resources.json')] = process.argv.slice(2);
  if (!inputDir || !outputDir) {
    console.error('Usage: node scripts/stage-dopa-village-batch.cjs <payload-dir> <output-dir> [manifest.json]');
    process.exit(2);
  }
  const manifest = readJson(path.resolve(manifestPath));
  const report = stageDopaVillageBatch({
    manifest,
    inputDir: path.resolve(inputDir),
    outputDir: path.resolve(outputDir)
  });
  console.error(`DOPA stage: ${report.summary.staged} staged, ${report.summary.missing} missing, ${report.summary.blocked} blocked`);
  if (report.summary.blocked) process.exitCode = 1;
}

module.exports = { candidatePaths, stageDopaVillageBatch, validateManifest };
