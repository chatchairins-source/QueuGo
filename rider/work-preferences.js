/* Rider choices are acknowledged by the server and scoped to the active account. */
(function(){
'use strict';
let actor=null,snapshot=null,readTask=null,busy=false,readAt=0;
const attempted=new Set();
const labels={food:'อาหารและเครื่องดื่ม',grocery:'ร้านขายของชำ',market:'ตลาดสด',express:'ส่งพัสดุ Express',ride:'รับส่งผู้โดยสาร'};
function identity(){return S.user&&S.riderProfile?JSON.stringify([S.user.id,S.riderProfile.id,S.session?.sessionId]):null}
function current(){const key=identity();if(key!==actor){actor=key;snapshot=null;readTask=null;readAt=0;attempted.clear()}return key}
function parse(raw){
 const p=Array.isArray(raw)?raw[0]:raw;
 if(!p||typeof p.auto_accept!=='boolean'||typeof p.bundle_enabled!=='boolean'||!Array.isArray(p.services)||!p.capabilities)throw Error('ยังยืนยันการตั้งค่ารับงานไม่ได้');
 return p;
}
async function load(force=false){
 const key=current();if(!key)return null;
 if(snapshot&&!force&&Date.now()-readAt<15000)return snapshot;if(readTask)return readTask;
 const task=(async()=>{const token=await getAccessToken();if(!token||identity()!==key)return null;const p=parse(await sbRpc('qg_rider_work_preferences',{},token));if(identity()!==key)return null;snapshot=p;readAt=Date.now();return p})();
 readTask=task;try{return await task}finally{if(readTask===task)readTask=null}
}
function render(host,p,error=''){
 host.innerHTML='<h2>รูปแบบและประเภทงาน</h2><div class="qg-setting-group"><div role="status" data-work-status style="padding:12px 16px">'+esc(error||'เลือกงานที่ต้องการรับ การรับงานพ่วงให้ยืนยันรายได้ก่อนทุกครั้ง')+'</div><form data-work-form style="padding:0 16px 16px"><label class="qg-work-choice"><span><b>รับงานอัตโนมัติ</b><small>รับงานประเภทที่เลือกขณะออนไลน์และเปิดหน้านี้</small></span><input type="checkbox" name="auto_accept" '+(p?.auto_accept?'checked':'')+'></label><label class="qg-work-choice"><span><b>รับงานพ่วง</b><small>'+(p?.capabilities.bundle?'แสดงงานพ่วงให้เลือก พร้อมรายได้และระยะทางเพิ่ม':'บันทึกความต้องการไว้ได้ · บริการงานพ่วงยังไม่เปิดใช้งาน')+'</small></span><input type="checkbox" name="bundle_enabled" '+(p?.bundle_enabled?'checked':'')+'></label><fieldset style="border:0;padding:8px 0;margin:0"><legend>ประเภทบริการที่รับ</legend>'+Object.entries(labels).map(([value,label])=>{const available=!!p?.capabilities[value];return '<label class="qg-work-choice"><span><b>'+label+'</b><small>'+(available?'เลือกเปิดหรือปิดรับงานได้':value==='ride'?'ยังไม่ได้รับสิทธิ์รับส่งผู้โดยสาร กรุณาติดต่อทีมงาน':'บริการนี้ยังไม่เปิดใช้งาน')+'</small></span><input type="checkbox" name="service" value="'+value+'" '+(p?.services?.includes(value)?'checked ':'')+(!available?'disabled':'')+'></label>'}).join('')+'</fieldset></form></div>';
 const form=host.querySelector('form');if(!p){form.querySelectorAll('input').forEach(x=>x.disabled=true);return}
 form.onchange=async e=>{
  if(!e?.target?.matches?.('input[type=checkbox]')||e.target.disabled||busy)return;
  const key=current();if(!key)return;
  const desired={auto_accept:form.elements.auto_accept.checked,bundle_enabled:form.elements.bundle_enabled.checked,services:[...form.querySelectorAll('[name=service]:checked:not(:disabled)')].map(x=>x.value)};
  if(!desired.services.length){render(host,snapshot||p,'ต้องเปิดรับบริการอย่างน้อยหนึ่งประเภท หากต้องการพักรับงานให้ปิดสถานะออนไลน์');return}
  const previous=snapshot||p;
  busy=true;form.querySelectorAll('input').forEach(x=>x.disabled=true);
  host.querySelector('[data-work-status]').textContent='กำลังบันทึก…';
  try{
   const token=await getAccessToken();if(identity()!==key||!token)throw Error('session changed');
   let ack;
   try{ack=parse(await sbRpc('qg_rider_work_preferences',{p_preferences:desired},token))}
   catch(error){const read=parse(await sbRpc('qg_rider_work_preferences',{},token));if(!matches(read,desired))throw error;ack=read}
   if(identity()!==key)return;if(!matches(ack,desired))throw Error('ยังยืนยันการบันทึกไม่ได้');
   snapshot=ack;readAt=Date.now();render(host,ack,'บันทึกแล้ว');S.bundleCandidates=[];refreshData();
  }catch(_){if(identity()===key){snapshot=previous;render(host,previous,'บันทึกไม่สำเร็จ ระบบคืนค่าเดิมแล้ว')}}
  finally{busy=false}
 };
}
function matches(p,d){return p.auto_accept===d.auto_accept&&p.bundle_enabled===d.bundle_enabled&&JSON.stringify([...p.services].sort())===JSON.stringify([...d.services].sort())}
async function mount(host){const key=current();render(host,null,'กำลังโหลดการรับงาน…');try{const p=await load(true);if(p&&identity()===key&&host.isConnected)render(host,p)}catch(_){if(identity()===key&&host.isConnected)render(host,null,'โหลดการรับงานไม่ได้ กรุณาเปิดโปรไฟล์ใหม่')}}
async function maybeAccept(){
 const key=current();if(!key||document.hidden||!navigator.onLine||!S.online||S.activeOrder||busy)return;
 const p=await load().catch(()=>null);if(identity()!==key||!p?.auto_accept||document.hidden||!S.online||S.activeOrder)return;
 const o=S.openJobs?.[0]?.order;if(!o||!p.services.includes(o.fulfillment_vertical||'food'))return;
 const expiry=Date.parse(o._offerExpiresAt);if(!Number.isFinite(expiry)||expiry<=Date.now()+1000)return;
 const id=key+':'+o.id+':'+o._offerExpiresAt;if(attempted.has(id))return;attempted.add(id);
 await window.acceptJob(o.id);
}
window.QueueGoRiderWork={mount,load,maybeAccept};
})();
