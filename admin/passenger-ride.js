/* Existing Admin auth/RPC/rules and document review, with explicit values only. */
(()=>{
 const fields=[['base_fare','ค่าเริ่มต้น (บาท)'],['included_km','ระยะทางที่รวม (กม.)'],['per_km','ค่าระยะทางเพิ่ม (บาท/กม.)'],['minimum_fare','ค่าโดยสารขั้นต่ำ (บาท)'],['platform_fee_percent','ค่าธรรมเนียมจากรายรับ Rider (%)']];
 let saving=false;
 const esc=v=>escapeHtml(String(v??''));
 window.qgRenderPassengerControls=async()=>{
  const host=document.getElementById('qg-passenger-controls');if(!host||currentUser()?.type!=='admin'||currentUser()?.status!=='approved')return;
  const owner=currentUser().id,alive=()=>host.isConnected&&currentUser()?.id===owner&&currentUser()?.type==='admin'&&currentUser()?.status==='approved';
  host.textContent='กำลังอ่านบริการผู้โดยสาร…';
  try{
   const [config,licenses]=await Promise.all([qtSupabaseRpc('queuego_ride_config',{}),qtSupabaseRpc('queuego_admin_public_licenses',{})]);
   if(!alive())return;
   host.innerHTML='<h3>รับส่งผู้โดยสาร</h3><p>ใบขับขี่สาธารณะต้องตรวจอนุมัติและยังไม่หมดอายุ ค่าโดยสารเงินสดแยกต่อเที่ยว</p><form id="qg-ride-policy"><label>เปิดบริการ <input id="qg-ride-enabled" type="checkbox" '+(config.enabled?'checked':'')+'></label><div class="qt-settings-grid">'+fields.map(([k,label])=>'<label>'+label+'<input name="'+k+'" type="number" min="0" max="'+(k==='platform_fee_percent'?'99.99':'100000')+'" step="0.01" required value="'+(config[k]>=0?esc(config[k]):'')+'"></label>').join('')+'</div><p>ไม่ใส่อัตราให้อัตโนมัติ ค่าธรรมเนียมมากกว่า 0 ต้องเชื่อมเครดิต Rider เดิมให้พร้อมก่อนเปิดบริการ</p><button class="btn-primary" type="submit">บันทึกบริการผู้โดยสาร</button><p id="qg-ride-policy-status" role="status">'+(config.enabled?'เปิดบริการ':'ปิดบริการ')+'</p></form><h3>ตรวจใบขับขี่สาธารณะ</h3><p>อนุมัติได้เฉพาะเมื่อ Rider ส่งหลักฐานใบขับขี่สาธารณะเข้าระบบแล้ว วันที่คือวันหมดอายุจริง การเว้นว่างคือถอนอนุมัติ</p>'+licenses.map(r=>{const evidence=r.evidence_path?'<button class="btn-secondary" type="button" data-evidence="'+esc(r.evidence_path)+'" onclick="qgAdminOpenEvidence(this.dataset.evidence)">ดูหลักฐานใบขับขี่สาธารณะ</button>':'<p>ยังไม่มีหลักฐานใบขับขี่สาธารณะ</p>';const submitted=r.submitted_at?'<small>ส่งเอกสาร '+esc(new Date(r.submitted_at).toLocaleString('th-TH'))+' · '+esc(r.submission_status||'pending')+'</small>':'';return '<form class="qg-public-license" data-rider="'+esc(r.rider_id)+'"><b>'+esc(r.name)+'</b><p>'+(r.approved?'ผ่านการตรวจ':'ยังไม่มีใบขับขี่สาธารณะที่ใช้ได้')+'</p>'+evidence+submitted+'<label>วันหมดอายุ <input name="expiry" type="date" value="'+esc(r.expires_on)+'"></label><label>บันทึกการตรวจ <input name="note" required minlength="3" maxlength="500"></label><button type="submit" class="btn-secondary">บันทึกผลตรวจ</button><p role="status"></p></form>'}).join('');
   host.querySelector('#qg-ride-policy').onsubmit=async event=>{
    event.preventDefault();if(!alive()||saving)return;saving=true;
    const form=event.currentTarget,button=form.querySelector('button'),status=form.querySelector('[role=status]');button.disabled=true;
    try{
     const pricing=Object.fromEntries(fields.map(([k])=>[k,Number(form.elements.namedItem(k).value)]));
     if(Object.values(pricing).some(n=>!Number.isFinite(n)))throw Error('กรอกราคาให้ครบ');
     const result=await qtSupabaseRpc('queuego_admin_configure_ride',{p_enabled:form.querySelector('#qg-ride-enabled').checked,p_pricing:pricing});
     Object.assign(config,result);status.textContent=result.enabled?'บันทึกแล้ว · เปิดบริการ':'บันทึกแล้ว · ปิดบริการ';
    }catch(e){form.querySelector('#qg-ride-enabled').checked=config.enabled===true;status.textContent='ยังบันทึกไม่ได้: '+e.message;}finally{saving=false;button.disabled=false;}
   };
   host.querySelectorAll('.qg-public-license').forEach(form=>form.onsubmit=async event=>{
    event.preventDefault();if(!alive())return;const button=form.querySelector('button'),status=form.querySelector('[role=status]');if(button.disabled)return;button.disabled=true;
    try{await qtSupabaseRpc('queuego_admin_review_public_license',{p_rider_id:form.dataset.rider,p_expires_on:form.elements.namedItem('expiry').value||null,p_note:form.elements.namedItem('note').value});status.textContent='บันทึกผลตรวจแล้ว';}
    catch(e){status.textContent='บันทึกไม่ได้: '+e.message;}finally{button.disabled=false;}
   });
  }catch(e){host.textContent='บริการผู้โดยสารยังไม่พร้อม: '+e.message;}
 };
})();
