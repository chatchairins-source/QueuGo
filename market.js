/* QueueGo Market — shop-first storefront. Products are shown only after a shop is selected. */
(()=>{
'use strict';
let rows=[],marketShops=[],query='';
const h=v=>escapeHtml(String(v??''));
const available=r=>Math.max(0,Math.floor(Number(r.available_packs||0)));
function intoExistingCatalog(){
  const products=dbGet('qt_products',[]),byId=new Map(products.map(p=>[String(p.id),p]));
  for(const r of rows) byId.set(String(r.product_id),{...byId.get(String(r.product_id)),id:r.product_id,shopId:r.shop_user_id,shopProfileId:r.shop_id,name:r.name,category:r.category,description:r.description||'',image:r.image||'',price:Number(r.price),available:available(r)>0,marketUnit:r.unit,marketPackSize:Number(r.pack_size)});
  QT_DB_CACHE.qt_products=[...byId.values()];
}
function shops(){const p=new Map();for(const r of rows){const id=String(r.shop_id||'');if(!id)continue;if(!p.has(id))p.set(id,{count:0,available:0,categories:new Set()});const x=p.get(id);x.count++;if(available(r)>0)x.available++;if(r.category)x.categories.add(r.category)}return marketShops.map(s=>{const x=p.get(String(s.shop_id))||{count:0,available:0,categories:new Set()};return{id:s.shop_id,userId:s.shop_user_id,name:s.shop_name||'ร้านตลาดสด',category:s.shop_category||'market',logo:s.shop_logo||'',count:x.count,available:x.available,categories:x.categories}})}
function shopCard(s){
  const cats=[...s.categories].slice(0,3).map(h).join(' · ');
  return `<button type="button" class="market-shop-card" onclick="navigate('market-shop/${h(s.id)}')"><div class="market-shop-photo">${s.logo?`<img src="${h(s.logo)}" alt="${h(s.name)}" loading="lazy">`:'<span>🏪</span>'}</div><div class="market-shop-detail"><small>ร้านตลาดสด</small><h3>${h(s.name)}</h3><p>${cats||'สินค้าในตลาดสด'}</p><span>${s.available} รายการพร้อมขาย</span></div><b aria-hidden="true">›</b></button>`;
}
function paintShops(){
  const box=document.getElementById('market-results');if(!box)return;
  const q=query.toLocaleLowerCase('th-TH');
  const list=shops().filter(s=>!q||(s.name+' '+[...s.categories].join(' ')).toLocaleLowerCase('th-TH').includes(q));
  box.innerHTML=list.length?list.map(shopCard).join(''):'<p class="market-empty">ยังไม่มีร้านตลาดสดที่ตรงกับการค้นหา</p>';
}
window.qgMarketSearch=value=>{query=String(value||'').trim();paintShops()};
window.qgMarketAdd=id=>{const r=rows.find(x=>String(x.product_id)===String(id));if(!r||!available(r))return toast('สินค้าหมด');intoExistingCatalog();addToCart(id);updateCartCountUI?.()};
window.renderMarket=function(){
  layout('ตลาดสด',`<section class="market-hero market-hero-photo"><img src="queuego-market-ai-banner.jpg" alt="ตลาดสด QueueGo"><div class="market-hero-copy"><small>QueueGo Market</small><h1>ตลาดสดใกล้คุณ</h1><p>ของสดใหม่ ส่งถึงบ้านในพื้นที่</p></div></section><label class="market-search"><span>ค้นหาร้านตลาดสด</span><input type="search" placeholder="ค้นหาชื่อร้าน" oninput="qgMarketSearch(this.value)"></label><h2 class="market-heading">ร้านค้า</h2><div id="market-results" class="market-shop-list"><p>กำลังโหลดร้านตลาดสด...</p></div>`);
  Promise.all([qtSupabaseTable('rpc/market_public_shops',{method:'POST',body:{}}),qtSupabaseTable('rpc/market_public_catalog',{method:'POST',body:{}}).catch(()=>[])]).then(([sd,pd])=>{if(!document.getElementById('market-results'))return;marketShops=Array.isArray(sd)?sd:[];rows=Array.isArray(pd)?pd:[];intoExistingCatalog();paintShops()}).catch(err=>{const box=document.getElementById('market-results');if(box)box.textContent='โหลดร้านไม่สำเร็จ: '+(err.message||err)});
};
window.renderMarketShop=function(id){
  const render=()=>{
    const list=rows.filter(r=>String(r.shop_id)===String(id)),first=list[0];
    if(!first){layout('ตลาดสด','<button class="btn-secondary" onclick="navigate(\'market\')">← กลับตลาดสด</button><p class="market-empty">ไม่พบร้านนี้หรือร้านยังไม่มีสินค้าพร้อมขาย</p>');return;}
    intoExistingCatalog();
    layout(first.shop_name||'ร้านตลาดสด',`<section class="market-shop-head"><button type="button" class="btn-secondary" onclick="navigate('market')">← ร้านตลาดสด</button><div>${first.shop_logo?`<img src="${h(first.shop_logo)}" alt="">`:'<span>🏪</span>'}<div><small>QueueGo Market</small><h1>${h(first.shop_name)}</h1><p>เลือกสินค้าจากร้านนี้</p></div></div></section><div class="market-list">${list.map(r=>`<article class="market-card"><div class="market-photo">${r.image?`<img src="${h(r.image)}" alt="${h(r.name)}" loading="lazy">`:'🛒'}</div><div class="market-detail"><small>${h(r.category)}</small><h3>${h(r.name)}</h3><p>${Number(r.pack_size)} ${h(r.unit)} / ชุด · เหลือ ${available(r)} ชุด</p><div><b>${Number(r.price).toLocaleString('th-TH')} ฿</b><button type="button" ${available(r)?'':'disabled'} onclick="qgMarketAdd('${h(r.product_id)}')">${available(r)?'เพิ่มสินค้า':'หมด'}</button></div></div></article>`).join('')}</div>`);
  };
  if(rows.length)return render();
  qtSupabaseTable('rpc/market_public_catalog',{method:'POST',body:{}}).then(data=>{rows=Array.isArray(data)?data:[];render()}).catch(err=>layout('ตลาดสด','<p class="market-empty">โหลดร้านไม่สำเร็จ: '+h(err.message||err)+'</p>'));
};
const route=window.qtRouteRender;window.qtRouteRender=function(){
  const parts=location.hash.replace('#','').split('/'),page=parts[0];
  if(page==='market'||page==='market-shop'){const u=currentUser();if(u&&u.type!=='customer')return renderQueueGoRoleNotice(u);return page==='market'?renderMarket():renderMarketShop(parts[1])}
  return route();
};
const home=window.renderHome;window.renderHome=function(){home();const target=document.getElementById('qg-categories');if(target&&!target.querySelector('.market-home-link')){const b=document.createElement('button');b.type='button';b.className='qg-cat market-home-link';b.innerHTML='<span class="qg-cat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 9h18l-2 11H5L3 9Z"/><path d="m7 9 5-6 5 6M9 13v4m6-4v4"/></svg></span><b>ตลาดสด</b>';b.setAttribute('aria-label','ตลาดสด QueueGo');b.onclick=()=>navigate('market');target.append(b)}};
})();