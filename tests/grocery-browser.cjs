'use strict';
// Browser component QA with sourced identities and isolated transport fixtures.
// Never signs into Production or certifies physical-device / live checkout behavior.
const fs=require('fs'),http=require('http'),assert=require('assert'),path=require('path'),{chromium}=require('playwright-core');
const root=path.resolve(__dirname,'..'),facts=JSON.parse(fs.readFileSync(path.join(root,'catalog/thai-grocery-import.json')));
const css=file=>[...fs.readFileSync(path.join(root,file),'utf8').matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map(m=>m[1]).join('\n');
(async()=>{
 if(!process.env.GROCERY_CHROMIUM_PATH)throw Error('Set GROCERY_CHROMIUM_PATH to a local Chromium executable');
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end('<meta name="viewport" content="width=device-width,initial-scale=1"><div id="root"><main id="app"></main></div>');});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const b=await chromium.launch({executablePath:process.env.GROCERY_CHROMIUM_PATH,headless:true,args:['--no-sandbox','--disable-gpu']});
 const dir=process.env.GROCERY_QA_DIR||path.join(root,'test-results/grocery-browser');fs.mkdirSync(dir,{recursive:true});let checks=0;
 try{for(const width of [320,390,768]){for(const role of ['merchant','admin','customer']){
  const page=await b.newPage({viewport:{width,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:'+server.address().port+'/#shop-products');
  await page.addStyleTag({content:css(role==='merchant'?'merchant/index.html':role==='admin'?'admin/index.html':'index.html')+'\n'+fs.readFileSync(path.join(root,'catalog/grocery.css'),'utf8')});
  if(process.env.GROCERY_QA_FONT){
   const data=fs.readFileSync(process.env.GROCERY_QA_FONT).toString('base64');
   await page.evaluate(async data=>{const f=new FontFace('Noto Sans Thai QA','url(data:font/ttf;base64,'+data+')');await f.load();document.fonts.add(f);},data);
   await page.addStyleTag({content:'body,input,button,select{font-family:"Noto Sans Thai QA",sans-serif!important}'});
  }
  await page.evaluate(({facts,role})=>{
   const rows=facts.slice(0,51).map((r,i)=>({...r,id:'00000000-0000-4000-8000-'+String(i+1).padStart(12,'0'),selling_price:null,promo_price:null,stock_qty:0,stock_tracking:false,enabled:false,stores:0,sources:[{source_url:r.source_url,source_type:r.source_type,retrieved_at:r.retrieved_at}]}));
   window.qaCalls=[];window.currentUser=()=>({id:'isolated-admin',type:'admin'});window.shopUser=()=>({id:'isolated-owner',category:'grocery'});window.profileId=()=> 'isolated-shop';window.navigate=()=>{};window.qtGetAccessToken=async()=> 'isolated-transport-fixture';
   window.layout=(a,b)=>document.getElementById('root').innerHTML='<main id="app">'+(b?.includes('<section')?b:a)+'</main>';
   window.qtSupabaseTable=async(name,o)=>{qaCalls.push({name,...o});if(name.endsWith('categories'))return[{category:rows[0].category,n:51}];if(/(?:merchant|admin)_page/.test(name))return rows;if(name.endsWith('store_save')){for(const saved of o.body.p_rows){const r=rows.find(r=>r.id===saved.master_product_id);Object.assign(r,{selling_price:saved.selling_price,enabled:saved.enabled,stock_qty:saved.stock_qty,stock_tracking:saved.stock_tracking});}return{saved:o.body.p_rows.length}}return{saved:1}};
   window.routeVersion=1;window.cart={};window.cartN=()=>0;window.cartTotal=()=>0;window.go=()=>{};window.esc=v=>String(v??'').replace(/[<>&"]/g,'');window.ico=()=>'';window.baht=n=>String(n);window.ini=n=>esc(String(n||'?').trim().charAt(0));window.photo=(src,name)=>`<div class="ph">${src?`<img src="${esc(src)}" alt="" loading="lazy" onerror="this.remove()">`:''}${src?'':ini(name)}</div>`;window.$=id=>document.getElementById(id);window.QGCustomer={loadFavorites:async()=>[],reviews:async()=>[],favoriteButton:()=>'',reviewList:()=>''};
   window.db=async(name,o)=>{qaCalls.push({name,...o});if(name.endsWith('categories'))return[{category:rows[0].category}];return rows.map(r=>({...r,name:r.brand+' '+r.product_name_th+' '+r.size_value+' '+r.size_unit+(r.pack_size>1?' × '+r.pack_size:''),price:8,delivery_price:8.8,available:true,delivery_available:true}));};
  },{facts,role});
  const file=role==='merchant'?'merchant/grocery-catalog.js':role==='admin'?'admin/grocery-catalog.js':'catalog/customer-grocery.js';
  await page.addScriptTag({content:fs.readFileSync(path.join(root,file),'utf8')});
  await page.evaluate(async role=>{if(role==='merchant')await qgRenderGroceryCatalog();else if(role==='admin')await qgRenderAdminGrocery();else await qgRenderCustomerGroceryShop({id:'isolated-shop',shop_name:'การตรวจหน้าจอแยกจาก Production',open:true},1)},role);
  assert.equal(await page.locator(role==='customer'?'.mi':'.qgg-row').count(),50);checks++;
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),role+' overflows at '+width);checks++;
  if(role==='merchant'){
   await page.locator('[data-field="selling_price"]').first().fill('8');await page.locator('[data-field="enabled"]').first().check();await page.locator('#qgg-save').click();
   await page.waitForFunction(()=>qaCalls.some(c=>c.name.endsWith('store_save')));
   await page.waitForFunction(()=>document.getElementById('qgg-status').textContent.startsWith('บันทึกแล้ว'));checks++;
   assert.equal(await page.evaluate(()=>qaCalls.find(c=>c.name.endsWith('store_save')).body.p_rows[0].selling_price),8);checks++;
   assert.equal(await page.locator('.qgg-image-pending').count(),50);checks++;
  }else if(role==='admin'){
   await page.locator('[data-edit]').first().click();assert.equal(await page.locator('form input[name="brand"]').inputValue(),facts[0].brand);checks++;
   await page.locator('[data-target]').first().waitFor();await page.locator('[data-target]').first().click();assert.equal(await page.locator('[data-merge]').isDisabled(),false);checks++;
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));checks++;
  }else{await page.locator('#qggc-more').click();await page.waitForFunction(()=>_menu.length===100);assert.equal(await page.locator('.mi').count(),100);checks++;}
  assert.deepEqual(errors,[]);checks++;
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:path.join(dir,role+'-'+width+'.png'),fullPage:false});await page.close();
 }}
 const result={checks,failures:0,browser:await b.version(),widths:[320,390,768],scope:'Rendered isolated components with real catalog names and transport fixtures; not authenticated Production, physical-device or live order E2E'};
 fs.writeFileSync(path.join(dir,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }finally{await b.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exit(1)});
