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
const valid=(lat,lng)=>Number.isFinite(Number(lat))&&Number.isFinite(Number(lng))&&
  Number(lat)>=5&&Number(lat)<=21&&Number(lng)>=97&&Number(lng)<=106;

const navigationStates=new WeakMap();
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
function navMatchRoute(route,point){
  let best=null;
  for(const part of navRouteParts(route)){
    let walked=0,total=0;
    const lengths=[];
    for(let i=0;i<part.length-1;i++){const d=navDistanceMeters(part[i],part[i+1]);lengths.push(d);total+=d}
    for(let i=0;i<part.length-1;i++){
      const leg=lengths[i];if(leg<=.01){walked+=leg;continue}
      const p=navProject(point,part[i],part[i+1]);
      if(!best||p.distance<best.offRouteMeters){
        best={part,snapped:p.point,offRouteMeters:p.distance,progressMeters:walked+leg*p.fraction,geometryDistanceMeters:total,segmentBearingDegrees:navBearing(part[i],part[i+1])};
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
  if(!Number.isFinite(Number(target)))return Number.isFinite(Number(previous))?navNormalizeBearing(previous):null;
  const t=navNormalizeBearing(target);if(!Number.isFinite(Number(previous)))return t;
  const p=navNormalizeBearing(previous),delta=navShortestDelta(p,t);
  if(Math.abs(delta)<.45)return p;
  return navNormalizeBearing(p+delta*Math.max(.05,Math.min(1,Number(factor)||.35)));
}
const navCameraEasing=t=>t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;
function navRotationDuration(currentBearing,targetBearing,cadenceMs,fallback=420){
  if(!Number.isFinite(Number(currentBearing))||!Number.isFinite(Number(targetBearing)))return Math.max(260,Math.min(760,Number(fallback)||420));
  const angle=Math.abs(navShortestDelta(currentBearing,targetBearing));
  const cadence=Number.isFinite(Number(cadenceMs))&&Number(cadenceMs)>0?Number(cadenceMs):Number(fallback)||420;
  const cadenceDuration=Math.max(260,Math.min(720,Math.round(cadence*.82)));
  const angleDuration=Math.max(300,Math.min(900,Math.round(260+angle*9)));
  return Math.max(cadenceDuration,angleDuration);
}
function navCameraDecision({lat,lng,speed=null,bearing=null,accuracy=null,route=[],previousBearing=null,baseZoom=17}={}){
  if(!valid(lat,lng))return null;
  const point={lat:Number(lat),lng:Number(lng)},spd=Number.isFinite(Number(speed))&&Number(speed)>=0?Number(speed):null;
  const acc=Number.isFinite(Number(accuracy))?Number(accuracy):null;
  const match=navMatchRoute(route,point),reliable=match&&match.offRouteMeters<=60?match:null;
  let routeBearing=reliable?.segmentBearingDegrees??null;
  const remaining=match?Math.max(0,match.geometryDistanceMeters-match.progressMeters):null;
  if(reliable){
    const desired=spd==null?24:spd>=18?70:spd>=11?55:spd>=5?38:spd>=2?26:14;
    const ahead=remaining==null?desired:Math.min(desired,Math.max(0,remaining*.45));
    const future=navPointAt(reliable.part,Math.min(reliable.geometryDistanceMeters,reliable.progressMeters+ahead));
    if(future&&navDistanceMeters(reliable.snapped,future)>=5){
      const futureBearing=navBearing(reliable.snapped,future),delta=Math.max(-70,Math.min(70,navShortestDelta(routeBearing,futureBearing)));
      const bounded=navNormalizeBearing(routeBearing+delta);
      const blend=spd==null?.30:spd>=18?.46:spd>=11?.44:spd>=5?.40:spd>=2?.30:.18;
      routeBearing=navSmoothBearing(routeBearing,bounded,blend);
    }
  }
  const gpsBearing=Number.isFinite(Number(bearing))?navNormalizeBearing(Number(bearing)):null;
  const desiredBearing=routeBearing!=null?routeBearing:(spd==null||spd<.83?previousBearing:(gpsBearing??previousBearing));
  const smoothing=spd==null?.28:spd>=18?.46:spd>=11?.42:spd>=5?.36:spd>=2?.28:.20;
  const cameraBearing=navSmoothBearing(previousBearing,desiredBearing,smoothing)??0;
  let zoom=remaining!=null&&remaining<=60?19:remaining!=null&&remaining<=180?18:spd==null?baseZoom:spd>=18?15:spd>=11?16:spd>=5?17:spd<=1.5?Math.max(baseZoom,18):baseZoom;
  if(acc!=null&&acc>60)zoom=Math.max(14,zoom-1);
  zoom=Math.max(14,Math.min(20,zoom));
  const pitch=Math.max(0,Math.min(55,acc!=null&&acc>80?24:remaining!=null&&remaining<=60?28:remaining!=null&&remaining<=180?34:spd==null?38:spd>=18?52:spd>=11?49:spd>=5?45:spd>=2?40:34));
  const look=spd==null?55:spd>=18?150:spd>=11?120:spd>=5?85:spd>=2?55:30;
  const lookAhead=remaining==null?look:Math.min(look,Math.max(0,remaining*.55));
  const center=reliable?(navPointAt(reliable.part,Math.min(reliable.geometryDistanceMeters,reliable.progressMeters+lookAhead))||point):point;
  return {center,bearing:cameraBearing,pitch,zoom,routeMatch:match,remainingRouteMeters:remaining};
}
function navState(map){
  let state=navigationStates.get(map);
  if(!state){
    state={
      follow:true,navigationActive:true,bearing:null,lastFix:null,lastRoute:[],baseZoom:17,lastDecision:null,lastAppliedAt:0,
      manualZoom:null,manualZoomUntil:0,reanchorTimer:null,gestureActive:false
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
  state.bearing=0;
  state.lastDecision=null;
  state.lastDecisionApplied=null;
  try{map.dragRotate?.disable?.()}catch(_){}
  try{map.touchZoomRotate?.disableRotation?.()}catch(_){}
  try{map.touchPitch?.disable?.()}catch(_){}
  try{map.setMaxPitch?.(0)}catch(_){}
  const camera={bearing:0,pitch:0,duration:Math.max(0,Number(duration)||0),easing:navCameraEasing,essential:true};
  if(center&&valid(center.lat,center.lng))camera.center=[Number(center.lng),Number(center.lat)];
  if(Number.isFinite(Number(zoom)))camera.zoom=Number(zoom);
  try{map.easeTo(camera)}catch(_){return false}
  return false;
}
function followNavigationCamera(map,fix={},route=[],{force=false,duration=420,baseZoom=17,preserveZoom=false}={}){
  if(!map)return null;
  const state=navState(map);
  state.lastFix={...fix};
  state.lastRoute=Array.isArray(route)?route:[];
  state.baseZoom=baseZoom;
  if(force){
    state.follow=true;
    state.gestureActive=false;
    state.manualZoom=null;
    state.manualZoomUntil=0;
  }
  if(!state.navigationActive||!state.follow)return null;
  const renderedBearing=Number(map.getBearing?.());
  const previousBearing=Number.isFinite(renderedBearing)?renderedBearing:state.bearing;
  const decision=navCameraDecision({...fix,route,previousBearing,baseZoom});if(!decision)return null;
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
  const target={...decision,zoom:targetZoom};
  state.bearing=target.bearing;
  state.lastDecision=target;
  if(state.gestureActive&&!force)return target;

  const previous=state.lastDecisionApplied;
  if(!force&&previous){
    const centerDelta=navDistanceMeters(previous.center,target.center);
    const bearingDelta=Math.abs(navShortestDelta(previous.bearing,target.bearing));
    const pitchDelta=Math.abs(Number(previous.pitch)-Number(target.pitch));
    const zoomDelta=Math.abs(Number(previous.zoom)-Number(target.zoom));
    if(centerDelta<.8&&bearingDelta<.7&&pitchDelta<.5&&zoomDelta<.05)return target;
  }
  const cadence=state.lastAppliedAt?Math.max(0,now-state.lastAppliedAt):duration;
  const smoothDuration=force
    ? Math.max(320,Math.min(560,Number(duration)||420))
    : navRotationDuration(previousBearing,target.bearing,cadence,duration);
  try{
    map.easeTo({
      center:[target.center.lng,target.center.lat],
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
      preserveZoom:true
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
      if(touches>=2){
        state.gestureActive=true;
        return;
      }
      setNavigationFollow(map,false);
    };
    const keepGesture=e=>{if(e?.originalEvent&&navState(map).follow)navState(map).gestureActive=true};
    const endGesture=e=>{if(e?.originalEvent&&navState(map).follow)reanchorNavigationAfterGesture(map)};
    try{map.on('dragstart',beginGesture)}catch(_){}
    for(const event of ['zoomstart','rotatestart','pitchstart'])try{map.on(event,keepGesture)}catch(_){}
    for(const event of ['zoomend','rotateend','pitchend'])try{map.on(event,endGesture)}catch(_){}
  }else{
    try{map.touchZoomRotate.disableRotation()}catch(_){}
  }
  try{map.addControl(new maplibregl.NavigationControl({showCompass:!!navigation,showZoom:false,visualizePitch:!!navigation}),'top-left')}catch(_){}
  try{map.addControl(new maplibregl.AttributionControl({compact:true}),'bottom-right')}catch(_){}
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
  if(Number.isFinite(Number(rotation)))try{marker.setRotation(Number(rotation))}catch(_){}
  return marker.addTo(map);
}
function moveMarker(m,lat,lng,rotation=null){
  if(m&&valid(lat,lng))m.setLngLat([Number(lng),Number(lat)]);
  if(m&&rotation!==null&&rotation!==undefined&&Number.isFinite(Number(rotation)))try{m.setRotation(Number(rotation))}catch(_){}
  return m
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
function addGeoJsonLine(map,id,coordinates,{color='#e6002d',width=6,opacity=1}={}){
  if(!map||!Array.isArray(coordinates)||coordinates.length<2)return false;
  const sourceId=id+'-source',layerId=id+'-layer';
  const data={type:'Feature',properties:{},geometry:{type:'LineString',coordinates}};
  const apply=()=>{
    try{
      const existing=map.getSource(sourceId);
      if(existing&&typeof existing.setData==='function'){
        existing.setData(data);
        if(map.getLayer(layerId)){
          try{map.setPaintProperty?.(layerId,'line-color',color)}catch(_){}
          try{map.setPaintProperty?.(layerId,'line-width',width)}catch(_){}
          try{map.setPaintProperty?.(layerId,'line-opacity',opacity)}catch(_){}
          return true;
        }
      }
      if(map.getLayer(layerId))map.removeLayer(layerId);
      if(map.getSource(sourceId))map.removeSource(sourceId);
      map.addSource(sourceId,{type:'geojson',data});
      map.addLayer({id:layerId,type:'line',source:sourceId,layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':color,'line-width':width,'line-opacity':opacity}});
      return true;
    }catch(_){return false}
  };
  if(map.isStyleLoaded())return apply();
  map.once('load',apply);return true;
}
function removeGeoJsonLine(map,id){
  if(!map)return;
  const sourceId=id+'-source',layerId=id+'-layer';
  try{if(map.getLayer(layerId))map.removeLayer(layerId)}catch(_){}
  try{if(map.getSource(sourceId))map.removeSource(sourceId)}catch(_){}
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
async function search(term){
  const q=String(term||'').trim();if(!q)return[];
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
window.QueueGoMapWeb={style:QG_OSM_STYLE,valid,create,marker,moveMarker,fit,setCenter,addGeoJsonLine,removeGeoJsonLine,locate,search,reverse,destroy,navigationCameraDecision:navCameraDecision,followNavigationCamera,setNavigationFollow,setNavigationMode,navigationOverview,reanchorNavigationAfterZoom,reanchorNavigationAfterGesture};
})();