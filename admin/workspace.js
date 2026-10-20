/* Separate workspaces reuse the existing catalog, pricing and content authorities. */
(()=>{
'use strict';
const admin=()=>currentUser()?.type==='admin'&&currentUser()?.status==='approved';
let marketState;
window.qgRenderAdminWorkspace=function(page){
 if(!admin())return navigate('login');
 if(page==='admin-content'){
  layout('Content · แบนเนอร์และประกาศ','<section class="card"><h2>ประกาศ</h2><button type="button" onclick="qgAdminSection(\'qt-sec-notify\')">ส่งประกาศถึงสมาชิก</button></section><section class="card"><h2>แบนเนอร์หน้าแรก</h2><div id="qg-home-banner-manager"></div></section><section class="card"><h2>แบนเนอร์หมวดบริการ</h2><div id="qg-service-banner-manager"></div></section>');
  qgAdminLoadHomeBanners();qgAdminLoadServiceBanners();return;
 }
 if(page==='admin-pricing'){
  layout('Pricing · ราคาและค่าบริการ','<section class="card"><div class="row-btns"><button type="button" onclick="navigate(\'admin-gp-rates\')">อัตรา GP รายร้าน</button><button type="button" onclick="navigate(\'admin-ride\')">ค่าโดยสาร Ride</button><button type="button" onclick="qgAdminSection(\'qt-sec-settings\')">ค่าจัดส่งและตั้งค่าทั่วไป</button></div></section><section class="card" id="qg-platform-controls">กำลังโหลดกฎราคา…</section>');qgRenderPlatformControls();return;
 }
 if(page==='admin-catalog'){
  layout('Catalog · สินค้ากลาง','<section class="card"><h2>แคตตาล็อก</h2><div class="row-btns"><button type="button" onclick="navigate(\'admin-grocery\')">Grocery Master</button><button type="button" onclick="navigate(\'admin-market-master\')">Market Master</button><button type="button" onclick="navigate(\'admin-market\')">ตลาดและสมาชิกร้านค้า</button></div></section>');return;
 }
 if(page==='admin-market-master'){
  const state=marketState={owner:currentUser().id,offset:0,version:0};
  layout('Market Master · แคตตาล็อกตลาด','<section class="card"><form id="qg-master-filter"><label>ค้นหาชื่อสินค้า<input id="qg-master-query" type="search" maxlength="100"></label><label>หมวดหมู่<input id="qg-master-category" maxlength="80" placeholder="เช่น ปลา หรือ ผลไม้"></label><label>สถานะ<select id="qg-master-active"><option value="all">ทั้งหมด</option><option value="true">เปิดใช้งาน</option><option value="false">ปิดใช้งาน</option></select></label><button type="submit">ค้นหา</button></form><p id="qg-master-status" role="status"></p><div id="qg-master-rows"></div><div class="row-btns"><button type="button" id="qg-master-prev">ก่อนหน้า</button><button type="button" id="qg-master-next">ถัดไป</button></div></section>');
  document.getElementById('qg-master-filter').onsubmit=e=>{e.preventDefault();state.offset=0;loadMarket(state)};
  document.getElementById('qg-master-prev').onclick=()=>{state.offset=Math.max(0,state.offset-50);loadMarket(state)};
  document.getElementById('qg-master-next').onclick=()=>{state.offset+=50;loadMarket(state)};
  loadMarket(state);
 }
};
const clean=value=>String(value||'').trim().replace(/[,*()%_"\\]/g,'').slice(0,100);
async function loadMarket(state){
 const host=document.getElementById('qg-master-rows'),note=document.getElementById('qg-master-status'),version=++state.version;
 const alive=()=>marketState===state&&state.version===version&&host.isConnected&&admin()&&currentUser().id===state.owner;
 if(!alive())return;const prev=document.getElementById('qg-master-prev'),next=document.getElementById('qg-master-next');prev.disabled=next.disabled=true;
 try{
  const query=clean(document.getElementById('qg-master-query').value),category=clean(document.getElementById('qg-master-category').value),active=document.getElementById('qg-master-active').value;
  note.textContent='กำลังโหลดหน้านี้…';
  const path='market_master_products?select=id,name_th,category,suggested_unit,source_unit,image,image_status,catalog_preview_image,active&order=name_th.asc,id.asc&limit=51&offset='+state.offset+(query?'&name_th=ilike.*'+encodeURIComponent(query)+'*':'')+(category?'&category=eq.'+encodeURIComponent(category):'')+(['true','false'].includes(active)?'&active=eq.'+active:'');
  const rows=await qtSupabaseTable(path,{accessToken:await qtGetAccessToken()});if(!alive())return;host.replaceChildren();
  for(const row of (rows||[]).slice(0,50)){
   const card=document.createElement('article');card.className='qg-master-card';const image=row.image_status==='approved'?row.image:row.catalog_preview_image;
   if(/^https:\/\//.test(image||'')){const img=document.createElement('img');img.src=image;img.alt=row.name_th;img.width=112;img.height=112;img.loading='lazy';img.referrerPolicy='no-referrer';img.onerror=()=>img.remove();card.append(img)}
   const body=document.createElement('div'),name=document.createElement('h3'),detail=document.createElement('p');name.textContent=row.name_th;detail.textContent=row.category+' · '+(row.suggested_unit||row.source_unit)+' · '+(row.active?'เปิดใช้งาน':'ปิดใช้งาน');body.append(name,detail);card.append(body);host.append(card);
  }
  note.textContent='หน้า '+(state.offset/50+1)+' · '+Math.min(rows?.length||0,50)+' รายการ · หน้านี้อ่านสถานะสินค้ากลางจากระบบจริง';prev.disabled=state.offset===0;next.disabled=(rows?.length||0)<=50;
 }catch(e){if(alive()){note.textContent='โหลดแคตตาล็อกไม่ได้: '+e.message;prev.disabled=state.offset===0;}}
}
})();
