'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom');
(async()=>{
 let checks=0;
 const d=new JSDOM(fs.readFileSync(path.join(__dirname,'../index.html'),'utf8'),{url:'https://queuego.test/#login',runScripts:'outside-only'}),w=d.window,ctx=d.getInternalVMContext();
 w.fetch=async()=>({ok:true,json:async()=>[]});w.AbortSignal=AbortSignal;w.setTimeout=()=>0;
 for(const script of w.document.querySelectorAll('script')){
  let code=script.textContent;
  if(script.src){const u=new URL(script.src);if(u.hostname!=='queuego.test')continue;code=fs.readFileSync(path.join(__dirname,'..',u.pathname.slice(1)),'utf8')}
  if(code)vm.runInContext(code,ctx);
 }
 const run=s=>vm.runInContext(s,ctx),eq=(a,b)=>{assert.deepStrictEqual(a,b);checks++};
 run(`route=()=>{};S.set({userId:'customer',authUserId:'auth-customer',accessToken:'test-only'});
  currentPos={lat:13,lng:100,address:'House detail'};initLocationPicker=()=>({map:{resize(){}},destroy(){}});
  window.shopFixture={id:'shop',status:'active',delivery_enabled:true,market_id:'market',market_membership_status:'approved',latitude:13,longitude:100};
  window.menuFixture=[{id:'product',name:'Item',price:100,available:true,delivery_available:true,grocery_stock_tracking:true,stock:3}];
  window.openFixture=[];window.marketFixture=[{active:true}];window.transportError=false;
  db=async p=>{if(transportError&&p.startsWith('shop_open_states?'))throw Error('network unavailable');if(p.startsWith('shop_profiles?'))return [shopFixture];if(p.startsWith('products?'))return menuFixture;if(p.startsWith('shop_open_states?'))return openFixture;if(p.startsWith('markets?'))return marketFixture;throw Error('unexpected endpoint')};
 `);
 const render=()=>{run("cart={shopId:'shop',items:[{id:'product',name:'Item',price:100,qty:2}]}");return run('V.checkout()')};
 await render();eq(w.document.getElementById('place-order-btn').disabled,false);
 for(const [mutate,restore,text] of [
  ["shopFixture.market_id=null","shopFixture.market_id='market'",'ยังไม่ได้เลือกตลาด'],
  ["shopFixture.market_membership_status='pending'","shopFixture.market_membership_status='approved'",'ยังไม่ได้เลือกตลาด'],
  ["marketFixture=[]","marketFixture=[{active:true}]",'ตลาดยังไม่เปิด'],
  ["shopFixture.delivery_enabled=false","shopFixture.delivery_enabled=true",'ยังไม่พร้อม'],
  ["openFixture=[{is_open:false,resume_at:null}]","openFixture=[]",'ร้านปิดอยู่'],
  ["menuFixture[0].stock=1","menuFixture[0].stock=3",'รายการที่ยังสั่งไม่ได้'],
  ["menuFixture[0].available=false","menuFixture[0].available=true",'รายการที่ยังสั่งไม่ได้']
 ]){
  run(mutate);await render();eq(w.document.getElementById('place-order-btn').disabled,true);eq(run('window.__checkoutQuote()'),null);eq(w.document.body.textContent.includes(text),true);eq(run('cart.items.length'),1);run(restore);
 }
 run("openFixture=[{is_open:false,resume_at:'2000-01-01T00:00:00Z'}]");await render();eq(w.document.getElementById('place-order-btn').disabled,false);
 run("transportError=true");await assert.rejects(render(),/network unavailable/);checks++;eq(run('cart.items.length'),1);
 d.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'actual Web checkout DOM: blocked eligibility, stock, missing market, expired closure, retained cart and network failure; isolated transport only'}));
})().catch(e=>{console.error(e);process.exit(1)});
