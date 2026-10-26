'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const crypto=require('node:crypto');
const {SOURCE,LICENSE,ATTRIBUTION,ADMIN_LEVELS,normalizeAdminBoundary,parseGeoJsonSeq,stageAdminBoundaries}=require('../scripts/stage-osm-admin-boundaries.cjs');

const exporter=fs.readFileSync(path.join(__dirname,'../scripts/export-osm-admin-boundaries-geojsonseq.py'),'utf8');
assert.match(exporter,/boundary.*administrative/);
assert.match(exporter,/ADMIN_LEVELS = \{'4': 'province', '6': 'district', '8': 'subdistrict'\}/);
assert.match(exporter,/create_multipolygon/);
assert.match(exporter,/orig_id\(\)/);
assert.match(exporter,/locations=True/);
assert.doesNotMatch(exporter,/changeset|['"]user['"]|['"]uid['"]/);

const polygon=[[[100,13],[100.2,13],[100.2,13.2],[100,13.2],[100,13]]];
const feature=(id,level,name='กรุงเทพมหานคร')=>({
 type:'Feature',
 properties:{'@type':'relation','@id':id,'@timestamp':1792497600,boundary:'administrative',admin_level:String(level),'name:th':name,name:'Bangkok','name:en':'Bangkok','ISO3166-2':'TH-10'},
 geometry:{type:'Polygon',coordinates:polygon}
});
const p=normalizeAdminBoundary(feature(1,4));
assert.equal(p.admin_type,'province');
assert.equal(p.canonical_name,'กรุงเทพมหานคร');
assert.equal(p.iso3166_2,'TH-10');
assert.equal(p.source_record_id,'relation/1');
assert.match(p.source_payload_hash,/^[a-f0-9]{64}$/);
assert.equal(normalizeAdminBoundary(feature(2,6,'เขตตัวอย่าง')).admin_type,'district');
assert.equal(normalizeAdminBoundary(feature(3,8,'แขวงตัวอย่าง')).admin_type,'subdistrict');
assert.equal(normalizeAdminBoundary(feature(4,10)).skipped,'not_target_admin_level');
assert.equal(normalizeAdminBoundary({...feature(5,4),properties:{...feature(5,4).properties,boundary:'protected_area'}}).skipped,'not_target_admin_boundary');
assert.equal(normalizeAdminBoundary({...feature(6,4),geometry:{type:'LineString',coordinates:[[100,13],[101,14]]}}).skipped,'unsupported_geometry');
assert.throws(()=>normalizeAdminBoundary({...feature(7,4),geometry:{type:'Polygon',coordinates:[[[100,13],[100.2,13],[100.2,13.2],[100,13.2]]]}}),/invalid administrative polygon ring/);
assert.equal(normalizeAdminBoundary({...feature(8,4),geometry:{type:'Polygon',coordinates:[[[96,13],[96.2,13],[96.2,13.2],[96,13.2],[96,13]]]}}).skipped,'outside_thailand_bounds');
assert.deepEqual(ADMIN_LEVELS,{'4':'province','6':'district','8':'subdistrict'});
assert.equal(SOURCE,'openstreetmap');
assert.equal(LICENSE,'ODbL-1.0');
assert.equal(ATTRIBUTION,'© OpenStreetMap contributors');

const seq=[feature(11,4,'จังหวัดหนึ่ง'),feature(12,6,'อำเภอหนึ่ง'),feature(13,8,'ตำบลหนึ่ง')].map(v=>'\x1e'+JSON.stringify(v)+'\n').join('');
const parsed=parseGeoJsonSeq(seq);
assert.equal(parsed.summary.staged,3);
assert.deepEqual(parsed.summary.staged_by_admin_type,{province:1,district:1,subdistrict:1});
assert.throws(()=>parseGeoJsonSeq('\x1e'+JSON.stringify(feature(11,4))+'\n\x1e'+JSON.stringify(feature(11,4))+'\n'),/duplicate OSM admin object/);

(async()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'qg-osm-admin-'));
 const input=path.join(root,'input.geojsonseq'),out=path.join(root,'out');
 fs.writeFileSync(input,seq);
 const report=await stageAdminBoundaries({
   inputFile:input,outputDir:out,
   pbfUrl:'https://download.geofabrik.de/asia/thailand-261024.osm.pbf',
   pbfSha256:'a'.repeat(64),snapshotDate:'2026-10-24',retrievedAt:'2026-10-25T10:59:51Z'
 });
 assert.equal(report.execution,'review_only_no_database_write');
 assert.equal(report.summary.staged,3);
 const normalized=fs.readFileSync(path.join(out,'osm-th-admin-boundaries.normalized.geojsonseq'),'utf8');
 assert.equal(report.normalized_sha256,crypto.createHash('sha256').update(normalized).digest('hex'));
 assert.ok(fs.existsSync(path.join(out,'osm-th-admin-boundaries-stage-report.json')));
 fs.rmSync(root,{recursive:true,force:true});
 console.log(JSON.stringify({checks:27,failures:0,scope:'review-only OSM administrative boundary extraction/staging levels 4/6/8; no Production mutation'}));
})().catch(error=>{console.error(error);process.exitCode=1});
