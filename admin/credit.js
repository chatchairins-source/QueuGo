(function(){
 const qgAdminCreditReadRpc=(fn,args={})=>typeof qgAdminRpcRead==='function'?qgAdminRpcRead(fn,args):qtSupabaseRpc(fn,args);
 const money=v=>'฿'+(Number(v||0)/100).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2});
 const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
 function grantForm(host,kind='merchant'){
  const rider=kind==='rider',subject=rider?'Rider':'ร้านค้า';
  const owner=currentUser()?.id;if(!owner)return;
  const key=(rider?'qg-admin-rider-credit-v1:':'qg-admin-merchant-promo-v1:')+owner;
  const card=document.createElement('section');card.className='card';
  const title=document.createElement('h3');title.textContent='เพิ่มเครดิตให้'+subject;card.append(title);
  const note=document.createElement('p');note.textContent='เพิ่มเข้ายอดใช้ได้เดียวกัน บันทึกผู้ทำรายการและเหตุผลในประวัติ Admin';card.append(note);
  const searchForm=document.createElement('form'),search=document.createElement('input'),find=document.createElement('button');
  search.placeholder=rider?'ค้นหาชื่อ Rider':'ค้นหาชื่อร้านหรือเจ้าของ';search.setAttribute('aria-label','ค้นหา'+subject);search.maxLength=100;
  find.type='submit';find.textContent='ค้นหา'+subject;searchForm.append(search,find);card.append(searchForm);
  const form=document.createElement('form');
  function field(text,control){const label=document.createElement('label');label.append(text,control);form.append(label);return control}
  const target=field(subject+'ที่รับเครดิต ',document.createElement('select'));target.required=true;
  const amount=field('จำนวนเครดิต (บาท) ',document.createElement('input'));amount.type='text';amount.inputMode='decimal';amount.required=true;amount.placeholder='เช่น 1000';
  const reason=field('เหตุผลที่ให้เครดิต ',document.createElement('textarea'));reason.required=true;reason.minLength=5;reason.maxLength=500;
  const submit=document.createElement('button');submit.type='submit';submit.textContent='เพิ่มเครดิตให้'+subject;
  const status=document.createElement('p');status.setAttribute('role','status');form.append(submit,status);card.append(form);host.append(card);
  let pending=null,busy=false,searching=0;
  const alive=()=>host.isConnected&&currentUser()?.id===owner&&currentUser()?.type==='admin'&&currentUser()?.status==='approved';
  function freeze(){for(const el of [search,find,target,amount,reason])el.disabled=!!pending;submit.textContent=pending?'ส่งรายการเดิมซ้ำเพื่อตรวจสอบ':'เพิ่มเครดิตให้'+subject}
  function restore(){
   const raw=localStorage.getItem(key);if(!raw)return;
   const p=JSON.parse(raw);
   if(!uuid.test(p.request)||!uuid.test(p.user)||!Number.isSafeInteger(p.minor)||p.minor<1||p.minor>10000000||typeof p.reason!=='string'||p.reason.length<5||p.reason.length>500)throw Error('ข้อมูลรายการค้างไม่ถูกต้อง กรุณาตรวจสอบก่อนเพิ่มเครดิต');
   pending=p;const option=new Option(p.label||p.user,p.user);target.replaceChildren(option);amount.value=(p.minor/100).toFixed(2);reason.value=p.reason;
   status.textContent='มีรายการรอยืนยัน กดส่งรายการเดิมซ้ำได้โดยไม่เพิ่มเครดิตซ้ำ';freeze();
  }
  async function loadTargets(){
   const generation=++searching;find.disabled=true;
   try{
    const rows=await qgAdminCreditReadRpc(rider?'queuego_admin_rider_credit_targets':'queuego_admin_merchant_credit_targets',{p_query:search.value.trim()});
    if(!alive()||pending||generation!==searching)return;
    target.replaceChildren(new Option('เลือก'+subject,''));
    for(const row of rows){
     const label=rider
      ? row.name+' · เครดิตใช้ได้ '+money(row.available_minor)+' · '+(row.last_sign_in_at?'เข้าใช้ล่าสุด '+new Date(row.last_sign_in_at).toLocaleDateString('th-TH'):'ยังไม่เคยเข้าใช้')+' · ID '+String(row.user_id).slice(-6)
      : row.shops+' · '+row.name+' · เครดิตฟรีคงเหลือ '+money(row.promo_minor);
     target.append(new Option(label,row.user_id));
    }
    status.textContent=rows.length?'พบ '+rows.length+' บัญชี'+subject+(rows.length===50?' · ค้นหาชื่อให้เฉพาะเจาะจงเพื่อดูร้านอื่น':''):'ไม่พบบัญชี'+subject+'ที่ยังใช้งานอยู่';
   }catch(err){if(alive())status.textContent=err.message||'โหลดร้านค้าไม่สำเร็จ'}finally{if(alive()&&!pending&&generation===searching)find.disabled=false}
  }
  searchForm.onsubmit=e=>{e.preventDefault();if(!pending)loadTargets()};
  form.onsubmit=async e=>{
   e.preventDefault();if(busy||!alive())return;busy=true;submit.disabled=true;
   try{
    if(!pending){
     const value=amount.value.trim(),why=reason.value.trim();
     if(!/^(?:0|[1-9][0-9]{0,5})(?:[.][0-9]{1,2})?$/.test(value))throw Error('กรอกจำนวนบาท ทศนิยมไม่เกิน 2 ตำแหน่ง');
     const parts=value.split('.'),minor=Number(parts[0])*100+Number((parts[1]||'').padEnd(2,'0'));
     if(!uuid.test(target.value)||minor<1||minor>10000000)throw Error('เลือก'+subject+'และจำนวน 0.01–100,000 บาท');
     if(why.length<5||why.length>500)throw Error('กรอกเหตุผล 5–500 ตัวอักษร');
     const request={request:crypto.randomUUID(),user:target.value,minor,reason:why,label:target.selectedOptions[0].textContent};
     // Persist BEFORE RPC. A lost reply must retry this UUID, never invent another grant.
     localStorage.setItem(key,JSON.stringify(request));pending=request;freeze();
    }
    const result=await qtSupabaseRpc(rider?'queuego_admin_grant_rider_credit':'queuego_admin_grant_merchant_credit',{p_request:pending.request,p_user:pending.user,p_amount_minor:pending.minor,p_reason:pending.reason});
    if(!uuid.test(String(result)))throw Error('ยังยืนยันรายการไม่ได้ กรุณาส่งรายการเดิมซ้ำ');
    try{localStorage.removeItem(key)}catch(_){}
    if(!alive())return;
    toast('เพิ่มเครดิตให้'+subject+' '+money(pending.minor)+' แล้ว');
    // Server success is final even when the following report refresh fails.
    window.qgAdminClearReadCache?.();
    await window.qgAdminLoadCredit();
   }catch(err){if(alive())status.textContent=(err.message||'ยังยืนยันรายการไม่ได้')+(pending?' · กดส่งรายการเดิมซ้ำ ระบบจะไม่เพิ่มเครดิตซ้ำ':'')}
   finally{busy=false;if(alive())submit.disabled=false}
  };
  try{restore();if(!pending)loadTargets()}catch(err){status.textContent=err.message;submit.disabled=true;search.disabled=true;find.disabled=true}
 }
 window.qgAdminLoadCredit=async function(){
  const host=document.getElementById('qg-credit-admin'),owner=currentUser()?.id;if(!host||!owner||currentUser()?.type!=='admin'||currentUser()?.status!=='approved')return;host.textContent='กำลังโหลดเครดิต...';
  try{
   const rows=await qgAdminCreditReadRpc('queuego_admin_credit_accounts',{});if(!host.isConnected||currentUser()?.id!==owner||currentUser()?.type!=='admin'||currentUser()?.status!=='approved')return;host.replaceChildren();grantForm(host);grantForm(host,'rider');
   for(const a of rows){const card=document.createElement('article');card.className='card';const title=document.createElement('h3');title.textContent=(a.kind==='merchant'?'ร้านค้า · ':'Rider · ')+a.name;card.append(title);const total=document.createElement('p');total.textContent='เงินจริง '+money(a.cash_minor)+' · โปรโมชั่น '+money(a.promo_minor)+' · กันไว้ '+money(Number(a.held_cash_minor)+Number(a.held_promo_minor));card.append(total);
    for(const e of a.entries||[]){const row=document.createElement('p');row.textContent=(e.event_type==='promo'?(e.actor_id?'Admin grant':'เครดิตเริ่มต้น'):e.event_type)+' · '+money(Number(e.cash_delta)+Number(e.promo_delta))+(e.reason?' · '+e.reason:'')+' · '+new Date(e.created_at).toLocaleString('th-TH')+(e.actor_id?' · ผู้ทำรายการ '+e.actor_id:'');if(e.event_type==='capture'){const button=document.createElement('button');button.textContent='คืนค่าบริการ';button.type='button';button.onclick=async()=>{if(button.disabled||!host.isConnected||currentUser()?.id!==owner||currentUser()?.type!=='admin'||currentUser()?.status!=='approved')return;button.disabled=true;try{const reason=await qgAdminInput({title:'คืนค่าบริการเครดิต',label:'เหตุผลการคืน (อย่างน้อย 5 ตัวอักษร)'});if(reason===null||!host.isConnected||currentUser()?.id!==owner||currentUser()?.type!=='admin'||currentUser()?.status!=='approved')return;if(String(reason).trim().length<5)throw Error('กรุณาระบุเหตุผลอย่างน้อย 5 ตัวอักษร');await qtSupabaseRpc('queuego_admin_reverse_credit',{p_entry:e.id,p_reason:String(reason).trim()});toast('บันทึกคืนเครดิตแล้ว');window.qgAdminClearReadCache?.();await qgAdminLoadCredit()}catch(err){toast(err.message||'คืนเครดิตไม่สำเร็จ')}finally{button.disabled=false}};row.append(' ',button)}card.append(row)}host.append(card)
   }
   if(!rows.length){const empty=document.createElement('p');empty.textContent='ยังไม่มีบัญชีเครดิต เลือกร้านค้าด้านบนเพื่อมอบเครดิตฟรีได้';host.append(empty)}
  }catch(_){host.textContent='ยังโหลดบัญชีเครดิตไม่ได้ · ต้องติดตั้งและตรวจ Release Gate ของระบบเครดิตก่อนใช้งาน'}
 };
})();

window.renderAdminCreditCenter=function(){
 const user=currentUser();if(!user||user.type!=='admin'||user.status!=='approved'){navigate('login');return}
 layout('เครดิต Rider / ร้านค้า','<section class="card"><p>เครดิตร้านค้า / Rider และประวัติรายการ · การเปิดเติมเงินจริงขึ้นอยู่กับสถานะระบบและ Release Gate</p><button type="button" onclick="navigate(\'admin-gp-rates\')">ปรับอัตรา GP ร้านค้า</button><div id="qg-credit-admin">กำลังโหลดเครดิต...</div></section>');
 qgAdminLoadCredit();
};
