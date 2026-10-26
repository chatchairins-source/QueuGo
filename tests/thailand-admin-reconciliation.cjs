'use strict';
const assert=require('node:assert/strict');
const {parentCandidate,buildReport}=require('../scripts/reconcile-thailand-admin-boundaries.cjs');
const osmBilingual={report:{execution:'review_only_no_database_write',osm_license:'ODbL-1.0',oad_commit:'pin',hierarchy_gate:{production_import_allowed:false},summary:{by_admin_type:{province:{deterministic:77,duplicate_osm_assignment:0},district:{deterministic:902,duplicate_osm_assignment:0},subdistrict:{deterministic:757,duplicate_osm_assignment:0}}}}};
const failures=[
 ['104101','subdistrict','outside_parent'],['310108','subdistrict','outside_parent'],['6211','district','outside_parent'],['621101','subdistrict','parent_rejected'],['621102','subdistrict','parent_rejected'],['621103','subdistrict','parent_rejected'],['671003','subdistrict','outside_parent'],['840501','subdistrict','outside_parent'],['9202','district','outside_parent']
].map(([oad_id,admin_type,status],i)=>({oad_id,admin_type,status,source_id:'r'+i,parent_source_id:'p'+i}));
const osmCrosswalk={execution:'review_only_no_database_write',production_import_allowed:false,osm_crosswalk_parent_review:{failures,summary:{district:{checked:902,covered:900,outside_parent:2,parent_rejected:0},subdistrict:{checked:757,covered:750,outside_parent:4,parent_rejected:3}}}};
const osmTopology={execution:'review_only_no_database_write',production_import_allowed:false,geometry_validity_gate:'passed'};
const hdxAudit={execution:'review_only_no_database_write',production_import_allowed:false,source:{license:'CC-BY-3.0-IGO',sha256:'hdx-sha',represented_date:'2022-01-01',metadata_modified:'2026-08-14T06:40:23.604903'},unresolved:[
 {oad_id:'4026',oad_name:'เวียงเก่า',admin_type:'district',status:'missing_source_code'},
 {oad_id:'4029',source_pcode:'TH4029',source_name:'เวียงเก่า',admin_type:'district',status:'extra_source_code'},
 {oad_id:'84',source_pcode:'TH84',source_name:'สุราษฎร์ธานี',oad_name:'สุราษฎร์ธานี',admin_type:'province',status:'centroid_outside'},
 {oad_id:'92',source_pcode:'TH92',source_name:'ตรัง',oad_name:'ตรัง',admin_type:'province',status:'centroid_outside'},
 {oad_id:'8405',source_pcode:'TH8405',source_name:'เกาะพะงัน',oad_name:'เกาะพะงัน',admin_type:'district',status:'parent_unresolved'},
 {oad_id:'9202',source_pcode:'TH9202',source_name:'กันตัง',oad_name:'กันตัง',admin_type:'district',status:'parent_unresolved'},
 {oad_id:'840501',source_pcode:'TH840501',source_name:'เกาะพะงัน',oad_name:'เกาะพะงัน',admin_type:'subdistrict',status:'parent_unresolved'}
]};
const hdxTopology={execution:'review_only_no_database_write',production_import_allowed:false,source_sha256:'hdx-sha',geometry_validity_gate:'passed',source_parent_polygon_gate:'passed',summary:{
 ADM1:{records:77,invalid:0,outside_parent:0,missing_parent:0},
 ADM2:{records:928,invalid:0,outside_parent:0,missing_parent:0},
 ADM3:{records:7425,invalid:0,outside_parent:0,missing_parent:0}
}};
const primaryAcquisition={execution:'review_only_no_database_write',production_import_allowed:false,status:'blocked_primary_code_reconciliation'};
const r=buildReport({osmBilingual,osmCrosswalk,osmTopology,hdxAudit,hdxTopology,primaryAcquisition});
assert.equal(parentCandidate('621101','subdistrict'),'6211');
assert.equal(parentCandidate('4026','district'),'40');
assert.equal(r.production_import_allowed,false);
assert.equal(r.release_decision,'blocked');
assert.equal(r.gates.source_geometry_validity.status,'passed');
assert.equal(r.gates.identity_code_correctness.status,'blocked');
assert.equal(r.gates.full_child_polygon_containment.status,'blocked');
assert.equal(r.gates.reverse_uniqueness.status,'passed_candidate_set');
assert.equal(r.gates.postgis_release_acceptance.status,'not_tested');
assert.equal(r.osm_excluded_rows.length,9);
assert.ok(r.osm_excluded_rows.every(x=>x.status==='quarantined'&&!x.eligible&&!x.canonical_code_approved));
assert.equal(r.named_conflicts[0].canonical_code_candidate,null);
assert.deepEqual(r.named_conflicts[0].candidates.map(x=>x.code),['4026','4029']);
assert.equal(r.named_conflicts[0].status,'quarantined');
assert.ok(r.hdx_unresolved_rows.every(x=>x.status==='quarantined'&&!x.eligible));
const hdxStatuses=r.summary.osm_excluded_hdx_identity_statuses;
assert.deepEqual(hdxStatuses,{code_name_centroid_match:7,parent_unresolved:2});
assert.equal(r.summary.hdx_source_topology_internal_pass_for_all_excluded,true);
assert.ok(r.osm_excluded_rows.every(x=>x.cross_source_decision.geometry_override_allowed===false&&!x.hdx_source_topology.canonical_geometry_approved));
assert.ok(r.osm_excluded_rows.every(x=>x.hdx_source_topology.geometry_validity_gate==='passed'&&x.hdx_source_topology.source_parent_polygon_gate==='passed'));
const row9202=r.osm_excluded_rows.find(x=>x.oad_id==='9202');
assert.equal(row9202.hdx_identity_diagnostic.status,'parent_unresolved');
assert.deepEqual(row9202.hdx_parent_diagnostic_chain.map(x=>[x.oad_id,x.status]),[['92','centroid_outside']]);
const row840501=r.osm_excluded_rows.find(x=>x.oad_id==='840501');
assert.equal(row840501.hdx_identity_diagnostic.status,'parent_unresolved');
assert.deepEqual(row840501.hdx_parent_diagnostic_chain.map(x=>[x.oad_id,x.status]),[['8405','parent_unresolved'],['84','centroid_outside']]);
const row621101=r.osm_excluded_rows.find(x=>x.oad_id==='621101');
assert.deepEqual(row621101.hdx_parent_diagnostic_chain.map(x=>[x.oad_id,x.status]),[['6211','code_name_centroid_match'],['62','code_name_centroid_match']]);
assert.ok(r.osm_excluded_rows.every(x=>x.status==='quarantined'&&!x.eligible));
console.log(JSON.stringify({checks:26,failures:0,scope:'9 OSM exclusions include HDX identity/parent/source-topology diagnostics without geometry override; Production fail-closed'}));
