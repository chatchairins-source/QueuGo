// Service-only evidence producer for existing SQL dispatch/route-bundle quote, never an assigner.
import {buildRoadMatrix} from './road-matrix.mjs';
import {planStackRoute} from './stack-planner.mjs';
import {simulateStackFinancialPlan} from './stack-financial-plan.mjs';
import {refreshRouteMatrices,workerTokenMatches} from './refresh.mjs';
const uuid=v=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
export function createRouteBundleMatrixHandler(env,fetcher=fetch){
return async(req)=>{
 const service=env.get('SUPABASE_SERVICE_ROLE_KEY')??'';
 const base=env.get('SUPABASE_URL')??'';
 const key=env.get('QUEUEGO_LONGDO_KEY')??'';
 const reply=(value,status=200)=>Response.json(value,{status,headers:{'Cache-Control':'no-store'}});
 if(req.method!=='POST')return reply({error:'POST required'},405);
 if(!service||(req.headers.get('authorization')!=='Bearer '+service&&
   !await workerTokenMatches(req.headers.get('x-queuego-route-worker'),env.get('QUEUEGO_ROUTE_MATRIX_WORKER_TOKEN'))))return reply({error:'service authorization required'},401);
 if(!base)return reply({error:'routing not configured'},503);
 const headers={apikey:service,authorization:'Bearer '+service,'content-type':'application/json'};
 try{
  const input=await req.json();
  if(input.action==='plan'||input.action==='quote'){
   if(!uuid(input.primaryOrderId)||!uuid(input.candidateOrderId)||input.primaryOrderId===input.candidateOrderId)return reply({error:'invalid orders'},400);
   const planRes=await fetcher(base+'/rest/v1/rpc/queuego_route_bundle_stack_financial_input',{method:'POST',headers,signal:AbortSignal.timeout(8000),body:JSON.stringify({
    p_primary_order_id:input.primaryOrderId,p_candidate_order_id:input.candidateOrderId
   })});
   if(!planRes.ok)throw Error('stack plan input read failed');
   const evidence=await planRes.json();
   if(!evidence||typeof evidence!=='object')return reply({cached:false,readOnly:true},409);
   const plan=planStackRoute(evidence);
   const generalized=Array.isArray(evidence.orderIds)&&evidence.orderIds.length>=3;
   const financial=generalized
     ? simulateStackFinancialPlan(evidence,plan)
     : {mode:'existing-two-order-live-quote',generalized:false};
   if(input.action==='plan')return reply({cached:true,readOnly:true,...plan,financial});
   if(!generalized)return reply({error:'two-order quote remains on existing live authority',quoteCached:false},409);
   if(!financial?.eligible)return reply({cached:true,quoteCached:false,readOnlyBusinessState:true,...plan,financial},409);
   const stopPlan=[...(Array.isArray(evidence.committedPrefix)?evidence.committedPrefix:[]),
    ...plan.stops.map(stop=>({order_id:stop.orderId,kind:stop.kind}))];
   const cacheRes=await fetcher(base+'/rest/v1/rpc/queuego_cache_route_bundle_stack_quote',{method:'POST',headers,signal:AbortSignal.timeout(8000),body:JSON.stringify({
    p_primary_order_id:input.primaryOrderId,p_candidate_order_id:input.candidateOrderId,
    p_stop_plan:stopPlan,p_financial:financial
   })});
   if(!cacheRes.ok)throw Error('stack quote cache failed');
   const quoteId=await cacheRes.json();
   if(!uuid(quoteId))return reply({cached:true,quoteCached:false,readOnlyBusinessState:true,...plan,financial},409);
   const offerRes=await fetcher(base+'/rest/v1/rpc/queuego_offer_route_bundle_stack_quote',{method:'POST',headers,signal:AbortSignal.timeout(8000),body:JSON.stringify({
    p_quote_id:quoteId
   })});
   if(!offerRes.ok)throw Error('stack targeted offer publish failed');
   const offerPublished=await offerRes.json();
   return reply({cached:true,quoteCached:true,offerPublished:offerPublished===true,readOnlyBusinessState:true,quoteId,...plan,financial});
  }
  if(!key)return reply({error:'routing not configured'},503);
  if(input.action==='refresh'){
   const produce=pair=>createRouteBundleMatrixHandler(env,fetcher)(new Request(req.url,{
    method:'POST',headers:{authorization:'Bearer '+service,'content-type':'application/json'},body:JSON.stringify(pair)}));
   return reply(await refreshRouteMatrices(base,headers,fetcher,produce));
  }
  if(!uuid(input.primaryOrderId)||!uuid(input.candidateOrderId)||input.primaryOrderId===input.candidateOrderId)return reply({error:'invalid orders'},400);
  const fields='id,rider_id,status,updated_at,route_bundle_id,route_bundle_sequence,created_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude';
  const res=await fetcher(base+'/rest/v1/orders?select='+fields+'&id=in.('+input.primaryOrderId+','+input.candidateOrderId+')',{headers,signal:AbortSignal.timeout(8000)});
  if(!res.ok)throw Error('order read failed');
  const rows=await res.json();const a=rows.find(r=>r.id===input.primaryOrderId),b=rows.find(r=>r.id===input.candidateOrderId);
  if(!a?.rider_id||!b||b.rider_id||b.status!=='searching_rider')return reply({cached:false},409);
  const rr=await fetcher(base+'/rest/v1/rider_profiles?select=latitude,longitude&id=eq.'+a.rider_id,{headers,signal:AbortSignal.timeout(8000)});
  if(!rr.ok)throw Error('rider read failed');const rider=(await rr.json())[0];
  const point=(o,prefix)=>({lat:o[prefix+'_latitude'],lng:o[prefix+'_longitude']});
  if(input.action==='stack'){
   const activeRes=await fetcher(base+'/rest/v1/orders?select='+fields+'&rider_id=eq.'+a.rider_id+
    '&status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)&order=route_bundle_sequence.asc.nullslast,created_at.asc,id.asc',
    {headers,signal:AbortSignal.timeout(8000)});
   if(!activeRes.ok)throw Error('active bundle read failed');
   let active=await activeRes.json();
   active=active.filter(o=>o.id===a.id||(a.route_bundle_id&&o.route_bundle_id===a.route_bundle_id));
   if(!active.some(o=>o.id===a.id)||active.length<1||active.length>4)return reply({cached:false},409);
   const orders=[...active,b];
   if(new Set(orders.map(o=>o.id)).size!==orders.length||orders.length<2||orders.length>5)return reply({cached:false},409);
   const points={R:{lat:rider?.latitude,lng:rider?.longitude}},pointMap={R:{kind:'rider'}};
   orders.forEach((o,index)=>{
    const p='O'+index+'P',d='O'+index+'D';
    points[p]=point(o,'pickup');points[d]=point(o,'delivery');
    pointMap[p]={orderId:o.id,kind:'pickup'};pointMap[d]={orderId:o.id,kind:'dropoff'};
   });
   const edges=await buildRoadMatrix(points,key,{fetcher,signal:AbortSignal.timeout(120000)});
   const cached=await fetcher(base+'/rest/v1/rpc/queuego_cache_route_bundle_stack_matrix',{method:'POST',headers,signal:AbortSignal.timeout(8000),body:JSON.stringify({
    p_primary_order_id:a.id,p_candidate_order_id:b.id,p_order_ids:orders.map(o=>o.id),
    p_order_revisions:Object.fromEntries(orders.map(o=>[o.id,o.updated_at])),
    p_point_map:pointMap,p_rider_latitude:rider.latitude,p_rider_longitude:rider.longitude,p_edges:edges
   })});
   if(!cached.ok)throw Error('stack evidence rejected');
   const acknowledged=await cached.json();
   if(acknowledged===true&&orders.length>=3&&input.quoteAfterCache===true){
    const quoteResponse=await createRouteBundleMatrixHandler(env,fetcher)(new Request(req.url,{
     method:'POST',headers:{authorization:'Bearer '+service,'content-type':'application/json'},
     body:JSON.stringify({action:'quote',primaryOrderId:a.id,candidateOrderId:b.id})
    }));
    const quoteBody=await quoteResponse.json();
    return reply({cached:true,expiresSeconds:90,orders:orders.length,points:Object.keys(points).length,
     quoteCached:quoteBody.quoteCached===true,offerPublished:quoteBody.offerPublished===true,
     quoteReason:quoteBody.financial?.reason??null});
   }
   return reply({cached:acknowledged===true,expiresSeconds:90,orders:orders.length,points:Object.keys(points).length},acknowledged===true?200:409);
  }
  const edges=await buildRoadMatrix({AP:point(a,'pickup'),AD:point(a,'delivery'),BP:point(b,'pickup'),BD:point(b,'delivery'),RP:{lat:rider?.latitude,lng:rider?.longitude}},key,{fetcher,signal:AbortSignal.timeout(45000)});
  const cached=await fetcher(base+'/rest/v1/rpc/queuego_cache_route_bundle_matrix',{method:'POST',headers,signal:AbortSignal.timeout(8000),body:JSON.stringify({
   p_primary_order_id:a.id,p_candidate_order_id:b.id,p_primary_updated_at:a.updated_at,p_candidate_updated_at:b.updated_at,
   p_rider_latitude:rider.latitude,p_rider_longitude:rider.longitude,p_edges:edges
  })});
  if(!cached.ok)throw Error('evidence rejected');
  const acknowledged=await cached.json();return reply({cached:acknowledged===true,expiresSeconds:90},acknowledged===true?200:409);
 }catch{
  // No credentials, provider URLs, order coordinates or user data in errors/logs.
  return reply({error:'road evidence unavailable; existing jobs unchanged'},503);
 }
};
}
