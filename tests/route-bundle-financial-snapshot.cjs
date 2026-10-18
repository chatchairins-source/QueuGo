const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const db=new PGlite();
 try{
  await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
  await db.exec("create function auth.role() returns text language sql as 'select coalesce(nullif(current_setting(''test.role'',true),''''),''authenticated'')';");
  await db.exec(fs.readFileSync('supabase/migrations/20261018060000_route_bundle_multi_stop_matrix_phase2.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018063000_route_bundle_stack_plan_readonly.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018074500_route_bundle_stack_financial_snapshot.sql','utf8'));
  await db.query("select set_config('test.role','service_role',false)");

  const rider=id(11),a=id(70),b=id(71),rev='2026-10-18T00:00:00Z';
  await db.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,delivery_fee,rider_delivery_payout,merchant_ready_estimate_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-A',$2,$3,$4,'ready',$5,50,55,$6,15,103,15.01,103)",[a,rider,id(60),id(50),rev,'2026-10-18T00:04:00Z']);
  await db.query("insert into orders(id,order_number,customer_id,shop_id,status,updated_at,delivery_fee,merchant_ready_estimate_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-B',$2,$3,'searching_rider',$4,40,$5,15,103,15.02,103)",[b,id(60),id(50),rev,'2026-10-18T00:03:00Z']);

  const orderIds=[a,b],pointMap={R:{kind:'rider'},AP:{orderId:a,kind:'pickup'},AD:{orderId:a,kind:'dropoff'},BP:{orderId:b,kind:'pickup'},BD:{orderId:b,kind:'dropoff'}};
  const keys=Object.keys(pointMap),edges=Object.fromEntries(keys.flatMap(x=>keys.filter(y=>y!==x).map(y=>[x+'|'+y,{km:1,minutes:1}])));
  const revisions=Object.fromEntries(orderIds.map(order=>[order,rev]));
  eq((await db.query("select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok",[a,b,orderIds,JSON.stringify(revisions),JSON.stringify(pointMap),JSON.stringify(edges)])).rows[0].ok,true);

  let snapshot=(await db.query("select queuego_route_bundle_stack_financial_input($1,$2) v",[a,b])).rows[0].v;
  eq(snapshot.financialPolicyVersion,'stack-savings-group-weighted-v1');
  eq(snapshot.financialOrders[a].currentFeeSatang,5000);
  eq(snapshot.financialOrders[a].lockedRiderPayoutSatang,5500);
  eq(snapshot.financialOrders[b].currentFeeSatang,4000);
  eq(snapshot.financialOrders[b].lockedRiderPayoutSatang,null);
  eq(snapshot.financialOrders[a].shopCategory,'food');
  eq(snapshot.financialRules.existingGroupSavingsPercent,50);
  eq(snapshot.financialRules.incrementalKmRateSatangPerKm,500);
  eq(snapshot.financialRules.pickupCostSatang,200);
  eq(snapshot.financialRules.dropoffCostSatang,200);
  eq(snapshot.financialRules.minuteRateSatangPerMinute,100);
  eq(snapshot.financialRules.maxDetourMeters,1500);
  eq(snapshot.financialRules.maxDelaySeconds,300);

  // A financial field change must be coupled to the same revision fence.
  await db.query("update orders set rider_delivery_payout=60,updated_at='2026-10-18T00:00:01Z' where id=$1",[a]);
  eq((await db.query("select queuego_route_bundle_stack_financial_input($1,$2) v",[a,b])).rows[0].v,null);

  await db.query("select set_config('test.role','authenticated',false)");
  await assert.rejects(db.query("select queuego_route_bundle_stack_financial_input($1,$2)",[a,b]),/service role required/);checks++;

  const sql=fs.readFileSync('supabase/migrations/20261018074500_route_bundle_stack_financial_snapshot.sql','utf8');
  assert(/\bstable\b/i.test(sql)&&!/\b(insert|update|delete)\s+(public\.|qg_private\.)/i.test(sql));checks++;
  assert(sql.includes('revoke all on function public.queuego_route_bundle_stack_financial_input')&&sql.includes('to service_role'));checks++;
 } finally { await db.close(); }
 console.log(JSON.stringify({checks,failures:0,scope:'same-snapshot authoritative fees, locked payouts, readiness and rule snapshot; no price/claim/cap mutation'}));
})().catch(e=>{console.error(e);process.exit(1)});
