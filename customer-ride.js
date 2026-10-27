/* Passenger flow shares existing auth, Orders, targeted dispatch, location picker and cash. */
(()=>{
 let owner='',pickup=null,destination=null,quote=null,rows=[],selected=null,busy=false,error='',picker=null,timer=null,supplyTimer=null,trackingMap=null,config={},supply={nearby_count:0,clusters:[]};
 const terminal=o=>!o||['completed','cancelled','no_rider_available'].includes(o.status);
 const key=()=> 'qg_customer_ride_pending_v1:'+S.get()?.userId;
 const actor=()=>S.get()?.userId||'';
 const onRide=()=>location.hash.split('/')[0]==='#ride';
 const rpc=(name,body={})=>db('rpc/'+name,{method:'POST',body});
 const pending=()=>{const raw=localStorage.getItem(key());if(!raw)return null;const p=JSON.parse(raw);if(!/^[0-9a-f-]{36}$/i.test(p.p_request_id)||!/^[0-9a-f-]{36}$/i.test(p.p_quote_id))throw Error('คำขอเดิมอ่านไม่ได้ กรุณาติดต่อฝ่ายช่วยเหลือ');return p};
 const same=u=>actor()===u&&onRide();
 const status=o=>o.status==='rider_assigned'&&o.arrived_pickup_at?'คนขับถึงจุดรับแล้ว':o.status==='in_progress'&&o.arrived_dropoff_at?'ถึงจุดหมายแล้ว':({searching_rider:'กำลังหาคนขับ',rider_assigned:'คนขับกำลังไปจุดรับ',in_progress:'กำลังเดินทาง',completed:'จบการเดินทางแล้ว',cancelled:'ยกเลิกแล้ว',no_rider_available:'ยังไม่มีคนขับพร้อมรับเที่ยว'})[o.status]||o.status;
 function stop(){clearTimeout(timer);timer=null;clearTimeout(supplyTimer);supplyTimer=null;picker?.destroy();picker=null;try{trackingMap?.remove()}catch{}trackingMap=null}
 async function read(id){return (await rpc('queuego_ride_state',{p_order_id:id||null})).rides||[]}
 async function refresh(){
  const u=actor(),recent=await read();if(!same(u))return;
  if(selected&&!recent.some(r=>r.id===selected)){const old=await read(selected);if(!same(u))return;rows=[...old,...recent]}else rows=recent;
  const active=rows.find(o=>!terminal(o));if(active)selected=active.id;
  const p=pending();if(p){const committed=rows.find(o=>o.id===p.p_request_id)||(await read(p.p_request_id))[0];if(!same(u))return;if(committed){selected=committed.id;rows=[committed,...rows.filter(o=>o.id!==committed.id)];localStorage.removeItem(key());}}
 }
 function supplyAllowed(){return !rows.some(o=>o.id===selected&&!terminal(o))&&!picker&&!pending()&&config.enabled&&config.configured&&pickup&&point(pickup.lat,pickup.lng)}
 function point(lat,lng){const y=Number(lat),x=Number(lng);return Number.isFinite(y)&&Number.isFinite(x)&&y>=5&&y<=21&&x>=97&&x<=106?[x,y]:null}
 function rideMapAvailable(o){return !!o&&!terminal(o)&&[['pickup_latitude','pickup_longitude'],['delivery_latitude','delivery_longitude'],['rider_latitude','rider_longitude']].some(keys=>point(o[keys[0]],o[keys[1]]))}
 function parseSupply(raw){const clusters=Array.isArray(raw?.clusters)?raw.clusters.slice(0,12).map(row=>{const xy=point(row?.latitude,row?.longitude),count=Number(row?.count);return xy&&Number.isInteger(count)&&count>=2&&row?.approximate===true?{xy,count}:null}).filter(Boolean):[];const count=Number(raw?.nearby_count);return{nearby_count:Number.isFinite(count)?Math.max(0,Math.floor(count)):0,clusters}}
 async function refreshSupply(){const u=actor();if(!same(u)||!supplyAllowed())return false;const chosen=pickup;try{const result=await rpc('queuego_customer_ride_supply',{p_lat:Number(chosen.lat),p_lng:Number(chosen.lng)});if(!same(u)||!supplyAllowed()||pickup!==chosen)return false;supply=parseSupply(result);return true}catch(_){if(same(u)&&supplyAllowed()&&pickup===chosen)supply={nearby_count:0,clusters:[]};return false}}
 function scheduleSupply(){clearTimeout(supplyTimer);supplyTimer=null;if(document.hidden||!onRide()||!supplyAllowed())return;supplyTimer=setTimeout(async()=>{if(await refreshSupply())render();scheduleSupply()},15000)}
 function schedule(){clearTimeout(timer);timer=null;if(document.hidden||!onRide())return;timer=setTimeout(async()=>{if(!busy&&!picker){try{const previous=rows.find(o=>o.id===selected);await refresh();if(!same(actor()))return;const current=rows.find(o=>o.id===selected);if(onRide()&&(pending()||current&&!previous||previous&&current&&(previous.status!==current.status||previous.rider_id!==current.rider_id||previous.arrived_pickup_at!==current.arrived_pickup_at||previous.arrived_dropoff_at!==current.arrived_dropoff_at)||previous&&!current))render();else if(current&&!terminal(current))syncTrackingMap(current)}catch(e){error=e.message;if(onRide())render()}}schedule();scheduleSupply()},5000)}
 function mountTrackingMap(current){
  try{trackingMap?.remove()}catch{}trackingMap=null;
  const host=typeof $==='function'?$('ride-live-map'):document.getElementById?.('ride-live-map');if(!host||typeof maplibregl==='undefined')return;
  const isCurrent=!!current,points=isCurrent?[
   {xy:point(current.pickup_latitude,current.pickup_longitude),color:'#1677ff',label:'รับ'},
   {xy:point(current.delivery_latitude,current.delivery_longitude),color:'#e6002d',label:'ส่ง'},
   {xy:point(current.rider_latitude,current.rider_longitude),color:'#e6002d',label:'Rider'}
  ]:([
   {xy:point(pickup?.lat,pickup?.lng),color:'#1677ff',label:'รับ'},
   {xy:point(destination?.lat,destination?.lng),color:'#e6002d',label:'ส่ง'}
  ].concat(supply.clusters.map(c=>({xy:c.xy,color:'#e6002d',label:String(c.count),count:c.count})))).filter(row=>row.xy);
  const valid=points.filter(row=>row.xy);const center=valid[0]?.xy||[100.5,15.5];
  try{
   trackingMap=new maplibregl.Map({container:host,style:qgQueueGoPickerStyle(),center,zoom:valid.length>1?12:14,minZoom:5,maxZoom:19,attributionControl:false,dragRotate:false,pitchWithRotate:false});
   trackingMap.touchZoomRotate.disableRotation();trackingMap.addControl(new maplibregl.NavigationControl({showCompass:false,visualizePitch:false}),'top-left');
   const map=trackingMap;
   map.on('load',()=>{
    if(trackingMap!==map)return;
    const bounds=new maplibregl.LngLatBounds();
    valid.forEach(row=>{
     let marker;
     if(Number.isInteger(row.count)){
      const badge=document.createElement('div');badge.textContent=String(row.count);badge.setAttribute('aria-label',row.count+' Rider โดยประมาณ');
      Object.assign(badge.style,{width:'34px',height:'34px',borderRadius:'50%',border:'2px solid #e6002d',background:'#fff',color:'#e6002d',display:'grid',placeItems:'center',fontWeight:'700',fontSize:'13px',boxShadow:'0 2px 8px #0003'});
      marker=new maplibregl.Marker({element:badge,anchor:'center'}).setLngLat(row.xy).addTo(map);
     }else marker=new maplibregl.Marker({color:row.color,scale:.85}).setLngLat(row.xy).addTo(map);
     row.marker=marker;bounds.extend(row.xy);
    });
    map._qgRideMarkers=valid;
    if(valid.length>1)map.fitBounds(bounds,{padding:38,maxZoom:15,duration:0});
    else if(valid.length===1)map.setCenter(valid[0].xy);
   });
  }catch(_){try{trackingMap?.remove()}catch{}trackingMap=null}
 }
 function syncTrackingMap(current){
  if(!trackingMap)return;const host=typeof $==='function'?$('ride-live-map'):document.getElementById?.('ride-live-map');if(!host||!trackingMap.getContainer||trackingMap.getContainer()!==host){mountTrackingMap(current);return}
  const desired=current?[
   {xy:point(current.pickup_latitude,current.pickup_longitude),color:'#1677ff',label:'รับ'},
   {xy:point(current.delivery_latitude,current.delivery_longitude),color:'#e6002d',label:'ส่ง'},
   {xy:point(current.rider_latitude,current.rider_longitude),color:'#e6002d',label:'Rider'}
  ].filter(row=>row.xy):[];
  const markers=trackingMap._qgRideMarkers||[];
  if(desired.length!==markers.length){mountTrackingMap(current);return}
  desired.forEach((row,index)=>{const old=markers[index];if(old?.marker)old.marker.setLngLat(row.xy)});
 }
 function render(){
  if(!onRide())return;const current=rows.find(o=>o.id===selected),p=pending();
  let html='<div class="pt"><button class="back" onclick="go(\'home\')">'+ico('back')+'</button><h1>รับส่งผู้โดยสาร</h1></div>';
  if(error)html+='<p role="alert">'+esc(error)+'</p><button class="lk" data-action="retry" '+(busy?'disabled':'')+'>ลองโหลดอีกครั้ง</button>';
  if(current){
   html+='<section class="card"><h2>'+esc(QueueGoOrderNumber.format(current))+'</h2><b>'+esc(status(current))+'</b><p>จุดรับ: '+esc(current.pickup_address)+'</p><p>จุดหมาย: '+esc(current.delivery_address)+'</p><p>ค่าโดยสารเงินสด <b>'+baht(current.fare)+'</b></p><p>คนขับ: '+esc(current.rider_name||'กำลังหา')+'</p><p>เส้นทางที่ประเมิน '+Number(current.distance_km).toLocaleString('th-TH')+' กม. · เวลาวิ่งประมาณ '+Math.ceil(current.duration_minutes)+' นาที ไม่รวมเวลารอคนขับ</p>'+(rideMapAvailable(current)?'<div id="ride-live-map" class="mapbox" style="height:220px"></div>':'');
   if(!terminal(current)&&current.rider_id)html+='<button class="lk" onclick="go(\'order-chat/'+esc(current.id)+'\')">ติดต่อคนขับ</button>';
   if(current.status==='searching_rider'&&!current.rider_id&&!p)html+='<button class="lk" data-action="cancel" '+(busy?'disabled':'')+'>ยกเลิกการเรียกรถ</button>';
   if(terminal(current)&&!p)html+='<button class="prim" data-action="new">เรียกรถเที่ยวใหม่</button>';
   html+='</section>';
  }else{
   html+='<section class="card">'+(!config.enabled||!config.configured?'<p>บริการเรียกรถยังไม่เปิดให้ใช้ในขณะนี้</p>':'')+'<div id="ride-live-map" class="mapbox" style="height:245px"></div>'+(pickup?'<p id="ride-supply-count">'+(supply.nearby_count>0?'มี Rider พร้อมรับงานใกล้จุดรับ '+supply.nearby_count+' คน':'ยังไม่พบ Rider พร้อมรับงานในระยะ 8 กม.')+'</p><small>ตำแหน่งบนแผนที่เป็นแบบกลุ่มโดยประมาณ</small>':'')+'<button class="lk" data-action="pickup" '+(busy||p||!config.enabled?'disabled':'')+'>จุดรับ: '+esc(pickup?.address||'เลือกบนแผนที่')+'</button><br><button class="lk" data-action="destination" '+(busy||p||!config.enabled?'disabled':'')+'>จุดหมาย: '+esc(destination?.address||'เลือกบนแผนที่')+'</button>';
   if(quote)html+='<p>ค่าโดยสารเงินสด <b>'+baht(quote.fare)+'</b></p><p>เวลาวิ่งประมาณ '+Math.ceil(quote.duration_minutes)+' นาที ไม่รวมเวลารอคนขับ</p>';
   else if(!p)html+='<button class="prim" data-action="quote" '+(busy||!config.enabled||!pickup||!destination?'disabled':'')+'>ดูค่าโดยสาร</button>';
   html+='</section>';
  }
  if(p||!current&&quote)html+='<button class="prim" data-action="request" '+(busy?'disabled':'')+'>'+(p?'ตรวจผลคำขอเดิม / ลองอีกครั้ง':'เรียกรถ')+'</button>';
  if(!current&&!p)html+=rows.filter(terminal).slice(0,5).map(o=>'<p><a href="#ride/'+esc(o.id)+'">'+esc(QueueGoOrderNumber.format(o))+' · '+baht(o.fare)+'</a></p>').join('');
  layout(html,'orders');mountTrackingMap(rideMapAvailable(current)?current:null);document.querySelectorAll('[data-action]').forEach(button=>button.onclick=()=>action(button.dataset.action));
 }
 async function pick(which){
  const old=which==='pickup'?pickup:destination;let point=null,revision=0;
  try{trackingMap?.remove()}catch{}trackingMap=null;
  layout('<div class="pt"><h1>'+(which==='pickup'?'เลือกจุดรับ':'เลือกจุดหมาย')+'</h1></div><section class="card"><div id="ride-location-map" class="mapbox" style="height:240px"></div><button class="lk" id="ride-gps">ใช้ตำแหน่ง GPS ปัจจุบัน</button><textarea id="ride-address" class="in" maxlength="500" placeholder="ที่อยู่หรือจุดสังเกต">'+esc(old?.address||'')+'</textarea><button id="ride-save-location" class="prim">ใช้จุดนี้</button><button id="ride-back" class="lk">กลับ</button><p id="ride-location-error" role="status"></p></section>','orders');
  const u=actor();picker=initLocationPicker('ride-location-map',old?.lat,old?.lng,async(lat,lng)=>{
   point={lat,lng};const version=++revision,field=$('ride-address');if(!field||field.value.trim())return;
   const address=await qtReverseGeocodeCustomer(lat,lng);if(same(u)&&version===revision&&field.isConnected&&!field.value.trim())field.value=address;
  });
  $('ride-gps').onclick=()=>picker?.locate();
  $('ride-back').onclick=()=>{picker?.destroy();picker=null;render()};
  $('ride-save-location').onclick=()=>{
   const address=$('ride-address').value.trim();if(!point||address.length<3){$('ride-location-error').textContent='เลือกหมุดและระบุที่อยู่ให้ครบ';return}
   const next={...point,address};if(which==='pickup'){pickup=next;supply={nearby_count:0,clusters:[]}}else destination=next;quote=null;try{trackingMap?.remove()}catch{}trackingMap=null;picker?.destroy();picker=null;render();if(which==='pickup'){refreshSupply().then(ok=>{if(ok&&same(actor()))render();scheduleSupply()});scheduleSupply()}
  };
 }
 async function action(kind){
  if(busy)return;if(kind==='retry')return V.ride(selected);if(kind==='pickup'||kind==='destination')return pick(kind);
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
  }finally{busy=false;if(same(u)){render();scheduleSupply()}}
 }
 V.ride=async(id,ticket=routeVersion)=>{
  const u=actor();if(!u)return go('login');stop();
  if(owner!==u){owner=u;pickup=currentPos?{lat:currentPos.lat,lng:currentPos.lng,address:currentPos.address}:null;destination=quote=selected=null;rows=[];error='';}
  if(id)selected=id;
  error='';
  try{
   const c=await rpc('queuego_ride_config');if(!same(u)||ticket!==routeVersion)return;config=c||{};
   await refresh();if(!same(u)||ticket!==routeVersion)return;
  }catch(e){
   if(!same(u)||ticket!==routeVersion)return;
   config={enabled:false,configured:false};
   error='โหลดข้อมูลเที่ยวไม่สำเร็จ กรุณาลองอีกครั้ง';
  }
  render();schedule();if(supplyAllowed()){refreshSupply().then(ok=>{if(ok&&same(u)){render();scheduleSupply()}});scheduleSupply()}
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
