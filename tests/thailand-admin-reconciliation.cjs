'use strict';
const assert=require('node:assert/strict');
const {parentCandidate,buildReport}=require('../scripts/reconcile-thailand-admin-boundaries.cjs');
const osmBilingual={report:{execution:'review_only_no_database_write',osm_license:'ODbL-1.0',oad_commit:'pin',hierarchy_gate:{production_import_allowed:false},summary:{by_admin_type:{province:{deterministic:77,duplicate_osm_assignment:0},district:{deterministic:902,duplicate_osm_assignment:0},subdistrict:{deterministic:757,duplicate_osm_assignment:0}}}}};
const failures=[
 ['104101','subdistrict','outside_parent'],['310108','subdistrict','outside_parent'],['6211','district','outside_parent'],['621101','subdistrict','parent_rejected'],['621102','subdistrict','parent_rejected'],['621103','subdistrict','parent_rejected'],['671003','subdistrict','outside_parent'],['840501','subdistrict','outside_parent'],['9202','district','outside_parent']
].map(([oad_id,admin_type,status],i)=>({oad_id,admin_type,status,source_id:'r'+i,parent_source_id:'p'+i}));
const osmCrosswalk={execution:'review_only_no_database_write',production_import_allowed:false,osm_crosswalk_parent_review:{failures,summary:{district:{checked:902,covered:900,outside_parent:2,parent_rejected:0},subdistrict:{checked:757,covered:750,outside_parent:4,parent_rejected:3}}}};
const osmTopology={execution:'review_only_no_database_write',production_import_allowed:false,geometry_validity_gate:'passed'};
const hdxAudit={execution:'review_only_no_database_write',production_import_allowed:false,source:{license:'CC-BY-3.0-IGO',represented_date:'2022-01-01',metadata_modified:'2026-08-14T06:40:23.604903'},unresolved:[
 {oad_id:'4026',oad_name:'เวียงเก่า',admin_type:'district',status:'missing_source_code'},
 {oad_id:'4029',source_pcode:'TH4029',source_name:'เวียงเก่า',admin_type:'district',status:'extra_source_code'},
 {oad_id:'840501',source_pcode:'TH840501',source_name:'เกาะพะงัน',oad_name:'เกาะพะงัน',admin_type:'subdistrict',status:'parent_unresolved'}
]};
const hdxTopology={execution:'review_only_no_database_write',production_import_allowed:false,geometry_validity_gate:'passed',source_parent_polygon_gate:'passed'};
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
console.log(JSON.stringify({checks:15,failures:0,scope:'deterministic Thailand admin reconciliation; gates remain independent; Production fail-closed'}));
