const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CHARGE=/^chrg_[A-Za-z0-9]+$/;
function isLiveOmiseSecret(value){
 return typeof value==='string' && /^skey_(?:live_)?[A-Za-z0-9]+$/.test(value) && !value.startsWith('skey_test_');
}
async function boundedText(response,limit=131072){
 const reader=response.body?.getReader();if(!reader)throw Error('empty response');let size=0;const chunks=[];
 try{while(true){const {value,done}=await reader.read();if(done)break;size+=value.byteLength;if(size>limit)throw Error('payload too large');chunks.push(value)}}finally{await reader.cancel().catch(()=>{})}
 const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length}return new TextDecoder().decode(bytes);
}
async function boundedJson(response,limit=131072){return JSON.parse(await boundedText(response,limit))}
function base64Bytes(value){
 if(typeof value!=='string'||value.length<8||value.length>512)throw Error('invalid webhook secret');
 const raw=atob(value),bytes=new Uint8Array(raw.length);for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);return bytes;
}
function hexBytes(value){
 if(!/^[0-9a-f]{64}$/i.test(value||''))return null;
 const bytes=new Uint8Array(32);for(let i=0;i<32;i++)bytes[i]=parseInt(value.slice(i*2,i*2+2),16);return bytes;
}
async function verifyWebhookSignature(req,raw,secret){
 try{
  const timestampText=req.headers.get('omise-signature-timestamp')||'',timestamp=Number(timestampText);
  const signatures=(req.headers.get('omise-signature')||'').split(',').map(x=>x.trim()).filter(Boolean);
  if(!Number.isSafeInteger(timestamp)||Math.abs(Math.floor(Date.now()/1000)-timestamp)>300||!signatures.length)return false;
  const key=await crypto.subtle.importKey('raw',base64Bytes(secret),{name:'HMAC',hash:'SHA-256'},false,['verify']);
  const payload=new TextEncoder().encode(timestampText+'.'+raw);
  for(const signature of signatures){const bytes=hexBytes(signature);if(bytes&&await crypto.subtle.verify('HMAC',key,bytes,payload))return true}
  return false;
 }catch{return false}
}
export function createCreditHandler(env,fetcher=fetch,webhook=false){
 return async req=>{
  const cors={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,apikey,content-type,x-client-info','Access-Control-Allow-Methods':'POST,OPTIONS','Cache-Control':'no-store'};
  const reply=(body,status=200)=>Response.json(body,{status,headers:cors});
  if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
  const base=env.get('SUPABASE_URL'),service=env.get('SUPABASE_SERVICE_ROLE_KEY'),secret=env.get('OMISE_SECRET_KEY'),webhookSecret=env.get('OMISE_WEBHOOK_SECRET');
  let signingReady=false;
  try{signingReady=base64Bytes(webhookSecret).length>=16}catch{}
  // Server-to-server operational check: never returns secrets or creates charges.
  if(!webhook&&req.method==='GET'&&new URL(req.url).pathname.endsWith('/readiness')){
   if(!service||req.headers.get('authorization')!=='Bearer '+service)return reply({error:'unauthorized'},401);
   return reply({environment_ready:!!base&&!!service,live_key_configured:isLiveOmiseSecret(secret),webhook_signing_configured:signingReady,live_intake_enabled:env.get('QUEUEGO_CREDIT_LIVE_ENABLED')==='true'});
  }
  if(req.method!=='POST')return reply({error:'POST required'},405);
  // New topups require the rollout switch. Already-paid signed webhooks remain settleable during an intake pause.
  // New intake and settlement both require LIVE credentials and a usable signing secret.
  if((!webhook&&env.get('QUEUEGO_CREDIT_LIVE_ENABLED')!=='true')||!base||!service||!isLiveOmiseSecret(secret)||!signingReady)return reply({error:'ระบบเครดิตยังไม่เปิดใช้งาน'},503);
  const serviceHeaders={apikey:service,authorization:'Bearer '+service,'content-type':'application/json'};
  const providerHeaders={authorization:'Basic '+btoa(secret+':')};
  const rpc=async(name,body,authorization='Bearer '+service)=>{
   const response=await fetcher(base+'/rest/v1/rpc/'+name,{method:'POST',headers:{...serviceHeaders,authorization},body:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
   if(!response.ok)throw Error('credit context refused');return boundedJson(response);
  };
  const retrieve=async id=>{
   if(!CHARGE.test(id||''))throw Error('invalid charge');
   const response=await fetcher('https://api.omise.co/charges/'+id,{headers:providerHeaders,signal:AbortSignal.timeout(15000)});
   if(!response.ok)throw Error('provider unavailable');return boundedJson(response);
  };
  const recoverChargeId=async topupId=>{
   if(!UUID.test(topupId||''))throw Error('invalid topup');
   const query=new URLSearchParams({scope:'charge',query:topupId,per_page:'10',order:'reverse_chronological'});
   const response=await fetcher('https://api.omise.co/search?'+query.toString(),{headers:providerHeaders,signal:AbortSignal.timeout(15000)});
   if(!response.ok)return null;
   const result=await boundedJson(response),rows=Array.isArray(result?.data)?result.data:[];
   const matches=rows.filter(c=>c?.object==='charge'&&CHARGE.test(c.id||'')&&c.ref_id===topupId&&c.metadata?.queuego_credit_topup===topupId);
   if(matches.length>1)throw Error('ambiguous topup charge');
   return matches[0]?.id||null;
  };
  const apply=async charge=>{
   const id=charge.metadata?.queuego_credit_topup;
   if(!UUID.test(id||'')||!CHARGE.test(charge.id||'')||charge.object!=='charge'||charge.ref_id!==id||charge.livemode!==true||charge.currency!=='thb'||charge.source?.type!=='promptpay'||!Number.isSafeInteger(charge.amount)||!['pending','successful','failed','expired'].includes(charge.status))throw Error('charge mismatch');
   await rpc('queuego_credit_apply_charge',{p_topup:id,p_charge:charge.id,p_amount:charge.amount,p_currency:charge.currency,p_live:charge.livemode,p_status:charge.status,p_paid:charge.paid===true,p_qr:charge.source?.scannable_code?.image?.download_uri??null,p_expires:charge.expires_at??null});
   return charge;
  };
  try{
   const raw=await boundedText(req,webhook?131072:4096),input=JSON.parse(raw);
   if(webhook){
    // Verify Omise HMAC over the exact raw body first, then independently retrieve the charge.
    if(!await verifyWebhookSignature(req,raw,webhookSecret))return reply({error:'invalid webhook signature'},401);
    if(typeof input.key!=='string'||!input.key.startsWith('charge.')||!CHARGE.test(input.data?.id||''))return reply({received:true});
    await apply(await retrieve(input.data.id));return reply({received:true});
   }
   const authorization=req.headers.get('authorization')||'';
   if(!/^Bearer \S+$/.test(authorization)||authorization==='Bearer '+service)return reply({error:'กรุณาเข้าสู่ระบบ'},401);
   const auth=await fetcher(base+'/auth/v1/user',{headers:{apikey:service,authorization},signal:AbortSignal.timeout(8000)});
   if(!auth.ok)return reply({error:'กรุณาเข้าสู่ระบบใหม่'},401);
   const user=await boundedJson(auth);if(!UUID.test(user.id||''))return reply({error:'กรุณาเข้าสู่ระบบใหม่'},401);
   if(!['rider','merchant'].includes(input.kind)||!UUID.test(input.id||'')||!Number.isSafeInteger(input.amount_minor)||input.amount_minor<2000||input.amount_minor>10000000)return reply({error:'จำนวนเงินไม่ถูกต้อง'},400);
   // User JWT is passed through: SQL verifies active live-session, role and owner.
   const topup=await rpc('queuego_credit_begin_topup',{p_id:input.id,p_kind:input.kind,p_amount_minor:input.amount_minor},authorization);
   let charge;
   if(topup.charge_id)charge=await retrieve(topup.charge_id);
   else{
    const form=new URLSearchParams({amount:String(topup.amount_minor),currency:'thb','source[type]':'promptpay','source[qr_settings][image_type]':'png',ref_id:topup.id,'metadata[queuego_credit_topup]':topup.id,'webhook_endpoints[]':base+'/functions/v1/queuego-credit-webhook'});
    let response=null;
    try{response=await fetcher('https://api.omise.co/charges',{method:'POST',headers:{...providerHeaders,'content-type':'application/x-www-form-urlencoded'},body:form.toString(),signal:AbortSignal.timeout(15000)})}catch{}
    if(response?.ok){
     const created=await boundedJson(response);charge=await retrieve(created.id);
    }else{
     // ref_id prevents a duplicate charge. If create succeeded but its response/webhook was lost,
     // recover the existing charge through Omise Search using the durable topup UUID in metadata,
     // then retrieve it again before applying any credit.
     const recovered=await recoverChargeId(topup.id);
     if(!recovered)return reply({id:topup.id,status:'awaiting_verification',message:'กำลังตรวจสอบรายการเดิม กรุณาใช้รายการเดิมเพื่อลองใหม่'},202);
     charge=await retrieve(recovered);
    }
   }
   if(charge.metadata?.queuego_credit_topup!==topup.id)throw Error('topup ownership mismatch');
   await apply(charge);
   return reply({id:topup.id,status:charge.status,amount_minor:charge.amount,qr_url:charge.source?.scannable_code?.image?.download_uri??null,expires_at:charge.expires_at??null});
  }catch{return reply({error:'ยังยืนยันรายการไม่ได้ กรุณาตรวจรายการเดิมอีกครั้ง'},503)}
 };
}
