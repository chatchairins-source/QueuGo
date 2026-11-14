const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;const q=(s,p=[])=>db.query(s,p),eq=(a,b)=>{assert.deepEqual(a,b);checks++},deny=async(s,p,re)=>{await assert.rejects(q(s,p),re);checks++};
 await db.exec(require('./fixtures/credit-schema.cjs')({retire:false}));
 await q("insert into settlements values($1,$2,current_date,'processing',10,'{}')",[id(901),id(50)]);
 await q("insert into orders(id,order_number,shop_id,status,gp_amount) values($1,'QT-test-old',$2,'pending',10)",[id(902),id(50)]);
 await db.exec("alter table storage.objects enable row level security;grant usage on schema storage to authenticated;grant select,insert,update,delete on storage.objects to authenticated;create policy fixture_objects on storage.objects for all to authenticated using(true) with check(true);");
 await q("insert into storage.objects(bucket_id,name) values('gp-slips','old.jpg'),('qg-evidence','proof.jpg')");
 const before=(await q('select to_jsonb(s) s from settlements s')).rows[0].s;
 await db.exec(fs.readFileSync('supabase/migrations/20261015084536_retire_legacy_gp_collection.sql','utf8'));
 eq((await q('select to_jsonb(s) s from settlements s')).rows[0].s,before);
 eq((await q("select count(*)::int n from qg_private.credit_entries")).rows[0].n,0);
 for(const role of ['anon','authenticated','service_role']){
  for(const fn of ['report_shop_gp(date,text)','admin_review_gp(uuid,boolean,text)'])eq((await q('select has_function_privilege($1,$2,\'execute\') v',[role,fn])).rows[0].v,false);
  eq((await q('select has_table_privilege($1,\'public.settlements\',\'insert\') v',[role])).rows[0].v,false);
 }
 await deny("select report_shop_gp(current_date,'old.jpg')",[],/retired/);
 await deny('select admin_review_gp($1,true,null)',[id(901)],/retired/);
 await deny("update settlements set status='completed'",[],/retired/);
 await deny("insert into settlements values($1,$2,current_date,'pending',20,'{}')",[id(903),id(50)],/retired/);
 await deny('delete from settlements',[],/retired/);
 await q('set role authenticated');
 eq((await q("select name from storage.objects where bucket_id='gp-slips'")).rows.length,0);
 await deny("insert into storage.objects(bucket_id,name) values('gp-slips','new.jpg')",[],/row-level security/);
 eq((await q("update storage.objects set name='renamed.jpg' where bucket_id='gp-slips' returning name")).rows.length,0);
 eq((await q("delete from storage.objects where bucket_id='gp-slips' returning name")).rows.length,0);
 await q("insert into storage.objects(bucket_id,name) values('qg-evidence','new-proof.jpg')");eq((await q("select name from storage.objects where bucket_id='qg-evidence'")).rows.length,2);
 await q('reset role');
 await deny("update orders set status='preparing' where id=$1",[id(902)],/Legacy test Order/);
 await q("update orders set status='cancelled' where id=$1",[id(902)]);
 await deny("insert into orders(id,order_number,shop_id,status,gp_amount) values($1,'QT-new',$2,'pending',10)",[id(904),id(50)],/credit is not enabled/);
 await q("insert into queuego_platform_rules(rule_key,value) values('feature.credit_merchant','true')");
 await q("insert into orders(id,order_number,shop_id,status,gp_amount) values($1,'QT-credit',$2,'pending',10)",[id(905),id(50)]);
 eq((await q('select credit_gp_required v from orders where id=$1',[id(905)])).rows[0].v,true);
 eq((await q('select count(*)::int n from qg_private.credit_entries')).rows[0].n,0);
 // User-facing runtime code must have no slip/settlement mutation or duplicate collection.
 for(const file of ['merchant/index.html','admin/index.html','native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt','native-android/merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt']){
  const s=fs.readFileSync(file,'utf8');eq(/report_shop_gp|admin_review_gp|qgmSubmitGpSlip|แนบสลิปและแจ้งโอน|settlements\?on_conflict/.test(s),false);
 }
 eq(fs.existsSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantGpSlipUpload.kt'),false);
 eq(fs.existsSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantGpSlipViewer.kt'),false);
 await db.close();console.log('Legacy GP retirement: '+checks+' checks PASS');
})().catch(e=>{console.error(e.message,e.stack);process.exitCode=1});
