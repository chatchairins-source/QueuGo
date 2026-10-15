const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite'),{JSDOM}=require('jsdom');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const db=new PGlite(),q=(s,p=[])=>db.query(s,p),as=n=>q("select set_config('test.uid',$1,false)",[n===null?null:id(n)]);
 const deny=async(s,p,re)=>{await assert.rejects(q(s,p),re);checks++};
 await db.exec(require('./fixtures/credit-schema.cjs')());
 const grant=(request=800,amount=100000,user=51,reason='Owner approved free GP credit')=>q('select queuego_admin_grant_merchant_credit($1,$2,$3,$4) id',[id(request),id(user),amount,reason]);
 await as(151);await deny("select queuego_admin_merchant_credit_targets('')",[],/active admin/);await assert.rejects(grant(),/active admin/);checks++;
 await as(null);await assert.rejects(grant(),/live session/);checks++;
 await as(170);eq((await q("select queuego_admin_merchant_credit_targets('isolated') r")).rows[0].r[0].user_id,id(51));
 eq((await q('select count(*)::int n from qg_private.credit_accounts')).rows[0].n,0);
 await deny('select queuego_admin_merchant_credit_targets($1)',['x'.repeat(101)],/search/);
 for(const amount of [null,0,-1,10000001]){await assert.rejects(grant(800,amount),/amount/);checks++}
 await assert.rejects(grant(800,10000,51,'bad'),/reason/);checks++;
 await assert.rejects(grant(800,10000,1),/merchant/);checks++;
 await q("update shop_profiles set archived_at=now() where id=$1",[id(50)]);await assert.rejects(grant(),/unarchived/);checks++;
 eq((await q("select queuego_admin_merchant_credit_targets('') r")).rows[0].r.length,0);
 await q('update shop_profiles set archived_at=null where id=$1',[id(50)]);
 const first=(await grant()).rows[0].id;eq((await grant()).rows[0].id,first);
 await assert.rejects(grant(800,100001),/mismatch/);checks++;
 await assert.rejects(grant(800,100000,51,'Different reason'),/mismatch/);checks++;
 const balance=async()=>(await q("select * from qg_private.credit_accounts where user_id=$1 and kind='merchant'",[id(51)])).rows[0];
 eq((await balance()).promo_minor,100000);eq((await balance()).cash_minor,0);
 eq((await q("select count(*)::int n from audit_logs where action='merchant_credit_grant'")).rows[0].n,1);
 eq((await q("select actor_id,reason from qg_private.credit_entries where id=$1",[first])).rows[0],{actor_id:id(70),reason:'Owner approved free GP credit'});
 await q("insert into orders(id,order_number,status) values($1,'QT-promo','pending')",[id(801)]);
 await q("select qg_private.credit_hold($1,'merchant',$2,25000)",[id(51),id(801)]);
 await q("select qg_private.credit_finish($1,'merchant',true)",[id(801)]);eq((await balance()).promo_minor,75000);eq((await balance()).cash_minor,0);
 await q("update users set status='suspended' where id=$1",[id(70)]);await assert.rejects(grant(802),/active admin/);checks++;
 await q("update users set status='active' where id=$1",[id(70)]);
 for(const fn of ['queuego_admin_grant_merchant_credit(uuid,uuid,bigint,text)','queuego_admin_merchant_credit_targets(text)'])eq((await q('select has_function_privilege(\'anon\',$1,\'execute\') v',[fn])).rows[0].v,false);
 await db.close();
 // Real browser event handlers: persist before RPC, freeze ambiguous grant, retry exact UUID across reload.
 const dom=new JSDOM('<div id="qg-credit-admin"></div>',{url:'https://queuego.invalid',runScripts:'outside-only'}),w=dom.window;let requests=[],lost=true,blockSave=false,blocked=0;
 w.currentUser=()=>({id:id(70),type:'admin'});w.toast=()=>{};
 w.qtSupabaseRpc=async(name,p)=>{
  if(name==='queuego_admin_credit_accounts')return [];
  if(name==='queuego_admin_merchant_credit_targets')return [{user_id:id(51),name:'Shop Owner',shops:'<img src=x onerror=alert(1)>',promo_minor:0}];
  requests.push(p);eq(JSON.parse(w.localStorage.getItem('qg-admin-merchant-promo-v1:'+id(70))).request,p.p_request);
  if(lost)throw Error('reply lost after server commit');return id(900);
 };
 w.eval(fs.readFileSync('admin/credit.js','utf8'));await w.qgAdminLoadCredit();await new Promise(r=>setTimeout(r,0));
 const form=()=>w.document.querySelectorAll('form')[1];let f=form();eq(w.document.querySelector('img'),null);
 f.querySelector('select').value=id(51);f.querySelector('input').value='1000.05';f.querySelector('textarea').value='Owner free GP campaign';
 f.dispatchEvent(new w.Event('submit',{cancelable:true}));await new Promise(r=>setTimeout(r,5));
 eq(requests.length,1);eq(requests[0].p_amount_minor,100005);eq(f.querySelector('input').disabled,true);
 await w.qgAdminLoadCredit();f=form();eq(f.querySelector('input').value,'1000.05');eq(f.querySelector('select').disabled,true);
 lost=false;f.dispatchEvent(new w.Event('submit',{cancelable:true}));await new Promise(r=>setTimeout(r,5));
 eq(requests.length,2);eq(requests[1],requests[0]);eq(w.localStorage.getItem('qg-admin-merchant-promo-v1:'+id(70)),null);
 // Storage unavailable: refuse the mutation because safe retry would be impossible.
 const realSet=w.Storage.prototype.setItem;w.Storage.prototype.setItem=function(){throw Error('storage unavailable')};f=form();await new Promise(r=>setTimeout(r,0));
 f.querySelector('select').value=id(51);f.querySelector('input').value='100';f.querySelector('textarea').value='Owner grant again';
 f.dispatchEvent(new w.Event('submit',{cancelable:true}));await new Promise(r=>setTimeout(r,5));eq(requests.length,2);w.Storage.prototype.setItem=realSet;
 dom.window.close();console.log('Admin Merchant free credit: '+checks+' checks PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
