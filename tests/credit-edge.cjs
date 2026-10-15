const assert=require('node:assert/strict'),crypto=require('node:crypto');
(async()=>{
 const {createCreditHandler}=await import('../supabase/functions/queuego-credit/handler.mjs');let checks=0,calls=[],applied=[];
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const id='00000000-0000-4000-8000-000000000300';
 const webhookKey=Buffer.from('queuego-isolated-webhook-secret'),webhookSecret=webhookKey.toString('base64');
 const values={SUPABASE_URL:'https://isolated.invalid',SUPABASE_SERVICE_ROLE_KEY:'isolated-service',OMISE_SECRET_KEY:'skey_live_isolated',OMISE_WEBHOOK_SECRET:webhookSecret,QUEUEGO_CREDIT_LIVE_ENABLED:'true'},env={get:k=>values[k]};
 let charge={object:'charge',id:'chrg_isolated300',ref_id:id,amount:10000,currency:'thb',livemode:true,paid:false,status:'pending',metadata:{queuego_credit_topup:id},source:{type:'promptpay',scannable_code:{image:{download_uri:'https://api.omise.co/charges/chrg_isolated300/documents/docu_fixture/downloads/fixture'}}}};
 let topup={id,amount_minor:10000,charge_id:null};
 const good=async(url,options)=>{calls.push([url,options]);if(url.endsWith('/auth/v1/user'))return Response.json({id});if(url.endsWith('/queuego_credit_begin_topup'))return Response.json(topup);if(url.endsWith('/queuego_credit_apply_charge')){applied.push(JSON.parse(options.body));return Response.json({status:charge.status})}if(url==='https://api.omise.co/charges')return Response.json({id:charge.id});if(url.startsWith('https://api.omise.co/search?'))return Response.json({data:[charge]});if(url==='https://api.omise.co/charges/'+charge.id)return Response.json(charge);throw Error('unexpected request')};
 const req=(data={id,kind:'rider',amount_minor:10000},token='rider-token')=>new Request('https://isolated.invalid/credit',{method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify(data)});
 const hookReq=(data,{timestamp=Math.floor(Date.now()/1000),key=webhookKey,prefix=''}={})=>{
  const body=JSON.stringify(data),signature=crypto.createHmac('sha256',key).update(String(timestamp)+'.'+body).digest('hex');
  return new Request('https://isolated.invalid/credit-webhook',{method:'POST',headers:{'omise-signature':prefix+signature,'omise-signature-timestamp':String(timestamp)},body});
 };
 const handler=createCreditHandler(env,good);eq((await handler(req())).status,200);eq(applied.length,1);eq(applied[0].p_paid,false);
 const form=new URLSearchParams(calls.find(([u])=>u==='https://api.omise.co/charges')[1].body);eq(form.get('ref_id'),id);eq(form.get('source[qr_settings][image_type]'),'png');eq(form.get('amount'),'10000');eq(form.get('webhook_endpoints[]'),'https://isolated.invalid/functions/v1/queuego-credit-webhook');
 eq(calls.find(([u])=>u.endsWith('queuego_credit_begin_topup'))[1].headers.authorization,'Bearer rider-token');
 // Retry retrieves original charge, not a second provider POST.
 calls=[];topup.charge_id=charge.id;charge={...charge,status:'successful',paid:true};eq((await handler(req())).status,200);eq(calls.some(([u])=>u==='https://api.omise.co/charges'),false);eq(applied.at(-1).p_status,'successful');
 const hook=createCreditHandler(env,good,true);calls=[];applied=[];
 const hookEvent={key:'charge.complete',data:{id:charge.id,status:'successful',amount:999999}};
 eq((await hook(hookReq(hookEvent))).status,200);eq(applied[0].p_amount,10000);eq(calls[0][0],'https://api.omise.co/charges/'+charge.id);
 // Signature is checked before any provider call; stale, bad and missing-secret webhook traffic fails closed.
 calls=[];applied=[];eq((await hook(hookReq(hookEvent,{key:Buffer.from('wrong-secret')}))).status,401);eq(calls.length,0);
 eq((await hook(hookReq(hookEvent,{timestamp:Math.floor(Date.now()/1000)-301}))).status,401);eq(calls.length,0);
 const noWebhookSecret=createCreditHandler({get:k=>({...values,OMISE_WEBHOOK_SECRET:''})[k]},good,true);eq((await noWebhookSecret(hookReq(hookEvent))).status,503);eq(calls.length,0);
 // Real provider charge envelopes can exceed the small client request limit.
 calls=[];applied=[];eq((await hook(hookReq({...hookEvent,provider_context:'x'.repeat(9000)}))).status,200);eq(applied.length,1);
 calls=[];applied=[];eq((await hook(hookReq({...hookEvent,provider_context:'x'.repeat(131073)}))).status,503);eq(calls.length,0);
 const pausedHook=createCreditHandler({get:k=>({...values,QUEUEGO_CREDIT_LIVE_ENABLED:'false'})[k]},good,true);
 calls=[];applied=[];eq((await pausedHook(hookReq(hookEvent))).status,200);eq(applied.length,1);
 // During Omise secret rotation, accept either signature from the comma-separated header.
 calls=[];applied=[];eq((await hook(hookReq(hookEvent,{prefix:'0'.repeat(64)+','}))).status,200);eq(applied.length,1);
 // Signed payload still cannot override the independently retrieved provider status.
 charge={...charge,status:'pending',paid:false};applied=[];eq((await hook(hookReq({key:'charge.complete',data:{id:charge.id,status:'successful',paid:true}}))).status,200);eq(applied[0].p_status,'pending');eq(applied[0].p_paid,false);
 for(const patch of [{livemode:false},{currency:'usd'},{amount:1.5},{source:{type:'card'}},{metadata:{}},{ref_id:'wrong-ref'},{status:'unknown'}]){const saved=charge;charge={...charge,...patch};applied=[];eq((await hook(hookReq({key:'charge.complete',data:{id:charge.id}}))).status,503);eq(applied.length,0);charge=saved}
 calls=[];eq((await handler(req({},'isolated-service'))).status,401);eq(calls.length,0);
 for(const envPatch of [{QUEUEGO_CREDIT_LIVE_ENABLED:'false'},{OMISE_SECRET_KEY:'skey_test_isolated'},{OMISE_SECRET_KEY:''}]){calls=[];eq((await createCreditHandler({get:k=>({...values,...envPatch})[k]},good)(req())).status,503);eq(calls.length,0)}
 eq((await handler(req({id,kind:'merchant',amount_minor:1999}))).status,400);
 const badAuth=createCreditHandler(env,async()=>new Response('',{status:401}));eq((await badAuth(req())).status,401);
 topup.charge_id=null;charge={...charge,ref_id:id,status:'pending',paid:false};
 const lost=createCreditHandler(env,async(u,o)=>u==='https://api.omise.co/charges'?new Response('',{status:409}):good(u,o));
 calls=[];applied=[];eq((await lost(req())).status,200);eq(calls.some(([u])=>u.startsWith('https://api.omise.co/search?scope=charge')),true);eq(applied.length,1);
 const absent=createCreditHandler(env,async(u,o)=>u==='https://api.omise.co/charges'?new Response('',{status:409}):u.startsWith('https://api.omise.co/search?')?Response.json({data:[]}):good(u,o));
 calls=[];eq((await absent(req())).status,202);
 const ambiguous=createCreditHandler(env,async(u,o)=>u==='https://api.omise.co/charges'?new Response('',{status:409}):u.startsWith('https://api.omise.co/search?')?Response.json({data:[charge,{...charge,id:'chrg_isolated301'}]}):good(u,o));
 calls=[];eq((await ambiguous(req())).status,503);
 const oversized=createCreditHandler(env,async(u,o)=>u.endsWith('/auth/v1/user')?new Response('x'.repeat(131073)):good(u,o));eq((await oversized(req())).status,503);
 const badProvider=createCreditHandler(env,async(u,o)=>u.includes('api.omise.co')?new Response('',{status:500}):good(u,o));const fail=await badProvider(req());eq((await fail.text()).includes(values.OMISE_SECRET_KEY),false);
 console.log('Credit Edge: '+checks+' checks PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
