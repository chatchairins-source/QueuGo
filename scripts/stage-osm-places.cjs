#!/usr/bin/env node
'use strict';

// Review-only staging for a locally exported OpenStreetMap Thailand PBF.
// Export with: osmium export -f geojsonseq --attributes type,id,timestamp
// Do not include uid, user, or changeset attributes. This tool never imports data.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const SOURCE_KEY = 'openstreetmap';
const SOURCE_LICENSE = 'ODbL-1.0';
const LICENSE_URL = 'https://www.openstreetmap.org/copyright';
const ATTRIBUTION = '© OpenStreetMap contributors';
const OFFICIAL_PBF_URL = 'https://download.geofabrik.de/asia/thailand-latest.osm.pbf';
const THAILAND_BOUNDS = { minLat: 5, maxLat: 21, minLon: 97, maxLon: 106 };
const ROAD_HIGHWAYS = new Set(['motorway','trunk','primary','secondary','tertiary','residential','unclassified','service','living_street','pedestrian','road']);
const CHUNK_ROWS = 250;
const sha256 = data => crypto.createHash('sha256').update(data).digest('hex');
const clean = (value, max = 240) => typeof value === 'string' ? value.trim().replace(/\s+/g, ' ').slice(0, max) : '';
const sqlLiteral = value => "'" + String(value).replace(/'/g, "''") + "'";

function timestamp(value, label) {
  const numeric = typeof value === 'number' || (typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value));
  const date = new Date(numeric ? Number(value) * 1000 : value);
  if (!value || !Number.isFinite(date.getTime())) throw new Error('valid ' + label + ' timestamp required');
  return date.toISOString();
}

function classify(tags) {
  if (tags.amenity === 'cafe') return { category: 'food', subcategory: 'cafe' };
  if (['restaurant', 'fast_food', 'food_court'].includes(tags.amenity)) return { category: 'food', subcategory: 'restaurant' };
  if (tags.amenity === 'marketplace' || tags.shop === 'farm') return { category: 'market', subcategory: 'fresh_market' };
  if (['laundry', 'dry_cleaning'].includes(tags.shop)) return { category: 'laundry', subcategory: 'laundry' };
  if (['supermarket', 'convenience', 'greengrocer', 'wholesale'].includes(tags.shop)) return { category: 'grocery', subcategory: tags.shop };
  if (tags.shop) return { category: 'shopping', subcategory: clean(tags.shop, 80) };
  if (tags.craft) return { category: 'shopping', subcategory: 'craft' };
  if (tags.leisure) return { category: 'place', subcategory: clean(tags.leisure, 80) };
  if (tags.historic) return { category: 'place', subcategory: clean(tags.historic, 80) };
  if (tags.office) return { category: 'place', subcategory: 'office' };
  if (tags.public_transport) return { category: 'place', subcategory: 'public_transport' };
  if (['station', 'halt', 'tram_stop', 'subway_entrance'].includes(tags.railway)) return { category: 'place', subcategory: 'railway' };
  if (tags.highway === 'bus_stop') return { category: 'place', subcategory: 'bus_stop' };
  if (ROAD_HIGHWAYS.has(tags.highway)) return { category: 'road', subcategory: tags.highway };
  if (tags.aeroway === 'terminal') return { category: 'place', subcategory: 'terminal' };
  if (tags.tourism) return { category: 'place', subcategory: clean(tags.tourism, 80) };
  if (tags.amenity) return { category: 'place', subcategory: clean(tags.amenity, 80) };
  return null;
}

function geometryCoordinates(type, coordinates, line) {
  if (type === 'Point') return Array.isArray(coordinates) ? [coordinates] : [];
  if (type === 'LineString') return Array.isArray(coordinates) ? coordinates : [];
  if (type === 'Polygon') return Array.isArray(coordinates) ? coordinates.flat(1) : [];
  if (type === 'MultiPolygon') return Array.isArray(coordinates) ? coordinates.flat(2) : [];
  return [];
}

function lineMidpoint(coordinates) {
  if (!Array.isArray(coordinates) || coordinates.length < 2) return null;
  const segments = [];
  let total = 0;
  for (let index = 1; index < coordinates.length; index++) {
    const a = coordinates[index - 1], b = coordinates[index];
    const midLat = (Number(a[1]) + Number(b[1])) * Math.PI / 360;
    const dx = (Number(b[0]) - Number(a[0])) * Math.cos(midLat);
    const dy = Number(b[1]) - Number(a[1]);
    const length = Math.hypot(dx, dy);
    segments.push({ a, b, length });
    total += length;
  }
  if (!(total > 0)) return null;
  let remaining = total / 2;
  for (const segment of segments) {
    if (remaining > segment.length) {
      remaining -= segment.length;
      continue;
    }
    const ratio = segment.length === 0 ? 0 : remaining / segment.length;
    return [
      Number(segment.a[0]) + (Number(segment.b[0]) - Number(segment.a[0])) * ratio,
      Number(segment.a[1]) + (Number(segment.b[1]) - Number(segment.a[1])) * ratio
    ];
  }
  const last = coordinates[coordinates.length - 1];
  return [Number(last[0]), Number(last[1])];
}

function normalizeFeature(feature, line = 1) {
  if (!feature || feature.type !== 'Feature' || !feature.properties || typeof feature.properties !== 'object') return null;
  const p = feature.properties;
  const type = clean(p.type || p['@type'], 16).toLowerCase();
  const id = clean(String(p.id ?? p['@id'] ?? ''), 24);
  if (!['node', 'way', 'relation'].includes(type) || !/^\d{1,20}$/.test(id)) throw new Error('missing stable OSM type/id at GeoJSONSeq record ' + line);
  const geometry = feature.geometry;
  if (!geometry || !['Point', 'LineString', 'Polygon', 'MultiPolygon'].includes(geometry.type) || !Array.isArray(geometry.coordinates)) return { skipped: 'unsupported_geometry' };
  if (geometry.type === 'LineString' && geometry.coordinates.length < 2) throw new Error('invalid linestring at GeoJSONSeq record ' + line);
  if (geometry.type === 'Polygon') {
    if (!geometry.coordinates.length || geometry.coordinates.some(ring => !Array.isArray(ring) || ring.length < 4 || JSON.stringify(ring[0]) !== JSON.stringify(ring[ring.length - 1]))) throw new Error('invalid polygon ring at GeoJSONSeq record ' + line);
  }
  if (geometry.type === 'MultiPolygon' && (!geometry.coordinates.length || geometry.coordinates.some(polygon => !Array.isArray(polygon) || !polygon.length || polygon.some(ring => !Array.isArray(ring) || ring.length < 4 || JSON.stringify(ring[0]) !== JSON.stringify(ring[ring.length - 1]))))) throw new Error('invalid multipolygon ring at GeoJSONSeq record ' + line);
  const coordinates = geometryCoordinates(geometry.type, geometry.coordinates, line);
  if (!coordinates.length || coordinates.some(point => !Array.isArray(point) || point.length < 2 || !Number.isFinite(Number(point[0])) || !Number.isFinite(Number(point[1])))) throw new Error('invalid OSM geometry coordinates at GeoJSONSeq record ' + line);
  if (coordinates.some(point => Number(point[1]) < 5 || Number(point[1]) > 21 || Number(point[0]) < 97 || Number(point[0]) > 106)) return { skipped: 'outside_thailand' };
  const linePoint = geometry.type === 'LineString' ? lineMidpoint(geometry.coordinates) : null;
  if (geometry.type === 'LineString' && !linePoint) throw new Error('invalid road linestring at GeoJSONSeq record ' + line);
  const longitude = geometry.type === 'Point' ? Number(geometry.coordinates[0]) : linePoint?.[0] ?? null;
  const latitude = geometry.type === 'Point' ? Number(geometry.coordinates[1]) : linePoint?.[1] ?? null;
  const sourceTags = p.tags && typeof p.tags === 'object' ? p.tags : p;
  const allowed = ['shop','amenity','tourism','leisure','historic','craft','office','public_transport','railway','highway','aeroway','name','name:th','name:en','brand','addr:housenumber','addr:street','addr:suburb','addr:city_district','addr:subdistrict','addr:subdistrict:th','addr:district','addr:district:th','addr:county','addr:state','addr:province','addr:province:th','addr:city','addr:village','addr:hamlet','addr:place','addr:moo','addr:postcode'];
  const tags = {};
  for (const key of allowed) {
    const value = clean(sourceTags[key], key.startsWith('name') ? 240 : 120);
    if (value) tags[key] = value;
  }
  const mapping = classify(tags);
  const name = tags['name:th'] || tags.name || tags['name:en'] || tags.brand;
  if (!mapping || !name) return { skipped: 'not_named_searchable_place' };
  if (geometry.type === 'LineString' && mapping.category !== 'road') return { skipped: 'unsupported_geometry' };
  if (mapping.category === 'road' && geometry.type !== 'LineString') return { skipped: 'unsupported_geometry' };
  const sourceId = type + '/' + id;
  const address = [tags['addr:housenumber'], tags['addr:street']].filter(Boolean).join(' ') || null;
  const payload = { tags, geometry };
  return {
    source_record_id: sourceId, source_id: sourceId, dedupe_key: 'osm:' + sourceId,
    canonical_name: name, name_th: tags['name:th'] || null, name_en: tags['name:en'] || null,
    aliases: [...new Set([tags.name, tags.brand].filter(v => v && v !== name))].slice(0, 8),
    category: mapping.category, subcategory: mapping.subcategory, latitude, longitude,
    boundary_geojson: ['Polygon','MultiPolygon'].includes(geometry.type) ? JSON.stringify(geometry) : null, address,
    village: tags['addr:village'] || tags['addr:hamlet'] || tags['addr:place'] || null,
    moo: tags['addr:moo'] || null,
    subdistrict: tags['addr:subdistrict:th'] || tags['addr:subdistrict'] || tags['addr:suburb'] || tags['addr:city_district'] || null,
    district: tags['addr:district:th'] || tags['addr:district'] || tags['addr:county'] || null,
    province: tags['addr:province:th'] || tags['addr:province'] || tags['addr:state'] || null,
    postcode: tags['addr:postcode'] || null,
    source_updated_at: timestamp(p.timestamp || p['@timestamp'], 'OSM object update'),
    source_payload_hash: sha256(Buffer.from(JSON.stringify(payload)))
  };
}

function parseGeoJsonSeq(text) {
  const records = String(text).split('\x1e').map(s => s.trim()).filter(Boolean);
  const rows = [], seen = new Set();
  const summary = { records: 0, staged: 0, skipped_unsupported_geometry: 0, skipped_outside_thailand: 0, skipped_not_named_searchable_place: 0 };
  records.forEach((record, index) => {
    let feature;
    try { feature = JSON.parse(record); } catch { throw new Error('invalid GeoJSONSeq JSON at record ' + (index + 1)); }
    summary.records++;
    const row = normalizeFeature(feature, index + 1);
    if (!row) { summary.skipped_not_named_searchable_place++; return; }
    if (row.skipped) { summary['skipped_' + row.skipped]++; return; }
    if (seen.has(row.source_record_id)) throw new Error('duplicate OSM object ' + row.source_record_id);
    seen.add(row.source_record_id); rows.push(row); summary.staged++;
  });
  rows.sort((a, b) => a.source_record_id.localeCompare(b.source_record_id));
  return { rows, summary };
}

function renderOsmChunkSql(rows, meta) {
  if (!Array.isArray(rows) || !rows.length) throw new Error('non-empty OSM rows required');
  const json = sqlLiteral(JSON.stringify(rows));
  const at = sqlLiteral(meta.retrievedAt), date = sqlLiteral(meta.snapshotDate);
  const pbfSha = sqlLiteral(meta.pbfSha256), exportSha = sqlLiteral(meta.exportSha256), url = sqlLiteral(meta.pbfUrl);
  return '-- REVIEW ONLY: OpenStreetMap import candidate; staging does not write to a database.\n' +
    '-- ' + ATTRIBUTION + ' · ' + LICENSE_URL + ' · ODbL-1.0; preserve attribution and share-alike terms for database distributions.\n' +
    '-- PBF ' + meta.pbfUrl + '; snapshot ' + meta.snapshotDate + '; retrieved ' + meta.retrievedAt + '; PBF SHA-256 ' + meta.pbfSha256 + '\n' +
    '-- GeoJSONSeq SHA-256 ' + meta.exportSha256 + '; records in chunk ' + rows.length + '\nBEGIN;\n' +
    'WITH i AS (SELECT * FROM jsonb_to_recordset(' + json + '::jsonb) AS r(source_record_id text,source_id text,dedupe_key text,canonical_name text,name_th text,name_en text,aliases text[],category text,subcategory text,latitude double precision,longitude double precision,boundary_geojson text,address text,village text,moo text,subdistrict text,district text,province text,postcode text,source_updated_at timestamptz,source_payload_hash text)),\n' +
    "g AS (SELECT i.*,CASE WHEN boundary_geojson IS NULL THEN NULL ELSE extensions.st_setsrid(extensions.st_geomfromgeojson(boundary_geojson),4326) END geom FROM i),\n" +
    'u AS (INSERT INTO public.queuego_places(canonical_name,name_th,name_en,aliases,category,subcategory,latitude,longitude,boundary,address,village,moo,subdistrict,district,province,postcode,country,country_code,source,source_id,source_license,source_updated_at,verification_status,verification_method,confidence,dedupe_key) ' +
    "SELECT canonical_name,name_th,name_en,aliases,category,subcategory,CASE WHEN geom IS NULL THEN latitude ELSE extensions.st_y(extensions.st_pointonsurface(geom)) END,CASE WHEN geom IS NULL THEN longitude ELSE extensions.st_x(extensions.st_pointonsurface(geom)) END,CASE WHEN geom IS NULL THEN NULL ELSE extensions.st_multi(geom)::extensions.geography END,address,village,moo,subdistrict,district,province,postcode,'Thailand','TH','openstreetmap',source_id,'ODbL-1.0',source_updated_at,'unverified','osm_import_review',0.700,dedupe_key FROM g " +
    'ON CONFLICT(dedupe_key) WHERE dedupe_key IS NOT NULL DO UPDATE SET canonical_name=excluded.canonical_name,name_th=excluded.name_th,name_en=excluded.name_en,aliases=excluded.aliases,category=excluded.category,subcategory=excluded.subcategory,latitude=excluded.latitude,longitude=excluded.longitude,boundary=excluded.boundary,address=excluded.address,village=excluded.village,moo=excluded.moo,subdistrict=excluded.subdistrict,district=excluded.district,province=excluded.province,postcode=excluded.postcode,source_id=excluded.source_id,source_license=excluded.source_license,source_updated_at=excluded.source_updated_at,verification_status=excluded.verification_status,verification_method=excluded.verification_method,confidence=excluded.confidence,updated_at=now() RETURNING place_id,dedupe_key) ' +
    "INSERT INTO public.queuego_place_source_records(source,source_record_id,place_id,source_license,source_updated_at,source_payload_hash) SELECT 'openstreetmap',i.source_record_id,u.place_id,'ODbL-1.0',i.source_updated_at,i.source_payload_hash FROM i JOIN u USING(dedupe_key) " +
    "ON CONFLICT(source,source_record_id) DO UPDATE SET place_id=excluded.place_id,source_license=excluded.source_license,source_updated_at=excluded.source_updated_at,source_payload_hash=excluded.source_payload_hash,imported_at=now();\n" +
    'WITH i AS (SELECT * FROM jsonb_to_recordset(' + json + '::jsonb) AS r(source_record_id text,dedupe_key text,canonical_name text,name_th text,name_en text,aliases text[],category text,subcategory text,latitude double precision,longitude double precision,boundary_geojson text,address text,village text,moo text,subdistrict text,district text,province text,postcode text,source_updated_at timestamptz,source_payload_hash text)), p AS (SELECT q.place_id,q.boundary,i.* FROM i JOIN public.queuego_places q USING(dedupe_key)) ' +
    "INSERT INTO public.queuego_place_field_provenance(place_id,field_name,source,source_record_id,observed_at,confidence,raw_value) SELECT p.place_id,v.field,'openstreetmap',p.source_record_id," + at + "::timestamptz,0.700,v.value FROM p CROSS JOIN LATERAL (VALUES ('canonical_name',to_jsonb(p.canonical_name)),('name_th',to_jsonb(p.name_th)),('name_en',to_jsonb(p.name_en)),('aliases',to_jsonb(p.aliases)),('category',to_jsonb(p.category)),('subcategory',to_jsonb(p.subcategory)),('latitude',to_jsonb(p.latitude)),('longitude',to_jsonb(p.longitude)),('boundary',to_jsonb(p.boundary)),('address',to_jsonb(p.address)),('village',to_jsonb(p.village)),('moo',to_jsonb(p.moo)),('subdistrict',to_jsonb(p.subdistrict)),('district',to_jsonb(p.district)),('province',to_jsonb(p.province)),('postcode',to_jsonb(p.postcode)),('source_license',to_jsonb('ODbL-1.0'::text)),('source_updated_at',to_jsonb(p.source_updated_at)),('verification_status',to_jsonb('unverified'::text))) v(field,value) ON CONFLICT(place_id,field_name,source,observed_at) DO UPDATE SET confidence=excluded.confidence,raw_value=excluded.raw_value;\n" +
    'WITH i AS (SELECT * FROM jsonb_to_recordset(' + json + '::jsonb) AS r(dedupe_key text,name_th text,name_en text)) INSERT INTO public.queuego_place_aliases(place_id,alias,language_code,source) SELECT p.place_id,a.alias,a.lang,$$openstreetmap$$ FROM i JOIN public.queuego_places p USING(dedupe_key) CROSS JOIN LATERAL (VALUES (i.name_th,$$th$$),(i.name_en,$$en$$)) a(alias,lang) WHERE a.alias IS NOT NULL ON CONFLICT(place_id,normalized_alias) DO UPDATE SET language_code=excluded.language_code,source=excluded.source;\n' +
    "UPDATE public.queuego_place_sources SET metadata=jsonb_set(coalesce(metadata,'{}'::jsonb),'{last_review_import}',jsonb_build_object('source_url'," + url + ",'snapshot_date'," + date + ",'retrieved_at'," + at + ",'pbf_sha256'," + pbfSha + ",'geojsonseq_sha256'," + exportSha + ",'license','ODbL-1.0','attribution','© OpenStreetMap contributors'),true),updated_at=now() WHERE source_key='openstreetmap';\nCOMMIT;\n";
}

async function hashFile(file) {
  const hash = crypto.createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

async function* geoJsonSeqRecords(file) {
  let buffer = '', started = false;
  for await (const chunk of fs.createReadStream(file, { encoding: 'utf8' })) {
    buffer += chunk;
    if (!started) {
      const first = buffer.indexOf('\x1e');
      if (first < 0) {
        if (buffer.trim()) throw new Error('GeoJSONSeq must use RFC 8142 record separators');
        continue;
      }
      if (buffer.slice(0, first).trim()) throw new Error('GeoJSONSeq must use RFC 8142 record separators');
      buffer = buffer.slice(first);
      started = true;
    }
    let next;
    while ((next = buffer.indexOf('\x1e', 1)) >= 0) {
      const record = buffer.slice(1, next).trim();
      if (record) yield record;
      buffer = buffer.slice(next);
    }
  }
  if (!started) {
    if (buffer.trim()) throw new Error('GeoJSONSeq must use RFC 8142 record separators');
    return;
  }
  const last = buffer.slice(1).trim();
  if (last) yield last;
}

async function stageOsmGeoJsonSeq({ inputFile, pbfFile, outputDir, retrievedAt, snapshotDate, pbfSha256, pbfUrl = OFFICIAL_PBF_URL, chunkRows = CHUNK_ROWS }) {
  if (!inputFile || !pbfFile || !outputDir) throw new Error('inputFile, pbfFile and outputDir are required');
  let parsedPbfUrl;
  try { parsedPbfUrl = new URL(pbfUrl); } catch { throw new Error('PBF URL must be a registered Geofabrik Thailand extract'); }
  if (parsedPbfUrl.protocol !== 'https:' || parsedPbfUrl.hostname !== 'download.geofabrik.de'
    || parsedPbfUrl.search || parsedPbfUrl.hash
    || !/^\/asia\/thailand-(?:latest|\d{6})\.osm\.pbf$/.test(parsedPbfUrl.pathname)) {
    throw new Error('PBF URL must be a registered Geofabrik Thailand extract');
  }
  const retrieved = timestamp(retrievedAt, 'retrieval');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(snapshotDate || '') || new Date(snapshotDate + 'T00:00:00Z').toISOString().slice(0, 10) !== snapshotDate) throw new Error('valid PBF snapshot date required');
  if (!/^[a-f0-9]{64}$/i.test(pbfSha256 || '')) throw new Error('verified PBF SHA-256 required');
  if (!Number.isInteger(chunkRows) || chunkRows < 1 || chunkRows > 1000) throw new Error('chunkRows must be between 1 and 1000');
  const actualPbfSha = await hashFile(pbfFile);
  if (actualPbfSha !== pbfSha256.toLowerCase()) throw new Error('PBF SHA-256 mismatch: got ' + actualPbfSha);
  const exportSha = await hashFile(inputFile);
  await fsp.mkdir(outputDir, { recursive: true });
  if ((await fsp.readdir(outputDir)).length) throw new Error('review output directory must be empty');
  const report = { source: SOURCE_KEY, license: SOURCE_LICENSE, attribution: ATTRIBUTION, license_url: LICENSE_URL, pbf_url: pbfUrl, snapshot_date: snapshotDate, retrieved_at: retrieved, pbf_sha256: actualPbfSha, geojsonseq_sha256: exportSha, execution: 'review_only_no_database_write', chunk_rows: chunkRows,
    summary: { records: 0, staged: 0, skipped_unsupported_geometry: 0, skipped_outside_thailand: 0, skipped_not_named_searchable_place: 0 },
    coverage: { staged_by_category: {}, staged_by_geometry: { point: 0, road: 0, boundary: 0 }, hierarchy_fields_present: { province: 0, district: 0, subdistrict: 0, village: 0, moo: 0 } }, chunks: [] };
  const meta = { retrievedAt: retrieved, snapshotDate, pbfSha256: actualPbfSha, exportSha256: exportSha, pbfUrl };
  const rows = [], seen = new Set();
  let sequence = 0;
  const flush = async () => {
    if (!rows.length) return;
    sequence++;
    const file = 'osm-th-' + snapshotDate + '-' + String(sequence).padStart(4, '0') + '.review.sql';
    await fsp.writeFile(path.join(outputDir, file), renderOsmChunkSql(rows, meta), 'utf8');
    report.chunks.push({ file, rows: rows.length }); rows.length = 0;
  };
  try {
    for await (const line of geoJsonSeqRecords(inputFile)) {
      let feature;
      try { feature = JSON.parse(line); } catch { throw new Error('invalid GeoJSONSeq JSON at record ' + (report.summary.records + 1)); }
      report.summary.records++;
      const row = normalizeFeature(feature, report.summary.records);
      if (!row) { report.summary.skipped_not_named_searchable_place++; continue; }
      if (row.skipped) { report.summary['skipped_' + row.skipped]++; continue; }
      if (seen.has(row.source_record_id)) throw new Error('duplicate OSM object ' + row.source_record_id);
      seen.add(row.source_record_id); rows.push(row); report.summary.staged++;
      report.coverage.staged_by_category[row.category] = (report.coverage.staged_by_category[row.category] || 0) + 1;
      report.coverage.staged_by_geometry[row.category === 'road' ? 'road' : (row.boundary_geojson ? 'boundary' : 'point')]++;
      for (const field of Object.keys(report.coverage.hierarchy_fields_present)) {
        if (row[field] !== null && row[field] !== undefined && row[field] !== '') report.coverage.hierarchy_fields_present[field]++;
      }
      if (rows.length >= chunkRows) await flush();
    }
    await flush();
  } catch (error) {
    for (const chunk of report.chunks) await fsp.rm(path.join(outputDir, chunk.file), { force: true });
    throw error;
  }
  await fsp.writeFile(path.join(outputDir, 'osm-th-stage-report.json'), JSON.stringify(report, null, 2) + '\n', 'utf8');
  return report;
}

if (require.main === module) {
  const [inputFile, pbfFile, outputDir, retrievedAt, snapshotDate, pbfSha256, pbfUrl] = process.argv.slice(2);
  if (!inputFile || !pbfFile || !outputDir || !retrievedAt || !snapshotDate || !pbfSha256) {
    console.error('Usage: node scripts/stage-osm-places.cjs <export.geojsonseq> <thailand.osm.pbf> <review-output-dir> <retrieved-at-ISO> <snapshot-date-YYYY-MM-DD> <pbf-sha256> [official-pbf-url]');
    process.exit(2);
  }
  stageOsmGeoJsonSeq({ inputFile: path.resolve(inputFile), pbfFile: path.resolve(pbfFile), outputDir: path.resolve(outputDir), retrievedAt, snapshotDate, pbfSha256, pbfUrl })
    .then(report => console.error('staged ' + report.summary.staged + ' OSM searchable places in ' + report.chunks.length + ' review SQL chunk(s); export SHA-256 ' + report.geojsonseq_sha256))
    .catch(error => { console.error(error.message); process.exitCode = 1; });
}

module.exports = { SOURCE_KEY, SOURCE_LICENSE, LICENSE_URL, ATTRIBUTION, OFFICIAL_PBF_URL, THAILAND_BOUNDS, ROAD_HIGHWAYS, CHUNK_ROWS, classify, lineMidpoint, normalizeFeature, parseGeoJsonSeq, renderOsmChunkSql, stageOsmGeoJsonSeq };
