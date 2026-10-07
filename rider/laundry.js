(()=>{
'use strict';
let L={state:null,busy:false};
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function rpc(name,body={}){
  const token=await getAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
  return sbTable('rpc/'+name,{method:'POST',token,body});
}
const money=v=>Number(v||0).toLocaleString('th-TH',{minimumFractionDigits:0,maximumFractionDigits:2});
function navLink(lat,lng,address){
  try{if(typeof mapsLink==='function')return mapsLink(lat,lng,address)}catch(e){}
  if(Number.isFinite(Number(lat))&&Number.isFinite(Number(lng)))return 'https://map.longdo.com/?lat='+encodeURIComponent(lat)+'&long='+encodeURIComponent(lng);
  return '#';
}
function ensureEntry(){
  const host=document.querySelector('#panel-profile .qt-settings');
  if(!host||host.querySelector('#qg-laundry-rider-entry'))return;
  const b=document.createElement('button');b.id='qg-laundry-rider-entry';b.className='qt-setting';b.type='button';
  b.innerHTML='<span>ฝากซัก / รับ-ส่งผ้า</span><span>›</span>';b.onclick=()=>window.qgOpenLaundryRider();
  host.insertBefore(b,host.querySelector('#qt-logout')||null);
}
const observer=new MutationObserver(()=>ensureEntry());
function startObserver(){const app=document.getElementById('app');if(app)observer.observe(app,{childList:true,subtree:true});ensureEntry()}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',startObserver);else startObserver();

function overlay(){
  let el=document.getElementById('qg-laundry-rider-panel');
  if(el)return el;
  el=document.createElement('section');el.id='qg-laundry-rider-panel';el.className='qglr-overlay';
  el.innerHTML='<div class="qglr-sheet"><header><button id="qglr-close" type="button">←</button><div><b>ฝากซัก</b><small>งานรับผ้าและส่งคืน</small></div><button id="qglr-refresh" type="button">↻</button></header><main id="qglr-body"><div class="qglr-empty">กำลังโหลด...</div></main></div>';
  document.body.appendChild(el);
  el.querySelector('#qglr-close').onclick=()=>el.classList.remove('show');
  el.querySelector('#qglr-refresh').onclick=()=>load();
  return el;
}
function actionButton(j){
  if(j.job_status==='assigned')return '<button class="qglr-primary" data-laundry-action="arrive" data-job="'+esc(j.job_id)+'">'+(j.leg==='pickup'?'ถึงจุดรับผ้าแล้ว':'ถึงร้านแล้ว')+'</button>';
  if(j.job_status==='arrived')return '<button class="qglr-primary" data-laundry-action="collect" data-job="'+esc(j.job_id)+'">'+(j.leg==='pickup'?'รับผ้าจากลูกค้าแล้ว':'รับผ้าสะอาดจากร้านแล้ว')+'</button>';
  if(j.job_status==='collected')return '<button class="qglr-primary" data-laundry-action="deliver" data-job="'+esc(j.job_id)+'">'+(j.leg==='pickup'?'ส่งผ้าถึงร้านแล้ว':'ส่งคืนลูกค้าแล้ว')+'</button>';
  return '';
}
function render(){
  const d=L.state||{},invites=Array.isArray(d.invites)?d.invites:[],jobs=Array.isArray(d.active_jobs)?d.active_jobs:[],pool=Array.isArray(d.pool)?d.pool:[];
  const body=document.getElementById('qglr-body');if(!body)return;
  let html='';
  if(!d.feature_enabled)html+='<div class="qglr-banner">ระบบฝากซักยังปิดรับงานใหม่จาก Admin ช่วง Beta แต่คุณยังรับคำเชิญจากร้านและตั้งโหมดล่วงหน้าได้</div>';
  html+='<section class="qglr-card"><div class="qglr-row"><div><b>โหมดรับงานฝากซัก</b><small>งานฝากซักแยกจากงาน Delivery ปกติ และจะไม่เสนอเมื่อคุณมีงานอื่นค้าง</small></div><label class="qglr-switch"><input id="qglr-mode" type="checkbox" '+(d.mode_enabled?'checked':'')+'><span></span></label></div></section>';

  if(invites.length){
    html+='<section class="qglr-card"><h3>คำเชิญจากร้าน</h3>'+invites.map(i=>'<article class="qglr-item"><div><b>'+esc(i.shop_name||i.hub_name||'ร้านฝากซัก')+'</b><small>'+esc(i.hub_name||'')+'</small></div><div class="qglr-btns"><button data-invite="'+esc(i.invite_id)+'" data-accept="1">ยอมรับ</button><button class="ghost" data-invite="'+esc(i.invite_id)+'" data-accept="0">ปฏิเสธ</button></div></article>').join('')+'</section>';
  }

  html+='<section class="qglr-card"><h3>งานที่กำลังทำ</h3>'+
    (jobs.length?jobs.map(j=>{
      const from=navLink(j.from_latitude,j.from_longitude,j.from_address);
      const to=navLink(j.to_latitude,j.to_longitude,j.to_address);
      return '<article class="qglr-job"><div class="qglr-jobhead"><div><b>'+esc(QueueGoOrderNumber.format(j))+'</b><small>'+(j.leg==='pickup'?'รับผ้าจากลูกค้า → ร้าน':'รับผ้าจากร้าน → ลูกค้า')+' · '+esc(j.service_name||'ฝากซัก')+'</small></div><strong>฿'+money(j.job_fee)+'</strong></div>'+
      '<div class="qglr-stop"><span>จาก</span><b>'+esc(j.from_address||'-')+'</b><a href="'+esc(from)+'" target="_blank" rel="noopener">นำทาง</a></div>'+
      '<div class="qglr-stop"><span>ไป</span><b>'+esc(j.to_address||'-')+'</b><a href="'+esc(to)+'" target="_blank" rel="noopener">ปลายทาง</a></div>'+
      actionButton(j)+'</article>';
    }).join(''):'<p class="qglr-empty">ไม่มีงานฝากซักที่กำลังทำ</p>')+'</section>';

  html+='<section class="qglr-card"><h3>งานฝากซักที่รับได้</h3>'+
    (d.feature_enabled?(pool.length?pool.map(j=>'<article class="qglr-item"><div><b>'+esc(QueueGoOrderNumber.format(j))+' · '+esc(j.shop_name||j.hub_name||'ร้านฝากซัก')+'</b><small>'+(j.leg==='pickup'?'ไปรับผ้าจากลูกค้า':'ส่งผ้าคืนลูกค้า')+' · '+esc(j.service_name||'')+' · รายรับ ฿'+money(j.job_fee)+'</small><small>'+esc(j.from_address||'')+' → '+esc(j.to_address||'')+'</small></div><button data-laundry-claim="'+esc(j.job_id)+'">รับงาน</button></article>').join(''):'<p class="qglr-empty">ตอนนี้ยังไม่มีงานฝากซักที่รับได้</p>'):'<p class="qglr-empty">Admin ยังปิดการรับงานใหม่</p>')+
    '</section>';
  body.innerHTML=html;
  bind();
}
function bind(){
  const mode=document.getElementById('qglr-mode');
  if(mode)mode.onchange=async()=>{const wanted=mode.checked;mode.disabled=true;try{await rpc('queuego_set_laundry_rider_mode',{p_enabled:wanted});toast(wanted?'เปิดโหมดฝากซักแล้ว':'ปิดโหมดฝากซักแล้ว');await load()}catch(e){mode.checked=!wanted;toast('เปลี่ยนโหมดไม่สำเร็จ: '+String(e.message||e).slice(0,100))}finally{if(mode.isConnected)mode.disabled=false}};
  document.querySelectorAll('[data-invite]').forEach(b=>b.onclick=async()=>{if(L.busy)return;L.busy=true;b.disabled=true;try{await rpc('queuego_laundry_rider_invite_action',{p_invite_id:b.dataset.invite,p_accept:b.dataset.accept==='1'});toast(b.dataset.accept==='1'?'ยอมรับร้านฝากซักแล้ว':'ปฏิเสธคำเชิญแล้ว');await load()}catch(e){toast('บันทึกคำเชิญไม่สำเร็จ: '+String(e.message||e).slice(0,100))}finally{L.busy=false}});
  document.querySelectorAll('[data-laundry-claim]').forEach(b=>b.onclick=async()=>{if(L.busy)return;L.busy=true;b.disabled=true;try{await rpc('queuego_claim_laundry_job',{p_job_id:b.dataset.laundryClaim});toast('รับงานฝากซักแล้ว');await load()}catch(e){toast('รับงานไม่ได้: '+String(e.message||e).slice(0,110))}finally{L.busy=false}});
  document.querySelectorAll('[data-laundry-action]').forEach(b=>b.onclick=async()=>{if(L.busy)return;L.busy=true;b.disabled=true;try{await rpc('queuego_laundry_rider_action',{p_job_id:b.dataset.job,p_action:b.dataset.laundryAction});toast('อัปเดตงานฝากซักแล้ว');await load();try{refreshData()}catch(e){}}catch(e){toast('อัปเดตไม่ได้: '+String(e.message||e).slice(0,110))}finally{L.busy=false}});
}
async function load(){
  const body=document.getElementById('qglr-body');if(body)body.innerHTML='<div class="qglr-empty">กำลังโหลด...</div>';
  try{L.state=await rpc('queuego_laundry_rider_state',{});render()}catch(e){if(body)body.innerHTML='<div class="qglr-empty">โหลดงานฝากซักไม่สำเร็จ<br>'+esc(String(e.message||e).slice(0,140))+'</div>'}
}
window.qgOpenLaundryRider=function(){const el=overlay();el.classList.add('show');load()};
})();