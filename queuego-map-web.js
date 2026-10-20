(function(){
'use strict';
const QG_OSM_STYLE={
  version:8,
  sources:{
    queuego_osm:{
      type:'raster',
      tiles:['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize:256,
      attribution:'© OpenStreetMap contributors'
    }
  },
  layers:[
    {id:'queuego-bg',type:'background',paint:{'background-color':'#f3f0e8'}},
    {id:'queuego-osm',type:'raster',source:'queuego_osm'}
  ]
};
function ensureQueueGoMapCss(){
  if(document.getElementById('queuego-map-control-css'))return;
  const style=document.createElement('style');
  style.id='queuego-map-control-css';
  style.textContent=`
    .queuego-map-host .maplibregl-ctrl-attrib{
      font:9px/13px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif!important;
      max-width:calc(100% - 8px)!important;
      padding:1px 22px 1px 5px!important;
      min-height:18px!important;
      border-radius:9px!important;
    }
    .queuego-map-host .maplibregl-ctrl-attrib-inner,
    .queuego-map-host .maplibregl-ctrl-attrib a{
      font-size:9px!important;
      line-height:13px!important;
    }
    .queuego-map-host .maplibregl-ctrl-attrib-button{
      width:18px!important;
      height:18px!important;
      min-width:18px!important;
      min-height:18px!important;
      padding:0!important;
      margin:0!important;
      border:0!important;
      border-radius:50%!important;
      background-size:14px 14px!important;
      box-shadow:none!important;
    }
  `;
  document.head.appendChild(style);
}
const valid=(lat,lng)=>navHasNumber(lat)&&navHasNumber(lng)&&
  Number(lat)>=5&&Number(lat)<=21&&Number(lng)>=97&&Number(lng)<=106;

const navHasNumber=value=>value!==null&&value!==undefined&&value!==''&&Number.isFinite(Number(value));
const navigationStates=new WeakMap();
const markerAnimationStates=new WeakMap();
const rad=v=>v*Math.PI/180;
const deg=v=>v*180/Math.PI;
const navNormalizeBearing=v=>((Number(v)%360)+360)%360;
const navShortestDelta=(from,to)=>((navNormalizeBearing(to)-navNormalizeBearing(from)+540)%360)-180;
const navDistanceMeters=(a,b)=>{
  const r=6371000,dLat=rad(b.lat-a.lat),dLng=rad(b.lng-a.lng),lat1=rad(a.lat),lat2=rad(b.lat);
  const s1=Math.sin(dLat/2),s2=Math.sin(dLng/2);
  return r*2*Math.atan2(Math.sqrt(s1*s1+Math.cos(lat1)*Math.cos(lat2)*s2*s2),Math.sqrt(Math.max(0,1-(s1*s1+Math.cos(lat1)*Math.cos(lat2)*s2*s2))));
};
const navBearing=(a,b)=>{
  const lat1=rad(a.lat),lat2=rad(b.lat),dLng=rad(b.lng-a.lng);
  return navNormalizeBearing(deg(Math.atan2(Math.sin(dLng)*Math.cos(lat2),Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLng))));
};
const navFreeDriveLookAheadMeters=spd=>spd==null?25:spd>=18?80:spd>=11?65:spd>=5?50:spd>=2?32:15;
const navScreenAnchorRatio=spd=>spd==null?.68:spd>=18?.76:spd>=11?.74:spd>=5?.72:spd>=2?.69:.66;
function navProjectForward(point,bearing,distanceMeters){
  if(!point||!valid(point.lat,point.lng)||!navHasNumber(bearing)||!navHasNumber(distanceMeters)||Number(distanceMeters)<=0)return null;
  const R=6371000,angular=Number(distanceMeters)/R,br=rad(navNormalizeBearing(bearing)),lat1=rad(point.lat),lon1=rad(point.lng);
  const lat2=Math.asin(Math.sin(lat1)*Math.cos(angular)+Math.cos(lat1)*Math.sin(angular)*Math.cos(br));
  const lon2=lon1+Math.atan2(Math.sin(br)*Math.sin(angular)*Math.cos(lat1),Math.cos(angular)-Math.sin(lat1)*Math.sin(lat2));
  const projected={lat:deg(lat2),lng:((deg(lon2)+540)%360)-180};
  return valid(projected.lat,projected.lng)?projected:null;
}
function navRouteParts(route){
  if(!Array.isArray(route)||!route.length)return[];
  const first=route[0];
  const raw=Array.isArray(first)&&first.length>=2&&Number.isFinite(Number(first[0]))?[route]:route;
  return raw.map(part=>(Array.isArray(part)?part:[]).map(p=>({lng:Number(p?.[0]),lat:Number(p?.[1])})).filter(p=>valid(p.lat,p.lng))).filter(part=>part.length>1);
}
function navProject(point,a,b){
  const ref=rad(point.lat),mLat=111320,mLng=111320*Math.max(.01,Math.cos(ref));
  const ax=(a.lng-point.lng)*mLng,ay=(a.lat-point.lat)*mLat,bx=(b.lng-point.lng)*mLng,by=(b.lat-point.lat)*mLat;
  const dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
  const t=den<=.0001?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/den));
  const x=ax+dx*t,y=ay+dy*t;
  return {point:{lat:a.lat+(b.lat-a.lat)*t,lng:a.lng+(b.lng-a.lng)*t},fraction:t,distance:Math.sqrt(x*x+y*y)};
}
function navMatchRoute(route,point,{bearing=null,speed=null,previousProgress=null}={}){
  let best=null;
  const spd=navHasNumber(speed)&&Number(speed)>=0?Number(speed):null;
  const travelBearing=spd!=null&&spd>=2&&navHasNumber(bearing)?navNormalizeBearing(Number(bearing)):null;
  const priorProgress=navHasNumber(previousProgress)?Number(previousProgress):null;
  for(const [partIndex,part] of navRouteParts(route).entries()){
    let walked=0,total=0;
    const lengths=[];
    for(let i=0;i<part.length-1;i++){const d=navDistanceMeters(part[i],part[i+1]);lengths.push(d);total+=d}
    for(let i=0;i<part.length-1;i++){
      const leg=lengths[i];if(leg<=.01){walked+=leg;continue}
      const p=navProject(point,part[i],part[i+1]);
      const progress=walked+leg*p.fraction;
      const segmentBearing=navBearing(part[i],part[i+1]);
      const headingDelta=travelBearing==null?null:Math.abs(navShortestDelta(travelBearing,segmentBearing));
      const headingPenalty=headingDelta==null?0:
        headingDelta<=35?headingDelta*.08:
        headingDelta<=75?2.8+(headingDelta-35)*.35:
        16.8+(headingDelta-75)*1.4;
      const backtrack=priorProgress==null?0:Math.max(0,priorProgress-progress-35);
      const continuityPenalty=backtrack*2.5;
      const score=p.distance+headingPenalty+continuityPenalty;
      if(!best||score<best.score){
        best={part,partIndex,snapped:p.point,offRouteMeters:p.distance,progressMeters:progress,geometryDistanceMeters:total,segmentBearingDegrees:segmentBearing,headingDeltaDegrees:headingDelta,score};
      }
      walked+=leg;
    }
  }
  return best;
}
function navPointAt(part,targetMeters){
  if(!Array.isArray(part)||part.length<2)return null;
  if(targetMeters<=0)return part[0];
  let walked=0;
  for(let i=0;i<part.length-1;i++){
    const leg=navDistanceMeters(part[i],part[i+1]);if(leg<=.01)continue;
    if(walked+leg>=targetMeters){
      const t=Math.max(0,Math.min(1,(targetMeters-walked)/leg));
      return {lat:part[i].lat+(part[i+1].lat-part[i].lat)*t,lng:part[i].lng+(part[i+1].lng-part[i].lng)*t};
    }
    walked+=leg;
  }
  return part[part.length-1];
}
function navSmoothBearing(previous,target,factor){
  if(!navHasNumber(target))return navHasNumber(previous)?navNormalizeBearing(previous):null;
  const t=navNormalizeBearing(target);if(!navHasNumber(previous))return t;
  const p=navNormalizeBearing(previous),delta=navShortestDelta(p,t);
  if(Math.abs(delta)<.45)return p;
  return navNormalizeBearing(p+delta*Math.max(.05,Math.min(1,Number(factor)||.35)));
}
const navCameraEasing=t=>{
  const x=Math.max(0,Math.min(1,Number(t)||0));
  const cubic=x<.5?4*x*x*x:1-Math.pow(-2*x+2,3)/2;
  // Keep some velocity at both ends so a ~1 Hz GPS stream does not look like
  // a sequence of stop/start camera animations.
  return .24*x+.76*cubic;
};
function navRotationDuration(currentBearing,targetBearing,cadenceMs,fallback=420,speed=null){
  const spd=navHasNumber(speed)&&Number(speed)>=0?Number(speed):null;
  const profile=spd==null
    ? {min:420,max:900,cadence:.90,base:340,gain:6.2}
    : spd>=18
      ? {min:220,max:460,cadence:.58,base:170,gain:3.7}
      : spd>=11
        ? {min:260,max:540,cadence:.64,base:200,gain:4.1}
        : spd>=5
          ? {min:310,max:660,cadence:.72,base:240,gain:4.8}
          : spd>=2
            ? {min:360,max:760,cadence:.80,base:290,gain:5.4}
            : {min:420,max:900,cadence:.90,base:340,gain:6.2};
  if(!navHasNumber(currentBearing)||!navHasNumber(targetBearing)){
    return Math.max(profile.min,Math.min(profile.max,Number(fallback)||520));
  }
  const angle=Math.abs(navShortestDelta(currentBearing,targetBearing));
  const cadence=navHasNumber(cadenceMs)&&Number(cadenceMs)>0?Number(cadenceMs):Number(fallback)||520;
  const cadenceDuration=Math.max(profile.min,Math.min(profile.max,Math.round(cadence*profile.cadence)));
  const angleDuration=Math.max(profile.min,Math.min(profile.max,Math.round(profile.base+angle*profile.gain)));
  return Math.max(profile.min,Math.min(profile.max,Math.max(cadenceDuration,angleDuration)));
}
function navSpeedAwareZoom(speed,baseZoom=17){
  const base=Math.max(14,Math.min(20,Number(baseZoom)||17));
  if(!navHasNumber(speed)||Number(speed)<0)return base;
  const spd=Math.max(0,Math.min(25,Number(speed)));
  if(spd<=.8)return Math.max(base,18.4);
  const dynamic=18.4-Math.min(3.4,spd*.14);
  return Math.round(Math.max(15,Math.min(18.4,dynamic))*10)/10;
}
function navCameraDecision({lat,lng,speed=null,bearing=null,accuracy=null,route=[],previousBearing=null,previousRouteProgress=null,baseZoom=17}={}){
  if(!valid(lat,lng))return null;
  const point={lat:Number(lat),lng:Number(lng)},spd=navHasNumber(speed)&&Number(speed)>=0?Number(speed):null;
  const acc=navHasNumber(accuracy)?Number(accuracy):null;
  const gpsBearing=navHasNumber(bearing)?navNormalizeBearing(Number(bearing)):null;
  const match=navMatchRoute(route,point,{bearing:gpsBearing,speed:spd,previousProgress:previousRouteProgress});
  const reliable=match&&match.offRouteMeters<=60?match:null;
  let routeBearing=reliable?.segmentBearingDegrees??null;
  const remaining=match?Math.max(0,match.geometryDistanceMeters-match.progressMeters):null;
  if(reliable){
    const desired=spd==null?24:spd>=18?70:spd>=11?55:spd>=5?38:spd>=2?26:14;
    const ahead=remaining==null?desired:Math.min(desired,Math.max(0,remaining*.45));
    const future=navPointAt(reliable.part,Math.min(reliable.geometryDistanceMeters,reliable.progressMeters+ahead));
    if(future&&navDistanceMeters(reliable.snapped,future)>=5){
      const futureBearing=navBearing(reliable.snapped,future),delta=Math.max(-70,Math.min(70,navShortestDelta(routeBearing,futureBearing)));
      const bounded=navNormalizeBearing(routeBearing+delta);
      const blend=spd==null?.32:spd>=18?.62:spd>=11?.58:spd>=5?.50:spd>=2?.36:.22;
      routeBearing=navSmoothBearing(routeBearing,bounded,blend);
    }
  }
  const routeBearingDelta=gpsBearing!=null&&routeBearing!=null
    ? Math.abs(navShortestDelta(gpsBearing,routeBearing))
    : null;
  const previousRouteDelta=navHasNumber(previousBearing)&&routeBearing!=null
    ? Math.abs(navShortestDelta(previousBearing,routeBearing))
    : null;
  const routeBearingOpposesMotion=(spd??0)>=2&&gpsBearing!=null&&(
    routeBearingDelta>110||
    (routeBearingDelta>75&&(previousRouteDelta==null||previousRouteDelta>75))
  );
  const routeBearingUsable=routeBearing!=null&&!routeBearingOpposesMotion;
  const desiredBearing=routeBearingUsable?routeBearing:(spd==null||spd<.83?previousBearing:(gpsBearing??previousBearing));
  const smoothing=spd==null?.30:spd>=18?.68:spd>=11?.62:spd>=5?.52:spd>=2?.38:.24;
  const smoothedBearing=navSmoothBearing(previousBearing,desiredBearing,smoothing);
  const cameraBearing=smoothedBearing??0;
  let zoom=remaining!=null&&remaining<=60?19:remaining!=null&&remaining<=180?18:navSpeedAwareZoom(spd,baseZoom);
  if(acc!=null&&acc>60)zoom=Math.max(14,zoom-.7);
  zoom=Math.round(Math.max(14,Math.min(20,zoom))*10)/10;
  const freeDrive=!reliable;
  const pitch=Math.max(0,Math.min(55,
    acc!=null&&acc>80?(freeDrive?28:24):
    remaining!=null&&remaining<=60?28:
    remaining!=null&&remaining<=180?34:
    freeDrive?(spd==null?44:spd>=18?53:spd>=11?51:spd>=5?48:spd>=2?44:40):
    spd==null?38:spd>=18?52:spd>=11?49:spd>=5?45:spd>=2?40:34
  ));
  const look=spd==null?55:spd>=18?150:spd>=11?120:spd>=5?85:spd>=2?55:30;
  const lookAhead=remaining==null?look:Math.min(look,Math.max(0,remaining*.55));
  const center=reliable&&routeBearingUsable
    ? (navPointAt(reliable.part,Math.min(reliable.geometryDistanceMeters,reliable.progressMeters+lookAhead))||point)
    : (smoothedBearing!=null?(navProjectForward(point,smoothedBearing,navFreeDriveLookAheadMeters(spd))||point):point);
  return {center,bearing:cameraBearing,pitch,zoom,routeMatch:match,remainingRouteMeters:remaining,routeBearingRejected:routeBearing!=null&&!routeBearingUsable};
}
function navState(map){
  let state=navigationStates.get(map);
  if(!state){
    state={
      follow:true,navigationActive:true,bearing:null,lastFix:null,lastRoute:[],baseZoom:17,lastDecision:null,lastAppliedAt:0,
      manualZoom:null,manualZoomUntil:0,reanchorTimer:null,gestureActive:false,lastBottomInset:0,headingOnly:false
    };
    navigationStates.set(map,state);
  }
  return state;
}
function setNavigationFollow(map,enabled=true){
  if(!map)return false;
  const state=navState(map);
  state.follow=!!enabled;
  if(!state.follow){
    state.gestureActive=false;
    if(state.reanchorTimer){clearTimeout(state.reanchorTimer);state.reanchorTimer=null}
  }
  return state.follow;
}
function setNavigationMode(map,enabled,{center=null,zoom=null,duration=420}={}){
  if(!map)return false;
  const state=navState(map),active=!!enabled;
  state.navigationActive=active;
  state.gestureActive=false;
  if(state.reanchorTimer){clearTimeout(state.reanchorTimer);state.reanchorTimer=null}
  state.manualZoom=null;
  state.manualZoomUntil=0;
  if(active){
    state.follow=true;
    try{map.setMaxPitch?.(60)}catch(_){}
    try{map.dragRotate?.enable?.()}catch(_){}
    try{map.touchZoomRotate?.enableRotation?.()}catch(_){}
    try{map.touchPitch?.enable?.()}catch(_){}
    return true;
  }
  state.follow=false;
  state.headingOnly=false;
  state.bearing=0;
  state.lastDecision=null;
  state.lastDecisionApplied=null;
  try{map.dragRotate?.disable?.()}catch(_){}
  try{map.touchZoomRotate?.disableRotation?.()}catch(_){}
  try{map.touchPitch?.disable?.()}catch(_){}
  try{map.setMaxPitch?.(0)}catch(_){}
  const camera={bearing:0,pitch:0,duration:Math.max(0,Number(duration)||0),easing:navCameraEasing,essential:true};
  if(center&&valid(center.lat,center.lng))camera.center=[Number(center.lng),Number(center.lat)];
  if(navHasNumber(zoom))camera.zoom=Number(zoom);
  try{map.easeTo(camera)}catch(_){return false}
  return false;
}
function followNavigationCamera(map,fix={},route=[],{force=false,duration=420,baseZoom=17,preserveZoom=false,bottomInset=null,puckBearing=null,headingOnly=false}={}){
  if(!map)return null;
  const state=navState(map);
  const normalizedRoute=Array.isArray(route)?route:[];
  const sameRoute=state.lastRoute===normalizedRoute;
  const previousRouteProgress=sameRoute?state.lastDecision?.routeMatch?.progressMeters:null;
  state.lastFix={...fix};
  state.lastRoute=normalizedRoute;
  state.baseZoom=baseZoom;
  state.headingOnly=!!headingOnly;
  if(navHasNumber(bottomInset)&&Number(bottomInset)>=0)state.lastBottomInset=Number(bottomInset);
  if(force){
    state.follow=true;
    state.gestureActive=false;
    state.manualZoom=null;
    state.manualZoomUntil=0;
  }
  if(!state.navigationActive||!state.follow)return null;
  const renderedBearing=Number(map.getBearing?.());
  const previousBearing=Number.isFinite(renderedBearing)?renderedBearing:state.bearing;
  const decision=navCameraDecision({...fix,route:normalizedRoute,previousBearing,previousRouteProgress,baseZoom});if(!decision)return null;
  const now=Date.now();
  let targetZoom=decision.zoom;
  if(preserveZoom||state.manualZoomUntil>now){
    const currentZoom=Number(map.getZoom?.());
    if(Number.isFinite(currentZoom))targetZoom=currentZoom;
    else if(Number.isFinite(state.manualZoom))targetZoom=state.manualZoom;
  }else{
    state.manualZoom=null;
    state.manualZoomUntil=0;
  }
  // Use the same confidence-bounded presentation point as the Rider puck.
  // Raw fixes stay authoritative and are never rewritten for display alignment.
  const presentation=remainingNavigationRoute(normalizedRoute,fix,fix);
  const riderCenter=presentation?.position||(valid(fix?.lat,fix?.lng)
    ? {lat:Number(fix.lat),lng:Number(fix.lng)}
    : decision.center);
  const speed=navHasNumber(fix?.speed)&&Number(fix.speed)>=0?Number(fix.speed):null;
  const target={...decision,bearing:navHasNumber(puckBearing)?navNormalizeBearing(Number(puckBearing)):decision.bearing,pitch:headingOnly?0:decision.pitch,zoom:targetZoom,cameraCenter:riderCenter};
  state.bearing=target.bearing;
  state.lastDecision=target;
  if(state.gestureActive&&!force)return target;

  const previous=state.lastDecisionApplied;
  if(!force&&previous){
    const centerDelta=navDistanceMeters(previous.cameraCenter||previous.center,target.cameraCenter);
    const bearingDelta=Math.abs(navShortestDelta(previous.bearing,target.bearing));
    const pitchDelta=Math.abs(Number(previous.pitch)-Number(target.pitch));
    const zoomDelta=Math.abs(Number(previous.zoom)-Number(target.zoom));
    if(centerDelta<.8&&bearingDelta<.7&&pitchDelta<.5&&zoomDelta<.05)return target;
  }
  const cadence=state.lastAppliedAt?Math.max(0,now-state.lastAppliedAt):duration;
  const smoothDuration=force
    ? Math.max(320,Math.min(560,Number(duration)||420))
    : navRotationDuration(previousBearing,target.bearing,cadence,duration,fix?.speed);
  target.animationDuration=smoothDuration;
  let container=null;
  try{container=map.getContainer?.()||null}catch(_){}
  const viewportHeight=Math.max(0,Number(container?.clientHeight)||0);
  const ratio=navScreenAnchorRatio(speed);
  const inset=Math.max(0,Math.min(viewportHeight*.6,Number(state.lastBottomInset)||0));
  const desiredY=viewportHeight>0
    ? Math.max(viewportHeight*.46,Math.min(viewportHeight*ratio,viewportHeight-inset-56))
    : 0;
  const offsetY=viewportHeight>0?Math.round(desiredY-viewportHeight/2):0;
  try{
    map.easeTo({
      center:[target.cameraCenter.lng,target.cameraCenter.lat],
      offset:[0,offsetY],
      bearing:target.bearing,
      pitch:target.pitch,
      zoom:target.zoom,
      duration:smoothDuration,
      easing:navCameraEasing,
      essential:true
    });
  }catch(_){return null}
  state.lastDecisionApplied=target;
  state.lastAppliedAt=now;
  return target;
}
function reanchorNavigationAfterGesture(map){
  const state=navState(map);
  if(!state.navigationActive||!state.follow||!state.lastFix)return false;
  state.gestureActive=true;
  const currentZoom=Number(map.getZoom?.());
  if(Number.isFinite(currentZoom)){
    state.manualZoom=currentZoom;
    state.manualZoomUntil=Date.now()+12000;
  }
  if(state.reanchorTimer)clearTimeout(state.reanchorTimer);
  state.reanchorTimer=setTimeout(()=>{
    state.reanchorTimer=null;
    if(!state.follow||!state.lastFix){state.gestureActive=false;return}
    state.gestureActive=false;
    followNavigationCamera(map,state.lastFix,state.lastRoute,{
      duration:650,
      baseZoom:state.baseZoom,
      preserveZoom:true,
      bottomInset:state.lastBottomInset,
      headingOnly:state.headingOnly
    });
  },110);
  return true;
}
function reanchorNavigationAfterZoom(map){return reanchorNavigationAfterGesture(map)}
function navigationOverview(map,points,{padding=64,maxZoom=16,duration=450}={}){
  if(!map)return false;setNavigationFollow(map,false);
  const rows=(points||[]).filter(p=>valid(p.lat,p.lng));if(!rows.length)return false;
  try{
    if(rows.length===1){map.easeTo({center:[Number(rows[0].lng),Number(rows[0].lat)],zoom:maxZoom,bearing:0,pitch:0,duration,essential:true});return true}
    const bounds=new maplibregl.LngLatBounds();rows.forEach(p=>bounds.extend([Number(p.lng),Number(p.lat)]));
    map.fitBounds(bounds,{padding,maxZoom,duration,bearing:0,pitch:0});return true;
  }catch(_){return false}
}

function create(host,{lat=15,lng=101,zoom=6,minZoom=5,maxZoom=19,interactive=true,navigation=false}={}){
  if(!host||typeof window.maplibregl==='undefined') return null;
  ensureQueueGoMapCss();
  host.classList.add('queuego-map-host');
  host.replaceChildren();
  const map=new maplibregl.Map({
    container:host,
    style:QG_OSM_STYLE,
    center:[Number(lng),Number(lat)],
    zoom:Number(zoom),
    minZoom,maxZoom,
    maxPitch:navigation?60:0,
    interactive,
    attributionControl:false,
    dragRotate:!!navigation,
    pitchWithRotate:!!navigation
  });
  if(navigation){
    navState(map).follow=true;
    navState(map).navigationActive=true;
    try{map.touchZoomRotate.enableRotation()}catch(_){}
    const beginGesture=e=>{
      const original=e?.originalEvent;
      if(!original)return;
      const touches=Number(original.touches?.length||original.targetTouches?.length||0);
      const state=navState(map);
      // Before the first GPS fix there is nothing meaningful to free-pan away from.
      // Keep FOLLOW ownership so a startup/refresh rotate cannot strand the Rider off-screen.
      if(!state.lastFix){
        state.follow=true;
        state.gestureActive=true;
        return;
      }
      if(touches>=2){
        state.follow=true;
        state.gestureActive=true;
        return;
      }
      setNavigationFollow(map,false);
    };
    const keepGesture=e=>{
      if(!e?.originalEvent)return;
      const state=navState(map);
      // Pinch/rotate/pitch are temporary camera transforms, not a request to abandon
      // Rider follow. This also recovers from MapLibre emitting dragstart before rotatestart.
      state.follow=true;
      state.gestureActive=true;
    };
    const endGesture=e=>{
      if(!e?.originalEvent)return;
      const state=navState(map);
      if(!state.lastFix){
        state.gestureActive=false;
        state.follow=true;
        return;
      }
      if(state.follow)reanchorNavigationAfterGesture(map);
    };
    try{map.on('dragstart',beginGesture)}catch(_){}
    for(const event of ['zoomstart','rotatestart','pitchstart'])try{map.on(event,keepGesture)}catch(_){}
    for(const event of ['zoomend','rotateend','pitchend'])try{map.on(event,endGesture)}catch(_){}
  }else{
    try{map.touchZoomRotate.disableRotation()}catch(_){}
  }
  try{map.addControl(new maplibregl.NavigationControl({showCompass:!!navigation,showZoom:false,visualizePitch:!!navigation}),'top-left')}catch(_){}
  try{map.addControl(new maplibregl.AttributionControl({compact:true}),'bottom-right')}catch(_){}
  attachPlaces(map,{searchControl:interactive&&!navigation});
  return map;
}
function markerElement(color='#e6002d',html=''){
  const el=document.createElement('div');
  if(html){el.innerHTML=html;return el}
  Object.assign(el.style,{
    width:'24px',height:'24px',borderRadius:'50%',background:color,
    border:'4px solid #fff',boxShadow:'0 3px 12px rgba(0,0,0,.28)'
  });
  return el;
}
function marker(map,{lat,lng,color='#e6002d',title='',html='',anchor='center',rotation=0,rotationAlignment='auto',pitchAlignment='auto'}={}){
  if(!map||!valid(lat,lng)) return null;
  const el=markerElement(color,html);
  if(title)el.setAttribute('aria-label',title);
  const marker=new maplibregl.Marker({element:el,anchor,rotationAlignment,pitchAlignment})
    .setLngLat([Number(lng),Number(lat)]);
  if(navHasNumber(rotation))try{marker.setRotation(Number(rotation))}catch(_){}
  return marker.addTo(map);
}
function moveMarker(m,lat,lng,rotation=null){
  if(m&&valid(lat,lng))m.setLngLat([Number(lng),Number(lat)]);
  if(m&&rotation!==null&&rotation!==undefined&&navHasNumber(rotation))try{m.setRotation(Number(rotation))}catch(_){}
  return m
}
function moveMarkerSmooth(m,lat,lng,rotation=null,{duration=1080,onPosition=null}={}){
  if(!m||!valid(lat,lng))return m;
  const target={lat:Number(lat),lng:Number(lng)};
  let previous=null;
  try{previous=m.getLngLat?.()||null}catch(_){}
  const from={lat:Number(previous?.lat),lng:Number(previous?.lng)};
  const fromRotation=m.getRotation?.();
  const existing=markerAnimationStates.get(m);
  if(existing?.frame&&typeof cancelAnimationFrame==='function')cancelAnimationFrame(existing.frame);
  const ms=Math.max(240,Math.min(1400,Number(duration)||1080));
  if(!valid(from.lat,from.lng)||typeof requestAnimationFrame!=='function'){
    const result=moveMarker(m,target.lat,target.lng,rotation);onPosition?.(target);return result;
  }
  const start=(typeof performance!=='undefined'&&typeof performance.now==='function')?performance.now():Date.now();
  const state={frame:null};
  markerAnimationStates.set(m,state);
  const tick=now=>{
    const elapsed=Math.max(0,Number(now)-start);
    const t=Math.max(0,Math.min(1,elapsed/ms));
    const eased=navCameraEasing(t);
    const nextLat=from.lat+(target.lat-from.lat)*eased;
    const nextLng=from.lng+(target.lng-from.lng)*eased;
    try{m.setLngLat([nextLng,nextLat]);onPosition?.({lat:nextLat,lng:nextLng})}catch(_){}
    if(navHasNumber(rotation))try{m.setRotation(navHasNumber(fromRotation)?navNormalizeBearing(Number(fromRotation)+navShortestDelta(Number(fromRotation),Number(rotation))*eased):Number(rotation))}catch(_){}
    if(t<1&&markerAnimationStates.get(m)===state){
      state.frame=requestAnimationFrame(tick);
    }else if(markerAnimationStates.get(m)===state){
      markerAnimationStates.delete(m);
    }
  };
  state.frame=requestAnimationFrame(tick);
  return m;
}
function fit(map,points,{padding=48,maxZoom=16,duration=0}={}){
  if(!map)return;
  const rows=(points||[]).filter(p=>valid(p.lat,p.lng));
  if(!rows.length)return;
  if(rows.length===1){map.easeTo({center:[Number(rows[0].lng),Number(rows[0].lat)],zoom:maxZoom,duration});return}
  const bounds=new maplibregl.LngLatBounds();
  rows.forEach(p=>bounds.extend([Number(p.lng),Number(p.lat)]));
  map.fitBounds(bounds,{padding,maxZoom,duration});
}
function setCenter(map,lat,lng,zoom=16,duration=350){
  if(map&&valid(lat,lng))map.easeTo({center:[Number(lng),Number(lat)],zoom,duration});
}
// Each overlay owns its style listener. A pending tile/source is not a new style load.
const lineOwners=new WeakMap();
function addGeoJsonLine(map,id,coordinates,{color='#e6002d',width=6,opacity=1,arrows=false}={}){
  if(!map||!Array.isArray(coordinates)||coordinates.length<2)return false;
  let owners=lineOwners.get(map);if(!owners){owners=new Map();lineOwners.set(map,owners)}
  const old=owners.get(id);if(old)map.off?.('style.load',old.apply);
  const sourceId=id+'-source',layerId=id+'-layer',arrowId=id+'-arrows';
  const owner={apply:null};owners.set(id,owner);
  const data={type:'Feature',properties:{},geometry:{type:'LineString',coordinates}};
  const apply=()=>{
    if(owners.get(id)!==owner)return false;
    try{
      const source=map.getSource(sourceId);
      if(source)source.setData(data);else map.addSource(sourceId,{type:'geojson',data});
      if(!map.getLayer(layerId))map.addLayer({id:layerId,type:'line',source:sourceId,layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':color,'line-width':width,'line-opacity':opacity}});
      else{
        map.setPaintProperty?.(layerId,'line-color',color);
        map.setPaintProperty?.(layerId,'line-width',width);
        map.setPaintProperty?.(layerId,'line-opacity',opacity);
      }
      if(arrows&&!map.getLayer(arrowId)){
        const imageId='qg-route-direction-white';
        if(!map.hasImage?.(imageId)){
          const canvas=document.createElement('canvas');canvas.width=32;canvas.height=32;
          const ctx=canvas.getContext('2d');ctx.clearRect(0,0,32,32);
          ctx.strokeStyle='#fff';ctx.lineWidth=5;ctx.lineCap='round';ctx.lineJoin='round';
          ctx.beginPath();ctx.moveTo(10,6);ctx.lineTo(22,16);ctx.lineTo(10,26);ctx.stroke();
          map.addImage(imageId,ctx.getImageData(0,0,32,32),{pixelRatio:2});
        }
        map.addLayer({id:arrowId,type:'symbol',source:sourceId,layout:{'symbol-placement':'line','symbol-spacing':80,'icon-image':imageId,'icon-size':.85,'icon-rotation-alignment':'map','icon-pitch-alignment':'map','icon-keep-upright':false,'icon-allow-overlap':true,'icon-ignore-placement':true}});
      }
      return true;
    }catch(_){return false}
  };
  owner.apply=apply;map.on?.('style.load',apply);
  // Attempt now: isStyleLoaded() can be false merely because our casing source loads.
  // If the style itself is still absent, the owned style.load listener retries safely.
  apply();return true;
}
function removeGeoJsonLine(map,id){
  if(!map)return;
  const owner=lineOwners.get(map)?.get(id);if(owner)map.off?.('style.load',owner.apply);
  lineOwners.get(map)?.delete(id);
  for(const layerId of [id+'-arrows',id+'-layer'])try{if(map.getLayer(layerId))map.removeLayer(layerId)}catch(_){}
  try{if(map.getSource(id+'-source'))map.removeSource(id+'-source')}catch(_){}
}
function remainingNavigationRoute(route,point,{bearing=null,speed=null,accuracy=null}={}){
  if(!valid(point?.lat,point?.lng)||!navHasNumber(accuracy)||Number(accuracy)>35)return null;
  const decision=navCameraDecision({...point,bearing,speed,accuracy,route});
  const match=decision?.routeMatch;if(!match||decision.routeBearingRejected||match.offRouteMeters>12)return null;
  let walked=0;const part=match.part,remaining=[];
  remaining.push([match.snapped.lng,match.snapped.lat]);
  for(let i=0;i<part.length-1;i++){
    walked+=navDistanceMeters(part[i],part[i+1]);
    if(walked>match.progressMeters+.1)remaining.push([part[i+1].lng,part[i+1].lat]);
  }
  // The endpoint is presentation-only. Keep the complete route for match/reroute/ETA.
  const lines=remaining.length>1?[remaining]:[];
  for(const next of navRouteParts(route).slice(match.partIndex+1))lines.push(next.map(p=>[p.lng,p.lat]));
  return {position:match.snapped,lines,anchorFirst:remaining.length>1,match};
}
function locate(map,onPoint){
  if(!navigator.geolocation)return Promise.reject(new Error('เบราว์เซอร์ไม่รองรับ GPS'));
  return new Promise((resolve,reject)=>{
    navigator.geolocation.getCurrentPosition(pos=>{
      const lat=pos.coords.latitude,lng=pos.coords.longitude;
      if(!valid(lat,lng)){reject(new Error('พิกัด GPS ไม่ถูกต้อง'));return}
      setCenter(map,lat,lng,16,450);
      try{onPoint?.(lat,lng)}catch(_){}
      resolve({lat,lng});
    },err=>reject(err),{enableHighAccuracy:true,timeout:12000,maximumAge:30000});
  });
}
const QG_PLACE_CATALOG_URL=(()=>{try{return new URL('data/maps/buriram-places.json?v=20261019-1',document.currentScript.src).href}catch(_){return 'data/maps/buriram-places.json?v=20261019-1'}})();
const PLACE_LABELS={food:'ร้านอาหาร คาเฟ่',grocery:'ร้านของชำ ซูเปอร์มาร์เก็ต',laundry:'ร้านซักรีด',fuel:'ปั๊มน้ำมัน ชาร์จรถ',health:'โรงพยาบาล คลินิก ร้านยา',hotel:'โรงแรม ที่พัก',finance:'ธนาคาร ATM',education:'โรงเรียน',business:'ร้านค้า บริการ',place:'สถานที่'};
let placeCatalogPromise=null;
function placeQuery(value){return String(value||'').normalize('NFKC').replace(/[\u200B-\u200D\uFEFF]/g,'').trim().toLocaleLowerCase('th')}
async function loadPlaceCatalog(){
  if(!placeCatalogPromise)placeCatalogPromise=(async()=>{
    const r=await fetch(QG_PLACE_CATALOG_URL,{credentials:'omit',cache:'force-cache'});
    if(!r.ok)throw Error('Place catalog unavailable');
    const c=await r.json();
    if(c.schema_version!==1||c.licence!=='ODbL-1.0'||!Array.isArray(c.records))throw Error('Invalid place catalog');
    const ids=new Set();
    for(const p of c.records){
      if(!/^osm:(node|way):[0-9]+$/.test(p.id)||ids.has(p.id)||!p.name||!valid(p.lat,p.lng)||p.orderable!==false||p.verified!==false)throw Error('Invalid place record');
      ids.add(p.id);
    }
    return c;
  })().catch(e=>{placeCatalogPromise=null;throw e});
  return placeCatalogPromise;
}
function placeDistance(a,b){return navDistanceMeters({lat:Number(a.lat),lng:Number(a.lng)},{lat:Number(b.lat),lng:Number(b.lng)})}
async function searchPlaceCatalog(query,{limit=20,category=null,center=null,bounds=null,radius=null}={}){
  const c=await loadPlaceCatalog(),q=placeQuery(query);
  if(!q&&!center&&!bounds)return [];
  const matches=c.records.filter(p=>{
    if(category&&category!=='all'&&p.category!==category)return false;
    if(bounds&&!(p.lng>=bounds[0]&&p.lat>=bounds[1]&&p.lng<=bounds[2]&&p.lat<=bounds[3]))return false;
    if(radius!==null&&center&&placeDistance(center,p)>radius)return false;
    const key=placeQuery([p.name,...p.aliases,...Object.values(p.address||{}),p.kind,PLACE_LABELS[p.category]].join(' '));
    return !q||key.includes(q);
  });
  const rank=p=>!q?0:placeQuery(p.name)===q?0:placeQuery(p.name).startsWith(q)?1:2;
  return matches.sort((a,b)=>rank(a)-rank(b)||(center?placeDistance(center,a)-placeDistance(center,b):0)||a.name.localeCompare(b.name,'th')||a.id.localeCompare(b.id)).slice(0,Math.max(1,Math.min(100,limit)));
}
function placeResult(p){return {...p,place_id:p.id,lon:String(p.lng),lat:String(p.lat),display_name:[p.name,...Object.values(p.address||{})].filter(Boolean).join(', '),source:'OpenStreetMap',licence:'ODbL-1.0',provider:'queuego-catalog'}}
async function nearbyPlaces(category,lat,lng,radius=5000){
  const groups={food:'food',fuel:'fuel',hospital:'health',school:'education',grocery:'grocery',laundry:'laundry',hotel:'hotel',all:null};
  if(!(category in groups))return null;
  return (await searchPlaceCatalog('',{category:groups[category],center:{lat,lng},radius:Math.max(1,Math.min(20000,Number(radius)||5000)),limit:50})).map(p=>({...placeResult(p),distance:placeDistance({lat,lng},p)}));
}
function addPlaceSearchControl(map,{onSelect=null}={}){
  if(!map?.addControl)return null;
  let host,input,list,serial=0,disposed=false;
  const control={
    onAdd(){
      host=document.createElement('div');host.className='maplibregl-ctrl';
      host.style.cssText='background:#fff;border-radius:12px;padding:6px;max-width:min(300px,75vw);box-shadow:0 2px 8px #0002';
      const row=document.createElement('div');row.style.cssText='display:flex;gap:4px';
      input=document.createElement('input');input.type='search';input.placeholder='ค้นหาร้านหรือสถานที่';input.setAttribute('aria-label','ค้นหาร้านหรือสถานที่');
      input.style.cssText='font-size:16px;min-width:0;width:190px;border:1px solid #ddd;border-radius:8px;padding:8px';
      const button=document.createElement('button');button.type='button';button.textContent='ค้นหา';button.style.cssText='border:0;background:#e6002d;color:white;border-radius:8px;padding:8px';
      list=document.createElement('div');list.style.cssText='max-height:210px;overflow-y:auto';
      const run=async()=>{
        const ticket=++serial,q=input.value.trim();list.replaceChildren();if(q.length<2)return;
        const status=document.createElement('small');status.textContent='กำลังค้นหาสถานที่…';list.append(status);
        try{
          const rows=await searchPlaceCatalog(q,{limit:6,center:map.getCenter?.()});
          if(disposed||ticket!==serial)return;list.replaceChildren();
          if(!rows.length){status.textContent='ไม่พบในข้อมูลพื้นที่นี้ ลองชื่ออื่น';list.append(status)}
          for(const p of rows){
            const item=document.createElement('button');item.type='button';item.textContent=p.name;
            item.style.cssText='display:block;width:100%;text-align:left;border:0;border-top:1px solid #eee;background:white;padding:10px;font-size:14px';
            item.onclick=()=>{serial++;list.replaceChildren();input.value=p.name;map.flyTo({center:[p.lng,p.lat],zoom:17,duration:400});onSelect?.(p)};
            list.append(item);
          }
        }catch(_){if(!disposed&&ticket===serial)status.textContent='โหลดข้อมูลสถานที่ไม่ได้ กรุณาลองอีกครั้ง'}
      };
      button.onclick=run;
      input.addEventListener('input',()=>{serial++;list.replaceChildren()});
      input.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();e.stopPropagation();run()}});
      row.append(input,button);host.append(row,list);return host;
    },
    onRemove(){disposed=true;serial++;host?.remove()}
  };
  map.addControl(control,'top-right');return control;
}
function attachPlaces(map,{onSelect=null,searchControl=false}={}){
  if(!map||map.__qgPlaces)return map?.__qgPlaces||null;
  let disposed=false,catalog=null,labels=[],popup=null;
  const clearLabels=()=>{labels.forEach(m=>m.remove());labels=[]};
  const choose=p=>{popup?.remove();map.flyTo({center:[p.lng,p.lat],zoom:17,duration:400});onSelect?.(p)};
  const show=p=>{
    popup?.remove();
    const box=document.createElement('div'),name=document.createElement('b'),detail=document.createElement('p'),button=document.createElement('button'),credit=document.createElement('a');
    name.textContent=p.name;
    detail.textContent=[PLACE_LABELS[p.category],...Object.values(p.address||{}),p.phone||'',p.opening_hours?'เวลาที่ระบุ: '+p.opening_hours:'','ข้อมูลสถานที่ โปรดตรวจสอบกับร้าน'].filter(Boolean).join(' · ');
    button.type='button';button.textContent='ใช้ตำแหน่งนี้';button.onclick=()=>choose(p);
    credit.textContent='© OpenStreetMap contributors';credit.href=p.source_url;credit.target='_blank';credit.rel='noopener noreferrer';credit.style.cssText='display:block;font-size:10px;margin-top:6px';
    box.append(name,detail,button,credit);
    if(typeof maplibregl.Popup==='function')popup=new maplibregl.Popup({closeButton:true}).setLngLat([p.lng,p.lat]).setDOMContent(box).addTo(map);
    else choose(p);
  };
  const render=()=>{
    if(disposed||!catalog)return;
    try{
      const id='qg-public-places';
      if(!map.getSource(id))map.addSource(id,{type:'geojson',data:{type:'FeatureCollection',features:catalog.records.filter(p=>p.category!=='place').map(p=>({type:'Feature',geometry:{type:'Point',coordinates:[p.lng,p.lat]},properties:{id:p.id,name:p.name,category:p.category}}))},attribution:'© OpenStreetMap contributors'});
      if(!map.getLayer(id)){
        const before=map.getStyle?.()?.layers?.find(l=>l.id.startsWith('qg-rider-route-'))?.id;
        map.addLayer({id,type:'circle',source:id,minzoom:13,paint:{'circle-radius':4,'circle-color':'#465b70','circle-stroke-color':'#fff','circle-stroke-width':1.5}},before);
      }
      clearLabels();if((map.getZoom?.()||0)<15)return;
      const b=map.getBounds?.(),center=map.getCenter?.();if(!b||!center)return;
      const cells=new Set();
      catalog.records.filter(p=>p.category!=='place'&&b.contains([p.lng,p.lat])).sort((a,b)=>placeDistance(center,a)-placeDistance(center,b)||a.id.localeCompare(b.id)).filter(p=>{const pixel=map.project?.([p.lng,p.lat]);if(!pixel)return true;const cell=Math.floor(pixel.x/150)+':'+Math.floor(pixel.y/45);if(cells.has(cell))return false;cells.add(cell);return true}).slice(0,12).forEach(p=>{
        const el=document.createElement('button');el.type='button';el.textContent=p.name;
        el.style.cssText='max-width:145px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;border:1px solid #dbe0e5;border-radius:8px;background:#fffffff0;color:#263649;font:11px/16px sans-serif;padding:2px 5px';
        el.onclick=e=>{e.stopPropagation();show(p)};
        labels.push(new maplibregl.Marker({element:el,anchor:'bottom'}).setLngLat([p.lng,p.lat]).addTo(map));
      });
    }catch(_){/* Initial style may still be loading; the owned load listener retries. */}
  };
  const click=e=>{const id=e.features?.[0]?.properties?.id,p=catalog?.records.find(x=>x.id===id);if(p)show(p)};
  const dispose=()=>{if(disposed)return;disposed=true;clearLabels();popup?.remove();map.off?.('load',render);map.off?.('style.load',render);map.off?.('moveend',render);map.off?.('click','qg-public-places',click);map.off?.('remove',dispose);try{if(control&&map.hasControl?.(control))map.removeControl(control);if(map.getLayer('qg-public-places'))map.removeLayer('qg-public-places');if(map.getSource('qg-public-places'))map.removeSource('qg-public-places')}catch(_){};delete map.__qgPlaces;};
  const control=searchControl?addPlaceSearchControl(map,{onSelect}):null;
  map.on?.('load',render);map.on?.('style.load',render);map.on?.('moveend',render);map.on?.('click','qg-public-places',click);map.on?.('remove',dispose);
  loadPlaceCatalog().then(c=>{if(!disposed){catalog=c;render()}}).catch(()=>{});
  map.__qgPlaces={dispose,control};return map.__qgPlaces;
}

async function search(term){
  const q=String(term||'').trim();if(!q)return[];
  try{const local=await searchPlaceCatalog(q,{limit:8});if(local.length)return local.map(p=>({...p,label:placeResult(p).display_name}))}catch(_){}
  try{
    const r=await fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&countrycodes=th&q='+encodeURIComponent(q),{
      headers:{'Accept':'application/json'}
    });
    if(!r.ok)return[];
    const rows=await r.json();
    return (Array.isArray(rows)?rows:[]).map(x=>({lat:Number(x.lat),lng:Number(x.lon),label:String(x.display_name||'')})).filter(x=>valid(x.lat,x.lng));
  }catch(_){return[]}
}
async function reverse(lat,lng){
  if(!valid(lat,lng))return'';
  try{
    const r=await fetch('https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&addressdetails=1&lat='+encodeURIComponent(lat)+'&lon='+encodeURIComponent(lng),{
      headers:{'Accept':'application/json'}
    });
    if(!r.ok)return'';
    const row=await r.json();
    return String(row?.display_name||'').trim();
  }catch(_){return''}
}
function destroy(map){try{map?.remove?.()}catch(_){}}
window.QueueGoMapWeb={loadPlaceCatalog,searchPlaceCatalog,nearbyPlaces,attachPlaces,addPlaceSearchControl,style:QG_OSM_STYLE,valid,create,marker,moveMarker,moveMarkerSmooth,fit,setCenter,addGeoJsonLine,removeGeoJsonLine,locate,search,reverse,destroy,remainingNavigationRoute,navigationCameraDecision:navCameraDecision,navigationScreenAnchorRatio:navScreenAnchorRatio,followNavigationCamera,setNavigationFollow,setNavigationMode,navigationOverview,reanchorNavigationAfterZoom,reanchorNavigationAfterGesture};
})();