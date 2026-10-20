/* Selected-shop inspection over existing products/market_products RLS. No stock mutations. */
(()=>{
'use strict';
const actor=()=>{const u=currentUser();return u?.type==='admin'&&u.status==='approved'?String(u.id):null};
const uuid=v=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(v||''));
let active=null;
const close=()=>{if(active){active.version++;active.box.remove();active=null}};
window.addEventListener('hashchange',close);
window.addEventListener('storage',()=>{if(active&&actor()!==active.owner)close()});
const number=value=>value!==null&&value!==undefined&&value!==''&&Number.isFinite(Number(value))?Number(value).toLocaleString('th-TH',{maximumFractionDigits:3}):'ไม่ระบุ';
window.qgAdminShopProducts=function(shopId,name='ร้านค้า'){
 const owner=actor();if(!owner||!uuid(shopId))return;close();
 const box=document.createElement('dialog');box.className='qg-admin-detail';box.style.width='min(880px,96vw)';box.style.maxHeight='85vh';box.style.overflow='auto';box.setAttribute('aria-label','สินค้าและสต๊อกร้าน');
 box.innerHTML='<div class="qg-menu-head"><div><h2>สินค้าและสต๊อก</h2><p class="qg-stock-shop-name"></p></div><button type="button" aria-label="ปิดสินค้าและสต๊อก">✕</button></div><form class="qg-stock-search"><label>ค้นหาสินค้าในร้าน<input type="search" maxlength="100" name="query"></label><button type="submit">ค้นหา</button><button type="button" class="qg-stock-refresh">รีเฟรช</button></form><p class="qg-stock-status" role="status"></p><div class="qg-stock-products"></div><div class="row-btns"><button type="button" class="qg-stock-prev">ก่อนหน้า</button><button type="button" class="qg-stock-next">ถัดไป</button></div>';
 box.querySelector('.qg-stock-shop-name').textContent=String(name);
 const state=active={owner,shopId,box,query:'',cursor:null,history:[],version:0,more:false,last:null};
 box.querySelector('.qg-menu-head button').onclick=close;
 box.addEventListener('cancel',e=>{e.preventDefault();close()});
 box.querySelector('form').onsubmit=e=>{e.preventDefault();state.query=box.querySelector('[name=query]').value.trim().replace(/[,.*()%_"\\]/g,'').slice(0,100);state.cursor=null;state.history=[];read(state)};
 box.querySelector('.qg-stock-refresh').onclick=()=>read(state);
 box.querySelector('.qg-stock-prev').onclick=()=>{if(active===state&&state.history.length){state.cursor=state.history.pop();read(state)}};
 box.querySelector('.qg-stock-next').onclick=()=>{if(active===state&&state.more&&state.last){state.history.push(state.cursor);state.cursor=state.last;read(state)}};
 box.addEventListener('close',()=>{if(active===state)close()});
 document.body.append(box);box.showModal();read(state);
};
async function read(state){
 const {box,owner,shopId}=state;if(active!==state||!box.isConnected||actor()!==owner){if(active===state)close();return}const version=++state.version;
 const alive=()=>active===state&&box.isConnected&&actor()===owner&&state.version===version;
 const status=box.querySelector('.qg-stock-status'),host=box.querySelector('.qg-stock-products');
 const prev=box.querySelector('.qg-stock-prev'),next=box.querySelector('.qg-stock-next'),refresh=box.querySelector('.qg-stock-refresh');prev.disabled=next.disabled=refresh.disabled=true;status.textContent='กำลังอ่านสินค้าในร้าน…';
 try{
  const token=await qtGetAccessToken();if(!alive())return;if(!token)throw Error('กรุณาเข้าสู่ระบบ Admin ใหม่');
  const path='products?select=id,name,category,price,delivery_price,available,stock,image,catalog_preview_image:metadata->>catalog_preview_image&shop_id=eq.'+encodeURIComponent(shopId)+'&order=id.asc&limit=51'+(state.cursor?'&id=gt.'+encodeURIComponent(state.cursor):'')+(state.query?'&name=ilike.*'+encodeURIComponent(state.query)+'*':'');
  const raw=await qtSupabaseTable(path,{accessToken:token});if(!alive())return;if(!Array.isArray(raw))throw Error('รูปแบบรายการสินค้าไม่ถูกต้อง');
  const rows=raw.slice(0,50),ids=rows.map(r=>r.id).filter(uuid);
  const stocks=ids.length?await qtSupabaseTable('market_products?select=product_id,unit,pack_size,stock_quantity,min_stock&shop_id=eq.'+encodeURIComponent(shopId)+'&product_id=in.('+ids.join(',')+')&limit=50',{accessToken:token}):[];
  if(!alive())return;if(!Array.isArray(stocks))throw Error('รูปแบบข้อมูลสต๊อกไม่ถูกต้อง');
  const byProduct=new Map(stocks.map(s=>[s.product_id,s]));host.replaceChildren();
  for(const row of rows){
   const card=document.createElement('article');card.className='card qg-stock-product';const stock=byProduct.get(row.id);
   const image=String(row.image||row.catalog_preview_image||'');if(/^https:\/\//.test(image)){const img=document.createElement('img');img.src=image;img.alt=String(row.name||'สินค้า');img.width=72;img.height=72;img.loading='lazy';img.style.objectFit='contain';img.onerror=()=>img.remove();card.append(img)}
   const heading=document.createElement('h3');heading.textContent=String(row.name||'สินค้า');card.append(heading);
   for(const text of [String(row.category||'ยังไม่ระบุหมวด')+' · '+(row.available===true?'เปิดขาย':'ปิดขาย'),
    'ราคาหน้าร้าน ฿'+number(row.price)+' · เดลิเวอรี่ ฿'+number(row.delivery_price),
    stock?'สต๊อกตลาด '+number(stock.stock_quantity)+' '+String(stock.unit||'')+' · ปริมาณต่อชุด '+number(stock.pack_size)+' · แจ้งเตือนใกล้หมด '+number(stock.min_stock):'สต๊อกสินค้า '+number(row.stock)]){const line=document.createElement('p');line.textContent=text;card.append(line)}
   host.append(card);
  }
  if(!rows.length){const p=document.createElement('p');p.textContent='ไม่พบสินค้าในร้านตามคำค้นนี้';host.append(p)}
  state.more=raw.length>50;state.last=rows.at(-1)?.id||null;
  status.textContent='หน้า '+(state.history.length+1)+' · '+rows.length+' รายการ · ตรวจข้อมูลล่าสุดด้วยปุ่มรีเฟรช';
  prev.disabled=!state.history.length;next.disabled=!state.more;
 }catch(e){if(alive())status.textContent='อ่านสินค้าและสต๊อกไม่ได้: '+e.message}
 finally{if(alive())refresh.disabled=false}
}
})();
