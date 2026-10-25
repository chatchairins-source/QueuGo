'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {normalizedName,geometryContains,matchHierarchy}=require('../scripts/match-osm-admin-boundaries-to-oad.cjs');

assert.equal(normalizedName('จังหวัด อุบลราชธานี'),'อุบลราชธานี');
assert.equal(normalizedName('อำเภอเมืองเชียงใหม่'),'เมืองเชียงใหม่');
assert.equal(normalizedName('แขวง บางมด'),'บางมด');

const square=(x1,y1,x2,y2)=>({type:'Polygon',coordinates:[[[x1,y1],[x2,y1],[x2,y2],[x1,y2],[x1,y1]]]});
assert.equal(geometryContains([100.5,13.5],square(100,13,101,14)),true);
assert.equal(geometryContains([102,13.5],square(100,13,101,14)),false);

const wrap=(source_record_id,admin_type,name,geometry,iso3166_2=null)=>({
  source_record_id,admin_type,canonical_name:name,name_th:name,name_en:null,osm_name:name,iso3166_2,
  geometry,bbox:[geometry.coordinates[0][0][0],geometry.coordinates[0][0][1],geometry.coordinates[0][2][0],geometry.coordinates[0][2][1]],
  names:new Set([normalizedName(name)])
});
const osm={byType:{
  province:[wrap('relation/p','province','จังหวัดหนึ่ง',square(100,10,104,16),'TH-10')],
  district:[wrap('relation/d','district','อำเภอเมืองหนึ่ง',square(101,11,103,15)),wrap('relation/island','district','เกาะตัวอย่าง',square(101.2,11.2,101.4,11.4))],
  subdistrict:[wrap('relation/s','subdistrict','ตำบลกลาง',square(101.5,12,102.5,14))]
}};
const oad={all:[
  {id:'10',type:'province',name:'หนึ่ง',normalized_name:'หนึ่ง',point:[102,13],parent_id:null,province_id:'10',district_id:null},
  {id:'1001',type:'district',name:'เมืองหนึ่ง',normalized_name:'เมืองหนึ่ง',point:[102,13],parent_id:'10',province_id:'10',district_id:'1001'},
  {id:'100101',type:'subdistrict',name:'กลาง',normalized_name:'กลาง',point:[102,13],parent_id:'1001',province_id:'10',district_id:'1001'}
]};
const matched=matchHierarchy(osm,oad);
assert.equal(matched.summary.by_admin_type.province.deterministic,1);
assert.equal(matched.summary.by_admin_type.district.deterministic,1);
assert.equal(matched.summary.by_admin_type.subdistrict.deterministic,1);
assert.equal(matched.summary.unused_osm_boundaries.district,1);
assert.deepEqual(matched.mapping.map(x=>x.status),['deterministic','deterministic','deterministic']);

const ambiguous={byType:{...osm.byType,district:[...osm.byType.district,wrap('relation/d2','district','อำเภอเมืองหนึ่ง',square(101.8,12.8,102.2,13.2))]}};
const amb=matchHierarchy(ambiguous,oad);
assert.equal(amb.summary.by_admin_type.district.ambiguous,1);
assert.equal(amb.summary.by_admin_type.subdistrict.parent_unresolved,1);

console.log(JSON.stringify({checks:14,failures:0,scope:'OAD centroid/name/parent deterministic matching to review-only OSM admin polygons'}));
