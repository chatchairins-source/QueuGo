'use strict';
// Runs the actual Laundry placement RPC replay branch against isolated PostgreSQL.
// No Production connection or synthetic order is sent to a live backend.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { PGlite } = require('@electric-sql/pglite');
const source = fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql', 'utf8');
const start = source.indexOf('CREATE OR REPLACE FUNCTION public.queuego_place_laundry_order_v2(');
const end = source.indexOf('-- Archived platform-Rider endpoints', start);
assert(start >= 0 && end > start, 'original Laundry placement RPC must exist');
const rpcSql = source.slice(start, end);
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');

(async () => {
  const db = new PGlite();
  let checks = 0;
  const eq = (a, b, label) => { assert.deepEqual(a, b, label); checks++; };
  const denied = async (promise, label, pattern = /request id already used/) => {
    await assert.rejects(promise, pattern, label); checks++;
  };
  const as = async n => {
    await db.exec('reset role');
    await db.query("select set_config('test.uid',$1,false)", [n ? id(n) : '']);
    await db.exec('set role authenticated');
  };
  const place = (overrides = {}) => {
    const v = {
      request: id(81), hub: id(21), service: id(41),
      address: ' 12 Main Road ', lat: 14.1, lng: 102.1,
      quantity: 2, note: ' Wash gently ', ...overrides
    };
    return db.query(
      'select public.queuego_place_laundry_order_v2($1,$2,$3,$4,$5,$6,$7,$8) as result',
      [v.request, v.hub, v.service, v.address, v.lat, v.lng, v.quantity, v.note]
    );
  };
  try {
    await db.exec(`create role anon; create role authenticated; create role service_role;
      create schema auth;
      create function auth.uid() returns uuid language sql stable as
        $$select nullif(current_setting('test.uid',true),'')::uuid$$;
      create function public.queuego_feature_enabled(text,timestamptz)
        returns boolean language sql stable as $$select true$$;
      create function public.queuego_assert_service_route(double precision,double precision,
        double precision,double precision,text) returns numeric language sql as $$select 1::numeric$$;
      create table public.users(id uuid primary key,auth_user_id uuid,role text,status text);
      create table public.shop_profiles(id uuid primary key,user_id uuid,status text,
        latitude double precision,longitude double precision);
      create table public.laundry_hubs(id uuid primary key,shop_id uuid,active boolean);
      create table public.laundry_services(id uuid primary key,hub_id uuid,active boolean,
        pricing_type text,price numeric,code text,name text);
      create table public.laundry_shop_settings(hub_id uuid primary key,enabled boolean,
        accepts_pickup boolean,accepts_return boolean,delivery_fee_mode text,
        round_trip_fee numeric,base_pickup_fee numeric,return_fee numeric,minimum_order numeric);
      create table public.laundry_orders(
        id uuid primary key,customer_id uuid,hub_id uuid,status text,service_type text,
        pickup_address text,pickup_latitude double precision,pickup_longitude double precision,
        note text,service_id uuid,service_name_snapshot text,pricing_type_snapshot text,
        unit_price_snapshot numeric,pickup_fee_snapshot numeric,return_fee_snapshot numeric,
        round_trip_fee_snapshot numeric,estimated_amount numeric,request_key uuid unique,
        estimated_quantity numeric,delivery_fee_mode_snapshot text,
        delivery_fee_total_snapshot numeric,estimated_total_amount numeric,order_number text);
      create table public.laundry_order_events(laundry_order_id uuid,from_status text,to_status text,
        actor_auth_user_id uuid,actor_role text,note text);
      create table public.notifications(user_id uuid,title text,message text,type text,reference_id uuid);
      grant usage on schema public,auth to authenticated,anon;
      insert into public.users values
        ('${id(3)}','${id(103)}','customer','active'),
        ('${id(4)}','${id(104)}','customer','active');
      insert into public.laundry_orders(id,customer_id,hub_id,status,pickup_address,
        pickup_latitude,pickup_longitude,note,service_id,request_key,estimated_quantity,
        estimated_total_amount,order_number)
      values('${id(31)}','${id(3)}','${id(21)}','pending','12 Main Road',
        14.1,102.1,'Wash gently','${id(41)}','${id(81)}',2,180,'L-31');`);
    await db.exec(rpcSql);
    await db.exec(`revoke all on function public.queuego_place_laundry_order_v2(
      uuid,uuid,uuid,text,double precision,double precision,numeric,text) from public,anon;
      grant execute on function public.queuego_place_laundry_order_v2(
      uuid,uuid,uuid,text,double precision,double precision,numeric,text) to authenticated;`);
    await as(103);
    const result = (await place()).rows[0].result;
    eq(result.id, id(31), 'identical request key returns original order');
    eq(result.replayed, true, 'identical request is a safe replay');
    for (const [field, value] of [
      ['hub', id(22)], ['service', id(42)], ['address', 'Other Street'],
      ['lat', 14.2], ['lng', 102.2], ['quantity', 3],
      ['quantity', null], ['note', 'Different instructions'], ['note', null]
    ]) {
      await denied(place({ [field]: value }), 'changed ' + field + ' rejected');
    }
    await as(104);
    await denied(place(), 'request key cannot be reused by another customer');
    await db.exec(`reset role;
      insert into public.laundry_orders(id,customer_id,hub_id,status,pickup_address,
        pickup_latitude,pickup_longitude,note,service_id,request_key,estimated_quantity,
        estimated_total_amount,order_number)
      values('${id(32)}',null,'${id(21)}','pending','12 Main Road',
        14.1,102.1,'Wash gently','${id(41)}','${id(82)}',2,180,'L-32');`);
    await as(103);
    await denied(place({ request: id(82) }), 'legacy null-owner request key must fail closed');
    await db.exec('reset role');
    const counts = (await db.query(`select
      (select count(*)::int from public.laundry_orders) as orders,
      (select count(*)::int from public.laundry_order_events) as events,
      (select count(*)::int from public.notifications) as notices`)).rows[0];
    eq(counts, { orders: 2, events: 0, notices: 0 }, 'replay and conflicts are side-effect free');
    await db.exec('set role anon');
    await denied(place(), 'anonymous cannot invoke placement RPC', /permission denied/);
    console.log(JSON.stringify({ checks, failures: 0, scope: 'isolated actual Laundry request-key replay, ownership and no side effects' }));
  } finally {
    await db.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
