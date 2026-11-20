'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {PGlite} = require('@electric-sql/pglite');
async function main() {
 const db = new PGlite(); let checks=0, next=0;
 const equal=(a,b)=>{assert.equal(a,b); checks++;};
 try {
  await db.exec(`CREATE ROLE service_role;
   CREATE TABLE orders(id integer PRIMARY KEY,status text,rider_id integer,rider_assigned_at timestamptz,market_order_id integer,paid_at timestamptz,total_amount numeric);
   CREATE TABLE payments(order_id integer,status text,payment_method text,paid_at timestamptz,transaction_reference text,amount numeric,updated_at timestamptz);
   CREATE TABLE deliveries(order_id integer,status text,updated_at timestamptz);
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
   n=>db.query('UPDATE orders SET rider_id=5 WHERE id=$1',[n]),
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
  // Simulate downstream failure: status and cash cleanup roll back together.
  await db.exec(`CREATE FUNCTION fail_terminal() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='no_rider_available' THEN RAISE EXCEPTION 'fixture downstream failure'; END IF; RETURN NEW; END $$;
    CREATE TRIGGER zzz_failure AFTER UPDATE OF status ON orders FOR EACH ROW EXECUTE FUNCTION fail_terminal();`);
  const atomic=await seed();await assert.rejects(transition(atomic),/fixture downstream failure/);checks++;
  equal(await status(atomic,'orders'),'searching_rider');equal(await status(atomic,'payments'),'pending');equal(await status(atomic,'deliveries'),'pending');
 } finally {await db.close();}
 console.log(JSON.stringify({checks,failures:0,scope:'review-only cash dependent helper, actual captured baseline + disposable synthetic rows; NOT timeout scheduler or Production certification'}));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
