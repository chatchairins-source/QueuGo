(()=>{
'use strict';
const esc=v=>typeof escapeHtml==='function'?escapeHtml(v):String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let state={feature:false,data:null,orders:[],busy:false,editing:null};
async function request(path,options={}){
  const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
  return qtSupabaseTable(path,{...options,accessToken:token});
}
async function rpc(name,body={}){return request('rpc/'+name,{method:'POST',body})}
const money=v=>Number(v||0).toLocaleString('th-TH',{minimumFractionDigits:0,maximumFractionDigits:2});
const unit=t=>({per_kg:'ต่อกิโลกรัม',per_item:'ต่อชิ้น',per_set:'ต่อชุด',fixed:'ราคาคงที่'})[t]||t;
const status=s=>({pending:'รอร้านรับ',accepted:'รอร้านจัดพนักงาน',pickup_assigned:'พนักงานของร้านไปรับผ้า',picked_up:'กำลังนำผ้ามาร้าน',at_hub:'ผ้าถึงร้าน',washing:'กำลังซัก',ready_return:'พร้อมส่งคืน',return_assigned:'ร้านเตรียมส่งคืน',out_for_return:'กำลังส่งคืน',completed:'เสร็จแล้ว',cancelled:'ยกเลิก'})[s]||s;
async function feature(){try{return (await rpc('queuego_feature_enabled',{p_feature:'laundry'}))===true}catch(e){return false}}
async function loadState(){
  const data=await rpc('queuego_laundry_merchant_state',{});
  let orders=[];
  if(data?.hub?.id){
    orders=await request('laundry_orders?select=id,order_number,status,service_id,service_type,service_name_snapshot,pricing_type_snapshot,unit_price_snapshot,estimated_quantity,actual_quantity,estimated_amount,final_amount,delivery_fee_total_snapshot,estimated_total_amount,final_total_amount,pickup_address,pickup_latitude,pickup_longitude,pickup_staff_name,return_staff_name,note,created_at,updated_at&hub_id=eq.'+encodeURIComponent(data.hub.id)+'&order=created_at.desc&limit=100');
  }
  if(data?.hub?.id){
    const contacts=await rpc('queuego_laundry_shop_contacts',{p_hub_id:data.hub.id});
    const byOrder=new Map((Array.isArray(contacts)?contacts:[]).map(c=>[String(c.order_id),c]));
    orders=(Array.isArray(orders)?orders:[]).map(o=>({...o,...byOrder.get(String(o.id))}));
  }
  state.data=data||{};state.orders=Array.isArray(orders)?orders:[];state.feature=await feature();
}
function contactHtml(o){
  let html=o.pickup_staff_name?'<small>ผู้รับผ้า: '+esc(o.pickup_staff_name)+'</small>':'';
  if(o.return_staff_name)html+='<small>ผู้ส่งคืน: '+esc(o.return_staff_name)+'</small>';
  if(['completed','cancelled'].includes(o.status))return html;
  if(o.customer_name)html+='<small>ลูกค้า: '+esc(o.customer_name)+'</small>';
  if(o.customer_phone)html+='<a class="qgm-outline" href="tel:'+esc(String(o.customer_phone).replace(/[^0-9+]/g,''))+'">โทรลูกค้า</a>';
  if(o.pickup_latitude!=null&&o.pickup_longitude!=null&&Number.isFinite(Number(o.pickup_latitude))&&Number.isFinite(Number(o.pickup_longitude)))
    html+='<a class="qgm-outline" target="_blank" rel="noopener" href="https://www.google.com/maps/dir/?api=1&destination='+Number(o.pickup_latitude)+','+Number(o.pickup_longitude)+'">นำทาง</a>';
  return html;
}
function actionHtml(o){
  if(o.status==='pending'&&!o.service_id)return '<button data-lo-legacy="'+esc(o.id)+'">ผูกบริการ/ราคาก่อนรับ</button><button class="danger" data-lo-act="cancel" data-id="'+esc(o.id)+'">ปฏิเสธ</button>';
  if(o.status==='pending')return '<button data-lo-act="accept" data-id="'+esc(o.id)+'">รับคำขอ</button><button class="danger" data-lo-act="cancel" data-id="'+esc(o.id)+'">ปฏิเสธ</button>';
  if(['accepted','ready_return','return_assigned'].includes(o.status)){
    const pickup=o.status==='accepted',action=pickup?'start_pickup':'start_return';
    return '<label>ชื่อพนักงาน'+(pickup?'รับผ้า':'ส่งคืน')+'<input data-lo-staff="'+esc(o.id)+'" maxlength="100"></label><button data-lo-act="'+action+'" data-id="'+esc(o.id)+'">'+(pickup?'มอบหมาย · กำลังไปรับ':'มอบหมาย · กำลังส่งคืน')+'</button>';
  }
  if(o.status==='pickup_assigned')return '<button data-lo-act="pickup_collected" data-id="'+esc(o.id)+'">รับผ้าจากลูกค้าแล้ว</button>';
  if(o.status==='picked_up')return '<button data-lo-act="receive_at_shop" data-id="'+esc(o.id)+'">ผ้าถึงร้านแล้ว</button>';
  if(o.status==='out_for_return')return '<button data-lo-act="complete_return" data-id="'+esc(o.id)+'">ส่งคืนลูกค้าสำเร็จ</button>';
  if(o.status==='at_hub')return '<button data-lo-act="start_washing" data-id="'+esc(o.id)+'">เริ่มซัก/ทำความสะอาด</button>';
  if(o.status==='washing')return '<button data-lo-act="ready_return" data-id="'+esc(o.id)+'">งานเสร็จ / พร้อมส่งคืน</button>';
  return '';
}
function serviceFormHtml(){
  return '<div id="qgl-service-form" class="qgl-service-form" hidden>'+
    '<input id="qgl-service-id" type="hidden">'+
    '<div class="qgl-grid">'+
      '<label>ชื่อบริการ<input id="qgl-service-name" maxlength="120" placeholder="เช่น ซัก + อบ + พับ"></label>'+
      '<label>คิดราคา<select id="qgl-service-type"><option value="per_kg">ต่อกิโลกรัม</option><option value="per_item">ต่อชิ้น</option><option value="per_set">ต่อชุด</option><option value="fixed">ราคาคงที่</option></select></label>'+
      '<label>ราคา (บาท)<input id="qgl-service-price" type="number" min="0" step="0.01"></label>'+
      '<label>เวลาประมาณ (นาที)<input id="qgl-service-minutes" type="number" min="1" max="10080" step="1"></label>'+
    '</div>'+
    '<label>รายละเอียด<textarea id="qgl-service-desc" maxlength="500" rows="3"></textarea></label>'+
    '<label class="qgl-check"><input id="qgl-service-active" type="checkbox" checked> เปิดขายบริการนี้</label>'+
    '<div class="qgl-actions"><button class="qgm-primary" id="qgl-save-service" type="button">บันทึกบริการ</button><button class="qgm-outline" id="qgl-cancel-service" type="button">ยกเลิก</button></div>'+
  '</div>';
}
function render(){
  const d=state.data||{},hub=d.hub,settings=d.settings||{},services=Array.isArray(d.services)?d.services:[],orders=state.orders||[];
  let body='<div class="qgl-head"><button class="qgm-iconbtn" type="button" onclick="navigate(\'shop-modules\')">←</button><div><h2>ฝากซัก</h2><small>ร้านจัดพนักงานรับผ้าและส่งคืนเอง</small></div></div>';
  if(!state.feature)body+='<div class="qgl-banner">ระบบฝากซักยังปิดจาก Admin สำหรับลูกค้า แต่ร้านสามารถตั้งค่าให้พร้อมก่อนเปิดจริงได้</div>';
  if(!hub){
    body+='<section class="qgm-card qgl-card"><h3>เริ่มใช้ฝากซัก</h3><p>สร้าง Laundry Hub จากร้านปัจจุบัน โดยใช้ข้อมูลร้านจริงที่มีอยู่แล้ว</p><button class="qgm-primary" id="qgl-setup" type="button">เปิดตั้งค่าฝากซักของร้านนี้</button></section>';
    window.qtShopLayout('ฝากซัก',body,'shop-profile');bind();return;
  }
  body+='<section class="qgm-card qgl-card"><h3>ค่าบริการรับ-ส่ง</h3>'+
    '<label class="qgl-check"><input id="qgl-enabled" type="checkbox" '+(settings.enabled?'checked':'')+'> เปิดรับคำขอฝากซักของร้าน</label>'+
    '<div class="qgl-grid">'+
      '<label>รูปแบบค่ารับส่ง<select id="qgl-mode"><option value="separate" '+(settings.delivery_fee_mode!=='round_trip'?'selected':'')+'>แยกค่ารับ / ส่งคืน</option><option value="round_trip" '+(settings.delivery_fee_mode==='round_trip'?'selected':'')+'>ค่ารับส่งรวม</option></select></label>'+
      '<label>ยอดบริการขั้นต่ำ (บาท)<input id="qgl-min" type="number" min="0" step="1" value="'+money(settings.minimum_order)+'"></label>'+
      '<label>ค่ารับผ้า (บาท)<input id="qgl-pickup" type="number" min="0" step="1" value="'+money(settings.base_pickup_fee)+'"></label>'+
      '<label>ค่าส่งคืน (บาท)<input id="qgl-return" type="number" min="0" step="1" value="'+money(settings.return_fee)+'"></label>'+
      '<label>ค่ารับส่งรวม (บาท)<input id="qgl-round" type="number" min="0" step="1" value="'+money(settings.round_trip_fee)+'"></label>'+
    '</div><p class="qgl-note">หากเลือก “ค่ารับส่งรวม” ค่ารับ–ส่งเป็นค่าบริการของร้าน และบันทึกราคาไว้กับออเดอร์</p>'+
    '<button class="qgm-primary" id="qgl-save-settings" type="button">บันทึกค่าฝากซัก</button></section>';

  body+='<section class="qgm-card qgl-card"><div class="qgl-section-head"><div><h3>บริการของร้าน</h3><small>รองรับต่อกก. / ต่อชิ้น / ต่อชุด / เหมาจ่าย</small></div><button class="qgm-outline" id="qgl-new-service" type="button">+ เพิ่มบริการ</button></div>'+
    serviceFormHtml()+
    '<div class="qgl-list">'+(services.length?services.map(s=>'<article><div><b>'+esc(s.name)+'</b><small>'+unit(s.pricing_type)+' · ฿'+money(s.price)+(s.estimated_minutes?' · '+Number(s.estimated_minutes)+' นาที':'')+' · '+(s.active?'เปิด':'ปิด')+'</small><small>'+esc(s.description||'')+'</small></div><button class="qgm-outline" data-edit-service="'+esc(s.id)+'">แก้ไข</button></article>').join(''):'<p class="qgl-empty">ยังไม่มีบริการ</p>')+'</div></section>';

  body+='<section class="qgm-card qgl-card"><h3>งานฝากซักล่าสุด</h3><div class="qgl-orders">'+
    (orders.length?orders.map(o=>'<article><div><b>'+esc(QueueGoOrderNumber.format(o))+'</b><small>'+esc(o.service_name_snapshot||'บริการฝากซัก')+' · '+status(o.status)+'</small><small>'+esc(o.pickup_address||'')+(o.final_total_amount!=null?' · ยอดรวม ฿'+money(o.final_total_amount):o.estimated_total_amount!=null?' · ประมาณ ฿'+money(o.estimated_total_amount):'')+'</small></div><div class="qgl-order-actions">'+contactHtml(o)+actionHtml(o)+'</div></article>').join(''):'<p class="qgl-empty">ยังไม่มีงานฝากซัก</p>')+
    '</div></section>';

  window.qtShopLayout('ฝากซัก',body,'shop-profile');bind();
}
function num(id){const n=Number(document.getElementById(id)?.value||0);if(!Number.isFinite(n)||n<0)throw Error('ราคาที่กรอกไม่ถูกต้อง');return n}
function bind(){
  document.getElementById('qgl-setup')?.addEventListener('click',setup);
  document.getElementById('qgl-save-settings')?.addEventListener('click',saveSettings);
  document.getElementById('qgl-new-service')?.addEventListener('click',()=>showService());
  document.getElementById('qgl-cancel-service')?.addEventListener('click',()=>document.getElementById('qgl-service-form').hidden=true);
  document.getElementById('qgl-save-service')?.addEventListener('click',saveService);
  document.querySelectorAll('[data-edit-service]').forEach(b=>b.onclick=()=>showService((state.data.services||[]).find(s=>String(s.id)===String(b.dataset.editService))));
  document.querySelectorAll('[data-lo-act]').forEach(b=>b.onclick=()=>shopAction(b.dataset.id,b.dataset.loAct));
  document.querySelectorAll('[data-lo-legacy]').forEach(b=>b.onclick=()=>reconcileLegacy(b.dataset.loLegacy));
}
async function reload(){await loadState();render()}
async function setup(){
  if(state.busy)return;state.busy=true;
  try{await rpc('queuego_laundry_merchant_setup',{p_name:null});toast('สร้างระบบฝากซักของร้านแล้ว');await reload()}catch(e){toast('ตั้งค่าไม่สำเร็จ: '+String(e.message||e).slice(0,120))}finally{state.busy=false}
}
async function saveSettings(){
  if(state.busy)return;state.busy=true;
  try{
    await rpc('queuego_laundry_save_settings',{
      p_enabled:document.getElementById('qgl-enabled').checked,
      p_minimum_order:num('qgl-min'),p_pickup_fee:num('qgl-pickup'),p_return_fee:num('qgl-return'),
      p_round_trip_fee:num('qgl-round'),p_delivery_fee_mode:document.getElementById('qgl-mode').value
    });
    toast('บันทึกค่าฝากซักแล้ว');await reload();
  }catch(e){toast('บันทึกไม่สำเร็จ: '+String(e.message||e).slice(0,120))}finally{state.busy=false}
}
function showService(s=null){
  const form=document.getElementById('qgl-service-form');if(!form)return;form.hidden=false;
  document.getElementById('qgl-service-id').value=s?.id||'';
  document.getElementById('qgl-service-name').value=s?.name||'';
  document.getElementById('qgl-service-type').value=s?.pricing_type||'per_kg';
  document.getElementById('qgl-service-price').value=Number(s?.price||0);
  document.getElementById('qgl-service-minutes').value=s?.estimated_minutes||'';
  document.getElementById('qgl-service-desc').value=s?.description||'';
  document.getElementById('qgl-service-active').checked=s?s.active!==false:true;
  form.scrollIntoView({behavior:'smooth',block:'start'});
}
async function saveService(){
  if(state.busy)return;
  const name=String(document.getElementById('qgl-service-name')?.value||'').trim();if(name.length<2)return toast('กรุณาระบุชื่อบริการ');
  state.busy=true;
  try{
    const mins=String(document.getElementById('qgl-service-minutes')?.value||'').trim();
    await rpc('queuego_laundry_save_service',{
      p_service_id:document.getElementById('qgl-service-id').value||null,
      p_name:name,p_description:String(document.getElementById('qgl-service-desc').value||'').trim()||null,
      p_pricing_type:document.getElementById('qgl-service-type').value,p_price:num('qgl-service-price'),
      p_estimated_minutes:mins?Number(mins):null,p_active:document.getElementById('qgl-service-active').checked
    });
    toast('บันทึกบริการแล้ว');await reload();
  }catch(e){toast('บันทึกบริการไม่สำเร็จ: '+String(e.message||e).slice(0,120))}finally{state.busy=false}
}
async function reconcileLegacy(id){
  if(state.busy)return;
  const services=(state.data?.services||[]).filter(s=>s.active);
  if(!services.length)return toast('กรุณาตั้งค่าบริการฝากซักและราคาให้พร้อมก่อน');
  const menu=services.map((s,i)=>(i+1)+'. '+s.name+' · '+unit(s.pricing_type)+' · ฿'+money(s.price)).join('\n');
  const raw=prompt('ออเดอร์นี้เป็นข้อมูลเก่า กรุณาเลือกบริการจริงก่อนรับงาน\n\n'+menu+'\n\nกรอกหมายเลขบริการ');
  if(raw===null)return;
  const service=services[Number(raw)-1];if(!service)return toast('เลือกบริการไม่ถูกต้อง');
  let qty=1;
  if(service.pricing_type!=='fixed'){
    const q=prompt(service.pricing_type==='per_kg'?'น้ำหนักโดยประมาณ (กก.)':service.pricing_type==='per_item'?'จำนวนชิ้นโดยประมาณ':'จำนวนชุดโดยประมาณ');
    if(q===null)return;qty=Number(q);if(!Number.isFinite(qty)||qty<=0)return toast('จำนวนไม่ถูกต้อง');
  }
  state.busy=true;
  try{
    await rpc('queuego_laundry_adopt_legacy_order',{p_order_id:id,p_service_id:service.id,p_estimated_quantity:qty});
    toast('ผูกบริการและ snapshot ราคาแล้ว กรุณาตรวจยอดก่อนกดรับงาน');await reload();
  }catch(e){toast('ผูกบริการเดิมไม่สำเร็จ: '+String(e.message||e).slice(0,130))}
  finally{state.busy=false}
}
async function shopAction(id,action){
  if(state.busy)return;let qty=null,note=null,staff=null;
  const o=state.orders.find(x=>String(x.id)===String(id));
  if(!o)return;
  if(['start_pickup','start_return'].includes(action)){
    staff=String([...document.querySelectorAll('[data-lo-staff]')].find(el=>el.dataset.loStaff===String(id))?.value||'').trim();
    if(!staff)return toast('กรุณาระบุชื่อพนักงานของร้าน');
  }
  if(action==='ready_return'&&o?.pricing_type_snapshot!=='fixed'){
    const label=o?.pricing_type_snapshot==='per_kg'?'น้ำหนักจริง (กก.)':o?.pricing_type_snapshot==='per_item'?'จำนวนชิ้นจริง':'จำนวนชุดจริง';
    const raw=prompt(label);if(raw===null)return;qty=Number(raw);if(!Number.isFinite(qty)||qty<=0)return toast('จำนวนไม่ถูกต้อง');
  }
  if(action==='cancel'){note=prompt('เหตุผลที่ปฏิเสธ/ยกเลิก');if(note===null)return;note=String(note).trim();if(!note)return toast('กรุณาระบุเหตุผล')}
  state.busy=true;
  try{await rpc('queuego_laundry_shop_manage',{p_order_id:id,p_action:action,p_expected_status:o.status,p_staff_name:staff,p_actual_quantity:qty,p_note:note});toast('อัปเดตงานฝากซักแล้ว');await reload()}catch(e){toast('อัปเดตไม่ได้: '+String(e.message||e).slice(0,120))}finally{state.busy=false}
}
window.addEventListener('qt:realtime-update',event=>{
  const reason=String(event?.detail?.reason||'');
  const view=(location.hash.slice(1)||'').split('/')[0];
  if(view==='shop-laundry'&&/^laundry_/.test(reason)&&!state.busy)reload().catch(()=>{});
});
window.qgRenderLaundryMerchant=async function(){
  const u=currentUser();if(!u||u.type!=='shop')return navigate('login');
  window.qtShopLayout('ฝากซัก','<div class="qgl-head"><button class="qgm-iconbtn" type="button" onclick="navigate(\'shop-modules\')">←</button><div><h2>ฝากซัก</h2><small>กำลังโหลด...</small></div></div><section class="qgm-card qgl-card">กำลังโหลดข้อมูลฝากซัก...</section>','shop-profile');
  try{await reload()}catch(e){window.qtShopLayout('ฝากซัก','<section class="qgm-card qgl-card"><h3>โหลดฝากซักไม่สำเร็จ</h3><p>'+esc(String(e.message||e))+'</p><button class="qgm-outline" onclick="qgRenderLaundryMerchant()">ลองใหม่</button></section>','shop-profile')}
};
function qgLaundryMerchantDeepLinkBoot(){if((location.hash.slice(1)||'').split('/')[0]==='shop-laundry')setTimeout(()=>window.qgRenderLaundryMerchant?.(),0)}
qgLaundryMerchantDeepLinkBoot();
})();