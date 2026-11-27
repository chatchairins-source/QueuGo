'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {PGlite}=require('@electric-sql/pglite');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
async function main(){
 const db=new PGlite();let checks=0;
 try{
  const audit=read('scripts/sql/audit-anon-security-definer-exposure.sql');
  for(const pattern of ["'\\mexecute\\M'","'\\mset_config\\s*\\('","'\\mrow_security\\M'"]){
   assert(audit.includes(pattern),'inventory must use the tested single-backslash SQL regex');checks++;
  }
  assert(!/^[ \t]*(create|alter|drop|grant|revoke|insert|update|delete|truncate|do)\b/im.test(audit),'inventory must remain read-only');checks++;
  await db.exec('SET standard_conforming_strings=on; CREATE ROLE anon; CREATE ROLE authenticated;');
  const cases=(await db.query(read('scripts/sql/test-anon-security-definer-regex.sql'))).rows;
  assert.equal(cases.length,6);checks++;
  for(const row of cases){assert.equal(row.passed,true,row.label+' must match actual PostgreSQL regex semantics');checks++;}
  // Inventory scans real catalog definitions but never invokes these functions.
  // Their textual markers are synthetic scanner inputs, not exploit evidence.
  await db.exec(`
   CREATE FUNCTION public.audit_fixture_dynamic() RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
    BEGIN EXECUTE 'SELECT 1'; END $$;
   CREATE FUNCTION public.audit_fixture_config() RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
    BEGIN PERFORM set_config('row_security','off',true); END $$;
   CREATE FUNCTION public.audit_fixture_identifiers() RETURNS text LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT 'execute_helper set_configured row_security_enabled'::text $$;`);
  const results=await db.exec(audit);
  assert.equal(results.length,4,'all four read-only inventory statements must execute');checks++;
  const rows=results[0].rows;
  assert.equal(rows.length,3);checks++;
  for(const [name,expected] of [
   ['audit_fixture_dynamic',[true,false,false]],
   ['audit_fixture_config',[false,true,true]],
   ['audit_fixture_identifiers',[false,false,false]]
  ]){
   const row=rows.find(x=>x.signature==='public.'+name+'()');
   assert(row,'actual catalog function must be inventoried: '+name);checks++;
   assert.deepEqual([row.contains_execute_keyword,row.contains_set_config_call,row.references_row_security],expected);checks++;
   assert.equal(row.anon_effective_execute,true);checks++;
  }
  console.log(JSON.stringify({checks,failures:0,scope:'read-only SECURITY DEFINER inventory and actual PostgreSQL regex/catalog scanning in isolated PGlite; NOT runtime authorization or exploit certification'}));
 }finally{await db.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
