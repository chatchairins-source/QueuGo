(function(){
'use strict';
if(typeof V==='undefined')return;
const QG_LAUNDRY_STATUS={pending:'รอร้านรับ',accepted:'ร้านรับแล้ว · รอ Rider',pickup_assigned:'Rider กำลังไปรับผ้า',picked_up:'Rider รับผ้าแล้ว',at_hub:'ผ้าถึงร้านแล้ว',washing:'กำลังซัก/ทำความสะอาด',ready_return:'พร้อมส่งคืน',return_assigned:'Rider กำลังไปรับผ้าสะอาด',out_for_return:'กำลังส่งคืน',completed:'ส่งคืนสำเร็จ',cancelled:'ยกเลิก'};
const QG_LAUNDRY_FLOW=['pending','accepted','pickup_assigned','picked_up','at_hub','washing','ready_return','return_assigned','out_for_return','completed'];
function qgLaundryUnit(t){return ({per_kg:'กก.',per_item:'ชิ้น',per_set:'ชุด',fixed:'เหมาจ่าย'})[t]||''}
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
  let html='<div class="pt"><button class="back" onclick="go(\'orders\')">'+ico('back')+'</button><h1>#'+esc(o.order_number||String(o.id).slice(0,8))+'</h1></div>';
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
    db('orders?select=*&customer_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=50'),
    db('market_orders?select=id,status,shop_count,total_amount,delivery_address,created_at&customer_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=30').catch(function(){return []}),
    db('laundry_orders?select=id,order_number,status,service_name_snapshot,estimated_total_amount,final_total_amount,pickup_address,created_at&customer_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=30').catch(function(){return []})
  ]);
  if(ticket!==routeVersion)return;
  const rows=out[0]||[],trips=out[1]||[],laundry=out[2]||[];
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
        return '<div class="oc" onclick="go(\'laundry-order/'+esc(l.id)+'\')"><div class="oh"><span class="on">ฝากซัก · #'+esc(l.order_number||String(l.id).slice(0,6))+'</span><span class="sp">'+esc(QG_LAUNDRY_STATUS[l.status]||l.status)+'</span></div><p>'+new Date(l.created_at).toLocaleString('th-TH')+' · '+esc(l.service_name_snapshot||'บริการฝากซัก')+'</p><b>'+(total!=null?baht(total):'รอสรุปราคา')+'</b></div>';
      }
      const o=entry.data;
      return '<div class="oc" onclick="go(\'order/'+esc(o.id)+'\')"><div class="oh"><span class="on">#'+esc(o.order_number||String(o.id).slice(0,6))+'</span><span class="sp">'+esc(STATUS_LABEL[o.status]||'กำลังดำเนินการ')+'</span></div><p>'+new Date(o.created_at).toLocaleString('th-TH')+(Number(o.bundle_customer_savings||0)>0?' · งานพ่วงประหยัด '+baht(o.bundle_customer_savings):'')+'</p><b>'+baht(o.total!=null?o.total:(o.total_amount!=null?o.total_amount:o.subtotal))+'</b></div>';
    }).join('');
  }else cards='<p class="empty">ยังไม่มีออเดอร์</p>';
  layout('<div class="pt"><h1>ออเดอร์ของฉัน</h1></div><div class="list">'+cards+'</div>','orders');
};
})();