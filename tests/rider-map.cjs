const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.resolve(__dirname,'../rider/index.html'),'utf8');
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}};const flush=async()=>{for(let i=0;i<12;i++)await Promise.resolve()};
(async()=>{
 eq(source.includes('new longdo.Map'),false);
 eq(source.includes('https://api.longdo.com/map/?key='),false);
 eq(source.includes('QueueGoMapWeb'),true);
 const riderMarkerFn=source.slice(source.indexOf('function placeRiderMarker('),source.indexOf('function qgRouteBundleHTML()'));
 eq(riderMarkerFn.includes('QueueGoMapWeb.moveMarker'),true);
 eq(riderMarkerFn.includes('qgRemoveOverlay(S.riderMarker)'),false);
 const activeRouteFn=source.slice(source.indexOf('function drawActiveRoute()'),source.indexOf('/* ---------- sheet'));
 eq(activeRouteFn.includes("if(isOfferPreview&&!riderRouteCache)focusRiderNavigation"),true);
 eq(activeRouteFn.includes('followNavigationCamera?.(map,pos,riderRouteGeometry'),false);
 eq(source.includes('Route rendering owns geometry only. GPS Follow is the single camera writer.'),true);
 eq(source.includes('function qgNavigationCameraShouldRun(){return !!(S.activeOrder||S.laundryJob||S.openJobs?.[0]?.order)}'),true);
 eq(source.includes('syncRiderNavigationCameraMode();'),true);
 eq(source.includes('data-qg-rider-puck="true"'),true);
 eq(source.includes("'route': '<svg class=\"ico\""),true);
 eq(source.includes('moveMarkerSmooth||window.QueueGoMapWeb.moveMarker'),true);
 eq(source.includes('${QTICON.route}'),true);
 eq(source.includes('queuego-map-web.js?v=20261019-places1'),true);
 eq(source.includes('queuego-map-service.js?v=20261017-1'),true);
 eq(source.includes('api.longdo.com/RouteService/geojson/route'),false);
 eq(source.includes('google.navigation:'),false);
 eq(source.includes('comgooglemaps://'),false);
 eq(source.includes('qgLaunchGoogleNavigation'),false);
 eq(source.includes("service.call('route'"),true);
 const inAppNavFn=source.slice(source.indexOf('function qgNavigateInApp('),source.indexOf('/* order status'));
 eq(inAppNavFn.includes('qgLaunchGoogleNavigation('),false);
 eq(inAppNavFn.includes("toast('เริ่มนำทางด้วย QueueGo')"),true);
 eq(source.includes('RIDER_ROUTE_OFF_ROUTE_METERS=30'),true);
 eq(source.includes('RIDER_ROUTE_REROUTE_SUSTAIN_MS=3000'),true);
 eq(source.includes('RIDER_ROUTE_REROUTE_COOLDOWN_MS=20000'),true);
 eq(source.includes('function qgRiderRouteNeedsReroute('),true);
 eq(source.includes("const directionConflict=decision?.routeBearingRejected===true"),true);
 eq(source.includes('maximumAge:0'),true);
 eq(source.includes('class="qg-speed-pill"'),true);
 eq(source.includes('class="qg-top-actions"'),false);
 eq(source.includes('qg-ready-card'),false);
 eq(source.includes('พร้อมรับงานแล้ว'),false);
 eq((source.match(/id="status-switch"/g)||[]).length,1);
 eq(source.includes("label.textContent=S.online?'พร้อมรับงาน':'พักรับงาน'"),true);
 eq(source.includes("qgHeaderWordmark('qg-brand-pill')"),true);
 eq(source.includes('opacity:.62'),true);
 eq(source.includes('.qg-route-badge,.qg-route-badge.show{display:none!important}'),true);
 eq(source.includes('<header class="qg-topbar"><div class="qg-speed-pill"'),true);
 eq(source.includes('id="status-label">${S.online?\'พร้อมรับงาน\':\'พักรับงาน\'}'),true);
 eq(source.includes('function qgSetHomeDockHTML'),true);
 eq(source.includes('qg-dock-collapsed'),true);
 eq(source.includes('margin:0 18px 6px auto'),true);
 eq(source.includes("mapModeLabel=riderRouteOverviewActive?'ไปที่':'ภาพรวม'"),true);
 eq(source.includes("mapMode.onclick=()=>riderRouteOverviewActive?recenterMap():overviewRoute()"),true);
 eq(source.includes('function qgOverviewRoutePoints()'),true);
 eq(source.includes('riderRouteOverviewActive=true;qgSyncCardMapMode()'),true);
 eq(source.includes('riderRouteOverviewActive=false;qgSyncCardMapMode()'),true);
 eq(source.includes('padding:{top:72,right:52,bottom:bottomPadding,left:52}'),true);
 eq(source.includes("if(!S.activeOrder)riderRouteOverviewActive=false"),true);
 eq(source.includes("aria-label',riderHomeDockCollapsed?'เปิดการ์ดกลับขนาดเดิม':'ย่อการ์ด'"),true);
 eq(source.includes('speed=meters<1.5?0:Math.min(55,meters/seconds)'),true);
 eq(source.includes('riderGpsCadenceMs'),true);
 eq(source.includes('Math.max(260,Math.min(1250,Math.round(riderGpsCadenceMs*1.05)))'),true);
 eq(source.includes('function placeRiderMarker(durationOverride=null)'),true);
 eq(source.includes('placeRiderMarker(followed?.animationDuration)'),true);
 eq(source.includes('function qgRiderCameraBottomInset()'),true);
 eq(source.includes('function qgFollowRiderCamera(force=false,duration=420)'),true);
 eq(source.includes('bottomInset:qgRiderCameraBottomInset()'),true);
 eq(source.includes('requestAnimationFrame(()=>qgFollowRiderCamera(true,360))'),true);
 eq(source.includes('qgFollowRiderCamera(!hadPosition,hadPosition?420:520)'),true);
 eq(source.includes('function qgResolveRiderTravelBearing('),true);
 eq(source.includes('Math.abs(qgRiderBearingDelta(motion,raw))>65'),true);
 eq((source.match(/followNavigationCamera/g)||[]).length,1);
 eq(source.includes('background:#e6002d'),true);
 eq(source.includes("rotationAlignment:'map'"),true);

 let now=100000,requests=[],gate=null,serial=0;
 const timers=new Map(),routeAdds=[],routeRemoves=[],fits=[],destroyed=[],badge={style:{}},label={textContent:''};
 const map={id:'queuego-map'};
 const cameraModes=[],cameraFollows=[];
 const Q={
  addGeoJsonLine:(m,id,line,style)=>{routeAdds.push({m,id,line,style});return true},
  removeGeoJsonLine:(m,id)=>routeRemoves.push({m,id}),
  fit:(m,points,opts)=>fits.push({m,points,opts}),
  destroy:m=>destroyed.push(m),
  setCenter(){},
  setNavigationMode:(m,active,opts)=>{cameraModes.push({m,active,opts});return !!active},
  followNavigationCamera:(m,pos,route,opts)=>{cameraFollows.push({m,pos,route,opts});return {center:pos}},
  navigationCameraDecision:({lat})=>Number(lat)>=13.7
    ? {routeMatch:{offRouteMeters:5},routeBearingRejected:true}
    : {routeMatch:{offRouteMeters:Number(lat)>=13.5?90:5},routeBearingRejected:false},
  marker(){return {remove(){}}}
 };
 const session={authUserId:'a',sessionId:'sa'},S={
  user:{id:'rider'},session,map,pos:{lat:13,lng:100},
  activeOrder:{id:'o',status:'assigned',pickup_latitude:13.1,pickup_longitude:100.1,delivery_latitude:13.2,delivery_longitude:100.2},
  routeOverlays:[],jobMarkers:[],watchId:null
 };
 const routeResponse={routes:[{distance:1200,duration:240,route_id:'qg-route-1',geometry:{type:'LineString',coordinates:[[100,13],[100.1,13.1]]}}]};
 const routeService={call:async(action,body,options)=>{requests.push({action,body,options});if(gate)return gate.promise;return routeResponse}};
 const ctx=vm.createContext({
  S,window:{QueueGoMapWeb:Q,QueueGoMapService:routeService},Date:class extends Date{static now(){return now}},Promise,AbortController,Number,String,JSON,encodeURIComponent,
  document:{hidden:false,getElementById:id=>id==='route-badge'?badge:label},
  navigator:{onLine:true},
  riderSessionMatches:s=>s===S.session,statusFromRow:o=>o.status,haversineKm:(a,b,c,d)=>Math.hypot(a-c,b-d)*111,
  setTimeout:f=>{timers.set(++serial,f);return serial},clearTimeout:id=>timers.delete(id)
 });
 vm.runInContext(source.slice(source.indexOf('const RIDER_CAMERA_SMOOTHNESS_TEST_MODE='),source.indexOf('function initMap()')),ctx);
 vm.runInContext(source.slice(source.indexOf('function qgCleanOrderNote('),source.indexOf('async function qgOpenOrderDetails(')),ctx);
 vm.runInContext(source.slice(source.indexOf('function qgRemoveOverlay('),source.indexOf('/* ---------- sheet')),ctx);
 const bearingCtx=vm.createContext({
  Math,Number,
  validRiderCoordinate:(lat,lng)=>Number.isFinite(Number(lat))&&Number.isFinite(Number(lng)),
  haversineKm:(lat1,lng1,lat2,lng2)=>{
    const R=6371,dLat=(lat2-lat1)*Math.PI/180,dLng=(lng2-lng1)*Math.PI/180;
    const a=Math.sin(dLat/2)**2+Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.sin(dLng/2)**2;
    return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
  }
 });
 vm.runInContext(
   source.slice(source.indexOf('function qgNormalizeRiderBearing'),source.indexOf('function syncRiderNavigationCameraMode')),
   bearingCtx
 );
 for(const missing of ['null','undefined',"''"]){
  eq(vm.runInContext('qgNormalizeRiderBearing('+missing+')',bearingCtx),null);
  eq(vm.runInContext("qgResolveRiderTravelBearing({lat:15,lng:103,bearing:270,timestamp:1000},15,103,"+missing+",10,1500)",bearingCtx),270);
 }
 eq(vm.runInContext('qgNormalizeRiderBearing(0)',bearingCtx),0);
 const fused=vm.runInContext(
   "qgResolveRiderTravelBearing({lat:15,lng:103,bearing:90,timestamp:1000},15,103.00005,270,10,1500)",
   bearingCtx
 );
 eq(fused>80&&fused<100,true);
 const closeHeading=vm.runInContext(
   "qgResolveRiderTravelBearing({lat:15,lng:103,bearing:90,timestamp:1000},15,103.00005,95,10,1500)",
   bearingCtx
 );
 eq(closeHeading,95);

 const run=s=>vm.runInContext(s,ctx);

 gate=deferred();
 const first=run('drawActiveRoute()');
 for(let i=0;i<20;i++)run('drawActiveRoute()');
 eq(requests.length,1);
 eq(requests[0].action,'route');
 eq(requests[0].body.profile,'rider_delivery');
 eq(requests[0].body.points[1].lat,13.1);
 gate.resolve(routeResponse);gate=null;await first;
 eq(routeAdds.length,2);
 eq(routeAdds.map(x=>[x.style.color,x.style.width]),[['#ffffff',16],['#e6002d',12]]);
 eq(routeAdds[0].line,routeAdds[1].line);
 eq(S.routeOverlays.length,2);
 eq(label.textContent,'ไปร้าน • 1.2 กม. • 4 นาที');
 eq(timers.size,0);

 // A nearby parallel/opposite route must not suppress reroute evidence.
 run('riderRouteOffRouteSince=0;riderRouteLastRerouteAt=0');
 S.pos={lat:13.7,lng:100,accuracy:5,speed:6,bearing:180};
 eq(run('qgRiderRouteNeedsReroute(S.pos,riderRouteCache.scope,S.map)'),false);
 now+=2999;
 eq(run('qgRiderRouteNeedsReroute(S.pos,riderRouteCache.scope,S.map)'),false);
 now+=1;
 eq(run('qgRiderRouteNeedsReroute(S.pos,riderRouteCache.scope,S.map)'),true);
 run('riderRouteOffRouteSince=0;riderRouteLastRerouteAt=0');
 S.pos={lat:13,lng:100,accuracy:5,speed:6,bearing:90};

 for(let i=0;i<20;i++)await run('drawActiveRoute()');
 eq(requests.length,1);
 now+=15000;S.pos={lat:13.001,lng:100,accuracy:5};await run('drawActiveRoute()');
 eq(requests.length,1);
 eq(routeRemoves.length,0);

 // A stable QueueGo route must survive time/movement and reroute only after
 // an accurate off-route condition is sustained for three seconds.
 S.pos={lat:13.5,lng:100,accuracy:5};
 await run('drawActiveRoute()');
 eq(requests.length,1);
 now+=2999;
 await run('drawActiveRoute()');
 eq(requests.length,1);
 now+=1;
 await run('drawActiveRoute()');
 eq(requests.length,2);
 eq(routeRemoves.length,2);

 // Weak GPS must never accumulate reroute evidence.
 S.pos={lat:13.5,lng:100,accuracy:90};
 for(let i=0;i<5;i++)await run('drawActiveRoute()');
 eq(requests.length,2);

 // Changing pickup to delivery invalidates an in-flight old-target request immediately.
 now+=21000;S.pos={lat:13.002,lng:100,accuracy:5};run('clearRiderRoute()');gate=deferred();
 const old=run('drawActiveRoute()'),oldSignal=requests.at(-1).options.signal;
 S.activeOrder.status='in_progress';const oldGate=gate;gate=null;
 await run('drawActiveRoute()');
 eq(oldSignal.aborted,true);
 eq(requests.at(-1).body.points[1].lat,13.2);
 const count=routeAdds.length;oldGate.resolve(routeResponse);await old;
 eq(routeAdds.length,count);
 eq(label.textContent.startsWith('ไปหาลูกค้า'),true);

 // A newly received unclaimed offer immediately routes and fits QueueGo Map to Rider + pickup.
 S.activeOrder=null;S.online=true;
 S.openJobs=[{order:{id:'offer',status:'searching_rider',pickup_latitude:14,pickup_longitude:101,delivery_latitude:14.5,delivery_longitude:101.5}}];
 S.pos={lat:13,lng:100};
 await run('drawActiveRoute()');
 eq(requests.at(-1).body.points[1].lat,14);
 eq(label.textContent.startsWith('ไปร้าน'),true);
 eq(fits.at(-1).points[0].lat,13);
 eq(fits.at(-1).points[1].lat,14);
 eq(fits.at(-1).opts.maxZoom,16);
 eq(run('qgNavigationCameraShouldRun()'),true);

 S.openJobs=[];S.activeOrder={id:'o',status:'assigned',pickup_latitude:13.1,pickup_longitude:100.1,delivery_latitude:13.2,delivery_longitude:100.2};
 eq(run('qgNavigationCameraShouldRun()'),true);
 run('syncRiderNavigationCameraMode(true)');
 eq(cameraModes.at(-1).active,true);
 eq(cameraFollows.length>0,true);

 // Completing a job while the provider is pending cannot redraw its route.
 now+=21000;S.pos={lat:13.003,lng:100,accuracy:5};run('clearRiderRoute()');gate=deferred();
 const retiring=run('drawActiveRoute()'),retiredSignal=requests.at(-1).options.signal;
 S.activeOrder=null;run('syncRiderNavigationCameraMode(true)');await run('drawActiveRoute()');
 eq(run('RIDER_CAMERA_SMOOTHNESS_TEST_MODE'),false);
 eq(cameraModes.at(-1).active,false);
 eq(retiredSignal.aborted,true);
 gate.resolve(routeResponse);gate=null;await retiring;
 eq(S.routeOverlays.length,0);
 eq(badge.style.display,'none');

 // Failed routing is explicitly an error, never a fabricated straight-line route.
 S.activeOrder={id:'new',status:'assigned',pickup_latitude:14,pickup_longitude:101};
 ctx.window.QueueGoMapService.call=async()=>({routes:[]});
 await run('drawActiveRoute()');
 eq(label.textContent.includes('โหลดเส้นทางไม่สำเร็จ'),true);
 eq(S.routeOverlays.length,0);

 // Provider timeout releases the request so the next attempt can recover.
 ctx.window.QueueGoMapService.call=async(_action,_body,{signal})=>new Promise((_r,reject)=>signal.addEventListener('abort',()=>reject(Error('timeout')),{once:true}));
 const timeout=run('drawActiveRoute()');for(const fn of [...timers.values()])fn();await timeout;
 eq(timers.size,0);
 ctx.window.QueueGoMapService.call=async()=>routeResponse;
 await run('drawActiveRoute()');
 eq(S.routeOverlays.length,2);

 // Invalid/missing coordinates never become a route to (0,0).
 S.activeOrder.pickup_latitude=null;await run('drawActiveRoute()');
 eq(S.routeOverlays.length,0);
 eq(run('validRiderCoordinate(null,100)'),false);

 // GPS callbacks are owned by a single live watcher, including watch ID zero.
 const watches=[],cleared=[];let renders=0;
 ctx.navigator.geolocation={watchPosition:(ok,error)=>{watches.push({ok,error});return watches.length-1},clearWatch:id=>cleared.push(id)};
 ctx.toast=()=>{};ctx.placeRiderMarker=()=>renders++;ctx.updateRiderSpeedUI=()=>{};ctx.renderHomeDock=()=>{};ctx.pushLocation=()=>{};S.online=false;
 vm.runInContext(source.slice(source.indexOf('function startGeolocation()'),source.indexOf('function recenterMap()')),ctx);
 run('startGeolocation();startGeolocation()');
 eq(cleared[0],0);
 watches[0].ok({coords:{latitude:15,longitude:102}});eq(renders,0);
 watches[1].ok({coords:{latitude:15,longitude:102}});eq(renders,1);
 run('disposeRiderMap()');
 watches[1].ok({coords:{latitude:16,longitude:103}});eq(renders,1);
 eq(S.pos,null);eq(S.map,null);eq(destroyed.at(-1),map);

 // Position uploads use the captured account and coalesce a GPS burst to the latest position.
 vm.runInContext(source.slice(source.indexOf('let riderLocationWrite='),source.indexOf('let riderRefreshTask=')),ctx);
 S.user={id:'rider'};S.session=session;S.riderProfile={id:'profile'};S.pos={lat:13,lng:100};S.online=true;
 const writes=[];let tokenGate=deferred();ctx.getAccessToken=()=>tokenGate.promise;
 ctx.sbTable=async(path,opts)=>{writes.push({path,opts});return []};
 const stale=run('pushLocation()');S.session={authUserId:'b',sessionId:'sb'};S.user={id:'other'};
 tokenGate.resolve('old-token');await stale;eq(writes.length,0);

 S.session=session;S.user={id:'rider'};ctx.getAccessToken=async()=>'fixture';
 const writeGate=deferred();ctx.sbTable=async(path,opts)=>{writes.push({path,opts});if(writes.length===1)await writeGate.promise;return []};
 const burst=run('pushLocation()');await flush();
 for(let i=0;i<20;i++){S.pos={lat:13+i/100,lng:100};run('pushLocation()')}
 eq(writes.length,1);writeGate.resolve();await burst;
 eq(writes.length,2);eq(writes[1].opts.body.latitude,13.19);eq(writes[1].path,'rider_profiles?user_id=eq.rider');

 console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Rider renderer + QueueGo sole route authority + local route matching/camera + route coalescing/cancellation/timeout/GPS ownership; no external RouteService fallback'}));
})().catch(e=>{console.error(e);process.exit(1)});
