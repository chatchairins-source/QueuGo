/* QueueGo Express Web: one pickup, one dropoff, trusted road quote, cash and durable request recovery. */
(()=>{
 let owner='',pickup=null,destination=null,quote=null,rows=[],selected=null,busy=false,error='',picker=null,timer=null,trackingMap=null,config={};
 let form={senderName:'',senderPhone:'',receiverName:'',receiverPhone:'',itemType:'พัสดุทั่วไป',weight:'1',size:'medium',vehicleType:'motorcycle',instructions:''};
 const terminal=o=>!o||['completed','cancelled','no_rider_available'].includes(o.status);
 const key=()=> 'qg_customer_express_pending_v1:'+S.get()?.userId;
 const actor=()=>S.get()?.userId||'';
 const onExpress=()=>location.hash.split('/')[0]==='#express';
 const rpc=(name,body={})=>db('rpc/'+name,{method:'POST',body});
 const same=u=>actor()===u&&onExpress();
 const pending=()=>{const raw=localStorage.getItem(key());if(!raw)return null;const p=JSON.parse(raw);if(!/^[0-9a-f-]{36}$/i.test(p.p_request_id)||!/^[0-9a-f-]{36}$/i.test(p.p_quote_id))throw Error('คำขอ Express เดิมอ่านไม่ได้ กรุณาติดต่อฝ่ายช่วยเหลือ');return p};
 const status=o=>({searching_rider:'กำลังหาไรเดอร์',rider_assigned:'ไรเดอร์กำลังไปจุดรับ',picked_up:'รับพัสดุแล้ว',in_progress:o.arrived_dropoff_at?'ถึงจุดส่งแล้ว':'กำลังนำส่ง',completed:'ส่งสำเร็จแล้ว',cancelled:'ยกเลิกแล้ว',no_rider_available:'ยังไม่มีไรเดอร์พร้อมรับงาน'})[o.status]||o.status;
 function stop(){clearTimeout(timer);timer=null;picker?.destroy();picker=null;try{trackingMap?.remove()}catch{}trackingMap=null}
 async function read(id){return (await rpc('queuego_express_state',{p_order_id:id||null})).express_orders||[]}
 async function refresh(){
  const u=actor(),recent=await read();if(!same(u))return;
  if(selected&&!recent.some(r=>r.id===selected)){const old=await read(selected);if(!same(u))return;rows=[...old,...recent]}else rows=recent;
  const active=rows.find(o=>!terminal(o));if(active)selected=active.id;
  const p=pending();if(p){const committed=rows.find(o=>o.id===p.p_request_id)||(await read(p.p_request_id))[0];if(!same(u))return;if(committed){selected=committed.id;rows=[committed,...rows.filter(o=>o.id!==committed.id)];localStorage.removeItem(key());}}
 }
 function schedule(){clearTimeout(timer);if(document.hidden||!onExpress())return;timer=setTimeout(async()=>{if(!busy&&!picker){try{await refresh();if(onExpress()&&(pending()||rows.some(o=>!terminal(o))))render()}catch(e){error=e.message;if(onExpress())render()}}schedule()},5000)}
 function bindForm(){
  const map={expressSenderName:'senderName',expressSenderPhone:'senderPhone',expressReceiverName:'receiverName',expressReceiverPhone:'receiverPhone',expressItemType:'itemType',expressWeight:'weight',expressSize:'size',expressVehicle:'vehicleType',expressInstructions:'instructions'};
  Object.entries(map).forEach(([id,k])=>{const e=$(id);if(e)e.oninput=()=>{form[k]=e.value;quote=null}});
 }
 function mountTracking(o){
  try{trackingMap?.remove()}catch{}trackingMap=null;
  const host=$('express-live-map'),rLat=Number(o?.rider_latitude),rLng=Number(o?.rider_longitude);if(!host||!Number.isFinite(rLat)||!Number.isFinite(rLng)||typeof maplibregl==='undefined')return;
  trackingMap=new maplibregl.Map({container:host,style:qgQueueGoPickerStyle(),center:[rLng,rLat],zoom:14,minZoom:5,maxZoom:19,attributionControl:false,dragRotate:false,pitchWithRotate:false});
  trackingMap.touchZoomRotate.disableRotation();trackingMap.addControl(new maplibregl.NavigationControl({showCompass:false,visualizePitch:false}),'top-left');
  const pts=[
   [Number(o.pickup_longitude),Number(o.pickup_latitude),'#232323'],
   [Number(o.delivery_longitude),Number(o.delivery_latitude),'#777777'],
   [rLng,rLat,'#e6002d']
  ].filter(x=>Number.isFinite(x[0])&&Number.isFinite(x[1]));
  trackingMap.on('load',()=>{if(!trackingMap)return;const bounds=new maplibregl.LngLatBounds();pts.forEach(p=>{new maplibregl.Marker({color:p[2],scale:.85}).setLngLat([p[0],p[1]]).addTo(trackingMap);bounds.extend([p[0],p[1]])});if(pts.length>1)trackingMap.fitBounds(bounds,{padding:38,maxZoom:15,duration:0})});
 }
 function render(){
  if(!onExpress())return;const current=rows.find(o=>o.id===selected),p=pending();stop();
  let html='<div class="pt"><button class="back" onclick="go(\'home\')">'+ico('back')+'</button><h1>QueueGo Express</h1></div>';
  html+='<p class="sm">ส่งด่วน 1 จุดรับ · 1 จุดส่ง · ชำระเงินสด</p>';
  if(error)html+='<p role="alert">'+esc(error)+'</p><button class="lk" data-action="retry" '+(busy?'disabled':'')+'>ลองโหลดอีกครั้ง</button>';
  if(current){
   html+='<section class="card"><h2>'+esc(QueueGoOrderNumber.format(current))+'</h2><b>'+esc(status(current))+'</b>';
   html+='<p>รับพัสดุ: '+esc(current.pickup_address||'จุดรับ')+'</p><p>ส่งพัสดุ: '+esc(current.delivery_address||'จุดส่ง')+'</p>';
   html+='<p>'+esc(current.item_type||'พัสดุ')+' · '+Number(current.weight_kg||0).toLocaleString('th-TH')+' กก. · '+esc(current.item_size||'')+'</p>';
   html+='<p>ผู้ส่ง: '+esc(current.sender_name||'-')+' · ผู้รับ: '+esc(current.receiver_name||'-')+'</p>';
   html+='<p>ค่าส่งเงินสด <b>'+baht(current.fare)+'</b></p><p>เส้นทาง '+Number(current.distance_km||0).toLocaleString('th-TH',{maximumFractionDigits:1})+' กม. · ประมาณ '+Math.ceil(Number(current.duration_minutes||0))+' นาที</p>';
   html+='<p>ไรเดอร์: '+esc(current.rider_name||'กำลังหา')+'</p>';
   if(!terminal(current)&&current.rider_id)html+='<button class="lk" onclick="go(\'order-chat/'+esc(current.id)+'\')">ติดต่อไรเดอร์</button>';
   if(!terminal(current)&&Number.isFinite(Number(current.rider_latitude))&&Number.isFinite(Number(current.rider_longitude)))html+='<div id="express-live-map" class="mapbox" style="height:220px"></div>';
   if(current.status==='searching_rider'&&!current.rider_id&&!p)html+='<button class="lk" data-action="cancel" '+(busy?'disabled':'')+'>ยกเลิกงาน Express</button>';
   if(terminal(current)&&!p)html+='<button class="prim" data-action="new">ส่งพัสดุรายการใหม่</button>';
   html+='</section>';
  }else{
   if(!config.enabled||!config.configured)html+='<p class="empty">บริการ Express ยังไม่เปิดให้ใช้ในขณะนี้</p>';
   html+='<section class="card">';
   html+='<button class="lk" data-action="pickup" '+(busy||p||!config.enabled?'disabled':'')+'>จุดรับ: '+esc(pickup?.address||'เลือกบนแผนที่')+'</button><br>';
   html+='<button class="lk" data-action="destination" '+(busy||p||!config.enabled?'disabled':'')+'>จุดส่ง: '+esc(destination?.address||'เลือกบนแผนที่')+'</button>';
   html+='<label>ชื่อผู้ส่ง</label><input id="expressSenderName" class="in" maxlength="120" value="'+esc(form.senderName)+'">';
   html+='<label>เบอร์ผู้ส่ง</label><input id="expressSenderPhone" class="in" maxlength="30" value="'+esc(form.senderPhone)+'">';
   html+='<label>ชื่อผู้รับ</label><input id="expressReceiverName" class="in" maxlength="120" value="'+esc(form.receiverName)+'">';
   html+='<label>เบอร์ผู้รับ</label><input id="expressReceiverPhone" class="in" maxlength="30" value="'+esc(form.receiverPhone)+'">';
   html+='<label>ประเภทสิ่งของ</label><input id="expressItemType" class="in" maxlength="80" value="'+esc(form.itemType)+'">';
   html+='<label>น้ำหนักโดยประมาณ (กก.)</label><input id="expressWeight" class="in" inputmode="decimal" value="'+esc(form.weight)+'">';
   html+='<label>ขนาดพัสดุ</label><select id="expressSize" class="in"><option value="small" '+(form.size==='small'?'selected':'')+'>เล็ก</option><option value="medium" '+(form.size==='medium'?'selected':'')+'>กลาง</option><option value="large" '+(form.size==='large'?'selected':'')+'>ใหญ่</option></select>';
   html+='<label>ประเภทรถ</label><select id="expressVehicle" class="in"><option value="motorcycle" '+(form.vehicleType==='motorcycle'?'selected':'')+'>มอเตอร์ไซค์</option><option value="car" '+(form.vehicleType==='car'?'selected':'')+'>รถยนต์</option><option value="saleng" '+(form.vehicleType==='saleng'?'selected':'')+'>ซาเล้ง</option></select>';
   html+='<label>หมายเหตุถึงไรเดอร์</label><textarea id="expressInstructions" class="ta" maxlength="500">'+esc(form.instructions)+'</textarea>';
   if(quote)html+='<p>ค่าส่งเงินสด <b>'+baht(quote.fare)+'</b></p><p>เส้นทาง '+Number(quote.distance_km).toLocaleString('th-TH',{maximumFractionDigits:1})+' กม. · ประมาณ '+Math.ceil(quote.duration_minutes)+' นาที</p>';
   else if(!p)html+='<button class="prim" data-action="quote" '+(busy||!config.enabled||!pickup||!destination?'disabled':'')+'>ดูค่าส่ง</button>';
   html+='</section>';
  }
  if(p||!current&&quote)html+='<button class="prim" data-action="request" '+(busy?'disabled':'')+'>'+(p?'ตรวจผลคำขอเดิม / ลองอีกครั้ง':'เรียกไรเดอร์ส่งด่วน')+'</button>';
  if(!current&&!p)html+=rows.filter(terminal).slice(0,5).map(o=>'<p><a href="#express/'+esc(o.id)+'">'+esc(QueueGoOrderNumber.format(o))+' · '+baht(o.fare)+'</a></p>').join('');
  layout(html,'orders');bindForm();document.querySelectorAll('[data-action]').forEach(button=>button.onclick=()=>action(button.dataset.action));if(current)mountTracking(current);
 }
 async function pick(which){
  const old=which==='pickup'?pickup:destination;let point=old?{lat:old.lat,lng:old.lng}:null,revision=0;
  layout('<div class="pt"><h1>'+(which==='pickup'?'เลือกจุดรับพัสดุ':'เลือกจุดส่งพัสดุ')+'</h1></div><section class="card"><div id="express-location-map" class="mapbox" style="height:240px"></div><button class="lk" id="express-gps">ใช้ตำแหน่ง GPS ปัจจุบัน</button><textarea id="express-address" class="in" maxlength="500" placeholder="ที่อยู่หรือจุดสังเกต">'+esc(old?.address||'')+'</textarea><button id="express-save-location" class="prim">ใช้จุดนี้</button><button id="express-back" class="lk">กลับ</button><p id="express-location-error" role="status"></p></section>','orders');
  const u=actor();picker=initLocationPicker('express-location-map',old?.lat,old?.lng,async(lat,lng)=>{point={lat,lng};const version=++revision,field=$('express-address');if(!field||field.value.trim())return;const address=await qtReverseGeocodeCustomer(lat,lng);if(same(u)&&version===revision&&field.isConnected&&!field.value.trim())field.value=address});
  $('express-gps').onclick=()=>picker?.locate();$('express-back').onclick=()=>{picker?.destroy();picker=null;render()};
  $('express-save-location').onclick=()=>{const address=$('express-address').value.trim();if(!point||address.length<3){$('express-location-error').textContent='เลือกหมุดและระบุที่อยู่ให้ครบ';return}const next={...point,address};if(which==='pickup')pickup=next;else destination=next;quote=null;picker?.destroy();picker=null;render()};
 }
 function formPayload(){
  const weight=Number(form.weight);
  if(!pickup||!destination)throw Error('เลือกจุดรับและจุดส่งให้ครบ');
  if(form.senderName.trim().length<1||form.senderPhone.trim().length<5||form.receiverName.trim().length<1||form.receiverPhone.trim().length<5||form.itemType.trim().length<1||!Number.isFinite(weight)||weight<=0)throw Error('กรอกข้อมูลผู้ส่ง ผู้รับ และพัสดุให้ครบ');
  return {pickup,destination,sender:{name:form.senderName.trim(),phone:form.senderPhone.trim()},receiver:{name:form.receiverName.trim(),phone:form.receiverPhone.trim()},item:{type:form.itemType.trim(),weightKg:weight,size:form.size,vehicleType:form.vehicleType,instructions:form.instructions.trim()}};
 }
 async function action(kind){
  if(busy)return;if(kind==='retry')return V.express(selected);if(kind==='pickup'||kind==='destination')return pick(kind);if(kind==='new'){selected=null;quote=null;error='';return render()}
  const u=actor();busy=true;error='';render();
  try{
   if(kind==='quote'){
    const accessToken=await token();if(!same(u))return;
    const res=await fetch(CFG.URL+'/functions/v1/queuego-express-quote',{method:'POST',headers:hdr(accessToken),body:JSON.stringify(formPayload()),signal:AbortSignal.timeout(30000)});
    const data=await res.json();if(!res.ok)throw Error(data.error||'ยังประเมินราคา Express ไม่ได้');if(!same(u))return;quote=data;
   }else if(kind==='request'){
    let p=pending();if(!p){if(!quote)throw Error('ตรวจค่าส่งก่อนเรียกไรเดอร์');p={p_request_id:uuid(),p_quote_id:quote.quote_id};localStorage.setItem(key(),JSON.stringify(p))}
    const result=await rpc('queuego_request_express',p);if(!same(u))return;if(result?.id!==p.p_request_id)throw Error('ยังยืนยันเลขงาน Express ไม่ได้');selected=result.id;
    try{localStorage.removeItem(key())}catch{}quote=null;await refresh();
   }else if(kind==='cancel'){
    const o=rows.find(r=>r.id===selected);if(!o||o.rider_id||o.status!=='searching_rider')throw Error('งานนี้ยกเลิกไม่ได้');await rpc('qg_customer_cancel_order',{p_order_id:o.id});if(same(u))await refresh();
   }
  }catch(e){
   if(same(u)){error=e.message;if(kind==='request'){try{const p=pending(),committed=p&&(await read(p.p_request_id))[0];if(!same(u))return;if(committed){selected=committed.id;rows=[committed,...rows.filter(o=>o.id!==committed.id)];localStorage.removeItem(key());error='';quote=null}}catch{}}}
  }finally{busy=false;if(same(u))render()}
 }
 V.express=async(id,ticket=routeVersion)=>{
  const u=actor();if(!u)return go('login');stop();if(owner!==u){owner=u;pickup=currentPos?{lat:currentPos.lat,lng:currentPos.lng,address:currentPos.address}:null;destination=quote=selected=null;rows=[];error='';}
  if(id)selected=id;error='';
  try{const c=await rpc('queuego_express_config');if(!same(u)||ticket!==routeVersion)return;config=c||{};await refresh();if(!same(u)||ticket!==routeVersion)return}catch(e){if(!same(u)||ticket!==routeVersion)return;config={enabled:false,configured:false};error='โหลดข้อมูล Express ไม่สำเร็จ กรุณาลองอีกครั้ง'}
  render();schedule();
 };
 const ordinary=V.order;
 V.order=async(id,ticket=routeVersion)=>{
  const u=actor(),o=(await db('orders?select=fulfillment_vertical&id=eq.'+encodeURIComponent(id)+'&customer_id=eq.'+encodeURIComponent(u)+'&limit=1'))?.[0];
  if(actor()!==u||ticket!==routeVersion)return;
  if(o?.fulfillment_vertical==='express')return go('express/'+id);
  return ordinary(id,ticket);
 };
 addEventListener('hashchange',stop);addEventListener('pagehide',stop);
 document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();else if(onExpress())route()});
 addEventListener('online',()=>{if(onExpress())route()});
})();