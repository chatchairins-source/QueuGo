const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('customer-ride.js','utf8');
let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};
function boot(){
 const state={user:'customer',html:'',renders:0,timers:[],failConfig:false,failState:false};
 const ctx=vm.createContext({
  S:{get:()=>({userId:state.user})},V:{order:async()=>{}},routeVersion:1,currentPos:null,
  location:{hash:'#ride'},localStorage:{getItem:()=>null},
  document:{hidden:false,querySelectorAll:()=>[],addEventListener:()=>{}},
  addEventListener:()=>{},setTimeout:fn=>{state.timers.push(fn);return state.timers.length},clearTimeout:()=>{},
  layout:html=>{state.html=html;state.renders++},ico:()=>'',esc:String,baht:String,
  QueueGoOrderNumber:{format:o=>o.id},go:()=>{},
  db:async path=>{
   if(path==='rpc/queuego_ride_config'){
    if(state.failConfig)throw Error('config unavailable');return {enabled:true,configured:true};
   }
   if(state.failState)throw Error('state unavailable');
   return {rides:[]};
  }
 });
 vm.runInContext(source,ctx);
 return {state,ctx,open:()=>ctx.V.ride()};
}
(async()=>{
 const unavailable=boot();unavailable.state.failConfig=true;
 await unavailable.open();
 ok(unavailable.state.html.includes('บริการเรียกรถ'),'failed config must still render Ride shell');
 ok(unavailable.state.html.includes('role="alert"'),'transport failure must be visible');
 ok(unavailable.state.html.includes('data-action="retry"'),'failed entry must offer explicit retry');
 ok(unavailable.state.html.includes('data-action="quote" disabled'),'failed config must fail closed');
 unavailable.state.failConfig=false;
 await unavailable.open();
 ok(!unavailable.state.html.includes('config unavailable'),'successful retry clears entry error');
 const stateFailure=boot();stateFailure.state.failState=true;
 await stateFailure.open();
 ok(stateFailure.state.html.includes('role="alert"'),'state failure must render recoverable shell');
 const stale=boot();let resolve;
 stale.ctx.db=()=>new Promise(r=>{resolve=r});
 const opening=stale.open();stale.state.user='another';resolve({enabled:true,configured:true});await opening;
 ok(stale.state.renders===0,'retired account response must not render');
 const retired=boot();retired.ctx.db=async()=>{retired.ctx.routeVersion=2;throw Error('late failure')};
 await retired.open();
 ok(retired.state.renders===0,'retired route failure must not replace next route');
 const normal=boot();await normal.open();
 ok(!normal.state.html.includes('role="alert"'),'healthy entry has no failure alert');
 ok(normal.state.timers.length===1,'healthy entry starts one refresh timer');
 console.log(JSON.stringify({checks,failures:0,scope:'isolated Ride entry transport failure/retry and account/route fencing; no live account/order mutation'}));
})().catch(e=>{console.error(e);process.exit(1)});
