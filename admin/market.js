/* QueueGo Admin Market operations. Platform feature/pricing controls live in Admin > ตั้งค่าระบบ. */
(()=>{
'use strict';
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const admin=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
async function api(path){return qtSupabaseTable(path,{accessToken:await qtGetAccessToken()})}
const actor=()=>admin()?String(currentUser().id):null;
const image=v=>/^(https:\/\/|data:image\/(jpeg|png|webp);base64,)/i.test(String(v||''))?String(v):'';
window.qgLoadMarketPendingCount=async()=>{
  const el=document.getElementById('qg-home-pending-markets'),owner=actor();if(!el||!owner)return;
  try{
    const [requests,members]=await Promise.all([
      api('market_requests?select=id&status=eq.pending'),
      api('shop_profiles?select=id&market_membership_status=eq.pending&archived_at=is.null')
    ]);
    if(el.isConnected&&actor()===owner){el.textContent=String(requests.length+members.length);const total=document.getElementById('qg-home-pending-total');if(total)total.textContent=String(Number(total.dataset.base||0)+requests.length+members.length);}
  }catch(e){if(el.isConnected&&actor()===owner)el.textContent='โหลดไม่ได้ — เปิดหน้าตลาดเพื่อลองใหม่'}
};
window.addEventListener('qt:realtime-update',()=>{
  if(!admin())return;
  qgLoadMarketPendingCount();
  if(location.hash==='#admin-market')qgLoadMarketOperations();
});
window.renderAdminMarket=async()=>{
  if(!admin())return navigate('login');
  layout('จัดการตลาดสด',`<section class="card qg-admin-market">
    <button class="qg-admin-back" onclick="navigate('admin')">‹ ศูนย์ควบคุม</button>
    <h2>ตลาดและร้านสมาชิก</h2>
    <details class="qg-market-guide"><summary>ขั้นตอนตรวจและอนุมัติ</summary><ol><li>คำขอเพิ่มตลาด: ตรวจชื่อ ที่อยู่และพิกัด แล้วอนุมัติหรือปฏิเสธพร้อมเหตุผล</li><li>ร้านสมาชิก: ตรวจร้าน แผง/โซน พิกัดและภาพหน้าร้าน แล้วอนุมัติแยกจากคำขอตลาด</li></ol><p>อนุมัติตลาดใหม่แล้ว ร้านผู้ขอจะเข้าคิวตรวจสมาชิก ระบบยังไม่ถือว่าร้านผ่านอนุมัติ</p></details><button type="button" onclick="qgLoadMarketOperations()">รีเฟรชคำขอ</button>
    <div class="row-btns"><button class="btn-secondary" type="button" onclick="qtAdminTab&&navigate('admin')">ตั้งค่า Feature / ราคา อยู่ใน Admin › ตั้งค่าระบบ</button></div>
    <div id="qg-market-admin">กำลังโหลดข้อมูลจริง...</div>
  </section>`);
  await qgLoadMarketOperations();
};
window.qgLoadMarketOperations=async()=>{
  const el=document.getElementById('qg-market-admin'),owner=actor();if(!el||!owner)return;
  try{
    const [memberships,markets,riders,orders,requests,applicants]=await Promise.all([
      api('shop_profiles?select=id,shop_name,market_id,public_category,market_membership_status,market_stall_no,market_zone,market_suggested_distance_km,address,status,latitude,longitude,market_proof_path,public_cover,metadata,markets:market_id(name)&market_membership_status=eq.pending&archived_at=is.null&order=updated_at.asc&limit=100'),
      api('markets?select=id,name,province,district,subdistrict,verified,active,latitude,longitude,assignment_radius_km&order=province.asc,name.asc'),
      api('rider_profiles?select=id,rider_name,vehicle_type,vehicle_plate,vehicle_status,vehicle_capacity_kg,vehicle_verified_at,status,metadata&status=eq.active&order=created_at.desc&limit=100'),
      api('orders?select=id,order_number,status,fulfillment_vertical,subtotal,total_amount,market_order_id,created_at&market_order_id=not.is.null&order=created_at.desc&limit=50'),
      api('market_requests?select=id,requested_name,province,district,subdistrict,address,latitude,longitude,status,note,requester_shop_user_id,created_at&status=eq.pending&order=created_at.asc&limit=50'),
      api('shop_profiles?select=user_id,shop_name,public_category,metadata&archived_at=is.null')
    ]);
    if(!el.isConnected||actor()!==owner)return;
    el.innerHTML=`
      <h2>รออนุมัติร้านสมาชิกตลาด (${(memberships||[]).length})</h2>
      ${(memberships||[]).map(s=>`<article>
        <b>${esc(s.shop_name||'ไม่ระบุชื่อร้าน')}</b> · ${esc(qgAdminShopCategory(s))} · ${esc(s.markets?.name||'ตลาด')}
        <br><small>แผง ${esc(s.market_stall_no||'-')} · โซน ${esc(s.market_zone||'-')} · ระยะจากตลาด ${Number(s.market_suggested_distance_km||0).toFixed(2)} กม.</small>
        <p>${esc(s.address||'ไม่ระบุที่อยู่')}</p>
        ${s.latitude!=null&&s.longitude!=null?`<button type="button" onclick="qgAdminViewLocation(${Number(s.latitude)},${Number(s.longitude)},'พิกัดร้าน')">ดูบนแผนที่</button>`:''}
        ${image(s.market_proof_path||s.public_cover||s.metadata?.cover||s.metadata?.profileImage||s.metadata?.profile_image)?`<img class="qg-market-proof" alt="ภาพหน้าร้านหรือแผง" src="${esc(image(s.market_proof_path||s.public_cover||s.metadata?.cover||s.metadata?.profileImage||s.metadata?.profile_image))}">`:'<p>ไม่มีภาพหน้าร้านที่เปิดดูได้ — ตรวจหลักฐานก่อนอนุมัติ</p>'}
        <div><button type="button" onclick="qgAdminEditLocation('shop','${s.id}')">แก้พิกัดร้าน</button><button onclick="qgAdminReviewMarket('${s.id}',true)">อนุมัติ</button><button onclick="qgAdminReviewMarket('${s.id}',false)">ปฏิเสธ</button></div>
      </article>`).join('')||'<p>ไม่มีร้านรออนุมัติ</p>'}
      <h2>คำขอเพิ่มตลาดใหม่ (${(requests||[]).length})</h2>
      ${(requests||[]).map(r=>`<article><b>${esc(r.requested_name)}</b><p>ร้านผู้ขอ: ${esc((applicants||[]).find(s=>s.user_id===r.requester_shop_user_id)?.shop_name||'ไม่ระบุชื่อร้าน')} · ${esc(qgAdminShopCategory((applicants||[]).find(s=>s.user_id===r.requester_shop_user_id)))}</p> · ${esc(r.subdistrict||'')} ${esc(r.district||'')} ${esc(r.province||'')}<br><small>${esc(r.address||'')} · ${r.latitude!=null&&r.longitude!=null&&Number.isFinite(Number(r.latitude))&&Number.isFinite(Number(r.longitude))?Number(r.latitude).toFixed(5)+', '+Number(r.longitude).toFixed(5):'ไม่มีพิกัด'} · ส่งคำขอ ${new Date(r.created_at).toLocaleString('th-TH')}</small><p>${esc(r.note||'')}</p>${r.latitude!=null&&r.longitude!=null?`<button type="button" onclick="qgAdminViewLocation(${Number(r.latitude)},${Number(r.longitude)},'พิกัดคำขอตลาด')">ดูบนแผนที่</button>`:''}<div><button onclick="qgAdminReviewMarketRequest('${r.id}',true)">อนุมัติและเพิ่มตลาด</button><button onclick="qgAdminReviewMarketRequest('${r.id}',false)">ปฏิเสธ</button></div></article>`).join('')||'<p>ไม่มีคำขอเพิ่มตลาดใหม่</p>'}
      <h2>ตลาดในระบบ</h2>
      ${(markets||[]).map(m=>`<article><b>${esc(m.name)}</b> · ${esc(m.subdistrict||'')} ${esc(m.district||'')} ${esc(m.province||'')}<br><small>${m.verified?'ยืนยันพิกัดแล้ว':'รอตรวจพิกัด'} · รัศมี ${Number(m.assignment_radius_km||0).toFixed(1)} กม. · ${m.latitude==null||m.longitude==null?'ยังไม่มีพิกัด':Number(m.latitude).toFixed(5)+', '+Number(m.longitude).toFixed(5)}</small><div><button type="button" onclick="qgAdminEditLocation('market','${m.id}')">แก้พิกัดตลาด</button></div></article>`).join('')||'<p>ยังไม่มีตลาด</p>'}
      <h2>รถและความจุ</h2>
      ${(riders||[]).map(r=>`<article><b>${esc(r.rider_name||'ไรเดอร์')}</b> · ${esc(r.vehicle_type||'-')} · ${esc(r.vehicle_plate||'')}<br><small>สถานะ ${esc(r.vehicle_status||'-')} · ยืนยัน ${Number(r.vehicle_capacity_kg||0)} กก. · ขอ ${esc(r.metadata?.requestedCapacityKg||'-')} กก.</small><div><button onclick="qgAdminVehicle('${r.id}','active',${Number(r.vehicle_capacity_kg||r.metadata?.requestedCapacityKg||20)})">ตรวจและอนุมัติความจุ</button><button onclick="qgAdminVehicle('${r.id}','suspended',0)">ระงับรถ</button></div></article>`).join('')||'<p>ยังไม่มีไรเดอร์ที่เปิดใช้งาน</p>'}
      <h2>ออเดอร์ตลาดล่าสุด</h2>
      ${(orders||[]).map(o=>`<article><b>${esc(o.order_number||o.id)}</b> · ${esc(o.status)} · ${Number(o.total_amount||0).toLocaleString('th-TH')} ฿</article>`).join('')||'<p>ยังไม่มีออเดอร์ตลาด</p>'}`;
  }catch(e){if(el.isConnected&&actor()===owner)el.textContent='โหลดข้อมูลไม่ได้ กรุณากดรีเฟรชคำขอ: '+e.message}
};
window.qgAdminReviewMarket=async(shopId,approve)=>{
  if(!admin())return;
  let reason=null;
  if(!approve){
    reason=await qgAdminInput({title:'ปฏิเสธสมาชิกตลาด',label:'เหตุผลที่ปฏิเสธการเข้าตลาด'});
    if(reason===null)return;
    reason=reason.trim();
    if(reason.length<2)return toast('กรุณาระบุเหตุผลอย่างน้อย 2 ตัวอักษร');
  }
  if(!confirm(approve?'อนุมัติร้านนี้เข้าตลาด?':'ยืนยันปฏิเสธคำขอเข้าตลาด?'))return;
  try{
    await qtSupabaseRpc('queuego_admin_review_market_membership',{p_shop_id:shopId,p_approve:!!approve,p_reason:reason});
    toast(approve?'อนุมัติสมาชิกตลาดแล้ว':'ปฏิเสธคำขอแล้ว');
    await qgLoadMarketOperations();
  }catch(e){toast('บันทึกไม่ได้: '+e.message)}
};
window.qgAdminReviewMarketRequest=async(requestId,approve)=>{
  if(!admin())return;
  let reason=null,radius=2;
  if(approve){
    const raw=await qgAdminInput({title:'อนุมัติตลาดใหม่',label:'รัศมีรับสมัครร้าน (กม.)',type:'number',value:2,min:0.1,max:10,step:0.1,hint:'ร้านผู้ขอต้องผ่านการตรวจสมาชิกแยกอีกครั้ง'});
    if(raw===null)return;
    radius=Number(raw);
    if(!Number.isFinite(radius)||radius<0.1||radius>10)return toast('รัศมีต้องอยู่ระหว่าง 0.1-10 กม.');
    if(!confirm('ยืนยันเพิ่มตลาดนี้เข้าระบบ? พิกัดจะยังมีสถานะรอตรวจจนกว่า Admin ยืนยันความถูกต้อง'))return;
  }else{
    reason=await qgAdminInput({title:'ปฏิเสธตลาดใหม่',label:'เหตุผลที่ปฏิเสธคำขอเพิ่มตลาด'});
    if(reason===null)return;
    reason=reason.trim();
    if(reason.length<2)return toast('กรุณาระบุเหตุผล');
  }
  try{
    const result=await qtSupabaseRpc('queuego_admin_review_market_request',{
      p_request_id:requestId,
      p_approve:!!approve,
      p_reason:reason,
      p_assignment_radius_km:radius
    });
    toast(approve?'เพิ่มตลาดแล้ว ร้านผู้ขอจะเข้าสู่คิวตรวจสมาชิกตลาด':'ปฏิเสธคำขอเพิ่มตลาดแล้ว');
    await qgLoadMarketOperations();
    return result;
  }catch(e){toast('บันทึกไม่ได้: '+String(e.message||e).slice(0,130))}
};
window.qgAdminVehicle=async(id,status,suggested)=>{
  if(!admin())return;
  const raw=status==='active'?await qgAdminInput({title:'ตรวจความจุรถ',label:'ความจุตามเอกสาร (กก.)',type:'number',value:suggested||'',min:0.1,max:1000,step:0.1}):null;
  if(status==='active'&&raw===null)return;
  const capacity=status==='active'?Number(raw):null;
  if(status==='active'&&(!Number.isFinite(capacity)||capacity<=0||capacity>1000))return toast('ความจุไม่ถูกต้อง');
  if(!confirm(status==='active'?'ยืนยันว่าได้ตรวจรถและความจุแล้ว?':'ระงับรถคันนี้จากงานตลาด?'))return;
  try{
    await qtSupabaseRpc('market_verify_rider_vehicle',{p_rider:id,p_capacity:capacity,p_status:status});
    toast('บันทึกสถานะรถแล้ว');
    await qgLoadMarketOperations();
  }catch(e){toast('บันทึกไม่ได้: '+e.message)}
};
})();