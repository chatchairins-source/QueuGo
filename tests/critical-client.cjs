const path=require('node:path');
const fs=require('fs'),vm=require('vm'),{JSDOM}=require('jsdom'),assert=require('assert');
const root=path.resolve(__dirname,'..')+'/';
function boot(file){const dom=new JSDOM(fs.readFileSync(root+file,'utf8'),{url:'https://queuego.test/'+file,runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,c=dom.getInternalVMContext();w.fetch=async()=>({ok:true,json:async()=>[]});w.setInterval=()=>0;w.setTimeout=()=>0;w.requestAnimationFrame=()=>0;w.alert=()=>{};w.confirm=()=>true;w.prompt=()=>null;w.AbortSignal=AbortSignal;w.crypto.randomUUID=()=>crypto.randomUUID();for(const script of w.document.querySelectorAll('script')){let code=script.textContent;if(script.src){const u=new URL(script.src);if(u.hostname!=='queuego.test')continue;code=fs.readFileSync(root+u.pathname.slice(1),'utf8')}if(code.trim())vm.runInContext(code,c)}return {dom,w,c,run:code=>vm.runInContext(code,c)}}
(async()=>{let checks=0;const t=boot('index.html');await Promise.resolve();const setup=`layout=()=>{};S.set({userId:'customer',accessToken:'fixture'});currentPos={lat:13,lng:100,address:'Test address'};cart={shopId:'20000000-0000-4000-8000-000000000001',items:[{id:'product',name:'Test',price:100,qty:1}]};saveCart();window.__checkoutQuote=()=>({shopId:cart.shopId,location:currentPos,subtotal:cartTotal(),deliveryFee:30});document.getElementById('root').innerHTML='<textarea id="delivery-address">Test address</textarea><button id="place-order-btn"></button>';window.testCalls=[];window.testCommitted=new Map();window.failNext=false;db=async(path,opts={})=>{testCalls.push({path,opts});if(path.startsWith('orders?'))return [];if(path.startsWith('shop_profiles?'))return [{id:'20000000-0000-4000-8000-000000000001',latitude:13,longitude:100}];if(path==='rpc/queuego_place_cash_order'){testCommitted.set(opts.body.p_order_id,true);if(failNext){failNext=false;throw Error('connection lost after commit')}return {id:opts.body.p_order_id,order_number:'QT-20261005-0001'}}return []};`;
t.run(setup);await t.run('Promise.all([placeOrder(),placeOrder(),placeOrder()])');assert.equal(t.run("testCalls.filter(x=>x.path==='rpc/queuego_place_cash_order').length"),1);assert.equal(t.run('testCommitted.size'),1);assert.equal(t.run('cart.items.length'),0);checks+=3;
t.run(setup+'failNext=true;');await t.run('placeOrder()');assert.equal(t.run('cart.items.length'),1);assert(t.run('readPendingCheckout()'));assert.equal(t.run('checkoutBusy'),false);const first=t.run('readPendingCheckout().body.p_order_id');checks+=3;
// Simulate app reopening: globals reset, durable localStorage contents retained.
t.run('checkoutBusy=false;cart=readCart();');await t.run('placeOrder()');assert.equal(t.run('testCommitted.size'),1);assert.equal(t.run("testCalls.filter(x=>x.path==='rpc/queuego_place_cash_order')[1].opts.body.p_order_id"),first);assert.equal(t.run('readPendingCheckout()'),null);checks+=3;
t.run(setup+"db=async(path)=>{if(path.startsWith('orders?'))return [];if(path.startsWith('shop_profiles?'))return [{id:'20000000-0000-4000-8000-000000000001',latitude:13,longitude:100}];const e=Error('unavailable');e.status=400;throw e};");await t.run('placeOrder()');assert.equal(t.run('readPendingCheckout()'),null);assert.equal(t.run('cart.items.length'),1);assert.equal(t.run('checkoutBusy'),false);checks+=3;
// A mismatched response must retain the original request and cart for safe replay.
t.run(setup+"db=async(path)=>path.startsWith('orders?')?[]:{id:'30000000-0000-4000-8000-000000000003'};");await t.run('placeOrder()');assert.equal(t.run('cart.items.length'),1);assert(t.run('readPendingCheckout()'));assert.equal(t.run('checkoutBusy'),false);const retained=t.run('readPendingCheckout().body.p_order_id');checks+=3;
t.run("db=async(path,opts)=>({id:opts.body.p_order_id,order_number:'QT-20261005-0001'})");await t.run('placeOrder()');assert.equal(t.run('readPendingCheckout()'),null);assert.equal(t.run('cart.items.length'),0);assert.notEqual(retained,'30000000-0000-4000-8000-000000000003');checks+=3;
// Existing server fee boundaries, not a new pricing policy.
assert.equal(t.run('deliveryFeeFor({latitude:13,longitude:100},{lat:13,lng:100})'),30);checks++;
assert.equal(t.run("customerOrderNumber({order_number:'QT-20261005-0001'})"),'QT-0001');checks++;
assert.equal(t.run("customerOrderNumber({order_number:'QT-0001'})"),'QT-0001');checks++;
assert.equal(t.run("customerOrderNumber({id:'abc-def-1234'})"),'QT-1234');checks++;
const r=boot('rider/index.html');await Promise.resolve();
r.run("S.user={id:'fixture'};S.riderProfile={id:'rider'};S.session={accessToken:'fixture'};S.activeTab='home';S.online=false;window.calls=[];getAccessToken=async()=>'fixture';sbTable=async(path,opts)=>{calls.push({path,opts});return path.includes('status=in.')?[{id:'order',status:'rider_assigned',pickup_latitude:13,pickup_longitude:100,delivery_latitude:13.1,delivery_longitude:100.1}]:[]};placeJobMarkers=()=>{};");await r.run('refreshData()');
assert.equal(r.run('S.activeOrder.status'),'rider_assigned');
assert(r.run("calls[0].path.includes('rider_assigned,preparing,ready')"));
assert(r.run("jobCardHTML(S.activeOrder).includes('disabled')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'ready',subtotal:100}).includes('รับสินค้าแล้ว')"));
assert.equal(r.run("jobCardHTML({...S.activeOrder,status:'delivering',id:'route',total_amount:130}).includes('qg-grab-slide')"),false);
assert(r.run("jobCardHTML({...S.activeOrder,status:'delivering',id:'route',total_amount:130}).includes('ถึงแล้ว')"));checks+=6;

// One tap maps to one idempotent server receipt. A retry with the same semantic action reuses the same request UUID.
r.run("calls=[];sbTable=async(path,opts)=>{calls.push({path,opts});return []};refreshData=async()=>{};");
await r.run("advanceOrder({id:'order',status:'ready',subtotal:100})");
let riderActionCalls=r.run("calls.filter(c=>c.path==='rpc/qg_rider_action_once')");
assert.equal(riderActionCalls.length,1);
assert.equal(riderActionCalls[0].opts.body.p_kind,'order');
assert.equal(riderActionCalls[0].opts.body.p_payload.p_action,'pickup_cash');
assert.match(riderActionCalls[0].opts.body.p_request_id,/^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
const retryId=riderActionCalls[0].opts.body.p_request_id;
await r.run("advanceOrder({id:'order',status:'ready',subtotal:100})");
riderActionCalls=r.run("calls.filter(c=>c.path==='rpc/qg_rider_action_once')");
assert.equal(riderActionCalls.length,2);
assert.equal(riderActionCalls[1].opts.body.p_request_id,retryId);checks+=6;

r.run("calls=[];sbTable=async(path,opts)=>{calls.push({path,opts});return []};refreshData=async()=>{};");
await r.run("advanceOrder({id:'order',status:'delivering',total_amount:130})");
riderActionCalls=r.run("calls.filter(c=>c.path==='rpc/qg_rider_action_once')");
assert.equal(riderActionCalls[0].opts.body.p_kind,'order');
assert.equal(riderActionCalls[0].opts.body.p_payload.p_action,'arrive');
assert.equal(r.run("calls.some(c=>c.path==='rpc/rider_order_action')"),false);checks+=3;

// The new sheet has 3 real snap levels; it no longer depends on the retired expanded-boolean renderer.
r.run("document.getElementById('app').innerHTML='<section id=sheet class=qg-sheet></section>';S.activeOrder=null;S.laundryJob=null;S.online=true;S.openJobs=[{order:{id:'open-a',status:'searching_rider',delivery_fee:30,pickup_latitude:13,pickup_longitude:100,delivery_latitude:13.1,delivery_longitude:100.1}},{order:{id:'open-b',status:'searching_rider',delivery_fee:35,pickup_latitude:13,pickup_longitude:100,delivery_latitude:13.1,delivery_longitude:100.1}}];qgSetSheetLevel(1);mapsLink=()=>'#';riderEarning=o=>Number(o.delivery_fee||0);renderSheet()");
assert.equal(r.run("document.querySelectorAll('#sheet .qg-offer-card').length"),1);
r.run("document.querySelector('#sheet [data-qg-sheet-toggle]').click()");
assert.equal(r.run("document.querySelectorAll('#sheet .qg-offer-card').length"),2);
assert.equal(r.run("document.querySelector('#sheet').dataset.level"),'2');
assert(r.run("document.querySelector('#sheet').classList.contains('qg-expanded')"));checks+=4;

// Arrival is a separate server transition; completion UI requires proof + six-digit PIN and has no payment-confirmation detour.
r.run("document.getElementById('app').innerHTML='<section class=stage><section id=sheet class=qg-sheet></section></section>';S.activeOrder=null;stopPolling=()=>{};startPolling=()=>{};renderSheet=()=>{};getAccessToken=async()=>'fixture';sbTable=async(path,opts)=>path.startsWith('order_items?')?[{item_name:'Test',quantity:1,unit_price:100,total_price:100}]:[];");
await r.run("qgOpenOrderChecklist({id:'proof',order_number:'QT-0002',status:'in_progress',note:'__QT_ORDER_STATUS__=arrived\\nวางไว้หน้าประตู',subtotal:100,total_amount:130,delivery_address:'Test address',_shop:{shop_name:'ร้านทดสอบ'},_customer:{name:'ลูกค้า',phone:'0800000000'}})");
assert(r.run("document.getElementById('qg-ordercheck-page').textContent.includes('ยืนยันการจัดส่ง')"));
assert(r.run("document.getElementById('qg-ordercheck-page').textContent.includes('รหัสส่งมอบ 6 หลัก')"));
assert(r.run("document.getElementById('qg-ordercheck-page').textContent.includes('จัดส่งแล้ว')"));
assert.equal(r.run("document.getElementById('qg-ordercheck-page').textContent.includes('ยืนยันการรับเงิน')"),false);
assert.equal(r.run("document.getElementById('qg-ordercheck-page').textContent.includes('ยืนยันการจ่ายเงิน')"),false);checks+=5;

const a=boot('admin/index.html');await Promise.resolve();a.run("window.calls=[];qtGetAccessToken=async()=>'fixture';qtSessionRead=()=>({role:'admin',authUserId:'admin'});qtSupabaseTable=async(path)=>{calls.push(path);return path==='users?select=*'?[{id:'shop',role:'shop',status:'active',metadata:{shopName:'Current shop'}}]:[]};");await a.run('qtHydrateDatabase({light:false})');assert.equal(a.run("calls.filter(p=>p.startsWith('users?')).length"),1);assert.equal(a.run("QT_DB_CACHE.qt_users.find(u=>u.id==='shop').shopName"),'Current shop');checks+=2;
const m=boot('merchant/index.html');await Promise.resolve();assert.equal(m.run("displayOrderNumber({order_number:'QT-20261005-0001'})"),'QT-0001');checks++;
assert.equal(m.run("displayOrderNumber({order_number:'QT-0001'})"),'QT-0001');checks++;
assert.equal(m.run("displayOrderNumber({id:'abc-def-1234'})"),'QT-1234');checks++;
m.run("currentUser=()=>({id:'shop',type:'shop'});window.alertEvents=0;window.addEventListener('qt:shop-new-order-sound',()=>alertEvents++);qgCheckMerchantOrders([]);qgCheckMerchantOrders([{id:'new-order'}]);qgCheckMerchantOrders([{id:'new-order'}]);");assert.equal(m.run('alertEvents'),1);checks++;
a.dom.window.close();m.dom.window.close();
console.log(JSON.stringify({checks,failures:0,scope:'isolated client contracts; no production orders or concurrency test'},null,2));t.dom.window.close();r.dom.window.close();})().catch(e=>{console.error(e);process.exit(1)});
