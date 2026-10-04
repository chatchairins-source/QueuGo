/* QueueGo Admin Market + platform runtime controls. Real Supabase only; RLS enforced. */
(()=>{
'use strict';
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const admin=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
const money=n=>Number(n||0).toLocaleString('th-TH',{minimumFractionDigits:0,maximumFractionDigits:2});
async function api(path){return qtSupabaseTable(path,{accessToken:await qtGetAccessToken()})}
async function write(path,method,body){return qtSupabaseTable(path,{method,accessToken:await qtGetAccessToken(),body})}
const localDateTime=(d=new Date())=>{
  const z=n=>String(n).padStart(2,'0');
  return d.getFullYear()+'-'+z(d.getMonth()+1)+'-'+z(d.getDate())+'T'+z(d.getHours())+':'+z(d.getMinutes());
};
const currentRule=(rows,type)=>{
  const now=Date.now();
  return (rows||[]).filter(r=>r.rule_type===type&&r.active!==false&&new Date(r.effective_from).getTime()<=now)
    .sort((a,b)=>new Date(b.effective_from)-new Date(a.effective_from))[0]||null;
};
const nextRule=(rows,type)=>{
  const now=Date.now();
  return (rows||[]).filter(r=>r.rule_type===type&&r.active!==false&&new Date(r.effective_from).getTime()>now)
    .sort((a,b)=>new Date(a.effective_from)-new Date(b.effective_from))[0]||null;
};
const checked=v=>v?'checked':'';
const n=(id,min=0,max=Number.POSITIVE_INFINITY)=>{
  const x=Number(document.getElementById(id)?.value);
  if(!Number.isFinite(x)||x<min||x>max)throw Error('ค่าที่กรอกไม่ถูกต้อง');
  return x;
};
function scheduledLabel(rule){
  if(!rule)return '';
  return '<div class="qg-admin-scheduled">ตั้งไว้ล่วงหน้า: '+new Date(rule.effective_from).toLocaleString('th-TH')+'</div>';
}
function pricingCard(title,body,type){
  return '<section class="qg-admin-control-card"><h3>'+title+'</h3>'+body+
    '<label class="qg-admin-field">มีผลตั้งแต่<input id="qg-'+type+'-effective" type="datetime-local" value="'+localDateTime()+'"></label>'+
    '<button class="qg-admin-save" type="button" onclick="qgSavePlatformPricing(\''+type+'\')">บันทึกราคา/กติกา</button></section>';
}
window.renderAdminMarket=async()=>{
  if(!admin())return navigate('login');
  layout('ตลาดสด',`<section class="card qg-admin-market">
    <button class="qg-admin-back" onclick="navigate('admin')">‹ ศูนย์ควบคุม</button>
    <h1>ตลาดสด · งานพ่วง · GP</h1>
    <p>ค่าที่เปิด/ปิดจากหน้านี้เก็บใน Supabase และมีผลกับแอปโดยไม่ต้อง Build APK ใหม่</p>
    <div id="qg-platform-controls">กำลังโหลดการตั้งค่าจริง...</div>
    <div id="qg-market-admin">กำลังโหลดข้อมูลตลาดและรถรับงาน...</div>
  </section>`);
  await Promise.allSettled([qgLoadPlatformControls(),qgLoadMarketOperations()]);
};

window.qgLoadPlatformControls=async()=>{
  const el=document.getElementById('qg-platform-controls');if(!el||!admin())return;
  try{
    const [settings,rules]=await Promise.all([
      api('system_settings?select=key,value&key=in.(platform_features,gp)'),
      api('platform_pricing_rules?select=id,rule_type,effective_from,config,active,created_at&rule_type=in.(market,route_bundle,gp)&order=effective_from.desc')
    ]);
    if(!el.isConnected)return;
    const byKey=Object.fromEntries((settings||[]).map(x=>[x.key,x.value||{}]));
    const features=byKey.platform_features||{};
    const market=currentRule(rules,'market')?.config||{base_fee:30,base_distance_km:5,extra_distance_per_km:10,second_shop_fee:10,additional_shop_fee:5};
    const route=currentRule(rules,'route_bundle')?.config||{max_detour_km:1.5,max_delay_minutes:10,max_jobs:2,min_rider_extra_fee:10};
    const gp=currentRule(rules,'gp')?.config||byKey.gp||{default_rate:10};
    const nextMarket=nextRule(rules,'market'),nextRoute=nextRule(rules,'route_bundle'),nextGp=nextRule(rules,'gp');
    el.innerHTML=`
      <section class="qg-admin-control-card qg-admin-switches">
        <h2>สวิตช์ระบบช่วงทดลอง</h2>
        <label><span><b>ตลาดสดซื้อหลายร้าน</b><small>ปิด = ตลาดสดยังสั่งร้านเดียวได้ แต่ห้ามสร้าง Market Trip หลายร้าน</small></span><input id="qg-flag-market" type="checkbox" ${checked(features.market_multi_shop_enabled)}></label>
        <label><span><b>งานพ่วง Route Bundle</b><small>อาหาร · เครื่องดื่ม · ร้านขายของชำ</small></span><input id="qg-flag-bundle" type="checkbox" ${checked(features.route_bundle_enabled)}></label>
        <label><span><b>คิด GP</b><small>ปิด = ออเดอร์ใหม่ GP 0% · ออเดอร์เก่าไม่ถูกแก้ย้อนหลัง</small></span><input id="qg-flag-gp" type="checkbox" ${checked(features.gp_enabled!==false)}></label>
        <button class="qg-admin-save" type="button" onclick="qgSaveFeatureFlags()">บันทึกสวิตช์</button>
      </section>
      <div class="qg-admin-control-grid">
        ${pricingCard('ค่าบริการตลาดสด',`
          <label class="qg-admin-field">ค่ารอบพื้นฐาน<input id="qg-market-base" type="number" min="0" step="1" value="${esc(market.base_fee??30)}"></label>
          <label class="qg-admin-field">ร้านที่ 2 เพิ่ม<input id="qg-market-second" type="number" min="0" step="1" value="${esc(market.second_shop_fee??10)}"></label>
          <label class="qg-admin-field">ร้านที่ 3 เป็นต้นไป / ร้าน<input id="qg-market-more" type="number" min="0" step="1" value="${esc(market.additional_shop_fee??5)}"></label>
          <input id="qg-market-base-km" type="hidden" value="${esc(market.base_distance_km??5)}">
          <input id="qg-market-extra-km" type="hidden" value="${esc(market.extra_distance_per_km??10)}">
          ${scheduledLabel(nextMarket)}
        `,'market')}
        ${pricingCard('งานพ่วง Route Bundle',`
          <label class="qg-admin-field">อ้อมได้สูงสุด (กม.)<input id="qg-route-detour" type="number" min="0" max="50" step=".1" value="${esc(route.max_detour_km??1.5)}"></label>
          <label class="qg-admin-field">เพิ่มเวลาสูงสุด (นาที)<input id="qg-route-delay" type="number" min="0" max="180" step="1" value="${esc(route.max_delay_minutes??10)}"></label>
          <label class="qg-admin-field">จำนวนงานพร้อมกันสูงสุด<input id="qg-route-jobs" type="number" min="1" max="10" step="1" value="${esc(route.max_jobs??2)}"></label>
          <label class="qg-admin-field">รายรับ Rider เพิ่มขั้นต่ำ<input id="qg-route-rider-min" type="number" min="0" step="1" value="${esc(route.min_rider_extra_fee??10)}"></label>
          ${scheduledLabel(nextRoute)}
        `,'route')}
        ${pricingCard('GP มาตรฐาน',`
          <label class="qg-admin-field">GP (%)<input id="qg-gp-rate" type="number" min="0" max="100" step=".1" value="${esc(gp.default_rate??10)}"></label>
          <small>อัตราพิเศษรายร้านที่มีอยู่เดิมยังมีผลก่อนอัตรามาตรฐาน</small>
          ${scheduledLabel(nextGp)}
        `,'gp')}
      </div>`;
  }catch(e){if(el.isConnected)el.textContent='โหลดการตั้งค่าไม่ได้: '+e.message}
};

window.qgSaveFeatureFlags=async()=>{
  if(!admin())return;
  const value={
    market_multi_shop_enabled:!!document.getElementById('qg-flag-market')?.checked,
    route_bundle_enabled:!!document.getElementById('qg-flag-bundle')?.checked,
    gp_enabled:!!document.getElementById('qg-flag-gp')?.checked
  };
  try{
    await write('system_settings?on_conflict=key','POST',{key:'platform_features',value,updated_at:new Date().toISOString()});
    try{qtAudit('platform_feature_flags_update',{entityType:'system_settings',entityId:'platform_features',value})}catch(_){}
    toast('บันทึกสวิตช์ระบบแล้ว');
    await qgLoadPlatformControls();
  }catch(e){toast('บันทึกสวิตช์ไม่ได้: '+e.message)}
};

window.qgSavePlatformPricing=async type=>{
  if(!admin())return;
  try{
    let ruleType=type,config;
    if(type==='market'){
      config={base_fee:n('qg-market-base'),base_distance_km:n('qg-market-base-km'),extra_distance_per_km:n('qg-market-extra-km'),second_shop_fee:n('qg-market-second'),additional_shop_fee:n('qg-market-more')};
    }else if(type==='route'){
      ruleType='route_bundle';
      config={max_detour_km:n('qg-route-detour',0,50),max_delay_minutes:n('qg-route-delay',0,180),max_jobs:Math.round(n('qg-route-jobs',1,10)),min_rider_extra_fee:n('qg-route-rider-min',0)};
    }else if(type==='gp'){
      config={default_rate:n('qg-gp-rate',0,100)};
    }else throw Error('ไม่รู้จักประเภทการตั้งค่า');
    const raw=document.getElementById('qg-'+type+'-effective')?.value;
    const effective=raw?new Date(raw):new Date();
    if(Number.isNaN(effective.getTime()))throw Error('วันเวลาที่เริ่มใช้ไม่ถูกต้อง');
    const body={rule_type:ruleType,effective_from:effective.toISOString(),config,active:true,updated_at:new Date().toISOString()};
    await write('platform_pricing_rules?on_conflict=rule_type,effective_from','POST',body);
    if(type==='gp'&&effective.getTime()<=Date.now()+1000){
      await write('system_settings?on_conflict=key','POST',{key:'gp',value:{default_rate:config.default_rate},updated_at:new Date().toISOString()});
      window.QT_GP_DEFAULT_RATE=config.default_rate;
    }
    try{qtAudit('platform_pricing_rule_update',{entityType:'platform_pricing_rules',entityId:ruleType,ruleType,effective_from:body.effective_from,config})}catch(_){}
    toast(effective.getTime()>Date.now()+60000?'ตั้งค่าล่วงหน้าแล้ว':'บันทึกค่าใหม่แล้ว');
    await qgLoadPlatformControls();
  }catch(e){toast('บันทึกค่าไม่ได้: '+e.message)}
};

window.qgLoadMarketOperations=async()=>{
  const el=document.getElementById('qg-market-admin');if(!el||!admin())return;
  try{
    const [riders,orders,markets]=await Promise.all([
      api('rider_profiles?select=id,rider_name,vehicle_type,vehicle_plate,vehicle_status,vehicle_capacity_kg,vehicle_verified_at,status,metadata&status=eq.active&order=created_at.desc&limit=100'),
      api('orders?select=id,order_number,status,fulfillment_vertical,subtotal,total_amount,created_at&fulfillment_vertical=in.(market,grocery)&order=created_at.desc&limit=50'),
      api('markets?select=id,name,province,district,subdistrict,verified,active,latitude,longitude,assignment_radius_km&order=province.asc,name.asc')
    ]);
    if(!el.isConnected)return;
    el.innerHTML=`<h2>ตลาดในระบบ</h2>
      ${(markets||[]).map(m=>`<article><b>${esc(m.name)}</b> · ${esc(m.subdistrict||'')} ${esc(m.district||'')} ${esc(m.province||'')}<br><small>${m.verified?'ยืนยันพิกัดแล้ว':'รอตรวจพิกัด'} · รัศมี ${money(m.assignment_radius_km)} กม. · ${m.latitude==null||m.longitude==null?'ยังไม่มีพิกัด':money(m.latitude)+', '+money(m.longitude)}</small></article>`).join('')||'<p>ยังไม่มีตลาด</p>'}
      <h2>รถและความจุ</h2>
      ${(riders||[]).map(r=>`<article><b>${esc(r.rider_name||'ไรเดอร์')}</b> · ${esc(r.vehicle_type)} · ${esc(r.vehicle_plate||'')}<br><small>สถานะ ${esc(r.vehicle_status)} · ยืนยัน ${Number(r.vehicle_capacity_kg||0)} กก. · ขอ ${esc(r.metadata?.requestedCapacityKg||'-')} กก.</small><div><button onclick="qgAdminVehicle('${r.id}','active',${Number(r.vehicle_capacity_kg||r.metadata?.requestedCapacityKg||20)})">ตรวจและอนุมัติความจุ</button><button onclick="qgAdminVehicle('${r.id}','suspended',0)">ระงับรถ</button></div></article>`).join('')||'<p>ยังไม่มีไรเดอร์ที่เปิดใช้งาน</p>'}
      <h2>ออเดอร์ตลาดล่าสุด</h2>
      ${(orders||[]).map(o=>`<article><b>${esc(o.order_number||o.id)}</b> · ${esc(o.fulfillment_vertical)} · ${esc(o.status)} · ${Number(o.total_amount||0).toLocaleString('th-TH')} ฿</article>`).join('')||'<p>ยังไม่มีออเดอร์ตลาด</p>'}`;
  }catch(e){if(el.isConnected)el.textContent='โหลดข้อมูลไม่ได้: '+e.message}
};

window.qgAdminVehicle=async(id,status,suggested)=>{
  if(!admin())return;
  const capacity=status==='active'?Number(prompt('ความจุที่ตรวจจากเอกสารรถ (กก.)',String(suggested||''))):null;
  if(status==='active'&&(!Number.isFinite(capacity)||capacity<=0||capacity>1000))return toast('ความจุไม่ถูกต้อง');
  if(!confirm(status==='active'?'ยืนยันว่าได้ตรวจรถและความจุแล้ว?':'ระงับรถคันนี้จากงานตลาด?'))return;
  try{
    await qtSupabaseRpc('market_verify_rider_vehicle',{p_rider:id,p_capacity:capacity,p_status:status});
    toast('บันทึกสถานะรถแล้ว');
    qgLoadMarketOperations();
  }catch(e){toast('บันทึกไม่ได้: '+e.message)}
};
})();