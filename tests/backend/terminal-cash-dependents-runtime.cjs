'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {PGlite} = require('@electric-sql/pglite');
const {Client} = require('pg');
const testUrl = process.env.QG_CASH_HELPER_TEST_DATABASE_URL;
async function database() {
 if(!testUrl) return new PGlite();
 const url=new URL(testUrl);
 assert.ok(['postgres:','postgresql:'].includes(url.protocol) &&
   ['127.0.0.1','localhost','[::1]'].includes(url.hostname) &&
   /^\/qg_cash_helper_test_[a-z0-9_]+$/.test(url.pathname), 'disposable loopback test database required');
 const client=new Client({connectionString:testUrl,connectionTimeoutMillis:5000});await client.connect();
 await client.query("SET statement_timeout='10s'; SET lock_timeout='5s'");
 return {query:(...args)=>client.query(...args),exec:sql=>client.query(sql),close:()=>client.end()};
}
async function main() {
 const db = await database(); let checks=0, next=0;
 const equal=(a,b)=>{assert.equal(a,b); checks++;};
 try {
  await db.exec(`CREATE ROLE service_role;
   CREATE TABLE orders(id integer PRIMARY KEY,status text,rider_id uuid,rider_assigned_at timestamptz,market_order_id integer,paid_at timestamptz,total_amount numeric);
   CREATE TABLE payments(order_id integer REFERENCES orders(id),status text,payment_method text,paid_at timestamptz,transaction_reference text,amount numeric,updated_at timestamptz);
   CREATE TABLE deliveries(order_id integer REFERENCES orders(id),status text,updated_at timestamptz);
   CREATE TABLE queuego_cash_order_locks(order_id integer PRIMARY KEY);
   CREATE TABLE merchant_cash_receipts(order_id integer);
   CREATE TABLE rider_cash_advances(order_id integer);
   CREATE TABLE qg_rider_cash_return_refunds(order_id integer);
   CREATE TABLE grocery_stock_reservations(order_id integer);
   CREATE TABLE pos_inventory_reservations(order_id integer);`);
  await db.exec(fs.readFileSync('tests/fixtures/cash-cancel-prod-snapshot-20261120.sql','utf8'));
  await db.exec(`REVOKE ALL ON FUNCTION public.queuego_cancel_cash_dependents() FROM PUBLIC;
    GRANT EXECUTE ON FUNCTION public.queuego_cancel_cash_dependents() TO service_role;
    CREATE TRIGGER cash_cancel AFTER UPDATE OF status ON orders FOR EACH ROW EXECUTE FUNCTION public.queuego_cancel_cash_dependents();`);
  const seed=async()=>{ const n=++next;
   await db.query("INSERT INTO orders VALUES($1,'searching_rider',NULL,NULL,NULL,NULL,100)",[n]);
   await db.query("INSERT INTO payments VALUES($1,'pending','cash',NULL,NULL,100,NULL)",[n]);
   await db.query("INSERT INTO deliveries VALUES($1,'pending',NULL)",[n]);
   await db.query('INSERT INTO queuego_cash_order_locks VALUES($1)',[n]);return n;
  };
  const status=async(n,table)=>(await db.query(`SELECT status FROM ${table} WHERE ${table==='orders'?'id':'order_id'}=$1`,[n])).rows[0].status;
  const transition=n=>db.query("UPDATE orders SET status='no_rider_available' WHERE id=$1",[n]);
  const original=await seed(); await transition(original);
  equal(await status(original,'payments'),'pending'); // Reproduce actual old helper gap.
  equal(await status(original,'deliveries'),'pending');
  const acl=(await db.query("SELECT proacl::text AS acl FROM pg_proc WHERE oid='public.queuego_cancel_cash_dependents()'::regprocedure")).rows[0].acl;
  await db.exec(fs.readFileSync('ops/p0/terminal-cash-dependents-review-only.sql','utf8'));
  equal((await db.query("SELECT proacl::text AS acl FROM pg_proc WHERE oid='public.queuego_cancel_cash_dependents()'::regprocedure")).rows[0].acl,acl);
  const definer=(await db.query("SELECT prosecdef, proconfig FROM pg_proc WHERE oid='public.queuego_cancel_cash_dependents()'::regprocedure")).rows[0];
  equal(definer.prosecdef,true);
  equal(definer.proconfig.length,1);
  async function assertEmptySearchPath(config) {
   const setting=config.find(value=>value.startsWith('search_path='));
   assert.ok(setting,'SECURITY DEFINER must explicitly set search_path');
   await db.exec('BEGIN');
   try {
    // PostgreSQL stores SET search_path TO '' as search_path="". Validate
    // effective schema resolution rather than one serialized spelling.
    await db.query("SELECT pg_catalog.set_config('search_path',$1,true)",[setting.slice('search_path='.length)]);
    const schemas=(await db.query('SELECT pg_catalog.array_to_json(pg_catalog.current_schemas(false)) AS schemas')).rows[0].schemas;
    assert.deepEqual(schemas,[],'SECURITY DEFINER must resolve no user schemas');
   } finally {await db.exec('ROLLBACK');}
  }
  await assertEmptySearchPath(definer.proconfig);checks++;
  await assert.rejects(assertEmptySearchPath(['search_path=public']),/must resolve no user schemas/);checks++;

  const clean=await seed(); await transition(clean);
  equal(await status(clean,'payments'),'cancelled');equal(await status(clean,'deliveries'),'cancelled');
  const stamp=(await db.query('SELECT updated_at::text AS stamp FROM payments WHERE order_id=$1',[clean])).rows[0].stamp;
  await transition(clean);
  equal((await db.query('SELECT updated_at::text AS stamp FROM payments WHERE order_id=$1',[clean])).rows[0].stamp,stamp);
  equal(Number((await db.query('SELECT count(*) AS n FROM queuego_cash_order_locks WHERE order_id=$1',[clean])).rows[0].n),1);
  const cancel=await seed();await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[cancel]);
  equal(await status(cancel,'payments'),'cancelled');equal(await status(cancel,'deliveries'),'cancelled');
  const unknown=await seed();await db.query('UPDATE orders SET status=NULL WHERE id=$1',[unknown]);
  equal(await status(unknown,'payments'),'pending');
  for(const mutate of [
   n=>db.query("UPDATE payments SET status='paid',paid_at=now() WHERE order_id=$1",[n]),
   n=>db.query("UPDATE payments SET status=NULL WHERE order_id=$1",[n]),
   n=>db.query("UPDATE payments SET payment_method='card' WHERE order_id=$1",[n]),
   n=>db.query("UPDATE payments SET transaction_reference='fixture-reference' WHERE order_id=$1",[n]),
   n=>db.query('UPDATE payments SET amount=101 WHERE order_id=$1',[n]),
   n=>db.query("UPDATE payments SET amount='NaN'::numeric WHERE order_id=$1",[n]),
   n=>db.query('DELETE FROM payments WHERE order_id=$1',[n]),
   n=>db.query("INSERT INTO payments VALUES($1,'pending','cash',NULL,NULL,100,NULL)",[n]),
   n=>db.query("UPDATE orders SET rider_id='00000000-0000-4000-8000-000000000005'::uuid WHERE id=$1",[n]),
   n=>db.query('UPDATE orders SET rider_assigned_at=now() WHERE id=$1',[n]),
   n=>db.query('UPDATE orders SET market_order_id=9 WHERE id=$1',[n]),
   n=>db.query('UPDATE orders SET paid_at=now() WHERE id=$1',[n]),
   n=>db.query("UPDATE deliveries SET status='delivered' WHERE order_id=$1",[n]),
   ...['merchant_cash_receipts','rider_cash_advances','qg_rider_cash_return_refunds','grocery_stock_reservations','pos_inventory_reservations'].map(table=>n=>db.query(`INSERT INTO ${table} VALUES($1)`,[n]))
  ]) {
   const n=await seed();await mutate(n);
   const before=(await db.query('SELECT * FROM payments WHERE order_id=$1',[n])).rows;
   await assert.rejects(transition(n),/financial lifecycle review/);checks++;
   equal(await status(n,'orders'),'searching_rider');
   assert.deepEqual((await db.query('SELECT * FROM payments WHERE order_id=$1',[n])).rows,before);checks++;
  }
  const historical=await seed();
  await db.query('UPDATE orders SET rider_assigned_at=now() WHERE id=$1',[historical]);
  await assert.rejects(db.query("UPDATE orders SET status='no_rider_available',rider_assigned_at=NULL WHERE id=$1",[historical]),/financial lifecycle review/);checks++;
  equal(await status(historical,'payments'),'pending');
  if(testUrl) {
   const peer=new Client({connectionString:testUrl,connectionTimeoutMillis:5000});await peer.connect();
   try {
    const concurrent=await seed();
    await peer.query('BEGIN');
    await peer.query('SELECT 1 FROM payments WHERE order_id=$1 FOR UPDATE',[concurrent]);
    const mainPid=(await db.query('SELECT pg_backend_pid() AS pid')).rows[0].pid;
    const peerPid=(await peer.query('SELECT pg_backend_pid() AS pid')).rows[0].pid;
    const flight=transition(concurrent).then(value=>({value}),error=>({error}));
    let blocked=false;
    for(let attempt=0;attempt<100;attempt++) {
     const blockers=(await peer.query('SELECT pg_blocking_pids($1) AS pids',[mainPid])).rows[0].pids;
     if(blockers.includes(peerPid)){blocked=true;break;}
     await new Promise(resolve=>setTimeout(resolve,10));
    }
    assert.ok(blocked,'terminal helper must wait on concurrent payment writer');checks++;
    await peer.query("UPDATE payments SET status='paid',paid_at=now() WHERE order_id=$1",[concurrent]);
    await peer.query('COMMIT');
    const outcome=await flight;
    assert.match(String(outcome.error),/financial lifecycle review/);checks++;
    equal(await status(concurrent,'orders'),'searching_rider');
    equal(await status(concurrent,'payments'),'paid');
    equal(await status(concurrent,'deliveries'),'pending');
    // Match Production's immediate order FK. An in-flight second ledger insert
    // must finish before the helper validates the one-ledger invariant.
    const phantom=await seed();await peer.query('BEGIN');
    await peer.query("INSERT INTO payments VALUES($1,'pending','cash',NULL,NULL,100,NULL)",[phantom]);
    const inserting=transition(phantom).then(value=>({value}),error=>({error}));
    let insertBlocked=false;
    for(let attempt=0;attempt<100;attempt++) {
     const blockers=(await peer.query('SELECT pg_blocking_pids($1) AS pids',[mainPid])).rows[0].pids;
     if(blockers.includes(peerPid)){insertBlocked=true;break;}
     await new Promise(resolve=>setTimeout(resolve,10));
    }
    assert.ok(insertBlocked,'parent FOR UPDATE must wait for in-flight ledger FK writer');checks++;
    await peer.query('COMMIT');
    assert.match(String((await inserting).error),/financial lifecycle review/);checks++;
    equal(await status(phantom,'orders'),'searching_rider');
    equal(Number((await db.query('SELECT count(*) AS n FROM payments WHERE order_id=$1',[phantom])).rows[0].n),2);

   } finally {await peer.query('ROLLBACK');await peer.end();}
  }
  // Simulate downstream failure: status and cash cleanup roll back together.
  await db.exec(`CREATE FUNCTION fail_terminal() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='no_rider_available' THEN RAISE EXCEPTION 'fixture downstream failure'; END IF; RETURN NEW; END $$;
    CREATE TRIGGER zzz_failure AFTER UPDATE OF status ON orders FOR EACH ROW EXECUTE FUNCTION fail_terminal();`);
  const atomic=await seed();await assert.rejects(transition(atomic),/fixture downstream failure/);checks++;
  equal(await status(atomic,'orders'),'searching_rider');equal(await status(atomic,'payments'),'pending');equal(await status(atomic,'deliveries'),'pending');
  // Add the actual current Production BEFORE guard, rather than certifying the
  // helper on a schema where every caller can transition orders directly.
  await db.exec('DROP TRIGGER zzz_failure ON orders;');
  const actualGuard=fs.readFileSync('tests/fixtures/cash-order-guard-prod-snapshot-20261121.sql','utf8');
  const existing=new Set((await db.query("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='orders'")).rows.map(row=>row.column_name));
  const guardFields=[...new Set([...actualGuard.matchAll(/\b(?:old|new)\.([a-z_][a-z0-9_]*)/gi)].map(match=>match[1].toLowerCase()))];
  const missing=guardFields.filter(name=>!existing.has(name));
  if(missing.length) await db.exec('ALTER TABLE public.orders '+missing.map(name=>'ADD COLUMN '+name+' text').join(','));
  // Actor lookup is an explicit fixture; this does not certify live JWT/RLS.
  await db.exec(`CREATE FUNCTION public.get_my_role() RETURNS text LANGUAGE sql AS $$ SELECT nullif(current_setting('qg.test_actor_role',true),'') $$;
    CREATE FUNCTION public.get_my_user_id() RETURNS uuid LANGUAGE sql AS $$ SELECT NULL::uuid $$;
    CREATE TABLE public.rider_profiles(id uuid,user_id uuid);`);
  await db.exec(actualGuard);
  await db.exec('CREATE TRIGGER zz_queuego_cash_order_guard BEFORE UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION public.queuego_guard_cash_order();');
  for(const actor of ['', 'customer', 'shop', 'rider', 'service_role']) {
   await db.query("SELECT set_config('qg.test_actor_role',$1,false)",[actor]);
   const n=await seed();
   await assert.rejects(transition(n),/forbidden|cannot change|invalid .*transition/);checks++;
   equal(await status(n,'orders'),'searching_rider');
   equal(await status(n,'payments'),'pending');equal(await status(n,'deliveries'),'pending');
  }
  await db.query("SELECT set_config('qg.test_actor_role','customer',false)");
  const legacyCancel=await seed();await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[legacyCancel]);
  equal(await status(legacyCancel,'payments'),'cancelled');
  await db.query("SELECT set_config('qg.test_actor_role','admin',false)");
  const adminCase=await seed();await transition(adminCase);
  equal(await status(adminCase,'orders'),'no_rider_available');equal(await status(adminCase,'payments'),'cancelled');
  const captured=await seed();await db.query("UPDATE payments SET status='paid',paid_at=now() WHERE order_id=$1",[captured]);
  await assert.rejects(transition(captured),/financial lifecycle review/);checks++;
  equal(await status(captured,'orders'),'searching_rider');equal(await status(captured,'payments'),'paid');

 } finally {await db.close();}
 console.log(JSON.stringify({checks,failures:0,engine:testUrl?'real PostgreSQL':'PGlite',scope:'review-only cash dependent helper, actual captured baseline + disposable synthetic rows; NOT timeout scheduler or Production certification'}));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
