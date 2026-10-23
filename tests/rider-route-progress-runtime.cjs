const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('rider/index.html','utf8'),label={textContent:'initial'};let checks=0;
const S={map:{},pos:{accuracy:5}},cache={map:S.map,dist:1200,time:240,label:'ไปร้าน'};
const ctx=vm.createContext({S,Number,Math,document:{getElementById:()=>label},riderRouteCache:cache,RIDER_ROUTE_OFF_ROUTE_METERS:30});
vm.runInContext(source.slice(source.indexOf('function qgFiniteOrNull('),source.indexOf('function qgNavigationCameraShouldRun(')),ctx);
vm.runInContext(source.slice(source.indexOf('function qgUpdateRiderRouteProgress('),source.indexOf('function qgRiderRouteNeedsReroute(')),ctx);
const run=s=>vm.runInContext(s,ctx),eq=(a,b)=>{assert.deepEqual(a,b);checks++;};
ctx.decision={routeMatch:{geometryDistanceMeters:1200,offRouteMeters:2},remainingRouteMeters:600,routeBearingRejected:false};
eq(run('qgUpdateRiderRouteProgress(decision)'),true);eq(label.textContent,'ไปร้าน • 600 ม. • ประมาณ 2 นาที');
ctx.decision.remainingRouteMeters=300;eq(run('qgUpdateRiderRouteProgress(decision)'),true);eq(label.textContent,'ไปร้าน • 300 ม. • ประมาณ 1 นาที');
const before=label.textContent;
for(const mutate of [d=>d.remainingRouteMeters=null,d=>d.routeBearingRejected=true,d=>d.routeMatch.offRouteMeters=90,d=>d.routeMatch.geometryDistanceMeters=0]){
 ctx.decision={routeMatch:{geometryDistanceMeters:1200,offRouteMeters:2},remainingRouteMeters:100,routeBearingRejected:false};mutate(ctx.decision);
 eq(run('qgUpdateRiderRouteProgress(decision)'),false);eq(label.textContent,before);
}
eq(cache.dist,1200);eq(cache.time,240);
console.log(JSON.stringify({checks,failures:0,scope:'read-only route-progress HUD uses existing authoritative road totals; approximate ETA; unknown/opposite/off-route evidence does not invent progress'}));
