'use strict';
// Synthetic disposable schema for checkout validation/idempotency; not full POS/RLS/E2E.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {PGlite} = require('@electric-sql/pglite');
const {pgcrypto} = require('@electric-sql/pglite/contrib/pgcrypto');
const migration = 'supabase/migrations/20261118222500_guard_table_checkout_replay_authorization.sql';
const uuid = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');
(async () => {
 const testUrl = process.env.QG_GUEST_REPLAY_TEST_DATABASE_URL;
 let db;
 if (testUrl) {
  const url = new URL(testUrl);
  assert.ok(['postgres:','postgresql:'].includes(url.protocol) &&
   ['127.0.0.1','localhost','[::1]'].includes(url.hostname) &&
   /^\/qg_guest_replay_test_[a-z0-9_]+$/.test(url.pathname), 'disposable loopback database required');
  const {Client} = require('pg');
  const client = new Client({connectionString:testUrl,connectionTimeoutMillis:5000});
  await client.connect(); await client.query("SET statement_timeout='10s'; SET lock_timeout='5s'");
  db = {exec:sql=>client.query(sql),query:(...args)=>client.query(...args),close:()=>client.end()};
 } else db = new PGlite({extensions:{pgcrypto}});
 let checks = 0;
 const equal = (a,b) => {assert.deepEqual(a,b); checks++;};
 try {
  await db.exec(`CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role; CREATE SCHEMA extensions;
   CREATE EXTENSION pgcrypto WITH SCHEMA extensions;
   CREATE TABLE qg_table_sessions(id uuid PRIMARY KEY,device_hash text,revoked_at timestamptz,expires_at timestamptz,table_id uuid,shop_id uuid);
   CREATE TABLE pos_tables(id uuid,shop_id uuid,active boolean,label text);
   CREATE TABLE shop_profiles(id uuid,status text,latitude double precision,longitude double precision);
   CREATE TABLE products(id uuid,shop_id uuid,available boolean,pos_available boolean,name text,pos_price numeric,price numeric);
   CREATE TABLE orders(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),order_number text UNIQUE,
    shop_id uuid,order_type text,sales_channel text,table_id uuid,table_session_id uuid,status text,
    kitchen_status text,payment_status text,bill_status text,subtotal numeric,total_amount numeric,
    delivery_fee numeric,gp_rate numeric,gp_amount numeric,updated_at timestamptz);
   CREATE TABLE order_items(order_id uuid,product_id uuid,item_type text,item_name text,description text,
    quantity integer,unit_price numeric,total_price numeric,pos_kitchen_status text,pos_batch integer);
   CREATE TABLE laundry_orders(order_number text,status text);
   CREATE TABLE qg_table_requests(session_id uuid,request_id uuid,payload_hash text,order_id uuid,created_at timestamptz DEFAULT now(),PRIMARY KEY(session_id,request_id));
   REVOKE ALL ON qg_table_sessions,qg_table_requests,orders FROM PUBLIC,anon;`);
  await db.exec(fs.readFileSync('tests/backend/guest-checkout-dependencies-prod-snapshot-20261121.sql','utf8'));
  await db.query("INSERT INTO pos_tables VALUES($1,$2,true,'SYNTHETIC TABLE')",[uuid(6),uuid(7)]);
  await db.query("INSERT INTO shop_profiles VALUES($1,'active',13.75,100.5)",[uuid(7)]);
  await db.query("INSERT INTO products VALUES($1,$2,true,true,'SYNTHETIC PRODUCT',100,100)",[uuid(5),uuid(7)]);
  const source = fs.readFileSync('supabase/migrations/20261007045146_unify_pos_qr_order_codes.sql','utf8');
  const start = source.toLowerCase().indexOf('create or replace function public.qg_table_checkout(');
  assert.ok(start >= 0); const end = source.indexOf('$function$;',start);
  assert.ok(end > start);
  const captured = fs.readFileSync('tests/backend/guest-checkout-prod-snapshot-20261121.sql','utf8');
  equal(captured.split('$function$')[1].trim(),source.slice(start,end).split('$function$')[1].trim());
  await db.exec(captured);
  await db.exec(`REVOKE ALL ON FUNCTION public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision) FROM PUBLIC;
   GRANT EXECUTE ON FUNCTION public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision) TO anon,authenticated,service_role;`);
  const items = JSON.stringify([{id:uuid(5),quantity:1}]);
  const seed = async () => {
   await db.exec('TRUNCATE qg_table_requests,qg_table_sessions,orders,order_items');
   await db.query("INSERT INTO qg_table_sessions VALUES($1,encode(extensions.digest($2::text,'sha256'),'hex'),NULL,now()+interval '1 hour','00000000-0000-4000-8000-000000000006','00000000-0000-4000-8000-000000000007')",[uuid(1),uuid(2)]);
   await db.query('INSERT INTO orders(id,order_number) VALUES($1,$2)',[uuid(4),'SYNTHETIC-REPLAY']);
   await db.query("INSERT INTO qg_table_requests VALUES($1,$2,encode(extensions.digest($3::jsonb::text,'sha256'),'hex'),$4,now())",[uuid(1),uuid(3),items,uuid(4)]);
  };
  const replay = async (device=uuid(2),cart=items,request=uuid(3)) => {
   await db.exec('SET ROLE anon');
   try {
    equal((await db.query('SELECT current_user AS role')).rows[0].role,'anon');
    return (await db.query('SELECT qg_table_checkout($1,$2,$3,$4::jsonb,$5,$6) AS result',[uuid(1),device,request,cart,13.75,100.5])).rows[0].result;
   } finally {await db.exec('RESET ROLE');}
  };
  await seed(); await db.exec('UPDATE qg_table_sessions SET revoked_at=now()');
  equal((await replay()).reason,'same_request'); // Reproduce the old source bug.
  const acl = (await db.query("SELECT proacl::text AS acl FROM pg_proc WHERE oid='public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)'::regprocedure")).rows[0].acl;
  await db.exec(fs.readFileSync(migration,'utf8'));
  equal((await db.query("SELECT proacl::text AS acl FROM pg_proc WHERE oid='public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)'::regprocedure")).rows[0].acl,acl);
  await seed(); equal((await replay()).reason,'same_request');
  equal((await replay()).duplicate,true);
  for (const mutation of ['revoked_at=now()',"expires_at=now()-interval '1 second'",'expires_at=NULL']) {
   await seed(); await db.exec('UPDATE qg_table_sessions SET '+mutation);
   await assert.rejects(replay(),/หมดอายุ/); checks++;
   equal(Number((await db.query('SELECT count(*) AS n FROM qg_table_requests')).rows[0].n),1);
  }
  await seed(); await assert.rejects(replay(uuid(9)),/ไม่พบสิทธิ์/); checks++;
  await assert.rejects(replay(uuid(2),JSON.stringify([{id:uuid(5),quantity:2}])),/รหัสคำขอถูกใช้กับรายการอาหารอื่น/); checks++;
  await db.exec('UPDATE qg_table_requests SET payload_hash=NULL');
  await assert.rejects(replay(uuid(2),null),/รายการอาหารไม่ถูกต้อง/); checks++;
  await assert.rejects(replay(),/รหัสคำขอถูกใช้กับรายการอาหารอื่น/); checks++;
  await seed(); await db.exec('DELETE FROM orders');
  await assert.rejects(replay(),/ไม่พบออเดอร์เดิม/); checks++;
  await seed(); await db.exec('SET ROLE anon');
  try {
   for(const sql of ['UPDATE qg_table_sessions SET revoked_at=NULL','DELETE FROM qg_table_requests',"UPDATE qg_table_requests SET payload_hash='forged'",'TRUNCATE qg_table_requests']) {
    await assert.rejects(db.exec(sql),/permission denied/); checks++;
   }
  } finally {await db.exec('RESET ROLE');}
  equal(Number((await db.query('SELECT count(*) AS n FROM orders')).rows[0].n),1);
  await seed(); await db.exec('DELETE FROM qg_table_requests');
  const created = await replay(uuid(2),items,uuid(8));
  equal(created.duplicate,false); equal(Number(created.total),100);
  equal(created.table_name,'SYNTHETIC TABLE');
  const line = (await db.query('SELECT quantity,total_price FROM order_items WHERE order_id=$1',[created.order_id])).rows[0];
  equal(line.quantity,1); equal(Number(line.total_price),100);
  equal(Number((await db.query('SELECT total_amount FROM orders WHERE id=$1',[created.order_id])).rows[0].total_amount),100);
  equal((await replay(uuid(2),items,uuid(8))).order_id,created.order_id);
  equal(Number((await db.query('SELECT count(*) AS n FROM qg_table_requests')).rows[0].n),1);
  await assert.rejects(replay(uuid(2),JSON.stringify([{id:uuid(5),quantity:0}]),uuid(9)),/จำนวนหรือหมายเหตุไม่ถูกต้อง/); checks++;
  equal(Number((await db.query('SELECT count(*) AS n FROM orders')).rows[0].n),2);
  equal(Number((await db.query('SELECT count(*) AS n FROM order_items')).rows[0].n),1);
  equal(Number((await db.query('SELECT count(*) AS n FROM qg_table_requests')).rows[0].n),1);
  if(testUrl) {
   const {Client} = require('pg');
   const holder = new Client({connectionString:testUrl,connectionTimeoutMillis:5000});
   const waiter = new Client({connectionString:testUrl,connectionTimeoutMillis:5000});
   await holder.connect(); await waiter.connect();
   const args = [uuid(1),uuid(2),uuid(3),items,13.75,100.5];
   const query = 'SELECT qg_table_checkout($1,$2,$3,$4::jsonb,$5,$6) AS result';
   const waitingOnLock = async pid => {
    const deadline = Date.now()+5000;
    while(Date.now()<deadline) {
     const row=(await db.query('SELECT wait_event_type FROM pg_stat_activity WHERE pid=$1',[pid])).rows[0];
     if(row?.wait_event_type==='Lock') return true;
     await new Promise(resolve=>setTimeout(resolve,25));
    }
    return false;
   };
   try {
    for(const revoke of [false,true]) {
     await seed();
     await holder.query("BEGIN; SET LOCAL statement_timeout='10s'");
     await waiter.query("BEGIN; SET LOCAL statement_timeout='10s'; SET LOCAL ROLE anon");
     if(revoke) await holder.query('UPDATE qg_table_sessions SET revoked_at=now() WHERE id=$1',[uuid(1)]);
     else {
      await holder.query('SET LOCAL ROLE anon');
      equal((await holder.query(query,args)).rows[0].result.reason,'same_request');
     }
     const pid = (await waiter.query('SELECT pg_backend_pid() AS pid')).rows[0].pid;
     // Attach both handlers immediately; preserve a genuine SQL failure as data
     // until the owning transaction releases its row lock.
     const pending = waiter.query(query,args).then(value=>({value}),error=>({error}));
     equal(await waitingOnLock(pid),true);
     await holder.query('COMMIT');
     const result = await pending;
     if(revoke) {assert.match(result.error?.message??'',/หมดอายุ/);checks++;}
     else {if(result.error) throw result.error; equal(result.value.rows[0].result.reason,'same_request');}
     await waiter.query('ROLLBACK');
     equal(Number((await db.query('SELECT count(*) AS n FROM qg_table_requests')).rows[0].n),1);
    }
    await seed(); await db.exec('DELETE FROM qg_table_requests');
    const createArgs=[uuid(1),uuid(2),uuid(8),items,13.75,100.5];
    await holder.query("BEGIN; SET LOCAL statement_timeout='10s'; SET LOCAL ROLE anon");
    const first=(await holder.query(query,createArgs)).rows[0].result;
    equal(first.duplicate,false);
    await waiter.query("BEGIN; SET LOCAL statement_timeout='10s'; SET LOCAL ROLE anon");
    const pid=(await waiter.query('SELECT pg_backend_pid() AS pid')).rows[0].pid;
    const pending=waiter.query(query,createArgs).then(value=>({value}),error=>({error}));
    equal(await waitingOnLock(pid),true);
    await holder.query('COMMIT');
    const second=await pending;
    if(second.error) throw second.error;
    equal(second.value.rows[0].result.duplicate,true);
    equal(second.value.rows[0].result.order_id,first.order_id);
    await waiter.query('ROLLBACK');
    equal(Number((await db.query('SELECT count(*) AS n FROM orders')).rows[0].n),2);
    equal(Number((await db.query('SELECT count(*) AS n FROM order_items')).rows[0].n),1);
    equal(Number((await db.query('SELECT count(*) AS n FROM qg_table_requests')).rows[0].n),1);
   } finally {
    await holder.query('ROLLBACK').catch(()=>{});await waiter.query('ROLLBACK').catch(()=>{});
    await holder.end();await waiter.end();
   }
  }
  console.log(JSON.stringify({checks,failures:0,engine:testUrl?'real PostgreSQL':'PGlite',scope:'actual source/candidate replay as anon; synthetic schema, existing-replay, committed-revocation and first-checkout races on real PostgreSQL; NOT full Production triggers/RLS/settlement proof'}));
 } finally {await db.close();}
})().catch(error => {console.error(error); process.exitCode=1;});
