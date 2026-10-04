(()=>{
'use strict';
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let cache={user:null,hub:null,at:0};
async function request(path,options={}){
  const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
  return qtSupabaseTable(path,{...options,accessToken:token});
}
async function enabled(){
  try{return (await request('rpc/queuego_feature_enabled',{method:'POST',body:{p_feature:'laundry'}}))===true}catch(e){return false}
}
async function ownHub(force=false){
  const u=currentUser();if(!u||u.type!=='shop')return null;
  if(!force&&cache.user===u.id&&Date.now()-cache.at<30000)return cache.hub;
  const shops=await request('shop_profiles?select=id,shop_name,user_id&user_id=eq.'+encodeURIComponent(u.id)+'&limit=1');
  const sp=Array.isArray(shops)?shops[0]:null;
  if(!sp){cache={user:u.id,hub:null,at:Date.now()};return null}
  const hubs=await request('laundry_hubs?select=id,name,shop_id,active&shop_id=eq.'+encodeURIComponent(sp.id)+'&active=eq.true&limit=1');
  const hub=Array.isArray(hubs)?hubs[0]:null;cache={user:u.id,hub,at:Date.now()};return hub;
}
window.qgLaundryMountMerchantEntry=async function(u){
  if(!u||u.type!=='shop')return;
  try{
    const [on,hub]=await Promise.all([enabled(),ownHub()]);
    if(!on||!hub||String(currentUser()?.id)!==String(u.id))return;
    const host=document.querySelector('.qgm-menu-grid');if(!host||host.querySelector('[data-qg-laundry]'))return;
    const b=document.createElement('button');b.type='button';b.className='qgm-menu';b.dataset.qgLaundry='1';
    b.innerHTML='<span class="ico">🧺</span><span>ฝากซัก</span>';b.onclick=()=>navigate('shop-laundry');host.prepend(b);
  }catch(e){console.warn('laundry merchant entry unavailable')}
};
const STATUS={
 pending:'รอร้านรับ',accepted:'รอ Rider รับงาน',pickup_assigned:'Rider ไปรับผ้า',picked_up:'กำลังนำผ้ามาร้าน',
 at_hub:'ผ้าถึงร้าน',washing:'กำลังซัก',ready_return:'พร้อมส่งคืน',return_assigned:'Rider รับงานส่งคืน',
 out_for_return:'กำลังส่งคืน',completed:'เสร็จแล้ว',cancelled:'ยกเลิก'
};
function actions(o,settings){
  if(o.status==='pending')return '<button class="qgl-primary" onclick="qgLaundryShopAction(\''+esc(o.id)+'\',\'accept\')">รับคำขอ</button><button class="qgl-outline" onclick="qgLaundryShopCancel(\''+esc(o.id)+'\')">ปฏิเสธ</button>';
  if(o.status==='accepted')return '<button class="qgl-outline" onclick="qgLaundryShopCancel(\''+esc(o.id)+'\')">ยกเลิกก่อน Rider รับงาน</button>';
  if(o.status==='at_hub')return '<button class="qgl-primary" onclick="qgLaundryShopAction(\''+esc(o.id)+'\',\'start_washing\')">เริ่มซัก</button>';
  if(o.status==='washing')return '<button class="qgl-primary" onclick="qgLaundryReadyReturn(\''+esc(o.id)+'\',\''+esc(o.pricing_type_snapshot||'')+'\')">ซักเสร็จ · พร้อมส่งคืน</button>';
  if(o.status==='ready_return'&&!settings?.accepts_return)return '<button class="qgl-primary" onclick="qgLaundryShopAction(\''+esc(o.id)+'\',\'complete_at_hub\')">ลูกค้ารับที่ร้าน · ปิดงาน</button>';
  return '';
}
window.qgRenderLaundryMerchant=async function(){
  const u=currentUser();if(!u||u.type!=='shop')return navigate('login');
  qtShopLayout('ฝากซัก','<div class="qgl-head"><button class="qgm-iconbtn" onclick="navigate(\'dashboard\')">←</button><div><h2>ฝากซัก</h2><small>คำขอจริงจากลูกค้า · การเปลี่ยนสถานะผ่าน Server RPC</small></div></div><div id="qgl-body" class="qgm-card qgl-loading">กำลังโหลด...</div>','dashboard');
  const body=document.getElementById('qgl-body');
  try{
    if(!await enabled()){body.innerHTML='<div class="qgl-empty">ฟีเจอร์ฝากซักถูกปิดจาก Admin อยู่</div>';return}
    const hub=await ownHub(true);if(!hub){body.innerHTML='<div class="qgl-empty">ร้านนี้ยังไม่ได้ผูก Laundry Hub</div>';return}
    const [settingsRows,orders]=await Promise.all([
      request('laundry_shop_settings?select=hub_id,enabled,accepts_pickup,accepts_return,minimum_order,base_pickup_fee,return_fee,round_trip_fee&hub_id=eq.'+encodeURIComponent(hub.id)+'&limit=1'),
      request('laundry_orders?select=id,order_number,status,service_type,service_name_snapshot,pricing_type_snapshot,unit_price_snapshot,actual_kg,final_amount,pickup_address,note,created_at,updated_at&hub_id=eq.'+encodeURIComponent(hub.id)+'&order=created_at.desc&limit=100')
    ]);
    const settings=Array.isArray(settingsRows)?settingsRows[0]:null,rows=Array.isArray(orders)?orders:[];
    body.className='';
    body.innerHTML='<div class="qgl-summary qgm-card"><b>'+esc(hub.name)+'</b><small>'+rows.filter(x=>!['completed','cancelled'].includes(x.status)).length+' งานที่ยังไม่จบ</small></div>'+
      '<div class="qgl-list">'+(rows.length?rows.map(o=>'<article class="qgm-card qgl-order"><div class="qgl-top"><div><b>'+esc(o.order_number)+'</b><small>'+esc(o.service_name_snapshot||o.service_type||'บริการซัก')+' · '+new Date(o.created_at).toLocaleString('th-TH')+'</small></div><span>'+esc(STATUS[o.status]||o.status)+'</span></div><div class="qgl-address">'+esc(o.pickup_address||'-')+'</div>'+(o.actual_kg?'<div class="qgl-meta">น้ำหนักจริง '+Number(o.actual_kg).toLocaleString('th-TH')+' กก.</div>':'')+(o.final_amount!=null?'<div class="qgl-meta">ค่าบริการ '+Number(o.final_amount).toLocaleString('th-TH')+' บาท</div>':'')+'<div class="qgl-actions">'+actions(o,settings)+'</div></article>').join(''):'<div class="qgl-empty">ยังไม่มีคำขอฝากซัก</div>')+'</div>';
  }catch(e){if(body)body.innerHTML='<div class="qgl-empty">โหลดงานฝากซักไม่สำเร็จ: '+esc(String(e.message||e).slice(0,120))+'</div>'}
};
window.qgLaundryShopAction=async function(id,action,kg=null,note=null){
  try{
    await request('rpc/queuego_laundry_shop_action',{method:'POST',body:{p_order_id:id,p_action:action,p_actual_kg:kg,p_note:note}});
    toast('อัปเดตงานฝากซักแล้ว');await qgRenderLaundryMerchant();
  }catch(e){toast('อัปเดตไม่สำเร็จ: '+String(e.message||e).slice(0,100))}
};
window.qgLaundryReadyReturn=function(id,pricing){
  let kg=null;if(pricing==='per_kg'){const v=prompt('น้ำหนักจริง (กก.)');if(v===null)return;kg=Number(v);if(!Number.isFinite(kg)||kg<=0)return toast('น้ำหนักไม่ถูกต้อง')}
  qgLaundryShopAction(id,'ready_return',kg);
};
window.qgLaundryShopCancel=function(id){const reason=prompt('เหตุผลที่ปฏิเสธ/ยกเลิก');if(reason===null)return;const note=String(reason).trim();if(!note)return toast('กรุณาระบุเหตุผล');qgLaundryShopAction(id,'cancel',null,note.slice(0,500))};
})();