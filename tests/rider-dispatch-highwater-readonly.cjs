'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {PGlite}=require('@electric-sql/pglite');
const sql=fs.readFileSync('ops/p0/rider-dispatch-highwater-readonly.sql','utf8');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');

async function main(){
  // Never run the health query as a write operation in Production.
  const stripped=sql.replace(/--[^\n]*/g,'');
  assert.ok(/^\s*WITH\b/i.test(stripped),'must be a read-only aggregate SELECT');
  assert.ok(!/\b(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|EXECUTE|PERFORM)\b/i.test(stripped),
    'operational read query must have no data mutation or RPC');
  const db=new PGlite();
  let checks=0;
  try{
    await db.exec([
      'CREATE TABLE system_settings(key text PRIMARY KEY,value jsonb NOT NULL);',
      'CREATE TABLE orders(id uuid PRIMARY KEY,status text,rider_id uuid,rider_search_started_at timestamptz,merchant_accepted_at timestamptz,created_at timestamptz);',
      'CREATE TABLE qg_rider_order_offers(order_id uuid,rider_id uuid,offered_at timestamptz,expires_at timestamptz,attempt integer);',
      'CREATE TABLE qg_rider_offer_history(order_id uuid,rider_id uuid,offered_at timestamptz,outcome text,attempt integer);',
      'CREATE TABLE notifications(reference_id uuid,created_at timestamptz);',
      "INSERT INTO system_settings VALUES('app','{\"orderTimeout\":30}');",
    ].join('\n'));
    await db.query(
      "INSERT INTO orders VALUES ($1,'searching_rider',NULL,now()-interval '55 hours',now()-interval '55 hours',now()-interval '56 hours')",
      [id(1)]
    );
    await db.query(
      "INSERT INTO orders VALUES ($1,'searching_rider',NULL,now()-interval '2 minutes',now()-interval '4 minutes',now()-interval '5 minutes')",
      [id(2)]
    );
    await db.query(
      "INSERT INTO orders VALUES ($1,'completed',NULL,now()-interval '1 day',now()-interval '1 day',now()-interval '1 day')",
      [id(3)]
    );
    await db.query(
      "INSERT INTO qg_rider_order_offers VALUES($1,$2,now()-interval '1 minute',now()+interval '20 seconds',1)",
      [id(1),id(99)]
    );
    await db.query(
      "INSERT INTO qg_rider_order_offers VALUES($1,$2,now()-interval '5 minutes',now()+interval '20 seconds',1)",
      [id(3),id(99)]
    );
    await db.query(
      "INSERT INTO qg_rider_offer_history VALUES($1,$2,now()-interval '10 minutes','expired',131),($1,$2,now()-interval '40 hours','expired',1)",
      [id(1),id(99)]
    );
    await db.query(
      "INSERT INTO notifications VALUES($1,now()-interval '20 minutes'),($1,now()-interval '30 hours')",
      [id(1)]
    );
    let row=(await db.query(sql)).rows[0];
    const equal=(value,expected,label)=>{assert.equal(Number(value),expected,label);checks++;};
    assert.equal(row.release_gate,'NO_GO_DISPATCH_P0');checks++;
    equal(row.configured_timeout_minutes,30,'finite Admin timeout');
    equal(row.searching_orders,2,'two unassigned searching orders');
    equal(row.overdue_searching_orders,1,'one overdue order');
    equal(row.late_active_offers,1,'overdue active offer');
    equal(row.invalid_status_active_offers,1,'orphaned active offer on terminal order');
    equal(row.overdue_historical_offers,2,'historic expired offers');
    equal(row.overdue_offers_last_hour,1,'one recent attempt on the overdue order');
    equal(row.overdue_notifications_last_hour,1,'one recent overdue notification');
    equal(row.overdue_offer_attempts_above_120,1,'detect extreme retry attempts');
    assert.ok(Object.keys(row).every(k=>!/(^id$|order_id|rider_id|customer_id|address|phone|email)/i.test(k)),
      'never return PII or raw customer/order identifiers');checks++;

    // A valid local fixture cleanup produces a release-ready aggregate.
    await db.exec('DELETE FROM qg_rider_order_offers;DELETE FROM notifications;DELETE FROM qg_rider_offer_history;');
    await db.query("UPDATE orders SET status='cancelled' WHERE id=$1",[id(1)]);
    row=(await db.query(sql)).rows[0];
    assert.equal(row.release_gate,'PASS_DISPATCH_OVERTIME');checks++;
    equal(row.searching_orders,1,'recent valid search remains visible');
    equal(row.overdue_searching_orders,0,'no overdue search remains');
    equal(row.invalid_status_active_offers,0,'no orphaned active offer');

    // Missing/broken config never means infinite time.
    await db.exec("UPDATE system_settings SET value='{\"orderTimeout\":\"unlimited\"}';");
    await db.query("UPDATE orders SET rider_search_started_at=now()-interval '35 minutes' WHERE id=$1",[id(2)]);
    row=(await db.query(sql)).rows[0];
    assert.equal(row.release_gate,'NO_GO_DISPATCH_P0');checks++;
    equal(row.configured_timeout_minutes,30,'invalid timeout uses finite default');
    equal(row.overdue_searching_orders,1,'fallback still catches overdue order');
    await db.exec('DELETE FROM system_settings;');
    row=(await db.query(sql)).rows[0];
    equal(row.configured_timeout_minutes,30,'missing setting still finite');

    // Admin can increase a valid timeout without modifying rows or history.
    await db.exec("INSERT INTO system_settings VALUES('app','{\"orderTimeout\":120}');");
    row=(await db.query(sql)).rows[0];
    equal(row.configured_timeout_minutes,120,'valid Admin override');
    assert.equal(row.release_gate,'PASS_DISPATCH_OVERTIME');checks++;
  }finally{await db.close();}
  console.log(JSON.stringify({checks,failures:0,scope:'isolated PGlite read-only Rider dispatch P0 health metrics; no Production write'}));
}
main().catch(e=>{console.error(e);process.exitCode=1});
