const assert=require('node:assert/strict');
(async()=>{
 const {parseRoadLeg,buildRoadMatrix}=await import('../supabase/functions/queuego-route-bundle-matrix/road-matrix.mjs');
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const body={meta:{status:200},data:{distance:1000,interval:120},features:[{geometry:{type:'LineString',coordinates:[[103,15],[103.01,15]]},properties:{distance:1000,interval:120}}]};
 eq(parseRoadLeg(body),{km:1,minutes:2});
 eq(parseRoadLeg({...body,data:undefined}),{km:1,minutes:2});
 for(const invalid of [null,{}, {...body,meta:{status:500}}, {...body,features:[]}, {...body,features:[{properties:{distance:1,interval:1}}]}, {...body,data:undefined,features:[{...body.features[0],properties:{distance:1000}}]}, {...body,data:{distance:NaN,interval:NaN},features:[{...body.features[0],properties:{distance:-1,interval:-1}}]}]){assert.throws(()=>parseRoadLeg(invalid));checks++;}
 const points={AP:{lat:15,lng:103},AD:{lat:15.01,lng:103},BP:{lat:15,lng:103},BD:{lat:15.02,lng:103},RP:{lat:15,lng:103}};
 const eleven=Object.fromEntries(Array.from({length:11},(_,i)=>['P'+i,{lat:15+i/1000,lng:103+i/1000}]));
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
 const originalFetch=globalThis.fetch;let cachedBody=null,stackCachedBody=null,providerCalls=0,stackMode=false;
 globalThis.fetch=async(url,options)=>{
  const u=new URL(url);
  if(u.hostname==='api.longdo.com'){providerCalls++;return {ok:true,text:async()=>JSON.stringify(body)}}
  assert.equal(options.headers.authorization,'Bearer isolated-service');
  if(u.pathname==='/rest/v1/orders'){
   if(stackMode&&u.searchParams.has('rider_id'))return {ok:true,json:async()=>[
    {id:'00000000-0000-4000-8000-000000000001',rider_id:'rider-id',status:'ready',updated_at:'2026-10-15T00:00:00Z',route_bundle_id:'bundle-id',route_bundle_sequence:1,created_at:'2026-10-15T00:00:00Z',pickup_latitude:15,pickup_longitude:103,delivery_latitude:15.01,delivery_longitude:103},
    {id:'00000000-0000-4000-8000-000000000003',rider_id:'rider-id',status:'ready',updated_at:'2026-10-15T00:00:00Z',route_bundle_id:'bundle-id',route_bundle_sequence:2,created_at:'2026-10-15T00:00:01Z',pickup_latitude:15.002,pickup_longitude:103.002,delivery_latitude:15.012,delivery_longitude:103.002},
    {id:'00000000-0000-4000-8000-000000000004',rider_id:'rider-id',status:'ready',updated_at:'2026-10-15T00:00:00Z',route_bundle_id:'bundle-id',route_bundle_sequence:3,created_at:'2026-10-15T00:00:02Z',pickup_latitude:15.003,pickup_longitude:103.003,delivery_latitude:15.013,delivery_longitude:103.003},
    {id:'00000000-0000-4000-8000-000000000005',rider_id:'rider-id',status:'ready',updated_at:'2026-10-15T00:00:00Z',route_bundle_id:'bundle-id',route_bundle_sequence:4,created_at:'2026-10-15T00:00:03Z',pickup_latitude:15.004,pickup_longitude:103.004,delivery_latitude:15.014,delivery_longitude:103.004}]};
   return {ok:true,json:async()=>[
    {id:'00000000-0000-4000-8000-000000000001',rider_id:'rider-id',status:'in_progress',updated_at:'2026-10-15T00:00:00Z',route_bundle_id:stackMode?'bundle-id':null,route_bundle_sequence:1,created_at:'2026-10-15T00:00:00Z',pickup_latitude:15,pickup_longitude:103,delivery_latitude:15.01,delivery_longitude:103},
    {id:'00000000-0000-4000-8000-000000000002',rider_id:null,status:'searching_rider',updated_at:'2026-10-15T00:00:00Z',route_bundle_id:null,route_bundle_sequence:null,created_at:'2026-10-15T00:00:10Z',pickup_latitude:15,pickup_longitude:103,delivery_latitude:15.02,delivery_longitude:103}]};
  }
  if(u.pathname==='/rest/v1/rider_profiles')return {ok:true,json:async()=>[{latitude:15,longitude:103}]};
  if(u.pathname==='/rest/v1/rpc/queuego_cache_route_bundle_matrix'){cachedBody=JSON.parse(options.body);return {ok:true,json:async()=>true}}
  if(u.pathname==='/rest/v1/rpc/queuego_cache_route_bundle_stack_matrix'){stackCachedBody=JSON.parse(options.body);return {ok:true,json:async()=>true}}
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
 stackMode=true;providerCalls=0;
 const stackResponse=await handler(request({...input,action:'stack'}));eq(stackResponse.status,200);
 eq(await stackResponse.json(),{cached:true,expiresSeconds:90,orders:5,points:11});
 eq(stackCachedBody.p_order_ids.length,5);eq(Object.keys(stackCachedBody.p_point_map).length,11);eq(Object.keys(stackCachedBody.p_edges).length,110);
 assert(providerCalls<=110);checks++;
 stackMode=false;
 let stackCalls=0;
 const many=await buildRoadMatrix(eleven,'isolated-key',{fetcher:async()=>{stackCalls++;return {ok:true,text:async()=>JSON.stringify(body)}}});
 eq(Object.keys(many).length,110);eq(stackCalls,110);
 await assert.rejects(buildRoadMatrix({...eleven,P11:{lat:15.02,lng:103.02}},'isolated-key',{fetcher}));checks++;
 const {workerTokenMatches,refreshRouteMatrices}=await import('../supabase/functions/queuego-route-bundle-matrix/refresh.mjs');
 const worker='isolated-worker-token-32-characters-minimum';
 eq(await workerTokenMatches(worker,worker),true);eq(await workerTokenMatches(worker+'x',worker),false);
 eq(await workerTokenMatches(null,worker),false);eq(await workerTokenMatches('short','short'),false);
 let releases=0,produced=0;
 const coordinator=async(url,options)=>({ok:true,json:async()=>url.endsWith('_claim')?{leaseId:'isolated-lease',pairs:[input,input]}:(releases++,true)});
 eq(await refreshRouteMatrices('https://backend.invalid',{},coordinator,async()=>({ok:++produced===1})),{refreshed:1,failed:1});eq(releases,1);
 produced=0;
 eq(await refreshRouteMatrices('https://backend.invalid',{},async()=>({ok:true,json:async()=>({})}),async()=>{produced++;}),{refreshed:0,failed:0,busyOrDisabled:true});eq(produced,0);
 releases=0;
 eq(await refreshRouteMatrices('https://backend.invalid',{},coordinator,async()=>{throw Error('network lost')}),{refreshed:0,failed:2});eq(releases,1);
 await assert.rejects(refreshRouteMatrices('https://backend.invalid',{},async()=>({ok:false}),async()=>({ok:true})),/coordination/);checks++;
 const pairTransport=globalThis.fetch;let refreshReleased=0;
 const workerTransport=async(url,options)=>{
  if(new URL(url).pathname.endsWith('/queuego_route_matrix_refresh_claim'))return {ok:true,json:async()=>({leaseId:'isolated-lease',pairs:[input]})};
  if(new URL(url).pathname.endsWith('/queuego_route_matrix_refresh_release')){refreshReleased++;return {ok:true,json:async()=>true};}
  return pairTransport(url,options);
 };
 env.QUEUEGO_ROUTE_MATRIX_WORKER_TOKEN=worker;
 const workerHandler=createRouteBundleMatrixHandler({get:k=>env[k]},workerTransport);
 const workerRequest=token=>new Request('https://edge.invalid',{method:'POST',headers:{'x-queuego-route-worker':token,'content-type':'application/json'},body:JSON.stringify({action:'refresh'})});
 eq((await workerHandler(workerRequest('invalid-worker-token'))).status,401);
 const refreshed=await workerHandler(workerRequest(worker));eq(refreshed.status,200);eq(await refreshed.json(),{refreshed:1,failed:0});eq(refreshReleased,1);
 const fs=require('node:fs');
 const phase2=fs.readFileSync('supabase/migrations/20261018060000_route_bundle_multi_stop_matrix_phase2.sql','utf8');
 assert(phase2.includes('qg_private.route_bundle_stack_matrices')&&phase2.includes('queuego_cache_route_bundle_stack_matrix'));checks++;
 assert(phase2.includes("::timestamptz is distinct from")&&!phase2.includes("updated_at#>>")&&!phase2.includes("updated_at::text"));checks++;
 assert(!phase2.includes('create or replace function public.queuego_claim_route_bundle')&&!phase2.includes("values('feature.route_bundle'")&&!phase2.includes("route_bundle.max_orders','5"));checks++;

 // Execute the Phase 2 cache migration against an isolated PostgreSQL-compatible engine.
 const {PGlite}=require('@electric-sql/pglite');
 const sqlDb=new PGlite();
 await sqlDb.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 await sqlDb.exec("create function auth.role() returns text language sql as $select coalesce(nullif(current_setting('test.role',true),''),'authenticated')$;");
 await sqlDb.exec(phase2);
 const rev='2026-10-18T00:00:00Z';
 const stackIds=[
  '00000000-0000-4000-8000-000000000070',
  '00000000-0000-4000-8000-000000000071',
  '00000000-0000-4000-8000-000000000072',
  '00000000-0000-4000-8000-000000000073',
  '00000000-0000-4000-8000-000000000074'
 ];
 for(let i=0;i<4;i++)await sqlDb.query(
  "insert into orders(id,order_number,rider_id,customer_id,shop_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,$2,$3,$4,$5,'ready',$6,15,103,15.01,103)",
  [stackIds[i],'QT-S'+i,'00000000-0000-4000-8000-000000000011','00000000-0000-4000-8000-000000000060','00000000-0000-4000-8000-000000000050',rev]
 );
 await sqlDb.query(
  "insert into orders(id,order_number,customer_id,shop_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-C',$2,$3,'searching_rider',$4,15,103,15.02,103)",
  [stackIds[4],'00000000-0000-4000-8000-000000000060','00000000-0000-4000-8000-000000000050',rev]
 );
 const stackPointMap={R:{kind:'rider'}},stackPoints=['R'];
 stackIds.forEach((orderId,index)=>{
  for(const [suffix,kind] of [['P','pickup'],['D','dropoff']]){
   const key='O'+index+suffix;stackPoints.push(key);stackPointMap[key]={orderId,kind};
  }
 });
 const stackEdges=Object.fromEntries(stackPoints.flatMap(a=>stackPoints.filter(b=>b!==a).map(b=>[a+'|'+b,{km:0.2,minutes:0.5}])));
 const stackRevisions=Object.fromEntries(stackIds.map(orderId=>[orderId,rev]));
 await sqlDb.query("select set_config('test.role','service_role',false)");
 let cached=(await sqlDb.query(
  "select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok",
  [stackIds[0],stackIds[4],stackIds,JSON.stringify(stackRevisions),JSON.stringify(stackPointMap),JSON.stringify(stackEdges)]
 )).rows[0].ok;
 eq(cached,true);
 eq((await sqlDb.query("select cardinality(order_ids)::int orders,jsonb_object_length(point_map)::int points,jsonb_object_length(edges)::int edges from qg_private.route_bundle_stack_matrices where primary_order_id=$1 and candidate_order_id=$2",[stackIds[0],stackIds[4]])).rows[0],{orders:5,points:11,edges:110});
 await sqlDb.query("update orders set updated_at=$2 where id=$1",[stackIds[4],'2026-10-18T00:00:01Z']);
 cached=(await sqlDb.query(
  "select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb) ok",
  [stackIds[0],stackIds[4],stackIds,JSON.stringify(stackRevisions),JSON.stringify(stackPointMap),JSON.stringify(stackEdges)]
 )).rows[0].ok;
 eq(cached,false);
 await sqlDb.query("select set_config('test.role','authenticated',false)");
 await assert.rejects(
  sqlDb.query("select queuego_cache_route_bundle_stack_matrix($1,$2,$3::uuid[],$4::jsonb,$5::jsonb,15,103,$6::jsonb)",
   [stackIds[0],stackIds[4],stackIds,JSON.stringify(stackRevisions),JSON.stringify(stackPointMap),JSON.stringify(stackEdges)]),
  /service role required/
 );checks++;
 await sqlDb.close();

 globalThis.fetch=originalFetch;
 console.log(JSON.stringify({checks,failures:0,scope:'isolated road adapter + service-only 5-order stack matrix groundwork; live claim/cap/feature flags unchanged; no live provider calls'}));
})().catch(e=>{console.error(e);process.exit(1)});
