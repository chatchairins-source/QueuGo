'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');
const {PGlite}=require('@electric-sql/pglite');
const uuid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
async function isolatedDatabase(){
 const target=process.env.QG_POS_TEST_DATABASE_URL;
 if(!target)return {db:new PGlite(),engine:'PGlite'};
 const url=new URL(target);
 // This destructive schema/role fixture must never point at a hosted database.
 assert(['postgres:','postgresql:'].includes(url.protocol)&&
  ['127.0.0.1','localhost'].includes(url.hostname)&&url.port==='55448'&&
  url.pathname==='/qg_pos_test_ci'&&!url.search,'only the disposable local POS CI database is allowed');
 const {Client}=require('pg');
 const client=new Client({connectionString:target});
 await client.connect();
 return {db:{exec:sql=>client.query(sql),query:(sql,args)=>client.query(sql,args),close:()=>client.end()},engine:'PostgreSQL'};
}
async function main(){
 const {db,engine}=await isolatedDatabase();let checks=0;
 try{
  await db.exec("CREATE SCHEMA auth; CREATE ROLE anon; CREATE ROLE authenticated;");
  await db.exec("CREATE TABLE auth.users(id uuid PRIMARY KEY);");
  await db.exec(`INSERT INTO auth.users(id) VALUES('${uuid(1)}'),('${uuid(2)}'),('${uuid(3)}'),('${uuid(4)}');
   CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
   CREATE TABLE public.users(id uuid PRIMARY KEY,auth_user_id uuid,role text,status text);
   INSERT INTO public.users VALUES('${uuid(11)}','${uuid(1)}','shop','active'),('${uuid(12)}','${uuid(2)}','shop','active');
   CREATE TABLE public.shop_profiles(id uuid PRIMARY KEY,user_id uuid,status text);
   INSERT INTO public.shop_profiles VALUES('${uuid(20)}','${uuid(11)}','active'),('${uuid(21)}','${uuid(12)}','active');
   CREATE TABLE public.pos_staff(shop_id uuid,user_id uuid,active boolean);
   INSERT INTO public.pos_staff VALUES('${uuid(20)}','${uuid(3)}',true);
   GRANT USAGE ON SCHEMA auth TO anon,authenticated;
   SELECT set_config('request.jwt.claim.sub','${uuid(1)}',false);`);
  // Helper bodies captured READ ONLY from Production on 2026-11-22. Real
  // PostgreSQL roles/RLS execute below; the session identity and rows are fixtures,
  // not proof of live JWT authorization, full checkout or Production parity.
  await db.exec(`CREATE FUNCTION public.pos_is_owner() RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public','pg_temp' AS $$
   select exists(select 1 from public.shop_profiles sp join public.users u on u.id=sp.user_id
   where u.auth_user_id=(select auth.uid()) and u.role='shop' and u.status='active' and sp.status='active') $$;
   CREATE FUNCTION public.pos_my_shop() RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public','pg_temp' AS $$
   select coalesce((select sp.id from public.shop_profiles sp join public.users u on u.id=sp.user_id
     where u.auth_user_id=(select auth.uid()) and u.role='shop' and u.status='active' and sp.status='active' limit 1),
    (select s.shop_id from public.pos_staff s join public.shop_profiles sp on sp.id=s.shop_id
     join public.users u on u.id=sp.user_id where s.user_id=(select auth.uid()) and s.active
     and sp.status='active' and u.status='active' limit 1)) $$;`);
  await db.exec("CREATE TABLE public.orders(id uuid PRIMARY KEY,shop_id uuid REFERENCES public.shop_profiles(id),order_type text,sales_channel text,customer_id uuid,rider_id uuid,delivery_fee numeric DEFAULT 0,gp_rate numeric DEFAULT 0,gp_amount numeric DEFAULT 0,subtotal numeric DEFAULT 0,discount_amount numeric DEFAULT 0,total_amount numeric DEFAULT 0);");
  await db.exec("ALTER TABLE public.orders ADD CONSTRAINT pos_channel_integrity_check CHECK ((order_type IN ('DINE_IN','TAKEAWAY') AND sales_channel='POS' AND customer_id IS NULL AND rider_id IS NULL AND delivery_fee=0 AND gp_rate=0 AND gp_amount=0 AND subtotal>=0 AND total_amount=subtotal-discount_amount) OR (order_type NOT IN ('DINE_IN','TAKEAWAY') AND sales_channel IS DISTINCT FROM 'POS' AND discount_amount=0));");
  await db.query("INSERT INTO orders(id,shop_id,order_type,sales_channel,subtotal,total_amount) VALUES($1,$2,'TAKEAWAY','POS',100,100)",[uuid(101),uuid(20)]);
  const sql=fs.readFileSync('QueueGo-POS-Tax-Checkout-Integration-Migration.sql','utf8');
  assert.equal((sql.match(/security definer set search_path=''/g)||[]).length,2);checks++;
  assert(sql.includes('tax configuration snapshots are immutable'));checks++;
  await db.exec(fs.readFileSync('QueueGo-POS-Tax-Foundation-Migration.sql','utf8'));
  await db.exec(sql);
  const rls=await db.query("SELECT relrowsecurity FROM pg_class WHERE oid='public.pos_tax_settings'::regclass");
  assert.equal(rls.rows[0].relrowsecurity,true);checks++;
  const legacy=(await db.query('SELECT total_amount,pos_tax_enabled_snapshot FROM orders WHERE id=$1',[uuid(101)])).rows[0];
  assert.equal(Number(legacy.total_amount),100);assert.equal(legacy.pos_tax_enabled_snapshot,null);checks+=2;
  async function insert(n,amount){
   await db.query("INSERT INTO orders(id,shop_id,order_type,sales_channel,subtotal,total_amount) VALUES($1,$2,'TAKEAWAY','POS',$3,$3)",[uuid(n),uuid(20),amount]);
   return (await db.query('SELECT total_amount,pos_taxable_base,pos_tax_amount,pos_tax_enabled_snapshot,pos_tax_rate_snapshot FROM orders WHERE id=$1',[uuid(n)])).rows[0];
  }
  let row=await insert(102,100);
  assert.equal(Number(row.total_amount),100);assert.equal(row.pos_tax_enabled_snapshot,false);checks+=2;
  await db.query('SELECT public.pos_save_tax_settings(true,7,true,null,null,null)');
  row=await insert(103,107);
  assert.equal(Number(row.total_amount),107);assert.equal(Number(row.pos_taxable_base),100);assert.equal(Number(row.pos_tax_amount),7);checks+=3;
  await db.query('SELECT public.pos_save_tax_settings(true,7,false,null,null,null)');
  row=await insert(104,100);
  assert.equal(Number(row.total_amount),107);assert.equal(Number(row.pos_tax_amount),7);checks+=2;
  await assert.rejects(db.query('UPDATE orders SET pos_tax_rate_snapshot=8 WHERE id=$1',[uuid(104)]),/immutable/i);checks++;
  // Derived tax fields cannot be forged by updating only the derived columns.
  await db.query('UPDATE orders SET pos_tax_amount=50,total_amount=150 WHERE id=$1',[uuid(104)]);
  row=(await db.query('SELECT total_amount,pos_taxable_base,pos_tax_amount FROM orders WHERE id=$1',[uuid(104)])).rows[0];
  assert.equal(Number(row.total_amount),107);assert.equal(Number(row.pos_tax_amount),7);checks+=2;
  await db.query('UPDATE orders SET pos_taxable_base=1 WHERE id=$1',[uuid(104)]);
  row=(await db.query('SELECT pos_taxable_base FROM orders WHERE id=$1',[uuid(104)])).rows[0];
  assert.equal(Number(row.pos_taxable_base),100);checks++;
  // Inclusive totals also remain tied to the line net, never a supplied tax split.
  await db.query('UPDATE orders SET pos_tax_amount=40,pos_taxable_base=67 WHERE id=$1',[uuid(103)]);
  row=(await db.query('SELECT total_amount,pos_taxable_base,pos_tax_amount FROM orders WHERE id=$1',[uuid(103)])).rows[0];
  assert.equal(Number(row.total_amount),107);assert.equal(Number(row.pos_taxable_base),100);assert.equal(Number(row.pos_tax_amount),7);checks+=3;

  await db.query('UPDATE orders SET subtotal=200 WHERE id=$1',[uuid(104)]);
  row=(await db.query('SELECT total_amount,pos_tax_rate_snapshot FROM orders WHERE id=$1',[uuid(104)])).rows[0];
  assert.equal(Number(row.total_amount),214);assert.equal(Number(row.pos_tax_rate_snapshot),7);checks+=2;
  await assert.rejects(db.query('SELECT public.pos_save_tax_settings(true,-1,true,null,null,null)'),/invalid tax settings/i);checks++;
  await db.query('SELECT public.pos_save_tax_settings(false,7,true,null,null,null)');
  row=await insert(105,100);
  assert.equal(Number(row.total_amount),100);assert.equal(row.pos_tax_enabled_snapshot,false);checks+=2;
  // PostgreSQL numeric admits NaN; comparison-based CHECKs alone are insufficient.
  for(const [index,special] of ['NaN','Infinity','-Infinity'].entries()) {
   await assert.rejects(insert(106+index,special),/finite POS bill amounts required/i);checks++;
   await assert.rejects(db.query('UPDATE orders SET discount_amount=$2::numeric WHERE id=$1',[uuid(104),special]),/finite POS bill amounts required/i);checks++;
  }
  row=(await db.query('SELECT total_amount,discount_amount FROM orders WHERE id=$1',[uuid(104)])).rows[0];
  assert.equal(Number(row.total_amount),214);assert.equal(Number(row.discount_amount),0);checks+=2;

  async function actor(role,n){
   await db.exec('RESET ROLE');
   await db.query("SELECT set_config('request.jwt.claim.sub',$1,false)",[n===null?'':uuid(n)]);
   await db.exec('SET ROLE '+role);
  }
  const save='SELECT public.pos_save_tax_settings(true,7,false,null,null,null)';
  await actor('authenticated',1);
  assert.equal((await db.query('SELECT shop_id FROM pos_tax_settings')).rows.length,1);checks++;
  await db.query(save);
  await assert.rejects(db.query('UPDATE pos_tax_settings SET tax_rate=99'),/permission denied/i);checks++;
  await assert.rejects(db.query('DELETE FROM pos_tax_settings'),/permission denied/i);checks++;
  await assert.rejects(db.query('INSERT INTO pos_tax_settings(shop_id) VALUES($1)',[uuid(21)]),/permission denied/i);checks++;

  await actor('authenticated',2);
  assert.equal((await db.query('SELECT shop_id FROM pos_tax_settings')).rows.length,0);checks++;
  await db.query('SELECT public.pos_save_tax_settings(true,0,true,null,null,null)');
  let visible=(await db.query('SELECT shop_id,tax_rate FROM pos_tax_settings')).rows;
  assert.equal(visible.length,1);assert.equal(visible[0].shop_id,uuid(21));assert.equal(Number(visible[0].tax_rate),0);checks+=3;
  assert.equal((await db.query('SELECT shop_id FROM pos_tax_settings WHERE shop_id=$1',[uuid(20)])).rows.length,0);checks++;

  await actor('authenticated',3);
  visible=(await db.query('SELECT shop_id FROM pos_tax_settings')).rows;
  assert.equal(visible.length,1);assert.equal(visible[0].shop_id,uuid(20));checks+=2;
  await assert.rejects(db.query(save),/POS owner permission required/);checks++;
  await actor('authenticated',4);
  assert.equal((await db.query('SELECT shop_id FROM pos_tax_settings')).rows.length,0);checks++;
  await assert.rejects(db.query(save),/POS owner permission required/);checks++;
  await actor('authenticated',null);
  await assert.rejects(db.query(save),/POS owner permission required/);checks++;
  await actor('anon',null);
  await assert.rejects(db.query('SELECT * FROM pos_tax_settings'),/permission denied/i);checks++;
  await assert.rejects(db.query(save),/permission denied/i);checks++;

  await actor('postgres',1);
  // Defense in depth: even accidental table write grants must not create an
  // RLS write policy. Roll back all temporary grants and attempted writes.
  await db.exec('BEGIN; GRANT INSERT,UPDATE,DELETE ON pos_tax_settings TO authenticated; SET ROLE authenticated');
  assert.equal((await db.query('UPDATE pos_tax_settings SET tax_rate=99 RETURNING shop_id')).rows.length,0);checks++;
  assert.equal((await db.query('DELETE FROM pos_tax_settings RETURNING shop_id')).rows.length,0);checks++;
  await assert.rejects(db.query('INSERT INTO pos_tax_settings(shop_id) VALUES($1)',[uuid(22)]),/row-level security/i);checks++;
  await db.exec('ROLLBACK; RESET ROLE');
  visible=(await db.query('SELECT tax_rate FROM pos_tax_settings WHERE shop_id=$1',[uuid(20)])).rows;
  assert.equal(Number(visible[0].tax_rate),7);checks++;
  const history=(await db.query('SELECT total_amount,pos_tax_rate_snapshot FROM orders WHERE id=$1',[uuid(104)])).rows[0];
  assert.equal(Number(history.total_amount),214);assert.equal(Number(history.pos_tax_rate_snapshot),7);checks+=2;
  assert.equal((await db.query("SELECT has_table_privilege('authenticated','pos_tax_settings','UPDATE') AS allowed")).rows[0].allowed,false);checks++;

  // Compose the candidate tax trigger with the two actual Production POS order
  // trigger functions. The orders/audit table shape and server flag are fixtures;
  // this does not simulate or certify the complete Production checkout RPC.
  await db.exec(`CREATE TABLE public.pos_events(
   id uuid PRIMARY KEY DEFAULT gen_random_uuid(),shop_id uuid,order_id uuid,actor_id uuid,
   entity text,action text,before_state jsonb,after_state jsonb);
   ${JSON.parse(fs.readFileSync('tests/fixtures/pos-order-guards-prod-20261122.json','utf8')).definitions.map(row=>row.definition+';').join('\n')}
   CREATE TRIGGER pos_audit_orders AFTER INSERT OR DELETE OR UPDATE ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.pos_record_event();
   CREATE TRIGGER pos_orders_guard BEFORE INSERT OR DELETE OR UPDATE ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.pos_guard_write();`);
  for(const [name,expected] of [
   ['is_active_admin','0c97812c83ef42b869a119a22d969aaf'],
   ['pos_guard_write','24b325d54243e5ee81ac9a38864d4664'],
   ['pos_record_event','db6ba3e0b907a762faad7a24eee378a2']
  ]){
   const hash=(await db.query("SELECT md5(prosrc) AS hash FROM pg_proc WHERE oid=('public.'||$1||'()')::regprocedure",[name])).rows[0].hash;
   assert.equal(hash,expected,'captured trigger/helper body must remain byte-identical');checks++;
  }
  const auditCount=async()=>Number((await db.query('SELECT count(*) AS n FROM pos_events')).rows[0].n);
  await db.query("SELECT set_config('queuego.pos_rpc','',false)");
  await assert.rejects(insert(201,100),/POS records require server RPC/);checks++;
  assert.equal((await db.query('SELECT id FROM orders WHERE id=$1',[uuid(201)])).rows.length,0);checks++;
  assert.equal(await auditCount(),0);checks++;
  await assert.rejects(db.query('UPDATE orders SET subtotal=300 WHERE id=$1',[uuid(104)]),/POS records require server RPC/);checks++;
  assert.equal(Number((await db.query('SELECT total_amount FROM orders WHERE id=$1',[uuid(104)])).rows[0].total_amount),214);checks++;
  await assert.rejects(db.query('DELETE FROM orders WHERE id=$1',[uuid(104)]),/POS records require server RPC/);checks++;

  await db.query("SELECT set_config('queuego.pos_rpc','on',false)");
  row=await insert(202,100);
  assert.equal(Number(row.total_amount),107);checks++;
  let event=(await db.query('SELECT * FROM pos_events WHERE order_id=$1',[uuid(202)])).rows[0];
  assert.equal(event.action,'INSERT');assert.equal(event.actor_id,uuid(1));assert.equal(event.before_state,null);checks+=3;
  assert.equal(Number(event.after_state.total_amount),107);assert.equal(Number(event.after_state.pos_tax_amount),7);checks+=2;
  await db.query('UPDATE orders SET subtotal=200 WHERE id=$1',[uuid(202)]);
  event=(await db.query("SELECT * FROM pos_events WHERE order_id=$1 AND action='UPDATE'",[uuid(202)])).rows[0];
  assert.equal(Number(event.before_state.total_amount),107);assert.equal(Number(event.after_state.total_amount),214);checks+=2;
  assert.equal(Number(event.before_state.subtotal),100);assert.equal(Number(event.after_state.subtotal),200);checks+=2;
  assert.equal(await auditCount(),2);checks++;
  await assert.rejects(db.query('UPDATE orders SET pos_tax_rate_snapshot=0 WHERE id=$1',[uuid(202)]),/immutable/);checks++;
  assert.equal(await auditCount(),2);checks++;

  // A downstream audit failure must roll back the tax total AND the source
  // order change atomically, instead of publishing an unaudited financial edit.
  await db.exec(`CREATE FUNCTION public.reject_tax_audit_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
   BEGIN RAISE EXCEPTION 'isolated audit failure'; END $$;
   CREATE TRIGGER reject_tax_audit_fixture BEFORE INSERT ON pos_events
    FOR EACH ROW EXECUTE FUNCTION public.reject_tax_audit_fixture();`);
  await assert.rejects(db.query('UPDATE orders SET subtotal=300 WHERE id=$1',[uuid(202)]),/isolated audit failure/);checks++;
  row=(await db.query('SELECT subtotal,total_amount,pos_tax_amount FROM orders WHERE id=$1',[uuid(202)])).rows[0];
  assert.equal(Number(row.subtotal),200);assert.equal(Number(row.total_amount),214);assert.equal(Number(row.pos_tax_amount),14);checks+=3;
  assert.equal(await auditCount(),2);checks++;
  await db.exec('DROP TRIGGER reject_tax_audit_fixture ON pos_events; DROP FUNCTION public.reject_tax_audit_fixture()');
  // A non-POS order retains its existing totals and creates no POS audit entry.
  await db.query("SELECT set_config('queuego.pos_rpc','',false)");
  await db.query("INSERT INTO orders(id,shop_id,order_type,sales_channel,subtotal,total_amount) VALUES($1,$2,'QUEUEGO_DELIVERY','APP',100,100)",[uuid(203),uuid(20)]);
  row=(await db.query('SELECT total_amount,pos_tax_enabled_snapshot FROM orders WHERE id=$1',[uuid(203)])).rows[0];
  assert.equal(Number(row.total_amount),100);assert.equal(row.pos_tax_enabled_snapshot,null);checks+=2;
  assert.equal(await auditCount(),2);checks++;

  // Exercise the captured payment RPC itself, not a fake receipt or client guard.
  await db.exec(`ALTER TABLE shop_profiles ADD COLUMN public_category text DEFAULT 'market', ADD COLUMN metadata jsonb DEFAULT '{}';
   ALTER TABLE pos_staff ADD COLUMN permissions jsonb DEFAULT '{}', ADD COLUMN staff_role text DEFAULT 'CASHIER';
   ALTER TABLE orders ADD COLUMN status text DEFAULT 'pending', ADD COLUMN payment_status text DEFAULT 'UNPAID',
    ADD COLUMN kitchen_status text DEFAULT 'NEW', ADD COLUMN bill_status text DEFAULT 'OPEN', ADD COLUMN payment_method text,
    ADD COLUMN cash_tendered numeric CHECK(cash_tendered IS NULL OR cash_tendered>=0),
    ADD COLUMN cash_change numeric CHECK(cash_change IS NULL OR cash_change>=0),
    ADD COLUMN paid_at timestamptz, ADD COLUMN updated_at timestamptz, ADD COLUMN pos_register_shift_id uuid;
   CREATE TABLE pos_register_shifts(id uuid,shop_id uuid,cashier_user_id uuid,closed_at timestamptz);
   INSERT INTO pos_register_shifts VALUES('${uuid(30)}','${uuid(20)}','${uuid(1)}',null);
   CREATE TABLE payments(order_id uuid,amount numeric,payment_method text,status text,paid_at timestamptz);
   ${JSON.parse(fs.readFileSync('tests/fixtures/pos-payment-prod-20261122.json','utf8')).definitions.map(row=>row.definition+';').join('\n')}
   CREATE ROLE service_role;
   REVOKE ALL ON FUNCTION public.pos_take_payment(uuid,text,numeric) FROM PUBLIC,anon;
   GRANT EXECUTE ON FUNCTION public.pos_take_payment(uuid,text,numeric) TO authenticated,service_role;`);
  const payment='public.pos_take_payment(uuid,text,numeric)';
  const executeAcl=(await db.query(`SELECT has_function_privilege('anon',$1,'EXECUTE') AS anon,
   has_function_privilege('authenticated',$1,'EXECUTE') AS auth,
   has_function_privilege('service_role',$1,'EXECUTE') AS service`,[payment])).rows[0];
  assert.equal(executeAcl.anon,false);assert.equal(executeAcl.auth,true);assert.equal(executeAcl.service,true);checks+=3;
  const originalPaymentHash=(await db.query('SELECT md5(prosrc) AS hash FROM pg_proc WHERE oid=$1::regprocedure',[payment])).rows[0].hash;
  assert.equal(originalPaymentHash,'439406595ff236408dd07a21fbd4afd3');checks++;
  await db.query("SELECT set_config('queuego.pos_rpc','on',false)");
  await insert(301,100);
  // Reproduce the captured baseline gap transactionally, then undo every row.
  await db.exec('BEGIN');
  await db.query("SELECT public.pos_take_payment($1,'cash','NaN'::numeric)",[uuid(301)]);
  row=(await db.query('SELECT payment_status,cash_tendered::text,cash_change::text FROM orders WHERE id=$1',[uuid(301)])).rows[0];
  assert.equal(row.payment_status,'PAID');assert.equal(row.cash_tendered,'NaN');assert.equal(row.cash_change,'NaN');checks+=3;
  await db.exec('ROLLBACK');
  assert.equal((await db.query('SELECT * FROM payments')).rows.length,0);checks++;
  assert.equal((await db.query('SELECT payment_status FROM orders WHERE id=$1',[uuid(301)])).rows[0].payment_status,'UNPAID');checks++;
  const beforeAcl=(await db.query('SELECT proowner,proacl::text FROM pg_proc WHERE oid=$1::regprocedure',[payment])).rows[0];
  await db.exec(fs.readFileSync('ops/p0/pos-payment-finite-review-only.sql','utf8'));
  assert.deepEqual((await db.query('SELECT proowner,proacl::text FROM pg_proc WHERE oid=$1::regprocedure',[payment])).rows[0],beforeAcl);checks++;
  const beforePaymentAudit=await auditCount();
  for(const special of ['NaN','Infinity','-Infinity']){
   await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash',$2::numeric)",[uuid(301),special]),/finite POS cash received required/);checks++;
  }
  await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash',100)",[uuid(301)]),/cash received is less than total/);checks++;
  await assert.rejects(db.query("SELECT public.pos_take_payment($1,null,null)",[uuid(301)]),/invalid payment method/);checks++;
  assert.equal((await db.query('SELECT * FROM payments')).rows.length,0);checks++;
  assert.equal(await auditCount(),beforePaymentAudit);checks++;
  let paid=(await db.query("SELECT public.pos_take_payment($1,'cash',110) AS bill",[uuid(301)])).rows[0].bill;
  assert.equal(paid.payment_status,'PAID');assert.equal(Number(paid.total_amount),107);assert.equal(Number(paid.cash_change),3);checks+=3;
  const paidAudit=await auditCount();
  paid=(await db.query("SELECT public.pos_take_payment($1,'cash',110) AS bill",[uuid(301)])).rows[0].bill;
  assert.equal(paid.payment_status,'PAID');assert.equal((await db.query('SELECT * FROM payments')).rows.length,1);checks+=2;
  assert.equal(await auditCount(),paidAudit);checks++;
  await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash','NaN'::numeric)",[uuid(301)]),/finite POS cash received required/);checks++;
  // A later audit failure must roll back the payment INSERT and closed bill too.
  await insert(302,100);
  await db.exec(`CREATE FUNCTION public.reject_payment_audit_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
   BEGIN RAISE EXCEPTION 'isolated payment audit failure'; END $$;
   CREATE TRIGGER reject_payment_audit_fixture BEFORE INSERT ON pos_events
    FOR EACH ROW EXECUTE FUNCTION public.reject_payment_audit_fixture();`);
  await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash',110)",[uuid(302)]),/isolated payment audit failure/);checks++;
  assert.equal((await db.query('SELECT * FROM payments WHERE order_id=$1',[uuid(302)])).rows.length,0);checks++;
  assert.equal((await db.query('SELECT payment_status FROM orders WHERE id=$1',[uuid(302)])).rows[0].payment_status,'UNPAID');checks++;
  await db.exec('DROP TRIGGER reject_payment_audit_fixture ON pos_events; DROP FUNCTION public.reject_payment_audit_fixture()');
  // Deliberately corrupt legacy-like rows only inside disposable transactions.
  // Remove the fixture tax constraint/trigger temporarily so the RPC's own total
  // validation is tested independently; every schema/row change is rolled back.
  for(const badTotal of ['NaN','Infinity','-Infinity',null,'-1']){
   await db.exec('BEGIN; ALTER TABLE orders DISABLE TRIGGER pos_apply_tax_snapshot; ALTER TABLE orders DROP CONSTRAINT pos_channel_integrity_check');
   await db.query('UPDATE orders SET total_amount=$2::numeric WHERE id=$1',[uuid(302),badTotal]);
   await db.exec('SAVEPOINT before_invalid_payment');
   await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash',110)",[uuid(302)]),/finite nonnegative POS bill total required/);checks++;
   await db.exec('ROLLBACK');
   assert.equal((await db.query('SELECT * FROM payments WHERE order_id=$1',[uuid(302)])).rows.length,0);checks++;
   assert.equal(Number((await db.query('SELECT total_amount FROM orders WHERE id=$1',[uuid(302)])).rows[0].total_amount),107);checks++;
  }
  // Review-only deployment refuses unknown/already-modified baselines instead
  // of overwriting a concurrently changed financial function.
  await assert.rejects(db.exec(fs.readFileSync('ops/p0/pos-payment-finite-review-only.sql','utf8')),/POS payment baseline changed/);checks++;
  await db.exec('ROLLBACK');
  assert.deepEqual((await db.query('SELECT proowner,proacl::text FROM pg_proc WHERE oid=$1::regprocedure',[payment])).rows[0],beforeAcl);checks++;
  // Preserve legitimate zero-value and noncash bills; a finite guard must not
  // silently narrow existing supported payment methods or manufacture change.
  await insert(303,0);
  paid=(await db.query("SELECT public.pos_take_payment($1,'cash',0) AS bill",[uuid(303)])).rows[0].bill;
  assert.equal(paid.payment_status,'PAID');assert.equal(Number(paid.cash_change),0);checks+=2;
  await insert(304,100);
  paid=(await db.query("SELECT public.pos_take_payment($1,'card',null) AS bill",[uuid(304)])).rows[0].bill;
  assert.equal(paid.payment_status,'PAID');assert.equal(paid.cash_change,null);assert.equal(Number(paid.total_amount),107);checks+=3;
  await actor('authenticated',2);
  await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash',110)",[uuid(301)]),/bill not found/);checks++;
  await actor('authenticated',4);
  await assert.rejects(db.query("SELECT public.pos_take_payment($1,'cash',110)",[uuid(301)]),/cashier permission denied/);checks++;
  await actor('postgres',1);

  console.log(JSON.stringify({checks,failures:0,engine,scope:'isolated POS tax/role/RLS, captured order guard/audit and payment RPC finite-value/replay/rollback; synthetic identities and tables, NOT all live triggers/JWT/refund/Production certification'}));
 }finally{await db.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
