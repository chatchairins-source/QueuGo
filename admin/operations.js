/* Reuse authoritative Orders, Rider review RPCs, Ride and Credit flows. No client state/financial bypass. */
(()=>{
'use strict';
const qgAdminOpsReadRpc=(fn,args={})=>typeof qgAdminRpcRead==='function'?qgAdminRpcRead(fn,args):qtSupabaseRpc(fn,args);
const permitted=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
const reviewQueues={
 identity:{title:'ยืนยันตัวตน',rpc:'qg_admin_rider_reverification_queue',review:'qg_admin_review_rider_reverification',request:'qg_admin_request_rider_reverification',photo:'selfie_path'},
 inspection:{title:'ตรวจสภาพรถ',rpc:'qg_admin_rider_vehicle_inspection_queue',review:'qg_admin_review_rider_vehicle_inspection',request:'qg_admin_request_rider_vehicle_inspection',photo:'vehicle_photo_path'},
 vehicle:{title:'อนุมัติยานพาหนะ',rpc:'qg_admin_rider_vehicle_queue',review:'qg_admin_review_rider_vehicle',photo:'photo_path'},
 documents:{title:'วันหมดอายุเอกสาร',rpc:'qg_admin_rider_document_queue'}
};
let generation=0,dispatchView='offers';
const reviewOffsets={identity:0,inspection:0,vehicle:0,documents:0},reviewPageSize=50;
const labels={'admin-dispatch':'Dispatch','admin-ride':'Ride · รับส่งผู้โดยสาร','admin-express':'Express · ส่งพัสดุ','admin-laundry':'ฝากซัก','admin-system':'สถานะระบบ','admin-rider-review':'ตรวจเอกสารและยานพาหนะ Rider'};
window.qgRenderAdminOperations=function(page){
 if(!permitted())return navigate('login');
 const title=labels[page];if(!title)return;
 layout(title,'<section class="card" id="qg-operations"><div class="row-btns"><button type="button" id="qg-ops-refresh">รีเฟรช</button><button type="button" onclick="qgAdminSection(\'qt-sec-settings\')">บริการและราคา</button></div><p role="status" id="qg-ops-status">กำลังโหลดเฉพาะหมวดนี้…</p><div id="qg-ops-body"></div></section>');
 document.getElementById('qg-ops-refresh').onclick=()=>load(page);
 load(page);
};
async function load(page){
 const host=document.getElementById('qg-ops-body'),status=document.getElementById('qg-ops-status'),owner=currentUser()?.id;
 if(!host||!permitted())return;const refresh=document.getElementById('qg-ops-refresh');if(refresh?.disabled)return;if(refresh)refresh.disabled=true;const version=++generation;const alive=()=>version===generation&&host.isConnected&&permitted()&&currentUser().id===owner;
 try{
  if(page==='admin-rider-review'){
   host.innerHTML='<label>งานตรวจ<select id="qg-review-kind">'+Object.entries(reviewQueues).map(([key,q])=>'<option value="'+key+'">'+q.title+'</option>').join('')+'</select></label><div id="qg-review-list"></div>';
   reviewOffsets.identity=0;
   host.querySelector('select').onchange=()=>{const kind=host.querySelector('select').value;reviewOffsets[kind]=0;loadReview(kind,alive)};await loadReview('identity',alive);
  }else if(page==='admin-ride'){
   host.innerHTML='<div id="qg-passenger-controls"></div><div id="qg-service-jobs"></div>';
   await Promise.all([qgRenderPassengerControls(),jobs('ride',host.querySelector('#qg-service-jobs'),alive)]);
  }else if(page==='admin-dispatch'){
   host.innerHTML='<section class="card" id="qg-dispatch-overview">กำลังอ่าน targeted offers…</section><div id="qg-dispatch-orders"></div>';
   await Promise.all([dispatchOverview(host.querySelector('#qg-dispatch-overview'),alive),jobs(null,host.querySelector('#qg-dispatch-orders'),alive)]);
  }else if(page==='admin-system'){
   const summary=await qgAdminOpsReadRpc('qg_admin_live_counts',{});if(!alive())return;host.replaceChildren();
   const p=document.createElement('p');p.textContent='ร้านเปิด '+Number(summary.shops_open||0)+' · Rider ออนไลน์ '+Number(summary.riders_online||0);host.append(p);
   const note=document.createElement('p');note.textContent='จำนวนนี้เป็นสถานะที่บันทึกในระบบ ยังไม่ใช่หลักฐานว่า Push, GPS หรือ worker ทำงานปกติ';host.append(note);
   for(const [key,value]of Object.entries(summary.orders||{})){const line=document.createElement('p');line.textContent=key+' · '+Number(value);host.append(line)}
   const health=document.createElement('section');health.className='card';host.append(health);await notificationHealth(health,alive);
  }else await jobs(page==='admin-express'?'express':page==='admin-laundry'?'laundry':null,host,alive);
  if(alive())status.textContent='ข้อมูลจากระบบล่าสุด';
 }catch(e){if(alive())status.textContent='ยังโหลดหมวดนี้ไม่ได้: '+e.message+' · ระบบที่ยังไม่เปิดใช้งานต้องผ่าน Release Gate ก่อน';}
 finally{if(refresh?.isConnected)refresh.disabled=false}
}
async function dispatchOverview(host,alive){
 try{const report=await qgAdminOpsReadRpc('qg_admin_dispatch_overview',{p_limit:100});if(!alive()||!host.isConnected)return;
  if(!report||!Array.isArray(report.offers)||!Array.isArray(report.history)||!Array.isArray(report.bundles))throw Error('ข้อมูล Dispatch ไม่ครบ');
  host.replaceChildren();const label=document.createElement('label'),select=document.createElement('select'),list=document.createElement('div'),stamp=document.createElement('p');label.textContent='สถานะการจัดสรรงาน ';label.append(select);stamp.textContent='อ่านล่าสุด '+new Date(report.as_of).toLocaleString('th-TH')+' · สูงสุด 100 รายการต่อหมวด';
  for(const [key,title]of [['offers','Targeted offers'],['history','ผลตอบรับ / timeout'],['bundles','งานพ่วง']]){const option=document.createElement('option');option.value=key;option.textContent=title+' ('+report[key].length+')';select.append(option)}
  const paint=()=>{list.replaceChildren();for(const row of report[select.value]){const card=document.createElement('article');card.className='card';const text=document.createElement('p');text.textContent=[row.order_id||row.primary_order_id||row.id,row.rider_id,row.outcome||row.status||(row.expired?'หมดเวลาแล้ว':'รอตอบรับ'),row.attempt?'ครั้งที่ '+row.attempt:'',row.expires_at?'หมดเวลา '+new Date(row.expires_at).toLocaleString('th-TH'):''].filter(Boolean).join(' · ');card.append(text);
   const order=row.order_id||row.primary_order_id;if(order){const button=document.createElement('button');button.type='button';button.textContent='รายละเอียดออเดอร์';button.onclick=()=>{if(alive())qgAdminTrace(order)};card.append(button)}list.append(card)}if(!report[select.value].length)list.textContent='ไม่มีรายการในหมวดนี้';};
  select.value=dispatchView;select.onchange=()=>{dispatchView=select.value;paint()};host.append(label,stamp,list);paint();
 }catch(e){if(alive()&&host.isConnected)host.textContent='ตัวอ่าน Dispatch ยังไม่พร้อม: '+e.message;}
}
async function notificationHealth(host,alive){
 host.textContent='กำลังอ่านสถานะคิวแจ้งเตือน…';
 try{const report=await qgAdminOpsReadRpc('qg_admin_notification_health',{});if(!alive()||!host.isConnected)return;
  if(!report||!Array.isArray(report.channels))throw Error('ข้อมูลคิวแจ้งเตือนไม่ครบ');host.replaceChildren();const title=document.createElement('h3'),note=document.createElement('p');title.textContent='คิวแจ้งเตือน';note.textContent='pending/leased รวมงานค้างทั้งหมด · sent/dead เฉพาะ 7 วันล่าสุด · ไม่ใช่หลักฐานว่าเครื่องผู้ใช้ได้รับแล้ว';host.append(title,note);
  for(const row of report.channels){const line=document.createElement('p');line.textContent=row.channel+' · '+row.status+' · '+Number(row.n)+' รายการ · ถึงเวลาลองส่ง '+Number(row.due)+(row.oldest?' · เก่าสุด '+new Date(row.oldest).toLocaleString('th-TH'):'');host.append(line)}
  if(!report.channels.length){const empty=document.createElement('p');empty.textContent='ไม่พบงานค้างหรือรายการในช่วงที่ตรวจ';host.append(empty)}
 }catch(e){if(alive()&&host.isConnected)host.textContent='ตัวอ่านคิวแจ้งเตือนยังไม่พร้อม: '+e.message;}
}
async function jobs(kind,host,alive){
 const token=await qtGetAccessToken();
 const table=kind==='laundry'?'laundry_orders':'orders';
 const query=table+'?select=id,status,created_at'+(kind&&kind!=='laundry'?'&order_type=eq.'+kind:'')+'&order=created_at.desc,id.desc&limit=100';
 const rows=await qtSupabaseTable(query,{accessToken:token});if(!alive())return;host.replaceChildren();
 const note=document.createElement('p');note.textContent='100 รายการล่าสุด · เปิดรายละเอียดเพื่อดูประวัติและหลักฐาน';host.append(note);
 for(const row of rows||[]){const card=document.createElement('article');card.className='card';const title=document.createElement('b');title.textContent=row.id;const line=document.createElement('p');line.textContent=row.status+' · '+new Date(row.created_at).toLocaleString('th-TH');card.append(title,line);
  if(kind!=='laundry'){const button=document.createElement('button');button.type='button';button.textContent='รายละเอียดและประวัติ';button.onclick=()=>{if(alive())qgAdminTrace(row.id)};card.append(button)}host.append(card)
 }
 if(!rows?.length){const p=document.createElement('p');p.textContent='ไม่มีรายการในหมวดนี้';host.append(p)}
}
async function loadReview(kind,alive,requestedOffset){
 const q=reviewQueues[kind],host=document.getElementById('qg-review-list');if(!q||!host||!alive())return;
 const offset=Number.isInteger(requestedOffset)?Math.max(0,requestedOffset):Math.max(0,reviewOffsets[kind]||0);reviewOffsets[kind]=offset;host.textContent='กำลังโหลดงานตรวจ…';
 try{
  const report=await qgAdminOpsReadRpc('qg_admin_rider_review_page',{p_kind:kind,p_limit:reviewPageSize,p_offset:offset});
  if(!alive()||!host.isConnected||document.getElementById('qg-review-kind')?.value!==kind)return;
  if(!report||!Array.isArray(report.items))throw Error('ข้อมูลคิวตรวจ Rider ไม่ครบ');
  const rows=report.items;if(!rows.length&&offset>0){reviewOffsets[kind]=Math.max(0,offset-reviewPageSize);return loadReview(kind,alive,reviewOffsets[kind])}
  host.replaceChildren();
  const note=document.createElement('p');note.textContent='แสดงสูงสุด '+reviewPageSize+' รายการต่อหน้า · เริ่มที่รายการ '+(offset+1);host.append(note);
  for(const row of rows){
   const card=document.createElement('article');card.className='card';const title=document.createElement('b');title.textContent=row.rider_name||row.rider_id;const detail=document.createElement('p');detail.textContent=[row.status||'',row.vehicle_plate||'',row.selected_vehicle_plate||'',row.reason||''].filter(Boolean).join(' · ');card.append(title,detail);
   const action=(text,fn)=>{const b=document.createElement('button');b.type='button';b.textContent=text;b.onclick=async()=>{if(!alive()||b.disabled)return;b.disabled=true;try{await fn();if(alive())await loadReview(kind,alive,reviewOffsets[kind])}catch(e){if(alive())toast(e.message)}finally{if(b.isConnected)b.disabled=false}};card.append(b)};
   if(row[q.photo])action('ดูหลักฐาน',()=>qgAdminOpenEvidence(row[q.photo]));
   if(kind==='documents')for(const [key,label]of [['national_id','บัตรประชาชน'],['driver_license','ใบขับขี่'],['vehicle_prb','พ.ร.บ.']]){
    const form=document.createElement('form'),labelNode=document.createElement('label'),input=document.createElement('input'),button=document.createElement('button');labelNode.textContent=label;input.type='date';input.value=row[key+'_expires_on']||'';labelNode.append(input);button.type='submit';button.textContent='บันทึกวันหมดอายุ';form.append(labelNode,button);form.onsubmit=async e=>{e.preventDefault();if(!alive()||button.disabled)return;button.disabled=true;try{await qtSupabaseRpc('qg_admin_set_rider_document_expiry',{p_rider_id:row.rider_id,p_document_key:key,p_expires_on:input.value||null,p_vehicle_id:key==='vehicle_prb'?row.selected_vehicle_id:null});if(alive()){toast('บันทึกวันหมดอายุแล้ว');await loadReview(kind,alive,reviewOffsets[kind])}}catch(err){if(alive())toast(err.message)}finally{if(button.isConnected)button.disabled=false}};card.append(form)
   }else{
    if(q.request)action('ขอตรวจใหม่',async()=>{const reason=await qgAdminInput({title:'ขอ'+q.title,label:'เหตุผล'});if(reason===null||!alive())return;if(reason.trim().length<3)throw Error('กรุณาระบุเหตุผลอย่างน้อย 3 ตัวอักษร');await qtSupabaseRpc(q.request,{p_rider_id:row.rider_id,p_reason:reason.trim()})});
    if(kind==='vehicle'||row.request_id)for(const [decision,text]of [['approved','อนุมัติ'],['rejected','ส่งกลับแก้ไข']])action(text,async()=>{const note=await qgAdminInput({title:text+' · '+q.title,label:'บันทึกผลตรวจ / เหตุผล'});if(note===null||!alive())return;if(note.trim().length<3)throw Error('กรุณาระบุผลตรวจอย่างน้อย 3 ตัวอักษร');await qtSupabaseRpc(q.review,{[kind==='vehicle'?'p_vehicle_id':'p_request_id']:kind==='vehicle'?row.vehicle_id:row.request_id,p_decision:decision,p_note:note.trim()})});
   }host.append(card);
  }
  const nav=document.createElement('div');nav.className='row-btns';
  const prev=document.createElement('button');prev.type='button';prev.textContent='ก่อนหน้า';prev.disabled=offset===0;prev.onclick=()=>{if(alive()&&!prev.disabled){reviewOffsets[kind]=Math.max(0,offset-reviewPageSize);loadReview(kind,alive,reviewOffsets[kind])}};
  const next=document.createElement('button');next.type='button';next.textContent='ถัดไป';next.disabled=!report.more;next.onclick=()=>{if(alive()&&!next.disabled){reviewOffsets[kind]=Number.isInteger(report.next_offset)?report.next_offset:offset+reviewPageSize;loadReview(kind,alive,reviewOffsets[kind])}};
  nav.append(prev,next);host.append(nav);
  if(!rows.length){const empty=document.createElement('p');empty.textContent='ไม่มีรายการตรวจ';host.append(empty)}
 }catch(e){if(alive()&&host.isConnected)host.textContent='โหลดงานตรวจไม่ได้: '+e.message}
}
// Private offer/outbox projections have no client-readable changefeed. Refresh only these visible snapshots.
setInterval(()=>{const page=location.hash.slice(1);if(!document.hidden&&navigator.onLine&&permitted()&&['admin-system','admin-dispatch'].includes(page))load(page)},60000);
})();
