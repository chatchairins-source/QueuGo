const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite'),{JSDOM}=require('jsdom');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};const db=new PGlite(),q=(s,p=[])=>db.query(s,p);
 await db.exec(require('./fixtures/credit-schema.cjs')());
 await db.exec(fs.readFileSync('supabase/migrations/20261021003910_credit_admin_rider_grant.sql','utf8'));
 const as=n=>q("select set_config('test.uid',$1,false)",[n===null?'':id(n)]);
 const grant=(request=950,user=1,amount=2000,reason='Owner approved Rider credit')=>q('select queuego_admin_grant_rider_credit($1,$2,$3,$4) id',[id(request),id(user),amount,reason]);
 await as(101);await assert.rejects(grant(),/active admin/);checks++;
 await as(null);await assert.rejects(grant(),/live session/);checks++;
 await as(170);eq((await q("select queuego_admin_rider_credit_targets('Rider One') r")).rows[0].r[0].user_id,id(1));
 const first=(await grant()).rows[0].id;eq((await grant()).rows[0].id,first);
 for(const args of [[950,2,2000],[950,1,2001],[950,1,2000,'changed reason'],[951,51,2000],[951,1,0]]){await assert.rejects(grant(...args));checks++}
 eq((await q("select promo_minor,cash_minor from qg_private.credit_accounts where user_id=$1 and kind='rider'",[id(1)])).rows[0],{promo_minor:0,cash_minor:2000});
 eq((await q("select count(*)::int n from audit_logs where action='rider_credit_grant'")).rows[0].n,1);
 await as(101);eq((await q("select queuego_credit_summary('rider') r")).rows[0].r.available_minor,2000);
 for(const f of ['queuego_admin_rider_credit_targets(text)','queuego_admin_grant_rider_credit(uuid,uuid,bigint,text)'])for(const role of ['anon','service_role'])eq((await q('select has_function_privilege($1,$2,\'execute\') v',[role,f])).rows[0].v,false);
 await db.close();
 console.log('Admin Rider ledger: '+checks+' checks PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
