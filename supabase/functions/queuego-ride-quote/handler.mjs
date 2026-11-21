import {validPoint,fetchRoadLeg} from '../queuego-route-bundle-matrix/road-matrix.mjs';
export function createRideQuoteHandler(env,fetcher=fetch){
 return async req=>{
  const cors={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,apikey,content-type,x-client-info','Access-Control-Allow-Methods':'POST,OPTIONS','Cache-Control':'no-store'};
  const reply=(body,status=200)=>Response.json(body,{status,headers:cors});
  if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
  if(req.method!=='POST')return reply({error:'POST required'},405);
  const base=env.get('SUPABASE_URL'),service=env.get('SUPABASE_SERVICE_ROLE_KEY'),key=env.get('QUEUEGO_LONGDO_KEY');
  if(!base||!service||!key)return reply({error:'บริการเรียกรถยังไม่พร้อม'},503);
  const authorization=req.headers.get('authorization')||'';
  if(!/^Bearer \S+$/.test(authorization)||authorization==='Bearer '+service)return reply({error:'กรุณาเข้าสู่ระบบลูกค้า'},401);
  try{
   const raw=await req.text();if(raw.length>4096)return reply({error:'คำขอใหญ่เกินไป'},400);
   let input;try{input=JSON.parse(raw)}catch{return reply({error:'คำขอไม่ถูกต้อง'},400)}
   const from=input?.pickup,to=input?.destination;
   if(!validPoint(from)||!validPoint(to)||typeof from.address!=='string'||typeof to.address!=='string'||[from,to].some(p=>p.address.trim().length<3||p.address.length>500)||from.lat===to.lat&&from.lng===to.lng)return reply({error:'เลือกจุดรับและจุดหมายให้ครบ'},400);
   const userResponse=await fetcher(base+'/auth/v1/user',{headers:{apikey:service,authorization},signal:AbortSignal.timeout(8000)});
   if(!userResponse.ok)return reply({error:'กรุณาเข้าสู่ระบบใหม่'},401);
   const user=await userResponse.json();if(typeof user.id!=='string'||!user.id)return reply({error:'กรุณาเข้าสู่ระบบใหม่'},401);
   const headers={apikey:service,authorization:'Bearer '+service,'content-type':'application/json'};
   const context=await fetcher(base+'/rest/v1/rpc/queuego_ride_quote_context',{method:'POST',headers,body:JSON.stringify({p_auth_user_id:user.id,p_pickup_lat:from.lat,p_pickup_lng:from.lng,p_delivery_lat:to.lat,p_delivery_lng:to.lng}),signal:AbortSignal.timeout(8000)});
   if(!context.ok)return reply({error:'ยังเรียกรถไม่ได้ ตรวจพื้นที่บริการหรือเที่ยวที่กำลังดำเนินการ'},409);
   const leg=await fetchRoadLeg(from,to,key,{fetcher,signal:AbortSignal.timeout(15000)});
   const cached=await fetcher(base+'/rest/v1/rpc/queuego_cache_ride_quote',{method:'POST',headers,body:JSON.stringify({p_auth_user_id:user.id,p_pickup_address:from.address.trim(),p_delivery_address:to.address.trim(),p_pickup_lat:from.lat,p_pickup_lng:from.lng,p_delivery_lat:to.lat,p_delivery_lng:to.lng,p_distance_km:leg.km,p_duration_minutes:leg.minutes}),signal:AbortSignal.timeout(8000)});
   if(!cached.ok)return reply({error:'ยังยืนยันราคาไม่ได้ กรุณาลองใหม่'},409);
   const quote=await cached.json();if(!quote?.quote_id||!Number.isFinite(quote.fare)||quote.fare<=0)return reply({error:'ราคาไม่พร้อม'},503);
   return reply(quote);
  }catch{return reply({error:'ตรวจเส้นทางไม่ได้ กรุณาลองใหม่'},503)}
 };
}
