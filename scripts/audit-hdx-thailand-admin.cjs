#!/usr/bin/env node
'use strict';
// Candidate review only: never emits SQL or changes canonical administrative Places.
const fs=require('node:fs');
const crypto=require('node:crypto');
const path=require('node:path');
const readline=require('node:readline');
const {spawn}=require('node:child_process');
const {parseOad,normalizedName,geometryContains}=require('./match-osm-admin-boundaries-to-oad.cjs');
const PIN=Object.freeze({
  dataset:'https://data.humdata.org/dataset/cod-ab-tha',
  resource_id:'89a09f13-7b83-458a-9531-4f2418613065',
  url:'https://data.humdata.org/dataset/d24bdc45-eb4c-4e3d-8b16-44db02667c27/resource/89a09f13-7b83-458a-9531-4f2418613065/download/tha_admin_boundaries.geojson.zip',
  bytes:436725731,
  sha256:'02370dc5bd824563de71fd71cc1ee0b88df717c935a09bd7f15611560ef8bfd1',
  license:'CC-BY-3.0-IGO',license_url:'https://creativecommons.org/licenses/by/3.0/igo/legalcode',
  attribution:'OCHA Regional Office for Asia and the Pacific (ROAP); OCHA Field Information Services (FIS); HDX',
  represented_date:'2022-01-01',metadata_modified:'2026-08-14T06:40:23.604903'
});
const TYPES={1:'province',2:'district',3:'subdistrict'};
function auditFeature(feature,level,oadById,parentResults){
  const p=feature.properties||{};
  const code=p[`adm${level}_pcode`];
  if(typeof code!=='string'||!new RegExp('^TH[0-9]{'+(2*level)+'}$').test(code)) throw Error('invalid source pcode');
  const id=code.slice(2),item=oadById.get(id);
  const result={source_pcode:code,oad_id:id,admin_type:TYPES[level],source_name:p[`adm${level}_name1`]||p[`adm${level}_name`],status:null};
  if(!item){result.status='extra_source_code';return result;}
  if(item.type!==TYPES[level]) throw Error('source/OAD level mismatch');
  result.oad_name=item.name;
  if(p.adm0_pcode!=='TH'||(level>1&&p.adm1_pcode!=='TH'+item.province_id)||(level>2&&p.adm2_pcode!=='TH'+item.district_id)){
    result.status='parent_code_mismatch';return result;
  }
  const sourceNames=[p[`adm${level}_name1`],p[`adm${level}_name`],p[`adm${level}_ref_name`]].map(normalizedName).filter(Boolean);
  const oadNames=[item.normalized_name,item.normalized_name_en].filter(Boolean);
  if(!oadNames.some(name=>sourceNames.includes(name))){result.status='name_mismatch';return result;}
  if(!feature.geometry||!['Polygon','MultiPolygon'].includes(feature.geometry.type)) throw Error('polygon geometry required');
  if(!geometryContains(item.point,feature.geometry)){result.status='centroid_outside';return result;}
  if(level>1&&parentResults.get(item.parent_id)?.status!=='code_name_centroid_match'){
    result.status='parent_unresolved';return result;
  }
  result.status='code_name_centroid_match';return result;
}
async function hashFile(file){
  const h=crypto.createHash('sha256');for await(const chunk of fs.createReadStream(file))h.update(chunk);return h.digest('hex');
}
async function auditArchive(zipFile,oad){
  if(fs.statSync(zipFile).size!==PIN.bytes||await hashFile(zipFile)!==PIN.sha256) throw Error('HDX source checksum/size mismatch');
  const oadById=new Map(oad.all.map(row=>[row.id,row]));
  const resolved=new Map(),results=[],counts={};
  for(const level of [1,2,3]){
    const proc=spawn('unzip',['-p',zipFile,`tha_admin${level}.geojson`],{stdio:['ignore','pipe','pipe']});
    const exit=new Promise((resolve,reject)=>{proc.on('error',reject);proc.on('close',code=>code===0?resolve():reject(Error('unzip failed '+code)));});
    // Handle errors immediately, even if the stream throws before awaiting exit.
    exit.catch(()=>{});
    let stderr='';proc.stderr.on('data',chunk=>{stderr=(stderr+chunk).slice(-1000);});
    const rows=[];
    try{
      for await(const line of readline.createInterface({input:proc.stdout,crlfDelay:Infinity})){
        const text=line.trim();
        // The checksum-pinned upstream GeoJSON has exactly one Feature per line.
        if(!text.startsWith('{ "type": "Feature",')) continue;
        rows.push(auditFeature(JSON.parse(text.replace(/,$/,'')),level,oadById,resolved));
      }
      await exit;
    }catch(error){proc.kill();throw error;}
    if(rows.length!==({1:77,2:928,3:7425})[level])throw Error('source feature count/serialization mismatch '+stderr);
    const sourceCount=rows.length;
    const grouped=new Map();
    for(const row of rows){if(!grouped.has(row.source_pcode))grouped.set(row.source_pcode,[]);grouped.get(row.source_pcode).push(row);}
    for(const group of grouped.values()){
      if(group.length>1)for(const row of group)row.status='duplicate_source_code';
    }
    for(const row of rows)if(row.status==='code_name_centroid_match')resolved.set(row.oad_id,row);
    const seen=new Set(rows.map(row=>row.oad_id));
    for(const item of oad.all.filter(row=>row.type===TYPES[level]))if(!seen.has(item.id))rows.push({oad_id:item.id,oad_name:item.name,admin_type:item.type,status:'missing_source_code'});
    counts[TYPES[level]]={source_records:sourceCount,source_unique_codes:grouped.size,oad_records:oad.counts[TYPES[level]],statuses:rows.reduce((a,r)=>(a[r.status]=(a[r.status]||0)+1,a),{})};
    results.push(...rows);
  }
  return {execution:'review_only_no_database_write',source:PIN,oad_counts:oad.counts,
    oad_flat_sha256:oad.flat_sha256,oad_subdistrict_sha256:oad.subdistrict_sha256,
    summary:counts,production_import_allowed:false,
    topology_gate:'not_tested',parent_polygon_containment_gate:'not_tested',
    note:'Exact code, published bilingual name, OAD centroid and resolved parent-code diagnostics only. This is not promotion approval, a cross-source union or a canonical data replacement.',
    unresolved:results.filter(row=>row.status!=='code_name_centroid_match')};
}
async function main(){
  const [zipFile,flatFile,subdistrictFile,outputFile]=process.argv.slice(2);
  if(!outputFile||fs.existsSync(outputFile))throw Error('new output file required');
  // Preserve the existing exact OAD source identity checks in this standalone audit.
  const gitBlob=file=>{const b=fs.readFileSync(file);return crypto.createHash('sha1').update(Buffer.from(`blob ${b.length}\0`)).update(b).digest('hex');};
  if(gitBlob(flatFile)!=='baa3eb18fb79412e85dcef08059d16f8cf47b317'||gitBlob(subdistrictFile)!=='bd5c49a7e1af578c9e34e32020ddd90eabdc167f')throw Error('OAD pinned bytes mismatch');
  const report=await auditArchive(zipFile,parseOad(flatFile,subdistrictFile));
  fs.mkdirSync(path.dirname(outputFile),{recursive:true});fs.writeFileSync(outputFile,JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report.summary));
}
if(require.main===module)main().catch(error=>{console.error(error.stack||error);process.exitCode=1;});
module.exports={PIN,auditFeature,auditArchive};
