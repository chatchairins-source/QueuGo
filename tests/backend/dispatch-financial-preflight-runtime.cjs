'use strict';
// Execute the actual operator SQL on synthetic data; never connect to Production.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { PGlite } = require('@electric-sql/pglite');
const sql = fs.readFileSync('ops/p0/dispatch-terminalization-preflight-readonly-20261120.sql', 'utf8');
async function main() {
  const db = new PGlite();
  let checks = 0;
  const equal = (actual, expected) => { assert.equal(Number(actual), expected); checks++; };
  const snapshot = async () => {
    const results = await db.exec(sql);
    return results.find(result => result.rows?.[0]?.searching_orders !== undefined).rows[0];
  };
  try {
    await db.exec(`
      CREATE TABLE system_settings(key text PRIMARY KEY, value jsonb);
      CREATE TABLE orders(id integer PRIMARY KEY, market_order_id integer, status text,
        payment_status text, payment_method text, paid_at timestamptz, cancelled_at timestamptz,
        rider_assigned_at timestamptz, merchant_accepted_at timestamptz,
        rider_search_started_at timestamptz, created_at timestamptz, rider_id integer);
      CREATE TABLE queuego_cash_order_locks(order_id integer);
      CREATE TABLE qg_rider_order_offers(order_id integer);
      CREATE TABLE payments(order_id integer, status text);
      CREATE TABLE merchant_cash_receipts(order_id integer);
      CREATE TABLE rider_cash_advances(order_id integer);
      CREATE TABLE qg_rider_cash_return_refunds(order_id integer);
      CREATE TABLE grocery_stock_reservations(order_id integer, released boolean);
      CREATE TABLE pos_inventory_reservations(order_id integer, released boolean);
      CREATE TABLE qg_rider_offer_history(order_id integer);
      INSERT INTO orders(id,status,payment_status,payment_method,rider_search_started_at,created_at)
        VALUES(1,'searching_rider','unpaid','cash',now()-interval '31 minutes',now()-interval '35 minutes');
    `);
    let row = await snapshot();
    equal(row.configured_timeout_minutes, 30);
    equal(row.financial_preflight_only_candidates, 1);
    equal(row.overdue_at_configured_timeout, 1);
    // A valid Admin timeout must agree with the dispatcher, not a hardcoded 30m.
    await db.exec(`INSERT INTO system_settings VALUES('app','{"orderTimeout":120}');`);
    row = await snapshot();
    equal(row.configured_timeout_minutes, 120);
    equal(row.financial_preflight_only_candidates, 0);
    equal(row.overdue_at_configured_timeout, 0);
    for (const setting of ['{"orderTimeout":1}', '{"orderTimeout":"bad"}', '{"orderTimeout":0}', '{"orderTimeout":1441}']) {
      await db.query('UPDATE system_settings SET value=$1::jsonb', [setting]);
      row = await snapshot();
      equal(row.configured_timeout_minutes, setting.includes(':1}') ? 1 : 30);
      equal(row.financial_preflight_only_candidates, 1);
    }
    // Each separate financial relation must block the actual query, including
    // released stock: this diagnostic never authorizes a financial transition.
    for (const table of ['queuego_cash_order_locks', 'qg_rider_order_offers', 'payments',
      'merchant_cash_receipts', 'rider_cash_advances', 'qg_rider_cash_return_refunds',
      'grocery_stock_reservations', 'pos_inventory_reservations']) {
      await db.exec(`INSERT INTO ${table}(order_id) VALUES(1);`);
      row = await snapshot();
      equal(row.financial_preflight_only_candidates, 0);
      equal(row.overdue_blocked_for_manual_review, 1);
      await db.exec(`DELETE FROM ${table};`);
    }
    for (const assignment of ["payment_method=NULL", "payment_method=' '", "payment_status=NULL",
      'paid_at=now()', 'rider_assigned_at=now()', 'cancelled_at=now()', 'market_order_id=7']) {
      await db.exec(`UPDATE orders SET ${assignment};`);
      row = await snapshot();
      equal(row.financial_preflight_only_candidates, 0);
      equal(row.overdue_blocked_for_manual_review, 1);
      await db.exec(`UPDATE orders SET payment_method='cash', payment_status='unpaid',
        paid_at=NULL, rider_assigned_at=NULL, cancelled_at=NULL, market_order_id=NULL;`);
    }
    await db.exec('UPDATE orders SET rider_search_started_at=NULL;');
    row = await snapshot();
    // A reporting fallback remains available, but the actual rider search start is absent.
    equal(row.missing_actual_rider_search_start, 1);
    equal(row.unknown_search_start, 0);
    equal(row.financial_preflight_only_candidates, 0);
    assert.ok(Object.keys(row).every(key => !/(^id$|order_id|rider_id|address|phone)/.test(key)));
    checks++;
    // The operational query itself must run in a read-only transaction.
    await assert.rejects(db.exec(sql.replace('FROM facts;', 'FROM facts; DELETE FROM public.orders;')));
    await db.exec('ROLLBACK;');
    equal((await db.query('SELECT count(*) AS n FROM orders')).rows[0].n, 1);
  } finally { await db.close(); }
  console.log(JSON.stringify({ checks, failures: 0, scope: 'actual financial preflight SQL, disposable PGlite only' }));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
