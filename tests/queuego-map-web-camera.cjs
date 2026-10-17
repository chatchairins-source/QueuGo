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

const easeCalls=[];
const map={
  easeTo:opts=>easeCalls.push(opts),
  fitBounds:(bounds,opts)=>{map.fit={bounds,opts}}
};
const followed=Q.followNavigationCamera(map,{
  lat:15.00001,lng:103.0020,speed:8,bearing:270,accuracy:5
},straight,{force:true,duration:400});
ok(!!followed,'follow camera should apply');
eq(easeCalls.length,1,'follow camera should use one camera transition');
ok(easeCalls[0].pitch>0,'follow transition must include pitch');
ok(Number.isFinite(easeCalls[0].bearing),'follow transition must include bearing');

Q.setNavigationFollow(map,false);
eq(Q.followNavigationCamera(map,{lat:15,lng:103.003,speed:8,bearing:90},straight),null,'free pan must suspend automatic follow');
Q.setNavigationFollow(map,true);

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

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Web 3D follow camera, road heading, turn anticipation, free-pan ownership and overview'}));
