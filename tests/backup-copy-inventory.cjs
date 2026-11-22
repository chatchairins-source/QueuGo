'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'..');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'queuego-copy-inventory-'));
const tables=['auth.users','public.users','public.shop_profiles','public.rider_profiles',
 'public.orders','public.order_items','public.deliveries','public.notifications',
 'public.products','public.markets','public.market_orders','public.laundry_orders',
 'public.qg_pickup_proofs','public.qg_delivery_proofs','public.qg_ugc_terms_acceptances',
 'public.qg_user_blocks','public.qg_ugc_reports','storage.buckets','storage.objects',
 'public.payments','public.pos_register_cash_events','public.pos_register_shifts','public.merchant_cash_receipts','public.qg_rider_cash_return_refunds','public.queuego_cash_order_locks','public.rider_cash_advances','public.grocery_stock_reservations','public.market_stock_movements','public.qg_rider_offer_history','public.qg_rider_order_offers','qg_private.credit_accounts','qg_private.credit_entries','qg_private.credit_holds','qg_private.credit_topups'];
let checks=0;
const section=(name,rows=[],quoted=false)=>'COPY '+(quoted?name.split('.').map(v=>'"'+v+'"').join('.'):name)+
 ' (id, description) FROM stdin;\n'+rows.map(v=>v+'\n').join('')+'\\.\n';
const dump=(orders=['one\tline\\nwith\\ttab','two\t\\\\.'],quoted=false)=>
 '-- pg_dump text COPY fixture; no Production rows\nSET row_security = off;\n'+
 tables.map(t=>section(t,t==='public.orders'?orders:[],quoted)).join('')+
 section('public.unrelated',['COPY public.orders (id) FROM stdin;','\\N\tunicode-บุรีรัมย์']);
function run(sql){
 const file=path.join(dir,'data.sql');fs.writeFileSync(file,sql);
 const r=spawnSync(process.execPath,[path.join(repo,'ops/inventory-from-copy-dump.mjs'),file],{encoding:'utf8',timeout:5000});
 if(r.error)throw r.error;return r;
}
try{
 for(const [sql,count] of [[dump(),2],[dump([],true),0],[dump(['three']).replaceAll('\n','\r\n'),1]]){
  const r=run(sql);assert.equal(r.status,0,r.stderr);const result=JSON.parse(r.stdout);
  assert.equal(result['public.orders'],count);assert.equal(result['auth.users'],0);
  assert.equal(result.inventory_basis,'pg_dump_text_copy_v1');checks+=4;
 }
 for(const [sql,reason] of [
  [dump().replace(section('auth.users'),''),'missing required'],
  [dump()+section('public.orders'),'Duplicate'],
  [dump()+'COPY public.tail (id) FROM stdin;\nunfinished','Unterminated'],
  [dump().replace('COPY auth.users (id, description) FROM stdin;', 'COPY auth.users FROM PROGRAM unsafe;'),'Unsupported'],
  ['', 'missing required'],
 ]){
  const r=run(sql);assert.notEqual(r.status,0);assert.match(r.stderr,new RegExp(reason));checks+=2;
  assert(!r.stdout.includes('unfinished'),'Never print backup row data');checks++;
 }
 // A moving live inventory is deliberately unrelated: restore the actual dump.
 const source=JSON.parse(run(dump(['snapshot-row'])).stdout);
 const restored=Object.fromEntries(tables.map(t=>[t,t==='public.orders'?1:0]));
 fs.writeFileSync(path.join(dir,'source.json'),JSON.stringify(source));
 fs.writeFileSync(path.join(dir,'restored.json'),JSON.stringify(restored));
 const args=[path.join(repo,'ops/compare-inventory.mjs'),path.join(dir,'source.json'),path.join(dir,'restored.json')];
 assert.equal(spawnSync(process.execPath,args,{encoding:'utf8',timeout:5000}).status,0);checks++;
 restored['public.orders']=0;fs.writeFileSync(path.join(dir,'restored.json'),JSON.stringify(restored));
 assert.notEqual(spawnSync(process.execPath,args,{encoding:'utf8',timeout:5000}).status,0);checks++;
 for(const target of ['', 'postgresql://production.invalid/postgres', 'not-a-container']){
  const r=spawnSync(process.execPath,[path.join(repo,'tests/backend/backup-copy-postgres.cjs')],{
   encoding:'utf8',timeout:5000,env:{...process.env,QG_BACKUP_TEST_CONTAINER:target,PATH:''}});
  assert.notEqual(r.status,0);assert.match(r.stderr,/Only an isolated PostgreSQL service container id/);checks+=2;
 }
 console.log(JSON.stringify({checks,failures:0,scope:'actual text-COPY inventory CLI counts escaped/quoted/empty snapshots and rejects incomplete dumps or one-row restore loss; no live backup certification'}));
}finally{fs.rmSync(dir,{recursive:true,force:true});}
