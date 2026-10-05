/* Admin map picker: Longdo, existing audited location RPC, no order mutations. */
(()=>{
'use strict';
const allowed=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
const owner=()=>allowed()?String(currentUser().id):null;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const valid=(lat,lng)=>lat!=null&&lng!=null&&String(lat).trim()!==''&&String(lng).trim()!==''&&Number.isFinite(Number(lat))&&Number.isFinite(Number(lng))&&Math.abs(Number(lat))<=90&&Math.abs(Number(lng))<=180&&!(Number(lat)===0&&Number(lng)===0);
let dialog=null,opening=0,dispose=null;
const close=()=>{opening++;if(dispose){dispose();dispose=null}if(dialog){dialog.remove();dialog=null}};
window.addEventListener('hashchange',close);
function picker(box,initial,editable,actor){
 const host=box.querySelector('.qg-location-map'),status=box.querySelector('[role=status]'),coord=box.querySelector('.qg-location-coordinates');
 const save=box.querySelector('[type=submit]');let selected=valid(initial.lat,initial.lon)?{lat:Number(initial.lat),lon:Number(initial.lon)}:null;
 let map=null,marker=null,disposed=false,busy=false;
 const active=()=>!disposed&&box.isConnected&&owner()===actor;
 const render=()=>{coord.textContent=selected?`${selected.lat.toFixed(6)}, ${selected.lon.toFixed(6)}`:'ยังไม่ได้เลือกพิกัด';if(save)save.disabled=!selected||!map||busy};
 const select=p=>{if(!active()||busy||!valid(p?.lat,p?.lon))return;selected={lat:Number(p.lat),lon:Number(p.lon)};if(marker)map.Overlays.remove(marker);marker=new longdo.Marker(selected,{title:'ตำแหน่งที่เลือก',icon:{html:'<span class="qg-map-pin"></span>',offset:{x:12,y:24}}});map.Overlays.add(marker);render()};
 const mount=()=>{if(!active())return;if(typeof longdo==='undefined'){status.textContent='โหลดแผนที่ไม่สำเร็จ กดลองอีกครั้ง';render();return}
  try{map=new longdo.Map({placeholder:host,language:'th',lastView:false,zoom:selected?16:11,location:selected||{lat:14.993,lon:103.102},ui:longdo.UiComponent.Mobile});map.Ui.LayerSelector.visible(false);map.Search.placeholder(box.querySelector('.qg-map-results'));if(selected)select(selected);
   if(editable){map.Event.bind('click',()=>{if(active()&&!busy)select(map.location(longdo.LocationMode.Pointer))});map.Event.bind('drop',()=>{if(active()&&!busy)select(map.location())})}
   status.textContent=editable?'แตะจุดที่ต้องการ หรือเลื่อนแผนที่แล้วกด “เลือกจุดกลางแผนที่”':'พิกัดตามข้อมูลที่บันทึกไว้';render();
  }catch(e){if(map){try{map.pause(true)}catch(_){}map=null}status.textContent='เปิดแผนที่ไม่ได้ กรุณาลองใหม่';render()}
 };
 box.querySelector('.qg-map-retry').onclick=()=>{if(!map)mount()};
 box.querySelector('.qg-map-search').onclick=()=>{const term=box.querySelector('[name=search]').value.trim();if(active()&&map&&term)map.Search.search(term)};
 box.querySelector('[name=search]').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();box.querySelector('.qg-map-search').click()}};
 if(editable){box.querySelector('.qg-map-center').onclick=()=>{if(map&&!busy)select(map.location())};box.querySelector('.qg-map-locate').onclick=()=>{
  if(!active()||busy)return;if(!navigator.geolocation){status.textContent='อุปกรณ์นี้ไม่รองรับตำแหน่ง GPS';return}status.textContent='กำลังค้นหาตำแหน่งของคุณ…';
  navigator.geolocation.getCurrentPosition(p=>{if(!active()||busy||!map)return;select({lat:p.coords.latitude,lon:p.coords.longitude});map.location(selected,true);map.zoom(16,true);status.textContent='เลือกตำแหน่ง GPS แล้ว ตรวจหมุดก่อนบันทึก'},e=>{if(active())status.textContent=e.code===1?'กรุณาเปิดสิทธิ์ตำแหน่ง แล้วลองใหม่':'รับตำแหน่งไม่ได้ คุณยังเลือกจุดจากแผนที่ได้'},{enableHighAccuracy:true,timeout:12000,maximumAge:30000});
 }}
 render();mount();
 return {get ready(){return !!map},get selected(){return selected},set busy(v){busy=v;box.querySelectorAll('button,input,textarea').forEach(e=>e.disabled=v);render()},destroy(){disposed=true;if(map){try{map.Search.clear();map.Overlays.clear();map.pause(true)}catch(e){}}}};
}
function shell(title,name,editable){
 const box=document.createElement('dialog');box.className='qg-location-editor';box.setAttribute('aria-label',title);
 box.innerHTML=`<form><div class="qg-menu-head"><div><h2>${esc(title)}</h2><p>${esc(name)}</p></div><button type="button" class="qg-location-cancel" aria-label="ปิดแผนที่">✕</button></div><div class="qg-map-searchbar"><input name="search" type="search" aria-label="ค้นหาสถานที่" placeholder="ค้นหาชื่อสถานที่ ถนน หรือตลาด"><button type="button" class="qg-map-search">ค้นหา</button></div><div class="qg-map-results"></div><div class="qg-location-map" aria-label="แผนที่ Longdo"></div>${editable?'<div class="qg-map-tools"><button type="button" class="qg-map-locate">ตำแหน่งของฉัน</button><button type="button" class="qg-map-center">เลือกจุดกลางแผนที่</button></div>':''}<output class="qg-location-coordinates"></output><p role="status" aria-live="polite"></p><button class="qg-map-retry" type="button">ลองโหลดแผนที่อีกครั้ง</button>${editable?'<label>เหตุผลที่แก้ไข<textarea name="reason" required minlength="2" maxlength="500" placeholder="เช่น ย้ายหมุดให้ตรงทางเข้าร้าน"></textarea></label><p class="qt-admin-note">พิกัดใหม่ใช้กับงานใหม่ ออเดอร์เดิมเก็บพิกัดตามรายการที่สร้างไว้</p><div class="qg-location-footer"><button type="button" class="qg-location-cancel">ยกเลิก</button><button type="submit" class="btn-primary">บันทึกพิกัด</button></div>':''}</form>`;
 dialog=box;document.body.append(box);box.showModal();return box;
}
window.qgAdminViewLocation=(lat,lng,title)=>{const actor=owner();if(!actor||!valid(lat,lng))return;close();const box=shell(title||'ตรวจพิกัด','ตำแหน่งตามข้อมูลในระบบ',false),p=picker(box,{lat,lon:lng},false,actor);dispose=()=>p.destroy();box.querySelector('.qg-location-cancel').onclick=close;box.addEventListener('cancel',e=>{e.preventDefault();close()});box.querySelector('form').onsubmit=e=>e.preventDefault()};
window.qgAdminEditLocation=async(kind,id)=>{
 const actor=owner();if(!actor||!['market','shop','shop_user'].includes(kind))return;close();const ticket=opening;
 try{
  const token=await qtGetAccessToken();if(owner()!==actor||opening!==ticket)return;
  const market=kind==='market',rows=await qtSupabaseTable((market?'markets?select=id,name,latitude,longitude&id=eq.':'shop_profiles?select=id,shop_name,public_category,metadata,latitude,longitude&'+(kind==='shop_user'?'user_id':'id')+'=eq.')+encodeURIComponent(id)+'&limit=1',{accessToken:token});
  if(owner()!==actor||opening!==ticket)return;const row=rows?.[0];if(!row)throw Error('ไม่พบข้อมูลพิกัด');
  const box=shell('แก้พิกัด'+(market?'ตลาด':'ร้าน'),(row.name||row.shop_name)+(market?'':' · '+qgAdminShopCategory(row)),true),form=box.querySelector('form'),status=box.querySelector('[role=status]'),p=picker(box,{lat:row.latitude,lon:row.longitude},true,actor);dispose=()=>p.destroy();let busy=false;
  box.querySelectorAll('.qg-location-cancel').forEach(b=>b.onclick=()=>{if(!busy)close()});box.addEventListener('cancel',e=>{e.preventDefault();if(!busy)close()});
  form.onsubmit=async e=>{
   e.preventDefault();if(busy||owner()!==actor||dialog!==box)return;const reason=form.elements.reason.value.trim(),point=p.selected;if(!p.ready||!point||reason.length<2||!form.reportValidity()){status.textContent='กรุณาเลือกพิกัดและกรอกเหตุผลให้ครบ';return}
   busy=true;p.busy=true;status.textContent='กำลังบันทึก…';
   try{
    const result=await qtSupabaseRpc('qg_admin_update_location',{p_entity_type:market?'market':'shop',p_entity_id:row.id,p_lat:point.lat,p_lng:point.lon,p_reason:reason,p_expected_lat:row.latitude??null,p_expected_lng:row.longitude??null});
    if(owner()!==actor||dialog!==box)return;if(result?.id!==row.id)throw Error('ไม่ได้รับผลยืนยันจากฐานข้อมูล');close();toast(result.changed?'บันทึกพิกัดและประวัติแล้ว':'พิกัดตรงกับข้อมูลที่บันทึกแล้ว');
    if(location.hash==='#admin-market')await qgLoadMarketOperations();else{await qtHydrateDatabase({light:false});if(owner()===actor&&location.hash==='#admin-governance'){renderAdminGovernance();qtAdminTab('qt-sec-shops')}}
   }catch(e){if(owner()===actor&&dialog===box)status.textContent='บันทึกไม่ได้: '+e.message}
   finally{busy=false;if(dialog===box&&owner()===actor)p.busy=false}
  };
 }catch(e){if(owner()===actor&&opening===ticket)toast('เปิดพิกัดไม่ได้: '+e.message)}
};
})();
