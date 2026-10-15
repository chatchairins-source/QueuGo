const assert=require('node:assert/strict');
(async()=>{
 const {parseRoadLeg,buildRoadMatrix}=await import('../supabase/functions/queuego-route-bundle-matrix/road-matrix.mjs');
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const body={meta:{status:200},data:{distance:1000,interval:120},features:[{geometry:{type:'LineString',coordinates:[[103,15],[103.01,15]]},properties:{distance:1000,interval:120}}]};
 eq(parseRoadLeg(body),{km:1,minutes:2});
 eq(parseRoadLeg({...body,data:undefined}),{km:1,minutes:2});
 for(const invalid of [null,{}, {...body,meta:{status:500}}, {...body,features:[]}, {...body,features:[{properties:{distance:1,interval:1}}]}, {...body,data:undefined,features:[{...body.features[0],properties:{distance:1000}}]}, {...body,data:{distance:NaN,interval:NaN},features:[{...body.features[0],properties:{distance:-1,interval:-1}}]}]){assert.throws(()=>parseRoadLeg(invalid));checks++;}
 const points={AP:{lat:15,lng:103},AD:{lat:15.01,lng:103},BP:{lat:15,lng:103},BD:{lat:15.02,lng:103},RP:{lat:15,lng:103}};
 let calls=0,inFlight=0,peak=0;
 const fetcher=async url=>{calls++;inFlight++;peak=Math.max(peak,inFlight);assert.equal(url.hostname,'api.longdo.com');assert.equal(url.searchParams.get('key'),'isolated-key');await Promise.resolve();inFlight--;return {ok:true,text:async()=>JSON.stringify(body)}};
 const edges=await buildRoadMatrix(points,'isolated-key',{fetcher});eq(Object.keys(edges).length,20);eq(edges['AP|BP'],{km:0,minutes:0});eq(edges['AP|AD'],{km:1,minutes:2});assert(peak<=3);checks++;eq(calls,14);
 await assert.rejects(buildRoadMatrix({...points,RP:{lat:NaN,lng:103}},'isolated-key',{fetcher}));checks++;
 await assert.rejects(buildRoadMatrix(points,'',{fetcher}));checks++;
 await assert.rejects(buildRoadMatrix(points,'isolated-key',{fetcher:async()=>({ok:false})}));checks++;
 await assert.rejects(buildRoadMatrix(points,'isolated-key',{fetcher:async()=>({ok:true,text:async()=>'{malformed'})}));checks++;
 const control=new AbortController();control.abort();await assert.rejects(buildRoadMatrix(points,'isolated-key',{fetcher,signal:control.signal}));checks++;
 // Execute the actual Edge handler with an isolated service context and provider transport.
 const env={SUPABASE_SERVICE_ROLE_KEY:'isolated-service',SUPABASE_URL:'https://backend.invalid',QUEUEGO_LONGDO_KEY:'isolated-key'};
 let handler;
 const originalFetch=globalThis.fetch;let cachedBody=null,providerCalls=0;
 globalThis.fetch=async(url,options)=>{
  const u=new URL(url);
  if(u.hostname==='api.longdo.com'){providerCalls++;return {ok:true,text:async()=>JSON.stringify(body)}}
  assert.equal(options.headers.authorization,'Bearer isolated-service');
  if(u.pathname==='/rest/v1/orders')return {ok:true,json:async()=>[
   {id:'00000000-0000-4000-8000-000000000001',rider_id:'rider-id',status:'in_progress',updated_at:'2026-10-15T00:00:00Z',pickup_latitude:15,pickup_longitude:103,delivery_latitude:15.01,delivery_longitude:103},
   {id:'00000000-0000-4000-8000-000000000002',rider_id:null,status:'searching_rider',updated_at:'2026-10-15T00:00:00Z',pickup_latitude:15,pickup_longitude:103,delivery_latitude:15.02,delivery_longitude:103}]};
  if(u.pathname==='/rest/v1/rider_profiles')return {ok:true,json:async()=>[{latitude:15,longitude:103}]};
  if(u.pathname==='/rest/v1/rpc/queuego_cache_route_bundle_matrix'){cachedBody=JSON.parse(options.body);return {ok:true,json:async()=>true}}
  throw Error('unexpected transport');
 };
 const {createRouteBundleMatrixHandler}=await import('../supabase/functions/queuego-route-bundle-matrix/handler.mjs');
 handler=createRouteBundleMatrixHandler({get:k=>env[k]},globalThis.fetch);
 const request=(data,authorization='Bearer isolated-service',method='POST')=>new Request('https://edge.invalid',{method,headers:{authorization,'content-type':'application/json'},body:method==='POST'?JSON.stringify(data):undefined});
 const input={primaryOrderId:'00000000-0000-4000-8000-000000000001',candidateOrderId:'00000000-0000-4000-8000-000000000002'};
 eq((await handler(request(input,'Bearer app-publishable'))).status,401);eq(providerCalls,0);
 eq((await handler(request(input,'Bearer isolated-service','GET'))).status,405);
 eq((await handler(request({...input,candidateOrderId:'invalid-id'}))).status,400);
 env.QUEUEGO_LONGDO_KEY='';eq((await handler(request(input))).status,503);env.QUEUEGO_LONGDO_KEY='isolated-key';
 const response=await handler(request(input));eq(response.status,200);eq(await response.json(),{cached:true,expiresSeconds:90});
 eq(cachedBody.p_primary_order_id,input.primaryOrderId);eq(cachedBody.p_candidate_order_id,input.candidateOrderId);eq(Object.keys(cachedBody.p_edges).length,20);
 eq(cachedBody.p_primary_updated_at,'2026-10-15T00:00:00Z');
 globalThis.fetch=originalFetch;
 console.log(JSON.stringify({checks,failures:0,scope:'isolated Longdo adapter failure/coordinate/duration/concurrency guards; no live provider calls'}));
})().catch(e=>{console.error(e);process.exit(1)});
