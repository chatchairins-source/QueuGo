/* QueueGo Market Master Catalog — additive Merchant selector over existing Market stock. */
(()=>{
'use strict';
const MARKET_SHOPS=new Set(['market','meat','fish','vegetable','fruit']);
const UNITS=['ชิ้น','กิโลกรัม','ขีด','กรัม','ถุง','แพ็ก','มัด','ลูก','ขวด','กล่อง'];
const E=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const state={epoch:0,rows:[],categories:[],query:'',category:'',offset:0,hasMore:false,loading:false,shop:null,gpRate:null};
const shop=()=>{const u=currentUser?.();return u?.type==='shop'?u:null};
const profileId=u=>u&&(u.shopProfileId||QT_SYNC?.profileMaps?.shopByUserId?.get(u.id)||null);
const permitted=u=>MARKET_SHOPS.has(String(u?.category||''));
const pendingKey=id=>'qg_market_catalog_pending_v1:'+id;
async function rpc(name,body){
  const accessToken=await qtGetAccessToken();
  if(!accessToken)throw Error('กรุณาเข้าสู่ระบบใหม่');
  return qtSupabaseTable('rpc/'+name,{method:'POST',accessToken,body});
}
function shell(body){
  qtShopLayout('เพิ่มสินค้าจากแคตตาล็อก',`<section class="qgm-card market-panel market-catalog-panel">
    <div class="market-head"><button type="button" onclick="navigate('market-stock')">‹ สินค้าและสต๊อกตลาด</button><h1>เพิ่มสินค้าจากแคตตาล็อก</h1></div>
    <p>เลือกชื่อสินค้ามาตรฐาน แล้วร้านกำหนดราคา หน่วยขาย น้ำหนัก ต้นทุน และสต๊อกเอง</p>
    <div id="market-catalog-error" role="alert"></div>${body}
  </section>`,'shop-profile');
}
function error(e){const msg=String(e?.message||e||'เกิดข้อผิดพลาด');const el=document.getElementById('market-catalog-error');if(el)el.textContent=msg;try{toast('แคตตาล็อกตลาด: '+msg)}catch(_){}}
function gpRate(raw){const v=Array.isArray(raw)?raw[0]:raw;if(v&&typeof v==='object'){const n=Number(v.effective_gp_rate??v.rate??v.value);return Number.isFinite(n)?n:null}const n=Number(v);return Number.isFinite(n)?n:null}
function deliveryPrice(storePrice){const p=Number(storePrice),r=Number(state.gpRate);return Number.isFinite(p)&&p>0&&Number.isFinite(r)&&r>=0&&r<=100?Math.round(p*(1+r/100)*100)/100:null}
function deliveryNote(storePrice){const d=deliveryPrice(storePrice),r=Number(state.gpRate);return Number.isFinite(d)?`GP ${r.toLocaleString('th-TH',{maximumFractionDigits:2})}% · ราคาเดลิเวอรี่ ${d.toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2})} บาท`:'ราคาเดลิเวอรี่คำนวณ GP อัตโนมัติหลังบันทึก'}
window.qgMarketCatalogPricePreview=()=>{const input=document.querySelector('#market-catalog-add-form [name="price"]'),out=document.getElementById('market-catalog-delivery-preview');if(out)out.textContent=deliveryNote(input?.value)};
window.qgMarketCatalogSetKg=kg=>{const form=document.getElementById('market-catalog-add-form');if(!form)return;const pack=form.querySelector('[name="pack"]'),weight=form.querySelector('[name="weight"]'),unit=form.querySelector('[name="unit"]');if(unit)unit.value='กิโลกรัม';if(pack)pack.value=String(kg);if(weight)weight.value=String(kg)};
function active(epoch,shopId){return epoch===state.epoch&&shopId===state.shop&&location.hash.replace('#','').split('/')[0]==='market-catalog'}
function card(r){
  const linked=!!r.product_id;
  const image=r.image? `<img class="market-catalog-image" src="${E(r.image)}" alt="" loading="lazy" onerror="this.replaceWith(Object.assign(document.createElement('div'),{className:'market-catalog-image market-catalog-placeholder',textContent:'${E((r.name_th||'?').charAt(0))}'}))">`:`<div class="market-catalog-image market-catalog-placeholder">${E((r.name_th||'?').charAt(0))}</div>`;
  return `<article class="market-catalog-card" data-search="${E((r.name_th+' '+r.category).toLocaleLowerCase('th-TH'))}">
    ${image}<div class="market-catalog-info"><b>${E(r.name_th)}</b><small>${E(r.category)} · หน่วยอ้างอิง ${E(r.source_unit)}</small>
    ${r.suggested_unit?`<small>หน่วยที่ระบบแนะนำ: ${E(r.suggested_pack_size)} ${E(r.suggested_unit)} / ชุด</small>`:'<small>หน่วยอ้างอิงยังไม่ตรงกับหน่วยขายในระบบ — ร้านเลือกหน่วยเอง</small>'}
    ${r.image?'<small>รูปสินค้า: ผ่านการอนุมัติแล้ว</small>':r.has_image_reference?'<small>รูปสินค้า: พบรูปอ้างอิงแล้ว · รอตรวจสิทธิ์ก่อนเผยแพร่</small>':'<small>รูปสินค้า: กำลังหารูปที่ตรวจสอบแหล่งที่มาได้</small>'}
    ${linked?`<strong>มีในร้านแล้ว · หน้าร้าน ${Number(r.price??0).toLocaleString('th-TH')} บาท · เดลิเวอรี่ ${Number(r.delivery_price??r.price??0).toLocaleString('th-TH')} บาท</strong>`:''}</div>
    <button type="button" class="${linked?'qgm-outline':'qgm-primary'}" ${linked?'disabled':''} onclick="qgMarketCatalogChoose('${E(r.id)}')">${linked?'มีในร้านแล้ว':'เพิ่มเข้าร้าน'}</button>
  </article>`;
}
function paint(){
  if(!state.rows.length){shell(`<form class="market-catalog-filter" onsubmit="qgMarketCatalogSearch(event)"><input id="market-catalog-search" type="search" value="${E(state.query)}" placeholder="ค้นหาสินค้า"><select id="market-catalog-category"><option value="">ทุกหมวด</option>${state.categories.map(c=>`<option value="${E(c.category)}" ${c.category===state.category?'selected':''}>${E(c.category)} (${Number(c.n)})</option>`).join('')}</select><button type="submit">ค้นหา</button></form><div class="market-catalog-chips" aria-label="หมวดสินค้า"><button type="button" class="market-catalog-chip ${state.category?'':'active'}" onclick="qgMarketCatalogSetCategory('')">ทั้งหมด</button>${state.categories.map(c=>`<button type="button" class="market-catalog-chip ${c.category===state.category?'active':''}" onclick="qgMarketCatalogSetCategory('${E(c.category)}')">${E(c.category)}</button>`).join('')}</div><p>ไม่พบสินค้าในเงื่อนไขนี้</p>`);return;}
  shell(`<form class="market-catalog-filter" onsubmit="qgMarketCatalogSearch(event)"><input id="market-catalog-search" type="search" value="${E(state.query)}" placeholder="ค้นหาสินค้า"><select id="market-catalog-category"><option value="">ทุกหมวด</option>${state.categories.map(c=>`<option value="${E(c.category)}" ${c.category===state.category?'selected':''}>${E(c.category)} (${Number(c.n)})</option>`).join('')}</select><button type="submit">ค้นหา</button></form><div class="market-catalog-chips" aria-label="หมวดสินค้า"><button type="button" class="market-catalog-chip ${state.category?'':'active'}" onclick="qgMarketCatalogSetCategory('')">ทั้งหมด</button>${state.categories.map(c=>`<button type="button" class="market-catalog-chip ${c.category===state.category?'active':''}" onclick="qgMarketCatalogSetCategory('${E(c.category)}')">${E(c.category)}</button>`).join('')}</div>
  <div class="market-catalog-list">${state.rows.map(card).join('')}</div>
  <div class="market-catalog-pages"><button type="button" class="qgm-outline" onclick="qgMarketCatalogPage(-1)" ${state.offset===0?'disabled':''}>ก่อนหน้า</button><small>รายการ ${state.offset+1}–${state.offset+state.rows.length}</small><button type="button" class="qgm-outline" onclick="qgMarketCatalogPage(1)" ${state.hasMore?'':'disabled'}>ถัดไป</button></div>`);
}
async function recoverPending(shopId,epoch){
  let pending=null;
  try{pending=JSON.parse(localStorage.getItem(pendingKey(shopId))||'null')}catch(_){}
  if(!pending?.p_request_id||pending.p_master_product_id==null)return;
  try{
    await rpc('qg_market_catalog_add',pending);
    try{localStorage.removeItem(pendingKey(shopId))}catch(_){}
  }catch(e){
    if(/request ID body mismatch|market seller access denied|master/i.test(String(e?.message||e))){
      try{localStorage.removeItem(pendingKey(shopId))}catch(_){}
    }
    if(active(epoch,shopId))error(e);
  }
}
async function load(){
  const u=shop(),id=profileId(u);
  if(!u||!id||!permitted(u)){shell('<p>หน้านี้ใช้กับร้านตลาดสด ร้านเนื้อ ปลา ผัก หรือผลไม้เท่านั้น</p>');return;}
  const epoch=++state.epoch;state.shop=id;state.loading=true;shell('<p>กำลังโหลดแคตตาล็อกตลาด...</p>');
  await recoverPending(id,epoch);
  try{
    const [cats,page,gp]=await Promise.all([
      rpc('qg_market_master_categories',{}),
      rpc('qg_market_catalog_page',{p_query:state.query,p_category:state.category,p_offset:state.offset}),
      rpc('effective_gp_rate',{p_shop_profile_id:id})
    ]);
    state.gpRate=gpRate(gp);
    if(!active(epoch,id))return;
    state.categories=Array.isArray(cats)?cats:[];
    const rows=Array.isArray(page)?page:[];
    state.hasMore=rows.length>50;state.rows=rows.slice(0,50);paint();
  }catch(e){if(active(epoch,id)){shell('<p>โหลดแคตตาล็อกไม่สำเร็จ</p>');error(e)}}
  finally{if(active(epoch,id))state.loading=false}
}
window.qgRenderMarketCatalog=load;
window.qgMarketCatalogSearch=e=>{e?.preventDefault?.();state.query=String(document.getElementById('market-catalog-search')?.value||'').trim();state.category=String(document.getElementById('market-catalog-category')?.value||'');state.offset=0;load()};
window.qgMarketCatalogSetCategory=category=>{if(state.loading)return;state.category=String(category||'');state.offset=0;load()};
window.qgMarketCatalogPage=dir=>{if(state.loading)return;const next=Math.max(0,state.offset+(dir>0?50:-50));if(dir>0&&!state.hasMore)return;state.offset=next;load()};
window.qgMarketCatalogChoose=id=>{
  const r=state.rows.find(x=>x.id===id);if(!r||r.product_id)return;
  const suggestedUnit=UNITS.includes(String(r.suggested_unit||''))?String(r.suggested_unit):'กิโลกรัม';
  const pack=Number(r.suggested_pack_size)>0?Number(r.suggested_pack_size):1;
  const weight=suggestedUnit==='กิโลกรัม'?pack:suggestedUnit==='ขีด'?pack/10:suggestedUnit==='กรัม'?pack/1000:'';
  shell(`<form id="market-catalog-add-form" class="market-form">
    <button type="button" class="qgm-outline" onclick="qgRenderMarketCatalog()">‹ กลับแคตตาล็อก</button>
    <h2>${E(r.name_th)}</h2><p>${E(r.category)} · หน่วยอ้างอิง ${E(r.source_unit)}</p>
    <label>ราคาหน้าร้าน / ชุด (บาท)<input name="price" type="number" min="0.01" max="999999" step="0.01" required oninput="qgMarketCatalogPricePreview()"></label>
    <small id="market-catalog-delivery-preview">ราคาเดลิเวอรี่คำนวณ GP อัตโนมัติหลังบันทึก</small>
    <label>หน่วยสต๊อก<select name="unit">${UNITS.map(u=>`<option value="${E(u)}" ${u===suggestedUnit?'selected':''}>${E(u)}</option>`).join('')}</select></label>
    ${suggestedUnit==='กิโลกรัม'?'<div class="market-actions"><small>ขนาดขายที่ใช้บ่อย</small><button type="button" class="qgm-outline" onclick="qgMarketCatalogSetKg(1)">1 กก.</button><button type="button" class="qgm-outline" onclick="qgMarketCatalogSetKg(2)">2 กก.</button><button type="button" class="qgm-outline" onclick="qgMarketCatalogSetKg(3)">3 กก.</button></div>':''}<label>ปริมาณต่อ 1 ชุดขาย<input name="pack" type="number" min="0.001" max="999999" step="0.001" value="${E(pack)}" required></label>
    <label>น้ำหนักต่อชุด (กก.)<input name="weight" type="number" min="0.001" max="999999" step="0.001" value="${E(weight)}" placeholder="กรอกน้ำหนักจริง" required></label>
    <label>ต้นทุนต่อชุด (บาท)<input name="cost" type="number" min="0" max="999999" step="0.01" value="0" required></label>
    <label>สต๊อกเริ่มต้น<input name="stock" type="number" min="0" max="999999" step="0.001" value="0" required></label>
    <label>แจ้งเตือนใกล้หมด<input name="min" type="number" min="0" max="999999" step="0.001" value="0" required></label>
    <label class="market-inline"><input name="available" type="checkbox" checked> เปิดขาย Delivery</label>
    <div class="market-actions"><button class="qgm-primary" type="submit">เพิ่มสินค้าเข้าร้าน</button></div>
  </form>`);
  const form=document.getElementById('market-catalog-add-form');
  qgMarketCatalogPricePreview();
  form.onsubmit=async ev=>{
    ev.preventDefault();if(state.loading)return;
    const f=new FormData(form),body={
      p_master_product_id:r.id,
      p_price:Number(f.get('price')),
      p_unit:String(f.get('unit')),
      p_pack_size:Number(f.get('pack')),
      p_weight_kg:Number(f.get('weight')),
      p_cost:Number(f.get('cost')),
      p_initial_stock:Number(f.get('stock')),
      p_min_stock:Number(f.get('min')),
      p_available:f.has('available'),
      p_request_id:crypto.randomUUID()
    };
    if(!(body.p_price>0&&body.p_pack_size>0&&body.p_weight_kg>0&&body.p_cost>=0&&body.p_initial_stock>=0&&body.p_min_stock>=0))return error(Error('ตรวจราคา หน่วย น้ำหนัก ต้นทุน และสต๊อกอีกครั้ง'));
    state.loading=true;const submit=ev.submitter;if(submit)submit.disabled=true;
    try{
      localStorage.setItem(pendingKey(state.shop),JSON.stringify(body));
      await rpc('qg_market_catalog_add',body);
      localStorage.removeItem(pendingKey(state.shop));
      toast('เพิ่มสินค้าเข้าร้านแล้ว');
      state.loading=false;
      state.offset=0;await load();
    }catch(err){error(err);if(submit)submit.disabled=false;state.loading=false}
  };
};
})();
