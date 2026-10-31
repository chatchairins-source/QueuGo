/* Passenger flow shares existing auth, Orders, targeted dispatch, location picker and cash. */
(()=>{
 let owner='',pickup=null,destination=null,quote=null,rows=[],selected=null,busy=false,error='',picker=null,previewMap=null,previewMode='',activeRideId='',activeRideMarkers={pickup:null,dropoff:null,rider:null},routeSupplyMarkers=[],timer=null,config={},supply={nearby_count:0,clusters:[]},supplyAt=0;
 const terminal=o=>!o||['completed','cancelled','no_rider_available'].includes(o.status);
 const key=()=> 'qg_customer_ride_pending_v1:'+S.get()?.userId;
 const actor=()=>S.get()?.userId||'';
 const onRide=()=>location.hash.split('/')[0]==='#ride';
 const rpc=(name,body={})=>db('rpc/'+name,{method:'POST',body});
 const pending=()=>{const raw=localStorage.getItem(key());if(!raw)return null;const p=JSON.parse(raw);if(!/^[0-9a-f-]{36}$/i.test(p.p_request_id)||!/^[0-9a-f-]{36}$/i.test(p.p_quote_id))throw Error('คำขอเดิมอ่านไม่ได้ กรุณาติดต่อฝ่ายช่วยเหลือ');return p};
 const same=u=>actor()===u&&onRide();
 const status=o=>{
  if(o?.status==='rider_assigned'&&o?.arrived_pickup_at)return 'คนขับถึงจุดรับแล้ว';
  if(o?.status==='in_progress'&&o?.arrived_dropoff_at)return 'ถึงจุดหมายแล้ว';
  return ({searching_rider:'กำลังหาคนขับ',rider_assigned:'คนขับกำลังไปจุดรับ',in_progress:'กำลังเดินทาง',completed:'จบการเดินทางแล้ว',cancelled:'ยกเลิกแล้ว',no_rider_available:'ยังไม่มีคนขับพร้อมรับเที่ยว'})[o?.status]||o?.status;
 };
 const rideMoney=v=>'฿'+Number(v||0).toLocaleString('th-TH',{minimumFractionDigits:Number.isInteger(Number(v||0))?0:2,maximumFractionDigits:2});
 function clearPreview(){
  activeRideMarkers={pickup:null,dropoff:null,rider:null};
  routeSupplyMarkers=[];
  activeRideId='';previewMode='';
  if(!previewMap)return;
  try{previewMap.remove?.()}catch{}
  previewMap=null;
 }
 function stop(){clearTimeout(timer);timer=null;picker?.destroy();picker=null;clearPreview()}
 async function read(id){return (await rpc('queuego_ride_state',{p_order_id:id||null})).rides||[]}
 async function refreshSupply(force=false){
  if(!pickup||!qgIsValidCoordinate(pickup.lat,pickup.lng,true)){
   supply={nearby_count:0,clusters:[]};supplyAt=Date.now();return;
  }
  if(!force&&Date.now()-supplyAt<15000)return;
  try{
   const next=await rpc('queuego_customer_ride_supply',{p_lat:Number(pickup.lat),p_lng:Number(pickup.lng)});
   supply={
    nearby_count:Number(next?.nearby_count||0),
    clusters:Array.isArray(next?.clusters)?next.clusters:[]
   };
   supplyAt=Date.now();
  }catch{
   supply={nearby_count:0,clusters:[]};supplyAt=Date.now();
  }
 }
 async function refresh(){
  const u=actor(),recent=await read();if(!same(u))return;
  if(selected&&!recent.some(r=>r.id===selected)){const old=await read(selected);if(!same(u))return;rows=[...old,...recent]}else rows=recent;
  const active=rows.find(o=>!terminal(o));if(active)selected=active.id;
  const p=pending();if(p){const committed=rows.find(o=>o.id===p.p_request_id)||(await read(p.p_request_id))[0];if(!same(u))return;if(committed){selected=committed.id;rows=[committed,...rows.filter(o=>o.id!==committed.id)];localStorage.removeItem(key());}}
 }
 const rideSnapshotKey=o=>o?[o.id,o.status,o.rider_id||'',o.rider_name||'',o.arrived_pickup_at||'',o.arrived_dropoff_at||'',o.fare,o.pickup_address||'',o.delivery_address||'',o.distance_km,o.duration_minutes].join('|'):'';
 function updateRideTrackingText(o){
  const el=$('ride-live-status');if(!el||!o?.rider_id||terminal(o))return;
  el.textContent=qgIsValidCoordinate(o.rider_latitude,o.rider_longitude,true)?'ตำแหน่งคนขับล่าสุด · อัปเดตประมาณทุก 5 วินาที':'กำลังรอตำแหน่งล่าสุดจากคนขับ';
 }
 function schedule(){clearTimeout(timer);if(document.hidden||!onRide())return;timer=setTimeout(async()=>{if(!busy&&!picker){try{
   const before=rows.find(o=>o.id===selected),beforeKey=rideSnapshotKey(before),hadActive=rows.some(o=>!terminal(o)),hadPending=!!pending(),beforeSupply=JSON.stringify(supply);
   await refresh();
   if(!rows.some(o=>!terminal(o))&&pickup)await refreshSupply();
   if(onRide()&&(pending()||rows.some(o=>!terminal(o))||pickup)){
    const current=rows.find(o=>o.id===selected);
    if(current){
     if(beforeKey&&beforeKey===rideSnapshotKey(current)&&updateActiveRideMap(current))updateRideTrackingText(current);
     else render();
    }else if(hadActive||hadPending!==!!pending())render();
    else if(beforeSupply!==JSON.stringify(supply))updateRouteSupplyView();
   }
  }catch(e){error=e.message}}schedule()},5000)}
 function ridePageHeader(title,subtitle,backId){
  const back=backId?'id="'+backId+'"':'onclick="go(\'home\')"';
  return '<div class="pt"><button type="button" class="back" '+back+' aria-label="กลับ">'+ico('back')+'</button><div><h1>'+esc(title)+'</h1><p class="sm" style="margin:3px 0 0">'+esc(subtitle)+'</p></div></div>';
 }
 const rideMapFrameStyle='height:245px;border-radius:16px;overflow:hidden;margin-bottom:8px;background:#eef1f4';
 function rideLocationButton(kind,label,value,color){
  return '<button type="button" data-action="'+kind+'" class="qg-ride-location" '+(busy||pending()||!config.enabled?'disabled':'')+' style="width:100%;display:flex;align-items:center;gap:12px;text-align:left;padding:13px 12px;border:1px solid #e6e7ea;border-radius:14px;background:#fff;color:#17181a;margin:0 0 9px"><span aria-hidden="true" style="width:12px;height:12px;border-radius:50%;background:'+color+';box-shadow:0 0 0 4px '+color+'1f;flex:none"></span><span style="min-width:0;flex:1"><small style="display:block;color:#777d86;font-weight:700;margin-bottom:3px">'+label+'</small><b style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">'+esc(window.QueueGoMapWeb?.compactAddress?.(value)||value||'เลือกบนแผนที่')+'</b></span><span aria-hidden="true" style="font-size:22px;color:#9aa0a8">›</span></button>';
 }
 function rideStopMarkerElement(text,color){
  const el=document.createElement('div');
  el.style.cssText='width:42px;height:52px;display:flex;flex-direction:column;align-items:center;justify-content:flex-start;z-index:60;filter:drop-shadow(0 4px 8px rgba(0,0,0,.24))';
  el.innerHTML='<div style="min-width:36px;height:36px;padding:0 7px;border-radius:18px;background:'+color+';border:3px solid #fff;color:#fff;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:1000;line-height:1;white-space:nowrap">'+text+'</div><div style="width:12px;height:12px;margin-top:-7px;background:'+color+';border-right:3px solid #fff;border-bottom:3px solid #fff;transform:rotate(45deg)"></div>';
  return el;
 }
 function riderLiveMarkerElement(){
  const el=document.createElement('div');
  el.style.cssText='z-index:20;transition:transform 900ms linear';
  el.innerHTML='<div style="width:42px;height:42px;border-radius:50%;background:#fff;border:3px solid #e6002d;box-shadow:0 4px 14px #0003;display:flex;align-items:center;justify-content:center;color:#e6002d">'+ico('motorcycle',22)+'</div>';
  el.setAttribute('aria-label','ตำแหน่งคนขับ');
  return el;
 }
 function rideClusterMarkerElement(cluster){
  const count=Math.max(2,Number(cluster.count||2)),el=document.createElement('div');
  el.innerHTML='<div style="min-width:36px;height:36px;padding:0 8px;border-radius:18px;background:#fff;border:2px solid #e6002d;box-shadow:0 3px 12px #0003;display:flex;align-items:center;justify-content:center;gap:3px;color:#e6002d;font-weight:900;font-size:12px">'+ico('motorcycle',16)+'<span>'+count+'</span></div>';
  el.style.zIndex='10';el.setAttribute('aria-label','Rider พร้อมรับงานโดยประมาณ');
  return el;
 }
 function updateRouteSupplyView(){
  const host=$('ride-route-map');
  if(previewMode==='route'&&previewMap&&host&&(!previewMap.getContainer||previewMap.getContainer()===host)){
   for(const marker of routeSupplyMarkers)try{marker.remove()}catch{}
   routeSupplyMarkers=[];
   for(const cluster of supply.clusters||[]){
    if(!qgIsValidCoordinate(cluster.latitude,cluster.longitude,true))continue;
    routeSupplyMarkers.push(new maplibregl.Marker({element:rideClusterMarkerElement(cluster)}).setLngLat([Number(cluster.longitude),Number(cluster.latitude)]).addTo(previewMap));
   }
  }
  const summary=$('ride-nearby-supply');
  if(summary){
   const n=Number(supply.nearby_count||0);
   summary.innerHTML='<span style="width:8px;height:8px;border-radius:50%;background:'+(n>0?'#18a875':'#a0a5ad')+'"></span><b>'+(n>0?'มี Rider พร้อมรับงานใกล้จุดรับ '+n+' คน':'ยังไม่พบ Rider พร้อมรับงานในระยะ 8 กม.')+'</b><span style="margin-left:auto;color:#8a9098">ตำแหน่งบนแผนที่เป็นแบบกลุ่มโดยประมาณ</span>';
  }
 }
 function setActiveRideMarker(kind,lat,lng,label,color){
  const current=activeRideMarkers[kind],valid=qgIsValidCoordinate(lat,lng,true);
  if(!valid){try{current?.remove?.()}catch{}activeRideMarkers[kind]=null;return null}
  const point=[Number(lng),Number(lat)];
  if(current){current.setLngLat(point);return point}
  const stop=kind!=='rider',el=stop?rideStopMarkerElement(label,color):riderLiveMarkerElement();
  el.setAttribute('aria-label',kind==='pickup'?'จุดรับ':kind==='dropoff'?'จุดหมาย':'ตำแหน่งคนขับ');
  activeRideMarkers[kind]=new maplibregl.Marker({element:el,anchor:stop?'bottom':'center'}).setLngLat(point).addTo(previewMap);
  return point;
 }
 function updateActiveRideMap(o){
  const host=$('ride-live-map');
  if(!o||previewMode!=='active'||activeRideId!==String(o.id||'')||!previewMap||!host)return false;
  try{
   if(previewMap.getContainer&&previewMap.getContainer()!==host)return false;
   setActiveRideMarker('pickup',o.pickup_latitude,o.pickup_longitude,'รับ','#1677ff');
   setActiveRideMarker('dropoff',o.delivery_latitude,o.delivery_longitude,'ส่ง','#e6002d');
   const previousRider=activeRideMarkers.rider?.getLngLat?.();
   const rider=setActiveRideMarker('rider',o.rider_latitude,o.rider_longitude,'','#e6002d');
   const target=o.status==='in_progress'
    ?(qgIsValidCoordinate(o.delivery_latitude,o.delivery_longitude,true)?[Number(o.delivery_longitude),Number(o.delivery_latitude)]:null)
    :(qgIsValidCoordinate(o.pickup_latitude,o.pickup_longitude,true)?[Number(o.pickup_longitude),Number(o.pickup_latitude)]:null);
   if(rider&&target&&(!previousRider||Math.abs(previousRider.lng-rider[0])>0.000001||Math.abs(previousRider.lat-rider[1])>0.000001)){
    const bounds=new maplibregl.LngLatBounds();bounds.extend(rider);bounds.extend(target);
    if(Math.abs(rider[0]-target[0])<0.00001&&Math.abs(rider[1]-target[1])<0.00001)previewMap.easeTo({center:rider,zoom:16,duration:850});
    else previewMap.fitBounds(bounds,{padding:56,maxZoom:16,duration:850});
   }
   return true;
  }catch{return false}
 }
 function renderActiveRideMap(o){
  clearPreview();
  const host=$('ride-live-map');
  if(!host||typeof maplibregl==='undefined'||typeof qgQueueGoPickerStyle!=='function'||!o)return;
  try{
   const points=[];
   const addPoint=(lat,lng)=>{if(qgIsValidCoordinate(lat,lng,true))points.push([Number(lng),Number(lat)])};
   addPoint(o.pickup_latitude,o.pickup_longitude);
   addPoint(o.delivery_latitude,o.delivery_longitude);
   addPoint(o.rider_latitude,o.rider_longitude);
   const center=points[2]||points[0]||points[1]||[101,15];
   previewMap=new maplibregl.Map({container:host,style:qgQueueGoPickerStyle(),center,zoom:points.length?15:6,minZoom:5,maxZoom:19,attributionControl:false,maxPitch:60,dragRotate:true,pitchWithRotate:true});
   previewMode='active';activeRideId=String(o.id||'');
   previewMap.touchZoomRotate.enableRotation();
   previewMap.touchPitch.enable();
   previewMap.addControl(new maplibregl.NavigationControl({showCompass:true,showZoom:true,visualizePitch:true}),'top-left');
   previewMap.addControl(new maplibregl.AttributionControl({compact:true}),'bottom-right');
   const bounds=new maplibregl.LngLatBounds();
   for(const point of [
    setActiveRideMarker('pickup',o.pickup_latitude,o.pickup_longitude,'รับ','#1677ff'),
    setActiveRideMarker('dropoff',o.delivery_latitude,o.delivery_longitude,'ส่ง','#e6002d'),
    setActiveRideMarker('rider',o.rider_latitude,o.rider_longitude,'','#e6002d')
   ])if(point)bounds.extend(point);
   previewMap.once('load',()=>{
    if(!previewMap||previewMode!=='active'||activeRideId!==String(o.id||'')||bounds.isEmpty())return;
    const ne=bounds.getNorthEast(),sw=bounds.getSouthWest();
    if(Math.abs(ne.lat-sw.lat)<0.00001&&Math.abs(ne.lng-sw.lng)<0.00001)previewMap.easeTo({center:[sw.lng,sw.lat],zoom:16,duration:0});
    else previewMap.fitBounds(bounds,{padding:64,maxZoom:16,duration:0});
   });
  }catch{clearPreview()}
 }
 function renderRoutePreview(){
  clearPreview();
  const host=$('ride-route-map');
  if(!host||typeof maplibregl==='undefined'||typeof qgQueueGoPickerStyle!=='function')return;
  try{
   const center=pickup&&qgIsValidCoordinate(pickup.lat,pickup.lng,true)
    ?[Number(pickup.lng),Number(pickup.lat)]
    :destination&&qgIsValidCoordinate(destination.lat,destination.lng,true)
      ?[Number(destination.lng),Number(destination.lat)]
      :[101,15];
   previewMap=new maplibregl.Map({
    container:host,
    style:qgQueueGoPickerStyle(),
    center,
    zoom:pickup||destination?15:6,
    minZoom:5,
    maxZoom:19,
    attributionControl:false,
    maxPitch:60,
    dragRotate:true,
    pitchWithRotate:true
   });
   previewMode='route';
   previewMap.touchZoomRotate.enableRotation();
   previewMap.touchPitch.enable();
   previewMap.addControl(new maplibregl.NavigationControl({showCompass:true,showZoom:true,visualizePitch:true}),'top-left');
   previewMap.addControl(new maplibregl.AttributionControl({compact:true}),'bottom-right');

   const bounds=new maplibregl.LngLatBounds();
   const addMarker=(lat,lng,color,title,html,stopLabel='')=>{
    if(!qgIsValidCoordinate(lat,lng,true))return;
    const el=stopLabel?rideStopMarkerElement(stopLabel,color):document.createElement('div');
    if(!stopLabel){
     if(html instanceof HTMLElement)el.append(html);
     else if(html)el.innerHTML=html;
     else {
      el.style.width='24px';el.style.height='24px';el.style.borderRadius='50%';
      el.style.background=color;el.style.border='4px solid #fff';
      el.style.boxShadow='0 3px 12px rgba(0,0,0,.28)';
     }
     el.style.zIndex='10';
    }
    el.setAttribute('aria-label',title||'ตำแหน่ง');
    const marker=new maplibregl.Marker({element:el,anchor:stopLabel?'bottom':'center'}).setLngLat([Number(lng),Number(lat)]).addTo(previewMap);
    bounds.extend([Number(lng),Number(lat)]);
    return marker;
   };

   for(const cluster of (supply.clusters||[])){
    if(!qgIsValidCoordinate(cluster.latitude,cluster.longitude,true))continue;
    const marker=addMarker(cluster.latitude,cluster.longitude,'#e6002d','Rider พร้อมรับงานโดยประมาณ',rideClusterMarkerElement(cluster));
    if(marker)routeSupplyMarkers.push(marker);
   }
   if(pickup)addMarker(pickup.lat,pickup.lng,'#1677ff','จุดรับ',null,'รับ');
   if(destination)addMarker(destination.lat,destination.lng,'#e6002d','จุดหมาย',null,'ส่ง');

   previewMap.once('load',()=>{
    if(!previewMap)return;
    if(!bounds.isEmpty()){
     const ne=bounds.getNorthEast(),sw=bounds.getSouthWest();
     if(Math.abs(ne.lat-sw.lat)<0.00001&&Math.abs(ne.lng-sw.lng)<0.00001){
      previewMap.easeTo({center:[sw.lng,sw.lat],zoom:16,duration:0});
     }else{
      previewMap.fitBounds(bounds,{padding:48,maxZoom:16,duration:0});
     }
    }
   });
  }catch{clearPreview()}
 }
 async function quickCurrentPickup(){
  if(busy||pending()||!config.enabled)return;
  busy=true;error='';render();
  try{
   const pos=await new Promise((ok,no)=>navigator.geolocation?navigator.geolocation.getCurrentPosition(ok,no,{enableHighAccuracy:true,timeout:12000,maximumAge:30000}):no(new Error('เบราว์เซอร์ไม่รองรับ GPS')));
   const lat=pos.coords.latitude,lng=pos.coords.longitude;
   if(!qgIsValidCoordinate(lat,lng,true))throw Error('พิกัด GPS ไม่ถูกต้อง');
   const address=await qtReverseGeocodeCustomer(lat,lng);
   pickup={lat,lng,address:address||'ตำแหน่งปัจจุบัน'};quote=null;supplyAt=0;await refreshSupply(true);
  }catch(e){error=e?.code===1?'กรุณาอนุญาตการเข้าถึงตำแหน่ง':(e?.message||'ไม่สามารถรับตำแหน่งปัจจุบันได้')}
  finally{busy=false;if(onRide())render()}
 }
 function render(){
  if(!onRide())return;clearPreview();const current=rows.find(o=>o.id===selected),p=pending();
  let html=ridePageHeader('บริการเรียกรถ','รับส่งผู้โดยสารด้วย QueueGo');
  if(error)html+='<p role="alert" style="margin:0 0 10px">'+esc(error)+'</p>';
  if(current){
   const tracking=current.rider_id&&!terminal(current)
    ?(qgIsValidCoordinate(current.rider_latitude,current.rider_longitude,true)?'ตำแหน่งคนขับล่าสุด · อัปเดตประมาณทุก 5 วินาที':'กำลังรอตำแหน่งล่าสุดจากคนขับ')
    :'';
   html+='<div id="ride-live-map" class="mapbox" style="height:300px;border-radius:20px;overflow:hidden;margin:0 0 12px;background:#eef1f4"></div>'+
    (tracking?'<p id="ride-live-status" class="sm" style="margin:0 2px 10px;color:#6f7680">'+esc(tracking)+'</p>':'')+
    '<section class="card"><h2>'+esc(QueueGoOrderNumber.format(current))+'</h2><b>'+esc(status(current))+'</b><p>จุดรับ: '+esc(current.pickup_address)+'</p><p>จุดหมาย: '+esc(current.delivery_address)+'</p><p>ค่าโดยสารเงินสด <b>'+rideMoney(current.fare)+'</b></p><p>คนขับ: '+esc(current.rider_name||'กำลังหา')+'</p><p>เส้นทางที่ประเมิน '+Number(current.distance_km).toLocaleString('th-TH')+' กม. · เวลาวิ่งประมาณ '+Math.ceil(current.duration_minutes)+' นาที ไม่รวมเวลารอคนขับ</p>';
   if(!terminal(current)&&current.rider_id)html+='<button class="lk" onclick="go(\'order-chat/'+esc(current.id)+'\')">ติดต่อคนขับ</button>';
   if(current.status==='searching_rider'&&!current.rider_id&&!p)html+='<button class="lk" data-action="cancel" '+(busy?'disabled':'')+'>ยกเลิกการเรียกรถ</button>';
   if(terminal(current)&&!p)html+='<button class="prim" data-action="new">เรียกรถเที่ยวใหม่</button>';
   html+='</section>';
  }else{
   html+='<section class="card" style="padding:14px">';
   if(!config.enabled||!config.configured)html+='<p>บริการเรียกรถยังไม่เปิดให้ใช้ในขณะนี้</p>';
   html+='<div id="ride-route-map" class="mapbox" style="'+rideMapFrameStyle+'"></div>';
   if(pickup){
    const n=Number(supply.nearby_count||0);
    html+='<div id="ride-nearby-supply" class="sm" style="display:flex;align-items:center;gap:7px;margin:0 2px 12px;color:#59606a"><span style="width:8px;height:8px;border-radius:50%;background:'+(n>0?'#18a875':'#a0a5ad')+'"></span><b>'+(n>0?'มี Rider พร้อมรับงานใกล้จุดรับ '+n+' คน':'ยังไม่พบ Rider พร้อมรับงานในระยะ 8 กม.')+'</b><span style="margin-left:auto;color:#8a9098">ตำแหน่งบนแผนที่เป็นแบบกลุ่มโดยประมาณ</span></div>';
   }
   html+=rideLocationButton('pickup','จุดรับ',pickup?.address,'#1677ff');
   html+=rideLocationButton('destination','จุดหมาย',destination?.address,'#e6002d');
   if(!pickup&&!p&&config.enabled)html+='<button type="button" id="ride-use-current" style="width:100%;padding:10px 12px;border:1px solid #cfd9e6;border-radius:12px;background:#f7faff;color:#2e6db4;font-weight:800;margin-bottom:10px">ใช้ตำแหน่งปัจจุบันเป็นจุดรับ</button>';
   if(quote)html+='<div style="padding:12px;border-radius:14px;background:#f7f8fa;margin-top:4px"><div style="display:flex;justify-content:space-between;align-items:end"><span>ค่าโดยสารเงินสด</span><b style="font-size:24px">'+rideMoney(quote.fare)+'</b></div><p class="sm" style="margin:5px 0 0">ระยะทาง '+Number(quote.distance_km).toLocaleString('th-TH',{maximumFractionDigits:1})+' กม. · เวลาวิ่งประมาณ '+Math.ceil(quote.duration_minutes)+' นาที</p></div>';
   else if(!p)html+='<button class="prim" data-action="quote" '+(busy||!config.enabled||!pickup||!destination?'disabled':'')+' style="margin-top:4px">'+(busy?'กำลังตรวจสอบ…':'ดูค่าโดยสาร')+'</button>';
   html+='</section>';
  }
  if(p||!current&&quote)html+='<button class="prim" data-action="request" '+(busy?'disabled':'')+'>'+(p?'ตรวจผลคำขอเดิม / ลองอีกครั้ง':'เรียกรถ')+'</button>';
  layout(html,'orders');
  document.querySelectorAll('[data-action]').forEach(button=>button.onclick=()=>action(button.dataset.action));
  const quick=$('ride-use-current');if(quick)quick.onclick=quickCurrentPickup;
  if(current)renderActiveRideMap(current);else renderRoutePreview();
 }
 async function pick(which){
  const saved=which==='pickup'?pickup:destination;
  const auto=which==='pickup'&&!saved&&currentPos&&qgIsValidCoordinate(currentPos.lat,currentPos.lng,true)
    ?{lat:Number(currentPos.lat),lng:Number(currentPos.lng),address:currentPos.address||''}:null;
  const reference=which==='destination'&&!saved
    ?(pickup&&qgIsValidCoordinate(pickup.lat,pickup.lng,true)?pickup:(currentPos&&qgIsValidCoordinate(currentPos.lat,currentPos.lng,true)?currentPos:null))
    :null;
  const selectedInitial=saved||auto;
  const initial=selectedInitial||reference;
  const rawAddress=selectedInitial?.address||'',initialAddress=window.QueueGoMapWeb?.compactAddress?.(rawAddress)||rawAddress;
  let point=selectedInitial?{lat:initial.lat,lng:initial.lng}:null,revision=0;
  const title=which==='pickup'?'เลือกจุดรับ':'เลือกจุดหมาย';
  const subtitle=which==='pickup'
    ?'แตะตำแหน่งบนแผนที่เพื่อปักจุดรับของคุณ'
    :'แตะตำแหน่งบนแผนที่เพื่อปักจุดส่งของคุณ';
  const addressLabel=which==='pickup'?'ที่อยู่จุดรับ':'ที่อยู่จุดส่ง';
  layout(
   ridePageHeader(title,subtitle,'ride-back')+
   '<section class="card" style="padding:14px">'+
    '<div id="ride-location-map" class="mapbox" style="'+rideMapFrameStyle+'"></div>'+
    '<button type="button" id="ride-gps" style="width:100%;display:flex;align-items:center;gap:12px;text-align:left;margin:9px 0;padding:13px 12px;border:1px solid #e6e7ea;border-radius:14px;background:#fff;color:#e6002d">'+
     '<span aria-hidden="true" style="width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#fff0f2;flex:none">'+ico('pin',18)+'</span>'+
     '<span style="min-width:0;flex:1"><b style="display:block;font-size:14px">ใช้ตำแหน่ง GPS ปัจจุบัน</b><small style="display:block;margin-top:3px;color:#777d86">ค้นหาตำแหน่งของคุณบนแผนที่</small></span>'+
     '<span aria-hidden="true" style="font-size:22px;color:#9aa0a8">›</span>'+
    '</button>'+
    '<label for="ride-address" class="sm" style="display:block;margin:12px 2px 7px;font-weight:700">'+addressLabel+'</label>'+
    '<textarea id="ride-address" maxlength="500" placeholder="ที่อยู่หรือจุดสังเกต" style="box-sizing:border-box;width:100%;min-height:74px;resize:vertical;border:1px solid #e6e7ea;border-radius:14px;background:#fff;padding:12px;font:inherit;font-size:16px;line-height:1.5;color:#17181a">'+esc(initialAddress)+'</textarea>'+
    '<div id="ride-picked-status" class="sm" style="margin:7px 2px 0;color:#777d86">'+(point?'เลือกพิกัดแล้ว':'แตะตำแหน่งบนแผนที่ หรือใช้ GPS เพื่อปักพิกัด')+'</div>'+
    '<button id="ride-save-location" class="prim">ใช้จุดนี้</button>'+
    '<p id="ride-location-error" role="status" style="margin:8px 2px 0;color:#e6002d"></p>'+
   '</section>','orders');
  const u=actor();picker=initLocationPicker('ride-location-map',initial?.lat,initial?.lng,async(lat,lng)=>{
   point={lat,lng};const version=++revision,field=$('ride-address'),status=$('ride-picked-status');
   if(status)status.textContent='เลือกพิกัดแล้ว';
   if(!field)return;
   const previous=field.value.trim();
   if(previous&&initialAddress&&previous===initialAddress)return;
   if(previous&&!initialAddress)return;
   const address=await qtReverseGeocodeCustomer(lat,lng);
   if(same(u)&&version===revision&&field.isConnected){
    if(!field.value.trim()||field.value.trim()===initialAddress)field.value=address||field.value;
   }
  },{selectInitial:!!(saved||auto)});
  $('ride-gps').onclick=()=>picker?.locate();
  $('ride-back').onclick=()=>{picker?.destroy();picker=null;render()};
  if(which==='pickup'&&!saved){
   // Saved/current QueueGo location becomes the initial selected point immediately.
   // If there is no valid saved point, try device GPS after the map is attached.
   if(!auto)setTimeout(()=>picker?.locate(),180);
  }
  $('ride-save-location').onclick=()=>{
   const address=$('ride-address').value.trim();
   if(!point||address.length<3){$('ride-location-error').textContent='เลือกพิกัดและระบุที่อยู่ให้ครบ';return}
   const next={...point,address};
   if(which==='pickup'){pickup=next;supplyAt=0}else destination=next;
   quote=null;picker?.destroy();picker=null;
   if(which==='pickup')refreshSupply(true).finally(()=>{if(onRide())render()});else render();
  };
 }
 async function action(kind){
  if(busy)return;if(kind==='pickup'||kind==='destination')return pick(kind);
  if(kind==='new'){selected=null;quote=null;error='';return render()}
  const u=actor();busy=true;error='';render();
  try{
   if(kind==='quote'){
    const accessToken=await token();if(!same(u))return;
    const res=await fetch(CFG.URL+'/functions/v1/queuego-ride-quote',{method:'POST',headers:hdr(accessToken),body:JSON.stringify({pickup,destination}),signal:AbortSignal.timeout(30000)});
    const data=await res.json();if(!res.ok)throw Error(data.error||'ยังประเมินราคาไม่ได้');if(!same(u))return;quote=data;
   }else if(kind==='request'){
    let p=pending();if(!p){if(!quote)throw Error('ตรวจราคาก่อนเรียกรถ');p={p_request_id:uuid(),p_quote_id:quote.quote_id};localStorage.setItem(key(),JSON.stringify(p));}
    const result=await rpc('queuego_request_ride',p);if(!same(u))return;
    if(result?.id!==p.p_request_id)throw Error('ยังยืนยันเลขเที่ยวไม่ได้');selected=result.id;
    // Clear only after the exact server receipt; refresh failure cannot turn success into a new request.
    try{localStorage.removeItem(key())}catch{}quote=null;await refresh();
   }else if(kind==='cancel'){
    const o=rows.find(r=>r.id===selected);if(!o||o.rider_id||o.status!=='searching_rider')throw Error('เที่ยวนี้ยกเลิกไม่ได้');await rpc('qg_customer_cancel_order',{p_order_id:o.id});if(same(u))await refresh();
   }
  }catch(e){
   if(same(u)){
    error=e.message;
    if(kind==='request'){
     try{const p=pending(),committed=p&&(await read(p.p_request_id))[0];if(!same(u))return;if(committed){selected=committed.id;rows=[committed,...rows.filter(o=>o.id!==committed.id)];localStorage.removeItem(key());error='';quote=null}else if([400,422].includes(e.status)){localStorage.removeItem(key());quote=null}}catch{/* Unknown replies retain the exact request. */}
    }
   }
  }finally{busy=false;if(same(u))render()}
 }
 V.ride=async(id,ticket=routeVersion)=>{
  const u=actor();if(!u)return go('login');stop();
  if(owner!==u){owner=u;pickup=currentPos?{lat:currentPos.lat,lng:currentPos.lng,address:currentPos.address}:null;destination=quote=selected=null;rows=[];error='';supply={nearby_count:0,clusters:[]};supplyAt=0;}
  if(id)selected=id;
  try{
   const c=await rpc('queuego_ride_config');if(!same(u)||ticket!==routeVersion)return;config=c||{};
   if(pickup)await refreshSupply(true);if(!same(u)||ticket!==routeVersion)return;
   await refresh();if(!same(u)||ticket!==routeVersion)return;
  }catch(e){
   if(!same(u)||ticket!==routeVersion)return;
   config={enabled:false,configured:false};
   error='';
   rows=[];
  }
  render();schedule();
 };
 const ordinary=V.order;
 V.order=async(id,ticket=routeVersion)=>{
  const u=actor(),o=(await db('orders?select=fulfillment_vertical&id=eq.'+encodeURIComponent(id)+'&customer_id=eq.'+encodeURIComponent(u)+'&limit=1'))?.[0];
  if(actor()!==u||ticket!==routeVersion)return;
  if(o?.fulfillment_vertical==='ride')return go('ride/'+id);return ordinary(id,ticket);
 };
 addEventListener('hashchange',stop);addEventListener('pagehide',stop);
 document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();else if(onRide())route()});
 addEventListener('online',()=>{if(onRide())route()});
})();
