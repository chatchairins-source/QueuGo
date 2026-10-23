const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('rider/index.html','utf8');let checks=0;
const offRouteMatch=source.match(/const RIDER_ROUTE_OFF_ROUTE_METERS=(\d+(?:\.\d+)?);/);assert.ok(offRouteMatch,'Rider off-route threshold must remain discoverable');
const RIDER_ROUTE_OFF_ROUTE_METERS=Number(offRouteMatch[1]);
const classSet=new Set(),makeEl=()=>({textContent:'',innerHTML:'',classList:{toggle:(name,on)=>on?classSet.add(name):classSet.delete(name)}});
const noteClasses=new Set(),noteEl={textContent:'',innerHTML:'',classList:{toggle:(name,on)=>on?noteClasses.add(name):noteClasses.delete(name)}};
const speedClasses=new Set(),speedEl={textContent:'',attrs:{},classList:{toggle:(name,on)=>on?speedClasses.add(name):speedClasses.delete(name),remove:name=>speedClasses.delete(name)},setAttribute:(k,v)=>speedEl.attrs[k]=String(v)};
const els={'route-text':makeEl(),'route-badge':makeEl(),'route-maneuver-distance':makeEl(),'route-road':makeEl(),'route-next':makeEl(),'route-turn-icon':makeEl(),'route-customer-note':noteEl,'qg-speed-limit-sign':speedEl};
const S={map:{},pos:{accuracy:5}},cache={map:S.map,dist:1200,time:240,label:'ไปหาลูกค้า',customerNote:'วางไว้หน้าบ้าน',instructions:[
 {turnCode:5,roadName:'ถนน A',distance:500,duration:100},
 {turnCode:1,roadName:'ถนน B',distance:400,duration:80},
 {turnCode:0,roadName:'ถนน C',distance:300,duration:60}
],speedLimits:[{start:0,end:500,kph:50,raw:'50',source:'osm:maxspeed',osmWayId:101}]};
const ctx=vm.createContext({S,Number,Math,Array,String,RIDER_ROUTE_OFF_ROUTE_METERS,document:{getElementById:id=>els[id]||null},riderRouteCache:cache,esc:v=>String(v)});
vm.runInContext(source.slice(source.indexOf('function qgFiniteOrNull('),source.indexOf('function qgNavigationCameraShouldRun(')),ctx);
vm.runInContext(source.slice(source.indexOf('function qgTurnPhrase('),source.indexOf('function qgRiderRouteNeedsReroute(')),ctx);
const run=s=>vm.runInContext(s,ctx),eq=(a,b)=>{assert.deepEqual(a,b);checks++;};
ctx.decision={routeMatch:{geometryDistanceMeters:1200,offRouteMeters:2},remainingRouteMeters:800,routeBearingRejected:false};
eq(run('qgUpdateRiderRouteProgress(decision)'),true);
eq(els['route-text'].textContent,'ไปหาลูกค้า • 800 ม. • ประมาณ 3 นาที');
eq(els['route-maneuver-distance'].textContent,'100 ม.');
eq(els['route-road'].textContent,'ถนน B');
eq(classSet.has('show'),true);
eq(els['route-next'].innerHTML.includes('เลี้ยวซ้าย'),true);
eq(speedClasses.has('show'),true);eq(speedEl.textContent,'50');
ctx.decision.remainingRouteMeters=600;eq(run('qgUpdateRiderRouteProgress(decision)'),true);eq(els['route-text'].textContent,'ไปหาลูกค้า • 600 ม. • ประมาณ 2 นาที');eq(noteClasses.has('show'),false);eq(speedClasses.has('show'),false);
ctx.decision.remainingRouteMeters=300;eq(run('qgUpdateRiderRouteProgress(decision)'),true);eq(noteClasses.has('show'),true);eq(els['route-customer-note'].innerHTML.includes('วางไว้หน้าบ้าน'),true);
const before=els['route-text'].textContent;
for(const mutate of [d=>d.remainingRouteMeters=null,d=>d.routeBearingRejected=true,d=>d.routeMatch.offRouteMeters=90,d=>d.routeMatch.geometryDistanceMeters=0]){
 ctx.decision={routeMatch:{geometryDistanceMeters:1200,offRouteMeters:2},remainingRouteMeters:100,routeBearingRejected:false};mutate(ctx.decision);
 eq(run('qgUpdateRiderRouteProgress(decision)'),false);eq(els['route-text'].textContent,before);
}
ctx.rawLimits=[
 {start_geometry_m:0,end_geometry_m:400,maxspeed_kph:50,maxspeed_raw:'50',source:'osm:maxspeed',osm_way_id:101},
 {start_geometry_m:400,end_geometry_m:800,maxspeed_kph:60,maxspeed_raw:'60',source:'queuego:road_class_estimate_v1',osm_way_id:202},
 {start_geometry_m:800,end_geometry_m:700,maxspeed_kph:40,maxspeed_raw:'40',source:'osm:maxspeed'},
 {start_geometry_m:800,end_geometry_m:1000,maxspeed_kph:0,maxspeed_raw:'0',source:'osm:maxspeed'}
];
ctx.normalized=run('qgNormalizeRiderSpeedLimits(rawLimits)');
eq(ctx.normalized.length,1);eq(ctx.normalized[0].source,'osm:maxspeed');eq(ctx.normalized[0].kph,50);
eq(cache.dist,1200);eq(cache.time,240);
console.log(JSON.stringify({checks,failures:0,scope:'authoritative route progress, current/next maneuver HUD and explicit OSM speed-limit intervals; estimates and invalid evidence stay hidden'}));
