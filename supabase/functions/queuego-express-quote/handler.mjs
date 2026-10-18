import {validPoint,fetchRoadLeg} from '../queuego-route-bundle-matrix/road-matrix.mjs';

const cleanText=(value,min,max)=>typeof value==='string'&&value.trim().length>=min&&value.trim().length<=max?value.trim():null;

export function createExpressQuoteHandler(env,fetcher=fetch){
 return async req=>{
  const cors={
   'Access-Control-Allow-Origin':'*',
   'Access-Control-Allow-Headers':'authorization,apikey,content-type,x-client-info',
   'Access-Control-Allow-Methods':'POST,OPTIONS',
   'Cache-Control':'no-store'
  };
  const reply=(body,status=200)=>Response.json(body,{status,headers:cors});
  if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
  if(req.method!=='POST')return reply({error:'POST required'},405);

  const base=env.get('SUPABASE_URL');
  const service=env.get('SUPABASE_SERVICE_ROLE_KEY');
  const key=env.get('QUEUEGO_LONGDO_KEY');
  if(!base||!service||!key)return reply({error:'QueueGo Express ยังไม่พร้อม'},503);

  const authorization=req.headers.get('authorization')||'';
  if(!/^Bearer \S+$/.test(authorization)||authorization==='Bearer '+service)
   return reply({error:'กรุณาเข้าสู่ระบบลูกค้า'},401);

  try{
   const raw=await req.text();
   if(raw.length>8192)return reply({error:'คำขอใหญ่เกินไป'},400);
   let input;
   try{input=JSON.parse(raw)}catch{return reply({error:'คำขอไม่ถูกต้อง'},400)}

   const from=input?.pickup,to=input?.destination;
   if(!validPoint(from)||!validPoint(to)
      ||!cleanText(from?.address,3,500)||!cleanText(to?.address,3,500)
      ||(from.lat===to.lat&&from.lng===to.lng))
    return reply({error:'เลือกจุดรับและจุดส่งให้ครบ'},400);

   const senderName=cleanText(input?.sender?.name,1,120);
   const senderPhone=cleanText(input?.sender?.phone,5,30);
   const receiverName=cleanText(input?.receiver?.name,1,120);
   const receiverPhone=cleanText(input?.receiver?.phone,5,30);
   const itemType=cleanText(input?.item?.type,1,80);
   const itemSize=cleanText(input?.item?.size,1,50);
   const vehicleType=cleanText(input?.item?.vehicleType,1,20);
   const weightKg=Number(input?.item?.weightKg);
   const instructions=input?.item?.instructions==null?'':cleanText(input.item.instructions,0,500);
   if(!senderName||!senderPhone||!receiverName||!receiverPhone||!itemType||!itemSize
      ||!['motorcycle','car','saleng'].includes(vehicleType)
      ||!Number.isFinite(weightKg)||weightKg<=0||weightKg>1000
      ||instructions===null)
    return reply({error:'กรอกข้อมูลผู้ส่ง ผู้รับ และพัสดุให้ครบ'},400);

   const userResponse=await fetcher(base+'/auth/v1/user',{
    headers:{apikey:service,authorization},
    signal:AbortSignal.timeout(8000)
   });
   if(!userResponse.ok)return reply({error:'กรุณาเข้าสู่ระบบใหม่'},401);
   const user=await userResponse.json();
   if(typeof user.id!=='string'||!user.id)return reply({error:'กรุณาเข้าสู่ระบบใหม่'},401);

   const headers={apikey:service,authorization:'Bearer '+service,'content-type':'application/json'};
   const context=await fetcher(base+'/rest/v1/rpc/queuego_express_quote_context',{
    method:'POST',headers,
    body:JSON.stringify({
     p_auth_user_id:user.id,
     p_pickup_lat:from.lat,p_pickup_lng:from.lng,
     p_delivery_lat:to.lat,p_delivery_lng:to.lng
    }),
    signal:AbortSignal.timeout(8000)
   });
   if(!context.ok)return reply({error:'ยังส่ง Express ไม่ได้ ตรวจพื้นที่บริการหรืองานที่กำลังดำเนินการ'},409);

   const leg=await fetchRoadLeg(from,to,key,{fetcher,signal:AbortSignal.timeout(15000)});
   const cached=await fetcher(base+'/rest/v1/rpc/queuego_cache_express_quote',{
    method:'POST',headers,
    body:JSON.stringify({
     p_auth_user_id:user.id,
     p_pickup_address:from.address.trim(),p_delivery_address:to.address.trim(),
     p_pickup_lat:from.lat,p_pickup_lng:from.lng,
     p_delivery_lat:to.lat,p_delivery_lng:to.lng,
     p_sender_name:senderName,p_sender_phone:senderPhone,
     p_receiver_name:receiverName,p_receiver_phone:receiverPhone,
     p_item_type:itemType,p_weight_kg:weightKg,p_item_size:itemSize,p_vehicle_type:vehicleType,
     p_instructions:instructions||null,
     p_distance_km:leg.km,p_duration_minutes:leg.minutes
    }),
    signal:AbortSignal.timeout(8000)
   });
   if(!cached.ok)return reply({error:'ยังยืนยันราคา Express ไม่ได้ กรุณาลองใหม่'},409);
   const quote=await cached.json();
   if(!quote?.quote_id||!Number.isFinite(quote.fare)||quote.fare<=0)
    return reply({error:'ราคา Express ไม่พร้อม'},503);
   return reply(quote);
  }catch{
   return reply({error:'ตรวจเส้นทาง Express ไม่ได้ กรุณาลองใหม่'},503);
  }
 };
}
