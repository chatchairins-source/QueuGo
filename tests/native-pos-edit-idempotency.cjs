const fs = require('fs'), path = require('path'), assert = require('assert');
const { PGlite } = require('@electric-sql/pglite');
const root = path.resolve(__dirname, '..');
(async () => {
  const db = new PGlite(); let checks = 0;
  const equal = (a, b) => { assert.deepEqual(a, b); checks++; };
  const rejected = async (sql, message) => { await assert.rejects(db.exec(sql), message); checks++; };
  const shop = '00000000-0000-0000-0000-000000000001';
  const actor = '00000000-0000-0000-0000-000000000002';
  const bill = '00000000-0000-0000-0000-000000000003';
  const product = '00000000-0000-0000-0000-000000000004';
  const request = n => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`;
  await db.exec(`
    create role anon; create role authenticated; create schema auth;
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    create function public.pos_my_shop() returns uuid language sql stable as $$
      select nullif(current_setting('test.shop',true),'')::uuid $$;
    create function public.pos_allowed(text) returns boolean language sql stable as $$
      select coalesce(current_setting('test.allowed',true),'false')::boolean $$;
    create table public.shop_profiles(id uuid primary key);
    create table public.orders(id uuid primary key, shop_id uuid, sales_channel text,
      order_type text, table_id uuid, payment_status text, status text, kitchen_status text,
      subtotal numeric default 0, discount_amount numeric default 0, total_amount numeric default 0,
      updated_at timestamptz);
    create table public.pos_tables(id uuid,shop_id uuid,active boolean);
    create table public.products(id uuid,shop_id uuid,name text,pos_available boolean,
      available boolean,pos_price numeric,price numeric);
    create table public.order_items(id uuid primary key default gen_random_uuid(),order_id uuid,
      product_id uuid,item_name text,description text,quantity integer,unit_price numeric,
      total_price numeric,created_at timestamptz default now());
    create table public.pos_request_keys(request_id uuid primary key,shop_id uuid,actor_id uuid,
      order_id uuid,created_at timestamptz default now());
    insert into public.shop_profiles values ('${shop}');
    insert into public.orders(id,shop_id,sales_channel,order_type,payment_status,status,kitchen_status)
      values ('${bill}','${shop}','POS','TAKEAWAY','UNPAID','pending','NEW');
    insert into public.products values ('${product}','${shop}','item',true,true,50,50);
    select set_config('request.jwt.claim.sub','${actor}',false);
    select set_config('test.shop','${shop}',false);
    select set_config('test.allowed','true',false);
  `);
  // Execute the captured deployed POS function, not a replacement mutation implementation.
  await db.exec(fs.readFileSync(path.join(root, 'ops/pos-edit-idempotency-schema-baseline-20261010.sql'), 'utf8'));
  const migration = fs.readFileSync(path.join(root, 'supabase/migrations/20261010043000_pos_edit_bill_idempotency.sql'), 'utf8');
  await db.exec(migration); await db.exec(migration);
  const call = (key, quantity, note = 'note') => `select public.pos_edit_bill_once('${key}','${bill}','TAKEAWAY',null,'${product}',${quantity},'${note}');`;
  const quantity = async () => (await db.query('select coalesce(sum(quantity),0)::integer as qty from public.order_items')).rows[0].qty;
  await db.exec(call(request(10), 1)); await db.exec(call(request(10), 1));
  equal(await quantity(), 1);
  await db.exec(call(request(11), 1)); equal(await quantity(), 2);
  await rejected(call(request(10), 2), /request id already in use/); equal(await quantity(), 2);
  await rejected(call(request(10), 1, 'different'), /request id already in use/);
  await db.exec(`select set_config('request.jwt.claim.sub','${request(99)}',false)`);
  await rejected(call(request(10), 1), /request id already in use/);
  await db.exec(`select set_config('request.jwt.claim.sub','${actor}',false); select set_config('test.allowed','false',false)`);
  await rejected(call(request(10), 1), /POS access denied/);
  await db.exec("select set_config('test.allowed','true',false)");
  await db.exec(call(request(12), -1)); await db.exec(call(request(12), -1)); equal(await quantity(), 1);
  await db.exec(`update public.orders set payment_status='PAID' where id='${bill}'`);
  await db.exec(call(request(11), 1)); equal(await quantity(), 1);
  await rejected(call(request(13), 1), /bill not editable/);
  equal((await db.query(`select count(*)::integer as n from public.pos_edit_request_keys where request_id='${request(13)}'`)).rows[0].n, 0);
  equal((await db.query("select has_function_privilege('anon','public.pos_edit_bill_once(uuid,uuid,text,uuid,uuid,integer,text)','EXECUTE') as allowed")).rows[0].allowed, false);
  equal((await db.query("select has_function_privilege('authenticated','public.pos_edit_bill_once(uuid,uuid,text,uuid,uuid,integer,text)','EXECUTE') as allowed")).rows[0].allowed, true);
  await rejected('set role authenticated; select * from public.pos_edit_request_keys;', /permission denied/);
  await db.exec('reset role');
  equal((await db.query("select relrowsecurity from pg_class where oid='public.pos_edit_request_keys'::regclass")).rows[0].relrowsecurity, true);
  await db.close();
  console.log(JSON.stringify({checks, failures:0, scope:'isolated SQL replay, payload ownership, permission revocation, rollback and ledger access; live JWT/concurrency not certified'}));
})().catch(error => { console.error(error); process.exit(1); });
