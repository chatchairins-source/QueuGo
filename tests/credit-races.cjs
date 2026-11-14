const assert=require('node:assert/strict'),{Client}=require('pg');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const raw=process.env.QG_TEST_PG_URL;if(!raw)throw Error('isolated database required');const url=new URL(raw);
 if(!['127.0.0.1','localhost','[::1]'].includes(url.hostname)||url.pathname!=='/queuego_credit_gate')throw Error('refusing non-local/non-credit database');
 const clients=[];const connect=async()=>{const c=new Client({connectionString:raw,statement_timeout:10000});await c.connect();clients.push(c);return c};let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 try{
  const admin=await connect(),a=await connect(),b=await connect();if((await admin.query("select to_regclass('public.orders') t")).rows[0].t)throw Error('fresh empty database required');
  await admin.query(require('./fixtures/credit-schema.cjs')());
  await a.query("select set_config('test.uid',$1,false)",[id(101)]);await b.query("select set_config('test.uid',$1,false)",[id(101)]);
  await admin.query("insert into queuego_platform_rules(rule_key,value) values('feature.credit_rider','true')");
  await Promise.all([a,b].map(c=>c.query("select queuego_credit_begin_topup($1,'rider',10000)",[id(700)])));
  eq((await admin.query('select count(*)::int n from qg_private.credit_topups')).rows[0].n,1);
  const payment="select queuego_credit_apply_charge($1,'chrg_isolated700',10000,'thb',true,'successful',true,null,null)";
  await Promise.all([a,b].map(c=>c.query(payment,[id(700)])));
  eq((await admin.query('select cash_minor::int n from qg_private.credit_accounts')).rows[0].n,10000);
  await admin.query("insert into orders(id,order_number,status) values($1,'QT-race-a','pending'),($2,'QT-race-b','pending')",[id(701),id(702)]);
  const reserve=(c,n)=>c.query("select qg_private.credit_hold($1,'rider',$2,7000)",[id(1),typeof n==='number'?id(n):n]);
  const result=await Promise.allSettled([reserve(a,701),reserve(b,702)]);eq(result.filter(x=>x.status==='fulfilled').length,1);eq(result.filter(x=>x.status==='rejected'&&/insufficient/.test(x.reason.message)).length,1);
  const order=(await admin.query("select order_id from qg_private.credit_holds where state='held'")).rows[0].order_id;
  // Two simultaneous completion retries cannot debit twice.
  await Promise.all([a,b].map(c=>c.query("select qg_private.credit_finish($1,'rider',true)",[order])));
  eq((await admin.query('select cash_minor::int n,held_cash_minor::int h from qg_private.credit_accounts')).rows[0],{n:3000,h:0});
  // Connection death before COMMIT must roll back both account and immutable journal.
  await a.query('begin');await a.query("select qg_private.credit_hold($1,'rider',$2,2000)",[id(1),order===id(701)?id(702):id(701)]);await a.end();
  eq((await b.query('select held_cash_minor::int h from qg_private.credit_accounts')).rows[0].h,0);
  const missing=order===id(701)?id(702):id(701);await reserve(b,missing).then(()=>{throw Error('overspend permitted')},e=>eq(/insufficient/.test(e.message),true));
  await b.query("select qg_private.credit_hold($1,'rider',$2,2000)",[id(1),missing]);await b.query("select qg_private.credit_finish($1,'rider',false)",[missing]);
  eq((await b.query('select cash_minor::int n,held_cash_minor::int h from qg_private.credit_accounts')).rows[0],{n:3000,h:0});
  eq((await b.query(`select count(*)::int n from qg_private.credit_accounts a join lateral(select coalesce(sum(cash_delta),0) c,coalesce(sum(held_cash_delta),0) h from qg_private.credit_entries where user_id=a.user_id and kind=a.kind)e on true where a.cash_minor<>e.c or a.held_cash_minor<>e.h`)).rows[0].n,0);
  const c=await connect();await c.query("select set_config('test.uid',$1,false)",[id(170)]);await b.query("select set_config('test.uid',$1,false)",[id(170)]);
  const gift=(client,n=810,amount=100000)=>client.query('select queuego_admin_grant_merchant_credit($1,$2,$3,$4) id',[id(n),id(51),amount,'Owner approved GP campaign']);
  const gifts=await Promise.all([b,c].map(client=>gift(client)));eq(gifts[0].rows[0].id,gifts[1].rows[0].id);
  eq((await admin.query("select promo_minor::int n,cash_minor::int cash from qg_private.credit_accounts where kind='merchant'")).rows[0],{n:100000,cash:0});
  eq((await admin.query("select count(*)::int n from audit_logs where action='merchant_credit_grant'")).rows[0].n,1);
  const mismatch=await Promise.allSettled([gift(b,811,20000),gift(c,811,30000)]);eq(mismatch.filter(x=>x.status==='fulfilled').length,1);eq(mismatch.filter(x=>x.status==='rejected'&&/mismatch/.test(x.reason.message)).length,1);
  const prior=(await admin.query("select promo_minor from qg_private.credit_accounts where kind='merchant'")).rows[0].promo_minor;
  const dying=await connect();await dying.query("select set_config('test.uid',$1,false)",[id(170)]);await dying.query('begin');await gift(dying,812);await dying.end();
  eq((await admin.query("select promo_minor from qg_private.credit_accounts where kind='merchant'")).rows[0].promo_minor,prior);
  eq((await admin.query("select count(*)::int n from qg_private.credit_entries where event_key=$1",['admin-promo:'+id(812)])).rows[0].n,0);
  await gift(c,812);eq((await admin.query("select count(*)::int n from audit_logs where action='merchant_credit_grant'")).rows[0].n,3);
  console.log('Independent PostgreSQL credit races: '+checks+' checks PASS');
 }finally{await Promise.allSettled(clients.map(c=>c.end()))}
})().catch(e=>{console.error(e);process.exitCode=1});
