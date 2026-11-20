const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const {planStackRoute,planExistingStackRoute}=await import('../supabase/functions/queuego-route-bundle-matrix/stack-planner.mjs');
 const orderIds=[id(70),id(71)];
 const pointMap={R:{kind:'rider'},AP:{orderId:orderIds[0],kind:'pickup'},AD:{orderId:orderIds[0],kind:'dropoff'},BP:{orderId:orderIds[1],kind:'pickup'},BD:{orderId:orderIds[1],kind:'dropoff'}};
 const keys=Object.keys(pointMap),edges={};
 for(const a of keys)for(const b of keys)if(a!==b)edges[a+'|'+b]={km:10,minutes:10};
 Object.assign(edges,{'R|BP':{km:1,minutes:1},'BP|AP':{km:1,minutes:1},'AP|AD':{km:1,minutes:1},'AD|BD':{km:1,minutes:1}});
 const input={orderIds,candidateOrderId:orderIds[1],pointMap,edges,orders:{[orderIds[0]]:{status:'ready'},[orderIds[1]]:{status:'searching_rider'}}};
 const before=JSON.stringify(input),plan=planStackRoute(input);
 eq(plan.stops.map(s=>s.key),['BP','AP','AD','BD']);eq(plan.totalKm,4);eq(plan.totalMinutes,4);eq(plan.policy,'all-pickups-before-dropoffs-v1');eq(JSON.stringify(input),before);
 const picked=JSON.parse(JSON.stringify(input));picked.orders[orderIds[0]].status='picked_up';picked.edges['R|BP']={km:1,minutes:1};picked.edges['BP|AD']={km:1,minutes:1};picked.edges['AD|BD']={km:1,minutes:1};
 eq(planStackRoute(picked).stops.map(s=>s.key),['BP','AD','BD']);eq(planExistingStackRoute(input).stops.map(s=>s.key),['AP','AD']);
 assert.throws(()=>planStackRoute({...input,orderIds:[...orderIds,id(72),id(73),id(74),id(75)]}),/invalid stack orders/);checks++;
 const broken=JSON.parse(JSON.stringify(input));delete broken.edges['R|AP'];delete broken.edges['R|BP'];assert.throws(()=>planStackRoute(broken),/incomplete road matrix/);checks++;

 // Handler plan action reads existing cache only: no provider call and no cache write.
 const evidence={...input,primaryOrderId:orderIds[0],candidateOrderId:orderIds[1],expiresAt:'2026-10-18T00:01:00Z',financialPolicyVersion:'stack-savings-group-weighted-v1',financialEvaluatedAt:'2026-10-18T00:00:00Z',financialOrders:{[orderIds[0]]:{currentFeeSatang:2500,lockedRiderPayoutSatang:2500,merchantReadyEstimateAt:'2026-10-18T00:00:00Z'},[orderIds[1]]:{currentFeeSatang:2500,lockedRiderPayoutSatang:null,merchantReadyEstimateAt:'2026-10-18T00:00:00Z'}},financialRules:{existingGroupSavingsPercent:50,incrementalKmRateSatangPerKm:500,pickupCostSatang:200,dropoffCostSatang:200,minuteRateSatangPerMinute:100,maxDetourMeters:1500,maxDelaySeconds:300,maxPickupWaitSeconds:300}};
 let providerCalls=0,rpcCalls=0;
 const fetcher=async(url,options)=>{
  const u=new URL(url);
  if(u.hostname==='api.longdo.com'){providerCalls++;throw Error('plan must not call provider');}
  if(u.pathname==='/rest/v1/rpc/queuego_route_bundle_stack_financial_input'){
   rpcCalls++;eq(JSON.parse(options.body),{p_primary_order_id:orderIds[0],p_candidate_order_id:orderIds[1]});
   return {ok:true,json:async()=>evidence};
  }
  throw Error('unexpected planner transport '+u.pathname);
 };
 const {createRouteBundleMatrixHandler}=await import('../supabase/functions/queuego-route-bundle-matrix/handler.mjs');
 const env={SUPABASE_SERVICE_ROLE_KEY:'isolated-service',SUPABASE_URL:'https://backend.invalid',QUEUEGO_LONGDO_KEY:''};
 const handler=createRouteBundleMatrixHandler({get:k=>env[k]},fetcher);
 const req=new Request('https://edge.invalid',{method:'POST',headers:{authorization:'Bearer isolated-service','content-type':'application/json'},body:JSON.stringify({action:'plan',primaryOrderId:orderIds[0],candidateOrderId:orderIds[1]})});
 const response=await handler(req);eq(response.status,200);
 const body=await response.json();eq(body.cached,true);eq(body.readOnly,true);eq(body.stops.map(s=>s.key),['BP','AP','AD','BD']);eq(body.financial,{mode:'existing-two-order-live-quote',generalized:false});eq(providerCalls,0);eq(rpcCalls,1);

 // Execute the read-only RPC against isolated PostgreSQL-compatible state.
 const db=new PGlite();
 try{
  await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
  await db.exec("create function auth.role() returns text language sql as 'select coalesce(nullif(current_setting(''test.role'',true),''''),''authenticated'')';");
  const phase2=fs.readFileSync('supabase/migrations/20261018060000_route_bundle_multi_stop_matrix_phase2.sql','utf8');
  const readOnlySql=fs.readFileSync('supabase/migrations/20261018063000_route_bundle_stack_plan_readonly.sql','utf8');
  await db.exec(phase2);await db.exec(readOnlySql);
  const rev='2026-10-18T00:00:00Z',rider=id(11),customer=id(60),shop=id(50);
  await db.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-A',$2,$3,$4,'ready',$5,15,103,15.01,103)",[orderIds[0],rider,customer,shop,rev]);
  await db.query("insert into orders(id,order_number,customer_id,shop_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-B',$2,$3,'searching_rider',$4,15,103,15.02,103)",[orderIds[1],customer,shop,rev]);
  const revisions=Object.fromEntries(orderIds.map(orderId=>[orderId,rev]));
  await db.query("select set_config('test.role','service_role',false)");
  eq((await db.query("select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok",[orderIds[0],orderIds[1],orderIds,JSON.stringify(revisions),JSON.stringify(pointMap),JSON.stringify(edges)])).rows[0].ok,true);
  let plannerInput=(await db.query("select queuego_route_bundle_stack_plan_input($1,$2) plan",[orderIds[0],orderIds[1]])).rows[0].plan;
  eq(plannerInput.orderIds.length,2);eq(plannerInput.orders[orderIds[0]].status,'ready');eq(plannerInput.orders[orderIds[1]].status,'searching_rider');
  // Membership is part of the evidence: another job can be assigned without touching A/B.
  await db.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-NEW',$2,$3,$4,'ready',$5,15,103,15.03,103)",[id(72),rider,customer,shop,rev]);
  for(const status of ['rider_assigned','preparing','ready','assigned','picked_up','in_progress']){
   await db.query("update orders set status=$2 where id=$1",[id(72),status]);
   plannerInput=(await db.query("select queuego_route_bundle_stack_plan_input($1,$2) plan",[orderIds[0],orderIds[1]])).rows[0].plan;
   eq(plannerInput,null);
  }
  await db.query("update orders set status='completed' where id=$1",[id(72)]);
  plannerInput=(await db.query("select queuego_route_bundle_stack_plan_input($1,$2) plan",[orderIds[0],orderIds[1]])).rows[0].plan;
  eq(plannerInput.orderIds.length,2); // Historical jobs are not active-set changes.
  await db.query("update orders set status='ready',rider_id=$2 where id=$1",[id(72),id(12)]);
  plannerInput=(await db.query("select queuego_route_bundle_stack_plan_input($1,$2) plan",[orderIds[0],orderIds[1]])).rows[0].plan;
  eq(plannerInput.orderIds.length,2); // Another Rider's work must not invalidate this snapshot.
  await db.query("update orders set updated_at=$2 where id=$1",[orderIds[1],'2026-10-18T00:00:01Z']);
  plannerInput=(await db.query("select queuego_route_bundle_stack_plan_input($1,$2) plan",[orderIds[0],orderIds[1]])).rows[0].plan;eq(plannerInput,null);
  await db.query("select set_config('test.role','authenticated',false)");
  await assert.rejects(db.query("select queuego_route_bundle_stack_plan_input($1,$2)",[orderIds[0],orderIds[1]]),/service role required/);checks++;
  assert(readOnlySql.includes('stable')&&!/\b(insert|update|delete)\s+(public\.|qg_private\.)/i.test(readOnlySql));checks++;
 }finally{await db.close();}
 console.log(JSON.stringify({checks,failures:0,scope:'read-only exact-revision route-bundle stack planner; no claim, reprice, assignment, provider call or Production mutation'}));
})().catch(e=>{console.error(e);process.exit(1)});
