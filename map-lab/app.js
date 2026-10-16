(function(){
"use strict";

const BURIRAM_BOUNDS=[[102.442357,14.132135],[103.499967,15.789217]];
const BURIRAM_CENTER=[103.1029,14.9930];
const BBOX="14.132135,102.442357,15.789217,103.499967";
const OVERPASS_ENDPOINTS=[
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter"
];
const OSRM_ENDPOINT="https://router.project-osrm.org/route/v1/driving/";
const NOMINATIM_ENDPOINT="https://nominatim.openstreetmap.org/search";
const DRR_SERVICE="https://gis.drr.go.th/arcgis/rest/services/DRR_Feature/FeatureServer";
const NGIS_HIGHWAY_SERVICE="https://ngis.go.th/arcgis/rest/services/Hosted/%E0%B8%97%E0%B8%B2%E0%B8%87%E0%B8%AB%E0%B8%A5%E0%B8%A7%E0%B8%87%E0%B9%81%E0%B8%9C%E0%B9%88%E0%B8%99%E0%B8%94%E0%B8%B4%E0%B8%99/FeatureServer/0";

const state={
  map:null,
  signals:emptyCollection(),
  safety:emptyCollection(),
  pois:emptyCollection(),
  ngis:emptyCollection(),
  drr:emptyCollection(),
  start:null,
  end:null,
  route:null,
  picking:"start",
  activeControllers:new Set(),
  sources:{
    osm:{name:"OpenStreetMap / Overpass",state:"loading",note:"ไฟจราจรและจุดควรระวัง · ODbL"},
    ngis:{name:"NGIS / ทางหลวงแผ่นดิน",state:"idle",note:"ArcGIS Feature Service · ชั้นตรวจสอบทางหลวง"},
    drr:{name:"กรมทางหลวงชนบท (DRR)",state:"idle",note:"FeatureServer / Open Data Common"},
    route:{name:"Routing prototype",state:"idle",note:"OSRM public demo · ใช้เพื่อทดสอบเท่านั้น"},
    qg:{name:"QueueGo observations",state:"idle",note:"พร้อมต่อ telemetry / rider reports ในระยะถัดไป"}
  }
};

function emptyCollection(){return {type:"FeatureCollection",features:[]}}
function el(id){return document.getElementById(id)}
function fmt(n,d){return Number(n||0).toLocaleString("th-TH",{maximumFractionDigits:d||0})}
function validCoord(lon,lat){return Number.isFinite(Number(lon))&&Number.isFinite(Number(lat))&&Math.abs(Number(lon))<=180&&Math.abs(Number(lat))<=90}
function escapeHtml(v){return String(v==null?"":v).replace(/[&<>"']/g,function(c){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]})}
function setSource(key,next,note){
  if(!state.sources[key])return;
  state.sources[key].state=next;
  if(note)state.sources[key].note=note;
  renderLedger();
}
function renderLedger(){
  const host=el("sourceLedger");
  if(!host)return;
  host.innerHTML=Object.keys(state.sources).map(function(key){
    const s=state.sources[key];
    const label=s.state==="ok"?"พร้อม":s.state==="loading"?"กำลังโหลด":s.state==="error"?"ไม่พร้อม":"รอ";
    return '<div class="qgm-source" data-state="'+escapeHtml(s.state)+'"><i></i><div><b>'+escapeHtml(s.name)+'</b><small>'+escapeHtml(s.note)+'</small></div><em>'+label+'</em></div>';
  }).join("");
  const ok=Object.values(state.sources).filter(function(s){return s.state==="ok"}).length;
  el("sourceSummary").textContent=ok+" แหล่งพร้อม";
}
function fetchTimeout(url,options,ms){
  const controller=new AbortController();
  state.activeControllers.add(controller);
  const timer=setTimeout(function(){controller.abort()},ms||20000);
  const opts=Object.assign({},options||{},{signal:controller.signal});
  return fetch(url,opts).finally(function(){clearTimeout(timer);state.activeControllers.delete(controller)});
}
function abortActive(){state.activeControllers.forEach(function(c){try{c.abort()}catch(_){}});state.activeControllers.clear()}
function baseStyle(){
  return {
    version:8,
    sources:{
      osm:{
        type:"raster",
        tiles:["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
        tileSize:256,
        attribution:"© OpenStreetMap contributors"
      }
    },
    layers:[
      {id:"background",type:"background",paint:{"background-color":"#eeeae8"}},
      {id:"osm",type:"raster",source:"osm",paint:{"raster-saturation":-0.38,"raster-contrast":0.04,"raster-brightness-max":0.96}}
    ]
  };
}
function initMap(){
  if(!window.maplibregl){
    el("map").innerHTML='<div style="padding:100px 20px;text-align:center">MapLibre โหลดไม่สำเร็จ</div>';
    return;
  }
  const map=new maplibregl.Map({
    container:"map",
    style:baseStyle(),
    center:BURIRAM_CENTER,
    zoom:8.4,
    minZoom:7,
    maxZoom:19,
    maxBounds:[[101.95,13.7],[104.0,16.2]]
  });
  state.map=map;
  map.addControl(new maplibregl.NavigationControl({showCompass:true,visualizePitch:true}),"bottom-right");
  map.on("load",function(){
    addDataLayers();
    map.fitBounds(BURIRAM_BOUNDS,{padding:{top:90,bottom:180,left:370,right:60},duration:0});
    loadCoreData();
    if(el("toggleNgis")&&el("toggleNgis").checked)loadNgis();
    if(el("toggleDrr")&&el("toggleDrr").checked)loadDrr();
  });
  map.on("click",handleMapClick);
}
function addDataLayers(){
  const map=state.map;
  map.addSource("signals",{type:"geojson",data:state.signals});
  map.addLayer({id:"signals-halo",type:"circle",source:"signals",paint:{"circle-radius":9,"circle-color":"#ffffff","circle-opacity":0.94}});
  map.addLayer({id:"signals",type:"circle",source:"signals",paint:{"circle-radius":6,"circle-color":"#e6002d","circle-stroke-width":1.5,"circle-stroke-color":"#6f0016"}});
  map.addSource("safety",{type:"geojson",data:state.safety});
  map.addLayer({id:"safety",type:"circle",source:"safety",paint:{"circle-radius":5,"circle-color":["match",["get","kind"],"level_crossing","#f59e0b","speed_camera","#7c3aed","traffic_calming","#0284c7","#111827"],"circle-stroke-width":1.5,"circle-stroke-color":"#fff"}});
  map.addSource("pois",{type:"geojson",data:state.pois});
  map.addLayer({id:"pois",type:"circle",source:"pois",layout:{visibility:"none"},paint:{"circle-radius":5,"circle-color":["match",["get","kind"],"hospital","#e6002d","school","#2563eb","#64748b"],"circle-stroke-width":1.5,"circle-stroke-color":"#fff"}});
  map.addSource("ngis",{type:"geojson",data:state.ngis});
  map.addLayer({id:"ngis",type:"line",source:"ngis",paint:{"line-color":"#334155","line-opacity":0.60,"line-width":["interpolate",["linear"],["zoom"],8,1.2,14,4.5]}});
  map.addSource("drr",{type:"geojson",data:state.drr});
  map.addLayer({id:"drr",type:"line",source:"drr",paint:{"line-color":"#e6002d","line-opacity":0.50,"line-width":["interpolate",["linear"],["zoom"],8,1,14,4]}});
  map.addSource("route",{type:"geojson",data:emptyCollection()});
  map.addLayer({id:"route-casing",type:"line",source:"route",paint:{"line-color":"#fff","line-width":["interpolate",["linear"],["zoom"],8,6,16,12],"line-opacity":0.95}});
  map.addLayer({id:"route",type:"line",source:"route",paint:{"line-color":"#e6002d","line-width":["interpolate",["linear"],["zoom"],8,3.5,16,7],"line-opacity":0.96}});
  map.addSource("route-points",{type:"geojson",data:emptyCollection()});
  map.addLayer({id:"route-points",type:"circle",source:"route-points",paint:{"circle-radius":8,"circle-color":["match",["get","role"],"start","#16a34a","#e6002d"],"circle-stroke-width":3,"circle-stroke-color":"#fff"}});
  ["signals","safety","pois","ngis","drr"].forEach(function(layer){
    map.on("click",layer,function(e){if(e.features&&e.features[0])showFeature(e.features[0])});
    map.on("mouseenter",layer,function(){map.getCanvas().style.cursor="pointer"});
    map.on("mouseleave",layer,function(){map.getCanvas().style.cursor=""});
  });
}
function overpassQuery(kind){
  if(kind==="core"){
    return "[out:json][timeout:50];("+[
      'node["highway"="traffic_signals"]('+BBOX+');',
      'node["highway"="stop"]('+BBOX+');',
      'node["highway"="give_way"]('+BBOX+');',
      'node["railway"="level_crossing"]('+BBOX+');',
      'node["traffic_calming"]('+BBOX+');',
      'node["highway"="speed_camera"]('+BBOX+');'
    ].join("")+");out body;";
  }
  return "[out:json][timeout:50];("+[
    'nwr["amenity"="hospital"]('+BBOX+');',
    'nwr["amenity"="school"]('+BBOX+');'
  ].join("")+");out center tags;";
}
async function callOverpass(query){
  let lastError=null;
  for(const endpoint of OVERPASS_ENDPOINTS){
    try{
      const res=await fetchTimeout(endpoint,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:"data="+encodeURIComponent(query)},55000);
      if(!res.ok)throw Error("Overpass HTTP "+res.status);
      return await res.json();
    }catch(err){lastError=err}
  }
  throw lastError||Error("Overpass unavailable");
}
function osmToFeature(item,kind){
  const lon=Number(item.lon!=null?item.lon:item.center&&item.center.lon);
  const lat=Number(item.lat!=null?item.lat:item.center&&item.center.lat);
  if(!validCoord(lon,lat))return null;
  const tags=item.tags||{};
  let mapped=kind;
  if(tags.highway==="traffic_signals")mapped="traffic_signal";
  else if(tags.highway==="stop")mapped="stop";
  else if(tags.highway==="give_way")mapped="give_way";
  else if(tags.railway==="level_crossing")mapped="level_crossing";
  else if(tags.highway==="speed_camera")mapped="speed_camera";
  else if(tags.traffic_calming)mapped="traffic_calming";
  else if(tags.amenity==="hospital")mapped="hospital";
  else if(tags.amenity==="school")mapped="school";
  return {
    type:"Feature",
    geometry:{type:"Point",coordinates:[lon,lat]},
    properties:Object.assign({
      source:"OpenStreetMap",
      osm_type:item.type||"",
      osm_id:item.id||"",
      kind:mapped,
      name:tags.name||tags["name:th"]||tags["name:en"]||"",
      signal_timing:"unknown"
    },tags)
  };
}
async function loadCoreData(){
  setSource("osm","loading","กำลังโหลดไฟจราจรและจุดควรระวังทั้งจังหวัดบุรีรัมย์");
  try{
    const json=await callOverpass(overpassQuery("core"));
    const signals=[],safety=[];
    (json.elements||[]).forEach(function(item){
      const f=osmToFeature(item,"safety");
      if(!f)return;
      if(f.properties.kind==="traffic_signal")signals.push(f);else safety.push(f);
    });
    state.signals={type:"FeatureCollection",features:signals};
    state.safety={type:"FeatureCollection",features:safety};
    updateSource("signals",state.signals);
    updateSource("safety",state.safety);
    el("countSignals").textContent=fmt(signals.length);
    el("countSafety").textContent=fmt(safety.length);
    setSource("osm","ok",fmt(signals.length)+" ไฟจราจร · "+fmt(safety.length)+" จุดควรระวังจาก OSM");
    auditRoute();
  }catch(err){
    console.warn("core map data",err);
    setSource("osm","error","โหลด Overpass ไม่สำเร็จ · แผนที่พื้นฐานยังใช้งานได้");
  }
}
async function loadPois(){
  if(state.pois.features.length){return}
  setSource("osm","loading","กำลังโหลดโรงเรียนและโรงพยาบาล");
  try{
    const json=await callOverpass(overpassQuery("pois"));
    state.pois={type:"FeatureCollection",features:(json.elements||[]).map(function(x){return osmToFeature(x,"poi")}).filter(Boolean)};
    updateSource("pois",state.pois);
    el("countPois").textContent=fmt(state.pois.features.length);
    setSource("osm","ok",fmt(state.signals.features.length)+" ไฟจราจร · "+fmt(state.safety.features.length)+" จุดควรระวัง · "+fmt(state.pois.features.length)+" สถานที่อ่อนไหว");
  }catch(err){
    console.warn("poi map data",err);
    el("countPois").textContent="!";
    setSource("osm","error","โหลดสถานที่อ่อนไหวไม่สำเร็จ");
  }
}
function esriGeometryToGeoJSON(geometry){
  if(!geometry)return null;
  if(Array.isArray(geometry.paths)){
    return geometry.paths.length===1
      ? {type:"LineString",coordinates:geometry.paths[0]}
      : {type:"MultiLineString",coordinates:geometry.paths};
  }
  if(Array.isArray(geometry.rings))return {type:"Polygon",coordinates:geometry.rings};
  if(validCoord(geometry.x,geometry.y))return {type:"Point",coordinates:[Number(geometry.x),Number(geometry.y)]};
  return null;
}
async function loadNgis(){
  if(state.ngis.features.length)return;
  setSource("ngis","loading","กำลังโหลดทางหลวงแผ่นดินในขอบเขตบุรีรัมย์");
  try{
    const envelope="102.442357,14.132135,103.499967,15.789217";
    const query=NGIS_HIGHWAY_SERVICE+"/query?where=1%3D1&geometry="+encodeURIComponent(envelope)+"&geometryType=esriGeometryEnvelope&inSR=4326&spatialRel=esriSpatialRelIntersects&outFields=*&outSR=4326&returnGeometry=true&resultRecordCount=2000&f=json";
    const res=await fetchTimeout(query,{},25000);
    if(!res.ok)throw Error("NGIS HTTP "+res.status);
    const json=await res.json();
    if(json.error)throw Error(json.error.message||"NGIS ArcGIS error");
    const features=(json.features||[]).map(function(row,i){
      const geometry=esriGeometryToGeoJSON(row.geometry);
      if(!geometry)return null;
      return {type:"Feature",geometry:geometry,properties:Object.assign({
        source:"NGIS / กระทรวงคมนาคม",
        source_layer:"ทางหลวงแผ่นดิน",
        source_row:i
      },row.attributes||{})};
    }).filter(Boolean);
    if(!features.length)throw Error("NGIS ไม่มีข้อมูลที่อ่านได้ในขณะนี้");
    state.ngis={type:"FeatureCollection",features:features};
    updateSource("ngis",state.ngis);
    el("countNgis").textContent=fmt(features.length);
    setSource("ngis","ok",fmt(features.length)+" รายการทางหลวงแผ่นดินในบุรีรัมย์");
  }catch(err){
    console.warn("NGIS",err);
    el("countNgis").textContent="!";
    setSource("ngis","error","ปลายทาง NGIS ไม่ตอบสนองหรือ CORS ไม่พร้อม · แยกความล้มเหลวจากแผนที่หลัก");
  }
}
async function loadDrr(){
  if(state.drr.features.length){return}
  setSource("drr","loading","กำลังตรวจ FeatureServer และชั้นโครงข่ายถนน");
  try{
    const metaRes=await fetchTimeout(DRR_SERVICE+"?f=pjson",{},18000);
    if(!metaRes.ok)throw Error("DRR metadata HTTP "+metaRes.status);
    const meta=await metaRes.json();
    const layers=(meta.layers||[]).filter(function(x){return /road|route|สายทาง|ทางหลวง|drr/i.test(String(x.name||""))});
    if(!layers.length)throw Error("ไม่พบชั้นถนนใน FeatureServer");
    const features=[];
    for(const layer of layers.slice(0,3)){
      const envelope="102.442357,14.132135,103.499967,15.789217";
      const query=DRR_SERVICE+"/"+encodeURIComponent(layer.id)+"/query?where=1%3D1&geometry="+encodeURIComponent(envelope)+"&geometryType=esriGeometryEnvelope&inSR=4326&spatialRel=esriSpatialRelIntersects&outFields=*&outSR=4326&returnGeometry=true&f=geojson";
      try{
        const r=await fetchTimeout(query,{},25000);
        if(!r.ok)continue;
        const gj=await r.json();
        (gj.features||[]).forEach(function(f){
          f.properties=Object.assign({source:"กรมทางหลวงชนบท",source_layer:layer.name},f.properties||{});
          features.push(f);
        });
      }catch(_){}
    }
    if(!features.length)throw Error("DRR ไม่มีข้อมูลที่อ่านได้ในขณะนี้");
    state.drr={type:"FeatureCollection",features:features};
    updateSource("drr",state.drr);
    el("countDrr").textContent=fmt(features.length);
    setSource("drr","ok",fmt(features.length)+" รายการในขอบเขตบุรีรัมย์");
  }catch(err){
    console.warn("DRR",err);
    el("countDrr").textContent="!";
    setSource("drr","error","ปลายทาง DRR ไม่ตอบสนองหรือ CORS ไม่พร้อม · ไม่ทำให้แผนที่หลักล้ม");
  }
}
function updateSource(name,data){
  const src=state.map&&state.map.getSource(name);
  if(src&&src.setData)src.setData(data);
}
function setVisibility(layer,visible){
  if(state.map&&state.map.getLayer(layer))state.map.setLayoutProperty(layer,"visibility",visible?"visible":"none");
}
function handleMapClick(e){
  const hit=state.map&&e.point?state.map.queryRenderedFeatures(e.point,{layers:["signals","safety","pois","ngis","drr"]}):[];
  if((e.features&&e.features.length)||(hit&&hit.length))return;
  if(!validCoord(e.lngLat.lng,e.lngLat.lat))return;
  if(state.picking==="start"||!state.start){
    state.start={lon:e.lngLat.lng,lat:e.lngLat.lat};
    state.picking="end";
  }else{
    state.end={lon:e.lngLat.lng,lat:e.lngLat.lat};
    state.picking="start";
  }
  syncRoutePoints();
  if(state.start&&state.end)calculateRoute();
}
function syncRoutePoints(){
  const features=[];
  [["start",state.start],["end",state.end]].forEach(function(row){
    const role=row[0],p=row[1];
    const button=el(role==="start"?"startPoint":"endPoint");
    const label=button.querySelector("b");
    if(p){
      label.textContent=p.lat.toFixed(5)+", "+p.lon.toFixed(5);
      features.push({type:"Feature",geometry:{type:"Point",coordinates:[p.lon,p.lat]},properties:{role:role}});
    }else label.textContent="ยังไม่ได้เลือก";
  });
  updateSource("route-points",{type:"FeatureCollection",features:features});
  el("routeInstruction").textContent=state.picking==="start"?"แตะแผนที่เพื่อเลือกจุดเริ่มต้น":"แตะแผนที่เพื่อเลือกปลายทาง";
}
async function calculateRoute(){
  const a=state.start,b=state.end;
  if(!a||!b)return;
  setSource("route","loading","กำลังคำนวณเส้นทางต้นแบบ");
  el("routeInstruction").textContent="กำลังคำนวณเส้นทาง…";
  try{
    const url=OSRM_ENDPOINT+a.lon+","+a.lat+";"+b.lon+","+b.lat+"?overview=full&geometries=geojson&steps=true&alternatives=false";
    const res=await fetchTimeout(url,{},18000);
    if(!res.ok)throw Error("routing HTTP "+res.status);
    const json=await res.json();
    const route=json.routes&&json.routes[0];
    if(!route||!route.geometry)throw Error("route missing");
    state.route={
      type:"Feature",
      geometry:route.geometry,
      properties:{distance:route.distance,duration:route.duration,source:"OSRM public demo"}
    };
    updateSource("route",{type:"FeatureCollection",features:[state.route]});
    el("routeDistance").textContent=fmt(route.distance/1000,1)+" กม.";
    el("routeDuration").textContent=Math.max(1,Math.round(route.duration/60))+" นาที";
    el("routeStats").hidden=false;
    setSource("route","ok","เส้นทางต้นแบบพร้อม · จะไม่ใช้ public demo เป็น production routing");
    el("routeInstruction").textContent="ตรวจเส้นทางและจุดควรระวัง";
    auditRoute();
    try{state.map.fitBounds(route.geometry.coordinates.reduce(function(bounds,c){return bounds.extend(c)},new maplibregl.LngLatBounds()),{padding:{top:100,bottom:240,left:80,right:80},duration:500})}catch(_){}
  }catch(err){
    console.warn("route",err);
    state.route=null;
    updateSource("route",emptyCollection());
    el("routeInstruction").textContent="คำนวณเส้นทางไม่สำเร็จ";
    setSource("route","error","routing demo ไม่พร้อม · ไม่มีการวาดเส้นตรงปลอมแทน");
  }
}
function toXY(coord,lat0){
  const x=coord[0]*111320*Math.cos(lat0*Math.PI/180);
  const y=coord[1]*110540;
  return [x,y];
}
function segmentDistanceMeters(point,a,b){
  const lat0=(point[1]+a[1]+b[1])/3;
  const p=toXY(point,lat0),v=toXY(a,lat0),w=toXY(b,lat0);
  const dx=w[0]-v[0],dy=w[1]-v[1];
  const len=dx*dx+dy*dy;
  let t=len?((p[0]-v[0])*dx+(p[1]-v[1])*dy)/len:0;
  t=Math.max(0,Math.min(1,t));
  const x=v[0]+t*dx,y=v[1]+t*dy;
  return Math.hypot(p[0]-x,p[1]-y);
}
function distancePointToLine(point,line){
  let best=Infinity;
  for(let i=1;i<line.length;i++)best=Math.min(best,segmentDistanceMeters(point,line[i-1],line[i]));
  return best;
}
function auditRoute(){
  if(!state.route)return;
  const line=state.route.geometry.coordinates||[];
  const near=function(collection,meters){
    return collection.features.filter(function(f){return f.geometry&&f.geometry.type==="Point"&&distancePointToLine(f.geometry.coordinates,line)<=meters});
  };
  const sig=near(state.signals,45);
  const haz=near(state.safety,45);
  el("routeSignals").textContent=fmt(sig.length);
  el("routeHazards").textContent=fmt(haz.length);
}
function showFeature(feature){
  const p=feature.properties||{};
  const kind=p.kind||"จุดข้อมูล";
  const titles={
    traffic_signal:"ไฟจราจร",stop:"ป้ายหยุด",give_way:"ให้ทาง",level_crossing:"ทางตัดรถไฟ",
    speed_camera:"กล้องตรวจความเร็ว",traffic_calming:"ชะลอความเร็ว",hospital:"โรงพยาบาล",school:"โรงเรียน"
  };
  const timing=p.kind==="traffic_signal"?"ไม่พบข้อมูล phase/countdown แบบสด":"–";
  const direction=p["traffic_signals:direction"]||p.direction||"ไม่ระบุ";
  const name=p.name||p.ref||titles[kind]||"จุดข้อมูล";
  el("featureDetail").innerHTML='<div class="qgm-detail"><h3>'+escapeHtml(name)+'</h3><p>'+escapeHtml(titles[kind]||kind)+' · แหล่งข้อมูล '+escapeHtml(p.source||"ไม่ระบุ")+'</p><div class="qgm-detail-grid"><div><small>ชนิด</small><b>'+escapeHtml(titles[kind]||kind)+'</b></div><div><small>ทิศทาง</small><b>'+escapeHtml(direction)+'</b></div><div><small>เวลาไฟสด</small><b>'+escapeHtml(timing)+'</b></div><div><small>ความเชื่อมั่น</small><b>'+(p.source==="OpenStreetMap"?"ข้อมูลชุมชน / ต้องตรวจภาคสนาม":"ตามแหล่งข้อมูล")+'</b></div></div><p>QueueGo จะไม่แสดงว่าไฟจะเขียวหรือแดงในกี่วินาที จนกว่าจะมีข้อมูล SPaT/controller ที่ตรวจสอบได้จริง</p></div>';
  const d=el("featureDialog");
  if(d.showModal)d.showModal();else d.setAttribute("open","");
}
async function searchPlace(q){
  const url=NOMINATIM_ENDPOINT+"?format=jsonv2&limit=6&countrycodes=th&bounded=1&viewbox=102.442357,15.789217,103.499967,14.132135&q="+encodeURIComponent(q);
  const res=await fetchTimeout(url,{headers:{"Accept-Language":"th,en"}},12000);
  if(!res.ok)throw Error("search HTTP "+res.status);
  return await res.json();
}
function renderSearch(rows){
  const host=el("searchResults");
  if(!rows||!rows.length){host.hidden=false;host.innerHTML='<button type="button"><b>ไม่พบสถานที่</b><small>ลองค้นหาชื่ออำเภอ ถนน หรือสถานที่</small></button>';return}
  host.innerHTML=rows.map(function(r,i){return '<button type="button" data-i="'+i+'"><b>'+escapeHtml(r.name||String(r.display_name||"").split(",")[0])+'</b><small>'+escapeHtml(r.display_name||"")+'</small></button>'}).join("");
  host.hidden=false;
  host.querySelectorAll("button[data-i]").forEach(function(btn){
    btn.onclick=function(){
      const r=rows[Number(btn.dataset.i)],lon=Number(r.lon),lat=Number(r.lat);
      if(validCoord(lon,lat)){state.map.flyTo({center:[lon,lat],zoom:15});host.hidden=true}
    };
  });
}
function locate(){
  if(!navigator.geolocation){alert("อุปกรณ์นี้ไม่รองรับ GPS");return}
  navigator.geolocation.getCurrentPosition(function(pos){
    const lon=Number(pos.coords.longitude),lat=Number(pos.coords.latitude);
    if(!validCoord(lon,lat))return;
    state.map.flyTo({center:[lon,lat],zoom:16});
    state.start={lon:lon,lat:lat};state.picking="end";syncRoutePoints();
    if(state.end)calculateRoute();
  },function(){alert("ไม่สามารถอ่านตำแหน่งได้ กรุณาตรวจสิทธิ์ตำแหน่ง")},{enableHighAccuracy:true,timeout:12000,maximumAge:5000});
}
function clearRoute(){
  state.start=null;state.end=null;state.route=null;state.picking="start";
  updateSource("route",emptyCollection());updateSource("route-points",emptyCollection());
  el("routeStats").hidden=true;setSource("route","idle","OSRM public demo · ใช้เพื่อทดสอบเท่านั้น");syncRoutePoints();
}
function exportGeojson(){
  const all=[].concat(state.signals.features,state.safety.features,state.pois.features,state.ngis.features,state.drr.features);
  const payload={
    type:"FeatureCollection",
    name:"QueueGo Buriram Map Lab export",
    generated_at:new Date().toISOString(),
    bbox:[102.442357,14.132135,103.499967,15.789217],
    attribution:"Contains OpenStreetMap data © OpenStreetMap contributors, ODbL. Other features retain their source property.",
    features:all
  };
  const blob=new Blob([JSON.stringify(payload,null,2)],{type:"application/geo+json"});
  const a=document.createElement("a");a.href=URL.createObjectURL(blob);a.download="queuego-buriram-map-lab.geojson";a.click();setTimeout(function(){URL.revokeObjectURL(a.href)},2000);
}
function bind(){
  el("fitProvince").onclick=function(){state.map.fitBounds(BURIRAM_BOUNDS,{padding:{top:90,bottom:210,left:60,right:60}})};
  el("locateMe").onclick=locate;
  el("clearRoute").onclick=clearRoute;
  el("startPoint").onclick=function(){state.picking="start";el("routeInstruction").textContent="แตะแผนที่เพื่อเลือกจุดเริ่มต้น"};
  el("endPoint").onclick=function(){state.picking="end";el("routeInstruction").textContent="แตะแผนที่เพื่อเลือกปลายทาง"};
  el("swapRoute").onclick=function(){const t=state.start;state.start=state.end;state.end=t;syncRoutePoints();if(state.start&&state.end)calculateRoute()};
  el("toggleSignals").onchange=function(e){setVisibility("signals",e.target.checked);setVisibility("signals-halo",e.target.checked)};
  el("toggleSafety").onchange=function(e){setVisibility("safety",e.target.checked)};
  el("togglePois").onchange=function(e){setVisibility("pois",e.target.checked);if(e.target.checked)loadPois()};
  el("toggleNgis").onchange=function(e){setVisibility("ngis",e.target.checked);if(e.target.checked)loadNgis()};
  el("toggleDrr").onchange=function(e){setVisibility("drr",e.target.checked);if(e.target.checked)loadDrr()};
  el("refreshData").onclick=function(){abortActive();state.signals=emptyCollection();state.safety=emptyCollection();updateSource("signals",state.signals);updateSource("safety",state.safety);loadCoreData();if(el("togglePois").checked){state.pois=emptyCollection();loadPois()}if(el("toggleNgis").checked){state.ngis=emptyCollection();updateSource("ngis",state.ngis);loadNgis()}if(el("toggleDrr").checked){state.drr=emptyCollection();updateSource("drr",state.drr);loadDrr()}};
  el("exportGeojson").onclick=exportGeojson;
  el("closeFeature").onclick=function(){el("featureDialog").close?el("featureDialog").close():el("featureDialog").removeAttribute("open")};
  el("searchForm").onsubmit=async function(e){
    e.preventDefault();const q=el("searchInput").value.trim();if(!q)return;
    try{renderSearch(await searchPlace(q))}catch(_){renderSearch([])}
  };
}
renderLedger();
bind();
initMap();
window.QueueGoMapLab={BURIRAM_BOUNDS:BURIRAM_BOUNDS,getState:function(){return state}};
})();
