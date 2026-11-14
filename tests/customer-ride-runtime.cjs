const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('customer-ride.js','utf8');
let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};
function boot(){
 const state={user:'customer',html:'',renders:0,timers:[],calls:[],maps:[],markerOptions:[],failConfig:false,failState:false};
 class FakeMap{constructor(options){this.options=options;this.markers=[];state.maps.push(this)}on(_event,callback){callback()}getContainer(){return this.options.container}remove(){this.removed=true}addControl(){}setCenter(){}fitBounds(){}touchZoomRotate={disableRotation(){}}}
 class FakeMarker{constructor(options){this.options=options;state.markerOptions.push(options)}setLngLat(point){this.point=point;return this}addTo(map){map.markers.push(this);return this}}
 const ctx=vm.createContext({
  S:{get:()=>({userId:state.user})},V:{order:async()=>{}},routeVersion:1,currentPos:null,$:()=>null,
  location:{hash:'#ride'},localStorage:{getItem:()=>null},
  document:{hidden:false,querySelectorAll:()=>[],addEventListener:()=>{},createElement:()=>({style:{},setAttribute(k,v){this[k]=v}})},
  addEventListener:()=>{},setTimeout:fn=>{state.timers.push(fn);return state.timers.length},clearTimeout:()=>{},
  layout:html=>{state.html=html;state.mapHost=html.includes('id="ride-live-map"')?{id:'ride-live-map'}:null;state.renders++},ico:()=>'',esc:String,baht:String,
  maplibregl:{Map:FakeMap,Marker:FakeMarker,LngLatBounds:class{extend(){return this}},NavigationControl:class{}},qgQueueGoPickerStyle:()=>({}),
  $:id=>id==='ride-live-map'?state.mapHost:null,
  QueueGoOrderNumber:{format:o=>o.id},go:()=>{},
  db:async (path,opts)=>{
   state.calls.push({path,opts});
   if(path==='rpc/queuego_ride_config'){
    if(state.failConfig)throw Error('config unavailable');return {enabled:true,configured:true};
   }
   if(path==='rpc/queuego_customer_ride_supply')return {nearby_count:2,clusters:[{latitude:15.02,longitude:103.02,count:2,approximate:true},{latitude:15,longitude:103,count:1,approximate:true},{latitude:15,longitude:103,count:5,approximate:false}]};
   if(state.failState)throw Error('state unavailable');
   return {rides:state.rides||[]};
  }
 });
 vm.runInContext(source,ctx);
 return {state,ctx,open:id=>ctx.V.ride(id)};
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
 const supply=boot();supply.ctx.currentPos={lat:15,lng:103,address:'Pickup'};await supply.open();await new Promise(r=>setTimeout(r,0));
 const supplyCall=supply.state.calls.find(c=>c.path==='rpc/queuego_customer_ride_supply');
 ok(!!supplyCall,'Ride booking reads the existing privacy-preserving nearby supply RPC');
 ok(supplyCall.opts.body.p_lat===15&&supplyCall.opts.body.p_lng===103,'nearby supply query uses the selected pickup point');
 ok(supply.state.html.includes('id="ride-live-map"')&&supply.state.html.includes('มี Rider พร้อมรับงานใกล้จุดรับ 2 คน'),'Ride booking shows its QueueGo map and nearby Rider count');
 ok(supply.state.html.includes('ตำแหน่งบนแผนที่เป็นแบบกลุ่มโดยประมาณ'),'supply display explains that map positions are approximate clusters');
 ok(supply.state.maps.at(-1).markers.length===2,'booking map includes the selected pickup and one validated approximate Rider cluster');
 ok(supply.state.markerOptions.some(o=>o.element?.['aria-label']==='2 Rider โดยประมาณ'),'cluster marker displays only its anonymous count');
 const active=boot();active.state.rides=[{id:'ride-1',status:'rider_assigned',rider_id:'rider-1',arrived_pickup_at:'2026-10-27T10:00:00Z',pickup_address:'รับ',pickup_latitude:15, pickup_longitude:103,delivery_address:'ส่ง',delivery_latitude:15.01,delivery_longitude:103.01,rider_latitude:15.005,rider_longitude:103.005,rider_name:'Rider',fare:50,distance_km:3,duration_minutes:8}];await active.open('ride-1');
 ok(active.state.html.includes('คนขับถึงจุดรับแล้ว'),'Customer Ride status reflects the same arrival milestone as Native');
 ok(active.state.maps.at(-1).markers.length===3,'active trip map renders pickup, destination, and assigned Rider markers');
 console.log(JSON.stringify({checks,failures:0,scope:'isolated Ride entry transport failure/retry and account/route fencing; no live account/order mutation'}));
})().catch(e=>{console.error(e);process.exit(1)});
