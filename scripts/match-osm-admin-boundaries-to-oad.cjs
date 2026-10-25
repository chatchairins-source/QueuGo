#!/usr/bin/env node
'use strict';

const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');

const EXPECTED=Object.freeze({province:77,district:928,subdistrict:7364});
const PROVINCE_CENTROID_ROUNDING_TOLERANCE_M=100;
const THAI_PREFIX=/^(จังหวัด|อำเภอ|เขต|ตำบล|แขวง)\s*/;
const EN_SUFFIX=/\s+(province|district|subdistrict)$/i;
const clean=value=>String(value??'').trim().replace(/\s+/g,' ');
const normalizedName=value=>clean(value).replace(THAI_PREFIX,'').replace(EN_SUFFIX,'').replace(/[.·,'’"()\-\s]/g,'').toLowerCase();
const sha256=buffer=>crypto.createHash('sha256').update(buffer).digest('hex');

function onSegment(point,a,b){
  const [x,y]=point,[x1,y1]=a,[x2,y2]=b;
  const cross=(x-x1)*(y2-y1)-(y-y1)*(x2-x1);
  if(Math.abs(cross)>1e-10) return false;
  return x>=Math.min(x1,x2)-1e-12&&x<=Math.max(x1,x2)+1e-12&&y>=Math.min(y1,y2)-1e-12&&y<=Math.max(y1,y2)+1e-12;
}
function ringContains(point,ring){
  let inside=false;
  for(let i=0,j=ring.length-1;i<ring.length;j=i++){
    const a=ring[j],b=ring[i];
    if(onSegment(point,a,b)) return true;
    const intersects=((a[1]>point[1])!==(b[1]>point[1])) &&
      point[0] < (b[0]-a[0])*(point[1]-a[1])/(b[1]-a[1])+a[0];
    if(intersects) inside=!inside;
  }
  return inside;
}
function polygonContains(point,polygon){
  if(!polygon?.length||!ringContains(point,polygon[0])) return false;
  for(let i=1;i<polygon.length;i++) if(ringContains(point,polygon[i])) return false;
  return true;
}
function geometryContains(point,geometry){
  if(!geometry) return false;
  if(geometry.type==='Polygon') return polygonContains(point,geometry.coordinates);
  if(geometry.type==='MultiPolygon') return geometry.coordinates.some(poly=>polygonContains(point,poly));
  return false;
}
function segmentDistanceMeters(point,a,b){
  const latRad=point[1]*Math.PI/180;
  const mx=Math.cos(latRad)*111320;
  const my=110574;
  const ax=(a[0]-point[0])*mx, ay=(a[1]-point[1])*my;
  const bx=(b[0]-point[0])*mx, by=(b[1]-point[1])*my;
  const dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
  const t=den===0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/den));
  return Math.hypot(ax+t*dx,ay+t*dy);
}
function geometryEdgeDistanceMeters(point,geometry){
  let best=Infinity;
  const rings=geometry?.type==='Polygon'?geometry.coordinates:
    geometry?.type==='MultiPolygon'?geometry.coordinates.flat(1):[];
  for(const ring of rings){
    for(let i=1;i<ring.length;i++) best=Math.min(best,segmentDistanceMeters(point,ring[i-1],ring[i]));
  }
  return best;
}

function geometryBbox(geometry){
  const boxes=[];
  const visit=value=>{
    if(Array.isArray(value)&&value.length>=2&&Number.isFinite(Number(value[0]))&&Number.isFinite(Number(value[1]))){
      boxes.push([Number(value[0]),Number(value[1])]); return;
    }
    if(Array.isArray(value)) value.forEach(visit);
  };
  visit(geometry?.coordinates);
  if(!boxes.length) throw new Error('boundary geometry has no coordinates');
  return boxes.reduce((b,p)=>[Math.min(b[0],p[0]),Math.min(b[1],p[1]),Math.max(b[2],p[0]),Math.max(b[3],p[1])],[Infinity,Infinity,-Infinity,-Infinity]);
}
const bboxContains=(bbox,point)=>point[0]>=bbox[0]&&point[0]<=bbox[2]&&point[1]>=bbox[1]&&point[1]<=bbox[3];

function readOsmBoundaries(file){
  const rows=fs.readFileSync(file,'utf8').split('\x1e').map(v=>v.trim()).filter(Boolean).map((raw,index)=>{
    const feature=JSON.parse(raw),p=feature.properties||{};
    if(!['province','district','subdistrict'].includes(p.admin_type)) throw new Error('invalid OSM admin type at record '+(index+1));
    const names=[p.canonical_name,p.name_th,p.osm_name,p.name_en].map(normalizedName).filter(Boolean);
    return {...p,geometry:feature.geometry,bbox:geometryBbox(feature.geometry),names:new Set(names)};
  });
  const byType={province:[],district:[],subdistrict:[]};
  rows.forEach(row=>byType[row.admin_type].push(row));
  return {rows,byType};
}
function parseOad(flatFile,subdistrictFile){
  const flatBuffer=fs.readFileSync(flatFile);
  const subsBuffer=fs.readFileSync(subdistrictFile);
  const flat=flatBuffer.toString('utf8').split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line));
  const subs=JSON.parse(subsBuffer.toString('utf8'));
  const all=[...flat,...subs].map(row=>{
    const type=row.level===1?'province':row.level===2?'district':row.level===3?'subdistrict':null;
    if(!type) throw new Error('unexpected OAD admin level '+row.level);
    const lat=Number(row.geo?.lat),lon=Number(row.geo?.lon);
    if(!Number.isFinite(lat)||!Number.isFinite(lon)||lat<5||lat>21||lon<97||lon>106) throw new Error('invalid OAD centroid '+row.id);
    return {
      id:String(row.id),type,name:clean(row.name?.local),name_en:clean(row.name?.en),
      normalized_name:normalizedName(row.name?.local),point:[lon,lat],
      parent_id:row.parent?.id?String(row.parent.id):null,
      province_id:row.level===1?String(row.id):String(row.ancestors?.find(a=>a.level===1)?.id||''),
      district_id:row.level===2?String(row.id):row.level===3?String(row.parent?.id||''):null
    };
  });
  const counts=all.reduce((acc,row)=>(acc[row.type]++,acc),{province:0,district:0,subdistrict:0});
  for(const type of Object.keys(EXPECTED)) if(counts[type]!==EXPECTED[type]) throw new Error('unexpected OAD '+type+' count '+counts[type]);
  const ids=new Set();
  for(const row of all){ if(ids.has(row.id)) throw new Error('duplicate OAD id '+row.id); ids.add(row.id); }
  return {all,counts,flat_sha256:sha256(flatBuffer),subdistrict_sha256:sha256(subsBuffer)};
}
function candidateRows(oad,osmRows){
  const containing=osmRows.filter(row=>bboxContains(row.bbox,oad.point)&&geometryContains(oad.point,row.geometry));
  const sameName=osmRows.filter(row=>row.names.has(oad.normalized_name));
  const matching=containing.filter(row=>row.names.has(oad.normalized_name));
  return {containing,sameName,matching};
}
function summarizeStatuses(mapping){
  const summary={total:mapping.length,deterministic:0,ambiguous:0,missing:0,name_only_outside_centroid:0,centroid_only_name_mismatch:0,parent_unresolved:0,duplicate_osm_assignment:0};
  for(const row of mapping){
    if(Object.hasOwn(summary,row.status)) summary[row.status]++;
  }
  return summary;
}
function matchHierarchy(osm,oad){
  const mapping=[];
  const resolved=new Map();
  const osmUsed=new Set();
  for(const type of ['province','district','subdistrict']){
    const levelStart=mapping.length;
    for(const item of oad.all.filter(row=>row.type===type)){
      const rows=osm.byType[type];
      const c=candidateRows(item,rows);
      let chosen=null,method=null,status=null;
      if(type==='province'){
        const iso='TH-'+item.id.padStart(2,'0');
        const isoMatches=rows.filter(row=>row.iso3166_2===iso);
        const isoContaining=isoMatches.filter(row=>bboxContains(row.bbox,item.point)&&geometryContains(item.point,row.geometry));
        if(isoContaining.length===1&&isoContaining[0].names.has(item.normalized_name)){
          chosen=isoContaining[0]; method='iso_name_centroid';
        } else if(
          isoMatches.length===1 &&
          isoMatches[0].names.has(item.normalized_name) &&
          geometryEdgeDistanceMeters(item.point,isoMatches[0].geometry)<=PROVINCE_CENTROID_ROUNDING_TOLERANCE_M
        ){
          chosen=isoMatches[0]; method='iso_name_centroid_rounding_tolerance';
        } else if(isoMatches.length>1||c.matching.length>1) status='ambiguous';
      }
      if(!chosen&&!status&&c.matching.length===1){ chosen=c.matching[0]; method='name_centroid'; }
      if(!chosen&&!status&&c.matching.length>1) status='ambiguous';
      if(!chosen&&!status&&c.sameName.length===1) status='name_only_outside_centroid';
      if(!chosen&&!status&&c.containing.length===1) status='centroid_only_name_mismatch';
      if(!chosen&&!status) status='missing';

      let parentOk=true;
      if(chosen&&type==='district'){
        const parent=resolved.get(item.province_id);
        parentOk=!!parent&&bboxContains(parent.osm.bbox,item.point)&&geometryContains(item.point,parent.osm.geometry);
      } else if(chosen&&type==='subdistrict'){
        const parent=resolved.get(item.district_id);
        parentOk=!!parent&&bboxContains(parent.osm.bbox,item.point)&&geometryContains(item.point,parent.osm.geometry);
      }
      if(chosen&&!parentOk){ status='parent_unresolved'; chosen=null; method=null; }
      if(chosen){
        status='deterministic';
        resolved.set(item.id,{oad:item,osm:chosen});
        osmUsed.add(chosen.source_record_id);
      }
      mapping.push({
        oad_id:item.id,admin_type:type,oad_name:item.name,parent_id:item.parent_id,
        status,method,
        osm_source_record_id:chosen?.source_record_id||null,
        osm_name:chosen?.canonical_name||null,
        candidate_name_centroid:c.matching.map(row=>row.source_record_id).slice(0,8),
        candidate_centroid_only:c.containing.map(row=>row.source_record_id).slice(0,8),
        candidate_name_only:c.sameName.map(row=>row.source_record_id).slice(0,8)
      });
    }
    // Reject every side of a collision before resolving the next level. Never
    // let input ordering choose which OAD code gets a shared OSM polygon.
    const claims=new Map();
    for(const row of mapping.slice(levelStart)){
      if(row.status!=='deterministic') continue;
      const key=row.osm_source_record_id;
      if(!claims.has(key)) claims.set(key,[]);
      claims.get(key).push(row);
    }
    for(const [sourceId,rows] of claims){
      if(rows.length<2) continue;
      osmUsed.delete(sourceId);
      for(const row of rows){
        resolved.delete(row.oad_id);
        row.status='duplicate_osm_assignment';
        row.conflicting_oad_ids=rows.map(other=>other.oad_id).sort();
        row.osm_source_record_id=null;
        row.osm_name=null;
        row.method=null;
      }
    }
  }
  const byType={};
  for(const type of ['province','district','subdistrict']) byType[type]=summarizeStatuses(mapping.filter(row=>row.admin_type===type));
  const unused={};
  for(const type of ['province','district','subdistrict']) unused[type]=osm.byType[type].filter(row=>!osmUsed.has(row.source_record_id)).length;
  return {mapping,summary:{by_admin_type:byType,unused_osm_boundaries:unused}};
}
function hierarchyGate(summary){
  const levels={};
  for(const [type,expected] of Object.entries(EXPECTED)){
    const counts=summary.by_admin_type[type];
    levels[type]={expected,deterministic:counts.deterministic,
      unresolved:counts.total-counts.deterministic,
      passed:counts.total===expected&&counts.deterministic===expected};
  }
  const passed=Object.values(levels).every(level=>level.passed);
  return {status:passed?'passed':'blocked',levels,
    // This audit certifies only centroid/name/parent identity and injectivity.
    // Topology, provenance/licensing and village gates remain independent.
    production_import_allowed:false};
}
function run({boundariesFile,flatFile,subdistrictFile,outputDir}){
  const osm=readOsmBoundaries(boundariesFile);
  const oad=parseOad(flatFile,subdistrictFile);
  const matched=matchHierarchy(osm,oad);
  fs.mkdirSync(outputDir,{recursive:true});
  if(fs.readdirSync(outputDir).length) throw new Error('output directory must be empty');
  const report={
    execution:'review_only_no_database_write',
    osm_source:'openstreetmap',osm_license:'ODbL-1.0',osm_attribution:'© OpenStreetMap contributors',
    osm_normalized_sha256:sha256(fs.readFileSync(boundariesFile)),
    oad_commit:'eef739a534e26181c86073d69e9aeb527110cf1a',
    oad_flat_git_blob:'baa3eb18fb79412e85dcef08059d16f8cf47b317',
    oad_subdistrict_git_blob:'bd5c49a7e1af578c9e34e32020ddd90eabdc167f',
    oad_flat_sha256:oad.flat_sha256,oad_subdistrict_sha256:oad.subdistrict_sha256,
    oad_counts:oad.counts,osm_counts:Object.fromEntries(Object.entries(osm.byType).map(([k,v])=>[k,v.length])),
    summary:matched.summary,
    hierarchy_gate:hierarchyGate(matched.summary)
  };
  fs.writeFileSync(path.join(outputDir,'osm-oad-admin-boundary-match-report.json'),JSON.stringify(report,null,2)+'\n');
  fs.writeFileSync(path.join(outputDir,'osm-oad-admin-boundary-mapping.json'),JSON.stringify({execution:report.execution,matches:matched.mapping},null,2)+'\n');
  return {report,mapping:matched.mapping};
}
if(require.main===module){
  const [boundariesFile,flatFile,subdistrictFile,outputDir]=process.argv.slice(2);
  try{const result=run({boundariesFile,flatFile,subdistrictFile,outputDir});console.error(JSON.stringify(result.report.summary));}
  catch(error){console.error(error.stack||error.message);process.exitCode=1;}
}
module.exports={EXPECTED,PROVINCE_CENTROID_ROUNDING_TOLERANCE_M,normalizedName,onSegment,ringContains,polygonContains,geometryContains,segmentDistanceMeters,geometryEdgeDistanceMeters,geometryBbox,parseOad,candidateRows,matchHierarchy,hierarchyGate,run};
