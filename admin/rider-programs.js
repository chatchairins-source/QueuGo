/* Admin Rider Programs: existing Production Missions, Benefits and Insurance authorities only. */
(()=>{
'use strict';
const isAdmin=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
const readRpc=(fn,args={})=>typeof window.qgAdminRpcRead==='function'?window.qgAdminRpcRead(fn,args):qtSupabaseRpc(fn,args);
const mutate=(fn,args={})=>qtSupabaseRpc(fn,args);
let view=null,epoch=0;
const benefitCategories=[['fuel','น้ำมัน / พลังงาน'],['maintenance','ดูแลรถ'],['food','อาหารและเครื่องดื่ม'],['telecom','โทรศัพท์ / อินเทอร์เน็ต'],['health','สุขภาพ'],['other','อื่น ๆ']];
const insuranceTypes=[['personal_accident','อุบัติเหตุส่วนบุคคล'],['income_protection','คุ้มครองรายได้'],['vehicle','ยานพาหนะ'],['other','อื่น ๆ']];
const optionHtml=rows=>rows.map(([v,l])=>'<option value="'+v+'">'+l+'</option>').join('');
const statusText=v=>({active:'ใช้งานอยู่',upcoming:'ยังไม่เริ่ม',expired:'หมดอายุ',disabled:'ปิดใช้งาน'}[String(v||'')]||String(v||''));
const text=(tag,value,cls)=>{const el=document.createElement(tag);if(cls)el.className=cls;el.textContent=value??'';return el};
const button=(label,handler,cls='btn-secondary')=>{const b=document.createElement('button');b.type='button';b.className=cls;b.textContent=label;b.onclick=async()=>{if(b.disabled||!viewAlive())return;b.disabled=true;try{await handler()}catch(e){if(viewAlive())toast(e.message||String(e))}finally{if(b.isConnected)b.disabled=false}};return b};
const viewAlive=()=>!!view&&view.owner===currentUser()?.id&&isAdmin()&&document.getElementById('qg-rider-programs')?.isConnected;
const isoLocal=(offsetHours=0)=>{const d=new Date(Date.now()+offsetHours*3600000);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16)};
const today=()=>{const d=new Date();return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10)};
const cleanPhone=v=>String(v||'').replace(/[ -]/g,'').trim();

window.qgRenderAdminRiderPrograms=function(){
 if(!isAdmin())return navigate('login');
 view={owner:currentUser().id,tab:'missions',version:++epoch};
 layout('โปรแกรม Rider','<section class="card" id="qg-rider-programs"><div class="row-btns" id="qg-rider-program-tabs"><button type="button" data-program="missions">Missions</button><button type="button" data-program="benefits">Benefits</button><button type="button" data-program="insurance">Insurance</button></div><p class="qt-admin-note">ใช้ข้อมูลจริงและ RPC เดิมของ QueueGo เท่านั้น · ไม่มีโบนัสหรือกรมธรรม์ตัวอย่าง</p><p id="qg-rider-program-status" role="status"></p><div id="qg-rider-program-body"></div></section>');
 document.querySelectorAll('#qg-rider-program-tabs [data-program]').forEach(b=>b.onclick=()=>{if(!viewAlive())return;view.tab=b.dataset.program;renderActive()});
 renderActive();
};

async function renderActive(){
 if(!viewAlive())return;const version=++view.version,tab=view.tab,host=document.getElementById('qg-rider-program-body'),note=document.getElementById('qg-rider-program-status');
 document.querySelectorAll('#qg-rider-program-tabs [data-program]').forEach(b=>b.classList.toggle('active',b.dataset.program===tab));
 note.textContent='กำลังโหลดข้อมูลจากระบบ…';host.replaceChildren();
 try{
  if(tab==='missions')await renderMissions(host,version);
  else if(tab==='benefits')await renderBenefits(host,version);
  else await renderInsurance(host,version);
  if(viewAlive()&&view.version===version&&view.tab===tab)note.textContent='ข้อมูลจากระบบล่าสุด';
 }catch(e){
  if(e?.name==='AbortError')return;
  if(viewAlive()&&view.version===version)note.textContent='โหลดข้อมูลไม่ได้: '+(e.message||e);
 }
}
const valid=(version,tab)=>viewAlive()&&view.version===version&&view.tab===tab;

async function renderMissions(host,version){
 host.innerHTML='<form id="qg-mission-form" class="card"><h2>สร้าง Mission</h2><label>ชื่อภารกิจ<input name="title" maxlength="120" required></label><label>รายละเอียด<textarea name="description" maxlength="500"></textarea></label><label>เป้าหมาย (เที่ยว)<input name="target" type="number" min="1" max="500" step="1" value="10" required></label><label>เริ่ม<input name="starts" type="datetime-local" required></label><label>สิ้นสุด<input name="ends" type="datetime-local" required></label><label>ข้อความรางวัล (ไม่บังคับ)<input name="reward" maxlength="160"></label><button type="submit" class="btn-primary">สร้าง Mission</button><p class="qt-admin-note">Mission นี้นับเที่ยวที่สำเร็จจริงเท่านั้น และไม่จ่ายโบนัส/เครดิตอัตโนมัติ</p></form><div id="qg-mission-list"></div>';
 const form=host.querySelector('#qg-mission-form');form.elements.starts.value=isoLocal(0);form.elements.ends.value=isoLocal(24);
 form.onsubmit=async e=>{e.preventDefault();if(!viewAlive())return;const target=Number(form.elements.target.value),starts=new Date(form.elements.starts.value),ends=new Date(form.elements.ends.value);
  if(!Number.isInteger(target)||target<1||target>500)return toast('เป้าหมายต้องอยู่ระหว่าง 1–500 เที่ยว');
  if(!Number.isFinite(starts.getTime())||!Number.isFinite(ends.getTime())||ends<=starts||ends-starts>90*86400000)return toast('ช่วงเวลา Mission ไม่ถูกต้อง');
  const submit=form.querySelector('button[type=submit]');submit.disabled=true;
  try{await mutate('qg_admin_create_rider_mission',{p_title:form.elements.title.value.trim(),p_description:form.elements.description.value.trim()||null,p_target_jobs:target,p_reward_note:form.elements.reward.value.trim()||null,p_starts_at:starts.toISOString(),p_ends_at:ends.toISOString()});if(viewAlive()){toast('สร้าง Mission แล้ว');await renderActive()}}
  catch(err){if(viewAlive())toast('สร้าง Mission ไม่สำเร็จ: '+(err.message||err))}
  finally{if(submit.isConnected)submit.disabled=false}
 };
 const rows=await readRpc('qg_admin_rider_missions',{});if(!valid(version,'missions'))return;
 const list=host.querySelector('#qg-mission-list');list.replaceChildren();
 for(const row of Array.isArray(rows)?rows:[]){
  const card=document.createElement('article');card.className='card';card.append(text('h3',row.title||'Mission Rider'));
  card.append(text('p','เป้าหมาย '+Number(row.target_jobs||0).toLocaleString('th-TH')+' เที่ยว · '+new Date(row.starts_at).toLocaleString('th-TH')+' → '+new Date(row.ends_at).toLocaleString('th-TH'),'qt-admin-note'));
  if(row.description)card.append(text('p',row.description));if(row.reward_note)card.append(text('p','รางวัล/หมายเหตุ: '+row.reward_note,'qt-admin-note'));
  card.append(button(row.active?'ปิด Mission':'เปิด Mission',async()=>{await mutate('qg_admin_set_rider_mission_active',{p_mission_id:row.id,p_active:!row.active});toast(row.active?'ปิด Mission แล้ว':'เปิด Mission แล้ว');await renderActive()},row.active?'btn-danger':'btn-primary'));list.append(card);
 }
 if(!list.children.length)list.append(text('p','ยังไม่มี Mission Rider'));
}

async function renderBenefits(host,version){
 host.innerHTML='<form id="qg-benefit-form" class="card"><h2>เพิ่ม Rider Benefit</h2><label>ประเภท<select name="category">'+optionHtml(benefitCategories)+'</select></label><label>ผู้ให้สิทธิ์<input name="provider" maxlength="120" required></label><label>ชื่อสิทธิ์<input name="title" maxlength="160" required></label><label>รายละเอียด<textarea name="description" maxlength="1200"></textarea></label><label>วันเริ่ม<input name="starts" type="date" required></label><label>วันหมดอายุ<input name="expires" type="date"></label><label>ข้อความปุ่ม<input name="actionLabel" maxlength="60"></label><label>ลิงก์ HTTPS<input name="actionUrl" type="url" inputmode="url"></label><label>เบอร์ติดต่อ<input name="phone" inputmode="tel" maxlength="18"></label><button type="submit" class="btn-primary">เพิ่มสิทธิ์</button></form><div id="qg-benefit-list"></div>';
 const form=host.querySelector('#qg-benefit-form');form.elements.starts.value=today();
 form.onsubmit=async e=>{e.preventDefault();if(!viewAlive())return;const start=form.elements.starts.value,end=form.elements.expires.value,url=form.elements.actionUrl.value.trim(),phone=cleanPhone(form.elements.phone.value);
  if(end&&end<start)return toast('วันหมดอายุต้องไม่ก่อนวันเริ่ม');
  if(url&&!/^https:\/\//i.test(url))return toast('ลิงก์สิทธิ์ต้องใช้ https://');
  if(phone&&!/^[+]?[0-9]{8,15}$/.test(phone))return toast('เบอร์ติดต่อไม่ถูกต้อง');
  const submit=form.querySelector('button[type=submit]');submit.disabled=true;
  try{await mutate('qg_admin_save_rider_benefit',{p_benefit_id:null,p_category:form.elements.category.value,p_provider_name:form.elements.provider.value.trim(),p_title:form.elements.title.value.trim(),p_description:form.elements.description.value.trim()||null,p_starts_on:start,p_expires_on:end||null,p_action_label:form.elements.actionLabel.value.trim()||null,p_action_url:url||null,p_support_phone:phone||null,p_enabled:true});if(viewAlive()){toast('เพิ่ม Rider Benefit แล้ว');await renderActive()}}
  catch(err){if(viewAlive())toast('เพิ่มสิทธิ์ไม่สำเร็จ: '+(err.message||err))}
  finally{if(submit.isConnected)submit.disabled=false}
 };
 const rows=await readRpc('qg_admin_rider_benefits',{});if(!valid(version,'benefits'))return;
 const list=host.querySelector('#qg-benefit-list');list.replaceChildren();
 for(const row of Array.isArray(rows)?rows:[]){
  const card=document.createElement('article');card.className='card';card.append(text('h3',row.title||'Rider Benefit'));card.append(text('p',(row.provider_name||'-')+' · '+statusText(row.status),'qt-admin-note'));
  if(row.description)card.append(text('p',row.description));const actions=document.createElement('div');actions.className='row-btns';
  actions.append(button(row.enabled?'ปิด':'เปิด',async()=>{await mutate('qg_admin_set_rider_benefit_enabled',{p_benefit_id:row.benefit_id,p_enabled:!row.enabled});toast(row.enabled?'ปิดสิทธิ์แล้ว':'เปิดสิทธิ์แล้ว');await renderActive()}));
  actions.append(button('ลบ',async()=>{if(!window.confirm('ลบสิทธิประโยชน์นี้ออกจาก QueueGo?'))return;await mutate('qg_admin_delete_rider_benefit',{p_benefit_id:row.benefit_id});toast('ลบสิทธิ์แล้ว');await renderActive()},'btn-danger'));card.append(actions);list.append(card);
 }
 if(!list.children.length)list.append(text('p','ยังไม่มี Rider Benefit ที่บันทึกใน QueueGo'));
}

async function loadRiderChoices(){
 const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบ Admin ใหม่');
 const users=await qtSupabaseTable('users?select=id,name,status&role=eq.rider&status=neq.deleted&order=id.asc&limit=201',{accessToken:token});
 const page=(users||[]).slice(0,200),ids=page.map(x=>x.id).filter(qtUuidLike);
 const profiles=ids.length?await qtSupabaseTable('rider_profiles?select=id,user_id,status&user_id=in.('+ids.join(',')+')&limit=200',{accessToken:token}):[];
 const names=new Map(page.map(x=>[String(x.id),x.name||'Rider']));
 return {rows:(profiles||[]).map(p=>({id:p.id,name:names.get(String(p.user_id))||'Rider'})),more:(users||[]).length>200};
}

async function renderInsurance(host,version){
 host.innerHTML='<form id="qg-insurance-form" class="card"><h2>เพิ่มกรมธรรม์ที่ตรวจสอบแล้ว</h2><label>Rider<select name="rider" required><option value="">กำลังโหลด Rider…</option></select></label><label>ประเภทความคุ้มครอง<select name="coverage">'+optionHtml(insuranceTypes)+'</select></label><label>บริษัทประกัน / ผู้ให้บริการ<input name="provider" maxlength="120" required></label><label>ชื่อผลิตภัณฑ์<input name="product" maxlength="160"></label><label>เลขกรมธรรม์<input name="policy" maxlength="100"></label><label>วันเริ่ม<input name="starts" type="date" required></label><label>วันหมดอายุ<input name="expires" type="date" required></label><label>สรุปความคุ้มครอง<textarea name="summary" maxlength="1000"></textarea></label><label>เบอร์ติดต่อ<input name="phone" inputmode="tel" maxlength="18"></label><label>ลิงก์ช่วยเหลือ HTTPS<input name="supportUrl" type="url" inputmode="url"></label><button type="submit" class="btn-primary">บันทึกกรมธรรม์</button><p class="qt-admin-note" id="qg-insurance-rider-note"></p></form><div id="qg-insurance-list"></div>';
 const form=host.querySelector('#qg-insurance-form');form.elements.starts.value=today();
 const choicesPromise=loadRiderChoices(),rowsPromise=readRpc('qg_admin_rider_insurance_queue',{});
 const [choices,rows]=await Promise.all([choicesPromise,rowsPromise]);if(!valid(version,'insurance'))return;
 const select=form.elements.rider;select.replaceChildren(new Option('เลือก Rider',''));for(const r of choices.rows)select.append(new Option(r.name,r.id));if(choices.more)host.querySelector('#qg-insurance-rider-note').textContent='รายการ Rider จำกัด 200 บัญชีแรกในหน้านี้';
 form.onsubmit=async e=>{e.preventDefault();if(!viewAlive())return;const start=form.elements.starts.value,end=form.elements.expires.value,url=form.elements.supportUrl.value.trim(),phone=cleanPhone(form.elements.phone.value);
  if(!select.value)return toast('กรุณาเลือก Rider');if(!start||!end||end<start)return toast('ช่วงวันคุ้มครองไม่ถูกต้อง');if(url&&!/^https:\/\//i.test(url))return toast('ลิงก์ช่วยเหลือต้องใช้ https://');if(phone&&!/^[+]?[0-9]{8,15}$/.test(phone))return toast('เบอร์ติดต่อไม่ถูกต้อง');
  const submit=form.querySelector('button[type=submit]');submit.disabled=true;
  try{await mutate('qg_admin_save_rider_insurance',{p_policy_id:null,p_rider_id:select.value,p_coverage_type:form.elements.coverage.value,p_provider_name:form.elements.provider.value.trim(),p_product_name:form.elements.product.value.trim()||null,p_policy_number:form.elements.policy.value.trim()||null,p_starts_on:start,p_expires_on:end,p_coverage_summary:form.elements.summary.value.trim()||null,p_support_phone:phone||null,p_support_url:url||null});if(viewAlive()){toast('บันทึกกรมธรรม์ Rider แล้ว');await renderActive()}}
  catch(err){if(viewAlive())toast('บันทึกกรมธรรม์ไม่สำเร็จ: '+(err.message||err))}
  finally{if(submit.isConnected)submit.disabled=false}
 };
 const list=host.querySelector('#qg-insurance-list');list.replaceChildren();
 for(const row of Array.isArray(rows)?rows:[]){
  const card=document.createElement('article');card.className='card';card.append(text('h3',row.product_name||row.coverage_type||'กรมธรรม์ Rider'));card.append(text('p',(row.rider_name||'Rider')+' · '+(row.provider_name||'-')+' · '+statusText(row.status),'qt-admin-note'));card.append(text('p',(row.starts_on||'-')+' → '+(row.expires_on||'-')));
  card.append(button('ลบกรมธรรม์',async()=>{if(!window.confirm('ลบกรมธรรม์นี้ออกจาก QueueGo?'))return;await mutate('qg_admin_delete_rider_insurance',{p_policy_id:row.policy_id});toast('ลบกรมธรรม์แล้ว');await renderActive()},'btn-danger'));list.append(card);
 }
 if(!list.children.length)list.append(text('p','ยังไม่มีกรมธรรม์ที่บันทึกใน QueueGo'));
}
})();
