'use strict';
const fs=require('fs'),path=require('path'),assert=require('assert'),{PGlite}=require('@electric-sql/pglite');
(async()=>{
  let checks=0;
  const db=new PGlite(),eq=(a,b)=>{assert.deepStrictEqual(a,b);checks++};
  await db.exec(`
    create role anon;create role authenticated;create role service_role;
    create table shop_profiles(id uuid primary key,status text,delivery_enabled boolean,market_id uuid,market_membership_status text);
    create table markets(id uuid primary key,active boolean);
    create table market_orders(id uuid primary key,market_id uuid);
    create table shop_open_states(shop_id uuid,is_open boolean,resume_at timestamptz);
    create table orders(id uuid primary key,shop_id uuid,sales_channel text,market_order_id uuid,status text);
    insert into markets values('10000000-0000-4000-8000-000000000001',true),('10000000-0000-4000-8000-000000000002',false);
    insert into shop_profiles values('20000000-0000-4000-8000-000000000001','active',true,'10000000-0000-4000-8000-000000000001','approved');
    insert into market_orders values('30000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001'),('30000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000002');
  `);
  await db.exec(fs.readFileSync(path.join(__dirname,'../supabase/migrations/20261014132148_customer_delivery_validation_guard_v1.sql'),'utf8'));
  let n=0;
  const shop='20000000-0000-4000-8000-000000000001';
  const place=async(channel='QUEUEGO_DELIVERY',market=null)=>db.query('insert into orders values($1,$2,$3,$4,$5)',[
    '40000000-0000-4000-8000-'+String(++n).padStart(12,'0'),shop,channel,market,'pending']);
  const blocked=async(re)=>{const before=(await db.query('select count(*)::int as n from orders')).rows[0].n;await assert.rejects(place(),re);checks++;eq((await db.query('select count(*)::int as n from orders')).rows[0].n,before)};
  await place();await place('QUEUEGO_DELIVERY','30000000-0000-4000-8000-000000000001');
  await db.exec("update shop_profiles set market_id=null");await blocked(/membership/);
  await place('POS');
  await db.exec("update shop_profiles set market_id='10000000-0000-4000-8000-000000000001',market_membership_status='pending'");await blocked(/membership/);
  await db.exec("update shop_profiles set market_membership_status='approved',delivery_enabled=false");await blocked(/unavailable/);
  await db.exec("update shop_profiles set delivery_enabled=true,status='suspended'");await blocked(/unavailable/);
  await db.exec("update shop_profiles set status='active';update markets set active=false");await blocked(/market unavailable/);
  await db.exec("update markets set active=true where id='10000000-0000-4000-8000-000000000001'");
  await assert.rejects(place('QUEUEGO_DELIVERY','30000000-0000-4000-8000-000000000002'),/does not match/);checks++;
  await db.exec("insert into shop_open_states values('20000000-0000-4000-8000-000000000001',false,null)");await blocked(/shop closed/);
  await db.exec("update shop_open_states set resume_at=now()+interval '1 hour'");await blocked(/shop closed/);
  // Closing a shop never blocks lifecycle updates on an already committed job.
  await db.exec("update orders set status='completed' where sales_channel='QUEUEGO_DELIVERY'");
  eq((await db.query("select count(*)::int n from orders where status='completed'")).rows[0].n,2);
  // A POS row cannot be changed into delivery after losing eligibility.
  await assert.rejects(db.exec("update orders set sales_channel='QUEUEGO_DELIVERY' where sales_channel='POS'"),/shop closed/);checks++;
  await db.exec("update shop_open_states set resume_at=now()-interval '1 hour'");await place();
  for(const role of ['anon','authenticated']){
    eq((await db.query("select has_function_privilege($1,'qg_market_membership_order_guard()','EXECUTE') as allowed",[role])).rows[0].allowed,false);
  }
  await db.close();
  console.log(JSON.stringify({checks,failures:0,scope:'isolated transactional delivery guard: normal + Market + POS + lifecycle + ACL; no Production records created'}));
})().catch(e=>{console.error(e);process.exit(1)});
