/* Edits the existing market/shop master coordinates through one audited Admin RPC. */
(()=>{
'use strict';
const allowed=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
const owner=()=>allowed()?String(currentUser().id):null;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let dialog=null,opening=0;
const close=()=>{opening++;if(dialog){dialog.remove();dialog=null}};
window.addEventListener('hashchange',close);
window.qgAdminEditLocation=async(kind,id)=>{
 const actor=owner(),ticket=++opening;if(!actor||!['market','shop','shop_user'].includes(kind))return;
 try{
  const token=await qtGetAccessToken();if(owner()!==actor||opening!==ticket)return;
  const market=kind==='market',rows=await qtSupabaseTable((market?'markets?select=id,name,latitude,longitude&id=eq.':'shop_profiles?select=id,shop_name,public_category,metadata,latitude,longitude&'+(kind==='shop_user'?'user_id':'id')+'=eq.')+encodeURIComponent(id)+'&limit=1',{accessToken:token});
  if(owner()!==actor||opening!==ticket)return;
  const row=rows?.[0];if(!row)throw Error('ไม่พบข้อมูลพิกัด');
  if(dialog)dialog.remove();const box=document.createElement('dialog');dialog=box;box.className='qg-location-editor';
  box.innerHTML=`<form><h2>แก้พิกัด${market?'ตลาด':'ร้าน'}</h2><p>${esc(row.name||row.shop_name)}${market?'':' · '+esc(qgAdminShopCategory(row))}</p><p>พิกัดเดิม: ${esc(row.latitude??'ไม่ระบุ')}, ${esc(row.longitude??'ไม่ระบุ')}</p><label>ละติจูด<input name="lat" type="number" step="any" min="-90" max="90" required value="${esc(row.latitude)}"></label><label>ลองจิจูด<input name="lng" type="number" step="any" min="-180" max="180" required value="${esc(row.longitude)}"></label><a class="qg-location-preview" target="_blank" rel="noopener" hidden>ตรวจจุดใหม่บนแผนที่</a><label>เหตุผลที่แก้ไข<textarea name="reason" required minlength="2" maxlength="500" placeholder="เช่น แก้หมุดให้ตรงทางเข้าร้าน"></textarea></label><p>พิกัดใหม่ใช้กับงานใหม่ ออเดอร์เดิมเก็บพิกัดตามรายการที่สร้างไว้</p><p role="status" class="qg-location-status"></p><div><button type="button" class="qg-location-cancel">ยกเลิก</button><button type="submit">บันทึกพิกัด</button></div></form>`;
  document.body.append(box);box.showModal();const form=box.querySelector('form'),status=box.querySelector('[role=status]');
  const valid=()=>form.elements.lat.value.trim()!==''&&form.elements.lng.value.trim()!==''&&Number.isFinite(Number(form.elements.lat.value))&&Number.isFinite(Number(form.elements.lng.value))&&Math.abs(Number(form.elements.lat.value))<=90&&Math.abs(Number(form.elements.lng.value))<=180&&!(Number(form.elements.lat.value)===0&&Number(form.elements.lng.value)===0);
  const preview=()=>{const link=box.querySelector('a');link.hidden=!valid();if(valid())link.href='https://www.google.com/maps?q='+encodeURIComponent(form.elements.lat.value+','+form.elements.lng.value)};
  form.addEventListener('input',preview);preview();let busy=false;
  box.querySelector('.qg-location-cancel').onclick=()=>{if(!busy)close()};box.addEventListener('cancel',e=>{e.preventDefault();if(!busy)close()});
  form.onsubmit=async e=>{
   e.preventDefault();if(busy||owner()!==actor||dialog!==box)return;
   const reason=form.elements.reason.value.trim();if(!valid()||reason.length<2){status.textContent='กรุณากรอกพิกัดและเหตุผลให้ครบ';return}
   busy=true;form.querySelectorAll('button,input,textarea').forEach(el=>el.disabled=true);status.textContent='กำลังบันทึก…';
   try{
    const result=await qtSupabaseRpc('qg_admin_update_location',{p_entity_type:market?'market':'shop',p_entity_id:row.id,p_lat:Number(form.elements.lat.value),p_lng:Number(form.elements.lng.value),p_reason:reason,p_expected_lat:row.latitude??null,p_expected_lng:row.longitude??null});
    if(owner()!==actor||dialog!==box)return;
    if(result?.id!==row.id)throw Error('ไม่ได้รับผลยืนยันจากฐานข้อมูล');
    close();toast(result.changed?'บันทึกพิกัดและประวัติแล้ว':'พิกัดตรงกับข้อมูลที่บันทึกแล้ว');
    if(location.hash==='#admin-market')await qgLoadMarketOperations();else{await qtHydrateDatabase({light:false});if(owner()===actor&&location.hash==='#admin-governance'){renderAdminGovernance();qtAdminTab('qt-sec-shops')}}
   }catch(e){if(owner()===actor&&dialog===box)status.textContent='บันทึกไม่ได้: '+e.message}
   finally{busy=false;if(dialog===box&&owner()===actor)form.querySelectorAll('button,input,textarea').forEach(el=>el.disabled=false)}
  };
 }catch(e){if(owner()===actor&&opening===ticket)toast('เปิดพิกัดไม่ได้: '+e.message)}
};
})();
