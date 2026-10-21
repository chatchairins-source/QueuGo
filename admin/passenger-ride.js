const qgAdminRideReadRpc=(fn,args={})=>typeof qgAdminRpcRead==='function'?qgAdminRpcRead(fn,args):qtSupabaseRpc(fn,args);
/* Existing Admin auth/RPC/rules and document review, with explicit values only. */
(()=>{
 const fields=[
  ['base_fare','ค่าเริ่มต้น (บาท)'],
  ['included_km','ระยะทางที่รวม (กม.)'],
  ['per_km','ค่าระยะทางเพิ่ม (บาท/กม.)'],
  ['minimum_fare','ค่าโดยสารขั้นต่ำ (บาท)'],
  ['long_distance_threshold_km','เริ่มคิดอัตราระยะไกลที่ (กม.)'],
  ['long_distance_per_km','อัตราระยะไกล (บาท/กม.)'],
  ['platform_fee_percent','ค่าธรรมเนียมจากรายรับ Rider (%)']
 ];
 let saving=false;
 const esc=v=>escapeHtml(String(v??''));
 const adminAlive=(host,owner)=>host.isConnected&&currentUser()?.id===owner&&currentUser()?.type==='admin'&&currentUser()?.status==='approved';
 window.qgRenderPassengerControls=async()=>{
  const host=document.getElementById('qg-passenger-controls');if(!host||currentUser()?.type!=='admin'||currentUser()?.status!=='approved')return;
  const owner=currentUser().id;
  host.innerHTML='<section id="qg-ride-config-panel" aria-labelledby="qg-ride-config-title"><h3 id="qg-ride-config-title">รับส่งผู้โดยสาร</h3><p role="status">กำลังอ่านการตั้งค่าจากระบบ…</p></section><section id="qg-ride-license-panel" aria-labelledby="qg-ride-license-title"><h3 id="qg-ride-license-title">เอกสาร Rider สำหรับ Ride</h3><p>ระบบปัจจุบันยังไม่มีคิวตรวจใบอนุญาตสาธารณะแยกสำหรับ Ride ข้อมูลเอกสารที่ตรวจได้อยู่ในศูนย์ตรวจเอกสาร Rider</p><button type="button" class="btn-secondary" data-open-rider-review>เปิดศูนย์ตรวจเอกสาร Rider</button></section>';
  const configPanel=host.querySelector('#qg-ride-config-panel');
  host.querySelector('[data-open-rider-review]').onclick=()=>{if(adminAlive(host,owner))navigate('admin-rider-review')};
  async function loadConfig(){
   if(!adminAlive(host,owner))return;configPanel.innerHTML='<h3 id="qg-ride-config-title">รับส่งผู้โดยสาร</h3><p role="status">กำลังอ่านการตั้งค่าจากระบบ…</p>';
   try{
    const config=await qgAdminRideReadRpc('queuego_ride_config',{});if(!adminAlive(host,owner))return;
    if(!config||Array.isArray(config)||typeof config.enabled!=='boolean')throw Error('ข้อมูลบริการผู้โดยสารไม่ครบ');
    const state=!config.configured?'ข้อมูลค่าโดยสารยังไม่ครบ · ระบบควรปิดรับงาน':config.enabled?'เปิดบริการ':'ปิดบริการ';
    configPanel.innerHTML='<h3 id="qg-ride-config-title">รับส่งผู้โดยสาร</h3><p>ค่าโดยสารเงินสดแยกต่อเที่ยว · ค่าเริ่มต้นและอัตราทุกช่วงอ่านจากกฎราคาปัจจุบัน</p><form id="qg-ride-policy"><label>เปิดบริการ <input id="qg-ride-enabled" type="checkbox" '+(config.enabled?'checked':'')+'></label><div class="qt-settings-grid">'+fields.map(([k,label])=>'<label>'+label+'<input name="'+k+'" type="number" min="0" max="'+(k==='platform_fee_percent'?'99.99':'100000')+'" step="0.01" required value="'+(Number(config[k])>=0?esc(config[k]):'')+'"></label>').join('')+'</div><p>ค่าธรรมเนียมมากกว่า 0% จะบันทึกได้เมื่อเชื่อมระบบเครดิต Rider แล้วเท่านั้น</p><button class="btn-primary" type="submit">บันทึกบริการผู้โดยสาร</button><p id="qg-ride-policy-status" role="status">'+esc(state)+'</p></form>';
    const form=configPanel.querySelector('#qg-ride-policy');
    form.onsubmit=async event=>{
     event.preventDefault();if(!adminAlive(host,owner)||saving)return;saving=true;
     const button=form.querySelector('button'),status=form.querySelector('[role=status]'),toggle=form.querySelector('#qg-ride-enabled');button.disabled=true;status.textContent='กำลังบันทึก…';
     try{
      const pricing=Object.fromEntries(fields.map(([k])=>[k,Number(form.elements.namedItem(k).value)]));
      if(Object.values(pricing).some(n=>!Number.isFinite(n)))throw Error('กรอกราคาให้ครบ');
      const result=await qtSupabaseRpc('queuego_admin_configure_ride',{p_enabled:toggle.checked,p_pricing:pricing});
      if(!adminAlive(host,owner))return;
      if(!result||typeof result.enabled!=='boolean'||result.configured!==true)throw Error('ระบบยังไม่ยืนยันการตั้งค่าครบ');
      status.textContent=result.configured?(result.enabled?'บันทึกแล้ว · เปิดบริการ':'บันทึกแล้ว · ปิดบริการ'):'บันทึกแล้ว แต่ค่าโดยสารยังตั้งค่าไม่ครบ';
      await loadConfig();
     }catch(e){if(adminAlive(host,owner)){toggle.checked=config.enabled===true;status.textContent='ยังบันทึกไม่ได้: '+e.message;}}
     finally{saving=false;if(button.isConnected)button.disabled=false;}
    };
   }catch(e){if(!adminAlive(host,owner))return;configPanel.innerHTML='<h3 id="qg-ride-config-title">รับส่งผู้โดยสาร</h3><p role="alert">อ่านการตั้งค่า Ride ไม่สำเร็จ: '+esc(e.message)+'</p><button type="button" class="btn-secondary" data-ride-retry>ลองอีกครั้ง</button>';configPanel.querySelector('[data-ride-retry]').onclick=loadConfig;}
  }
  await loadConfig();
 };
})();
