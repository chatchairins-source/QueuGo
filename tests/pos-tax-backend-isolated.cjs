'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');
const {PGlite}=require('@electric-sql/pglite');
const uuid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
async function main(){
 const db=new PGlite();let checks=0;
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

  console.log(JSON.stringify({checks,failures:0,scope:'isolated PGlite POS tax calculations and actual role/RLS enforcement with captured helpers and synthetic identities; NOT live JWT, full checkout, refund or Production certification'}));
 }finally{await db.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
