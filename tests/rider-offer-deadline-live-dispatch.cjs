'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');

async function main(){
 const db=new PGlite();
 let checks=0;
 try{
   await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
   await db.exec("ALTER TABLE orders ADD COLUMN rider_search_started_at timestamptz;");
   await db.exec("CREATE TABLE system_settings(key text PRIMARY KEY,value jsonb NOT NULL);INSERT INTO system_settings VALUES('app','{\"orderTimeout\":30}');");
   await db.exec("CREATE FUNCTION qg_private.rider_work_allows(uuid,uuid) RETURNS boolean LANGUAGE sql AS $$SELECT true$$;");
   await db.exec("CREATE FUNCTION qg_private.ride_owner_authorized(uuid) RETURNS boolean LANGUAGE sql AS $$SELECT false$$;");
   // Two real codepaths: the owner-configured timeout trigger and the captured
   // 2026-11-19 live PostgreSQL dispatcher body, not a synthetic dispatcher.
   const guard=fs.readFileSync('ops/p0/rider-offer-deadline-guard.sql','utf8');
   const deployed=fs.readFileSync('tests/fixtures/rider-dispatch-prod-snapshot-20261119.sql','utf8');
   assert(deployed.includes('left join qg_private.rider_destination_preferences'), 'preserve live destination ranking');
   assert(deployed.includes('for update of r skip locked'), 'preserve production rider claim serialization');
   assert(deployed.includes('if found then'), 'live notification insertion must require an inserted offer');
   checks+=3;
   await db.exec(guard);
   await db.exec(deployed);
   await db.query("UPDATE rider_profiles SET metadata='{\"online\":false,\"available\":false}'::jsonb WHERE id=$1",[id(12)]);
   const seed=async(n,age,status='searching_rider')=>{
     await db.query(
       "INSERT INTO orders(id,order_number,customer_id,shop_id,order_type,status,fulfillment_vertical,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,created_at,rider_search_started_at) VALUES($1,$2,$3,$4,'shopping',$5,'food',15.001,103.001,15.003,103.005,now()-($6::text)::interval,now()-($6::text)::interval)",
       [id(n),'P0-'+n,id(60),id(50),status,age]
     );
   };
   const run=async()=>Number((await db.query("SELECT public.qg_dispatch_rider_offers() AS offered")).rows[0].offered);
   const count=async(table,order)=>{
     const v=(await db.query('SELECT count(*)::integer AS c FROM '+table+' WHERE '+(table==='notifications'?'reference_id':'order_id')+'=$1',[id(order)])).rows[0].c;
     return Number(v);
   };
   const eq=(value,expected,message)=>{assert.equal(value,expected,message);checks++;};

   await seed(801,'55 hours');
   for(let i=0;i<5;i++)eq(await run(),0,'old real order must never be re-offered');
   eq(await count('qg_rider_order_offers',801),0,'no active offers on stale order');
   eq(await count('notifications',801),0,'no notification spam for stale order');
   eq(await count('qg_rider_offer_history',801),0,'no fake expired attempt when guard suppresses insert');

   await seed(802,'1 minute');
   eq(await run(),1,'live dispatcher offers a valid new order');
   eq(await count('qg_rider_order_offers',802),1,'valid order gets exactly one offer');
   eq(await count('notifications',802),1,'notification appears exactly after successful insert');
   eq(await run(),0,'second cron cannot duplicate live offer');
   eq(await count('notifications',802),1,'duplicate cron must not duplicate notification');

   await db.query("UPDATE qg_rider_order_offers SET expires_at=now()-interval '1 second' WHERE order_id=$1",[id(802)]);
   eq(await run(),0,'expired offer enters thirty-second cooldown');
   eq(await count('qg_rider_offer_history',802),1,'actual deployed dispatcher records expiry once');
   eq(await count('qg_rider_order_offers',802),0,'expired offer removed');
   await db.query("UPDATE qg_rider_offer_history SET resolved_at=now()-interval '31 seconds' WHERE order_id=$1",[id(802)]);
   await db.query("UPDATE orders SET rider_search_started_at=now()-interval '31 minutes' WHERE id=$1",[id(802)]);
   for(let i=0;i<4;i++)eq(await run(),0,'deadline overrides cleared rider cooldown');
   eq(await count('notifications',802),1,'no new notification after deadline, despite available Rider');
   eq(await count('qg_rider_order_offers',802),0,'no new offer after deadline');

   await seed(803,'2 minutes','cancelled');
   eq(await run(),0,'terminal order is never offered');
   eq(await count('qg_rider_order_offers',803),0,'terminal order cannot have new offers');

   await db.exec("UPDATE system_settings SET value='{\"orderTimeout\":1}'::jsonb WHERE key='app'");
   await seed(804,'2 minutes');
   eq(await run(),0,'one-minute owner timeout also works on real dispatcher');
   eq(await count('notifications',804),0,'one-minute timeout sends no Rider notification');
   await db.exec("UPDATE system_settings SET value='{\"orderTimeout\":120}'::jsonb WHERE key='app'");
   eq(await run(),1,'admin increase to 120 minutes permits dispatch for a valid order');
   eq(await count('notifications',804),1,'notification reappears only when offer is legitimate');

   assert(!/\b(?:update|delete)\s+public\.orders\b/i.test(guard),'guard cannot modify order state');checks++;
   assert(!/\b(?:update|delete|insert\s+into)\s+public\.(?:payments|settlements)\b/i.test(guard),'guard cannot modify money');checks++;
 }finally{await db.close();}
 console.log(JSON.stringify({checks,failures:0,scope:'isolated PGlite deployed-dispatcher snapshot with bounded production offer trigger; no Production writes'}));
}
main().catch(e=>{console.error(e);process.exitCode=1});
