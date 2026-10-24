'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const {
  SOURCE_KEY, SOURCE_LICENSE, ATTRIBUTION, OFFICIAL_PBF_URL,
  normalizeFeature, parseGeoJsonSeq, renderOsmChunkSql, stageOsmGeoJsonSeq
} = require('../scripts/stage-osm-places.cjs');
const checksum = value => crypto.createHash('sha256').update(value).digest('hex');
const feature = (id, tags = {}, coordinates = [103.1, 15.2], geometry = 'Point') => ({
  type: 'Feature', id, properties: { type: 'node', id: String(id), timestamp: '2026-10-20T12:00:00Z', ...tags },
  geometry: geometry === 'Point' ? { type: 'Point', coordinates } : { type: geometry, coordinates: [] }
});
const areaFeature = (id, tags = { shop: 'supermarket', name: 'ตลาดอาคาร' }) => ({
  type: 'Feature', properties: { '@type': 'way', '@id': id, '@timestamp': 1792497600, ...tags },
  geometry: { type: 'Polygon', coordinates: [[[103,15],[103.02,15],[103.02,15.02],[103,15.02],[103,15]]] }
});

const cafe = normalizeFeature(feature(101, { amenity: 'cafe', name: 'ร้านกาแฟ', 'name:th': 'กาแฟชุมชน', 'addr:province': 'ขอนแก่น', 'addr:district': 'เมืองขอนแก่น', brand: 'ชุมชน' }));
assert.equal(cafe.source_record_id, 'node/101');
assert.equal(cafe.category, 'food');
assert.equal(cafe.subcategory, 'cafe');
assert.equal(cafe.canonical_name, 'กาแฟชุมชน');
assert.equal(cafe.province, 'ขอนแก่น');
assert.equal(cafe.boundary_geojson, null);
const osmiumAttributeFeature = { ...feature(109, { amenity: 'cafe', name: 'เวลา' }), properties: { '@type': 'node', '@id': 109, '@timestamp': 1792497600, amenity: 'cafe', name: 'เวลา' } };
assert.equal(normalizeFeature(osmiumAttributeFeature).source_updated_at, '2026-10-20T12:00:00.000Z');
assert.deepEqual(cafe.aliases, ['ร้านกาแฟ', 'ชุมชน']);
assert.equal(cafe.source_payload_hash, checksum(JSON.stringify({
  tags: { amenity: 'cafe', name: 'ร้านกาแฟ', 'name:th': 'กาแฟชุมชน', brand: 'ชุมชน', 'addr:district': 'เมืองขอนแก่น', 'addr:province': 'ขอนแก่น' },
  geometry: { type: 'Point', coordinates: [103.1, 15.2] }
})));
const marketArea = normalizeFeature(areaFeature(110));
assert.equal(marketArea.source_record_id, 'way/110');
assert.equal(marketArea.category, 'grocery');
assert.equal(marketArea.latitude, null);
assert.match(marketArea.boundary_geojson, /Polygon/);
const multiArea = normalizeFeature({ ...areaFeature(111), geometry: { type: 'MultiPolygon', coordinates: [[[[103,15],[103.02,15],[103.02,15.02],[103,15.02],[103,15]]]] } });
assert.match(multiArea.boundary_geojson, /MultiPolygon/);
assert.equal(normalizeFeature({ ...areaFeature(112), geometry: { type: 'Polygon', coordinates: [[[120,15],[120.02,15],[120.02,15.02],[120,15.02],[120,15]]] } }).skipped, 'outside_thailand');
assert.throws(() => normalizeFeature({ ...areaFeature(113), geometry: { type: 'Polygon', coordinates: [[[103,15],[103.02,15],[103.02,15.02],[103,15.02]]] } }), /invalid polygon ring/);
assert.equal(normalizeFeature(feature(102, { shop: 'supermarket', name: 'ร้าน' })).category, 'grocery');
assert.equal(normalizeFeature(feature(103, { shop: 'laundry', name: 'ซักรีด' })).category, 'laundry');
assert.equal(normalizeFeature(feature(104, { shop: 'bookshop', name: 'หนังสือ' })).category, 'shopping');
assert.equal(normalizeFeature(feature(105, { shop: 'cafe', name: 'Cafe' }, [103, 15], 'LineString')).skipped, 'unsupported_geometry');
assert.equal(normalizeFeature(feature(106, { shop: 'cafe', name: 'Cafe' }, [120, 15])).skipped, 'outside_thailand');
assert.equal(normalizeFeature(feature(107, { shop: 'cafe' })).skipped, 'not_named_searchable_place');
const privateTagged = normalizeFeature({ ...feature(108, { shop: 'cafe', name: 'Cafe' }), properties: { ...feature(108, { shop: 'cafe', name: 'Cafe' }).properties, user: 'must not be accepted' } });
assert.equal(privateTagged.source_record_id, 'node/108');

const seq = [feature(201, { shop: 'convenience', name: 'ร้านสะดวกซื้อ' }), feature(202, { tourism: 'attraction', name: 'แหล่งท่องเที่ยว' }, [90, 15]), feature(203, { amenity: 'restaurant', name: 'ร้านอาหาร' }, [103, 15], 'LineString'), areaFeature(204)]
  .map(value => '\x1e' + JSON.stringify(value) + '\n').join('');
const parsed = parseGeoJsonSeq(seq);
assert.equal(parsed.summary.records, 4);
assert.equal(parsed.summary.staged, 2);
assert.equal(parsed.summary.skipped_outside_thailand, 1);
assert.equal(parsed.summary.skipped_unsupported_geometry, 1);
assert.throws(() => parseGeoJsonSeq('\x1e{bad}\n'), /invalid GeoJSONSeq/);
assert.throws(() => parseGeoJsonSeq('\x1e' + JSON.stringify(feature(1, { shop: 'cafe', name: 'A' })) + '\n\x1e' + JSON.stringify(feature(1, { shop: 'cafe', name: 'B' })) + '\n'), /duplicate OSM object/);

const sql = renderOsmChunkSql([cafe], {
  retrievedAt: '2026-10-24T00:00:00.000Z', snapshotDate: '2026-10-20',
  pbfSha256: 'a'.repeat(64), exportSha256: 'b'.repeat(64), pbfUrl: OFFICIAL_PBF_URL
});
assert.match(sql, /REVIEW ONLY/);
assert.match(sql, /© OpenStreetMap contributors/);
assert.match(sql, /ODbL-1\.0/);
assert.match(sql, /queuego_place_source_records/);
assert.match(sql, /queuego_place_field_provenance/);
assert.match(sql, /st_pointonsurface/);
assert.match(sql, /st_geomfromgeojson/);
assert.match(sql, /boundary=excluded\.boundary/);
const areaSql = renderOsmChunkSql([marketArea], {
  retrievedAt: '2026-10-24T00:00:00.000Z', snapshotDate: '2026-10-20',
  pbfSha256: 'a'.repeat(64), exportSha256: 'b'.repeat(64), pbfUrl: OFFICIAL_PBF_URL
});
assert.match(areaSql, /boundary_geojson/);
assert.match(areaSql, /st_pointonsurface/);
assert.match(areaSql, /st_multi\(geom\)/);
assert.match(sql, /osm:node\/101/);
assert.match(sql, /unverified/);
assert.match(sql, /ร้านกาแฟ/);
assert.doesNotMatch(sql, /must not be accepted|\\buid\\b|\\buser\\b|changeset/);
assert.equal(SOURCE_KEY, 'openstreetmap');
assert.equal(SOURCE_LICENSE, 'ODbL-1.0');
assert.equal(ATTRIBUTION, '© OpenStreetMap contributors');

(async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'queuego-osm-stage-'));
  const input = path.join(root, 'sample.geojsonseq'), pbf = path.join(root, 'sample.osm.pbf'), output = path.join(root, 'review');
  const exportText = [feature(301, { amenity: 'cafe', name: 'กาแฟ' }), feature(302, { shop: 'supermarket', name: 'มินิมาร์ท' })]
    .map(value => '\x1e' + JSON.stringify(value) + '\n').join('');
  fs.writeFileSync(input, exportText);
  fs.writeFileSync(pbf, 'isolated non-production OSM checksum fixture');
  const pbfSha256 = checksum(fs.readFileSync(pbf));
  const report = await stageOsmGeoJsonSeq({
    inputFile: input, pbfFile: pbf, outputDir: output, retrievedAt: '2026-10-24T00:00:00Z',
    snapshotDate: '2026-10-20', pbfSha256, chunkRows: 1
  });
  assert.equal(report.summary.staged, 2);
  assert.equal(report.chunks.length, 2);
  assert.equal(report.geojsonseq_sha256, checksum(exportText));
  assert.equal(report.execution, 'review_only_no_database_write');
  assert.ok(fs.existsSync(path.join(output, report.chunks[0].file)));
  assert.ok(fs.existsSync(path.join(output, 'osm-th-stage-report.json')));
  await assert.rejects(stageOsmGeoJsonSeq({
    inputFile: input, pbfFile: pbf, outputDir: path.join(root, 'bad'), retrievedAt: '2026-10-24T00:00:00Z',
    snapshotDate: '2026-10-20', pbfSha256, pbfUrl: 'https://example.com/file.osm.pbf'
  }), /registered Geofabrik/);
  await assert.rejects(stageOsmGeoJsonSeq({
    inputFile: input, pbfFile: pbf, outputDir: path.join(root, 'bad-sha'), retrievedAt: '2026-10-24T00:00:00Z',
    snapshotDate: '2026-10-20', pbfSha256: 'c'.repeat(64)
  }), /PBF SHA-256 mismatch/);
  fs.rmSync(root, { recursive: true, force: true });
  console.log(JSON.stringify({ checks: 49, failures: 0, scope: 'OSM ODbL point and polygon staging, Thailand bounds, stable IDs, category mapping, allowlisted tags, bounded chunks and provenance' }));
})().catch(error => { console.error(error); process.exitCode = 1; });
