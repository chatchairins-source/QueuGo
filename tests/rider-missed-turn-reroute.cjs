const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('rider/index.html','utf8');
let checks=0,now=100000,decision=null;
const eq=(a,b,msg)=>{assert.deepEqual(a,b,msg);checks++;};
const map={id:'map'};
const S={map,pos:{accuracy:5}};
const ctx=vm.createContext({
  S,Math,Number,String,JSON,
  Date:class extends Date{static now(){return now}},
  document:{getElementById:()=>null},
  window:{QueueGoMapWeb:{navigationCameraDecision:()=>decision,removeGeoJsonLine(){},addGeoJsonLine(){}}},
  qgFiniteOrNull:v=>v===null||v===undefined||v===''?null:(Number.isFinite(Number(v))?Number(v):null)
});
const start=source.indexOf('const RIDER_ROUTE_OFF_ROUTE_METERS=30;');
const end=source.indexOf('function disposeRiderMap(){',start);
assert(start>=0&&end>start,'reroute runtime block missing');
vm.runInContext(source.slice(start,end),ctx);
const run=s=>vm.runInContext(s,ctx);

run(`riderRouteGeometry=[[[103,15],[103.01,15]]];
riderRouteCache={scope:'active',map:S.map,dist:1000,time:180,label:'ไปหาลูกค้า',instructions:[
 {turnCode:5,roadName:'ถนนหลัก',distance:200,duration:20},
 {turnCode:1,roadName:'ซอยเป้าหมาย',distance:300,duration:40},
 {turnCode:5,roadName:'ปลายทาง',distance:500,duration:80}
]};`);

decision={routeMatch:{offRouteMeters:12,geometryDistanceMeters:1000},remainingRouteMeters:790,routeBearingRejected:false};
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:5,bearing:90},'active',S.map)"),true,'passing a commanded turn while moving must reroute immediately');

now+=1000;
decision={routeMatch:{offRouteMeters:12,geometryDistanceMeters:1000},remainingRouteMeters:780,routeBearingRejected:false};
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:5,bearing:90},'active',S.map)"),false,'immediate reroute path must have a short anti-storm cooldown');

now+=3000;
decision={routeMatch:{offRouteMeters:12,geometryDistanceMeters:1000},remainingRouteMeters:770,routeBearingRejected:true};
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:5,bearing:90},'active',S.map)"),true,'strong direction conflict while off route must reroute immediately');

run("riderRouteLastRerouteAt=0;riderRouteOffRouteSince=0");
decision={routeMatch:{offRouteMeters:40,geometryDistanceMeters:1000},remainingRouteMeters:500,routeBearingRejected:false};
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:5,bearing:90},'active',S.map)"),false,'ordinary off-route drift must not reroute on first sample');
now+=2999;
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:5,bearing:90},'active',S.map)"),false,'ordinary drift must retain the sustained three-second fence');
now+=1;
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:5,bearing:90},'active',S.map)"),true,'ordinary drift reroutes after three sustained seconds');

run("riderRouteLastRerouteAt=0;riderRouteOffRouteSince=0");
decision={routeMatch:{offRouteMeters:14,geometryDistanceMeters:1000},remainingRouteMeters:790,routeBearingRejected:true};
eq(run("qgRiderRouteNeedsReroute({lat:15,lng:103,speed:8,accuracy:35,bearing:90},'active',S.map)"),false,'poor GPS must not trigger immediate missed-turn reroute');

console.log(JSON.stringify({checks,failures:0,scope:'Rider Web missed-turn immediate reroute with GPS-quality and anti-storm guards; generic drift retains 30m/3s policy'}));
