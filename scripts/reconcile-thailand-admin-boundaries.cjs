#!/usr/bin/env node
'use strict';
const fs=require('node:fs');
const crypto=require('node:crypto');
const path=require('node:path');
const sha256=b=>crypto.createHash('sha256').update(b).digest('hex');
const stable=(a,b)=>String(a.admin_type||'').localeCompare(String(b.admin_type||''))||String(a.oad_id||a.source_pcode||'').localeCompare(String(b.oad_id||b.source_pcode||''));
function readJson(file){const raw=fs.readFileSync(file);return {raw,json:JSON.parse(raw)};}
function parentCandidate(id,type){id=String(id||''); if(type==='district'&&/^\d{4}$/.test(id)) return id.slice(0,2); if(type==='subdistrict'&&/^\d{6}$/.test(id)) return id.slice(0,4); return null;}
function assertReviewOnly(doc,label){if(doc.execution!=='review_only_no_database_write'||doc.production_import_allowed!==false) throw Error(label+' must be review-only and fail closed');}
function hdxDiagnostic(hdxByKey,id,type){
  const key=`${type}:${id}`,h=hdxByKey.get(key);
  if(h)return {oad_id:String(id),admin_type:type,source_pcode:h.source_pcode||null,source_name:h.source_name||null,oad_name:h.oad_name||null,status:h.status,inferred:false};
  return {oad_id:String(id),admin_type:type,source_pcode:'TH'+String(id),source_name:null,oad_name:null,status:'code_name_centroid_match',inferred:true,inference_basis:'Exact HDX audit persists every non-match in unresolved; absence means code/name/centroid/resolved-parent candidate match'};
}
function hdxParentDiagnosticChain(hdxByKey,id,type){
  const chain=[]; let currentId=String(id),currentType=type;
  while(currentType!=='province'){
    const p=parentCandidate(currentId,currentType); if(!p)break;
    const pt=currentType==='subdistrict'?'district':'province';
    chain.push(hdxDiagnostic(hdxByKey,p,pt)); currentId=p; currentType=pt;
  }
  return chain;
}
function hdxTopologyEvidence(hdxTopology,type){
  const level=type==='district'?'ADM2':type==='subdistrict'?'ADM3':'ADM1';
  const x=hdxTopology.summary?.[level]; if(!x)throw Error('missing HDX topology summary for '+level);
  return {scope:'source_internal_level_gate_not_cross_source_approval',level,source_sha256:hdxTopology.source_sha256,records:x.records,invalid:x.invalid,outside_parent:x.outside_parent,missing_parent:x.missing_parent,geometry_validity_gate:hdxTopology.geometry_validity_gate,source_parent_polygon_gate:hdxTopology.source_parent_polygon_gate,canonical_geometry_approved:false,geometry_override_allowed:false};
}
function buildReport({osmBilingual,osmCrosswalk,osmTopology,hdxAudit,hdxTopology,primaryAcquisition,inputHashes={}}){
  assertReviewOnly(osmCrosswalk,'OSM crosswalk'); assertReviewOnly(osmTopology,'OSM topology'); assertReviewOnly(hdxAudit,'HDX audit'); assertReviewOnly(hdxTopology,'HDX topology'); assertReviewOnly(primaryAcquisition,'primary acquisition');
  const br=osmBilingual.report||osmBilingual; if(br.execution!=='review_only_no_database_write'||br.hierarchy_gate?.production_import_allowed!==false) throw Error('OSM bilingual report must fail closed');
  if(br.osm_license!=='ODbL-1.0'||hdxAudit.source?.license!=='CC-BY-3.0-IGO') throw Error('expected source licenses not present');
  if(osmTopology.geometry_validity_gate!=='passed'||hdxTopology.geometry_validity_gate!=='passed'||hdxTopology.source_parent_polygon_gate!=='passed') throw Error('source topology prerequisites not passed');
  if(hdxTopology.source_sha256!==hdxAudit.source?.sha256) throw Error('HDX audit/topology source checksum mismatch');
  const failures=osmCrosswalk.osm_crosswalk_parent_review?.failures||[];
  if(failures.length!==9) throw Error('expected exactly 9 OSM matched-parent exclusions');
  const hdxUnresolved=hdxAudit.unresolved||[];
  const hdxByKey=new Map(hdxUnresolved.map(r=>[`${r.admin_type}:${r.oad_id}`,r]));
  const osmExcludedRows=failures.map(f=>{
    const hd=hdxDiagnostic(hdxByKey,f.oad_id,f.admin_type);
    const parentChain=hdxParentDiagnosticChain(hdxByKey,f.oad_id,f.admin_type);
    const hdxTopo=hdxTopologyEvidence(hdxTopology,f.admin_type);
    return {
      admin_type:f.admin_type,oad_id:f.oad_id,
      source_a:{source:'osm',source_record_id:f.source_id,parent_source_record_id:f.parent_source_id,matched_parent_status:f.status,license:'ODbL-1.0'},
      source_b:{source:'hdx',source_pcode:hd.source_pcode,status:hd.status,name:hd.source_name,represented_date:hdxAudit.source.represented_date,license:hdxAudit.source.license,inferred_from_unresolved_complement:hd.inferred===true},
      hdx_identity_diagnostic:hd,
      hdx_parent_diagnostic_chain:parentChain,
      hdx_source_topology:hdxTopo,
      cross_source_decision:{identity_diagnostic_only:true,geometry_override_allowed:false,canonical_geometry_approved:false,reason:'HDX identity/source-internal topology cannot override failed OSM matched-parent containment or certify HDX as canonical geometry'},
      canonical_code_candidate:f.oad_id,canonical_code_approved:false,
      canonical_parent_candidate:parentCandidate(f.oad_id,f.admin_type),canonical_parent_approved:false,
      evidence:{osm_matched_parent_gate:f.status,hdx_identity_diagnostic:hd.status,hdx_parent_diagnostic_chain:parentChain.map(x=>({oad_id:x.oad_id,status:x.status})),hdx_source_geometry_validity:hdxTopo.geometry_validity_gate,hdx_source_parent_polygon_gate:hdxTopo.source_parent_polygon_gate,primary_reference_status:primaryAcquisition.status},
      unresolved_reason:f.status==='parent_rejected'?'OSM parent polygon failed full containment; descendant inherits quarantine regardless of HDX diagnostics':'OSM full child polygon is outside its matched parent polygon; HDX diagnostics do not override cross-source containment and current canonical primary identity remains unavailable',
      status:'quarantined',eligible:false
    };
  }).sort(stable);
  const hdxRows=hdxUnresolved.map(h=>({
    admin_type:h.admin_type,oad_id:h.oad_id,
    source_a:{source:'oad_pinned_candidate',code:h.oad_name?String(h.oad_id):null,name:h.oad_name||null,commit:br.oad_commit||null},
    source_b:{source:'hdx',source_pcode:h.source_pcode||null,name:h.source_name||null,status:h.status,represented_date:hdxAudit.source.represented_date,license:hdxAudit.source.license},
    canonical_code_candidate:h.status==='extra_source_code'?null:String(h.oad_id),canonical_code_approved:false,
    canonical_parent_candidate:parentCandidate(h.oad_id,h.admin_type),canonical_parent_approved:false,
    evidence:{diagnostic:h.status,primary_reference_status:primaryAcquisition.status},
    unresolved_reason:`HDX/OAD ${h.status}; current versioned primary administrative-code bytes are unavailable`,
    status:'quarantined',eligible:false
  })).sort(stable);
  const wiang={
    name:'เวียงเก่า',admin_type:'district',candidates:[
      {source:'oad_pinned_candidate',code:'4026',status:'missing_source_code_in_hdx'},
      {source:'hdx',code:'4029',source_pcode:'TH4029',status:'extra_source_code'}
    ],canonical_code_candidate:null,canonical_parent_candidate:'40',canonical_code_approved:false,canonical_parent_approved:false,
    evidence:{primary_reference_status:primaryAcquisition.status,related_oad_subdistricts:['402602','402608','402615'],related_hdx_subdistricts:['402901','402902','402903']},
    unresolved_reason:'Sources disagree on district/subdistrict codes; no current versioned primary code payload has been acquired. Preserve both identities; no remap.',status:'quarantined',eligible:false
  };
  const dup=br.summary?.by_admin_type||{};
  const reverseUnique=['province','district','subdistrict'].every(k=>(dup[k]?.duplicate_osm_assignment||0)===0);
  const gates={
    identity_code_correctness:{status:'blocked',reason:'current_versioned_primary_reference_unavailable'},
    bilingual_name_match:{status:'candidate_only',evidence:{province:dup.province?.deterministic,district:dup.district?.deterministic,subdistrict:dup.subdistrict?.deterministic}},
    centroid_containment:{status:'candidate_only',reason:'representative-point containment is not boundary approval'},
    source_geometry_validity:{status:'passed',evidence:{osm:osmTopology.geometry_validity_gate,hdx:hdxTopology.geometry_validity_gate}},
    full_child_polygon_containment:{status:'blocked',evidence:{excluded:failures.length,district:osmCrosswalk.osm_crosswalk_parent_review.summary.district,subdistrict:osmCrosswalk.osm_crosswalk_parent_review.summary.subdistrict}},
    parent_hierarchy_containment:{status:'blocked',reason:'2 district polygons + 4 subdistrict polygons are outside matched parents; 3 descendants inherit rejected parent'},
    reverse_uniqueness:{status:reverseUnique?'passed_candidate_set':'blocked',reason:'OSM candidate matcher reverse-assignment gate only'},
    license_provenance:{status:'blocked',evidence:{osm_license:br.osm_license,hdx_license:hdxAudit.source.license,primary_reference:primaryAcquisition.status},reason:'OSM/HDX provenance is recorded, but canonical primary-code provenance is unresolved'},
    source_freshness:{status:'blocked',evidence:{osm_snapshot:'2026-10-24',hdx_represented_date:hdxAudit.source.represented_date,hdx_metadata_modified:hdxAudit.source.metadata_modified},reason:'HDX represented boundary date is 2022-01-01 and current primary code bytes are unavailable'},
    postgis_release_acceptance:{status:'not_tested'},
    full_canonical_hierarchy_coverage:{status:'blocked'}
  };
  const osmExcludedHdxStatuses=osmExcludedRows.reduce((a,row)=>(a[row.hdx_identity_diagnostic.status]=(a[row.hdx_identity_diagnostic.status]||0)+1,a),{});
  const hdxTopologyInternalPassForAllExcluded=osmExcludedRows.every(row=>row.hdx_source_topology.geometry_validity_gate==='passed'&&row.hdx_source_topology.source_parent_polygon_gate==='passed'&&row.hdx_source_topology.invalid===0&&row.hdx_source_topology.outside_parent===0&&row.hdx_source_topology.missing_parent===0);
  return {execution:'review_only_no_database_write',report_version:2,input_sha256:inputHashes,gates,
    summary:{osm_matched_parent_excluded:osmExcludedRows.length,osm_excluded_hdx_identity_statuses:osmExcludedHdxStatuses,hdx_source_topology_internal_pass_for_all_excluded:hdxTopologyInternalPassForAllExcluded,hdx_unresolved:hdxRows.length,wiang_kao_conflict:true,eligible_rows:0,quarantined_rows:osmExcludedRows.length+hdxRows.length+1},
    osm_excluded_rows:osmExcludedRows,hdx_unresolved_rows:hdxRows,named_conflicts:[wiang],production_import_allowed:false,
    release_decision:'blocked',next:'Acquire checksum-pinned current/versioned primary administrative-code bytes; reconcile all quarantined identities, then rerun independent hierarchy, provenance/freshness and PostGIS release gates. No Production import.'};
}
function run(files,out){
  const docs={},hashes={}; for(const [k,file] of Object.entries(files)){const r=readJson(file);docs[k]=r.json;hashes[k]=sha256(r.raw);}
  const report=buildReport({...docs,inputHashes:hashes}); if(out){if(fs.existsSync(out)) throw Error('output must be new');fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');} return report;
}
if(require.main===module){const args=process.argv.slice(2);if(args.length!==7)throw Error('usage: reconcile <osm-bilingual> <osm-crosswalk> <osm-topology> <hdx-audit> <hdx-topology> <primary-acquisition> <output>'); const keys=['osmBilingual','osmCrosswalk','osmTopology','hdxAudit','hdxTopology','primaryAcquisition'];const files=Object.fromEntries(keys.map((k,i)=>[k,args[i]]));const r=run(files,args[6]);console.log(JSON.stringify(r.summary));}
module.exports={parentCandidate,buildReport,run};
