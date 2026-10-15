// Service-only evidence producer for existing SQL dispatch/route-bundle quote, never an assigner.
import {buildRoadMatrix} from './road-matrix.mjs';
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
 if(!key||!base)return reply({error:'routing not configured'},503);
 const headers={apikey:service,authorization:'Bearer '+service,'content-type':'application/json'};
 try{
  const input=await req.json();
  if(input.action==='refresh'){
   const produce=pair=>createRouteBundleMatrixHandler(env,fetcher)(new Request(req.url,{
    method:'POST',headers:{authorization:'Bearer '+service,'content-type':'application/json'},body:JSON.stringify(pair)}));
   return reply(await refreshRouteMatrices(base,headers,fetcher,produce));
  }
  if(!uuid(input.primaryOrderId)||!uuid(input.candidateOrderId)||input.primaryOrderId===input.candidateOrderId)return reply({error:'invalid orders'},400);
  const fields='id,rider_id,status,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude';
  const res=await fetcher(base+'/rest/v1/orders?select='+fields+'&id=in.('+input.primaryOrderId+','+input.candidateOrderId+')',{headers,signal:AbortSignal.timeout(8000)});
  if(!res.ok)throw Error('order read failed');
  const rows=await res.json();const a=rows.find(r=>r.id===input.primaryOrderId),b=rows.find(r=>r.id===input.candidateOrderId);
  if(!a?.rider_id||!b||b.rider_id||b.status!=='searching_rider')return reply({cached:false},409);
  const rr=await fetcher(base+'/rest/v1/rider_profiles?select=latitude,longitude&id=eq.'+a.rider_id,{headers,signal:AbortSignal.timeout(8000)});
  if(!rr.ok)throw Error('rider read failed');const rider=(await rr.json())[0];
  const point=(o,prefix)=>({lat:o[prefix+'_latitude'],lng:o[prefix+'_longitude']});
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
