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

function create(host,{lat=15,lng=101,zoom=6,minZoom=5,maxZoom=19,interactive=true}={}){
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
    interactive,
    attributionControl:false,
    dragRotate:false,
    pitchWithRotate:false
  });
  try{map.touchZoomRotate.disableRotation()}catch(_){}
  try{map.addControl(new maplibregl.NavigationControl({showCompass:false,visualizePitch:false}),'top-left')}catch(_){}
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
function marker(map,{lat,lng,color='#e6002d',title='',html='',anchor='center'}={}){
  if(!map||!valid(lat,lng)) return null;
  const el=markerElement(color,html);
  if(title)el.setAttribute('aria-label',title);
  return new maplibregl.Marker({element:el,anchor})
    .setLngLat([Number(lng),Number(lat)])
    .addTo(map);
}
function moveMarker(m,lat,lng){if(m&&valid(lat,lng))m.setLngLat([Number(lng),Number(lat)]);return m}
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
  const apply=()=>{
    try{
      if(map.getLayer(layerId))map.removeLayer(layerId);
      if(map.getSource(sourceId))map.removeSource(sourceId);
      map.addSource(sourceId,{type:'geojson',data:{type:'Feature',properties:{},geometry:{type:'LineString',coordinates}}});
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
window.QueueGoMapWeb={style:QG_OSM_STYLE,valid,create,marker,moveMarker,fit,setCenter,addGeoJsonLine,removeGeoJsonLine,locate,search,reverse,destroy};
})();