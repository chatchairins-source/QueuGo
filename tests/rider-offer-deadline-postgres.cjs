'use strict';
// Dedicated, empty loopback PostgreSQL only. Never point this at Supabase.
// This proves concurrent dispatcher/row-lock behavior, not financial timeout
// terminalization, customer consent or live deployment.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {Client} = require('pg');
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');

async function main() {
  const url = new URL(process.env.QG_DISPATCH_TEST_DATABASE_URL || 'invalid:');
  assert.ok(['postgres:', 'postgresql:'].includes(url.protocol), 'dedicated PostgreSQL test URL required');
  assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname), 'refuse non-loopback database');
  assert.match(url.pathname, /^\/qg_dispatch_test_[a-z0-9_]+$/, 'refuse existing application database names');
  const clients = [];
  let checks = 0;
  const connect = async () => {
    const db = new Client({connectionString: url.toString(), connectionTimeoutMillis: 5000});
    await db.connect(); clients.push(db);
    await db.query("SET statement_timeout='8s'; SET lock_timeout='3s'");
    return db;
  };
  const eq = (actual, expected, message) => {assert.equal(actual, expected, message); checks++;};
  try {
    const db = await connect();
    const occupied = await db.query("SELECT count(*)::int AS n FROM pg_tables WHERE schemaname='public'");
    eq(occupied.rows[0].n, 0, 'fixture requires an empty disposable database');
    const version = (await db.query('SHOW server_version')).rows[0].server_version;
    assert.match(version, /^17\./, 'use the same PostgreSQL major as Production'); checks++;
    await db.query(require('./fixtures/rider-dispatch-schema.cjs')());
    await db.query(`ALTER TABLE orders ADD COLUMN rider_search_started_at timestamptz;
      CREATE TABLE system_settings(key text PRIMARY KEY,value jsonb NOT NULL);
      INSERT INTO system_settings VALUES('app','{"orderTimeout":30}');
      CREATE FUNCTION qg_private.rider_work_allows(uuid,uuid) RETURNS boolean LANGUAGE sql AS $$SELECT true$$;
      CREATE FUNCTION qg_private.ride_owner_authorized(uuid) RETURNS boolean LANGUAGE sql AS $$SELECT false$$;`);
    const migration = fs.readFileSync('supabase/migrations/20261120043000_rider_offer_deadline_guard_p0.sql', 'utf8');
    await db.query(migration);
    await db.query(fs.readFileSync('tests/fixtures/rider-dispatch-prod-snapshot-20261119.sql', 'utf8'));
    // The captured body omits ACLs. Match the separately read live dispatcher
    // ACL: only postgres and service_role, never anon/authenticated.
    await db.query(`REVOKE ALL ON FUNCTION public.qg_dispatch_rider_offers() FROM PUBLIC,anon,authenticated;
      GRANT EXECUTE ON FUNCTION public.qg_dispatch_rider_offers() TO service_role;`);
    await db.query("UPDATE rider_profiles SET metadata='{\"online\":false,\"available\":false}'::jsonb WHERE id=$1", [id(12)]);
    const seed = async (n, age='1 minute') => db.query(`INSERT INTO orders(
      id,order_number,customer_id,shop_id,status,order_type,fulfillment_vertical,
      pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,
      created_at,rider_search_started_at)
      VALUES($1,$2,$3,$4,'searching_rider','shopping','food',15.001,103.001,15.003,103.005,
        now()-$5::interval,now()-$5::interval)`, [id(n),'ISOLATED-'+n,id(60),id(50),age]);
    const dispatch = async client => Number((await client.query('SELECT public.qg_dispatch_rider_offers() AS n')).rows[0].n);
    const count = async (table, n) => Number((await db.query(
      'SELECT count(*)::int AS n FROM '+table+' WHERE '+(table==='notifications'?'reference_id':'order_id')+'=$1', [id(n)])).rows[0].n);
    const a = await connect(), b = await connect();

    await seed(901, '55 hours');
    eq((await Promise.all([dispatch(a),dispatch(b)])).reduce((x,y)=>x+y,0), 0, 'simultaneous cron calls suppress overdue order');
    eq(await count('notifications',901), 0, 'overdue race sends no notification');
    eq(await count('qg_rider_offer_history',901), 0, 'suppressed offer does not create fake history');

    await seed(902);
    eq((await Promise.all([dispatch(a),dispatch(b)])).reduce((x,y)=>x+y,0), 1, 'parallel cron creates exactly one valid offer');
    eq(await count('qg_rider_order_offers',902), 1, 'one live offer across real sessions');
    eq(await count('notifications',902), 1, 'exactly one notification across real sessions');
    eq((await db.query('SELECT expires_at<=offered_at+interval \'30 seconds\' AS bounded FROM qg_rider_order_offers WHERE order_id=$1',[id(902)])).rows[0].bounded, true, 'server offer TTL stays bounded');
    await db.query('DELETE FROM qg_rider_order_offers WHERE order_id=$1',[id(902)]);
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(902)]);

    await seed(903);
    await a.query('BEGIN');
    await a.query('SELECT id FROM orders WHERE id=$1 FOR UPDATE',[id(903)]);
    await a.query("UPDATE orders SET rider_id=$2,status='rider_assigned' WHERE id=$1",[id(903),id(12)]);
    eq(await dispatch(b), 0, 'dispatcher skips order locked by concurrent claim transition');
    await a.query('COMMIT');
    eq(await dispatch(b), 0, 'claimed order never reappears after claim commits');
    eq(await count('qg_rider_order_offers',903), 0, 'no offer behind committed claim');
    eq(await count('notifications',903), 0, 'no notification behind committed claim');

    await seed(904);
    await a.query('BEGIN');
    await a.query("UPDATE orders SET status='no_rider_available' WHERE id=$1",[id(904)]);
    eq(await dispatch(b), 0, 'dispatcher skips concurrent terminal transition lock');
    await a.query('COMMIT');
    for (let n=0;n<3;n++) eq(await dispatch(b),0,'cron cannot resurrect terminal order');
    eq(await count('notifications',904),0,'terminal race has no notification');

    await seed(905);
    await a.query('BEGIN');
    await a.query("UPDATE orders SET status='no_rider_available' WHERE id=$1",[id(905)]);
    eq(await dispatch(b),0,'uncommitted terminal lock is skipped');
    await a.query('ROLLBACK');
    eq(await dispatch(b),1,'rolled-back transition preserves eligible real order');
    await db.query('DELETE FROM qg_rider_order_offers WHERE order_id=$1',[id(905)]);
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(905)]);

    await seed(906);
    await db.query(`INSERT INTO qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
      SELECT $1,$2,now()-interval '2 minutes',now()-interval '2 minutes','expired',generate_series(1,120)`,[id(906),id(11)]);
    eq((await Promise.all([dispatch(a),dispatch(b)])).reduce((x,y)=>x+y,0),0,'parallel cron never creates attempt 121');
    eq(await count('notifications',906),0,'attempt cap sends no notification');
    eq(await count('qg_rider_offer_history',906),120,'attempt cap preserves finite history');

    for (const role of ['anon','authenticated']) {
      await a.query('SET ROLE '+role);
      await assert.rejects(dispatch(a),e=>e.code==='42501',role+' cannot invoke dispatcher'); checks++;
      await assert.rejects(a.query(`INSERT INTO public.qg_rider_order_offers(order_id,rider_id,attempt,offered_at,expires_at)
        VALUES($1,$2,1,now(),now()+interval '30 seconds')`,[id(906),id(11)]),e=>e.code==='42501',role+' cannot insert targeted offer'); checks++;
      await a.query('RESET ROLE');
    }
    await seed(907);
    await a.query('SET ROLE service_role');
    eq(await dispatch(a),1,'service-role dispatcher can execute INVOKER trigger under its existing definer context');
    await a.query('RESET ROLE');
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(907)]);
    await db.query('DELETE FROM qg_rider_order_offers WHERE order_id=$1',[id(907)]);

    await seed(908);
    const futureOffer = await db.query(`INSERT INTO qg_rider_order_offers(order_id,rider_id,attempt,offered_at,expires_at)
      VALUES($1,$2,1,now()+interval '10 minutes',now()+interval '11 minutes') RETURNING order_id`,[id(908),id(11)]);
    eq(futureOffer.rows.length,0,'future caller timestamp cannot extend the server offer lease');
    eq(await count('qg_rider_order_offers',908),0,'future timestamp creates no actionable offer');
    eq(await dispatch(a),1,'ordinary server-timestamp dispatch still works after rejection');
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(908)]);
    await db.query('DELETE FROM qg_rider_order_offers WHERE order_id=$1',[id(908)]);
    await seed(909,'-10 minutes');
    eq(await dispatch(a),0,'future rider search start fails closed');
    eq(await count('notifications',909),0,'future search clock cannot trigger targeted notifications');
    await db.query("UPDATE orders SET rider_search_started_at=now()-interval '1 minute',created_at=now()-interval '1 minute' WHERE id=$1",[id(909)]);
    eq(await dispatch(a),1,'repairing the synthetic clock restores normal dispatch');
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(909)]);
    await db.query('DELETE FROM qg_rider_order_offers WHERE order_id=$1',[id(909)]);

    // The real dispatcher and insert trigger must agree on the persisted clock;
    // no fallback to merchant acceptance or created_at can extend an offer.
    await seed(910,'1 minute');
    await db.query('UPDATE orders SET rider_search_started_at=NULL WHERE id=$1',[id(910)]);
    eq(await dispatch(a),0,'missing server search timestamp suppresses real dispatch');
    eq(await count('notifications',910),0,'missing clock never notifies Rider');
    await seed(911,'2 minutes');
    await db.query("UPDATE orders SET created_at=now()-interval '1 minute' WHERE id=$1",[id(911)]);
    eq(await dispatch(a),0,'search time preceding order creation cannot dispatch');
    eq(await count('qg_rider_order_offers',911),0,'chronologically invalid search cannot offer');
    await seed(912,'2 minutes');
    await db.query("UPDATE orders SET merchant_accepted_at=now()-interval '1 minute' WHERE id=$1",[id(912)]);
    eq(await dispatch(a),0,'search time before merchant acceptance cannot dispatch');
    eq(await count('notifications',912),0,'bad merchant/search time cannot notify');
    await seed(913,'1 minute');
    await db.query('UPDATE orders SET rider_assigned_at=now() WHERE id=$1',[id(913)]);
    eq(await dispatch(a),0,'stale rider assignment timestamp cannot be reoffered');
    await seed(914,'1 minute');
    await db.query("UPDATE orders SET created_at=now()-interval '3 minutes', merchant_accepted_at=now()-interval '2 minutes' WHERE id=$1",[id(914)]);
    eq(await dispatch(a),1,'valid merchant-before-search time remains dispatchable');
    eq(await count('notifications',914),1,'valid timestamp chain notifies exactly once');
    await db.query('DELETE FROM qg_rider_order_offers WHERE order_id=$1',[id(914)]);
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(914)]);

    // P0 regression: PostgreSQL now() is frozen at BEGIN. A transaction that
    // crosses the actual deadline must not insert a Rider offer using stale time.
    await seed(915, '1 minute');
    await db.query("UPDATE system_settings SET value=jsonb_set(value,'{orderTimeout}','1'::jsonb) WHERE key='app'");
    await a.query('BEGIN');
    await a.query("UPDATE orders SET created_at=now()-interval '59 seconds', rider_search_started_at=now()-interval '59 seconds' WHERE id=$1",[id(915)]);
    await a.query('SELECT pg_sleep(2.2)');
    const expiredAfterWait = await a.query("INSERT INTO public.qg_rider_order_offers(order_id,rider_id,attempt,offered_at,expires_at) VALUES($1,$2,1,now(),now()+interval '30 seconds') RETURNING order_id",[id(915),id(11)]);
    eq(expiredAfterWait.rows.length,0,'real wall clock expiry blocks a stale transaction timestamp');
    await a.query('COMMIT');
    eq(await count('qg_rider_order_offers',915),0,'delayed transaction leaves no Rider offer');
    await db.query("UPDATE system_settings SET value=jsonb_set(value,'{orderTimeout}','30'::jsonb) WHERE key='app'");

    await db.query('BEGIN');
    await db.query('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers; DROP FUNCTION qg_private.guard_rider_offer_deadline()');
    await db.query('ROLLBACK');
    eq((await db.query("SELECT count(*)::int AS n FROM pg_trigger WHERE tgname='qg_rider_offer_deadline_guard' AND NOT tgisinternal")).rows[0].n,1,'transaction rollback restores guard DDL');
    eq(await dispatch(b),0,'guard remains effective after rolled-back DDL');
    console.log(JSON.stringify({checks,failures:0,postgres_version:version,
      concurrent_sessions:3,fixture:'synthetic local only',
      scope:'real PostgreSQL dispatch locking, role ACL, no resurrection and DDL rollback; NOT financial terminalization or Production certification'}));
  } finally {
    for(const client of clients) { await client.query('ROLLBACK').catch(()=>{}); await client.end(); }
  }
}
main().catch(error => {console.error(error.message);process.exitCode=1;});
