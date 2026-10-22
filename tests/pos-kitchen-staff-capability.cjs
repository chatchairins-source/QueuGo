const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');

const root = path.resolve(__dirname, '..');
const migrationPath = fs.readdirSync(path.join(root, 'supabase/migrations'))
  .find(name => name.endsWith('_pos_kitchen_staff_capability_guard.sql'));
assert.ok(migrationPath, 'Kitchen staff capability migration exists');
const migration = fs.readFileSync(path.join(root, 'supabase/migrations', migrationPath), 'utf8');
assert.match(migration, /create or replace function public\.pos_allowed/);
assert.match(migration, /create or replace function public\.pos_create_role_invite/);
assert.match(migration, /create or replace function public\.pos_join_shop/);
assert.match(migration, /create or replace function public\.pos_set_staff_role/);
assert.doesNotMatch(migration, /\b(create table|drop|grant|revoke|delete from)\b/i);

(async () => {
  const db = new PGlite();
  const food = '10000000-0000-4000-8000-000000000001';
  const market = '10000000-0000-4000-8000-000000000002';
  const owner = '20000000-0000-4000-8000-000000000001';
  const kitchenStaff = '20000000-0000-4000-8000-000000000002';
  const invitee = '20000000-0000-4000-8000-000000000003';
  const secret = 'a'.repeat(48);
  await db.exec(`
    create schema auth;
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
    $$;
    create table public.shop_profiles(id uuid primary key, public_category text, metadata jsonb default '{}'::jsonb);
    create table public.pos_staff(
      user_id uuid primary key, shop_id uuid not null, display_name text not null,
      permissions jsonb not null default '{}'::jsonb, staff_role text not null,
      active boolean not null default true
    );
    create table public.pos_invites(
      id uuid primary key default gen_random_uuid(), shop_id uuid not null, secret_hash text not null,
      staff_role text not null, permissions jsonb not null default '{}'::jsonb,
      expires_at timestamptz not null default now() + interval '1 day', used_by uuid
    );
    create function public.pos_my_shop() returns uuid language sql stable as $$
      select nullif(current_setting('app.shop_id', true), '')::uuid
    $$;
    create function public.pos_is_owner() returns boolean language sql stable as $$
      select coalesce(current_setting('app.is_owner', true) = 'true', false)
    $$;
    insert into public.shop_profiles(id, public_category) values ('${food}', 'food'), ('${market}', 'market');
    insert into public.pos_staff(user_id, shop_id, display_name, staff_role)
      values ('${kitchenStaff}', '${market}', 'Kitchen', 'KITCHEN');
  `);
  await db.exec(migration);
  await db.exec(`select set_config('app.shop_id','${market}',false);select set_config('app.is_owner','false',false);select set_config('request.jwt.claim.sub','${kitchenStaff}',false);`);
  const retailKitchen = await db.query(`select public.pos_allowed('cook_order') as cook,public.pos_allowed('ready_order') as ready`);
  assert.deepEqual(retailKitchen.rows[0], { cook: false, ready: false }, 'legacy Kitchen role cannot invoke Kitchen permissions for Market');

  await db.exec(`update public.pos_staff set shop_id='${food}' where user_id='${kitchenStaff}';`);
  await db.exec(`select set_config('app.shop_id','${food}',false);`);
  const foodKitchen = await db.query(`select public.pos_allowed('cook_order') as cook,public.pos_allowed('ready_order') as ready`);
  assert.deepEqual(foodKitchen.rows[0], { cook: true, ready: true }, 'Kitchen staff keeps Food permissions');

  await db.exec(`update public.pos_staff set shop_id='${market}' where user_id='${kitchenStaff}';`);
  await db.exec(`select set_config('app.is_owner','true',false);select set_config('request.jwt.claim.sub','${owner}',false);select set_config('app.shop_id','${market}',false);`);
  await assert.rejects(db.query(`select public.pos_create_role_invite('${secret}','KITCHEN')`), /kitchen role is not available/);
  await assert.rejects(db.query(`select public.pos_set_staff_role('${kitchenStaff}','KITCHEN',true)`), /kitchen role is not available/);
  const roleChange = await db.query(`select public.pos_set_staff_role('${kitchenStaff}','CASHIER',true)`);
  assert.equal(roleChange.rows.length, 1, 'retail owner can still assign non-Kitchen roles');

  await db.exec(`select set_config('app.shop_id','${food}',false);`);
  await db.query(`select public.pos_create_role_invite('${secret}','KITCHEN')`);
  const invite = await db.query(`select id from public.pos_invites where secret_hash=encode(sha256(convert_to('${secret}','utf8')),'hex')`);
  assert.equal(invite.rows.length, 1, 'Food owner can create a Kitchen invite');

  await db.exec(`update public.shop_profiles set public_category='market' where id='${food}';select set_config('app.shop_id','${food}',false);select set_config('app.is_owner','false',false);select set_config('request.jwt.claim.sub','${invitee}',false);`);
  await assert.rejects(db.query(`select public.pos_join_shop('${secret}','Kitchen Staff')`), /kitchen role is not available/);
  const staffAfterRejectedJoin = await db.query(`select count(*)::int as count from public.pos_staff where user_id='${invitee}' and shop_id='${food}'`);
  assert.equal(staffAfterRejectedJoin.rows[0].count, 0, 'stale Food invite cannot join after the shop switches to Market');

  await db.close();
  console.log(JSON.stringify({ checks: 11, status: 'PASS', scope: 'Food/Market Kitchen staff RPC authorization' }));
})().catch(error => {
  console.error(error);
  process.exit(1);
});
