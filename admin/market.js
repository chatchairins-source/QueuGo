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
const rowValue=(r,fallback)=>{
  if(!r)return fallback;
  const v=r.value;
  if(typeof fallback==='boolean')return v===true||v==='true';
  if(typeof fallback==='number'){const x=Number(v);return Number.isFinite(x)?x:fallback}
  return v??fallback;
};
const currentRow=(rows,key)=>{
  const now=Date.now();
  return (rows||[]).filter(r=>r.rule_key===key&&new Date(r.effective_from).getTime()<=now)
    .sort((a,b)=>new Date(b.effective_from)-new Date(a.effective_from)||new Date(b.created_at)-new Date(a.created_at))[0]||null;
};
const nextRow=(rows,key)=>{
  const now=Date.now();
  return (rows||[]).filter(r=>r.rule_key===key&&new Date(r.effective_from).getTime()>now)
    .sort((a,b)=>new Date(a.effective_from)-new Date(b.effective_from))[0]||null;
};
const val=(rows,key,fallback)=>rowValue(currentRow(rows,key),fallback);
const checked=v=>v?'checked':'';
const n=(id,min=0,max=Number.POSITIVE_INFINITY)=>{
  const x=Number(document.getElementById(id)?.value);
  if(!Number.isFinite(x)||x<min||x>max)throw Error('ค่าที่กรอกไม่ถูกต้อง');
  return x;
};
function futureLabel(rows,keys){
  const next=keys.map(k=>nextRow(rows,k)).filter(Boolean).sort((a,b)=>new Date(a.effective_from)-new Date(b.effective_from))[0];
  return next?'<div class="qg-admin-scheduled">มีค่าที่ตั้งล่วงหน้า เริ่ม '+new Date(next.effective_from).toLocaleString('th-TH')+'</div>':'';
}
function pricingCard(title,body,type){
  return '<section class="qg-admin-control-card"><h3>'+title+'</h3>'+body+
    '<label class="qg-admin-field">มีผลตั้งแต่<input id="qg-'+type+'-effective" type="datetime-local" value="'+localDateTime()+'"></label>'+
    '<button class="qg-admin-save" type="button" onclick="qgSavePlatformPricing(\''+type+'\')">บันทึกราคา/กติกา</button></section>';
}
async function insertRules(rows){
  if(!rows.length)return;
  return write('queuego_platform_rules?on_conflict=rule_key,effective_from','POST',rows);
}

window.renderAdminMarket=async()=>{
  if(!admin())return navigate('login');
  layout('ตลาดสด',`<section class="card qg-admin-market">
    <button class="qg-admin-back" onclick="navigate('admin')">‹ ศูนย์ควบคุม</button>
    <h1>ตลาดสด · งานพ่วง · GP</h1>
    <p>ค่าที่เปิด/ปิดจากหน้านี้เก็บใน Supabase และมีผลกับออเดอร์ใหม่โดยไม่ต้อง Build APK ใหม่</p>
    <div id="qg-platform-controls">กำลังโหลดการตั้งค่าจริง...</div>
    <div id="qg-market-admin">กำลังโหลดข้อมูลตลาดและรถรับงาน...</div>
  </section>`);
  await Promise.allSettled([qgLoadPlatformControls(),qgLoadMarketOperations()]);
};

window.qgLoadPlatformControls=async()=>{
  const el=document.getElementById('qg-platform-controls');if(!el||!admin())return;
  try{
    const rules=await api('queuego_platform_rules?select=id,rule_key,value,effective_from,note,created_at&order=effective_from.desc');
    if(!el.isConnected)return;
    const marketMulti=val(rules,'feature.market_multi_shop',true);
    const routeBundle=val(rules,'feature.route_bundle',false);
    const gpEnabled=val(rules,'feature.gp',true);
    const market={
      base_fee:val(rules,'pricing.market_base_fee',30),
      second_shop_fee:val(rules,'pricing.market_second_shop_fee',10),
      additional_shop_fee:val(rules,'pricing.market_additional_shop_fee',5)
    };
    const route={
      max_detour_km:val(rules,'route_bundle.max_detour_km',1.5),
      max_delay_minutes:val(rules,'route_bundle.max_delay_minutes',10),
      max_orders:val(rules,'route_bundle.max_orders',2),
      min_rider_extra_fee:val(rules,'route_bundle.min_rider_extra_fee',10)
    };
    let gpRate=val(rules,'pricing.gp_default_rate',NaN);
    if(!Number.isFinite(gpRate)){
      try{
        const legacy=await api('system_settings?select=value&key=eq.gp&limit=1');
        gpRate=Number(legacy?.[0]?.value?.default_rate);
      }catch(_){}
      if(!Number.isFinite(gpRate))gpRate=10;
    }
    el.innerHTML=`
      <section class="qg-admin-control-card qg-admin-switches">
        <h2>สวิตช์ระบบช่วงทดลอง</h2>
        <label><span><b>ตลาดสดซื้อหลายร้าน</b><small>ปิด = ตลาดสดยังสั่งร้านเดียวได้ แต่สร้าง Market Trip หลายร้านไม่ได้</small></span><input id="qg-flag-market" type="checkbox" ${checked(marketMulti)}></label>
        <label><span><b>งานพ่วง Route Bundle</b><small>อาหาร · เครื่องดื่ม · ร้านขายของชำ</small></span><input id="qg-flag-bundle" type="checkbox" ${checked(routeBundle)}></label>
        <label><span><b>คิด GP</b><small>ปิด = ออเดอร์ใหม่ GP 0% · ออเดอร์เก่าใช้ snapshot เดิม</small></span><input id="qg-flag-gp" type="checkbox" ${checked(gpEnabled)}></label>
        <button class="qg-admin-save" type="button" onclick="qgSaveFeatureFlags()">บันทึกสวิตช์</button>
      </section>
      <div class="qg-admin-control-grid">
        ${pricingCard('ค่าบริการตลาดสด',`
          <label class="qg-admin-field">ค่ารอบพื้นฐาน<input id="qg-market-base" type="number" min="0" step="1" value="${esc(market.base_fee)}"></label>
          <label class="qg-admin-field">ร้านที่ 2 เพิ่ม<input id="qg-market-second" type="number" min="0" step="1" value="${esc(market.second_shop_fee)}"></label>
          <label class="qg-admin-field">ร้านที่ 3 เป็นต้นไป / ร้าน<input id="qg-market-more" type="number" min="0" step="1" value="${esc(market.additional_shop_fee)}"></label>
          ${futureLabel(rules,['pricing.market_base_fee','pricing.market_second_shop_fee','pricing.market_additional_shop_fee'])}
        `,'market')}
        ${pricingCard('งานพ่วง Route Bundle',`
          <label class="qg-admin-field">อ้อมได้สูงสุด (กม.)<input id="qg-route-detour" type="number" min="0" max="50" step=".1" value="${esc(route.max_detour_km)}"></label>
          <label class="qg-admin-field">เพิ่มเวลาสูงสุด (นาที)<input id="qg-route-delay" type="number" min="0" max="240" step="1" value="${esc(route.max_delay_minutes)}"></label>
          <label class="qg-admin-field">จำนวนออเดอร์พร้อมกันสูงสุด<input id="qg-route-jobs" type="number" min="1" max="5" step="1" value="${esc(route.max_orders)}"></label>
          <label class="qg-admin-field">รายรับ Rider เพิ่มขั้นต่ำ<input id="qg-route-rider-min" type="number" min="0" step="1" value="${esc(route.min_rider_extra_fee)}"></label>
          ${futureLabel(rules,['route_bundle.max_detour_km','route_bundle.max_delay_minutes','route_bundle.max_orders','route_bundle.min_rider_extra_fee'])}
        `,'route')}
        ${pricingCard('GP มาตรฐาน',`
          <label class="qg-admin-field">GP (%)<input id="qg-gp-rate" type="number" min="0" max="100" step=".1" value="${esc(gpRate)}"></label>
          <small>อัตราพิเศษรายร้านที่มีอยู่เดิมยังมีผลก่อนอัตรามาตรฐาน</small>
          ${futureLabel(rules,['pricing.gp_default_rate'])}
        `,'gp')}
      </div>`;
  }catch(e){if(el.isConnected)el.textContent='โหลดการตั้งค่าไม่ได้: '+e.message}
};

window.qgSaveFeatureFlags=async()=>{
  if(!admin())return;
  const at=new Date().toISOString();
  const rows=[
    {rule_key:'feature.market_multi_shop',value:!!document.getElementById('qg-flag-market')?.checked,effective_from:at,note:'Admin feature switch'},
    {rule_key:'feature.route_bundle',value:!!document.getElementById('qg-flag-bundle')?.checked,effective_from:at,note:'Admin feature switch'},
    {rule_key:'feature.gp',value:!!document.getElementById('qg-flag-gp')?.checked,effective_from:at,note:'Admin feature switch'}
  ];
  try{
    await insertRules(rows);
    try{qtAudit('platform_feature_flags_update',{entityType:'queuego_platform_rules',entityId:'features',value:rows.map(x=>({key:x.rule_key,value:x.value}))})}catch(_){}
    toast('บันทึกสวิตช์ระบบแล้ว');
    await qgLoadPlatformControls();
  }catch(e){toast('บันทึกสวิตช์ไม่ได้: '+e.message)}
};

window.qgSavePlatformPricing=async type=>{
  if(!admin())return;
  try{
    const raw=document.getElementById('qg-'+type+'-effective')?.value;
    const effective=raw?new Date(raw):new Date();
    if(Number.isNaN(effective.getTime()))throw Error('วันเวลาที่เริ่มใช้ไม่ถูกต้อง');
    const at=effective.toISOString();
    let rows=[];
    if(type==='market'){
      rows=[
        {rule_key:'pricing.market_base_fee',value:n('qg-market-base'),effective_from:at,note:'Admin market pricing'},
        {rule_key:'pricing.market_second_shop_fee',value:n('qg-market-second'),effective_from:at,note:'Admin market pricing'},
        {rule_key:'pricing.market_additional_shop_fee',value:n('qg-market-more'),effective_from:at,note:'Admin market pricing'}
      ];
    }else if(type==='route'){
      rows=[
        {rule_key:'route_bundle.max_detour_km',value:n('qg-route-detour',0,50),effective_from:at,note:'Admin route bundle rule'},
        {rule_key:'route_bundle.max_delay_minutes',value:n('qg-route-delay',0,240),effective_from:at,note:'Admin route bundle rule'},
        {rule_key:'route_bundle.max_orders',value:Math.round(n('qg-route-jobs',1,5)),effective_from:at,note:'Admin route bundle rule'},
        {rule_key:'route_bundle.min_rider_extra_fee',value:n('qg-route-rider-min',0),effective_from:at,note:'Admin route bundle rule'}
      ];
    }else if(type==='gp'){
      rows=[{rule_key:'pricing.gp_default_rate',value:n('qg-gp-rate',0,100),effective_from:at,note:'Admin default GP rate'}];
    }else throw Error('ไม่รู้จักประเภทการตั้งค่า');
    await insertRules(rows);
    if(type==='gp'&&effective.getTime()<=Date.now()+1000){
      const rate=Number(rows[0].value);
      await write('system_settings?on_conflict=key','POST',{key:'gp',value:{default_rate:rate},updated_at:new Date().toISOString()});
      window.QT_GP_DEFAULT_RATE=rate;
    }
    try{qtAudit('platform_pricing_rule_update',{entityType:'queuego_platform_rules',entityId:type,effective_from:at,values:rows.map(x=>({key:x.rule_key,value:x.value}))})}catch(_){}
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