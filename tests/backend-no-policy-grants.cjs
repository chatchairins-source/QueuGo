'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawnSync}=require('node:child_process');
const {auditNoPolicyTables}=require('../scripts/audit-supabase-no-policy-grants.cjs');
let checks=0;
const check=(condition,message)=>{assert.ok(condition,message);checks++};
const row={schema_name:'public',table_name:'private_receipts',rls_enabled:true,policy_count:0,
  anon_select:false,anon_insert:false,anon_update:false,anon_delete:false,
  authenticated_select:false,authenticated_insert:false,authenticated_update:false,authenticated_delete:false};
assert.throws(()=>auditNoPolicyTables([],1),/Snapshot count differs/);checks++;
assert.throws(()=>auditNoPolicyTables([],0),/nonempty metadata rows/);checks++;
assert.throws(()=>auditNoPolicyTables([row]),/catalog count/);checks++;
assert.throws(()=>auditNoPolicyTables([row],2),/Snapshot count differs/);checks++;
assert.throws(()=>auditNoPolicyTables([row],-1),/catalog count/);checks++;
const safe=auditNoPolicyTables([row],1);
check(safe.assessment==='NO_POLICY_TABLE_GRANTS_ABSENT_NOT_SECURITY_CERTIFIED','deny-only snapshot is not certification');
check(safe.counts.noPolicyTables===1 && safe.counts.grantsFound===0,'no direct grants');
const risk=auditNoPolicyTables([{...row,authenticated_update:true,anon_select:true}],1);
check(risk.assessment==='NO_POLICY_GRANTS_REQUIRE_REVIEW','direct grants require review');
check(risk.grants.length===2,'reports all grants');
assert.throws(()=>auditNoPolicyTables([row,row],2),/Duplicate table/);checks++;
assert.throws(()=>auditNoPolicyTables([{...row,anon_delete:null}],1),/Missing boolean/);checks++;
assert.throws(()=>auditNoPolicyTables([{...row,policy_count:1}],1),/Invalid no-policy/);checks++;
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-rls-'));
try {
  const file=path.join(dir,'snapshot.json'),cli=path.join(__dirname,'../scripts/audit-supabase-no-policy-grants.cjs');
  const run=(...args)=>spawnSync(process.execPath,[cli,...args],{encoding:'utf8'});
  fs.writeFileSync(file,JSON.stringify({expected_catalog_count:1,tables:[row]}));
  check(run(file).status===0,'safe metadata exit zero');
  fs.writeFileSync(file,JSON.stringify({expected_catalog_count:2,tables:[row]}));
  check(run(file).status===1,'truncated snapshot fails closed');
  fs.writeFileSync(file,JSON.stringify({tables:[row]}));
  check(run(file).status===1,'missing catalog count fails closed');
  fs.writeFileSync(file,JSON.stringify({expected_catalog_count:1,tables:[]}));
  check(run(file).status===1,'empty snapshot fails closed');
  fs.writeFileSync(file,JSON.stringify({expected_catalog_count:1,tables:[{...row,anon_select:true}]}));
  check(run(file).status===2,'grant risk exit two');
  fs.writeFileSync(file,'bad');
  check(run(file).status===1,'invalid JSON fails');
  check(run().status===1,'missing input fails');
} finally {fs.rmSync(dir,{recursive:true,force:true});}
console.log(JSON.stringify({checks,failures:0,scope:'Read-only no-policy grants audit'}));
