/* QueueGo Market Master Catalog — additive Merchant selector over existing Market stock. */
(()=>{
'use strict';
const MARKET_SHOPS=new Set(['market','meat','fish','vegetable','fruit']);
const MARKET_MASTER_CATEGORIES=new Set(['ผักสด','เห็ด','ผลไม้','เนื้อหมู','เนื้อวัว','ไก่ / เป็ด','ปลา','กุ้ง','ปู','หอย','ปลาหมึก','ไข่','เต้าหู้ / เส้นสด / ลูกชิ้น','ของแห้ง']);
const UNITS=['ชิ้น','กิโลกรัม','ขีด','กรัม','ถุง','แพ็ก','มัด','ลูก','ขวด','กล่อง'];
const E=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const state={epoch:0,rows:[],categories:[],query:'',category:'',offset:0,hasMore:false,loading:false,shop:null,gpRate:null};
let searchTimer=null;
const shop=()=>{const u=currentUser?.();return u?.type==='shop'?u:null};
const profileId=u=>u&&(u.shopProfileId||QT_SYNC?.profileMaps?.shopByUserId?.get(u.id)||null);
const shopCategory=u=>typeof window.qgmShopCategory==='function'?window.qgmShopCategory(u):String(u?.public_category||u?.category||u?.metadata?.category||'').trim().toLowerCase();
const permitted=u=>MARKET_SHOPS.has(shopCategory(u));
const pendingKey=id=>'qg_market_catalog_pending_v1:'+id;
async function rpc(name,body){
  const accessToken=await qtGetAccessToken();
  if(!accessToken)throw Error('กรุณาเข้าสู่ระบบใหม่');
  return qtSupabaseTable('rpc/'+name,{method:'POST',accessToken,body});
}
function shell(body){
  qtShopLayout('เพิ่มสินค้าจากแคตตาล็อก',`<section class="qgg qgg-merchant qgg-market-catalog">
    <div class="qgm-unified-intro"><b>เพิ่มสินค้าจากแคตตาล็อก</b><small>เลือกสินค้า ใส่ราคาของร้าน แล้วเพิ่มเข้าร้าน · หน้าตาและการใช้งานแบบเดียวกับแคตตาล็อกร้านขายของชำ</small></div>
    <div class="qgg-primary-actions">
      <button type="button" onclick="qgMarketEdit()">เพิ่มสินค้าเอง</button>
      <button type="button" onclick="navigate('market-stock')">← กลับไปจัดการสินค้า</button>
    </div>
    <div id="market-catalog-error" role="alert"></div>
    ${body}
    <p class="qgg-source">รูปในแคตตาล็อกใช้ช่วยร้านค้าตรวจสินค้า ส่วนราคา หน่วยขาย น้ำหนัก ต้นทุน และสต๊อกเป็นข้อมูลของร้าน</p>
  </section>`,'shop-profile');
}
function error(e){const msg=String(e?.message||e||'เกิดข้อผิดพลาด');const el=document.getElementById('market-catalog-error');if(el)el.textContent=msg;try{toast('แคตตาล็อกตลาด: '+msg)}catch(_){}}

function deliveryPrice(storePrice){if(state.gpRate==null)return null;const p=Number(storePrice),r=Number(state.gpRate);return Number.isFinite(p)&&p>0&&Number.isFinite(r)&&r>=0&&r<=100?Math.round(p*(1+r/100)*100)/100:null}
function deliveryNote(storePrice){const d=deliveryPrice(storePrice),r=Number(state.gpRate);return Number.isFinite(d)?`GP ${r.toLocaleString('th-TH',{maximumFractionDigits:2})}% · ราคาเดลิเวอรี่ ${d.toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2})} บาท`:'ราคาเดลิเวอรี่คำนวณ GP อัตโนมัติหลังบันทึก'}
window.qgMarketCatalogPricePreview=()=>{const input=document.querySelector('#market-catalog-add-form [name="price"]'),out=document.getElementById('market-catalog-delivery-preview');if(out)out.textContent=deliveryNote(input?.value)};
window.qgMarketCatalogSetKg=kg=>qgMarketSetWeightPreset(document.getElementById('market-catalog-add-form'),kg);
function active(epoch,shopId){return epoch===state.epoch&&shopId===state.shop&&location.hash.replace('#','').split('/')[0]==='market-catalog'}
function productImage(r){
  const src=String(r.catalog_preview_image||'').trim();
  const image=src
    ? `<img class="market-catalog-image" src="${E(src)}" alt="${E(r.name_th)}" loading="lazy" referrerpolicy="no-referrer" onerror="qgMarketCatalogImageError(this)">`
    : `<div class="market-catalog-image market-catalog-placeholder" aria-label="ยังไม่มีรูป ${E(r.name_th)}">${E((r.name_th||'?').charAt(0))}</div>`;
  return `<div class="market-catalog-media" data-preview="${src?'catalog':'missing'}">${image}<div class="market-catalog-image-status"></div></div>`;
}
function rowImage(r){
  const src=String(r.catalog_preview_image||'').trim();
  return src
    ? `<img src="${E(src)}" alt="${E(r.name_th)}" loading="lazy" referrerpolicy="no-referrer" onerror="qgMarketCatalogRowImageError(this)">`
    : '<span class="qgg-image-pending">รอรูป</span>';
}
window.qgMarketCatalogRowImageError=img=>{
  const span=document.createElement('span');
  span.className='qgg-image-pending';
  span.textContent='รอรูป';
  img.replaceWith(span);
};
window.qgMarketCatalogInlinePrice=input=>{
  const card=input?.closest?.('[data-market-id]'),note=card?.querySelector('.qgg-gp-note');
  if(note)note.textContent=deliveryNote(input.value);
};
window.qgMarketCatalogImageError=img=>{
  const media=img.closest('.market-catalog-media');
  const fallback=document.createElement('div');
  fallback.className='market-catalog-image market-catalog-placeholder';
  fallback.textContent='โหลดรูปไม่สำเร็จ';
  fallback.setAttribute('role','img');
  fallback.setAttribute('aria-label','โหลดรูป '+img.alt+' ไม่สำเร็จ');
  img.replaceWith(fallback);
  const status=media?.querySelector('.market-catalog-image-status');
  if(status){const note=document.createElement('small');note.textContent='โหลดรูปไม่สำเร็จ · ลองเปิดหน้านี้อีกครั้ง';status.prepend(note)}
};
function card(r){
  const linked=!!r.product_id;
  const storePrice=linked&&Number(r.price)>0?Number(r.price):'';
  const stock=linked&&Number.isFinite(Number(r.stock_quantity))?Number(r.stock_quantity):0;
  return `<article class="qgg-row qgg-source-row market-catalog-card" data-market-id="${E(r.id)}">
    ${rowImage(r)}
    <div class="qgg-info">
      <b>${E(r.name_th)}</b>
      <small>${E(r.category)} · ${E(r.source_unit||'เลือกหน่วยขายตอนเพิ่มสินค้า')}</small>
      <div class="qgg-controls qgg-price-controls">
        <label>ราคาหน้าร้าน<input data-market-price type="number" inputmode="decimal" min="0.01" max="999999" step="0.01" value="${E(storePrice)}" placeholder="ใส่ราคาขาย" ${linked?'disabled':''} oninput="qgMarketCatalogInlinePrice(this)"></label>
        <label>สต๊อก<input data-market-stock type="number" inputmode="decimal" min="0" max="999999" step="0.001" value="${E(stock)}" ${linked?'disabled':''}></label>
      </div>
      <div class="qgg-controls qgg-channel-controls">
        <label><input data-market-delivery type="checkbox" ${r.available===false?'':'checked'} ${linked?'disabled':''}> เดลิเวอรี่ลูกค้า</label>
      </div>
      <div class="qgg-gp-note">${linked&&Number(r.delivery_price)>0?'ราคาเดลิเวอรี่ '+Number(r.delivery_price).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2})+' บาท':deliveryNote(storePrice)}</div>
      <button type="button" class="${linked?'qgm-outline':'qgm-primary'}" ${linked?'disabled':''} onclick="qgMarketCatalogChoose('${E(r.id)}')">${linked?'มีในร้านแล้ว':'เพิ่มสินค้าเข้าร้าน'}</button>
    </div>
  </article>`;
}
function paint(){
  const categories=`<option value="">ทุกหมวด</option>`+state.categories.map(c=>`<option value="${E(c.category)}" ${c.category===state.category?'selected':''}>${E(c.category)} (${Number(c.n)})</option>`).join('');
  const rows=state.rows.length
    ? state.rows.map(card).join('')
    : '<div class="qgg-catalog-empty">ไม่พบสินค้าในเงื่อนไขนี้</div>';
  shell(`<label class="qgm-search"><input id="market-catalog-search" type="search" value="${E(state.query)}" placeholder="ค้นหาชื่อสินค้า" aria-label="ค้นหาสินค้า" oninput="qgMarketCatalogQueueSearch(this.value)"></label>
    <div class="qgg-controls"><label>หมวดหมู่สินค้า<select id="market-catalog-category" aria-label="หมวดหมู่สินค้า" onchange="qgMarketCatalogSetCategory(this.value)">${categories}</select></label></div>
    <p id="market-catalog-status" role="status" aria-live="polite">${state.rows.length?'เลือกสินค้า ใส่ราคา แล้วกดเพิ่มสินค้าเข้าร้าน':'ลองค้นชื่อสินค้า หรือเปลี่ยนหมวดหมู่'}</p>
    <div id="market-catalog-rows">${rows}</div>
    <div class="qgg-footer">
      <button type="button" onclick="qgMarketCatalogPage(-1)" ${state.offset===0?'disabled':''}>ก่อนหน้า</button>
      <span>หน้า ${Math.floor(state.offset/50)+1}</span>
      <button type="button" onclick="qgMarketCatalogPage(1)" ${state.hasMore?'':'disabled'}>ถัดไป</button>
    </div>`);
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
      qgMarketReadGpRate(id)
    ]);
    state.gpRate=gp;
    if(!active(epoch,id))return;
    state.categories=(Array.isArray(cats)?cats:[]).filter(c=>MARKET_MASTER_CATEGORIES.has(String(c?.category||'')));
    const rows=(Array.isArray(page)?page:[]).filter(r=>MARKET_MASTER_CATEGORIES.has(String(r?.category||'')));
    state.hasMore=rows.length>50;state.rows=rows.slice(0,50);paint();
  }catch(e){if(active(epoch,id)){shell('<p>โหลดแคตตาล็อกไม่สำเร็จ</p>');error(e)}}
  finally{if(active(epoch,id))state.loading=false}
}
window.qgRenderMarketCatalog=load;
window.qgMarketCatalogSearch=e=>{e?.preventDefault?.();state.query=String(document.getElementById('market-catalog-search')?.value||'').trim();state.category=String(document.getElementById('market-catalog-category')?.value||'');state.offset=0;load()};
window.qgMarketCatalogQueueSearch=value=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{if(state.loading)return;state.query=String(value||'').trim();state.offset=0;load()},300)};
window.qgMarketCatalogSetCategory=category=>{if(state.loading)return;state.category=String(category||'');state.offset=0;load()};
window.qgMarketCatalogPage=dir=>{if(state.loading)return;const next=Math.max(0,state.offset+(dir>0?50:-50));if(dir>0&&!state.hasMore)return;state.offset=next;load()};
window.qgMarketCatalogChoose=id=>{
  const r=state.rows.find(x=>x.id===id);if(!r||r.product_id)return;
  const card=[...document.querySelectorAll('[data-market-id]')].find(x=>x.getAttribute('data-market-id')===String(id));
  const inlinePrice=Number(card?.querySelector('[data-market-price]')?.value),inlineStock=Number(card?.querySelector('[data-market-stock]')?.value);
  const inlineAvailable=card?.querySelector('[data-market-delivery]')?.checked!==false;
  const suggestedUnit=UNITS.includes(String(r.suggested_unit||''))?String(r.suggested_unit):'กิโลกรัม';
  const pack=Number(r.suggested_pack_size)>0?Number(r.suggested_pack_size):1;
  const weight=suggestedUnit==='กิโลกรัม'?pack:suggestedUnit==='ขีด'?pack/10:suggestedUnit==='กรัม'?pack/1000:'';
  shell(`<form id="market-catalog-add-form" class="market-form">
    <button type="button" class="qgm-outline" onclick="qgRenderMarketCatalog()">‹ กลับแคตตาล็อก</button>
    <div class="market-catalog-preview">${productImage(r)}</div>
    <h2>${E(r.name_th)}</h2><p>${E(r.category)} · ขนาดสินค้า ${E(r.source_unit)}</p>
    <label>ราคาหน้าร้าน / ชุด (บาท)<input name="price" type="number" min="0.01" max="999999" step="0.01" value="${Number.isFinite(inlinePrice)&&inlinePrice>0?E(inlinePrice):''}" required oninput="qgMarketCatalogPricePreview()"></label>
    <small id="market-catalog-delivery-preview">ราคาเดลิเวอรี่คำนวณ GP อัตโนมัติหลังบันทึก</small>
    <label>หน่วยสต๊อก<select name="unit">${UNITS.map(u=>`<option value="${E(u)}" ${u===suggestedUnit?'selected':''}>${E(u)}</option>`).join('')}</select></label>
    <label>ปริมาณต่อ 1 ชุดขาย<input name="pack" type="number" min="0.001" max="999999" step="0.001" value="${E(pack)}" required></label>
    <label>น้ำหนักต่อชุด (กก.)<input name="weight" type="number" min="0.001" max="999999" step="0.001" value="${E(weight)}" placeholder="กรอกน้ำหนักจริง" required></label>
    <label>ต้นทุนต่อชุด (บาท)<input name="cost" type="number" min="0" max="999999" step="0.01" value="0" required></label>
    <label>สต๊อกเริ่มต้น<input name="stock" type="number" min="0" max="999999" step="0.001" value="${Number.isFinite(inlineStock)&&inlineStock>=0?E(inlineStock):0}" required></label>
    <label>แจ้งเตือนใกล้หมด<input name="min" type="number" min="0" max="999999" step="0.001" value="0" required></label>
    <label class="market-inline"><input name="available" type="checkbox" ${inlineAvailable?'checked':''}> เดลิเวอรี่ลูกค้า</label>
    <div class="market-actions"><button class="qgm-primary" type="submit">เพิ่มสินค้าเข้าร้าน</button></div>
  </form>`);
  const form=document.getElementById('market-catalog-add-form');
  qgMarketAttachWeightPresets(form);
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
