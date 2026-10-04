/* QueueGo Merchant Market Membership
   Uses the existing shop profile/location and real Supabase market RPCs. */
(()=>{
'use strict';
const MARKET_CATS=new Set(['market','meat','fish','vegetable','fruit']);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const km=v=>Number(v||0).toLocaleString('th-TH',{minimumFractionDigits:0,maximumFractionDigits:2});
const marketStatusLabel=s=>({
  none:'ยังไม่ได้สมัคร',
  suggested:'พบตลาดใกล้ร้าน',
  pending:'รอแอดมินตรวจสอบ',
  approved:'อนุมัติแล้ว',
  rejected:'ไม่ผ่านการตรวจสอบ'
}[s]||'ยังไม่ได้สมัคร');

async function ownShop(){
  const u=currentUser();
  if(!u||u.type!=='shop')throw Error('กรุณาเข้าสู่ระบบร้านค้า');
  const token=await qtGetAccessToken();
  if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
  const rows=await qtSupabaseTable(
    'shop_profiles?select=id,user_id,shop_name,public_category,status,address,latitude,longitude,market_id,market_suggested_id,market_suggested_distance_km,market_membership_status,market_stall_no,market_zone,market_proof_path,market_rejection_reason,public_cover,metadata&user_id=eq.'+
    encodeURIComponent(u.id)+'&limit=1',
    {accessToken:token}
  );
  const shop=Array.isArray(rows)?rows[0]:null;
  if(!shop)throw Error('ไม่พบข้อมูลร้าน');
  return {u,token,shop};
}

async function nearby(lat,lng){
  const rows=await qtSupabaseRpc('queuego_nearby_markets',{
    p_lat:Number(lat),p_lng:Number(lng),p_max_km:10
  });
  return Array.isArray(rows)?rows:[];
}

function setupMarketEntry(){
  const form=document.getElementById('qgm-shop-form');
  if(!form||document.getElementById('qgm-market-membership-entry'))return;
  const u=currentUser();if(!u||u.type!=='shop')return;
  const box=document.createElement('section');
  box.id='qgm-market-membership-entry';
  box.className='qgm-market-membership-entry';
  const marketish=MARKET_CATS.has(String(u.category||''));
  box.innerHTML=
    '<div><b>ร้านของคุณอยู่ในตลาดสดหรือไม่?</b>'+
    '<small>'+(marketish?'ร้านประเภทนี้ต้องยืนยันตลาดก่อนแสดงสินค้าในโหมดตลาดสด':'ถ้าร้านของคุณตั้งอยู่ในตลาดจริง สามารถสมัครเข้าตลาดสดได้ แม้ประเภทร้านหลักจะเป็นอาหาร/ของชำ/อื่น ๆ')+'</small></div>'+
    '<button type="button" class="qgm-outline" onclick="navigate(\'market-membership\')">สมัคร / ตรวจสถานะตลาดสด</button>';
  const submit=form.querySelector('button[type=submit]');
  if(submit)form.insertBefore(box,submit);else form.appendChild(box);
}

const baseSetup=window.renderShopSetup;
if(typeof baseSetup==='function'){
  window.renderShopSetup=function(){
    const r=baseSetup.apply(this,arguments);
    setTimeout(setupMarketEntry,0);
    return r;
  };
}

const baseRoute=window.qtShopRoute;
window.qtShopRoute=function(){
  const page=(location.hash.replace('#','')||'dashboard').split('/')[0];
  if(page==='market-membership')return window.qgmRenderMarketMembership();
  return baseRoute.apply(this,arguments);
};

window.qgmRenderMarketMembership=async function(){
  const u=currentUser();if(!u||u.type!=='shop')return navigate('login');
  if(typeof window.qtShopLayout!=='function')return toast('ไม่สามารถเปิดหน้าสมัครตลาดได้');
  window.qtShopLayout('สมัครเข้าตลาดสด',
    '<section class="qgm-card qgm-market-register">'+
      '<button type="button" class="qgm-market-back" onclick="navigate(\'shop-setup\')">← ข้อมูลร้าน</button>'+
      '<h2>สมัครร้านในตลาดสด</h2>'+
      '<p>QueueGo จะใช้พิกัดหน้าร้านเพื่อแนะนำตลาดที่ใกล้ที่สุดก่อน แล้วให้คุณยืนยันว่าขายอยู่ในตลาดนั้นจริง</p>'+
      '<div id="qgm-market-membership-body"><div class="qgm-market-loading">กำลังตรวจข้อมูลร้านและตลาดใกล้เคียง...</div></div>'+
    '</section>','shop-profile');

  const host=document.getElementById('qgm-market-membership-body');
  try{
    const {u:me,shop}=await ownShop();
    if(!host?.isConnected)return;
    const status=shop.market_membership_status||'none';
    let selectedMarket=null;
    if(shop.market_id){
      const t=await qtGetAccessToken();
      const rows=await qtSupabaseTable(
        'markets?select=id,name,address,province,district,subdistrict,verified&active=eq.true&id=eq.'+encodeURIComponent(shop.market_id)+'&limit=1',
        {accessToken:t}
      ).catch(()=>[]);
      selectedMarket=Array.isArray(rows)?rows[0]:null;
    }

    if(status==='approved'){
      host.innerHTML='<div class="qgm-market-status approved"><b>✓ ร้านผ่านการยืนยันตลาดแล้ว</b>'+
        '<span>'+esc(selectedMarket?.name||'ตลาดที่ลงทะเบียน')+'</span>'+
        '<small>แผง '+esc(shop.market_stall_no||'-')+' · โซน '+esc(shop.market_zone||'-')+'</small></div>';
      return;
    }
    if(status==='pending'){
      host.innerHTML='<div class="qgm-market-status pending"><b>กำลังรอ Admin ตรวจสอบ</b>'+
        '<span>'+esc(selectedMarket?.name||'ตลาดที่เลือก')+'</span>'+
        '<small>QueueGo จะยังไม่แสดงร้านเป็นสมาชิกตลาดจนกว่าจะอนุมัติ</small></div>';
      return;
    }

    const lat=Number(shop.latitude),lng=Number(shop.longitude);
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180||(lat===0&&lng===0)){
      host.innerHTML='<div class="qgm-market-status warn"><b>ต้องปักพิกัดร้านก่อน</b><small>กลับไปหน้า “ข้อมูลและตำแหน่งร้าน” ปักหมุดหน้าร้านและบันทึก แล้วกลับมาสมัครตลาดอีกครั้ง</small></div>'+
        '<button class="qgm-primary" type="button" onclick="navigate(\'shop-setup\')">ไปปักพิกัดร้าน</button>';
      return;
    }

    const markets=await nearby(lat,lng);
    const reject=status==='rejected'
      ? '<div class="qgm-market-status rejected"><b>ใบสมัครก่อนหน้าไม่ผ่าน</b><small>'+esc(shop.market_rejection_reason||'กรุณาตรวจพิกัดและข้อมูลแล้วส่งใหม่')+'</small></div>'
      : '';

    host.innerHTML=reject+
      '<div class="qgm-market-location"><b>พิกัดร้านที่ใช้ตรวจ</b><small>'+lat.toFixed(6)+', '+lng.toFixed(6)+'</small></div>'+
      (markets.length
        ? '<div class="qgm-market-nearby"><h3>ตลาดใกล้ร้านของคุณ</h3><p>ระบบเลือกตลาดที่ใกล้ที่สุดให้ก่อน คุณเปลี่ยนได้ถ้าร้านอยู่ตลาดอื่น</p>'+
          markets.map((m,i)=>'<label class="qgm-market-choice">'+
            '<input type="radio" name="qgm-market-id" value="'+esc(m.market_id)+'" '+(i===0?'checked':'')+'>'+
            '<span><b>'+esc(m.market_name)+'</b><small>'+km(m.distance_km)+' กม. · '+esc([m.subdistrict,m.district,m.province].filter(Boolean).join(' · '))+(m.verified?' · ยืนยันพิกัดแล้ว':' · พิกัดรอตรวจ')+'</small></span>'+
          '</label>').join('')+
          '</div>'+
          '<div class="qgm-market-fields"><label>เลขแผง (ถ้ามี)<input id="qgm-market-stall" maxlength="80" value="'+esc(shop.market_stall_no||'')+'"></label>'+
          '<label>โซน (ถ้ามี)<input id="qgm-market-zone" maxlength="80" value="'+esc(shop.market_zone||'')+'"></label></div>'+
          '<label class="qgm-market-confirm"><input id="qgm-market-confirm-place" type="checkbox"><span>ฉันยืนยันว่า <b>ร้าน/แผงของฉันตั้งอยู่ในตลาดที่เลือกจริง</b></span></label>'+
          '<label class="qgm-market-confirm"><input id="qgm-market-confirm-seller" type="checkbox"><span>ฉันยืนยันว่า <b>ฉันเป็นผู้ค้าหรือมีร้าน/แผงขายอยู่ในตลาดนี้จริง</b></span></label>'+
          '<button class="qgm-primary" type="button" onclick="qgmSubmitMarketMembership()">ส่งให้ Admin ตรวจสอบ</button>'
        : '<div class="qgm-market-status warn"><b>ยังไม่พบตลาดใกล้พิกัดนี้</b><small>คุณสามารถขอเพิ่มตลาดใหม่ได้ ระบบจะส่งให้ Admin ตรวจสอบก่อนเปิดใช้</small></div>')+
      '<details class="qgm-market-request"><summary>ไม่พบตลาดของฉัน / ขอเพิ่มตลาดใหม่</summary>'+
        '<label>ชื่อตลาด<input id="qgm-request-name" maxlength="160" placeholder="เช่น ตลาดสด..."></label>'+
        '<label>จังหวัด<input id="qgm-request-province" maxlength="100" value="บุรีรัมย์"></label>'+
        '<label>อำเภอ<input id="qgm-request-district" maxlength="100"></label>'+
        '<label>ตำบล<input id="qgm-request-subdistrict" maxlength="100"></label>'+
        '<label>รายละเอียดเพิ่มเติม<textarea id="qgm-request-note" maxlength="1000" rows="3"></textarea></label>'+
        '<button class="qgm-outline" type="button" onclick="qgmRequestNewMarket()">ส่งคำขอเพิ่มตลาด</button>'+
      '</details>';
  }catch(e){
    if(host?.isConnected)host.innerHTML='<div class="qgm-market-status rejected"><b>โหลดข้อมูลไม่สำเร็จ</b><small>'+esc(e.message||e)+'</small></div>';
  }
};

window.qgmSubmitMarketMembership=async function(){
  const marketId=document.querySelector('input[name=qgm-market-id]:checked')?.value;
  if(!marketId)return toast('กรุณาเลือกตลาด');
  if(!document.getElementById('qgm-market-confirm-place')?.checked||
     !document.getElementById('qgm-market-confirm-seller')?.checked){
    return toast('กรุณายืนยันว่าร้านอยู่ในตลาดและเป็นผู้ค้าจริง');
  }
  const button=document.querySelector('.qgm-market-register .qgm-primary');
  if(button)button.disabled=true;
  try{
    const {shop}=await ownShop();
    const cover=String(shop.public_cover||shop.metadata?.cover||shop.metadata?.profileImage||shop.metadata?.profile_image||'').trim();
    const result=await qtSupabaseRpc('queuego_submit_market_membership',{
      p_market_id:marketId,
      p_stall_no:document.getElementById('qgm-market-stall')?.value?.trim()||null,
      p_zone:document.getElementById('qgm-market-zone')?.value?.trim()||null,
      p_confirmed:true,
      p_proof_path:cover
    });
    toast('ส่งสมัครตลาดสดแล้ว รอ Admin ตรวจสอบ');
    await qgmRenderMarketMembership();
    return result;
  }catch(e){toast('ส่งใบสมัครไม่สำเร็จ: '+String(e.message||e).slice(0,130))}
  finally{if(button?.isConnected)button.disabled=false}
};

window.qgmRequestNewMarket=async function(){
  const name=document.getElementById('qgm-request-name')?.value?.trim()||'';
  const province=document.getElementById('qgm-request-province')?.value?.trim()||'';
  if(name.length<2||province.length<2)return toast('กรุณาระบุชื่อตลาดและจังหวัด');
  try{
    const {shop}=await ownShop();
    await qtSupabaseRpc('queuego_request_market',{
      p_name:name,
      p_province:province,
      p_district:document.getElementById('qgm-request-district')?.value?.trim()||null,
      p_subdistrict:document.getElementById('qgm-request-subdistrict')?.value?.trim()||null,
      p_address:shop.address||null,
      p_lat:Number(shop.latitude),
      p_lng:Number(shop.longitude),
      p_note:document.getElementById('qgm-request-note')?.value?.trim()||null
    });
    toast('ส่งคำขอเพิ่มตลาดแล้ว');
    const details=document.querySelector('.qgm-market-request');if(details)details.open=false;
  }catch(e){toast('ส่งคำขอไม่สำเร็จ: '+String(e.message||e).slice(0,130))}
};
})();