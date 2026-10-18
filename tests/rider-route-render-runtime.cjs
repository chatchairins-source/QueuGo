const fs=require('fs'),vm=require('vm'),assert=require('assert');
const window={},events=new Map(),sources=new Map(),layers=new Map();let ready=true,checks=0;
const document={createElement:()=>({width:0,height:0,getContext:()=>({clearRect(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},getImageData:()=>({width:32,height:32,data:new Uint8Array(4096)})})})};
vm.runInNewContext(fs.readFileSync('queuego-map-web.js','utf8'),{window,document,console,WeakMap,Math,Number,Array,String,Map,Uint8Array});
const Q=window.QueueGoMapWeb;
const map={isStyleLoaded:()=>ready,getStyle:()=>({version:8}),getSource:id=>sources.get(id),getLayer:id=>layers.get(id),addSource(id,s){ready=false;sources.set(id,{data:s.data,setData(d){this.data=d}})},addLayer(l){layers.set(l.id,l)},removeLayer(id){layers.delete(id)},removeSource(id){sources.delete(id)},setPaintProperty(){},on(e,fn){events.set(e,[...(events.get(e)||[]),fn])},off(e,fn){events.set(e,(events.get(e)||[]).filter(x=>x!==fn))},once(e,fn){this.on(e,fn)},hasImage:()=>false,addImage(_id,img){eq(img.width,32)}};
const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
const line=[[103,15],[103,15.01]];
Q.addGeoJsonLine(map,'casing',line,{color:'#fff',width:16});
Q.addGeoJsonLine(map,'route',line,{color:'#e6002d',width:12,arrows:true});
eq(layers.has('route-layer'),true); // source loading must not defer the red fill to a past map load.
eq(layers.has('route-arrows'),true);
Q.removeGeoJsonLine(map,'route');eq(layers.has('route-arrows'),false);
for(const fn of events.get('style.load')||[])fn();eq(layers.has('route-layer'),false);
const calls=[];
const camera={getBearing:()=>45,getContainer:()=>({clientHeight:800}),easeTo:x=>calls.push(x)};
Q.followNavigationCamera(camera,{lat:15,lng:103,bearing:45,speed:8,accuracy:5},[line],{force:true,puckBearing:45});
eq(calls[0].bearing,45); // route course must not steer away from the shared Rider heading.
const multi=[[[103,15],[103,15.001]],[[103,15.001],[103,15.01]],[[103,15.01],[103,15.02]]];
const mid=Q.remainingNavigationRoute(multi,{lat:15.005,lng:103},{bearing:0,speed:8,accuracy:5});
eq(mid.lines.length,2);eq(mid.lines[0][0],[103,15.005]);eq(mid.lines[1],multi[2]);
const d=Q.navigationCameraDecision({lat:15.004,lng:103,bearing:0,speed:8,accuracy:5,route:[line]});
const p=Q.remainingNavigationRoute([line],{lat:15.004,lng:103},{bearing:0,speed:8,accuracy:5});
eq(p.lines[0][0],[103,15.004]);eq(p.lines[0].length,2);eq(line[0],[103,15]);
const near=Q.remainingNavigationRoute([line],{lat:15.004,lng:103.00004},{bearing:0,speed:8,accuracy:5});
eq(near.position.lng,103);eq(near.position.lat,15.004);
eq(Q.remainingNavigationRoute([line],{lat:15.004,lng:103.01},{bearing:0,speed:8,accuracy:5}),null);
eq(Q.remainingNavigationRoute([line],{lat:15.004,lng:103},{bearing:180,speed:8,accuracy:5}),null);
// Replay a style replacement and the real Rider presentation helper.
Q.addGeoJsonLine(map,'route',line,{color:'#e6002d',width:12,arrows:true});
sources.clear();layers.clear();for(const fn of [...events.get('style.load')||[]])fn();
eq(layers.has('route-layer'),true);eq(layers.has('route-arrows'),true);
const S={map,pos:{bearing:0,speed:8,accuracy:5}};
const context=vm.createContext({window,S,riderRouteCache:{map},riderRouteGeometry:[line],Number});
const html=fs.readFileSync('rider/index.html','utf8');
vm.runInContext(html.slice(html.indexOf('function qgRenderRiderRemainingRoute('),html.indexOf('function qgUpdateRiderRouteProgress(')),context);
context.position={lat:15.004,lng:103};vm.runInContext('qgRenderRiderRemainingRoute(position)',context);
eq(sources.get('qg-rider-route-0-source').data.geometry.coordinates[0],[103,15.004]);
context.position={lat:15.005,lng:103};vm.runInContext('qgRenderRiderRemainingRoute(position)',context);
eq(sources.get('qg-rider-route-0-source').data.geometry.coordinates[0],[103,15.005]);
eq(line[0],[103,15]);
S.pos.accuracy=90;context.position={lat:15.006,lng:103};vm.runInContext('qgRenderRiderRemainingRoute(position)',context);
eq(sources.get('qg-rider-route-0-source').data.geometry.coordinates[0],[103,15.005]);
S.pos.accuracy=5;context.position={lat:15.01,lng:103};vm.runInContext('qgRenderRiderRemainingRoute(position)',context);
eq(layers.has('qg-rider-route-0-layer'),false); // no stale tail after reaching the route end.
eq(layers.has('qg-rider-route-0-casing-layer'),false);
context.position={lat:15.00999,lng:103};vm.runInContext('qgRenderRiderRemainingRoute(position)',context);
eq(layers.has('qg-rider-route-0-layer'),false); // stationary GPS jitter cannot regrow a passed tail.

Q.removeGeoJsonLine(map,'qg-rider-route-0');Q.removeGeoJsonLine(map,'qg-rider-route-0-casing');
eq(layers.has('qg-rider-route-0-arrows'),false);
console.log(JSON.stringify({checks,failures:0,scope:'actual MapLibre source-loading/casing/red-fill/direction/cleanup and remaining route, no Production writes'}));
