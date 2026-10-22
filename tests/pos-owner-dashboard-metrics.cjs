const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');

const root = path.resolve(__dirname, '..');
const migrationName = fs.readdirSync(path.join(root, 'supabase/migrations'))
  .find(name => name.endsWith('_pos_owner_dashboard_metrics.sql'));
assert.ok(migrationName, 'POS dashboard metrics migration exists');
const migration = fs.readFileSync(path.join(root, 'supabase/migrations', migrationName), 'utf8');

(async () => {
  const db = new PGlite();
  const shop = '10000000-0000-4000-8000-000000000001';
  const otherShop = '10000000-0000-4000-8000-000000000002';
  await db.exec(`
    create role anon;
    create role authenticated;
    create table public.orders(
      id uuid primary key,shop_id uuid not null,status text not null,sales_channel text not null,
      order_type text not null,payment_status text not null,payment_method text,
      discount_amount numeric not null default 0,total_amount numeric not null,
      paid_at timestamptz,created_at timestamptz not null default now()
    );
    create table public.pos_register_cash_events(
      id uuid primary key default gen_random_uuid(),shop_id uuid not null,event_type text not null,
      amount numeric not null,created_at timestamptz not null default now()
    );
    create function public.pos_my_shop() returns uuid language sql stable as $$ select '${shop}'::uuid $$;
    create function public.pos_is_owner() returns boolean language sql stable as $$
      select coalesce(current_setting('test.owner',true)='true',false)
    $$;
    create function public.pos_owner_dashboard(p_days integer default 1) returns jsonb
      language sql stable security definer set search_path=public,pg_temp as $$ select '{}'::jsonb $$;
    revoke all on function public.pos_owner_dashboard(integer) from public,anon;
    grant execute on function public.pos_owner_dashboard(integer) to authenticated;
    insert into public.orders(id,shop_id,status,sales_channel,order_type,payment_status,payment_method,discount_amount,total_amount,paid_at)
    values
      ('20000000-0000-4000-8000-000000000001','${shop}','completed','POS','DINE_IN','PAID','cash',10,100,now()-interval '2 hours'),
      ('20000000-0000-4000-8000-000000000002','${shop}','completed','POS','TAKEAWAY','PAID','card',0,50,now()-interval '2 hours'),
      ('20000000-0000-4000-8000-000000000003','${shop}','completed','DELIVERY','TAKEAWAY','PAID','cash',0,999,now()-interval '2 hours'),
      ('20000000-0000-4000-8000-000000000004','${shop}','pending','POS','TAKEAWAY','UNPAID','cash',0,80,now()-interval '2 hours'),
      ('20000000-0000-4000-8000-000000000005','${shop}','cancelled','POS','DINE_IN','UNPAID',null,0,30,null),
      ('20000000-0000-4000-8000-000000000006','${shop}','completed','POS','DINE_IN','PAID','cash',0,40,
        (((now() at time zone 'Asia/Bangkok')::date-interval '8 days')::date::timestamp at time zone 'Asia/Bangkok')+interval '12 hours'),
      ('20000000-0000-4000-8000-000000000007','${otherShop}','completed','POS','DINE_IN','PAID','cash',0,500,now()-interval '2 hours');
    insert into public.pos_register_cash_events(shop_id,event_type,amount,created_at)
      values('${shop}','refund',-15,now()-interval '1 hour'),('${otherShop}','refund',-500,now()-interval '1 hour');
  `);
  await db.exec(migration);
  await db.exec("select set_config('test.owner','true',false)");
  const result = (await db.query('select public.pos_owner_dashboard(7) as data')).rows[0].data;
  assert.equal(Number(result.DINE_IN), 100);
  assert.equal(Number(result.TAKEAWAY), 50);
  assert.equal(Number(result.orders), 2);
  assert.equal(Number(result.average_ticket), 75);
  assert.equal(Number(result.cash_sales), 100);
  assert.equal(Number(result.discounts), 10);
  assert.equal(Number(result.refunds), 15);
  assert.equal(Number(result.voids), 1);
  assert.equal(Number(result.previous_period_sales), 40);
  assert.equal(Number(result.change_percent), 275);
  assert.equal(result.hourly_sales.length, 1);
  assert.equal(Number(result.hourly_sales[0].sales), 150);
  const acl = (await db.query(`select has_function_privilege('public','public.pos_owner_dashboard(integer)','execute') as public_exec,
    has_function_privilege('anon','public.pos_owner_dashboard(integer)','execute') as anon_exec,
    has_function_privilege('authenticated','public.pos_owner_dashboard(integer)','execute') as auth_exec`)).rows[0];
  assert.deepEqual(acl, { public_exec: false, anon_exec: false, auth_exec: true });
  await db.exec("select set_config('test.owner','false',false)");
  await assert.rejects(db.query('select public.pos_owner_dashboard(7)'), /owner only/);
  await db.close();
  console.log(JSON.stringify({ checks: 14, failures: 0, scope: 'POS-only sales, average ticket, cash, discounts, refunds, voids, prior period, hourly totals, owner gate and preserved ACL; isolated SQL only' }));
})().catch(error => { console.error(error); process.exit(1); });
