const assert=require('node:assert/strict');
(async()=>{
 const {createExpressQuoteHandler}=await import('../supabase/functions/queuego-express-quote/handler.mjs');
 let checks=0,calls=[];const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const values={SUPABASE_URL:'https://isolated.invalid',SUPABASE_SERVICE_ROLE_KEY:'isolated-service',QUEUEGO_LONGDO_KEY:'isolated-routing'};
 const env={get:k=>values[k]};
 const payload={
  pickup:{lat:15,lng:103,address:'จุดรับพัสดุใน fixture'},
  destination:{lat:15.01,lng:103,address:'จุดส่งพัสดุใน fixture'},
  sender:{name:'Sender',phone:'0811111111'},
  receiver:{name:'Receiver',phone:'0822222222'},
  item:{type:'parcel',weightKg:2,size:'medium',vehicleType:'motorcycle',instructions:'handle carefully'}
 };
 const req=(body=payload,authorization='Bearer customer-token',method='POST')=>new Request('https://isolated.invalid/quote',{method,headers:{authorization,'content-type':'application/json'},...(method==='POST'?{body:typeof body==='string'?body:JSON.stringify(body)}:{})});
 const good=async(url,options={})=>{
  calls.push([String(url),options]);
  if(String(url).endsWith('/auth/v1/user'))return Response.json({id:'00000000-0000-4000-8000-000000000160'});
  if(String(url).endsWith('/queuego_express_quote_context'))return Response.json({available:true});
  if(String(url).startsWith('https://api.longdo.com/'))return Response.json({features:[{geometry:{type:'LineString',coordinates:[[103,15],[103,15.01]]},properties:{distance:3000,interval:480}}]});
  if(String(url).endsWith('/queuego_cache_express_quote'))return Response.json({quote_id:'00000000-0000-4000-8000-000000000001',fare:50,duration_minutes:8,distance_km:3,vehicle_type:'motorcycle',expires_at:'2030-01-01T00:00:00Z'});
  throw Error('unexpected request '+url);
 };
 const handler=createExpressQuoteHandler(env,good);
 let res=await handler(req());eq(res.status,200);eq((await res.json()).fare,50);eq(calls.length,4);
 const context=JSON.parse(calls[1][1].body);eq(context.p_auth_user_id,'00000000-0000-4000-8000-000000000160');eq(context.p_pickup_lat,15);
 const cache=JSON.parse(calls[3][1].body);
 eq(cache.p_distance_km,3);eq(cache.p_duration_minutes,8);eq(cache.p_weight_kg,2);eq(cache.p_vehicle_type,'motorcycle');eq(cache.p_sender_phone,'0811111111');
 eq(calls[0][1].headers.authorization,'Bearer customer-token');eq(calls[3][1].headers.authorization,'Bearer isolated-service');

 calls=[];eq((await handler(req(payload,''))).status,401);eq(calls.length,0);
 eq((await handler(req(payload,'Bearer isolated-service'))).status,401);
 eq((await handler(req(payload,'Bearer customer-token','GET'))).status,405);
 eq((await handler(req(payload,'Bearer customer-token','OPTIONS'))).status,204);
 eq((await handler(req('{'))).status,400);eq((await handler(req(' '.repeat(8193)))).status,400);
 eq((await handler(req({...payload,pickup:{...payload.pickup,lat:100}}))).status,400);
 eq((await handler(req({...payload,destination:payload.pickup}))).status,400);
 eq((await handler(req({...payload,sender:{...payload.sender,phone:''}}))).status,400);
 eq((await handler(req({...payload,item:{...payload.item,weightKg:0}}))).status,400);
 eq((await handler(req({...payload,item:{...payload.item,vehicleType:'truck'}}))).status,400);

 calls=[];const denied=createExpressQuoteHandler(env,async(url,opts)=>String(url).endsWith('/queuego_express_quote_context')?new Response('',{status:403}):good(url,opts));
 eq((await denied(req())).status,409);eq(calls.some(([url])=>url.startsWith('https://api.longdo.com/')),false);
 const missing=createExpressQuoteHandler({get:k=>k==='QUEUEGO_LONGDO_KEY'?undefined:values[k]},good);eq((await missing(req())).status,503);
 const badAuth=createExpressQuoteHandler(env,async()=>new Response('',{status:401}));eq((await badAuth(req())).status,401);
 const badRoad=createExpressQuoteHandler(env,async(url,opts)=>String(url).startsWith('https://api.longdo.com/')?Response.json({features:[]}):good(url,opts));
 res=await badRoad(req());eq(res.status,503);assert(!(await res.text()).includes('isolated-routing'));checks++;

 calls=[];const forged=await handler(req({...payload,fare:1,distance_km:0.01,auth_user_id:'forged',item:{...payload.item,weightKg:2}}));eq(forged.status,200);
 const trusted=JSON.parse(calls.at(-1)[1].body);eq(trusted.p_distance_km,3);eq(trusted.p_auth_user_id,'00000000-0000-4000-8000-000000000160');
 console.log('Express road quote: '+checks+' checks PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
