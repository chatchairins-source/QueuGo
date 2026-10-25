#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const SOURCE = 'openstreetmap';
const LICENSE = 'ODbL-1.0';
const ATTRIBUTION = '© OpenStreetMap contributors';
const ADMIN_LEVELS = Object.freeze({ '4': 'province', '6': 'district', '8': 'subdistrict' });
const THAILAND_BOUNDS = Object.freeze({ minLat: 5, maxLat: 21, minLon: 97, maxLon: 106 });

const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const clean = (value, max = 240) => String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, max);

function geometryPoints(type, coordinates) {
  if (type === 'Polygon') return Array.isArray(coordinates) ? coordinates.flat(1) : [];
  if (type === 'MultiPolygon') return Array.isArray(coordinates) ? coordinates.flat(2) : [];
  return [];
}

function normalizeAdminBoundary(feature, record = 1) {
  if (!feature || feature.type !== 'Feature' || !feature.properties || typeof feature.properties !== 'object') return null;
  const p = feature.properties;
  const type = clean(p['@type'] || p.type, 16).toLowerCase();
  const id = clean(String(p['@id'] ?? p.id ?? ''), 24);
  if (!['way', 'relation'].includes(type) || !/^\d{1,20}$/.test(id)) throw new Error('missing stable OSM area identity at record ' + record);
  if (clean(p.boundary, 32) !== 'administrative') return { skipped: 'not_target_admin_boundary' };
  const level = clean(p.admin_level, 4);
  const adminType = ADMIN_LEVELS[level];
  if (!adminType) return { skipped: 'not_target_admin_level' };
  const geometry = feature.geometry;
  if (!geometry || !['Polygon', 'MultiPolygon'].includes(geometry.type) || !Array.isArray(geometry.coordinates)) return { skipped: 'unsupported_geometry' };
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
  if (!polygons.length || polygons.some(poly => !Array.isArray(poly) || !poly.length || poly.some(ring => !Array.isArray(ring) || ring.length < 4 || JSON.stringify(ring[0]) !== JSON.stringify(ring[ring.length - 1])))) {
    throw new Error('invalid administrative polygon ring at record ' + record);
  }
  const points = geometryPoints(geometry.type, geometry.coordinates);
  if (!points.length || points.some(point => !Array.isArray(point) || point.length < 2 || !Number.isFinite(Number(point[0])) || !Number.isFinite(Number(point[1])))) {
    throw new Error('invalid administrative coordinates at record ' + record);
  }
  if (points.some(point => Number(point[1]) < THAILAND_BOUNDS.minLat || Number(point[1]) > THAILAND_BOUNDS.maxLat || Number(point[0]) < THAILAND_BOUNDS.minLon || Number(point[0]) > THAILAND_BOUNDS.maxLon)) {
    return { skipped: 'outside_thailand_bounds' };
  }
  const nameTh = clean(p['name:th']);
  const name = clean(p.name);
  const nameEn = clean(p['name:en']);
  const canonical = nameTh || name || nameEn;
  if (!canonical) return { skipped: 'missing_name' };
  const tags = {
    boundary: 'administrative',
    admin_level: level,
    ...(name ? { name } : {}),
    ...(nameTh ? { 'name:th': nameTh } : {}),
    ...(nameEn ? { 'name:en': nameEn } : {}),
    ...(clean(p.official_name) ? { official_name: clean(p.official_name) } : {}),
    ...(clean(p.ref) ? { ref: clean(p.ref, 80) } : {}),
    ...(clean(p['ISO3166-2']) ? { 'ISO3166-2': clean(p['ISO3166-2'], 80) } : {})
  };
  const sourceId = type + '/' + id;
  const payload = { tags, geometry };
  return {
    source_record_id: sourceId,
    admin_level: Number(level),
    admin_type: adminType,
    canonical_name: canonical,
    name_th: nameTh || null,
    name_en: nameEn || null,
    osm_name: name || null,
    ref: tags.ref || null,
    iso3166_2: tags['ISO3166-2'] || null,
    geometry,
    source_updated_at: new Date(Number(p['@timestamp']) * 1000).toISOString(),
    source_payload_hash: sha256(Buffer.from(JSON.stringify(payload)))
  };
}

function parseGeoJsonSeq(text) {
  const records = String(text).split('\x1e').map(v => v.trim()).filter(Boolean);
  const rows = [];
  const seen = new Set();
  const summary = {
    records: 0,
    staged: 0,
    skipped_not_target_admin_boundary: 0,
    skipped_not_target_admin_level: 0,
    skipped_unsupported_geometry: 0,
    skipped_outside_thailand_bounds: 0,
    skipped_missing_name: 0,
    staged_by_admin_type: { province: 0, district: 0, subdistrict: 0 }
  };
  records.forEach((raw, index) => {
    let feature;
    try { feature = JSON.parse(raw); } catch { throw new Error('invalid admin GeoJSONSeq JSON at record ' + (index + 1)); }
    summary.records++;
    const row = normalizeAdminBoundary(feature, index + 1);
    if (!row) return;
    if (row.skipped) {
      summary['skipped_' + row.skipped]++;
      return;
    }
    if (seen.has(row.source_record_id)) throw new Error('duplicate OSM admin object ' + row.source_record_id);
    seen.add(row.source_record_id);
    rows.push(row);
    summary.staged++;
    summary.staged_by_admin_type[row.admin_type]++;
  });
  rows.sort((a,b) => a.admin_level - b.admin_level || a.source_record_id.localeCompare(b.source_record_id));
  return { rows, summary };
}

async function stageAdminBoundaries({ inputFile, outputDir, pbfUrl, pbfSha256, snapshotDate, retrievedAt }) {
  if (!inputFile || !outputDir) throw new Error('inputFile and outputDir are required');
  if (!/^https:\/\/download\.geofabrik\.de\/asia\/thailand-\d{6}\.osm\.pbf$/.test(pbfUrl || '')) throw new Error('exact dated Geofabrik Thailand PBF URL required');
  if (!/^[a-f0-9]{64}$/i.test(pbfSha256 || '')) throw new Error('PBF SHA-256 required');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(snapshotDate || '')) throw new Error('snapshot date required');
  const retrieved = new Date(retrievedAt);
  if (!Number.isFinite(retrieved.getTime())) throw new Error('retrieved_at required');
  await fsp.mkdir(outputDir, { recursive: true });
  if ((await fsp.readdir(outputDir)).length) throw new Error('output directory must be empty');
  const text = await fsp.readFile(inputFile, 'utf8');
  const parsed = parseGeoJsonSeq(text);
  const exportSha256 = sha256(Buffer.from(text));
  const normalizedFile = path.join(outputDir, 'osm-th-admin-boundaries.normalized.geojsonseq');
  const normalized = parsed.rows.map(row => '\x1e' + JSON.stringify({
    type:'Feature',
    properties:{
      source_record_id:row.source_record_id,
      admin_level:row.admin_level,
      admin_type:row.admin_type,
      canonical_name:row.canonical_name,
      name_th:row.name_th,
      name_en:row.name_en,
      osm_name:row.osm_name,
      ref:row.ref,
      iso3166_2:row.iso3166_2,
      source_updated_at:row.source_updated_at,
      source_payload_hash:row.source_payload_hash
    },
    geometry:row.geometry
  }) + '\n').join('');
  await fsp.writeFile(normalizedFile, normalized, 'utf8');
  const report = {
    source: SOURCE,
    license: LICENSE,
    attribution: ATTRIBUTION,
    execution: 'review_only_no_database_write',
    pbf_url: pbfUrl,
    pbf_sha256: pbfSha256.toLowerCase(),
    snapshot_date: snapshotDate,
    retrieved_at: retrieved.toISOString(),
    export_sha256: exportSha256,
    normalized_sha256: sha256(Buffer.from(normalized)),
    summary: parsed.summary
  };
  await fsp.writeFile(path.join(outputDir, 'osm-th-admin-boundaries-stage-report.json'), JSON.stringify(report, null, 2) + '\n');
  return report;
}

if (require.main === module) {
  const [inputFile, outputDir, pbfUrl, pbfSha256, snapshotDate, retrievedAt] = process.argv.slice(2);
  stageAdminBoundaries({ inputFile:path.resolve(inputFile), outputDir:path.resolve(outputDir), pbfUrl, pbfSha256, snapshotDate, retrievedAt })
    .then(report => console.error(JSON.stringify(report.summary)))
    .catch(error => { console.error(error.message); process.exitCode = 1; });
}

module.exports = { SOURCE, LICENSE, ATTRIBUTION, ADMIN_LEVELS, THAILAND_BOUNDS, normalizeAdminBoundary, parseGeoJsonSeq, stageAdminBoundaries };
