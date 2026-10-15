'use strict';
// Whole Merchant document and shipped scripts: never replace shopUser/profileId/layout.
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'..'),html=fs.readFileSync(root+'/merchant/index.html','utf8');
const source=JSON.parse(fs.readFileSync(root+'/catalog/thai-grocery-import.json','utf8'))[0];
const tick=()=>new Promise(r=>setImmediate(r));
(async()=>{
 let checks=0;const ok=(v,m)=>{assert(v,m);checks++};
 const d=new JSDOM(html,{url:'https://queuego.test/merchant/#shop-grocery-catalog',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window,ctx=d.getInternalVMContext();
 const session={role:'shop',userId:'isolated-owner',authUserId:'isolated-auth',accessToken:'isolated-token',refreshToken:'isolated-refresh',expiresAt:Date.now()+3600000};
 w.localStorage.setItem('qg_merchant_session_v1',JSON.stringify(session));
 w.AbortController=AbortController;w.AbortSignal={};w.crypto.randomUUID=require('crypto').randomUUID;
 w.setInterval=()=>0;w.setTimeout=(fn,ms)=>ms===0?setTimeout(fn,0):0;w.requestAnimationFrame=()=>0;
 w.fetch=async url=>({ok:true,status:200,json:async()=>{
  if(url.includes('shop_profiles?'))return [{id:'isolated-shop',user_id:'isolated-owner',public_category:'grocery'}];
  if(url.includes('/rpc/qg_grocery_categories'))return [{category:source.category,n:1}];
  if(url.includes('/rpc/qg_grocery_source_search'))return [{source:'makro-pro',source_product_id:'isolated-source',brand:source.brand,title_th:source.product_name_th,barcode:source.barcode||null,deepest_category:'dry-grocery/instant-meals/instant-noodles',image_url:null,in_shop:false}];
  if(url.includes('/rpc/qg_grocery_merchant_page'))return [{...source,id:'isolated-master',selling_price:null,stock_qty:0,enabled:false}];
  return [];
 }});
 for(const s of w.document.scripts){let code=s.textContent;if(s.src){const u=new URL(s.src);if(u.hostname!=='queuego.test')continue;code=fs.readFileSync(root+u.pathname,'utf8')}if(code.trim())vm.runInContext(code,ctx)}
 for(const name of ['shopUser','profileId','layout'])ok(typeof w[name]==='function',name+' must be available to the external Catalog file');
 await tick();await w.qgRenderGroceryCatalog({addMode:true});
 ok(w.document.querySelectorAll('.qgg-row').length===1,'full Merchant shell and Catalog must render together');
 ok(w.document.getElementById('app').textContent.includes(source.product_name_th),'catalog transport row must appear in the real Merchant layout');
 ok(!w.document.querySelector('#qt-shop-login'),'existing session must not be redirected to Login');
 const timeout=w.qtRequestTimeout(15000);ok(timeout.signal instanceof AbortSignal,'Safari fallback must supply an AbortController signal');timeout.clear();
 // Async route rejection used to escape router's synchronous try/catch.
 w.qtShopRoute=async()=>{throw Error('isolated renderer fault')};w.router();await tick();
 ok(w.document.querySelector('#qgm-route-error').textContent.includes('isolated renderer fault'),'rejected renderer must expose its failure and retain shell');
 w.document.getElementById('app').replaceChildren();await tick();
 ok(w.document.getElementById('app').textContent.trim().length>0,'empty app must recover even outside the router');
 d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'whole-document Merchant/Catalog integration, Safari timeout fallback and nonempty-app recovery; isolated transport only'}));
})().catch(e=>{console.error(e);process.exitCode=1});
