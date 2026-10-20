const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<12;i++)await new Promise(r=>setImmediate(r))};
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const d=new JSDOM('<main></main>',{url:'https://isolated.invalid/admin/#admin-governance',runScripts:'outside-only'}),w=d.window;
 w.HTMLDialogElement.prototype.showModal=function(){};
 let actor={id:id(999),type:'admin',status:'approved'},calls=[],empty=false,held=null;
 w.currentUser=()=>actor;w.qtGetAccessToken=async()=> 'fixture';
 w.qtSupabaseTable=async(path,options)=>{
  calls.push(path);assert(!options.method||options.method==='GET','inspection must never mutate');
  if(path.startsWith('products?')){
   if(held){const wait=held;held=null;await wait}
   if(empty)return [];
   const rows=path.includes('&id=gt.')?[{id:id(51),name:'หน้าถัดไป',price:20,delivery_price:22,stock:2,available:true}]:Array.from({length:51},(_,i)=>({id:id(i),name:'สินค้า <script>'+i,category:'ปลา',price:100,delivery_price:110,stock:999,available:true,image:i===0?'javascript:alert(1)':'https://isolated.invalid/product.jpg'}));
   return rows;
  }
  if(path.startsWith('market_products?'))return [{product_id:id(0),unit:'กิโลกรัม',pack_size:2.25,stock_quantity:4.5,min_stock:1}];
  throw Error('unexpected '+path);
 };
 const source=fs.readFileSync('admin/shop-products.js','utf8');w.eval(source);let checks=0;
 w.qgAdminShopProducts(id(800),'ร้าน <script>');await tick();
 assert.equal(w.document.querySelectorAll('.qg-stock-product').length,50);assert.equal(w.document.querySelectorAll('script').length,0);assert.equal(w.document.querySelectorAll('img[src^="javascript:"]').length,0);checks+=3;
 assert(calls[0].includes('shop_id=eq.'+id(800)));assert(calls[0].includes('limit=51'));assert(!calls[0].includes('offset='));checks+=3;
 assert(calls[1].includes('shop_id=eq.'+id(800)));assert(calls[1].includes('product_id=in.('));assert(calls[1].includes('limit=50'));assert(!calls[1].includes(id(50)));checks+=4;
 const first=w.document.querySelector('.qg-stock-product').textContent;assert(first.includes('สต๊อกตลาด 4.5 กิโลกรัม'));assert(!first.includes('999'));assert(first.includes('2.25'));checks+=3;
 w.document.querySelector('.qg-stock-next').click();await tick();assert(calls.some(p=>p.startsWith('products?')&&p.includes('id=gt.'+id(49))));assert(w.document.querySelector('.qg-stock-next').disabled);checks+=2;
 const beforeRefresh=calls.length;w.document.querySelector('.qg-stock-refresh').click();await tick();assert(calls[beforeRefresh].includes('id=gt.'+id(49)));checks++;
 w.document.querySelector('.qg-stock-prev').click();await tick();assert(!calls[calls.length-2].includes('&id=gt.'));checks++;
 empty=true;const beforeEmpty=calls.length;w.document.querySelector('[name=query]').value='หมู,*%';w.document.querySelector('form').onsubmit({preventDefault(){}});await tick();assert.equal(calls.length,beforeEmpty+1);assert(calls.at(-1).includes('&name=ilike.*'+encodeURIComponent('หมู')+'*'));assert(w.document.querySelector('.qg-stock-products').textContent.includes('ไม่พบสินค้า'));checks+=3;
 empty=false;let release;held=new Promise(r=>release=r);w.document.querySelector('.qg-stock-refresh').click();await tick();
 w.qgAdminShopProducts(id(801),'ร้านใหม่');await tick();release();await tick();assert.equal(w.document.querySelectorAll('dialog').length,1);assert.equal(w.document.querySelector('.qg-stock-shop-name').textContent,'ร้านใหม่');checks+=2;
 actor={id:id(998),type:'customer',status:'approved'};w.dispatchEvent(new w.StorageEvent('storage',{key:'session'}));assert.equal(w.document.querySelectorAll('dialog').length,0);const beforeDenied=calls.length;w.qgAdminShopProducts(id(800));await tick();assert.equal(calls.length,beforeDenied);checks+=2;
 assert(!/method\s*:\s*['"](?:POST|PATCH|DELETE)/.test(source));checks++;
 d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'selected-shop products/stock read through existing RLS, 50/51 ID cursors, exact page stock IDs, fractional Market stock authority, same-page refresh, search, XSS and account/close races; no live mutations'}));
})().catch(e=>{console.error(e);process.exit(1)});
