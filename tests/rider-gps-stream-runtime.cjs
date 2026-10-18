const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('rider/index.html','utf8');let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};
let callback,paints=0,duration;
const initial={lat:15,lng:103,timestamp:1000,bearing:90,speed:10},session={sessionId:'isolated'};
const S={session,user:{id:'isolated-rider'},pos:initial,map:null,watchId:null,online:false,activeTab:'none'};
const ctx=vm.createContext({S,Number,Math,Date,
 riderLocationEpoch:0,riderLastGpsFixAt:1000,riderGpsCadenceMs:1000,
 riderSessionMatches:s=>s===session,validRiderCoordinate:(a,b)=>Number.isFinite(a)&&Number.isFinite(b),
 qgResolveRiderTravelBearing:()=>90,updateRiderSpeedUI:()=>paints++,placeRiderMarker(){},
 haversineKm:()=>0.01,toast(){},navigator:{geolocation:{clearWatch(){},watchPosition:cb=>{callback=cb;return 1}}},
 window:{QueueGoMapWeb:{moveMarkerSmooth:(_m,_lat,_lng,_heading,opts)=>{duration=opts.duration}}},
 qgSmoothedRiderPuckHeading:()=>90,document:{getElementById:()=>null}
});
vm.runInContext(source.slice(source.indexOf('function qgFiniteOrNull('),source.indexOf('function qgNavigationCameraShouldRun(')),ctx);
vm.runInContext(source.slice(source.indexOf('function startGeolocation('),source.indexOf('function qgSyncCardMapMode(')),ctx);
vm.runInContext('startGeolocation()',ctx);
const fix=t=>({timestamp:t,coords:{latitude:15,longitude:103.0001,heading:90,speed:10,accuracy:5}});
callback(fix(500));eq(S.pos,initial,'stale callback cannot rewind the accepted GPS fix');eq(paints,0);
callback(fix(1000));eq(S.pos,initial,'duplicate timestamp cannot restart camera/puck animations');eq(paints,0);
callback(fix(1500));eq(S.pos.timestamp,1500);eq(paints,1,'fresh sample updates once');
S.map={};S.riderMarker={};
vm.runInContext(source.slice(source.indexOf('function qgRiderSpeedKph('),source.indexOf('function qgRouteBundleHTML(')),ctx);
vm.runInContext('riderGpsCadenceMs=1000;placeRiderMarker()',ctx);eq(duration,1050,'omitted duration must use GPS cadence, not null coerced to 220ms');
vm.runInContext('placeRiderMarker(420)',ctx);eq(duration,420,'explicit camera timing is shared with marker');
eq(vm.runInContext('qgRiderSpeedKph({speed:null})',ctx),null,'missing speed must not display a fabricated zero');
console.log(JSON.stringify({checks,failures:0,scope:'actual Rider geolocation callback drops stale/duplicate fixes; marker default cadence and exact camera duration; missing speed stays unknown'}));
