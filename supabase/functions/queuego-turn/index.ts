const supabaseUrl=Deno.env.get('SUPABASE_URL')!;
const anonKey=Deno.env.get('SUPABASE_ANON_KEY')!;
const turnKeyId=Deno.env.get('CLOUDFLARE_TURN_KEY_ID')||'';
const turnApiToken=Deno.env.get('CLOUDFLARE_TURN_KEY_API_TOKEN')||'';

const allowedOrigins=new Set([
  'https://chatchairins-source.github.io',
  'https://localhost',
  'http://localhost',
  'capacitor://localhost'
]);

function uuid(value:unknown){
  return typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
function allowedIceUrl(value:unknown){
  return typeof value==='string'&&/^(?:stun|stuns|turn|turns):/i.test(value);
}
function sanitizeIceServers(value:unknown){
  if(!Array.isArray(value))throw new Error('invalid TURN response');
  return value.map((row:any)=>{
    const urls=(Array.isArray(row?.urls)?row.urls:[row?.urls]).filter(allowedIceUrl);
    if(!urls.length)throw new Error('invalid TURN response');
    const clean:any={urls};
    if(typeof row?.username==='string'&&row.username.length<=512)clean.username=row.username;
    if(typeof row?.credential==='string'&&row.credential.length<=1024)clean.credential=row.credential;
    return clean;
  });
}

Deno.serve(async(req)=>{
  const origin=req.headers.get('origin');
  const cors={
    'Access-Control-Allow-Origin':origin&&allowedOrigins.has(origin)?origin:'https://chatchairins-source.github.io',
    'Access-Control-Allow-Headers':'authorization,apikey,content-type',
    'Access-Control-Allow-Methods':'POST,OPTIONS',
    'Cache-Control':'no-store',
    'Vary':'Origin'
  };
  const respond=(body:unknown,status=200)=>Response.json(body,{status,headers:cors});
  if(origin&&!allowedOrigins.has(origin))return respond({error:'origin unavailable'},403);
  if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
  if(req.method!=='POST')return respond({error:'POST required'},405);

  const authorization=req.headers.get('authorization')||'';
  if(!/^Bearer\s+\S+$/i.test(authorization))return respond({error:'login required'},401);
  if(!anonKey)return respond({error:'TURN authorization unavailable'},503);

  try{
    const input=await req.json();
    if(!uuid(input?.callId)||!uuid(input?.sessionId))return respond({error:'invalid call'},400);

    // Authorize in the caller's JWT context. Service-role must never bypass call/session rules.
    const authCheck=await fetch(supabaseUrl.replace(/\/$/,'')+'/rest/v1/rpc/qg_call_ice_config',{
      method:'POST',
      headers:{
        apikey:anonKey,
        authorization,
        'content-type':'application/json',
        accept:'application/json'
      },
      body:JSON.stringify({p_call_id:input.callId,p_session_id:input.sessionId}),
      signal:AbortSignal.timeout(8000)
    });
    const authPayload=await authCheck.json().catch(()=>null);
    if(!authCheck.ok){
      const status=authCheck.status===401?401:authCheck.status===403?403:409;
      return respond({error:'call unavailable'},status);
    }
    if(!authPayload||authPayload.authorized!==true)return respond({error:'call unavailable'},403);

    if(!turnKeyId||!turnApiToken){
      return respond({
        error:'TURN relay not configured',
        turnReady:false,
        iceServers:[{urls:['stun:stun.cloudflare.com:3478']}]
      },503);
    }

    const cloudflare=await fetch(
      'https://rtc.live.cloudflare.com/v1/turn/keys/'+encodeURIComponent(turnKeyId)+'/credentials/generate-ice-servers',
      {
        method:'POST',
        headers:{
          authorization:'Bearer '+turnApiToken,
          'content-type':'application/json'
        },
        body:JSON.stringify({ttl:7200}),
        signal:AbortSignal.timeout(8000)
      }
    );
    const cf=await cloudflare.json().catch(()=>null);
    if(cloudflare.status!==201||!cf?.iceServers){
      return respond({error:'TURN relay unavailable',turnReady:false},503);
    }
    const iceServers=sanitizeIceServers(cf.iceServers);
    if(!iceServers.some((row:any)=>row.urls.some((url:string)=>/^turns?:/i.test(url)))){
      return respond({error:'TURN relay unavailable',turnReady:false},503);
    }
    return respond({turnReady:true,iceServers});
  }catch{
    return respond({error:'TURN relay unavailable',turnReady:false},503);
  }
});
