const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('rider/index.html','utf8');let checks=0;
const classSet=new Set(),makeEl=()=>({textContent:'',innerHTML:'',classList:{toggle:(name,on)=>on?classSet.add(name):classSet.delete(name)}});
const noteClasses=new Set(),noteEl={textContent:'',innerHTML:'',classList:{toggle:(name,on)=>on?noteClasses.add(name):noteClasses.delete(name)}};
const els={'route-text':makeEl(),'route-badge':makeEl(),'route-maneuver-distance':makeEl(),'route-road':makeEl(),'route-next':makeEl(),'route-turn-icon':makeEl(),'route-customer-note':noteEl};
const S={map:{},pos:{accuracy:5}},cache={map:S.map,dist:1200,time:240,label:'ไปหาลูกค้า',customerNote:'วางไว้หน้าบ้าน',instructions:[
 {turnCode:5,roadName:'ถนน A',distance:500,duration:100},
 {turnCode:1,roadName:'ถนน B',distance:400,duration:80},
 {turnCode:0,roadName:'ถนน C',distance:300,duration:60}
]};
const ctx=vm.createContext({S,Number,Math,Array,String,document:{getElementById:id=>els[id]||null},riderRouteCache:cache,esc:v=>String(v)});
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
ctx.decision.remainingRouteMeters=600;eq(run('qgUpdateRiderRouteProgress(decision)'),true);eq(els['route-text'].textContent,'ไปหาลูกค้า • 600 ม. • ประมาณ 2 นาที');eq(noteClasses.has('show'),false);
ctx.decision.remainingRouteMeters=300;eq(run('qgUpdateRiderRouteProgress(decision)'),true);eq(noteClasses.has('show'),true);eq(els['route-customer-note'].innerHTML.includes('วางไว้หน้าบ้าน'),true);
const before=els['route-text'].textContent;
for(const mutate of [d=>d.remainingRouteMeters=null,d=>d.routeBearingRejected=true,d=>d.routeMatch.offRouteMeters=90,d=>d.routeMatch.geometryDistanceMeters=0]){
 ctx.decision={routeMatch:{geometryDistanceMeters:1200,offRouteMeters:2},remainingRouteMeters:100,routeBearingRejected:false};mutate(ctx.decision);
 eq(run('qgUpdateRiderRouteProgress(decision)'),false);eq(els['route-text'].textContent,before);
}
eq(cache.dist,1200);eq(cache.time,240);
console.log(JSON.stringify({checks,failures:0,scope:'authoritative route progress plus current/next maneuver HUD; invalid/off-route evidence cannot invent progress'}));
