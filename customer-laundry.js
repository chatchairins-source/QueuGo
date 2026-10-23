(function(){
'use strict';
if(typeof V==='undefined')return;
const QG_LAUNDRY_STATUS={pending:'รอร้านรับ',accepted:'ร้านรับแล้ว · รอ Rider',pickup_assigned:'Rider กำลังไปรับผ้า',picked_up:'Rider รับผ้าแล้ว',at_hub:'ผ้าถึงร้านแล้ว',washing:'กำลังซัก/ทำความสะอาด',ready_return:'พร้อมส่งคืน',return_assigned:'Rider กำลังไปรับผ้าสะอาด',out_for_return:'กำลังส่งคืน',completed:'ส่งคืนสำเร็จ',cancelled:'ยกเลิก'};
const QG_LAUNDRY_FLOW=['pending','accepted','pickup_assigned','picked_up','at_hub','washing','ready_return','return_assigned','out_for_return','completed'];
function qgLaundryUnit(t){return ({per_kg:'กก.',per_item:'ชิ้น',per_set:'ชุด',fixed:'เหมาจ่าย'})[t]||''}

let customerLaundryCatalog=null;
async function qgLoadCustomerLaundryCatalog(){
  if(customerLaundryCatalog)return customerLaundryCatalog;
  customerLaundryCatalog=await db('rpc/queuego_laundry_customer_catalog',{method:'POST',body:{}});
  return customerLaundryCatalog;
}
function qgLaundryServiceAmount(service,quantity,minimum){
  const price=Number(service.price||0);
  if(service.pricing_type==='fixed')return Math.max(price,minimum);
  if(quantity==null)return null;
  return Math.max(price*quantity,minimum);
}
function qgLaundryDelivery(settings){
  return settings?.delivery_fee_mode==='round_trip'
    ?Number(settings.round_trip_fee||0)
    :Number(settings?.pickup_fee||0)+Number(settings?.return_fee||0);
}
function qgLaundryUnitPrice(service){
  const unit=qgLaundryUnit(service.pricing_type);
  return baht(service.price)+(unit?' / '+unit:'');
}
V.laundry=async function(id,ticket){
  ticket=ticket==null?routeVersion:ticket;
  const session=S.get();
  if(!session)return go('login');
  const result=await Promise.all([
    qgLoadCustomerLaundryCatalog(),
    loadShops(),
    loadServiceBannerConfig()
  ]);
  const catalog=result[0],shopRows=result[1]||[];
  if(ticket!==routeVersion)return;
  if(catalog?.enabled!==true){
    layout('<div class="pt"><button class="back" onclick="history.back()">'+ico('back')+'</button><h1>ฝากซัก</h1></div><section class="card"><b>ฝากซักยังไม่เปิดให้บริการ</b><p class="sm">Admin ยังปิดบริการฝากซักอยู่</p></section>','home');
    return;
  }
  const hubs=Array.isArray(catalog.hubs)?catalog.hubs:[];
  const profiles=new Map(shopRows.map(function(s){return [String(s.id),s]}));
  const eligible=hubs.map(function(h){
    const s=profiles.get(String(h.shop_id));
    return s?Object.assign({},s,{public_category:'laundry',laundryHub:h}):null;
  }).filter(Boolean);
  if(currentPos&&qgIsValidCoordinate(currentPos.lat,currentPos.lng,true)){
    eligible.sort(function(a,b){
      const da=calculateDistanceKm(currentPos.lat,currentPos.lng,a.latitude,a.longitude);
      const dbb=calculateDistanceKm(currentPos.lat,currentPos.lng,b.latitude,b.longitude);
      return (Number.isFinite(da)?da:Infinity)-(Number.isFinite(dbb)?dbb:Infinity);
    });
  }
  if(!id){
    const banner='assets/banners/qg_laundry_banner.webp?v=20261013-bright1';
    layout('<div class="pt"><button class="back" onclick="history.back()">'+ico('back')+'</button><h1>ฝากซัก</h1></div>'+
      qgServiceBannerMarkup('laundry',banner,'ฝากซัก','เลือกร้าน ดูบริการ และส่งคำขอได้ทันที')+
      '<section class="sec"><div class="st"><div><h2>ร้านฝากซักใกล้คุณ</h2><p class="sm" style="margin:4px 0 0">ร้านยืนยันก่อน แล้ว Rider รับผ้าและส่งคืนถึงคุณ</p></div><span class="sm">'+eligible.length+' ร้าน</span></div>'+
      '<div class="list">'+(eligible.length?eligible.map(shopCard).join(''):'<p class="empty">ยังไม่มีร้านฝากซักที่เปิดให้บริการ</p>')+'</div></section>','home');
    return;
  }
  const shop=eligible.find(function(s){return String(s.id)===String(id)});
  if(!shop){
    layout('<div class="pt"><button class="back" onclick="go(\'laundry\')">'+ico('back')+'</button><h1>ฝากซัก</h1></div><section class="card"><p class="empty">ไม่พบร้านฝากซักที่เปิดให้บริการ</p></section>','home');
    return;
  }
  const hub=shop.laundryHub,services=Array.isArray(hub.services)?hub.services:[];
  if(ticket!==routeVersion)return;
  const selected=services[0]||null;
  const delivery=qgLaundryDelivery(hub.settings||{});
  const cover=shop.public_cover||shop.public_logo||'';
  const coverHtml=cover?'<img src="'+esc(cover)+'" alt="" onerror="this.remove()">':'';
  const header='<div class="cover">'+coverHtml+'<div class="ca"><button class="ic" onclick="go(\'laundry\')" aria-label="กลับ">'+ico('back')+'</button><button class="ic" onclick="shareShop(\''+esc(shop.id)+'\')" aria-label="แชร์ร้าน">↗</button></div></div>'+
    '<div class="intro"><h1>'+esc(shop.shop_name||'ร้านฝากซัก')+'</h1><p><span>ร้านฝากซัก</span>'+(shop.address?'<span>'+esc(shop.address)+'</span>':'')+'</p><span class="pill '+(shop.open?'':'off')+'">'+(shop.open?'เปิดอยู่':'ปิดอยู่')+'</span></div>';
  const serviceRows=services.map(function(s,i){
    const checked=i===0?' checked':'';
    const price=qgLaundryUnitPrice(s);
    return '<div class="mi qg-laundry-service"><label style="display:flex;align-items:center;flex:none;margin:0"><input type="radio" name="qg-laundry-service" value="'+esc(s.id)+'"'+checked+'></label><div class="mc"><b>'+esc(s.name)+'</b><p>'+esc(s.description||'')+(s.estimated_minutes?' · ประมาณ '+Number(s.estimated_minutes)+' นาที':'')+'</p><div class="pr">'+price+'</div></div></div>';
  }).join('');
  layout(header+'<section class="sec"><div class="st"><div><h2>บริการฝากซัก</h2><p class="sm" style="margin:4px 0 0">เลือกรายการที่ต้องการ ร้านจะยืนยันราคาและจำนวนจริงก่อนเริ่มงาน</p></div></div>'+
    (serviceRows||'<div class="card"><p class="empty">ร้านนี้ยังไม่มีบริการที่เปิดขาย</p></div>')+
    '<div class="card">'+
      '<div id="qg-laundry-quantity" class="hidden"></div>'+
      '<div id="qg-laundry-quote"></div>'+
      '<label for="qg-laundry-note"><b>หมายเหตุ</b></label><textarea class="in" id="qg-laundry-note" maxlength="500" rows="3" placeholder="เช่น โทรก่อนถึง / มีผ้าสีตก"></textarea>'+
      '<button class="prim" id="qg-laundry-submit" '+(services.length&&shop.open?'':'disabled')+'>ส่งคำขอฝากซัก</button>'+
      '<p class="sm">ร้านยืนยันคำขอก่อนเริ่มบริการ และยอดสุดท้ายสรุปตามน้ำหนักหรือจำนวนจริง</p>'+
    '</div></section>','home');
  let chosen=selected,submitting=false;
  const quantityBox=document.getElementById('qg-laundry-quantity'),quoteBox=document.getElementById('qg-laundry-quote'),submit=document.getElementById('qg-laundry-submit');
  function renderLaundryQuantity(){
    if(!chosen)return;
    if(chosen.pricing_type==='fixed'){quantityBox.classList.add('hidden');quantityBox.innerHTML='';return}
    const label=chosen.pricing_type==='per_kg'?'น้ำหนักโดยประมาณ (กก.)':chosen.pricing_type==='per_item'?'จำนวนชิ้นโดยประมาณ':'จำนวนชุดโดยประมาณ';
    quantityBox.classList.remove('hidden');
    quantityBox.innerHTML='<label for="qg-laundry-qty"><b>'+label+'</b></label><input class="in" id="qg-laundry-qty" type="number" min="0.01" max="1000" step="'+(chosen.pricing_type==='per_kg'?'0.1':'1')+'" placeholder="ไม่แน่ใจสามารถเว้นว่างได้"><p class="sm">ใช้เพื่อประมาณราคา ร้านจะชั่งหรือนับจำนวนจริงก่อนสรุปยอด</p>';
    document.getElementById('qg-laundry-qty').addEventListener('input',renderLaundryQuote);
  }
  function renderLaundryQuote(){
    if(!chosen)return;
    const quantity=Number(document.getElementById('qg-laundry-qty')?.value||0)||null;
    const amount=qgLaundryServiceAmount(chosen,quantity,Number(hub.settings?.minimum_order||0));
    let feeRows=hub.settings?.delivery_fee_mode==='round_trip'
      ?'<div class="sr"><span>ค่ารับ-ส่งรวม</span><b>'+baht(hub.settings.round_trip_fee||0)+'</b></div>'
      :'<div class="sr"><span>ค่ารับผ้า</span><b>'+baht(hub.settings?.pickup_fee||0)+'</b></div><div class="sr"><span>ค่าส่งคืน</span><b>'+baht(hub.settings?.return_fee||0)+'</b></div>';
    quoteBox.innerHTML=feeRows+'<div class="sr"><span>ค่าบริการ'+(amount==null?' (สรุปตามจำนวนจริง)':'')+'</span><b>'+(amount==null?'รอร้านสรุป':baht(amount))+'</b></div><div class="tot"><span>ยอดประมาณ</span><span>'+(amount==null?'รอสรุปจำนวนจริง':baht(amount+delivery))+'</span></div>';
  }
  document.querySelectorAll('[name="qg-laundry-service"]').forEach(function(input){
    input.addEventListener('change',function(){
      chosen=services.find(function(s){return String(s.id)===String(input.value)})||selected;
      renderLaundryQuote();
    });
  });
  renderLaundryQuote();
  if(submit)submit.addEventListener('click',async function(){
    if(submitting)return;
    if(!chosen)return alert('ร้านนี้ยังไม่มีบริการที่เปิดขาย');
    const qty=chosen.pricing_type==='fixed'?null:(Number(document.getElementById('qg-laundry-qty')?.value||0)||null);
    if(chosen.pricing_type!=='fixed'&&qty==null)return alert('กรุณาระบุจำนวนโดยประมาณ หรือให้ร้านสรุปจำนวนจริง');
    const userRows=await db('users?select=id,metadata,role,status&id=eq.'+encodeURIComponent(session.userId)+'&limit=1');
    const user=Array.isArray(userRows)?userRows[0]:null,meta=user?.metadata||{},position=currentPos||{};
    const address=String(position.address||meta.address||meta.delivery_address||meta.location_address||'').trim();
    const latitude=Number(position.lat??position.latitude??meta.lat??meta.latitude),longitude=Number(position.lng??position.longitude??meta.lng??meta.longitude);
    if(!address||!Number.isFinite(latitude)||!Number.isFinite(longitude)){
      alert('กรุณาบันทึกที่อยู่และพิกัดจัดส่งในโปรไฟล์ก่อน');
      return go('profile');
    }
    let request=null;const pendingKey='qg_laundry_pending_'+session.userId;
    try{request=JSON.parse(localStorage.getItem(pendingKey)||'null')}catch(e){}
    if(!request||request.hub_id!==hub.id||request.service_id!==chosen.id||String(request.qty??'')!==String(qty??'')){
      request={request_id:crypto.randomUUID(),hub_id:hub.id,service_id:chosen.id,qty};
      localStorage.setItem(pendingKey,JSON.stringify(request));
    }
    submitting=true;if(submit){submit.disabled=true;submit.textContent='กำลังส่งคำขอ...'}
    try{
      const data=await db('rpc/queuego_place_laundry_order_v2',{method:'POST',body:{
        p_request_id:request.request_id,p_hub_id:hub.id,p_service_id:chosen.id,
        p_pickup_address:address,p_pickup_latitude:latitude,p_pickup_longitude:longitude,
        p_estimated_quantity:qty,p_note:String(document.getElementById('qg-laundry-note')?.value||'').trim().slice(0,500)||null
      }});
      localStorage.removeItem(pendingKey);
      alert('ส่งคำขอฝากซักแล้ว '+QueueGoOrderNumber.format(data)+' รอร้านกดรับคำขอ');
      go('orders');
    }catch(e){
      alert('ส่งคำขอไม่สำเร็จ: '+String(e?.message||e).slice(0,140));
    }finally{
      submitting=false;if(submit){submit.disabled=!shop.open;submit.textContent='ส่งคำขอฝากซัก'}
    }
  });
};

V['laundry-order']=async function(id,ticket){
  ticket=ticket==null?routeVersion:ticket;
  const u=S.get();if(!u)return go('login');
  const rows=await db('laundry_orders?select=*&id=eq.'+encodeURIComponent(id)+'&customer_id=eq.'+encodeURIComponent(u.userId)+'&limit=1');
  const o=Array.isArray(rows)&&rows[0];if(ticket!==routeVersion)return;
  if(!o)return layout('<p class="empty">ไม่พบงานฝากซัก</p>','orders');
  let events=[];try{events=await db('laundry_order_events?select=from_status,to_status,note,created_at&laundry_order_id=eq.'+encodeURIComponent(id)+'&order=created_at.asc')}catch(e){}
  if(ticket!==routeVersion)return;
  const idx=QG_LAUNDRY_FLOW.indexOf(o.status);
  const delivery=Number(o.delivery_fee_total_snapshot!=null?o.delivery_fee_total_snapshot:(Number(o.pickup_fee_snapshot||0)+Number(o.return_fee_snapshot||0)))||0;
  const qty=o.actual_quantity!=null?o.actual_quantity:(o.actual_kg!=null?o.actual_kg:(o.estimated_quantity!=null?o.estimated_quantity:o.estimated_kg));
  const serviceAmount=o.final_amount!=null?o.final_amount:o.estimated_amount;
  const total=o.final_total_amount!=null?o.final_total_amount:(o.estimated_total_amount!=null?o.estimated_total_amount:(serviceAmount!=null?Number(serviceAmount)+delivery:null));
  let html='<div class="pt"><button class="back" onclick="go(\'orders\')">'+ico('back')+'</button><h1>'+esc(QueueGoOrderNumber.format(o))+'</h1></div>';
  html+='<section class="card" style="margin-top:0"><div class="sr"><b>ฝากซัก · '+esc(QG_LAUNDRY_STATUS[o.status]||o.status)+'</b><button class="lk" onclick="route()">โหลดสถานะล่าสุด</button></div><p class="sm">'+esc(o.service_name_snapshot||o.service_type||'บริการฝากซัก')+'</p><p class="sm">'+esc(o.pickup_address||'')+'</p>';
  if(o.status==='cancelled')html+='<p style="color:#b4233c"><b>งานนี้ถูกยกเลิก</b></p>';
  else html+='<div class="statusflow">'+QG_LAUNDRY_FLOW.map(function(x,i){return '<span class="'+(idx>=0&&i<=idx?'on':'')+'">'+esc(QG_LAUNDRY_STATUS[x])+'</span>'}).join('')+'</div>';
  html+='</section><section class="card"><b>สรุปราคา</b>';
  html+='<div class="sr"><span>รูปแบบราคา</span><span>'+esc(o.pricing_type_snapshot==='fixed'?'เหมาจ่าย':(o.unit_price_snapshot!=null?baht(o.unit_price_snapshot)+' / '+qgLaundryUnit(o.pricing_type_snapshot):'-'))+'</span></div>';
  if(qty!=null)html+='<div class="sr"><span>'+(o.actual_quantity!=null||o.actual_kg!=null?'จำนวนจริง':'จำนวนประมาณ')+'</span><span>'+Number(qty).toLocaleString('th-TH')+' '+esc(qgLaundryUnit(o.pricing_type_snapshot))+'</span></div>';
  html+='<div class="sr"><span>ค่าบริการ</span><span>'+(serviceAmount!=null?baht(serviceAmount):'รอร้านชั่ง/นับจริง')+'</span></div>';
  html+='<div class="sr"><span>ค่ารับผ้า</span><span>'+baht(o.pickup_fee_snapshot||0)+'</span></div><div class="sr"><span>ค่าส่งคืน</span><span>'+baht(o.return_fee_snapshot||0)+'</span></div>';
  html+='<div class="tot"><span>รวม'+(o.final_total_amount!=null?'':'โดยประมาณ')+'</span><span>'+(total!=null?baht(total):'รอสรุป')+'</span></div></section>';
  html+='<section class="card"><b>ประวัติสถานะ</b>';
  html+=(events||[]).length?(events||[]).map(function(e){return '<div style="padding:9px 0;border-bottom:1px solid #eee"><b>'+esc(QG_LAUNDRY_STATUS[e.to_status]||e.to_status)+'</b><br><small>'+new Date(e.created_at).toLocaleString('th-TH')+(e.note?' · '+esc(e.note):'')+'</small></div>'}).join(''):'<p class="empty">ยังไม่มีประวัติเพิ่มเติม</p>';
  html+='</section>';
  layout(html,'orders');
};
V.orders=async function(_,ticket){
  ticket=ticket==null?routeVersion:ticket;
  const u=S.get();if(!u)return go('login');
  const out=await Promise.all([
    db('orders?select=*,order_items(quantity)&customer_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=50'),
    db('market_orders?select=id,status,shop_count,total_amount,delivery_address,created_at&customer_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=30').catch(function(){return []}),
    db('laundry_orders?select=id,order_number,status,service_name_snapshot,estimated_total_amount,final_total_amount,pickup_address,created_at&customer_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=30').catch(function(){return []})
  ]);
  if(ticket!==routeVersion)return;
  const rows=out[0]||[],trips=out[1]||[],laundry=out[2]||[];
  const shops=await loadShops();if(ticket!==routeVersion)return;const shopNames=new Map(shops.map(s=>[s.id,s.shop_name]));
  const normal=rows.filter(function(o){return !o.market_order_id}).map(function(o){return {kind:'order',created_at:o.created_at,data:o}});
  const market=trips.map(function(t){return {kind:'market',created_at:t.created_at,data:t}});
  const wash=laundry.map(function(t){return {kind:'laundry',created_at:t.created_at,data:t}});
  const all=normal.concat(market,wash).sort(function(a,b){return new Date(b.created_at)-new Date(a.created_at)});
  let cards='';
  if(all.length){
    cards=all.map(function(entry){
      if(entry.kind==='market'){
        const t=entry.data,status=String(t.status||'PENDING').toUpperCase();
        return '<div class="oc" onclick="go(\'market-order/'+esc(t.id)+'\')"><div class="oh"><span class="on">ตลาดสด · '+Number(t.shop_count||0)+' ร้าน</span><span class="sp">'+esc(MARKET_TRIP_STATUS[status]||status)+'</span></div><p>'+new Date(t.created_at).toLocaleString('th-TH')+'</p><b>'+baht(t.total_amount)+'</b></div>';
      }
      if(entry.kind==='laundry'){
        const l=entry.data,total=l.final_total_amount!=null?l.final_total_amount:l.estimated_total_amount;
        return '<div class="oc" onclick="go(\'laundry-order/'+esc(l.id)+'\')"><div class="oh"><span class="on">ฝากซัก · '+esc(QueueGoOrderNumber.format(l))+'</span><span class="sp">'+esc(QG_LAUNDRY_STATUS[l.status]||l.status)+'</span></div><p>'+new Date(l.created_at).toLocaleString('th-TH')+' · '+esc(l.service_name_snapshot||'บริการฝากซัก')+'</p><b>'+(total!=null?baht(total):'รอสรุปราคา')+'</b></div>';
      }
      const o=entry.data;
      const riderOrder=customerOrderNumber(o);
      const stage=o.status==='completed'?4:(o.status==='picked_up'||o.status==='in_progress'?3:(['rider_assigned','preparing','ready','assigned'].includes(o.status)?2:(o.status==='searching_rider'?1:0)));
      const stageLabels=['รอร้านรับ','หาไรเดอร์','รับสินค้า','กำลังส่ง','สำเร็จ'];
      const progress=o.status==='cancelled'?'':('<div class="qg-order-list-progress">'+stageLabels.map((label,i)=>'<span class="qg-order-list-step '+(o.status==='completed'?'done':i<stage?'done':i===stage?'current':'')+'">'+esc(label)+'</span>').join('')+'</div>');
      const statusClass=o.status==='completed'?'done':o.status==='cancelled'?'cancelled':'';
      return '<div class="oc qg-order-list-card" onclick="go(\'order/'+esc(o.id)+'\')"><div class="oh"><span class="on qg-order-list-number">'+esc(riderOrder)+'</span><span class="qg-order-list-status '+statusClass+'">'+esc(STATUS_LABEL[o.status]||'กำลังดำเนินการ')+'</span></div><p class="qg-order-list-meta">'+esc(shopNames.get(o.shop_id)||'ร้านค้า')+' · '+(o.order_items||[]).reduce((sum,i)=>sum+Number(i.quantity||0),0)+' รายการ</p><p class="qg-order-list-date">'+new Date(o.created_at).toLocaleString('th-TH')+(Number(o.bundle_customer_savings||0)>0?' · งานพ่วงประหยัด '+baht(o.bundle_customer_savings):'')+'</p>'+progress+'<b class="qg-order-list-total">'+baht(o.total!=null?o.total:(o.total_amount!=null?o.total_amount:o.subtotal))+'</b></div>';
    }).join('');
  }else cards='<p class="empty">ยังไม่มีออเดอร์</p>';
  layout('<div class="pt"><h1>ออเดอร์ของฉัน</h1></div><div class="list">'+cards+'</div>','orders');
};
function qgLaundryDeepLinkBoot(){if(/^#(?:orders|laundry-order\/)/.test(location.hash)&&S.get())setTimeout(()=>route(),0)}
qgLaundryDeepLinkBoot();
})();