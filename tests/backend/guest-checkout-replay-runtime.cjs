'use strict';
// Synthetic disposable schema for replay authorization only, not full POS/RLS/E2E.
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
  await db.exec(`CREATE ROLE anon; CREATE SCHEMA extensions;
   CREATE EXTENSION pgcrypto WITH SCHEMA extensions;
   CREATE TABLE qg_table_sessions(id uuid PRIMARY KEY,device_hash text,revoked_at timestamptz,expires_at timestamptz,table_id uuid,shop_id uuid);
   CREATE TABLE pos_tables(id uuid,shop_id uuid,active boolean,label text);
   CREATE TABLE shop_profiles(id uuid,status text,latitude double precision,longitude double precision);
   CREATE TABLE products(id uuid,shop_id uuid,available boolean,pos_available boolean,name text,pos_price numeric,price numeric);
   CREATE TABLE orders(id uuid PRIMARY KEY,order_number text);
   CREATE TABLE qg_table_requests(session_id uuid,request_id uuid,payload_hash text,order_id uuid,created_at timestamptz DEFAULT now(),PRIMARY KEY(session_id,request_id));
   REVOKE ALL ON qg_table_sessions,qg_table_requests,orders FROM PUBLIC,anon;`);
  const source = fs.readFileSync('supabase/migrations/20261007045146_unify_pos_qr_order_codes.sql','utf8');
  const start = source.toLowerCase().indexOf('create or replace function public.qg_table_checkout(');
  assert.ok(start >= 0); const end = source.indexOf('$function$;',start);
  assert.ok(end > start);
  await db.exec(source.slice(start,end+'$function$;'.length));
  await db.exec(`REVOKE ALL ON FUNCTION public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision) FROM PUBLIC;
   GRANT EXECUTE ON FUNCTION public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision) TO anon;`);
  const items = JSON.stringify([{id:uuid(5),quantity:1}]);
  const seed = async () => {
   await db.exec('TRUNCATE qg_table_requests,qg_table_sessions,orders');
   await db.query("INSERT INTO qg_table_sessions VALUES($1,encode(extensions.digest($2::text,'sha256'),'hex'),NULL,now()+interval '1 hour',NULL,NULL)",[uuid(1),uuid(2)]);
   await db.query('INSERT INTO orders VALUES($1,$2)',[uuid(4),'SYNTHETIC-REPLAY']);
   await db.query("INSERT INTO qg_table_requests VALUES($1,$2,encode(extensions.digest($3::jsonb::text,'sha256'),'hex'),$4,now())",[uuid(1),uuid(3),items,uuid(4)]);
  };
  const replay = async (device=uuid(2),cart=items) => {
   await db.exec('SET ROLE anon');
   try {
    equal((await db.query('SELECT current_user AS role')).rows[0].role,'anon');
    return (await db.query('SELECT qg_table_checkout($1,$2,$3,$4::jsonb,$5,$6) AS result',[uuid(1),device,uuid(3),cart,13.75,100.5])).rows[0].result;
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
  console.log(JSON.stringify({checks,failures:0,engine:testUrl?'real PostgreSQL':'PGlite',scope:'actual source/candidate replay as anon; synthetic schema, NOT Production parity, concurrent new checkout or settlement proof'}));
 } finally {await db.close();}
})().catch(error => {console.error(error); process.exitCode=1;});
