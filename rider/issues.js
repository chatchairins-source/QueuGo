/* Quick job issues use the existing support ticket RPC and audit trail. */
(function(){
 const original=qgOpenTicketPanel;
 const reasons={shop_closed:{label:'ร้านปิด',category:'merchant'},not_ready:{label:'สินค้ายังไม่พร้อม',category:'merchant'},unreachable:{label:'ติดต่อผู้รับไม่ได้',category:'rider'},wrong_location:{label:'จุดส่งผิด',category:'other'}};
 window.qgOpenTicketPanel=function(orderId=''){
  if(!orderId)return original();
  const order=S.activeOrder?.id===orderId?S.activeOrder:(S.bundleOrders||[]).find(o=>o.id===orderId);if(!order)return original(orderId);
  const user=S.user?.id,session=S.session;if(!user)return;const current=()=>S.user?.id===user&&riderSessionMatches(session);
  document.getElementById('qg-v22-ticket')?.remove();const panel=document.createElement('section');panel.id='qg-v22-ticket';panel.className='qg-v22-overlay';
  panel.innerHTML=`<div class="qg-v22-dialog"><header><b>แจ้งปัญหางาน #${esc(qtShortOrder(order))}</b><button type="button" data-close aria-label="ปิด">✕</button></header><p>ผูกกับออเดอร์นี้และบัญชีของคุณอัตโนมัติ</p><form><label>ปัญหาที่พบ<select name="reason" required><option value="">เลือกปัญหา</option>${Object.entries(reasons).map(([id,r])=>`<option value="${id}">${r.label}</option>`).join('')}</select></label><label>รายละเอียดเพิ่มเติม (ถ้ามี)<textarea name="details" maxlength="1500" placeholder="เช่น รอสินค้า 15 นาที หรือจุดสังเกตที่ถูกต้อง"></textarea></label><label>รูปหลักฐาน (ถ้ามี)<input name="evidence" type="file" accept="image/jpeg,image/png,image/webp"></label><div role="status" data-status></div><button type="submit">ส่งเรื่อง</button></form><h3>ติดตามเรื่อง</h3><div id="qg-ticket-list"></div></div>`;
  document.body.appendChild(panel);panel.querySelector('[data-close]').onclick=()=>panel.remove();panel.onclick=e=>{if(e.target===panel)panel.remove()};
  qgLoadTickets();const form=panel.querySelector('form'),box=panel.querySelector('[data-status]'),button=form.querySelector('[type=submit]'),storeKey='qg-rider-issue:'+user+':'+orderId;
  let pending;try{pending=JSON.parse(localStorage.getItem(storeKey)||'null')}catch(e){}
  if(pending){form.elements.reason.value=pending.reason;form.elements.details.value=pending.extra||'';box.textContent='มีเรื่องที่กำลังตรวจสอบ กดส่งเรื่องเพื่อตรวจสอบผลเดิม';}
  form.onsubmit=async e=>{e.preventDefault();if(button.disabled)return;button.disabled=true;box.textContent='กำลังส่งเรื่อง...';
   try{const token=await getAccessToken();if(!current()||!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
    if(!pending){const reason=form.elements.reason.value;if(!reasons[reason])throw Error('กรุณาเลือกปัญหา');const extra=form.elements.details.value.trim();const evidence=await qgUploadEvidence(form.elements.evidence.files[0]);if(!current())return;
     pending={reason,extra,body:{p_ticket_id:crypto.randomUUID(),p_order_id:orderId,p_category:reasons[reason].category,p_details:'['+reasons[reason].label+'] ออเดอร์ '+qtShortOrder(order)+' · '+new Date().toISOString()+(extra?'\n'+extra:''),p_evidence_path:evidence}};localStorage.setItem(storeKey,JSON.stringify(pending));
    }
    await sbTable('rpc/qg_create_ticket',{method:'POST',token,body:pending.body});if(!current())return;localStorage.removeItem(storeKey);pending=null;form.reset();box.textContent='ส่งเรื่องแล้ว ตรวจสอบสถานะได้ด้านล่าง';await qgLoadTickets();
   }catch(err){if(current()){if(err.definitive){localStorage.removeItem(storeKey);pending=null}box.textContent='ยังยืนยันการส่งไม่ได้: '+err.message+' · ลองใหม่จะใช้รายการเดิม'}}finally{if(current()&&button.isConnected)button.disabled=false}
  };
 };
})();
