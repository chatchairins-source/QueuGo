/* Passenger flow shares existing auth, Orders, targeted dispatch, location picker and cash. */
(()=>{
 let owner='',pickup=null,destination=null,quote=null,rows=[],selected=null,busy=false,error='',picker=null,previewMap=null,timer=null,config={};
 const terminal=o=>!o||['completed','cancelled','no_rider_available'].includes(o.status);
 const key=()=> 'qg_customer_ride_pending_v1:'+S.get()?.userId;
 const actor=()=>S.get()?.userId||'';
 const onRide=()=>location.hash.split('/')[0]==='#ride';
 const rpc=(name,body={})=>db('rpc/'+name,{method:'POST',body});
 const pending=()=>{const raw=localStorage.getItem(key());if(!raw)return null;const p=JSON.parse(raw);if(!/^[0-9a-f-]{36}$/i.test(p.p_request_id)||!/^[0-9a-f-]{36}$/i.test(p.p_quote_id))throw Error('คำขอเดิมอ่านไม่ได้ กรุณาติดต่อฝ่ายช่วยเหลือ');return p};
 const same=u=>actor()===u&&onRide();
 const status=o=>({searching_rider:'กำลังหาคนขับ',rider_assigned:'คนขับกำลังไปจุดรับ',in_progress:'กำลังเดินทาง',completed:'จบการเดินทางแล้ว',cancelled:'ยกเลิกแล้ว',no_rider_available:'ยังไม่มีคนขับพร้อมรับเที่ยว'})[o.status]||o.status;
 function clearPreview(){
  if(!previewMap)return;
  try{previewMap.Overlays.clear()}catch{}
  try{previewMap.pause(true)}catch{}
  try{previewMap.placeholder()?.remove()}catch{}
  previewMap=null;
 }
 function stop(){clearTimeout(timer);timer=null;picker?.destroy();picker=null;clearPreview()}
 async function read(id){return (await rpc('queuego_ride_state',{p_order_id:id||null})).rides||[]}
 async function refresh(){
  const u=actor(),recent=await read();if(!same(u))return;
  if(selected&&!recent.some(r=>r.id===selected)){const old=await read(selected);if(!same(u))return;rows=[...old,...recent]}else rows=recent;
  const active=rows.find(o=>!terminal(o));if(active)selected=active.id;
  const p=pending();if(p){const committed=rows.find(o=>o.id===p.p_request_id)||(await read(p.p_request_id))[0];if(!same(u))return;if(committed){selected=committed.id;rows=[committed,...rows.filter(o=>o.id!==committed.id)];localStorage.removeItem(key());}}
 }
 function schedule(){clearTimeout(timer);if(document.hidden||!onRide())return;timer=setTimeout(async()=>{if(!busy&&!picker){try{await refresh();if(onRide()&&(pending()||rows.some(o=>!terminal(o))))render()}catch(e){error=e.message;if(onRide())render()}}schedule()},5000)}
 function rideLocationButton(kind,label,value,color){
  return '<button type="button" data-action="'+kind+'" class="qg-ride-location" '+(busy||pending()||!config.enabled?'disabled':'')+' style="width:100%;display:flex;align-items:center;gap:12px;text-align:left;padding:13px 12px;border:1px solid #e6e7ea;border-radius:14px;background:#fff;color:#17181a;margin:0 0 9px"><span aria-hidden="true" style="width:12px;height:12px;border-radius:50%;background:'+color+';box-shadow:0 0 0 4px '+color+'1f;flex:none"></span><span style="min-width:0;flex:1"><small style="display:block;color:#777d86;font-weight:700;margin-bottom:3px">'+label+'</small><b style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">'+esc(value||'เลือกบนแผนที่')+'</b></span><span aria-hidden="true" style="font-size:22px;color:#9aa0a8">›</span></button>';
 }
 function renderRoutePreview(){
  clearPreview();
  const host=$('ride-route-map');if(!host||typeof longdo==='undefined')return;
  try{
   previewMap=new longdo.Map({placeholder:host,language:'th',zoom:pickup||destination?15:6,ui:window.longdo.UiComponent.Mobile});
   try{previewMap.Ui.LayerSelector.visible(false)}catch{}
   const points=[];
   const add=(point,title,color)=>{
    if(!point||!qgIsValidCoordinate(point.lat,point.lng,true))return;
    const loc={lat:Number(point.lat),lon:Number(point.lng)};
    const marker=new longdo.Marker(loc,{title,icon:{html:'<div style="width:22px;height:22px;border-radius:50%;background:'+color+';border:4px solid white;box-shadow:0 3px 12px #0004"></div>'},weight:longdo.OverlayWeight.Top});
    previewMap.Overlays.add(marker);points.push(loc);
   };
   add(pickup,'จุดรับ','#1677ff');add(destination,'จุดหมาย','#e6002d');
   if(points.length===1){previewMap.location(points[0],true);previewMap.zoom(16,true)}
   else if(points.length===2){
    previewMap.location({lat:(points[0].lat+points[1].lat)/2,lon:(points[0].lon+points[1].lon)/2},true);
    const km=calculateDistanceKm(points[0].lat,points[0].lon,points[1].lat,points[1].lon);
    previewMap.zoom(km>30?9:km>15?10:km>7?11:km>3?12:km>1.5?13:14,true);
   }
   setTimeout(()=>previewMap?.resize(),80);
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
   pickup={lat,lng,address:address||'ตำแหน่งปัจจุบัน'};quote=null;
  }catch(e){error=e?.code===1?'กรุณาอนุญาตการเข้าถึงตำแหน่ง':(e?.message||'ไม่สามารถรับตำแหน่งปัจจุบันได้')}
  finally{busy=false;if(onRide())render()}
 }
 function render(){
  if(!onRide())return;clearPreview();const current=rows.find(o=>o.id===selected),p=pending();
  let html='<div class="pt"><button class="back" onclick="go(\'home\')">'+ico('back')+'</button><div><h1>บริการเรียกรถ</h1><p class="sm" style="margin:3px 0 0">รับส่งผู้โดยสารด้วย QueueGo</p></div></div>';
  if(error)html+='<p role="alert" style="margin:0 0 10px">'+esc(error)+'</p>';
  if(current){
   html+='<section class="card"><h2>'+esc(QueueGoOrderNumber.format(current))+'</h2><b>'+esc(status(current))+'</b><p>จุดรับ: '+esc(current.pickup_address)+'</p><p>จุดหมาย: '+esc(current.delivery_address)+'</p><p>ค่าโดยสารเงินสด <b>'+baht(current.fare)+'</b></p><p>คนขับ: '+esc(current.rider_name||'กำลังหา')+'</p><p>เส้นทางที่ประเมิน '+Number(current.distance_km).toLocaleString('th-TH')+' กม. · เวลาวิ่งประมาณ '+Math.ceil(current.duration_minutes)+' นาที ไม่รวมเวลารอคนขับ</p>';
   if(!terminal(current)&&current.rider_id)html+='<button class="lk" onclick="go(\'order-chat/'+esc(current.id)+'\')">ติดต่อคนขับ</button>';
   if(current.status==='searching_rider'&&!current.rider_id&&!p)html+='<button class="lk" data-action="cancel" '+(busy?'disabled':'')+'>ยกเลิกการเรียกรถ</button>';
   if(terminal(current)&&!p)html+='<button class="prim" data-action="new">เรียกรถเที่ยวใหม่</button>';
   html+='</section>';
  }else{
   html+='<section class="card" style="padding:14px">';
   if(!config.enabled||!config.configured)html+='<p>บริการเรียกรถยังไม่เปิดให้ใช้ในขณะนี้</p>';
   html+='<div id="ride-route-map" class="mapbox" style="height:245px;border-radius:16px;overflow:hidden;margin-bottom:12px;background:#eef1f4"></div>';
   html+=rideLocationButton('pickup','จุดรับ',pickup?.address,'#1677ff');
   html+=rideLocationButton('destination','จุดหมาย',destination?.address,'#e6002d');
   if(!pickup&&!p&&config.enabled)html+='<button type="button" id="ride-use-current" style="width:100%;padding:10px 12px;border:1px solid #cfd9e6;border-radius:12px;background:#f7faff;color:#2e6db4;font-weight:800;margin-bottom:10px">ใช้ตำแหน่งปัจจุบันเป็นจุดรับ</button>';
   if(quote)html+='<div style="padding:12px;border-radius:14px;background:#f7f8fa;margin-top:4px"><div style="display:flex;justify-content:space-between;align-items:end"><span>ค่าโดยสารเงินสด</span><b style="font-size:24px">'+baht(quote.fare)+'</b></div><p class="sm" style="margin:5px 0 0">ระยะทาง '+Number(quote.distance_km).toLocaleString('th-TH',{maximumFractionDigits:1})+' กม. · เวลาวิ่งประมาณ '+Math.ceil(quote.duration_minutes)+' นาที</p></div>';
   else if(!p)html+='<button class="prim" data-action="quote" '+(busy||!config.enabled||!pickup||!destination?'disabled':'')+' style="margin-top:4px">'+(busy?'กำลังตรวจสอบ…':'ดูค่าโดยสาร')+'</button>';
   html+='</section>';
  }
  if(p||!current&&quote)html+='<button class="prim" data-action="request" '+(busy?'disabled':'')+'>'+(p?'ตรวจผลคำขอเดิม / ลองอีกครั้ง':'เรียกรถ')+'</button>';
  if(!current&&!p)html+=rows.filter(terminal).slice(0,5).map(o=>'<p><a href="#ride/'+esc(o.id)+'">'+esc(QueueGoOrderNumber.format(o))+' · '+baht(o.fare)+'</a></p>').join('');
  layout(html,'orders');
  document.querySelectorAll('[data-action]').forEach(button=>button.onclick=()=>action(button.dataset.action));
  const quick=$('ride-use-current');if(quick)quick.onclick=quickCurrentPickup;
  if(!current)renderRoutePreview();
 }
 async function pick(which){
  const old=which==='pickup'?pickup:destination;let point=null,revision=0;
  layout('<div class="pt"><h1>'+(which==='pickup'?'เลือกจุดรับ':'เลือกจุดหมาย')+'</h1></div><section class="card"><div id="ride-location-map" class="mapbox" style="height:240px"></div><button class="lk" id="ride-gps">ใช้ตำแหน่ง GPS ปัจจุบัน</button><textarea id="ride-address" class="in" maxlength="500" placeholder="ที่อยู่หรือจุดสังเกต">'+esc(old?.address||'')+'</textarea><button id="ride-save-location" class="prim">ใช้จุดนี้</button><button id="ride-back" class="lk">กลับ</button><p id="ride-location-error" role="status"></p></section>','orders');
  const u=actor();picker=initLocationPicker('ride-location-map',old?.lat,old?.lng,async(lat,lng)=>{
   point={lat,lng};const version=++revision,field=$('ride-address');if(!field||field.value.trim())return;
   const address=await qtReverseGeocodeCustomer(lat,lng);if(same(u)&&version===revision&&field.isConnected&&!field.value.trim())field.value=address;
  });
  $('ride-gps').onclick=()=>picker?.locate();
  if(which==='pickup'&&!old)setTimeout(()=>picker?.locate(),100);
  $('ride-back').onclick=()=>{picker?.destroy();picker=null;render()};
  $('ride-save-location').onclick=()=>{
   const address=$('ride-address').value.trim();if(!point||address.length<3){$('ride-location-error').textContent='เลือกหมุดและระบุที่อยู่ให้ครบ';return}
   const next={...point,address};if(which==='pickup')pickup=next;else destination=next;quote=null;picker?.destroy();picker=null;render();
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
  if(owner!==u){owner=u;pickup=currentPos?{lat:currentPos.lat,lng:currentPos.lng,address:currentPos.address}:null;destination=quote=selected=null;rows=[];error='';}
  if(id)selected=id;
  try{
   const c=await rpc('queuego_ride_config');if(!same(u)||ticket!==routeVersion)return;config=c||{};
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
