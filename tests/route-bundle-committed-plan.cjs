const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const {planStackRoute}=await import('../supabase/functions/queuego-route-bundle-matrix/stack-planner.mjs');
 // A cheaper new pickup must not replace the already committed next stop.
 const ids=[id(70),id(71)],points={R:{kind:'rider'},AP:{orderId:ids[0],kind:'pickup'},AD:{orderId:ids[0],kind:'dropoff'},BP:{orderId:ids[1],kind:'pickup'},BD:{orderId:ids[1],kind:'dropoff'}};
 const edgeMap=Object.fromEntries(Object.keys(points).flatMap(a=>Object.keys(points).filter(b=>b!==a).map(b=>[a+'|'+b,{km:10,minutes:10}])));
 edgeMap['R|BP']={km:0.1,minutes:0.1};edgeMap['BP|AP']={km:0.1,minutes:0.1};
 const direct={orderIds:ids,pointMap:points,edges:edgeMap,orders:{[ids[0]]:{status:'ready'},[ids[1]]:{status:'searching_rider'}},bundleId:id(80),routeRevision:7,committedPrefix:[{order_id:id(90),kind:'pickup'},{order_id:id(90),kind:'dropoff'}],lockedNext:'AP',lockedNextStop:{order_id:ids[0],kind:'pickup'}};
 eq(planStackRoute(direct).stops[0].key,'AP');
 eq(planStackRoute(direct).committedPrefix,direct.committedPrefix);
 const before=JSON.stringify(direct);planStackRoute(direct);eq(JSON.stringify(direct),before);
 assert.throws(()=>planStackRoute({...direct,lockedNext:null}),/invalid stack commitment/);checks++;
 assert.throws(()=>planStackRoute({...direct,routeRevision:1.5}),/invalid stack commitment/);checks++;
 assert.throws(()=>planStackRoute({...direct,lockedNext:'BP'}),/invalid stack commitment/);checks++;
 const db=new PGlite();
 try{
  await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
  await db.exec("create function auth.role() returns text language sql as 'select coalesce(nullif(current_setting(''test.role'',true),''''),''authenticated'')';");
  await db.exec(fs.readFileSync('supabase/migrations/20261018060000_route_bundle_multi_stop_matrix_phase2.sql','utf8'));
  await db.exec(fs.readFileSync('supabase/migrations/20261018063000_route_bundle_stack_plan_readonly.sql','utf8'));
  await db.query("select set_config('test.role','service_role',false)");
  for(const count of [2,3,4,5]){
   await db.exec('delete from orders; delete from route_bundles;');
   const bundle=id(80),rider=id(11),rev='2026-10-18T00:00:00Z',orderIds=Array.from({length:count},(_,i)=>id(70+i));
   const candidate=orderIds.at(-1),pointMap={R:{kind:'rider'}},orders={};
   const completed=[{order_id:id(90),kind:'pickup',retained:'exact-object'},{order_id:id(90),kind:'dropoff'}];
   const cancelled=[{order_id:id(91),kind:'pickup'},{order_id:id(91),kind:'dropoff'}];
   const oldStops=[...completed,...cancelled];
   for(const [index,order] of orderIds.entries()){
    const isCandidate=order===candidate;
    await db.query("insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,route_bundle_id) values($1,$2,$3,$4,$5,$6,$7,$8)",[order,'QT-'+index,isCandidate?null:rider,id(60),id(50),isCandidate?'searching_rider':'ready',rev,isCandidate?null:bundle]);
    pointMap['O'+index+'P']={orderId:order,kind:'pickup'};pointMap['O'+index+'D']={orderId:order,kind:'dropoff'};
    orders[order]={status:isCandidate?'searching_rider':'ready'};
   }
   for(const kind of ['pickup','dropoff'])for(const order of orderIds.slice(0,-1))oldStops.push({order_id:order,kind});
   for(const [order,status] of [[id(90),'completed'],[id(91),'cancelled']])await db.query("insert into orders(id,order_number,rider_id,status,updated_at,route_bundle_id) values($1,$2,$3,$4,$5,$6)",[order,'QT-history',rider,status,rev,bundle]);
   await db.query("insert into route_bundles(id,rider_id,primary_order_id,status,stop_plan,route_revision) values($1,$2,$3,'ACTIVE',$4::jsonb,7)",[bundle,rider,orderIds[0],JSON.stringify(oldStops)]);
   const keys=Object.keys(pointMap),edges=Object.fromEntries(keys.flatMap(a=>keys.filter(b=>b!==a).map(b=>[a+'|'+b,{km:1,minutes:1}])));
   edges['R|O'+(count-1)+'P']={km:0.01,minutes:0.01}; // candidate is cheapest, but not committed next
   const cache=()=>db.query('select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok',[orderIds[0],candidate,orderIds,JSON.stringify(Object.fromEntries(orderIds.map(o=>[o,rev]))),JSON.stringify(pointMap),JSON.stringify(edges)]);
   const read=async()=>(await db.query('select queuego_route_bundle_stack_plan_input($1,$2) plan',[orderIds[0],candidate])).rows[0].plan;
   eq((await cache()).rows[0].ok,true);
   let input=await read();eq(input.bundleId,bundle);eq(input.routeRevision,7);eq(input.committedPrefix,completed);eq(input.lockedNext,'O0P');eq(input.lockedNextStop,{order_id:orderIds[0],kind:'pickup'});
   const plan=planStackRoute(input);eq(plan.stops[0].key,'O0P');eq(plan.stops.length,count*2);eq(plan.committedPrefix,completed);eq(plan.routeRevision,7);
   // Change only the bundle revision: all Order updated_at values remain exactly unchanged.
   await db.query('update route_bundles set route_revision=8 where id=$1',[bundle]);
   eq(await read(),null);
   eq((await cache()).rows[0].ok,true);eq((await read()).routeRevision,8);
   await db.query("update orders set status='picked_up' where id=$1",[orderIds[0]]);
   input=await read();eq(input.committedPrefix,[...completed,{order_id:orderIds[0],kind:'pickup'}]);
   eq(input.lockedNext,count===2?'O0D':'O1P');
   const afterPickup=()=>planStackRoute(input);
   if(count===2){assert.throws(afterPickup,/route plan unavailable/);checks++;} // locked dropoff conflicts with pickup-first
   else{eq(afterPickup().stops[0].key,'O1P');eq(afterPickup().stops.length,count*2-1);}
  }
 }finally{await db.close();}
 console.log(JSON.stringify({checks,failures:0,scope:'read-only bundle revision, committed prefix and locked-next binding for 2-5 orders; no claim/cap/pricing or Production mutation'}));
})().catch(e=>{console.error(e);process.exit(1)});
