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
  await db.exec("INSERT INTO auth.users(id) VALUES('00000000-0000-4000-8000-000000000001');");
  await db.exec("CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql AS $$ SELECT '00000000-0000-4000-8000-000000000001'::uuid $$;");
  await db.exec("CREATE TABLE public.shop_profiles(id uuid PRIMARY KEY); INSERT INTO public.shop_profiles VALUES('00000000-0000-4000-8000-000000000020');");
  await db.exec("CREATE FUNCTION public.pos_my_shop() RETURNS uuid LANGUAGE sql AS $$ SELECT '00000000-0000-4000-8000-000000000020'::uuid $$;");
  await db.exec("CREATE FUNCTION public.pos_is_owner() RETURNS boolean LANGUAGE sql AS $$ SELECT true $$;");
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
  console.log(JSON.stringify({checks,failures:0,scope:'isolated PGlite POS tax settings/snapshot/checkout; no Production writes'}));
 }finally{await db.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});