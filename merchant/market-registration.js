/* QueueGo Merchant Market Membership
   Uses the existing shop profile/location and real Supabase market data.
   Market picker: nearest auto-selection + dropdown + name search. */
(()=>{
'use strict';
const MARKET_CATS=new Set(['market','meat','fish','vegetable','fruit']);
const BURIRAM='บุรีรัมย์';
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const km=v=>Number(v||0).toLocaleString('th-TH',{minimumFractionDigits:0,maximumFractionDigits:2});
const picker={rows:[],filtered:[],selectedMarketId:null,nearestMarketId:null,query:'',lat:null,lng:null};

function distanceKm(lat1,lng1,lat2,lng2){
  const a=Number(lat1),b=Number(lng1),c=Number(lat2),d=Number(lng2);
  if(![a,b,c,d].every(Number.isFinite))return null;
  const r=6371,toRad=x=>x*Math.PI/180;
  const dLat=toRad(c-a),dLng=toRad(d-b);
  const h=Math.sin(dLat/2)**2+Math.cos(toRad(a))*Math.cos(toRad(c))*Math.sin(dLng/2)**2;
  return r*2*Math.asin(Math.min(1,Math.sqrt(h)));
}
function compactName(v){
  return String(v||'').toLocaleLowerCase('th-TH').replace(/ตลาดสด|ตลาด|เทศบาล|เมือง/g,'').replace(/[^0-9a-zก-๙]/gi,'');
}
function grams(s){
  const x=compactName(s);if(!x)return[];
  if(x.length===1)return[x];
  const out=[];for(let i=0;i<x.length-1;i++)out.push(x.slice(i,i+2));return out;
}
function nameScore(name,query){
  const q=compactName(query),n=compactName(name);
  if(!q)return 1;
  if(n===q)return 100;
  if(n.startsWith(q))return 90;
  if(n.includes(q))return 80;
  const qa=grams(q),na=grams(n);if(!qa.length||!na.length)return 0;
  const pool=na.slice();let hit=0;
  qa.forEach(g=>{const i=pool.indexOf(g);if(i>=0){hit++;pool.splice(i,1)}});
  return (2*hit)/(qa.length+na.length)*60;
}
function selectable(m){
  const dist=Number(m.distance_km),radius=Number(m.assignment_radius_km||3);
  return Number.isFinite(dist)&&Number.isFinite(radius)&&dist<=radius;
}
function marketMeta(m){
  const parts=[m.subdistrict,m.district,m.province].filter(Boolean).join(' · ');
  const dist=Number.isFinite(Number(m.distance_km))?km(m.distance_km)+' กม.':'ไม่ทราบระยะ';
  const verified=m.verified?'ยืนยันพิกัดแล้ว':'พิกัดรอ Admin ยืนยัน';
  const range=selectable(m)?'อยู่ในรัศมีสมัคร':'นอกรัศมี '+km(m.assignment_radius_km||3)+' กม.';
  return [dist,parts,verified,range].filter(Boolean).join(' · ');
}
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
async function rpc(name,body){
  const token=await qtGetAccessToken();
  if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
  return qtSupabaseTable('rpc/'+name,{method:'POST',accessToken:token,body});
}
async function loadMarkets(lat,lng){
  const token=await qtGetAccessToken();
  if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
  const rows=await qtSupabaseTable(
    'markets?select=id,name,address,province,district,subdistrict,latitude,longitude,verified,assignment_radius_km&active=eq.true&latitude=not.is.null&longitude=not.is.null&province=eq.'+
    encodeURIComponent(BURIRAM)+'&order=name.asc',
    {accessToken:token}
  );
  return (Array.isArray(rows)?rows:[]).map(m=>{
    const dist=distanceKm(lat,lng,m.latitude,m.longitude);
    return {
      market_id:m.id,market_name:m.name,address:m.address,province:m.province,district:m.district,subdistrict:m.subdistrict,
      latitude:m.latitude,longitude:m.longitude,verified:m.verified===true,assignment_radius_km:Number(m.assignment_radius_km||3),
      distance_km:dist
    };
  }).sort((a,b)=>(Number(a.distance_km)-Number(b.distance_km))||String(a.market_name).localeCompare(String(b.market_name),'th'));
}
function applyFilter(query){
  picker.query=String(query||'').trim();
  const q=picker.query;
  picker.filtered=picker.rows.map(m=>({m,score:nameScore(m.market_name,q)}))
    .filter(x=>!q||x.score>=12)
    .sort((a,b)=>q?(b.score-a.score)||(Number(a.m.distance_km)-Number(b.m.distance_km)):(Number(a.m.distance_km)-Number(b.m.distance_km)))
    .map(x=>x.m);
  if(q&&picker.filtered.length&&!picker.filtered.some(m=>String(m.market_id)===String(picker.selectedMarketId))){
    picker.selectedMarketId=String(picker.filtered[0].market_id);
  }
}
function pickerOptions(){
  const rows=picker.filtered.length||!picker.query?picker.filtered:[];
  if(!rows.length)return '<option value="">ไม่พบตลาดที่ชื่อใกล้เคียง</option>';
  return rows.map(m=>'<option value="'+esc(m.market_id)+'" '+(String(m.market_id)===String(picker.selectedMarketId)?'selected':'')+'>'+
    esc(m.market_name)+' · '+(Number.isFinite(Number(m.distance_km))?km(m.distance_km)+' กม.':'ไม่ทราบระยะ')+
    (String(m.market_id)===String(picker.nearestMarketId)?' · ใกล้ที่สุด':'')+
  '</option>').join('');
}
function renderSuggestions(){
  const host=document.getElementById('qgm-market-suggestions');if(!host)return;
  const rows=(picker.filtered||[]).slice(0,6);
  if(!rows.length){
    host.innerHTML='<div class="qgm-market-status warn"><small>ไม่พบชื่อใกล้เคียง ลองพิมพ์คำสั้นลง หรือส่งคำขอเพิ่มตลาดใหม่ด้านล่าง</small></div>';
    return;
  }
  host.innerHTML=rows.map(m=>{
    const chosen=String(m.market_id)===String(picker.selectedMarketId);
    const nearest=String(m.market_id)===String(picker.nearestMarketId);
    return '<button type="button" class="qgm-market-suggestion '+(chosen?'selected':'')+'" onclick="qgmMarketSelect(\''+esc(m.market_id)+'\')">'+
      '<span><b>'+esc(m.market_name)+'</b><small>'+esc(marketMeta(m))+'</small></span>'+
      '<em>'+(nearest?'ใกล้ที่สุด':chosen?'เลือกแล้ว':'เลือก')+'</em>'+
    '</button>';
  }).join('');
}
function renderPicker(){
  const select=document.getElementById('qgm-market-select');
  if(select)select.innerHTML=pickerOptions();
  renderSuggestions();
  const selected=picker.rows.find(m=>String(m.market_id)===String(picker.selectedMarketId));
  const note=document.getElementById('qgm-market-selected-note');
  if(note){
    note.innerHTML=selected
      ? '<b>'+esc(selected.market_name)+'</b><small>'+esc(marketMeta(selected))+'</small>'
      : '<b>ยังไม่ได้เลือกตลาด</b><small>ค้นหาหรือเลือกจากรายการตลาดในบุรีรัมย์</small>';
    note.classList.toggle('warn',!!selected&&!selectable(selected));
  }
}
window.qgmMarketFilter=function(value){applyFilter(value);renderPicker()};
window.qgmMarketSelect=function(id){
  const found=picker.rows.find(m=>String(m.market_id)===String(id));if(!found)return;
  picker.selectedMarketId=String(found.market_id);
  applyFilter(picker.query);
  renderPicker();
};
function pickerHtml(){
  applyFilter('');
  const nearest=picker.rows[0]||null;
  picker.nearestMarketId=nearest?.market_id||null;
  if(!picker.selectedMarketId&&nearest)picker.selectedMarketId=String(nearest.market_id);
  applyFilter('');
  return '<section class="qgm-market-picker">'+
    '<div class="qgm-market-picker-head"><div><b>เลือกตลาด</b><small>ระบบเลือกตลาดที่ใกล้พิกัดร้านที่สุดให้ก่อน คุณเปลี่ยนเองได้</small></div>'+(nearest?'<span>ใกล้สุด '+km(nearest.distance_km)+' กม.</span>':'')+'</div>'+
    '<label class="qgm-market-search"><span>ค้นหาชื่อตลาด</span><input id="qgm-market-search" type="search" autocomplete="off" placeholder="เช่น สวายจีก, เทศบาลเมืองบุรีรัมย์" oninput="qgmMarketFilter(this.value)"></label>'+
    '<label class="qgm-market-select-label"><span>ตลาดในจังหวัดบุรีรัมย์</span><select id="qgm-market-select" onchange="qgmMarketSelect(this.value)">'+pickerOptions()+'</select></label>'+
    '<div id="qgm-market-selected-note" class="qgm-market-selected-note"></div>'+
    '<div id="qgm-market-suggestions" class="qgm-market-suggestions"></div>'+
  '</section>';
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
      '<p>QueueGo ใช้พิกัดหน้าร้านเพื่อแนะนำตลาดที่ใกล้ที่สุดก่อน และคุณสามารถค้นหาหรือเลือกตลาดเองได้</p>'+
      '<div id="qgm-market-membership-body"><div class="qgm-market-loading">กำลังโหลดข้อมูลร้าน...</div></div>'+
    '</section>','shop-profile');

  const host=document.getElementById('qgm-market-membership-body');
  try{
    const {shop}=await ownShop();
    if(!host?.isConnected)return;
    const status=shop.market_membership_status||'none';
    let selectedMarket=null;
    if(shop.market_id){
      const token=await qtGetAccessToken();
      const rows=await qtSupabaseTable(
        'markets?select=id,name,address,province,district,subdistrict,verified&active=eq.true&id=eq.'+encodeURIComponent(shop.market_id)+'&limit=1',
        {accessToken:token}
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

    picker.lat=lat;picker.lng=lng;picker.query='';
    picker.rows=await loadMarkets(lat,lng);
    picker.filtered=picker.rows.slice();
    picker.nearestMarketId=picker.rows[0]?.market_id||null;
    picker.selectedMarketId=shop.market_suggested_id&&picker.rows.some(m=>String(m.market_id)===String(shop.market_suggested_id))
      ?String(shop.market_suggested_id)
      :(picker.nearestMarketId?String(picker.nearestMarketId):null);

    const reject=status==='rejected'
      ? '<div class="qgm-market-status rejected"><b>ใบสมัครก่อนหน้าไม่ผ่าน</b><small>'+esc(shop.market_rejection_reason||'กรุณาตรวจพิกัดและข้อมูลแล้วส่งใหม่')+'</small></div>'
      : '';

    host.innerHTML=reject+
      '<div class="qgm-market-location"><b>พิกัดร้านที่ใช้ตรวจ</b><small>'+lat.toFixed(6)+', '+lng.toFixed(6)+'</small></div>'+
      (picker.rows.length
        ? pickerHtml()+
          '<div class="qgm-market-fields"><label>เลขแผง (ถ้ามี)<input id="qgm-market-stall" maxlength="80" value="'+esc(shop.market_stall_no||'')+'"></label>'+
          '<label>โซน (ถ้ามี)<input id="qgm-market-zone" maxlength="80" value="'+esc(shop.market_zone||'')+'"></label></div>'+
          '<label class="qgm-market-confirm"><input id="qgm-market-confirm-place" type="checkbox"><span>ฉันยืนยันว่า <b>ร้าน/แผงของฉันตั้งอยู่ในตลาดที่เลือกจริง</b></span></label>'+
          '<label class="qgm-market-confirm"><input id="qgm-market-confirm-seller" type="checkbox"><span>ฉันยืนยันว่า <b>ฉันเป็นผู้ค้าหรือมีร้าน/แผงขายอยู่ในตลาดนี้จริง</b></span></label>'+
          '<button class="qgm-primary" type="button" onclick="qgmSubmitMarketMembership()">ส่งให้ Admin ตรวจสอบ</button>'
        : '<div class="qgm-market-status warn"><b>ยังไม่มีตลาดในจังหวัดบุรีรัมย์ที่เปิดใช้</b><small>คุณสามารถส่งคำขอเพิ่มตลาดใหม่ให้ Admin ตรวจสอบได้</small></div>')+
      '<details class="qgm-market-request"><summary>ไม่พบตลาดของฉัน / ขอเพิ่มตลาดใหม่</summary>'+
        '<label>ชื่อตลาด<input id="qgm-request-name" maxlength="160" placeholder="เช่น ตลาดสดสวายจีก"></label>'+
        '<label>จังหวัด<input id="qgm-request-province" maxlength="100" value="บุรีรัมย์"></label>'+
        '<label>อำเภอ<input id="qgm-request-district" maxlength="100"></label>'+
        '<label>ตำบล<input id="qgm-request-subdistrict" maxlength="100"></label>'+
        '<label>รายละเอียดเพิ่มเติม<textarea id="qgm-request-note" maxlength="1000" rows="3"></textarea></label>'+
        '<button class="qgm-outline" type="button" onclick="qgmRequestNewMarket()">ส่งคำขอเพิ่มตลาด</button>'+
      '</details>';
    renderPicker();
  }catch(e){
    if(host?.isConnected)host.innerHTML='<div class="qgm-market-status rejected"><b>โหลดข้อมูลไม่สำเร็จ</b><small>'+esc(e.message||e)+'</small></div>';
  }
};

window.qgmSubmitMarketMembership=async function(){
  const marketId=String(picker.selectedMarketId||document.getElementById('qgm-market-select')?.value||'');
  const selected=picker.rows.find(m=>String(m.market_id)===marketId);
  if(!selected)return toast('กรุณาเลือกตลาด');
  if(!selectable(selected))return toast('พิกัดร้านอยู่นอกรัศมีของตลาดนี้ กรุณาเลือกตลาดที่ใกล้กว่าหรือขอเพิ่มตลาดใหม่');
  if(!document.getElementById('qgm-market-confirm-place')?.checked||
     !document.getElementById('qgm-market-confirm-seller')?.checked){
    return toast('กรุณายืนยันว่าร้านอยู่ในตลาดและเป็นผู้ค้าจริง');
  }
  const button=document.querySelector('.qgm-market-register .qgm-primary');
  if(button)button.disabled=true;
  try{
    const {shop}=await ownShop();
    const cover=String(shop.public_cover||shop.metadata?.cover||shop.metadata?.profileImage||shop.metadata?.profile_image||'').trim();
    const result=await rpc('queuego_submit_market_membership',{
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
    await rpc('queuego_request_market',{
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
