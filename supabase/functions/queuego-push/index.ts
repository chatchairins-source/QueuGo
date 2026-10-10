import webpush from 'npm:web-push@3.6.7';
import { createClient } from 'npm:@supabase/supabase-js@2.49.10';

const url=Deno.env.get('SUPABASE_URL')!;
const admin=createClient(url,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{
  auth:{persistSession:false,autoRefreshToken:false}
});
const allowed=new Set([
  'https://chatchairins-source.github.io',
  'https://localhost',
  'http://localhost',
  'capacitor://localhost'
]);
const allowedRoles=new Set(['customer','shop','rider']);
const allowedPlatforms=new Set(['android','ios']);
let firebaseCache:{accessToken:string;expiresAt:number;projectId:string}|null=null;

function endpointOK(value:string){
  try{
    const u=new URL(value);
    return u.protocol==='https:'&&!u.username&&!u.password&&(!u.port||u.port==='443')&&(
      u.hostname==='fcm.googleapis.com'||
      u.hostname==='updates.push.services.mozilla.com'||
      u.hostname==='web.push.apple.com'||
      u.hostname.endsWith('.push.apple.com')||
      u.hostname.endsWith('.notify.windows.com')
    );
  }catch{return false}
}
function roleUrl(role:string,referenceId?:string|null){
  if(role==='shop') return '/QueuGo/merchant/#orders';
  if(role==='rider') return '/QueuGo/rider/#home';
  return referenceId?'/QueuGo/#order/'+encodeURIComponent(referenceId):'/QueuGo/#notifications';
}
function visibleFour(token:string,code:string){
  const upper=String(code||'').toUpperCase();
  if(/^(?:POS|QR)-/i.test(token)&&/^[0-9A-F]{4,}$/.test(upper))return String(parseInt(upper.slice(-4),16)%10000).padStart(4,'0');
  if(/^LW-/i.test(token)&&/^[0-9A-F]{4}$/.test(upper))return String(parseInt(upper,16)%10000).padStart(4,'0');
  if(/^\d{1,4}$/.test(upper))return upper.padStart(4,'0');
  if(/^[A-Z0-9]{1,4}$/.test(upper))return String(parseInt(upper,36)%10000).padStart(4,'0');
  return '----';
}
function visibleOrderText(value:unknown){
  return String(value??'').replace(
    /\b(?:QT|QO)-\d{8}-([A-Z0-9]{1,4})\b|\b(?:QT|QO)-([A-Z0-9]{1,4})\b|\bLW-\d{8}-\d{6}-([0-9A-F]{4})\b|\b(?:POS|QR)-([0-9A-F]{4,})\b/gi,
    (token,a,b,c,d)=>'QT-'+visibleFour(token,a||b||c||d)
  );
}
function b64url(value:Uint8Array|string){
  const bytes=typeof value==='string'?new TextEncoder().encode(value):value;
  let binary='';
  for(const b of bytes)binary+=String.fromCharCode(b);
  return btoa(binary).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
function pemBytes(pem:string){
  const raw=pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
  const binary=atob(raw),out=new Uint8Array(binary.length);
  for(let i=0;i<binary.length;i++)out[i]=binary.charCodeAt(i);
  return out;
}
async function firebaseAccess(){
  if(firebaseCache&&firebaseCache.expiresAt>Date.now()+60000)return firebaseCache;
  const raw=Deno.env.get('FIREBASE_SERVICE_ACCOUNT_JSON');
  if(!raw)throw new Error('firebase credentials unavailable');
  const service=JSON.parse(raw);
  if(!service?.client_email||!service?.private_key||!service?.project_id)throw new Error('invalid firebase credentials');
  const now=Math.floor(Date.now()/1000);
  const header=b64url(JSON.stringify({alg:'RS256',typ:'JWT'}));
  const claims=b64url(JSON.stringify({
    iss:service.client_email,
    scope:'https://www.googleapis.com/auth/firebase.messaging',
    aud:'https://oauth2.googleapis.com/token',
    iat:now,
    exp:now+3300
  }));
  const unsigned=header+'.'+claims;
  const key=await crypto.subtle.importKey(
    'pkcs8',
    pemBytes(service.private_key),
    {name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},
    false,
    ['sign']
  );
  const signature=new Uint8Array(await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(unsigned)
  ));
  const assertion=unsigned+'.'+b64url(signature);
  const response=await fetch('https://oauth2.googleapis.com/token',{
    method:'POST',
    headers:{'Content-Type':'application/x-www-form-urlencoded'},
    body:new URLSearchParams({
      grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion
    }),
    signal:AbortSignal.timeout(10000)
  });
  const data=await response.json().catch(()=>({}));
  if(!response.ok||!data.access_token)throw new Error('firebase oauth unavailable');
  firebaseCache={
    accessToken:data.access_token,
    expiresAt:Date.now()+Math.max(300,Number(data.expires_in||3600)-120)*1000,
    projectId:service.project_id
  };
  return firebaseCache;
}
async function sendNativePush(deviceToken:string,user:any,n:any){
  const firebase=await firebaseAccess();
  const data={
    notificationId:String(n.id||''),
    role:String(user.role||''),
    type:String(n.type||'notification'),
    referenceId:String(n.reference_id||'')
  };
  const response=await fetch(
    'https://fcm.googleapis.com/v1/projects/'+encodeURIComponent(firebase.projectId)+'/messages:send',
    {
      method:'POST',
      headers:{
        Authorization:'Bearer '+firebase.accessToken,
        'Content-Type':'application/json'
      },
      body:JSON.stringify({
        message:{
          token:deviceToken,
          notification:{
            title:visibleOrderText(n.title||'QueueGo').slice(0,80),
            body:visibleOrderText(n.message||'มีรายการอัปเดต').slice(0,220)
          },
          data,
          android:{
            priority:(user.role==='rider'||n.type==='voice_call')?'high':'normal',
            notification:{channel_id:'queuego_orders',sound:'default'}
          }
        }
      }),
      signal:AbortSignal.timeout(10000)
    }
  );
  await response.body?.cancel();
  return response.status;
}
async function config(){
  let {data,error}=await admin.from('qg_push_config').select('*').eq('id',true).maybeSingle();
  if(error)throw error;
  if(!data){
    const keys=webpush.generateVAPIDKeys();
    const insert=await admin.from('qg_push_config').upsert({
      id:true,public_key:keys.publicKey,private_key:keys.privateKey
    },{onConflict:'id',ignoreDuplicates:true});
    if(insert.error)throw insert.error;
    const result=await admin.from('qg_push_config').select('*').eq('id',true).single();
    if(result.error)throw result.error;
    data=result.data;
  }
  return data;
}
async function notificationAndUser(userId:string,notificationId:string){
  const [{data:n,error:nError},{data:user,error:uError}]=await Promise.all([
    admin.from('notifications').select('id,user_id,title,message,type,reference_id').eq('id',notificationId).single(),
    admin.from('users').select('role,status,auth_user_id').eq('id',userId).single()
  ]);
  if(nError||uError||!n||!user||n.user_id!==userId||user.status!=='active'||!allowedRoles.has(user.role)){
    return null;
  }
  return {n,user};
}

Deno.serve(async(req)=>{
  const origin=req.headers.get('origin');
  const cors={
    'Access-Control-Allow-Origin':origin&&allowed.has(origin)?origin:'https://chatchairins-source.github.io',
    'Vary':'Origin',
    'Access-Control-Allow-Headers':'authorization,apikey,content-type',
    'Access-Control-Allow-Methods':'POST,OPTIONS'
  };
  const respond=(body:unknown,status=200)=>Response.json(body,{status,headers:cors});
  if(origin&&!allowed.has(origin))return respond({error:'origin unavailable'},403);
  if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
  if(req.method!=='POST')return respond({error:'POST required'},405);

  try{
    const input=await req.json();
    if(input.action==='dispatch'){
      const c=await config();
      if(req.headers.get('x-queuego-worker')!==c.worker_token)return respond({error:'unauthorized'},401);
      const leased=await admin.rpc('qg_lease_push');
      if(leased.error)throw leased.error;
      let processed=0;
      await Promise.all((leased.data||[]).map(async(job:any)=>{
        let status=0;
        try{
          if(job.native_token_id){
            const {data:t}=await admin.from('qg_native_push_tokens').select('*').eq('id',job.native_token_id).single();
            if(!t?.enabled||!allowedPlatforms.has(t.platform)){
              status=410;
            }else{
              const context=await notificationAndUser(t.user_id,job.notification_id);
              status=!context||context.user.role!==t.role
                ?410
                :await sendNativePush(t.token,context.user,context.n);
            }
          }else{
            const {data:s}=await admin.from('qg_push_subscriptions').select('*').eq('id',job.subscription_id).single();
            if(!s?.enabled||!endpointOK(s.endpoint)){
              status=410;
            }else{
              const context=await notificationAndUser(s.user_id,job.notification_id);
              if(!context||context.user.role!==s.role){
                status=410;
              }else{
                const {n,user}=context;
                const payload=JSON.stringify({
                  notificationId:n.id,
                  userId:n.user_id,
                  authUserId:user.auth_user_id,
                  role:user.role,
                  title:visibleOrderText(n.title||'QueueGo'),
                  message:visibleOrderText(n.message||'มีรายการอัปเดต'),
                  type:n.type||'notification',
                  referenceId:n.reference_id||null,
                  url:roleUrl(user.role,n.reference_id)
                });
                const details=webpush.generateRequestDetails(
                  {endpoint:s.endpoint,keys:{p256dh:s.p256dh,auth:s.auth_key}},
                  payload,
                  {
                    TTL:3600,
                    urgency:user.role==='rider'?'high':'normal',
                    vapidDetails:{
                      subject:'https://chatchairins-source.github.io/QueuGo/',
                      publicKey:c.public_key,
                      privateKey:c.private_key
                    }
                  }
                );
                const response=await fetch(details.endpoint,{
                  method:details.method,
                  headers:details.headers,
                  body:details.body,
                  signal:AbortSignal.timeout(10000),
                  redirect:'error'
                });
                status=response.status;
                await response.body?.cancel();
              }
            }
          }
        }catch{status=503}
        finally{
          await admin.rpc('qg_finish_push',{p_id:job.id,p_lease:job.lease_id,p_http:status});
          processed++;
        }
      }));
      return respond({processed});
    }

    const token=req.headers.get('authorization')?.replace(/^Bearer\s+/i,'');
    if(!token)return respond({error:'login required'},401);
    const auth=await admin.auth.getUser(token);
    if(auth.error||!auth.data.user)return respond({error:'login required'},401);
    const {data:user}=await admin.from('users').select('id,role,status').eq('auth_user_id',auth.data.user.id).single();
    if(!user||user.status!=='active'||!allowedRoles.has(user.role))return respond({error:'active app account required'},403);

    if(input.action==='test'){
      const [{count:webCount,error:webError},{count:nativeCount,error:nativeError}]=await Promise.all([
        admin.from('qg_push_subscriptions').select('id',{count:'exact',head:true}).eq('user_id',user.id).eq('enabled',true),
        admin.from('qg_native_push_tokens').select('id',{count:'exact',head:true}).eq('user_id',user.id).eq('enabled',true)
      ]);
      if(webError||nativeError)throw webError||nativeError;
      if((webCount||0)+(nativeCount||0)===0)return respond({error:'เปิดการแจ้งเตือนก่อนทดสอบ'},409);
      if((nativeCount||0)>0){
        try{
          await firebaseAccess();
        }catch{
          return respond({error:'ระบบแจ้งเตือน Android ยังไม่พร้อม'},503);
        }
      }

      const since=new Date(Date.now()-60000).toISOString();
      const recent=await admin.from('notifications')
        .select('id',{count:'exact',head:true})
        .eq('user_id',user.id)
        .eq('type','push_test')
        .gte('created_at',since);
      if(recent.error)throw recent.error;
      if((recent.count||0)>0)return respond({ok:true,rateLimited:true});

      await new Promise(resolve=>setTimeout(resolve,7000));
      const created=await admin.from('notifications').insert({
        user_id:user.id,
        title:'ทดสอบการแจ้งเตือน QueueGo',
        message:'ถ้าคุณเห็นข้อความนี้ตอนแอปอยู่เบื้องหลัง ระบบแจ้งเตือนทำงานแล้ว',
        type:'push_test',
        is_read:false
      }).select('id').single();
      if(created.error)throw created.error;
      return respond({ok:true,notificationId:created.data.id,delaySeconds:7});
    }

    if(input.action==='public-key'){
      const c=await config();
      return respond({publicKey:c.public_key,role:user.role});
    }
    if(!/^[0-9a-f-]{36}$/i.test(input.deviceId||''))return respond({error:'invalid device'},400);

    if(input.action==='unsubscribe-native'){
      const result=await admin.from('qg_native_push_tokens')
        .update({enabled:false,updated_at:new Date().toISOString()})
        .eq('id',input.deviceId).eq('user_id',user.id);
      if(result.error)throw result.error;
      return respond({ok:true});
    }
    if(input.action==='subscribe-native'){
      const nativeToken=String(input.token||'').trim();
      const platform=String(input.platform||'').toLowerCase();
      if(nativeToken.length<16||nativeToken.length>4096||!allowedPlatforms.has(platform)){
        return respond({error:'invalid native token'},400);
      }
      // Native FCM registration must bootstrap qg_push_config as well.
      // qg_wake_push() reads its worker_token from this row before it can invoke the worker.
      await config();
      const deviceRow=await admin.from('qg_native_push_tokens').select('user_id').eq('id',input.deviceId).maybeSingle();
      if(deviceRow.error)throw deviceRow.error;
      if(deviceRow.data&&deviceRow.data.user_id!==user.id)return respond({error:'device unavailable'},403);
      const duplicate=await admin.from('qg_native_push_tokens').select('id,user_id').eq('token',nativeToken).maybeSingle();
      if(duplicate.error)throw duplicate.error;
      if(duplicate.data&&duplicate.data.user_id!==user.id)return respond({error:'token unavailable'},403);
      const id=duplicate.data?.id||input.deviceId;
      const result=await admin.from('qg_native_push_tokens').upsert({
        id,
        user_id:user.id,
        role:user.role,
        platform,
        token:nativeToken,
        enabled:true,
        updated_at:new Date().toISOString()
      },{onConflict:'id'});
      if(result.error)throw result.error;
      return respond({ok:true,deviceId:id,role:user.role});
    }

    if(input.action==='unsubscribe'){
      const result=await admin.from('qg_push_subscriptions')
        .update({enabled:false,updated_at:new Date().toISOString()})
        .eq('id',input.deviceId).eq('user_id',user.id);
      if(result.error)throw result.error;
      return respond({ok:true});
    }
    if(input.action!=='subscribe')return respond({error:'invalid action'},400);

    const s=input.subscription;
    if(!s||!endpointOK(s.endpoint)||!/^[-_A-Za-z0-9]{87}$/.test(s.keys?.p256dh||'')||!/^[-_A-Za-z0-9]{22}$/.test(s.keys?.auth||'')){
      return respond({error:'invalid subscription'},400);
    }

    await config();
    const deviceRow=await admin.from('qg_push_subscriptions').select('user_id,endpoint').eq('id',input.deviceId).maybeSingle();
    if(deviceRow.error)throw deviceRow.error;
    if(deviceRow.data&&deviceRow.data.user_id!==user.id){
      return respond({error:'device unavailable'},403);
    }
    const duplicate=await admin.from('qg_push_subscriptions').select('id,user_id,p256dh,auth_key').eq('endpoint',s.endpoint).maybeSingle();
    if(duplicate.error)throw duplicate.error;
    if(duplicate.data&&duplicate.data.user_id!==user.id)return respond({error:'subscription unavailable'},403);
    if(duplicate.data&&(duplicate.data.p256dh!==s.keys.p256dh||duplicate.data.auth_key!==s.keys.auth)){
      return respond({error:'subscription unavailable'},403);
    }
    const id=duplicate.data?.id||input.deviceId;
    const result=await admin.from('qg_push_subscriptions').upsert({
      id,
      user_id:user.id,
      role:user.role,
      endpoint:s.endpoint,
      p256dh:s.keys.p256dh,
      auth_key:s.keys.auth,
      enabled:true,
      updated_at:new Date().toISOString()
    },{onConflict:'id'});
    if(result.error)throw result.error;
    return respond({ok:true,deviceId:id,role:user.role});
  }catch{
    return respond({error:'บริการแจ้งเตือนยังไม่พร้อม กรุณาลองใหม่'},503);
  }
});