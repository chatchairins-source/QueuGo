const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');

const root = path.resolve(__dirname, '..');
const web = fs.readFileSync(path.join(root, 'merchant/pos-table-v2.js'), 'utf8');
const migrationPath = fs.readdirSync(path.join(root, 'supabase/migrations')).find(name => name.endsWith('_pos_shop_type_kitchen_rules.sql'));
assert.ok(migrationPath, 'shop-type kitchen migration exists');
const migration = fs.readFileSync(path.join(root, 'supabase/migrations', migrationPath), 'utf8');

const policy = web.match(/const kitchenEnabled=\(\)=>[^;]+;/)?.[0];
assert.ok(policy, 'Web exposes the canonical kitchen category policy');
assert.match(web, /const allowedStaffRoles=\(\)=>kitchenEnabled\(\)\?\['WAITER','CASHIER','KITCHEN'\]:\['WAITER','CASHIER'\]/, 'Kitchen role is only offered to food and cafe shops');
assert.match(web, /if\(!allowedStaffRoles\(\)\.includes\(role\)\)return message\('ประเภทร้านนี้ไม่มีตำแหน่งพนักงานครัว'\)/, 'tampered Kitchen invite selection is rejected before RPC');
assert.match(web, /const roles=allowedStaffRoles\(\),role=prompt\('หน้าที่ '\+roles\.join\(' \/ '\)/, 'staff role editor offers only the shop capability roles');
const allowsKitchen = category => new Function('state', `${policy};return kitchenEnabled();`)({ shopCategory: category });
for (const category of ['food', 'cafe']) assert.equal(allowsKitchen(category), true, `${category} keeps Kitchen`);
for (const category of ['grocery', 'market', '', null]) assert.equal(allowsKitchen(category), false, `${category} hides Kitchen`);

assert.match(web, /shop_profiles\?select=shop_name,public_category,metadata/);
assert.match(web, /x\[0\]!=='kitchen'\|\|kitchenEnabled\(\)/);
assert.match(web, /!kitchenEnabled\(\)\|\|\['READY','SERVED'\]\.includes\(bill\.kitchen_status\)/);
assert.match(web, /if\(!kitchenEnabled\(\)&&state\.view==='kitchen'\)state\.view='counter'/);
assert.match(migration, /elsif not v_kitchen_enabled then\s+raise exception 'kitchen is not available for this shop category'/);
assert.match(migration, /v_order\.kitchen_status not in \('READY','SERVED'\)/);
assert.match(migration, /not coalesce\(v_kitchen_enabled,false\).*'NEW','SENT_TO_KITCHEN','COOKING','READY','SERVED'/s);
assert.match(migration, /pos_register_shifts/);
assert.match(migration, /pos_allowed\('close_bill'\)/);
assert.match(migration, /CREATE OR REPLACE preserves the production owner and existing EXECUTE ACLs/);
assert.doesNotMatch(migration, /\b(grant|revoke|drop|delete)\b/i);

let checks = 19;
async function verifyDatabaseBehavior() {
  const db = new PGlite();
  const shopMarket = '00000000-0000-0000-0000-000000000001';
  const shopFood = '00000000-0000-0000-0000-000000000002';
  const cashier = '00000000-0000-0000-0000-000000000099';
  const marketOrder = '00000000-0000-0000-0000-000000000011';
  const foodOrder = '00000000-0000-0000-0000-000000000012';
  await db.exec(`
    create schema auth;
    create function auth.uid() returns uuid language sql stable as $$ select '${cashier}'::uuid $$;
    create table public.shop_profiles(id uuid,public_category text,metadata jsonb);
    create table public.orders(id uuid,shop_id uuid,sales_channel text,status text,payment_status text,
      kitchen_status text,bill_status text,payment_method text,cash_tendered numeric,subtotal numeric,total_amount numeric,
      pos_register_shift_id uuid,cash_change numeric,paid_at timestamptz,updated_at timestamptz);
    create table public.order_items(order_id uuid,pos_batch int,pos_kitchen_status text);
    create table public.payments(order_id uuid,amount numeric,payment_method text,status text,paid_at timestamptz);
    create table public.pos_register_shifts(id uuid,shop_id uuid,cashier_user_id uuid,closed_at timestamptz);
    create function public.pos_my_shop() returns uuid language sql stable as $$ select current_setting('app.shop_id')::uuid $$;
    create function public.pos_allowed(text) returns boolean language sql stable as $$ select true $$;
  `);
  await db.exec(migration);
  await db.exec(`
    insert into public.shop_profiles values
      ('${shopMarket}','market','{}'),('${shopFood}','food','{}');
    insert into public.orders(id,shop_id,sales_channel,status,payment_status,kitchen_status,bill_status,subtotal,total_amount)
    values ('${marketOrder}','${shopMarket}','POS','pending','UNPAID','NEW','OPEN',25,25),
           ('${foodOrder}','${shopFood}','POS','pending','UNPAID','NEW','OPEN',25,25);
    insert into public.order_items values ('${foodOrder}',1,'NEW');
    insert into public.pos_register_shifts values
      ('00000000-0000-0000-0000-000000000021','${shopMarket}','${cashier}',null),
      ('00000000-0000-0000-0000-000000000022','${shopFood}','${cashier}',null);
  `);
  await db.exec(`set app.shop_id='${shopMarket}'`);
  await assert.rejects(db.query(`select public.pos_bill_action('${marketOrder}','send',null)`), /kitchen is not available/);
  checks++;
  await db.query(`select public.pos_take_payment('${marketOrder}','cash',30)`);
  const retail = await db.query(`select payment_status,bill_status,cash_change from public.orders where id='${marketOrder}'`);
  assert.deepEqual(retail.rows[0], { payment_status: 'PAID', bill_status: 'CLOSED', cash_change: '5' });
  checks++;
  await db.exec(`set app.shop_id='${shopFood}'`);
  await assert.rejects(db.query(`select public.pos_take_payment('${foodOrder}','cash',30)`), /bill not ready for payment/);
  checks++;
  await db.query(`select public.pos_bill_action('${foodOrder}','send',null)`);
  const restaurant = await db.query(`select kitchen_status from public.orders where id='${foodOrder}'`);
  assert.equal(restaurant.rows[0].kitchen_status, 'SENT_TO_KITCHEN');
  checks++;
  await db.close();
}

verifyDatabaseBehavior().then(() => {
  console.log(JSON.stringify({ checks: 23, status: 'PASS', scope: 'Web shop-category Kitchen visibility, direct POS checkout, and server-side database behavior' }));
}).catch(error => {
  console.error(error);
  process.exit(1);
});
