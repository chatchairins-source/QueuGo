'use strict';
// Executes the actual source migration and shop state machine on isolated PGlite.
// Never connects to Supabase Production or creates real financial transactions.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { PGlite } = require('@electric-sql/pglite');
const source = fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql', 'utf8');
const begin = source.indexOf('create or replace function public.queuego_laundry_shop_manage(');
const end = source.indexOf('-- Existing API delegates', begin);
const columnsBegin = source.indexOf('alter table public.laundry_orders add column if not exists pickup_staff_name');
assert(begin > 0 && end > begin && columnsBegin > 0);
const columns = source.slice(columnsBegin, begin);
const functionSql = source.slice(begin, end);
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');

(async () => {
  const db = new PGlite();
  let checks = 0;
  const eq = (actual, expected, description) => {
    assert.deepEqual(actual, expected, description);
    checks++;
  };
  const denied = async (promise, pattern, description) => {
    await assert.rejects(promise, pattern, description);
    checks++;
  };
  const as = async n => {
    await db.exec('reset role');
    await db.query("select set_config('test.uid',$1,false)", [n ? id(n) : '']);
    await db.exec('set role authenticated');
  };
  const state = async orderId => (await db.query(
    'select status,gp_rate_snapshot,platform_fee_amount,shop_net_amount,final_amount,final_total_amount from laundry_orders where id=$1',
    [id(orderId)]
  )).rows[0];
  const counts = async () => {
    const rows = await db.query('select (select count(*)::integer from laundry_order_events) as events, (select count(*)::integer from notifications) as notices');
    return rows.rows[0];
  };
  const action = async (orderId, quantity) => db.query(
    "select public.queuego_laundry_shop_manage($1,'ready_return','washing',null,$2,null) as result",
    [id(orderId), quantity]
  );
  try {
    await db.exec(`create role anon; create role authenticated; create role service_role;
      create schema auth;
      create function auth.uid() returns uuid language sql stable as $$
        select nullif(current_setting('test.uid',true),'')::uuid $$;
      create table users(id uuid primary key,auth_user_id uuid,role text,status text);
      create table shop_profiles(id uuid primary key,user_id uuid,status text,archived_at timestamptz);
      create table laundry_hubs(id uuid primary key,shop_id uuid);
      create table laundry_orders(
        id uuid primary key,hub_id uuid,customer_id uuid,status text,
        service_id uuid,pricing_type_snapshot text,unit_price_snapshot numeric,
        actual_quantity numeric,actual_kg numeric,delivery_fee_total_snapshot numeric,
        final_amount numeric,final_total_amount numeric,updated_at timestamptz);
      create table laundry_shop_settings(hub_id uuid primary key,minimum_order numeric);
      create table laundry_rider_jobs(laundry_order_id uuid,status text);
      create table laundry_order_events(laundry_order_id uuid,from_status text,to_status text,
        actor_auth_user_id uuid,actor_role text,note text);
      create table notifications(user_id uuid,title text,message text,type text,reference_id uuid);
      alter table users enable row level security;
      alter table laundry_orders enable row level security;
      create function public.get_my_user_id() returns uuid language sql stable security definer
        set search_path=public as $$select id from public.users
          where auth_user_id=auth.uid() and status='active' limit 1$$;
      create function public.get_my_role() returns text language sql stable security definer
        set search_path=public as $$select role from public.users
          where auth_user_id=auth.uid() and status='active' limit 1$$;
      grant usage on schema public,auth to authenticated,anon;
      insert into users values
        ('${id(1)}','${id(101)}','shop','active'),
        ('${id(2)}','${id(102)}','shop','active'),
        ('${id(3)}','${id(103)}','customer','active');
      insert into shop_profiles values
        ('${id(11)}','${id(1)}','active',null),
        ('${id(12)}','${id(2)}','active',null);
      insert into laundry_hubs values
        ('${id(21)}','${id(11)}'),('${id(22)}','${id(12)}');
      insert into laundry_shop_settings values ('${id(21)}',100),('${id(22)}',100);
      insert into laundry_orders(id,hub_id,customer_id,status,service_id,pricing_type_snapshot,
        unit_price_snapshot,delivery_fee_total_snapshot) values
        ('${id(31)}','${id(21)}','${id(3)}','washing','${id(41)}','per_kg',80,20),
        ('${id(32)}','${id(21)}','${id(3)}','washing','${id(41)}','per_kg',80,20),
        ('${id(33)}','${id(22)}','${id(3)}','washing','${id(41)}','per_kg',80,20),
        ('${id(34)}','${id(21)}','${id(3)}','washing','${id(41)}','per_kg',80,20);`);
    await db.exec(columns);
    await db.exec(functionSql);
    eq((await state(31)).gp_rate_snapshot, null, 'existing Laundry order has no fabricated 10% GP');
    await as(101);
    await denied(action(31, 2), /commission snapshot required/, 'missing rate must reject settlement');
    await db.exec('reset role');
    eq(await counts(), { events: 0, notices: 0 }, 'missing rate must not emit side effects');
    await db.exec(`update laundry_orders set gp_rate_snapshot=0.10 where id='${id(32)}';
      update laundry_orders set gp_rate_snapshot=1.5 where id='${id(34)}';
      update laundry_orders set gp_rate_snapshot=0.10 where id='${id(33)}';`);
    await as(101);
    await denied(action(34, 2), /commission snapshot required/, 'out of range GP must reject settlement');
    await denied(action(33, 2), /laundry order unavailable/, 'other shop cannot settle');
    const first = (await action(32, 2)).rows[0].result;
    eq(first.status, 'ready_return', 'valid owner advances state');
    eq(first.replayed, false, 'first settlement is not replay');
    await db.exec('reset role');
    const settled = await state(32);
    eq(settled.final_amount, 160, 'service amount');
    eq(settled.final_total_amount, 180, 'delivery included in total');
    eq(settled.platform_fee_amount, 18, 'GP snapshot applied exactly once');
    eq(settled.shop_net_amount, 162, 'shop net reconciles');
    eq(await counts(), { events: 1, notices: 1 }, 'one event and notice for successful settlement');
    await as(101);
    const replay = (await action(32, 2)).rows[0].result;
    eq(replay.replayed, true, 'same request replay is idempotent');
    await denied(action(32, 3), /already completed with different details/, 'conflicting replay denied');
    await db.exec('reset role');
    eq(await counts(), { events: 1, notices: 1 }, 'replays do not duplicate side effects');
    await as(103);
    await denied(action(31, 2), /shop login required/, 'customer role denied');
    await as(null);
    await denied(action(31, 2), /shop login required/, 'missing session denied');
    await db.exec('reset role; set role anon');
    await denied(action(31, 2), /permission denied/, 'anonymous execute denied');
    console.log(JSON.stringify({checks, failures: 0, scope: 'actual isolated Laundry manage state/finance/ownership/replay; Production untouched'}));
  } finally {
    await db.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
