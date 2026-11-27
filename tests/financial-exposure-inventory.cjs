'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {PGlite}=require('@electric-sql/pglite');
async function main(){
 const db=new PGlite();let checks=0;
 try{
  const sql=fs.readFileSync(path.join(__dirname,'../scripts/sql/audit-noncash-financial-update-exposure.sql'),'utf8');
  assert(!/^[ \t]*(create|alter|drop|grant|revoke|insert|update|delete|truncate)\b/im.test(sql),'catalog inventory cannot contain schema/row mutations');checks++;
  await db.exec(`CREATE ROLE anon; CREATE ROLE authenticated;
   CREATE TABLE public.orders(total_amount numeric,payment_status text,subtotal numeric,delivery_fee numeric);
   CREATE TABLE public.order_items(unit_price numeric,quantity integer,order_id uuid);
   CREATE TABLE public.payments(id uuid);
   ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;
   ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;
   ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
   GRANT UPDATE(total_amount) ON public.orders TO authenticated;
   CREATE FUNCTION public.audit_catalog_fixture_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$;
   CREATE TRIGGER zz_queuego_cash_order_guard BEFORE UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION public.audit_catalog_fixture_guard();
   CREATE TRIGGER pos_items_guard BEFORE UPDATE ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.audit_catalog_fixture_guard();`);
  // Guard bodies are catalog fixtures and never invoked: this tests inventory
  // semantics and negative sentinels, not financial immutability/authorization.
  const results=await db.exec(sql);
  assert.equal(results.length,4);checks++;
  assert.equal(results[0].rows.length,14);checks++;
  assert.equal(results[1].rows.length,2);checks++;
  const rows=results[0].rows;
  const target=rows.find(r=>r.role_name==='authenticated'&&r.table_name==='orders'&&r.column_name==='total_amount');
  assert.equal(target.table_update_grant,false);assert.equal(target.effective_column_update,true);checks+=2;
  assert.equal(rows.find(r=>r.role_name==='anon'&&r.table_name==='orders'&&r.column_name==='total_amount').effective_column_update,false);checks++;
  assert.equal(rows.find(r=>r.role_name==='authenticated'&&r.table_name==='orders'&&r.column_name==='subtotal').effective_column_update,false);checks++;
  assert(rows.every(r=>r.rls_enabled===true));checks++;
  await db.exec('ALTER TABLE public.payments DISABLE ROW LEVEL SECURITY');
  await assert.rejects(db.exec(sql),/RLS absent on public.payments/);checks++;
  await db.exec('ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY; ALTER TABLE public.order_items DISABLE TRIGGER pos_items_guard');
  await assert.rejects(db.exec(sql),/Expected order_items POS guard absent or disabled/);checks++;
  console.log(JSON.stringify({checks,failures:0,scope:'read-only financial catalog inventory, column grants versus table grants and negative RLS/trigger sentinels; NOT runtime financial authorization'}));
 }finally{await db.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
