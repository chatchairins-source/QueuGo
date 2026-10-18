const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.resolve(__dirname,'../queuego-map-web.js'),'utf8');
let checks=0;
const ok=(v,msg)=>{assert.ok(v,msg);checks++};
const eq=(a,b,msg)=>{assert.deepEqual(a,b,msg);checks++};

const window={};
const context=vm.createContext({
  window,
  console,
  WeakMap,
  Number,
  Math,
  Array,
  String,
  encodeURIComponent,
  fetch:async()=>({ok:false}),
  navigator:{},
  setTimeout:fn=>{fn();return 1},
  clearTimeout(){},
  document:{
    getElementById(){return null},
    createElement(){return {id:'',style:{},textContent:''}},
    head:{appendChild(){}}
  }
});
vm.runInContext(source,context);
const Q=window.QueueGoMapWeb;
ok(!!Q,'QueueGoMapWeb must export');

// Safari may omit heading; missing values must never become a northbound fix.
for(const missing of [null,undefined,'']){
 const unknown=Q.navigationCameraDecision({lat:15,lng:103,speed:10,bearing:missing,route:[],previousBearing:270});
 eq(unknown.bearing,270,'missing GPS heading must preserve westbound camera course');
 const routeOnly=Q.navigationCameraDecision({lat:15,lng:103.004,speed:10,bearing:missing,route:[[103,15],[103.01,15]],previousBearing:270});
 eq(routeOnly.routeBearingRejected,false,'missing heading cannot be evidence of opposite motion');
}

const corner=[
  [100.0000,13.0000],
  [100.0004,13.0000],
  [100.0004,13.0004]
];
const cornerDecision=Q.navigationCameraDecision({
  lat:13.0000,lng:100.00030,
  speed:8,bearing:90,accuracy:5,
  route:corner,previousBearing:90,baseZoom:17
});
ok(cornerDecision,'corner camera decision');
ok(cornerDecision.pitch>=28,'navigation must keep a visible forward pitch near a corner/destination');
ok(cornerDecision.bearing<89 && cornerDecision.bearing>60,'camera should begin turning before the corner');
ok(cornerDecision.center.lat>13.0000,'look-ahead center should advance into the upcoming corner');

const straight=[[103.0000,15.0000],[103.0100,15.0000]];
const roadWins=Q.navigationCameraDecision({
  lat:15.00001,lng:103.0040,
  speed:10,bearing:170,accuracy:5,
  route:straight,previousBearing:90
});
ok(roadWins.bearing>85&&roadWins.bearing<95,'matched road may beat a moderately noisy GPS course when prior motion still agrees with the route');
eq(roadWins.routeBearingRejected,false,'moderate GPS disagreement must not discard a stable matched-road direction');

const wrongWayRoute=Q.navigationCameraDecision({
  lat:15.00001,lng:103.0040,
  speed:10,bearing:270,accuracy:5,
  route:straight,previousBearing:270
});
eq(wrongWayRoute.routeBearingRejected,true,'opposite-direction route match must be rejected when GPS and prior motion agree');
ok(wrongWayRoute.bearing>250&&wrongWayRoute.bearing<290,'camera must keep the Rider travel direction instead of rotating back onto an opposite route segment');
ok(wrongWayRoute.center.lng<103.0040,'wrong-way route match must not move the look-ahead center back onto the opposite route direction');

const wrongPreviousStillRecovers=Q.navigationCameraDecision({
  lat:15.00001,lng:103.0040,
  speed:10,bearing:270,accuracy:5,
  route:straight,previousBearing:90
});
eq(wrongPreviousStillRecovers.routeBearingRejected,true,'strong live travel direction must reject a 180-degree route match even when the previous camera was wrong');
ok(wrongPreviousStillRecovers.bearing>180,'camera must begin recovering toward the live travel direction instead of staying locked to the wrong route');

const overlappingReturn=[
  [103.0000,15.0000],
  [103.0100,15.0000],
  [103.0000,15.0000]
];
const continuityWins=Q.navigationCameraDecision({
  lat:15.0000,lng:103.0060,
  speed:0,bearing:null,accuracy:5,
  route:overlappingReturn,previousBearing:270,previousRouteProgress:1600
});
ok(continuityWins.routeMatch.progressMeters>1000,'QueueGo matcher must preserve forward route progress instead of snapping back to an overlapping earlier segment');
ok(continuityWins.routeMatch.segmentBearingDegrees>250&&continuityWins.routeMatch.segmentBearingDegrees<290,'continuity-aware matching must stay on the return-direction segment');

const parallelChoice=[
  [103.0000,15.0000],
  [103.0100,15.0000],
  [103.0100,15.00010],
  [103.0000,15.00010]
];
const headingWins=Q.navigationCameraDecision({
  lat:15.00005,lng:103.0060,
  speed:10,bearing:270,accuracy:5,
  route:parallelChoice,previousBearing:270,previousRouteProgress:1500
});
ok(headingWins.routeMatch.segmentBearingDegrees>250&&headingWins.routeMatch.segmentBearingDegrees<290,'QueueGo matcher must use live travel direction to avoid an adjacent opposite carriageway');

const fast=Q.navigationCameraDecision({
  lat:15.00001,lng:103.0020,
  speed:20,bearing:90,accuracy:5,
  route:straight,previousBearing:90
});
eq(fast.zoom,15,'high speed should zoom out');
ok(fast.pitch>=50,'high speed should strengthen forward perspective');

const poor=Q.navigationCameraDecision({
  lat:15.00001,lng:103.0020,
  speed:8,bearing:90,accuracy:95,
  route:straight,previousBearing:90
});
eq(poor.pitch,24,'poor GPS accuracy should reduce perspective');

const freeDrive=Q.navigationCameraDecision({
  lat:15,lng:103,
  speed:8,bearing:90,accuracy:5,
  route:[],previousBearing:90
});
ok(freeDrive,'free-drive camera decision');
ok(freeDrive.center.lng>103,'free-drive 3D camera should look ahead in the travel direction');
ok(freeDrive.center.lng<103.001,'free-drive look-ahead must stay bounded instead of jumping far ahead');
ok(freeDrive.pitch>0,'free-drive smoothness test must retain 3D pitch without an active route');
ok(freeDrive.pitch>=44,'free-drive road test should keep a clearly visible 3D perspective');

const easeCalls=[];
const map={
  bearing:90,
  easeTo:opts=>{easeCalls.push(opts);if(Number.isFinite(opts.bearing))map.bearing=opts.bearing},
  getBearing:()=>map.bearing,
  getContainer:()=>({clientHeight:1000}),
  fitBounds:(bounds,opts)=>{map.fit={bounds,opts}}
};
const followed=Q.followNavigationCamera(map,{
  lat:15.00001,lng:103.0020,speed:8,bearing:270,accuracy:5
},straight,{force:true,duration:400});
ok(!!followed,'follow camera should apply');
eq(easeCalls.length,1,'follow camera should use one camera transition');
ok(easeCalls[0].pitch>0,'follow transition must include pitch');
ok(Number.isFinite(easeCalls[0].bearing),'follow transition must include bearing');
ok(typeof easeCalls[0].easing==='function','follow rotation must use a smooth easing curve');
ok(easeCalls[0].duration>=320,'follow rotation should not snap instantly');
ok(easeCalls[0].easing(.25)>.03,'camera easing must retain non-zero edge velocity between GPS samples');
ok(typeof Q.moveMarkerSmooth==='function','QueueGo Map must expose cancellable smooth marker movement');
eq(followed.animationDuration,easeCalls[0].duration,'follow result must expose the exact camera duration used to synchronize the Rider puck');
eq(easeCalls[0].center[0],103.0020,'follow camera center must stay on Rider longitude instead of projected look-ahead');
ok(Math.abs(easeCalls[0].center[1]-15.00001)<1e-9,'follow camera center must stay on Rider latitude');
eq(easeCalls[0].offset[0],0,'follow anchor must stay horizontally centered');
ok(easeCalls[0].offset[1]>=180&&easeCalls[0].offset[1]<=260,'follow anchor must keep Rider in the lower navigation zone');
eq(Q.navigationScreenAnchorRatio(8),0.72,'medium-speed screen anchor ratio');

const fastTurnCalls=[],slowTurnCalls=[];
const fastTurnMap={
  bearing:0,
  easeTo:opts=>{fastTurnCalls.push(opts);if(Number.isFinite(opts.bearing))fastTurnMap.bearing=opts.bearing},
  getBearing:()=>fastTurnMap.bearing,
  getContainer:()=>({clientHeight:1000})
};
const slowTurnMap={
  bearing:0,
  easeTo:opts=>{slowTurnCalls.push(opts);if(Number.isFinite(opts.bearing))slowTurnMap.bearing=opts.bearing},
  getBearing:()=>slowTurnMap.bearing,
  getContainer:()=>({clientHeight:1000})
};
const fastTurn=Q.followNavigationCamera(fastTurnMap,{lat:15,lng:103,speed:20,bearing:90,accuracy:5},[],{duration:520});
const slowTurn=Q.followNavigationCamera(slowTurnMap,{lat:15,lng:103,speed:3,bearing:90,accuracy:5},[],{duration:520});
ok(fastTurn.bearing>slowTurn.bearing,'high-speed free-drive bearing must react more strongly to the same turn');
ok(fastTurnCalls[0].duration<slowTurnCalls[0].duration,'high-speed camera rotation must complete faster than low-speed rotation');

Q.setNavigationFollow(map,false);
eq(Q.followNavigationCamera(map,{lat:15,lng:103.003,speed:8,bearing:90},straight),null,'free pan must suspend automatic follow');
Q.setNavigationFollow(map,true);


class GestureMap {
  constructor(opts){
    this.opts=opts;this.handlers={};this.zoom=opts.zoom;this.bearing=Number(opts.bearing)||0;this.easeCalls=[];this.maxPitch=opts.maxPitch;
    this.rotationEnabled=!!opts.dragRotate;this.pitchEnabled=!!opts.pitchWithRotate;
    this.touchZoomRotate={enableRotation:()=>{this.rotationEnabled=true},disableRotation:()=>{this.rotationEnabled=false}};
    this.dragRotate={enable:()=>{this.rotationEnabled=true},disable:()=>{this.rotationEnabled=false}};
    this.touchPitch={enable:()=>{this.pitchEnabled=true},disable:()=>{this.pitchEnabled=false}};
  }
  on(name,fn){this.handlers[name]=fn}
  addControl(){}
  easeTo(opts){this.easeCalls.push(opts);if(Number.isFinite(opts.zoom))this.zoom=opts.zoom;if(Number.isFinite(opts.bearing))this.bearing=opts.bearing}
  getZoom(){return this.zoom}
  getBearing(){return this.bearing}
  getContainer(){return {clientHeight:1000}}
  setMaxPitch(v){this.maxPitch=v}
  isStyleLoaded(){return true}
  getLayer(){return null}
  getSource(){return null}
  addSource(){}
  addLayer(){}
}
context.maplibregl={
  Map:GestureMap,
  NavigationControl:function(){},
  AttributionControl:function(){},
  LngLatBounds:class{constructor(){this.points=[]}extend(p){this.points.push(p);return this}}
};
window.maplibregl=context.maplibregl;
const host={classList:{add(){}},replaceChildren(){}};
const gestureMap=Q.create(host,{lat:15,lng:103,zoom:17,navigation:true});

// Startup/refresh regression: manipulating the map before the first GPS fix must not
// silently abandon FOLLOW. The first valid fix should still own the camera.
gestureMap.handlers.dragstart({originalEvent:{touches:[{}]}});
gestureMap.handlers.rotatestart({originalEvent:{touches:[{},{}]}});
gestureMap.handlers.rotateend({originalEvent:{touches:[]}});
ok(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.0015,speed:8,bearing:90,accuracy:5},straight)!=null,
  'pre-fix rotate/pan must retain follow ownership until first GPS fix');

Q.followNavigationCamera(gestureMap,{lat:15,lng:103.002,speed:8,bearing:90,accuracy:5},straight,{force:true});
gestureMap.zoom=18.4;
gestureMap.handlers.zoomend({originalEvent:{touches:[]}});
ok(gestureMap.easeCalls.at(-1).zoom>18.3&&gestureMap.easeCalls.at(-1).zoom<18.5,'pinch release must re-anchor Rider while preserving chosen zoom');
const beforePinchFollow=gestureMap.easeCalls.length;
gestureMap.handlers.dragstart({originalEvent:{touches:[{},{}]}});
const duringPinchCalls=gestureMap.easeCalls.length;
ok(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.0025,speed:8,bearing:90,accuracy:5},straight)!=null,'two-finger pinch must not abandon Rider follow');
eq(gestureMap.easeCalls.length,duringPinchCalls,'automatic camera writes must pause while fingers are rotating/zooming the map');
gestureMap.handlers.rotateend({originalEvent:{touches:[]}});
ok(gestureMap.easeCalls.length>duringPinchCalls,'gesture release must smoothly re-anchor the follow camera');
ok(gestureMap.easeCalls.at(-1).duration>=220,'gesture release rotation must remain animated even when speed-aware turn response is fast');
gestureMap.handlers.dragstart({originalEvent:{touches:[{}]}});
eq(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.003,speed:8,bearing:90,accuracy:5},straight),null,'one-finger pan should still enter free pan');

Q.setNavigationFollow(gestureMap,false);
gestureMap.handlers.rotatestart({originalEvent:{touches:[{},{}]}});
gestureMap.handlers.rotateend({originalEvent:{touches:[]}});
ok(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.0035,speed:8,bearing:90,accuracy:5},straight)!=null,
  'explicit rotate transform must recover follow after dragstart ordering');

Q.setNavigationMode(gestureMap,false,{center:{lat:15,lng:103.004},zoom:15,duration:300});
eq(gestureMap.easeCalls.at(-1).bearing,0,'idle Rider map must return north-up');
eq(gestureMap.easeCalls.at(-1).pitch,0,'idle Rider map must return top-down');
eq(gestureMap.maxPitch,0,'idle Rider map must disable navigation pitch');
eq(gestureMap.rotationEnabled,false,'idle Rider map must disable rotation gestures');
eq(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.005,speed:8,bearing:90},straight,{force:true}),null,'idle Rider map must reject automatic follow even when a caller asks to force it');

Q.setNavigationMode(gestureMap,true);
eq(gestureMap.maxPitch,60,'active job must restore navigation pitch');
eq(gestureMap.rotationEnabled,true,'active job must restore rotation gestures');
ok(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.005,speed:8,bearing:90},straight,{force:true})!=null,'active job must start follow camera');

context.maplibregl={
  LngLatBounds:class{
    constructor(){this.points=[]}
    extend(p){this.points.push(p);return this}
  }
};
const points=[{lat:15,lng:103},{lat:15.01,lng:103.01}];
ok(Q.navigationOverview(map,points,{duration:300}),'overview should fit route');
eq(map.fit.opts.bearing,0,'overview should return north-up');
eq(map.fit.opts.pitch,0,'overview should return top-down');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Web free-drive 3D look-ahead + active-job follow, smooth rotation, gesture ownership and overview'}));
