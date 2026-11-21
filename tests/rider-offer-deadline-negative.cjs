'use strict';
// P0 #1268: isolated negative tests of the reviewed *migration*, never Production.
// Run: node tests/rider-offer-deadline-negative.cjs
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
async function main(){
 const db=new PGlite();let checks=0;
 try{
  await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
  await db.exec("ALTER TABLE orders ADD COLUMN rider_search_started_at timestamptz");
  await db.exec("CREATE TABLE system_settings(key text PRIMARY KEY,value jsonb NOT NULL); INSERT INTO system_settings VALUES('app','{\"orderTimeout\":30}')");
  const migration=fs.readFileSync('supabase/migrations/20261120043000_rider_offer_deadline_guard_p0.sql','utf8');
  await db.exec(migration);
  const seed=async(n,age,status='searching_rider')=>db.query(
   "INSERT INTO orders(id,status,rider_search_started_at,created_at) VALUES($1,$2,now()-($3::text)::interval,now()-($3::text)::interval)",
   [id(n),status,age]);
  const offer=async(n,attempt,offeredAt='now()',expiresAt="now()+interval '30 seconds'")=>{
   const sql="INSERT INTO qg_rider_order_offers(order_id,rider_id,attempt,offered_at,expires_at) VALUES($1,$2,$3,"+offeredAt+","+expiresAt+") RETURNING order_id";
   return (await db.query(sql,[id(n),id(11),attempt])).rows.length;
  };
  const eq=async(n,attempt,want,message,offeredAt,expiresAt)=>{
   assert.equal(await offer(n,attempt,offeredAt,expiresAt),want,message);checks++;
  };
  await seed(901,'31 minutes');
  await eq(901,1,0,'expired search must not create offer');
  await seed(902,'1 minute','cancelled');
  await eq(902,1,0,'cancelled order must not resurrect');
  await seed(903,'1 minute');
  await eq(903,0,0,'zero attempt rejected');
  await eq(903,121,0,'attempt 121 rejected');
  await eq(903,null,0,'NULL attempt rejected');
  await eq(903,120,1,'attempt 120 allowed within deadline');
  await seed(904,'1 minute');
  await eq(904,1,0,'NULL offered_at rejected','NULL');
  await eq(904,1,0,'NULL expires_at rejected','now()','NULL');
  await eq(904,1,0,'already-expired offer rejected',"now()-interval '2 minutes'","now()-interval '1 minute'");
  await seed(905,'1 minute');
  await eq(905,1,1,'valid offer allowed');
  await db.query("INSERT INTO orders(id,status,created_at) VALUES($1,'searching_rider',now()-interval '1 minute')",[id(906)]);
  await eq(906,1,0,'NULL rider search timestamp cannot fall back to recent creation');
  await db.query("INSERT INTO orders(id,status,created_at,merchant_accepted_at) VALUES($1,'searching_rider',now()-interval '2 minutes',now()-interval '1 minute')",[id(907)]);
  await eq(907,1,0,'NULL rider search timestamp cannot fall back to merchant acceptance');
  await seed(908,'2 minutes');
  await db.query("UPDATE orders SET created_at=now()-interval '1 minute' WHERE id=$1",[id(908)]);
  await eq(908,1,0,'search started before order creation is rejected');
  await seed(909,'2 minutes');
  await db.query("UPDATE orders SET merchant_accepted_at=now()-interval '1 minute' WHERE id=$1",[id(909)]);
  await eq(909,1,0,'search started before merchant accepted is rejected');
  const expiry=(await db.query('SELECT expires_at <= offered_at + interval \'30 seconds\' AS bounded FROM qg_rider_order_offers WHERE order_id=$1',[id(905)])).rows[0].bounded;
  assert.equal(expiry,true,'server caps offer TTL at 30 seconds');checks++;
  assert.equal((await db.query("SELECT count(*)::int AS n FROM notifications")).rows[0].n,0,'trigger itself must not emit notifications');checks++;
  console.log(JSON.stringify({checks,failures:0,scope:'disposable PGlite; migration INSERT negative gates'}));
 }finally{await db.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1});
