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
    await db.exec('CREATE SCHEMA qg_private; CREATE SCHEMA cron; CREATE TABLE public.qg_rider_order_offers(id integer); CREATE TABLE cron.job(jobname text, active boolean, username text, database text, nodename text, nodeport integer, schedule text, command text);');
    let row = await snapshot();
    equal(row.offer_guard_function_present, false, 'missing offer guard');
    equal(row.offer_guard_trigger_enabled, false, 'missing offer guard trigger');
    equal(row.cash_timeout_function_present, false, 'missing timeout authority');
    equal(row.cash_timeout_cron_active, false, 'missing trusted cron job');
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
    equal(row.cash_timeout_cron_active, false, 'installed function is not a running scheduler');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'without cron fail closed');

    // Synthetic metadata only: NO cron.schedule or Production access.
    await db.exec(`INSERT INTO cron.job(jobname,active,username,database,nodename,nodeport,schedule,command)
      VALUES('queuego-rider-cash-timeout',true,'postgres',current_database(),'localhost',5432,'* * * * *',
        'SELECT qg_private.cancel_rider_timeout_cash(30);');`);
    row = await snapshot();
    equal(row.cash_timeout_cron_active, true, 'exact trusted active 1-minute cron found');
    equal(row.release_gate, 'OPERATOR_ATTESTATION_REQUIRED',
      'a correct job still requires independent real operator evidence');
    for(const [field,bad,good,label] of [
      ['active','false','true','disabled'],
      ['username',"'anon'","'postgres'",'untrusted execution role'],
      ['database',"'wrong_database'","current_database()",'wrong target database'],
      ['nodename',"'remote-host.invalid'","'localhost'",'wrong Cron execution host'],
      ['nodeport','6543','5432','wrong Cron execution port'],
      ['schedule',"'*/2 * * * *'","'* * * * *'",'wrong interval'],
      ['command',"'SELECT qg_private.cancel_rider_timeout_cash(31);'","'SELECT qg_private.cancel_rider_timeout_cash(30);'",'wrong command']
    ]) {
      await db.exec(`UPDATE cron.job SET ${field}=${bad};`);
      row = await snapshot();
      equal(row.cash_timeout_cron_active, false, label+' must fail');
      equal(row.release_gate, 'NO_GO_DISPATCH_P0', label+' release blocked');
      await db.exec(`UPDATE cron.job SET ${field}=${good};`);
    }
    await db.exec(`INSERT INTO cron.job(jobname,active,username,database,nodename,nodeport,schedule,command)
      VALUES('shadow-timeout',true,'postgres',current_database(),'localhost',5432,'* * * * *',
        'SELECT qg_private.cancel_rider_timeout_cash(30);');`);
    row = await snapshot();
    equal(row.cash_timeout_cron_active, false, 'duplicate invocation blocked');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'duplicate cron cannot certify');
    await db.exec("DELETE FROM cron.job WHERE jobname='shadow-timeout';");
    row = await snapshot();
    equal(row.cash_timeout_cron_active, true, 'exact single job restored');

    // An enabled trigger is not enough: wrong event/timing/row mode or WHEN
    // false would bypass every real offer insertion while appearing installed.
    await db.exec('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;');
    await db.exec(`CREATE TRIGGER qg_rider_offer_deadline_guard
      AFTER INSERT ON public.qg_rider_order_offers
      FOR EACH ROW EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();`);
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, false, 'AFTER INSERT trigger cannot enforce pre-insert guard');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'AFTER trigger rejected');

    await db.exec('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;');
    await db.exec(`CREATE TRIGGER qg_rider_offer_deadline_guard
      BEFORE UPDATE ON public.qg_rider_order_offers
      FOR EACH ROW EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();`);
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, false, 'BEFORE UPDATE trigger cannot guard INSERT');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'UPDATE-only trigger rejected');

    await db.exec('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;');
    await db.exec(`CREATE TRIGGER qg_rider_offer_deadline_guard
      BEFORE INSERT ON public.qg_rider_order_offers
      FOR EACH STATEMENT EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();`);
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, false, 'statement-level guard cannot reject an inserted row');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'statement-level trigger rejected');

    await db.exec('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;');
    await db.exec(`CREATE TRIGGER qg_rider_offer_deadline_guard
      BEFORE INSERT ON public.qg_rider_order_offers
      FOR EACH ROW WHEN (false)
      EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();`);
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, false, 'always-skipped WHEN clause rejected');
    equal(row.release_gate, 'NO_GO_DISPATCH_P0', 'filtered trigger rejected');

    await db.exec('DROP TRIGGER qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;');
    await db.exec(`CREATE TRIGGER qg_rider_offer_deadline_guard
      BEFORE INSERT ON public.qg_rider_order_offers
      FOR EACH ROW EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();`);
    row = await snapshot();
    equal(row.offer_guard_trigger_enabled, true, 'reinstalled exact unconditional row guard');
    equal(row.release_gate, 'OPERATOR_ATTESTATION_REQUIRED',
      'valid shape still requires operator review');

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
