const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 try{
  await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
  await db.exec("create function auth.role() returns text language sql as 'select coalesce(nullif(current_setting(''test.role'',true),''''),''authenticated'')';");
  await db.exec("create table qg_private.route_matrix_refresh_lease(singleton boolean primary key default true check(singleton),lease_id uuid,expires_at timestamptz not null default '-infinity');insert into qg_private.route_matrix_refresh_lease(singleton) values(true);");
  await db.exec(fs.readFileSync('supabase/migrations/20261018060000_route_bundle_multi_stop_matrix_phase2.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018090000_route_bundle_stack_refresh_discovery.sql','utf8'));
  const rider=id(11),bundle=id(80),A=id(70),C=id(71),D=id(72),rev='2026-10-18T00:00:00Z';
  await db.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,route_bundle_id,route_bundle_sequence,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-A',$2,$3,$4,'ready',$5,$6,1,15,103,15.01,103),($7,'QT-C',$2,$3,$4,'ready',$5,$6,2,15.001,103,15.011,103)",[A,rider,id(60),id(50),rev,bundle,C]);
  await db.query("insert into orders(id,order_number,customer_id,shop_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-D',$2,$3,'searching_rider',$4,15.002,103,15.012,103)",[D,id(60),id(50),rev]);
  await db.query("insert into route_bundles(id,rider_id,primary_order_id,status,route_revision) values($1,$2,$3,'ACTIVE',7)",[bundle,rider,A]);
  await db.query("insert into qg_private.rider_dispatch_preferences(rider_id,auto_accept,stop_after_work,ready_until) values($1,false,false,now()+interval '1 minute')",[rider]);
  await db.query("insert into queuego_platform_rules(rule_key,value) values('dispatch.continuous_enabled','1'::jsonb),('route_bundle.max_orders','2'::jsonb)");
  await db.query("select set_config('test.role','service_role',false)");
  eq((await db.query("select queuego_route_bundle_stack_refresh_claim(2) v")).rows[0].v,{});
  await db.query("update queuego_platform_rules set value='3'::jsonb where rule_key='route_bundle.max_orders'");
  const work=(await db.query("select queuego_route_bundle_stack_refresh_claim(2) v")).rows[0].v;
  assert(work.leaseId);checks++;eq(work.pairs.length,1);eq(work.pairs[0].action,'stack');eq(work.pairs[0].primaryOrderId,A);eq(work.pairs[0].candidateOrderId,D);
  eq((await db.query("select rider_id from orders where id=$1",[D])).rows[0].rider_id,null);
  await db.query("update qg_private.route_matrix_refresh_lease set lease_id=null,expires_at=now()-interval '1 second'");
  await db.query("insert into qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt) values($1,$2,now(),now()+interval '30 seconds',1)",[D,id(12)]);
  eq((await db.query("select queuego_route_bundle_stack_refresh_claim(2) v")).rows[0].v,{});
  await db.query("select set_config('test.role','authenticated',false)");
  await assert.rejects(db.query("select queuego_route_bundle_stack_refresh_claim(2)"),/service role required/);checks++;
  const sql=fs.readFileSync('supabase/migrations/20261018090000_route_bundle_stack_refresh_discovery.sql','utf8');
  assert(sql.includes("route_bundle.max_orders',2")&&sql.includes("'action','stack'")&&sql.includes("to service_role"));checks++;
 }finally{await db.close();}
 console.log(JSON.stringify({checks,failures:0,scope:'cap-gated active 2-4 Order stack discovery only; targeted-offer conflicts excluded; no assignment or Production mutation'}));
})().catch(e=>{console.error(e);process.exit(1)});
