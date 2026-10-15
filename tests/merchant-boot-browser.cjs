'use strict';
// Real WebKit executes the complete shipped HTML, not an extracted/stubbed renderer.
// All data/session/transport fixtures below are confined to localhost QA.
const fs=require('fs'),http=require('http'),path=require('path'),assert=require('node:assert/strict');
const {webkit}=require(process.env.MERCHANT_PLAYWRIGHT_MODULE||'playwright-core');
const root=path.resolve(__dirname,'..'),facts=JSON.parse(fs.readFileSync(root+'/catalog/thai-grocery-import.json'));
const owner='00000000-0000-4000-8000-000000000001',shop='00000000-0000-4000-8000-000000000002';
(async()=>{
 const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)){res.writeHead(403).end();return}let target=file.endsWith(path.sep)?file+'index.html':file;try{res.setHeader('Content-Type',target.endsWith('.js')?'text/javascript':target.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(target))}catch(e){res.writeHead(404).end()}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await webkit.launch({headless:true,...(process.env.MERCHANT_WEBKIT_PATH?{executablePath:process.env.MERCHANT_WEBKIT_PATH}:{})});let checks=0;
 try{
 for(const width of [390,430])for(const scenario of ['fresh','session','expired','refresh-timeout','timeout','slow-catalog','missing-pos','old-safari','rejected-renderer']){
  const context=await browser.newContext({viewport:{width,height:width===390?844:932},isMobile:true,hasTouch:true});const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  let saved=null,refreshes=0;
  await context.route('https://**/*',async route=>{
   const url=route.request().url();
   if(!url.includes('.supabase.co/'))return route.abort();
   if(scenario==='timeout'||scenario==='refresh-timeout')return; // Browser must retain a visible shell while requests stall.
   let data=[];
   if(url.includes('/auth/v1/token')){refreshes++;data={access_token:'isolated-refreshed-token',refresh_token:'isolated-refresh',expires_at:Math.floor(Date.now()/1000)+3600}}
   else if(url.includes('/users?'))data=[{id:owner,auth_user_id:owner,role:'shop',status:'active',name:'Isolated QA owner',created_at:new Date().toISOString()}];
   else if(url.includes('/shop_profiles?'))data=[{id:shop,user_id:owner,public_category:'grocery',shop_name:'Isolated QA shop',onboarding_status:'approved'}];
   else if(url.includes('rpc/qg_grocery_categories'))data=[{category:facts[0].category,n:1}];
   else if(url.includes('rpc/qg_grocery_merchant_page'))data=[{...facts[0],id:'00000000-0000-4000-8000-000000000003',selling_price:saved?.selling_price??null,stock_qty:0,stock_tracking:false,enabled:saved?.enabled??false}];
   else if(url.includes('rpc/qg_grocery_store_save')){const body=route.request().postDataJSON();saved=body.p_rows[0];data={saved:body.p_rows.length}}
   await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
  });
  if(scenario==='missing-pos')await context.route('**/pos-table-v2.js?*',r=>r.abort());
  if(scenario==='slow-catalog')await context.route('**/grocery-catalog.js?*',async r=>{await new Promise(resolve=>setTimeout(resolve,1000));await r.continue()});
  await context.addInitScript(({owner,scenario})=>{
   if(scenario==='old-safari')Object.defineProperty(AbortSignal,'timeout',{value:undefined});
   if(scenario!=='fresh')localStorage.setItem('qg_merchant_session_v1',JSON.stringify({role:'shop',userId:owner,authUserId:owner,accessToken:'isolated-token',refreshToken:'isolated-refresh',expiresAt:['expired','refresh-timeout'].includes(scenario)?Date.now()-1000:Date.now()+3600000}));
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
     await page.locator('[data-field="selling_price"]').fill('8');await page.locator('[data-field="enabled"]').check();await page.locator('#qgg-save').click();await page.getByText(/บันทึกแล้ว/).waitFor();assert.equal(saved.selling_price,8);checks++;
     await page.reload({waitUntil:'domcontentloaded'});await page.locator('.qgg-row').waitFor();assert.equal(await page.locator('[data-field="selling_price"]').inputValue(),'8');checks++;
     await page.getByRole('button',{name:'สินค้า',exact:true}).click();await page.locator('#qgm-grocery-catalog-add').waitFor();await page.locator('#qgm-grocery-catalog-add').click();await page.locator('.qgg-row').waitFor();checks++;
     await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));assert(await page.locator('#app').innerText());checks++;
     await page.close();const reopened=await context.newPage();await reopened.goto(origin+'/merchant/#shop-grocery-catalog');await reopened.locator('.qgg-row').waitFor();assert.equal(await reopened.locator('#qt-shop-login').count(),0);checks++;
    }
    if(scenario==='expired'){assert(refreshes>0);checks++}
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
