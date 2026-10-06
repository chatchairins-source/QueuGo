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
          const [{data:s},{data:n}]=await Promise.all([
            admin.from('qg_push_subscriptions').select('*').eq('id',job.subscription_id).single(),
            admin.from('notifications').select('id,user_id,title,message,type,reference_id').eq('id',job.notification_id).single()
          ]);
          if(!s?.enabled||!n||s.user_id!==n.user_id||!endpointOK(s.endpoint)){
            status=410;
          }else{
            const {data:user}=await admin.from('users').select('role,status').eq('id',s.user_id).single();
            if(!user||user.status!=='active'||user.role!==s.role||!allowedRoles.has(user.role)){
              status=410;
            }else{
              const payload=JSON.stringify({
                notificationId:n.id,
                userId:n.user_id,
                role:user.role,
                title:n.title||'QueueGo',
                message:n.message||'มีรายการอัปเดต',
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
        }catch{status=0}
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

    if(input.action==='public-key'){
      const c=await config();
      return respond({publicKey:c.public_key,role:user.role});
    }
    if(!/^[0-9a-f-]{36}$/i.test(input.deviceId||''))return respond({error:'invalid device'},400);

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
    if(deviceRow.data&&deviceRow.data.user_id!==user.id&&deviceRow.data.endpoint!==s.endpoint){
      return respond({error:'device unavailable'},403);
    }
    const duplicate=await admin.from('qg_push_subscriptions').select('id,user_id,p256dh,auth_key').eq('endpoint',s.endpoint).maybeSingle();
    if(duplicate.error)throw duplicate.error;
    if(duplicate.data&&duplicate.data.user_id!==user.id)return respond({error:'subscription unavailable'},403);
    if(duplicate.data&&(duplicate.data.p256dh!==s.keys.p256dh||duplicate.data.auth_key!==s.keys.auth)){
      return respond({error:'subscription unavailable'},403);
    }
    const result=await admin.from('qg_push_subscriptions').upsert({
      id:duplicate.data?.id||input.deviceId,
      user_id:user.id,
      role:user.role,
      endpoint:s.endpoint,
      p256dh:s.keys.p256dh,
      auth_key:s.keys.auth,
      enabled:true,
      updated_at:new Date().toISOString()
    },{onConflict:'id'});
    if(result.error)throw result.error;
    return respond({ok:true,deviceId:duplicate.data?.id||input.deviceId,role:user.role});
  }catch{
    return respond({error:'บริการแจ้งเตือนยังไม่พร้อม กรุณาลองใหม่'},503);
  }
});