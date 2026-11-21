'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { PGlite } = require('@electric-sql/pglite');

const guardFile = 'ops/p0/rider-offer-deadline-guard.sql';
const guardSql = fs.readFileSync(guardFile, 'utf8');
assert.ok(!guardSql.includes('\\n'), 'SQL guard must use real line separators; escaped newline comments can silently disable conditions');
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');

async function main() {
  let checks = 0;
  const db = new PGlite();
  await db.exec([
    'CREATE ROLE anon;',
    'CREATE ROLE authenticated;',
    'CREATE SCHEMA qg_private;',
    'CREATE TABLE public.system_settings(key text PRIMARY KEY, value jsonb NOT NULL);',
    'CREATE TABLE public.orders(id uuid PRIMARY KEY, status text NOT NULL, rider_id uuid, rider_search_started_at timestamptz, merchant_accepted_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());',
    'CREATE TABLE public.qg_rider_order_offers(order_id uuid PRIMARY KEY, rider_id uuid NOT NULL, offered_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, attempt integer NOT NULL, CONSTRAINT expires_after_offer CHECK(expires_at>offered_at));',
    'CREATE TABLE public.notifications(reference_id uuid NOT NULL);',
    "INSERT INTO public.system_settings(key,value) VALUES('app','{\"orderTimeout\":30}'::jsonb);",
    "CREATE OR REPLACE FUNCTION public.synthetic_dispatch(p_order_id uuid) RETURNS boolean LANGUAGE plpgsql AS $synthetic$ BEGIN INSERT INTO public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) VALUES(p_order_id,'10000000-0000-4000-8000-000000000001',now(),now()+interval '30 seconds',1) ON CONFLICT(order_id) DO NOTHING; IF FOUND THEN INSERT INTO public.notifications(reference_id) VALUES (p_order_id); RETURN true; END IF; RETURN false; END $synthetic$;"
  ].join('\n'));
  await db.exec(guardSql);
  async function seed(n, status, rider, searchAge, merchantAge, createdAge) {
    await db.query(
      'INSERT INTO public.orders(id,status,rider_id,rider_search_started_at,merchant_accepted_at,created_at) VALUES($1,$2,$3,now()-($4::text)::interval,now()-($5::text)::interval,now()-($6::text)::interval)',
      [id(n), status, rider, searchAge, merchantAge || searchAge, createdAge || searchAge]
    );
  }
  async function seedFallback(n, merchantAge, createdAge) {
    await db.query(
      'INSERT INTO public.orders(id,status,rider_id,rider_search_started_at,merchant_accepted_at,created_at) VALUES($1,$2,NULL,NULL,now()-($3::text)::interval,now()-($4::text)::interval)',
      [id(n), 'searching_rider', merchantAge, createdAge]
    );
  }
  async function offer(n) {
    return (await db.query('SELECT public.synthetic_dispatch($1::uuid) AS accepted', [id(n)])).rows[0].accepted;
  }
  async function counts(n) {
    const row = (await db.query(
      'SELECT (SELECT count(*)::integer FROM qg_rider_order_offers WHERE order_id=$1) offers, (SELECT count(*)::integer FROM notifications WHERE reference_id=$1) notifications',
      [id(n)]
    )).rows[0];
    return [row.offers, row.notifications];
  }
  async function equal(actual, expected, label) {
    assert.deepEqual(actual, expected, label); checks++;
  }
  await seed(1, 'searching_rider', null, '55 hours');
  for (let n=0; n<8; n++) await equal(await offer(1), false, 'stale rider offer retry must never be created');
  await equal(await counts(1), [0, 0], 'no repeated targeted notifications for a stale order');

  await seed(2, 'searching_rider', null, '1 minute');
  await equal(await offer(2), true, 'valid searching order can be offered');
  await equal(await offer(2), false, 'same live offer must not duplicate a notification');
  await equal(await counts(2), [1, 1], 'one offer creates one notification');

  await seed(3, 'searching_rider', null, '29 minutes 45 seconds');
  await equal(await offer(3), true, 'a 30-second offer inside the search deadline is allowed');
  const ttl = (await db.query('SELECT EXTRACT(epoch FROM (f.expires_at - now())) AS seconds FROM public.qg_rider_order_offers f WHERE order_id=$1', [id(3)])).rows[0].seconds;
  assert(Number(ttl) > 0 && Number(ttl) <= 16, 'offer must not remain valid after overall deadline'); checks++;
  await db.query('DELETE FROM public.qg_rider_order_offers WHERE order_id=$1', [id(3)]);
  await db.query("UPDATE public.orders SET rider_search_started_at=now()-interval '31 minutes' WHERE id=$1", [id(3)]);
  await equal(await offer(3), false, 'cron must not resurrect offers after deadline');
  await equal(await counts(3), [0, 1], 'no new notification after deadline');

  await seed(4, 'completed', null, '1 minute');
  await equal(await offer(4), false, 'terminal completed order cannot be reoffered');
  await seed(5, 'searching_rider', '20000000-0000-4000-8000-000000000002', '1 minute');
  await equal(await offer(5), false, 'already assigned rider cannot receive new offer');
  await seedFallback(6, '2 minutes', '3 hours');
  await equal(await offer(6), true, 'older orders without search start use merchant acceptance time');

  await db.exec("UPDATE public.system_settings SET value='{\"orderTimeout\":1}'::jsonb WHERE key='app'");
  await seed(7, 'searching_rider', null, '2 minutes');
  await equal(await offer(7), false, 'one-minute Admin timeout is honored');
  await db.exec("UPDATE public.system_settings SET value='{\"orderTimeout\":120}'::jsonb WHERE key='app'");
  await equal(await offer(7), true, 'larger Admin timeout is honored');

  await db.exec("UPDATE public.system_settings SET value='{\"orderTimeout\":\"unlimited\"}'::jsonb WHERE key='app'");
  await seed(8, 'searching_rider', null, '40 minutes');
  await equal(await offer(8), false, 'malformed timeout falls back to finite 30 minutes');
  await db.exec("UPDATE public.system_settings SET value='{\"orderTimeout\":0}'::jsonb WHERE key='app'");
  await equal(await offer(8), false, 'zero timeout is never unlimited');
  await db.exec("DELETE FROM public.system_settings WHERE key='app'");
  await equal(await offer(8), false, 'missing setting defaults to bounded timeout');

  await db.exec("INSERT INTO public.orders(id,status,rider_search_started_at,created_at) VALUES('" + id(9) + "','searching_rider',NULL,now()-interval '1 hour')");
  await equal(await offer(9), false, 'created timestamp is a finite fallback when search start missing');
  await db.query('UPDATE public.orders SET status=$2 WHERE id=$1', [id(2),'cancelled']);
  await db.query('DELETE FROM public.qg_rider_order_offers WHERE order_id=$1', [id(2)]);
  await equal(await offer(2), false, 'concurrent terminal state prevents cron revival');

  // Hard cap is enforced by the server trigger even if a caller bypasses the
  // normal dispatcher and submits an arbitrary attempt counter.
  await seed(10, 'searching_rider', null, '1 minute');
  const insertAttempt = async attempt => {
    const result = await db.query(
      'INSERT INTO public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) VALUES($1,$2,now(),now()+interval \'30 seconds\',$3) ON CONFLICT(order_id) DO NOTHING RETURNING attempt',
      [id(10), id(11), attempt]
    );
    return result.rows.length;
  };
  await equal(await insertAttempt(121), 0, 'attempt 121 must be rejected before notification');
  await equal(await insertAttempt(0), 0, 'attempt zero must be rejected');
  await equal(await insertAttempt(120), 1, 'attempt 120 remains valid inside deadline');
  await equal(await counts(10), [1, 0], 'rejected attempts must not create offers or notifications');

  // PostgreSQL LEAST(NULL, deadline) is NOT NULL. A missing offer expiry
  // must be rejected before any default/deadline clamping occurs.
  await seed(11, 'searching_rider', null, '1 minute');
  const noExpiry = await db.query(
    'INSERT INTO public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) VALUES($1,$2,now(),NULL,1) ON CONFLICT(order_id) DO NOTHING RETURNING attempt',
    [id(11), id(101)]
  );
  await equal(noExpiry.rows.length, 0, 'null offer expiry is suppressed, not converted to search deadline');
  await equal(await counts(11), [0, 0], 'null expiry cannot create an offer or Rider notification');

  await seed(12, 'searching_rider', null, '1 minute');
  const oversized = await db.query(
    "INSERT INTO public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) VALUES($1,$2,now(),now()+interval '10 minutes',1) ON CONFLICT(order_id) DO NOTHING RETURNING attempt",
    [id(12), id(101)]
  );
  await equal(oversized.rows.length, 1, 'oversized upstream TTL remains a valid bounded offer');
  const actualTTL = (await db.query(
    'SELECT EXTRACT(epoch FROM (expires_at - offered_at)) AS ttl FROM public.qg_rider_order_offers WHERE order_id=$1', [id(12)]
  )).rows[0].ttl;
  assert(Number(actualTTL) > 0 && Number(actualTTL) <= 30.01, 'upstream 10-minute offer must be clamped to 30 seconds'); checks++;

  await seed(13, 'searching_rider', null, '1 minute');
  const alreadyExpired = await db.query(
    "INSERT INTO public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) VALUES($1,$2,now()-interval '2 minutes',now()+interval '30 seconds',1) ON CONFLICT(order_id) DO NOTHING RETURNING attempt",
    [id(13), id(101)]
  );
  await equal(alreadyExpired.rows.length, 0, 'delayed offer past its original 30-second TTL cannot be inserted');
  await equal(await counts(13), [0, 0], 'delayed expiry cannot cause notification amplification');
  await seed(14, 'searching_rider', null, '1 minute');
  const futureOffer = await db.query(
    "INSERT INTO public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) VALUES($1,$2,now()+interval '10 minutes',now()+interval '11 minutes',1) RETURNING attempt",
    [id(14),id(101)]);
  await equal(futureOffer.rows.length,0,'future offered_at cannot extend an offer beyond 30 seconds of server time');
  await equal(await counts(14),[0,0],'future offer cannot create targeted work');
  await seed(15,'searching_rider',null,'-10 minutes');
  await equal(await offer(15),false,'future search clock must not extend the dispatch window');
  await equal(await counts(15),[0,0],'future search start produces no offer or notification');
  const priv = (await db.query(
    "SELECT has_function_privilege('anon','qg_private.guard_rider_offer_deadline()','EXECUTE') anon, has_function_privilege('authenticated','qg_private.guard_rider_offer_deadline()','EXECUTE') authenticated"
  )).rows[0];
  await equal([priv.anon,priv.authenticated], [false,false], 'no public direct execute grant');
  assert(!/\bUPDATE\s+public\.orders\b/i.test(guardSql), 'containment may not modify real order states'); checks++;
  assert(!/\b(INSERT|UPDATE|DELETE)\s+(?:FROM\s+)?public\.(payments|settlements|merchant_cash_receipts|rider_cash_advances)\b/i.test(guardSql), 'containment must not touch money'); checks++;
  assert(guardSql.includes('SECURITY INVOKER') && guardSql.includes("SET search_path TO ''"), 'trigger must not introduce definer bypass'); checks++;
  assert(guardSql.includes("NEW.offered_at + interval '30 seconds'"), 'each individual offer is capped at 30 seconds'); checks++;
  assert(guardSql.includes('NEW.expires_at <= now()'), 'already expired offer cannot trigger Rider notification'); checks++;
  assert(guardSql.includes('NEW.offered_at IS NULL OR NEW.expires_at IS NULL'), 'malformed timestamp must fail closed before LEAST'); checks++;
  await db.close();
  console.log(JSON.stringify({checks, failures:0, scope:'isolated PGlite bounded Rider offer insertion/notification guard; source-only, not deployed'}));
}
main().catch(error=>{console.error(error);process.exit(1)});
