'use strict';
// No Production connection or identifiers; exercise the actual read-only SQL.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {PGlite} = require('@electric-sql/pglite');
const sql = fs.readFileSync('ops/p0/rider-dispatch-deployment-readonly.sql', 'utf8');
const stripped = sql.replace(/--[^\n]*/g, '');
assert.match(stripped, /^\s*WITH\b/i, 'must start with a read-only CTE');
assert.doesNotMatch(stripped, /\b(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|EXECUTE|PERFORM)\b/i,
  'release gate must contain no mutation or RPC');
const db = new PGlite();
let checks = 0;
const equal = (actual, expected, title) => {assert.equal(actual, expected, title); checks++;};
async function snapshot() {
  const {rows} = await db.query(sql);
  equal(rows.length, 1, 'one aggregate release-gate row');
  const row = rows[0];
  assert.ok(Object.keys(row).every(k => !/(^id$|order_id|rider_id|customer_id|address|phone|email)/i.test(k)));
  checks++;
  return row;
}
async function main() {
  try {
    await db.exec('CREATE SCHEMA qg_private; CREATE TABLE public.qg_rider_order_offers(id integer);');
    let row = await snapshot();
    equal(row.offer_guard_function_present, false, 'missing offer guard');
    equal(row.offer_guard_trigger_enabled, false, 'missing offer guard trigger');
    equal(row.cash_timeout_function_present, false, 'missing timeout authority');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'empty production does not certify prevention');

    await db.exec(`CREATE FUNCTION qg_private.cancel_rider_timeout_cash(integer)
      RETURNS integer LANGUAGE sql AS 'SELECT 0';`);
    row = await snapshot();
    equal(row.cash_timeout_function_present, true, 'authority presence recognized');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'authority alone cannot certify');

    await db.exec(`CREATE FUNCTION qg_private.guard_rider_offer_deadline()
      RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RETURN NEW; END';`);
    row = await snapshot();
    equal(row.offer_guard_function_present, true, 'guard function presence recognized');
    equal(row.offer_guard_trigger_enabled, false, 'function without trigger cannot certify');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'missing trigger fails closed');

    await db.exec(`CREATE TRIGGER qg_rider_offer_deadline_guard
      BEFORE INSERT ON public.qg_rider_order_offers
      FOR EACH ROW EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();`);
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, true, 'enabled correct trigger found');
    equal(row.release_gate, 'OPERATOR_ATTESTATION_REQUIRED',
      'installation evidence is not operational approval');

    await db.exec('ALTER TABLE public.qg_rider_order_offers DISABLE TRIGGER qg_rider_offer_deadline_guard;');
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, false, 'disabled trigger rejected');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'disabled trigger fails closed');

    await db.exec('ALTER TABLE public.qg_rider_order_offers ENABLE TRIGGER qg_rider_offer_deadline_guard;');
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, true, 'reactivated trigger recognized');
    equal(row.release_gate, 'OPERATOR_ATTESTATION_REQUIRED', 'no automatic green release');

    await db.exec('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;');
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, false, 'removed trigger rejected');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'removed trigger fails closed');
  } finally {
    await db.close();
  }
  console.log(JSON.stringify({checks,failures:0,scope:
    'read-only deployed P0 function/trigger presence; operator attestation remains mandatory'}));
}
main().catch(e => {console.error(e);process.exitCode = 1;});
