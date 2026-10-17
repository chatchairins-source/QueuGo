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
  speed:10,bearing:270,accuracy:5,
  route:straight,previousBearing:90
});
ok(roadWins.bearing>85&&roadWins.bearing<95,'matched road must beat opposite noisy GPS heading');

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
  lat:15.0000,lng:103.0000,
  speed:8,bearing:90,accuracy:5,
  route:[],previousBearing:90
});
ok(freeDrive.center.lng>103.0003,'route-less 3D follow should keep looking ahead instead of pinning Rider to dead-center');
ok(freeDrive.center.lng<103.0010,'route-less look-ahead must stay bounded and avoid camera jumps');

const easeCalls=[];
const map={
  bearing:90,
  easeTo:opts=>{easeCalls.push(opts);if(Number.isFinite(opts.bearing))map.bearing=opts.bearing},
  getBearing:()=>map.bearing,
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
ok(gestureMap.easeCalls.at(-1).duration>=320,'gesture release rotation must be animated, not snapped');
gestureMap.handlers.dragstart({originalEvent:{touches:[{}]}});
eq(Q.followNavigationCamera(gestureMap,{lat:15,lng:103.003,speed:8,bearing:90,accuracy:5},straight),null,'one-finger pan should still enter free pan');

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

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Web 3D follow camera, route-less forward anchor, smooth rotation, gesture ownership and overview'}));
