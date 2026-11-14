const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('customer-express.js','utf8');
let checks=0;
const ok=(condition,message)=>{assert.ok(condition,message);checks++};
async function open(overrides={}){
 const state={html:'',maps:[],markers:[]};
 const row={id:'order',status:'rider_assigned',rider_id:'rider',pickup_latitude:null,pickup_longitude:null,delivery_latitude:15.02,delivery_longitude:103.02,...overrides};
 const ctx=vm.createContext({S:{get:()=>({userId:'customer'})},V:{order:async()=>{}},routeVersion:1,currentPos:null,
  location:{hash:'#express'},localStorage:{getItem:()=>null},document:{hidden:false,querySelectorAll:()=>[],addEventListener:()=>{}},
  addEventListener:()=>{},setTimeout:()=>1,clearTimeout:()=>{},layout:html=>{state.html=html},ico:()=>'',esc:String,baht:String,
  QueueGoOrderNumber:{format:o=>o.id},go:()=>{},$:id=>id==='express-live-map'&&state.html.includes('id="express-live-map"')?{}:null,
  qgQueueGoPickerStyle:()=>({}),db:async path=>path==='rpc/queuego_express_config'?{enabled:true,configured:true}:{express_orders:[row]},
  maplibregl:{Map:class{constructor(options){state.maps.push(options);this.touchZoomRotate={disableRotation(){}}}addControl(){}on(event,fn){fn()}fitBounds(){}remove(){}},NavigationControl:class{},LngLatBounds:class{extend(){}},Marker:class{setLngLat(p){state.markers.push(p);return this}addTo(){return this}}}
 });
 vm.runInContext(source,ctx);await ctx.V.express();return state;
}
(async()=>{
 for(const [lat,lng] of [[null,null],[undefined,103],['',103],[' ',103],[15,''],[91,103],[15,181],['bad',103],[false,103],[[],103]]){
  const s=await open({rider_latitude:lat,rider_longitude:lng});
  ok(!s.html.includes('id="express-live-map"'),'missing/invalid Rider GPS must not offer tracking map');
  ok(s.maps.length===0,'missing/invalid Rider GPS must not create a map at zero');
 }
 const valid=await open({rider_latitude:'15',rider_longitude:'103'});
 ok(valid.maps.length===1,'numeric server coordinates must mount tracking');
 ok(valid.maps[0].center[0]===103&&valid.maps[0].center[1]===15,'tracking center must preserve actual coordinates');
 ok(valid.markers.length===2,'missing pickup coordinates must not create a phantom stop');
 ok(valid.markers.every(p=>p[0]!==0&&p[1]!==0),'missing coordinates must not become zero markers');
 const zero=await open({rider_latitude:0,rider_longitude:0});
 ok(zero.maps.length===1,'explicit numeric zero remains a valid coordinate');
 console.log(JSON.stringify({checks,failures:0,scope:'Express Web real render/map lifecycle with missing, invalid, numeric-string and zero coordinates; isolated records only'}));
})().catch(e=>{console.error(e);process.exit(1)});
