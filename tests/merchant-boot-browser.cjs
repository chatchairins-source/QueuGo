'use strict';
// Real WebKit executes the complete shipped HTML, not an extracted/stubbed renderer.
// All data/session/transport fixtures below are confined to localhost QA.
const fs=require('fs'),http=require('http'),path=require('path'),assert=require('node:assert/strict');
const {webkit}=require(process.env.MERCHANT_PLAYWRIGHT_MODULE||'playwright-core');
const root=path.resolve(__dirname,'..'),facts=JSON.parse(fs.readFileSync(root+'/catalog/thai-grocery-import.json'));
const owner='00000000-0000-4000-8000-000000000001',shop='00000000-0000-4000-8000-000000000002';
(async()=>{
 const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)){res.writeHead(403).end();return}let target=new URL(req.url,'http://localhost').pathname.endsWith('/')?path.join(file,'index.html'):file;try{res.setHeader('Content-Type',target.endsWith('.js')?'text/javascript':target.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(target))}catch(e){res.writeHead(404).end()}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await webkit.launch({headless:true,...(process.env.MERCHANT_WEBKIT_PATH?{executablePath:process.env.MERCHANT_WEBKIT_PATH}:{})});let checks=0;
 try{
 for(const width of [390,430])for(const scenario of ['fresh','session','expired','refresh-timeout','timeout','slow-catalog','missing-pos','old-safari','expired-old-safari','optional-hang','rejected-renderer']){
  const context=await browser.newContext({viewport:{width,height:width===390?844:932},isMobile:true,hasTouch:true});const page=await context.newPage(),errors=[];page.on('pageerror',e=>{errors.push(e.message);console.error('PAGEERROR',scenario,e.stack)});console.log('scenario',width,scenario);if(process.env.MERCHANT_SCENARIO&&scenario!==process.env.MERCHANT_SCENARIO){await context.close();continue;}
  let saved=null,refreshes=0;
  await context.route('https://**/*',async route=>{
   const url=route.request().url();
   if(!url.includes('.supabase.co/')){if(scenario==='optional-hang')return;return route.abort();}
   if(scenario==='timeout'||scenario==='refresh-timeout')return; // Browser must retain a visible shell while requests stall.
   if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers:{'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Headers':'content-type,apikey,authorization,prefer','Access-Control-Allow-Methods':'GET,POST,PATCH,PUT,DELETE,OPTIONS'}});
   let data=[];
   if(url.includes('/auth/v1/token')){refreshes++;data={access_token:'isolated-refreshed-token',refresh_token:'isolated-refresh',expires_at:Math.floor(Date.now()/1000)+3600}}
   else if(url.includes('/users?'))data=[{id:owner,auth_user_id:owner,role:'shop',status:'active',name:'Isolated QA owner',created_at:new Date().toISOString()}];
   else if(url.includes('/shop_profiles?'))data=[{id:shop,user_id:owner,public_category:'grocery',shop_name:'Isolated QA shop',onboarding_status:'approved'}];
   else if(url.includes('/products?'))data=Array.from({length:60},(_,i)=>({id:'30000000-0000-4000-8000-'+String(i+1).padStart(12,'0'),shop_id:shop,name:'isolated priced product '+i,price:8,pos_price:8,category:i%2?'เครื่องปรุง':'บะหมี่กึ่งสำเร็จรูป',available:true})).concat([{id:'30000000-0000-4000-8000-000000000099',shop_id:shop,name:'isolated unpriced draft',price:0,pos_price:0,category:'ของแห้ง',available:false}]);
   else if(url.includes('rpc/qg_grocery_source_categories'))data=[{category:'บะหมี่กึ่งสำเร็จรูป',n:1}];
   else if(url.includes('rpc/qg_grocery_categories'))data=[{category:facts[0].category,n:1}];
   else if(url.includes('rpc/qg_grocery_source_search'))data=[{source:'makro-pro',source_product_id:'isolated-source',brand:facts[0].brand,title_th:facts[0].product_name_th,barcode:facts[0].barcode||null,deepest_category:'dry-grocery/instant-meals/instant-noodles',image_url:null,in_shop:false}];
   else if(url.includes('rpc/qg_grocery_source_add_to_shop'))data={product_id:'isolated-source-product',delivery_price:8.8};
   else if(url.includes('rpc/qg_grocery_merchant_page'))data=[{...facts[0],id:'00000000-0000-4000-8000-000000000003',selling_price:saved?.selling_price??null,stock_qty:0,stock_tracking:false,enabled:saved?.enabled??false,pos_available:saved?.pos_available??true,delivery_available:saved?.delivery_available??true}];
   else if(url.includes('rpc/qg_grocery_store_save')){const body=route.request().postDataJSON();saved=body.p_rows[0];data={saved:body.p_rows.length}}
   await route.fulfill({status:200,contentType:'application/json',headers:{'Access-Control-Allow-Origin':origin},body:JSON.stringify(data)});
  });
  if(scenario==='missing-pos')await context.route('**/pos-table-v2.js?*',r=>r.abort());
  if(scenario==='slow-catalog')await context.route('**/grocery-catalog.js?*',async r=>{await new Promise(resolve=>setTimeout(resolve,1000));await r.continue()});
  await context.addInitScript(({owner,scenario})=>{
   if(scenario.includes('old-safari'))Object.defineProperty(AbortSignal,'timeout',{value:undefined});
   if(scenario!=='fresh')localStorage.setItem('qg_merchant_session_v1',JSON.stringify({role:'shop',userId:owner,authUserId:owner,accessToken:'isolated-token',refreshToken:'isolated-refresh',expiresAt:['expired','expired-old-safari','refresh-timeout'].includes(scenario)?Date.now()-1000:Date.now()+3600000}));
   document.addEventListener('DOMContentLoaded',()=>{window.qaAppAtDomReady=!!document.getElementById('app')?.innerText.trim()});
  },{owner,scenario});
  await page.goto(origin+'/merchant/'+(scenario==='fresh'?'':'#shop-grocery-catalog'),{waitUntil:'domcontentloaded'});
  assert(await page.evaluate(()=>window.qaAppAtDomReady),'visible #app at DOMContentLoaded: '+scenario);checks++;
  if(scenario==='fresh'){await page.locator('#qt-shop-login').waitFor();checks++}
  else{
   await page.locator('#qgg-search').waitFor();checks++;
   if(scenario==='refresh-timeout'){await page.locator('#qgg-status').filter({hasText:'โหลดไม่สำเร็จ'}).waitFor({timeout:25000});assert.equal(await page.locator('#qt-shop-login').count(),0);checks++;}
   if(!['timeout','refresh-timeout'].includes(scenario)){
    await page.locator('.qgg-row').waitFor();checks++;
    assert.equal(await page.locator('#qt-shop-login').count(),0);checks++;
    if(scenario==='session'){
     const geometry=await page.evaluate(()=>{const card=document.querySelector('.qgg-row'),photo=card.querySelector('.qgg-image-pending'),input=card.querySelector('[data-field="selling_price"]'),r=photo.getBoundingClientRect(),p=input.getBoundingClientRect();return {photoWidth:r.width,photoHeight:r.height,priceWidth:p.width,priceHeight:p.height,font:getComputedStyle(input).fontSize,overflow:document.documentElement.scrollWidth>innerWidth+1}});
     assert(geometry.photoWidth>=112&&geometry.photoHeight>=144,'large mobile product photo');checks++;
     assert(geometry.priceWidth<=112&&geometry.priceHeight>=44,'compact price width with usable touch height');checks++;
     assert.equal(geometry.font,'16px');checks++;assert.equal(geometry.overflow,false);checks++;
     if(process.env.MERCHANT_LAYOUT_QA_DIR){fs.mkdirSync(process.env.MERCHANT_LAYOUT_QA_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.MERCHANT_LAYOUT_QA_DIR,'merchant-'+width+'.png')});}
     await page.locator('[data-field="selling_price"]').fill('8');await page.locator('[data-field="pos_available"]').check();await page.locator('[data-field="delivery_available"]').check();await page.locator('#qgg-save').click();await page.getByText(/บันทึกแล้ว/).waitFor();assert.equal(saved.selling_price,8);assert.equal(saved.pos_available,true);assert.equal(saved.delivery_available,true);assert.equal('promo_price' in saved,false);checks+=4;
     await page.waitForLoadState('networkidle');await page.reload({waitUntil:'domcontentloaded'});await page.locator('.qgg-row').waitFor();assert.equal(await page.locator('[data-field="selling_price"]').inputValue(),'8');checks++;
     await page.getByRole('button',{name:'สินค้า',exact:true}).click();await page.locator('#qgm-grocery-catalog-add').waitFor();
     await page.waitForFunction(()=>document.querySelectorAll('.qgm-product').length===60);assert.equal(await page.locator('.qgm-product').count(),60,'unpriced draft is not shown as a store product');checks++;
     assert.equal(await page.locator('.qgm-grocery-add-choices').evaluate(el=>getComputedStyle(el).position),'fixed');checks++;const dockBefore=await page.locator('.qgm-grocery-add-choices').boundingBox();await page.evaluate(()=>window.scrollTo(0,document.body.scrollHeight));
     const dockAfter=await page.locator('.qgm-grocery-add-choices').boundingBox();assert(Math.abs(dockBefore.y-dockAfter.y)<1,'both add buttons remain fixed while products scroll');checks++;const lastProduct=await page.locator('.qgm-product').last().boundingBox();assert(lastProduct.y+lastProduct.height<=dockAfter.y+1,'fixed dock does not cover the final product');checks++;
     assert.equal(await page.locator('#qgm-grocery-catalog-add').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(229, 35, 44)');checks++;
     await page.evaluate(()=>window.scrollTo(0,0));await page.getByRole('button',{name:'ปิดขาย',exact:true}).click();assert.equal(await page.locator('.qgm-product:visible').count(),0,'off filter excludes products currently available');checks++;
     await page.getByRole('button',{name:'เปิดขาย',exact:true}).click();assert.equal(await page.locator('.qgm-product:visible').count(),60,'on filter shows all currently available products');checks++;
     await page.locator('#qgm-grocery-catalog-add').click();await page.locator('.qgg-row').waitFor();checks++;
     await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));assert(await page.locator('#app').innerText());checks++;
     await page.waitForLoadState('networkidle');await page.close();const reopened=await context.newPage();await reopened.goto(origin+'/merchant/#shop-grocery-catalog');await reopened.locator('.qgg-row').waitFor();assert.equal(await reopened.locator('#qt-shop-login').count(),0);checks++;
    }
    if(['expired','expired-old-safari'].includes(scenario)){assert(refreshes>0);checks++}
    if(scenario==='rejected-renderer'){
     await page.evaluate(()=>{window.qtShopRoute=async()=>{throw Error('isolated async renderer failure')};router()});await page.getByRole('alert').filter({hasText:'isolated async renderer failure'}).waitFor();assert(await page.locator('.qgm-shell').count());checks++;
     assert(errors.every(e=>!e.includes('isolated async renderer failure')),'router rejection must be handled');
    }
   }
  }
  assert.deepEqual(errors,[],scenario+' uncaught errors');checks++;
  await context.close();
 }
 console.log(JSON.stringify({checks,failures:0,engine:'WebKit',scope:'whole Merchant mobile HTML, session/refresh, slow/missing assets, catalog editing/reload and recovery; localhost isolated transport, not live Supabase or physical iPhone'}));
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exit(1)});
