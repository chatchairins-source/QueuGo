/* Passenger flow shares existing auth, Orders, targeted dispatch, location picker and cash. */
(()=>{
 let owner='',pickup=null,destination=null,quote=null,rows=[],selected=null,busy=false,error='',picker=null,previewMap=null,timer=null,config={},supply={nearby_count:0,clusters:[]},supplyAt=0;
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
 function schedule(){clearTimeout(timer);if(document.hidden||!onRide())return;timer=setTimeout(async()=>{if(!busy&&!picker){try{await refresh();if(!rows.some(o=>!terminal(o))&&pickup)await refreshSupply();if(onRide()&&(pending()||rows.some(o=>!terminal(o))||pickup))render()}catch(e){error=e.message;if(onRide())render()}}schedule()},5000)}
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
   for(const cluster of (supply.clusters||[])){
    if(!qgIsValidCoordinate(cluster.latitude,cluster.longitude,true))continue;
    const loc={lat:Number(cluster.latitude),lon:Number(cluster.longitude)};
    const count=Math.max(2,Number(cluster.count||2));
    const marker=new longdo.Marker(loc,{
     title:'Rider พร้อมรับงานโดยประมาณ',
     icon:{html:'<div style="min-width:34px;height:34px;padding:0 7px;border-radius:17px;background:#fff;border:2px solid #e6002d;box-shadow:0 3px 12px #0003;display:flex;align-items:center;justify-content:center;gap:3px;color:#e6002d;font-weight:900;font-size:12px">'+ico('car',16)+'<span>'+count+'</span></div>'},
     weight:longdo.OverlayWeight.Top
    });
    previewMap.Overlays.add(marker);
   }
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
   pickup={lat,lng,address:address||'ตำแหน่งปัจจุบัน'};quote=null;supplyAt=0;await refreshSupply(true);
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
   html+='<div id="ride-route-map" class="mapbox" style="height:245px;border-radius:16px;overflow:hidden;margin-bottom:8px;background:#eef1f4"></div>';
   if(pickup){
    const n=Number(supply.nearby_count||0);
    html+='<div class="sm" style="display:flex;align-items:center;gap:7px;margin:0 2px 12px;color:#59606a"><span style="width:8px;height:8px;border-radius:50%;background:'+(n>0?'#18a875':'#a0a5ad')+'"></span><b>'+(n>0?'มี Rider พร้อมรับงานใกล้จุดรับ '+n+' คน':'ยังไม่พบ Rider พร้อมรับงานในระยะ 8 กม.')+'</b><span style="margin-left:auto;color:#8a9098">ตำแหน่งบนแผนที่เป็นแบบกลุ่มโดยประมาณ</span></div>';
   }
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
  const saved=which==='pickup'?pickup:destination;
  const auto=which==='pickup'&&!saved&&currentPos&&qgIsValidCoordinate(currentPos.lat,currentPos.lng,true)
    ?{lat:Number(currentPos.lat),lng:Number(currentPos.lng),address:currentPos.address||''}:null;
  const reference=which==='destination'&&!saved
    ?(pickup&&qgIsValidCoordinate(pickup.lat,pickup.lng,true)?pickup:(currentPos&&qgIsValidCoordinate(currentPos.lat,currentPos.lng,true)?currentPos:null))
    :null;
  const selectedInitial=saved||auto;
  const initial=selectedInitial||reference;
  const initialAddress=selectedInitial?.address||'';
  let point=selectedInitial?{lat:initial.lat,lng:initial.lng}:null,revision=0;
  const title=which==='pickup'?'เลือกจุดรับ':'เลือกจุดหมาย';
  const subtitle=which==='pickup'
    ?'เลื่อนแผนที่เพื่อเลือกตำแหน่งจุดรับของคุณ'
    :'เลื่อนแผนที่เพื่อเลือกตำแหน่งจุดส่งของคุณ';
  const addressLabel=which==='pickup'?'ที่อยู่จุดรับ':'ที่อยู่จุดส่ง';
  layout(
   '<div style="margin:2px 0 18px;padding:0 2px">'+
    '<h1 style="margin:0;font-size:30px;line-height:1.15;font-weight:900;color:#111827">'+title+'</h1>'+
    '<p style="margin:7px 0 0;color:#8a9099;font-size:15px;font-weight:600">'+subtitle+'</p>'+
   '</div>'+
   '<section class="card" style="padding:14px 14px 16px;border-radius:24px;box-shadow:0 12px 32px rgba(24,32,48,.06);border:1px solid #eceef2;background:#fff">'+
    '<div style="position:relative">'+
     '<div id="ride-location-map" class="mapbox" style="height:330px;border-radius:20px;overflow:hidden;background:#eef1f4;box-shadow:inset 0 0 0 1px rgba(17,24,39,.03)"></div>'+
     '<button type="button" id="ride-select-center" style="position:absolute;right:12px;bottom:12px;min-height:42px;padding:0 14px;border:1px solid #e0e3e8;border-radius:999px;background:rgba(255,255,255,.96);color:#343941;font-weight:800;box-shadow:0 5px 18px rgba(17,24,39,.12)">เลือกตรงกลาง</button>'+
    '</div>'+
    '<button type="button" id="ride-gps" style="width:100%;display:flex;align-items:center;gap:12px;text-align:left;margin-top:14px;padding:12px;border:1px solid #e4e7ec;border-radius:17px;background:#fff;color:#ef233c">'+
     '<span style="width:42px;height:42px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#fff0f2;color:#ef233c;flex:none">'+ico('pin',20)+'</span>'+
     '<span style="min-width:0;flex:1"><b style="display:block;font-size:16px;line-height:1.25">ใช้ตำแหน่ง GPS ปัจจุบัน</b><span style="display:block;margin-top:2px;color:#9aa0aa;font-size:13px">ค้นหาตำแหน่งของคุณบนแผนที่</span></span>'+
     '<span aria-hidden="true" style="font-size:28px;line-height:1;color:#6f7680">›</span>'+
    '</button>'+
    '<div style="margin:14px 2px 7px;color:#8b929d;font-size:13px;font-weight:800">'+addressLabel+'</div>'+
    '<textarea id="ride-address" maxlength="500" placeholder="ที่อยู่หรือจุดสังเกต" style="box-sizing:border-box;width:100%;min-height:82px;resize:none;border:1px solid #dfe3e8;border-radius:17px;background:#fff;padding:14px 14px;font:inherit;font-size:16px;line-height:1.5;color:#16181c;outline:none">'+esc(initialAddress)+'</textarea>'+
    '<div id="ride-picked-status" class="sm" style="margin:7px 2px 0;color:#8a9099">'+(point?'เลือกพิกัดแล้ว':'เลื่อนแผนที่หรือใช้ GPS เพื่อเลือกพิกัด')+'</div>'+
    '<button id="ride-save-location" class="prim" style="min-height:58px;margin-top:14px;border-radius:18px;font-size:18px;font-weight:900;box-shadow:0 10px 24px rgba(239,35,60,.20)">'+ico('pin',20)+'<span style="margin-left:7px">ใช้จุดนี้</span></button>'+
    '<button id="ride-back" type="button" style="min-height:52px;margin-top:10px;padding:0 18px;border:0;border-radius:17px;background:#fff4f5;color:#e3263f;font-weight:900;font-size:16px;display:inline-flex;align-items:center;gap:7px">'+ico('back',18)+'<span>กลับ</span></button>'+
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
  $('ride-select-center').onclick=()=>{
   if(!picker?.selectCenter?.())$('ride-location-error').textContent='ยังอ่านจุดกึ่งกลางแผนที่ไม่ได้ กรุณาลองเลื่อนแผนที่อีกครั้ง';
  };
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
