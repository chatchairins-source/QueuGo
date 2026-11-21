'use strict';
const assert = require('node:assert/strict');
const {Client} = require('pg');
const raw = process.env.QG_CASH_HELPER_TEST_DATABASE_URL || '';
const url = new URL(raw);
assert.ok(['postgres:','postgresql:'].includes(url.protocol),'Postgres only');
assert.ok(['127.0.0.1','localhost','[::1]'].includes(url.hostname),'loopback-only database');
assert.equal(url.pathname,'/qg_cash_helper_test_ci','disposable fixture database only');
assert.equal(url.port,'55440','isolated CI port only');
const uuid = n => '00000000-0000-4000-8000-'+String(n).padStart(12,'0');
const makeClient = async () => {
  const c = new Client({connectionString:raw,connectionTimeoutMillis:5000});
  await c.connect();
  await c.query("SET statement_timeout='12000ms'");
  return c;
};
const scalar = async (c,sql,values=[]) => (await c.query(sql,values)).rows[0];
const seed = (c,id,minutes=31) => c.query('SELECT public.qg_test_seed_timeout($1::uuid,$2::int)',[id,minutes]);
(async () => {
 const clients = await Promise.all([makeClient(),makeClient(),makeClient()]);
 const [cron,actor,other] = clients; let checks=0;
 try {
  // Production cash guard cannot see a previously accepted Rider at deadline.
  const assigned=uuid(1201);
  await seed(cron,assigned,31);
  await actor.query('BEGIN');
  await actor.query("SELECT set_config('qg.test_actor_role','admin',true)");
  const accepted=await actor.query(
    "UPDATE public.orders SET status='rider_assigned',rider_id=$2::uuid,rider_assigned_at=now() WHERE id=$1",
    [assigned,uuid(1298)]);
  assert.equal(accepted.rowCount,1);checks++;
  const attempt=await scalar(cron,'SELECT qg_private.cancel_rider_timeout_cash(50) AS n');
  assert.equal(Number(attempt.n),0,'SKIP LOCKED may not cancel an in-flight Rider acceptance');checks++;
  await actor.query('COMMIT');
  const assignedRow=await scalar(cron,'SELECT status,rider_id FROM public.orders WHERE id=$1',[assigned]);
  assert.equal(assignedRow.status,'rider_assigned');checks++;
  assert.equal(assignedRow.rider_id,uuid(1298));checks++;
  const noCancel=await scalar(cron,'SELECT qg_private.cancel_rider_timeout_cash(50) AS n');
  assert.equal(Number(noCancel.n),0);checks++;

  // Two concurrent Cron calls must cancel exactly once, without duplicate alerts.
  const competing=uuid(1202);
  await seed(cron,competing,31);
  const [a,b]=await Promise.all([
    scalar(cron,'SELECT qg_private.cancel_rider_timeout_cash(50) AS n'),
    scalar(other,'SELECT qg_private.cancel_rider_timeout_cash(50) AS n')
  ]);
  assert.equal(Number(a.n)+Number(b.n),1,'duplicate Cron calls must cancel only once');checks++;
  assert.equal((await scalar(cron,'SELECT status FROM public.orders WHERE id=$1',[competing])).status,'cancelled');checks++;
  assert.equal((await scalar(cron,'SELECT status FROM public.payments WHERE order_id=$1',[competing])).status,'cancelled');checks++;
  assert.equal((await scalar(cron,'SELECT status FROM public.deliveries WHERE order_id=$1',[competing])).status,'cancelled');checks++;
  const notice=await scalar(cron,'SELECT count(*)::int AS n FROM public.notifications WHERE reference_id=$1',[competing]);
  assert.equal(notice.n,2,'customer and shop notifications only once');checks++;
  const audit=await scalar(cron,"SELECT count(*)::int AS n FROM public.audit_logs WHERE entity_id=$1 AND action='no_rider_timeout'",[competing]);
  assert.equal(audit.n,1,'timeout audited once');checks++;

  // An active payment writer keeps its lock and commits capture before
  // Cron's financial validation: no cancellation should leak through.
  const captured=uuid(1203);
  await seed(cron,captured,31);
  await actor.query('BEGIN');
  await actor.query("SELECT set_config('qg.test_actor_role','admin',true)");
  await actor.query('SELECT 1 FROM public.payments WHERE order_id=$1 FOR UPDATE',[captured]);
  const actorPid=(await scalar(actor,'SELECT pg_backend_pid() AS pid')).pid;
  const cronPid=(await scalar(cron,'SELECT pg_backend_pid() AS pid')).pid;
  const flight=scalar(cron,'SELECT qg_private.cancel_rider_timeout_cash(50) AS n');
  let blocked=false;
  for(let i=0;i<150;i++) {
    const pids=(await scalar(actor,'SELECT pg_blocking_pids($1) AS pids',[cronPid])).pids;
    if(pids.includes(actorPid)){blocked=true;break;}
    await new Promise(resolve=>setTimeout(resolve,10));
  }
  assert.ok(blocked,'Cron must wait for in-flight payment writer lock');checks++;
  await actor.query("UPDATE public.payments SET status='paid',paid_at=now() WHERE order_id=$1",[captured]);
  await actor.query('COMMIT');
  assert.equal(Number((await flight).n),0,'captured cash is not cancelled');checks++;
  assert.equal((await scalar(cron,'SELECT status FROM public.orders WHERE id=$1',[captured])).status,'searching_rider');checks++;
  assert.equal((await scalar(cron,'SELECT status FROM public.payments WHERE order_id=$1',[captured])).status,'paid');checks++;
  console.log(JSON.stringify({scope:'real Postgres 17.6 disposable only; not Production',checks,failures:0}));
 } finally {
  try {await actor.query('ROLLBACK')} catch {}
  await Promise.all(clients.map(c=>c.end()));
 }
})().catch(e=>{console.error(e);process.exitCode=1});
