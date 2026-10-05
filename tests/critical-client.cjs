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
const r=boot('rider/index.html');await Promise.resolve();r.run("readSession=()=>S.session;qgRiderMutation=(path,opts)=>sbTable(path,opts);S.user={id:'fixture'};S.riderProfile={id:'rider'};S.session={accessToken:'fixture'};S.activeTab='home';S.online=false;window.calls=[];getAccessToken=async()=>'fixture';sbTable=async(path,opts)=>{calls.push({path,opts});return path.includes('status=in.')?[{id:'order',status:'rider_assigned',pickup_latitude:13,pickup_longitude:100,delivery_latitude:13.1,delivery_longitude:100.1}]:[]};placeJobMarkers=()=>{};");await r.run('refreshData()');assert.equal(r.run('S.activeOrder.status'),'rider_assigned');assert(r.run("calls[0].path.includes('rider_assigned,preparing,ready')"));assert(r.run("jobCardHTML(S.activeOrder).includes('นำทางไปร้าน')"));assert(r.run("jobCardHTML({...S.activeOrder,status:'ready',subtotal:100}).includes('เงินสดที่ต้องจ่ายร้าน')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'ready',subtotal:100}).includes('฿100')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'delivering',id:'slide',total_amount:130}).includes('ส่งสำเร็จ')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'delivering',id:'slide',total_amount:130}).includes('type=\"range\"')===false"));checks+=5;
r.run("calls=[];sbTable=async(path,opts)=>{calls.push({path,opts});return path.startsWith('orders?select=subtotal')?[{status:'ready',subtotal:100}]:[]};refreshData=async()=>{};");await r.run("advanceOrder({id:'order',status:'ready',subtotal:100})");assert.equal(r.run("calls.find(c=>c.path==='rpc/rider_order_action').opts.body.p_action"),'pickup_cash');assert.equal(r.run("calls.filter(c=>c.path.startsWith('orders?select=subtotal')).length"),0);checks+=2;
r.run("calls=[];sbTable=async(path,opts)=>{calls.push({path,opts});return []};refreshData=async()=>{};");await r.run("advanceOrder({id:'order',status:'delivering',total_amount:130})");assert.equal(r.run("calls.find(c=>c.path==='rpc/rider_order_action').opts.body.p_action"),'complete');checks++;

// One map card: stage actions, contact tools and displayed cash remain distinct.
assert(r.run("jobCardHTML({...S.activeOrder,status:'ready',subtotal:150,total_amount:180}).includes('รับสินค้าแล้ว')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'picked_up',subtotal:150,total_amount:180}).includes('เริ่มจัดส่ง')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'in_progress',subtotal:150,total_amount:180}).includes('เก็บเงินลูกค้า')"));
assert(r.run("jobCardHTML({...S.activeOrder,status:'in_progress',_customer:{phone:'0812345678'}}).includes('tel:0812345678')"));
assert(r.run("jobCardHTML(S.activeOrder).includes('แจ้งปัญหา')"));checks+=5;

// After pickup, both ordinary and market routes target the customer's real location.
for(const status of ['picked_up','in_progress']){
 const html=r.run(`jobCardHTML({...S.activeOrder,status:'${status}',delivery_latitude:13.9,delivery_longitude:100.8,market_order_id:'market',_marketPickups:[{shop_id:'shop',status:'READY',latitude:12,longitude:99}]})`);
 assert(html.includes('นำทางไปบ้านลูกค้า'));assert(html.includes('destination=13.9%2C100.8'));assert(!html.includes('destination=12%2C99'));checks+=3;
}
assert(r.run("mapsLink(null,null,'บ้านลูกค้า').includes(encodeURIComponent('บ้านลูกค้า'))"));assert(r.run("!mapsLink(null,null,'บ้านลูกค้า').includes('destination=0%2C0')"));checks+=2;

// Navigation exposes arrival; the arrival RPC keeps the order state intact.
r.run("readSession=()=>S.session;S.activeOrder.shop_id='shop';qgSetPickupFlag(S.activeOrder,'navigation');");
assert(r.run("jobCardHTML(S.activeOrder).includes('id=\"arrive-shop-order\"')"));checks++;
r.run("calls=[];sbTable=async(path,opts)=>{calls.push({path,opts});return 'rider_assigned'};refreshData=async()=>{};");
await r.run('qgRiderArriveShop(S.activeOrder)');
assert.equal(r.run("calls[0].opts.body.p_action"),'arrive_shop');assert.equal(r.run('S.activeOrder.status'),'rider_assigned');checks+=2;
r.run("S.activeOrder.rider_arrived_shop_at=new Date().toISOString();");assert(r.run("!jobCardHTML(S.activeOrder).includes('id=\"arrive-shop-order\"')"));checks++;

// Completion is the only user action; no cash or PIN confirmation, including market groups.
r.run("confirm=()=>{throw Error('unexpected cash confirmation')};prompt=()=>{throw Error('unexpected PIN')};calls=[];sbTable=async(path,opts)=>{calls.push({path,opts});return []};refreshData=async()=>{};");
await r.run("finishOrder({id:'finish',status:'in_progress'})");assert.equal(r.run("calls[0].path"),'rpc/rider_order_action');assert.equal(r.run("calls[0].opts.body.p_action"),'complete');checks+=2;
r.run("calls=[]");await r.run("finishOrder({id:'market-finish',market_order_id:'group',status:'in_progress'})");assert.equal(r.run("calls[0].path"),'rpc/market_rider_group_action');assert.equal(r.run("calls[0].opts.body.p_action"),'complete');checks+=2;

// If the order committed but the network reply disappeared, reconcile its exact request UUID.
t.run(`window.reconcileDb=async(path)=>path.startsWith('orders?select=id,order_number')?[{id:readPendingCheckout().body.p_order_id,order_number:'QT-0002'}]:[];db=reconcileDb`);
t.run(setup+'failNext=true;');await t.run('placeOrder()');const recovered=t.run('readPendingCheckout().body.p_order_id');
t.run(`db=async(path)=>path.startsWith('orders?select=id,order_number')?[{id:'${recovered}',order_number:'QT-0002'}]:[]`);
await t.run('reconcilePendingCheckout()');assert.equal(t.run('readPendingCheckout()'),null);assert.equal(t.run('cart.items.length'),0);assert.equal(t.run('location.hash').split('/').pop(),recovered);checks+=3;
// Keep items added after the timeout; only clear the cart snapshot that was submitted.
t.run(setup+'failNext=true;');await t.run('placeOrder()');const editedId=t.run('readPendingCheckout().body.p_order_id');
t.run(`cart.items[0].qty=2;saveCart();db=async(path)=>path.startsWith('orders?select=id,order_number')?[{id:'${editedId}',order_number:'QT-0003'}]:[]`);
await t.run('reconcilePendingCheckout()');assert.equal(t.run('readPendingCheckout()'),null);assert.equal(t.run('cart.items[0].qty'),2);checks+=2;

const a=boot('admin/index.html');await Promise.resolve();a.run("window.calls=[];qtGetAccessToken=async()=>'fixture';qtSessionRead=()=>({role:'admin',authUserId:'admin'});qtSupabaseTable=async(path)=>{calls.push(path);return path==='users?select=*'?[{id:'shop',role:'shop',status:'active',metadata:{shopName:'Current shop'}}]:[]};");await a.run('qtHydrateDatabase({light:false})');assert.equal(a.run("calls.filter(p=>p.startsWith('users?')).length"),1);assert.equal(a.run("QT_DB_CACHE.qt_users.find(u=>u.id==='shop').shopName"),'Current shop');checks+=2;
const m=boot('merchant/index.html');await Promise.resolve();assert.equal(m.run("displayOrderNumber({order_number:'QT-20261005-0001'})"),'QT-0001');checks++;
assert.equal(m.run("displayOrderNumber({order_number:'QT-0001'})"),'QT-0001');checks++;
assert.equal(m.run("displayOrderNumber({id:'abc-def-1234'})"),'QT-1234');checks++;
m.run("currentUser=()=>({id:'shop',type:'shop'});window.alertEvents=0;window.addEventListener('qt:shop-new-order-sound',()=>alertEvents++);qgCheckMerchantOrders([]);qgCheckMerchantOrders([{id:'new-order'}]);qgCheckMerchantOrders([{id:'new-order'}]);");assert.equal(m.run('alertEvents'),1);checks++;
m.run("window.notificationSounds=0;window.qgPlayMerchantNotificationSound=()=>notificationSounds++;qtHandleNewNotification({id:'arrival',userId:'shop',title:'ไรเดอร์ถึงร้านแล้ว',message:'รับออเดอร์',read:false});");assert.equal(m.run('notificationSounds'),1);assert.equal(m.run("document.querySelectorAll('#notif-toast-box .notif-toast').length"),1);checks+=2;
m.run("qtGetAccessToken=async()=>'fixture';window.notificationWrites=[];qtSupabaseTable=async(path,opts)=>{notificationWrites.push({path,opts});return []};");await m.run("markAllNotifRead('shop')");assert.equal(m.run('notificationWrites[0].opts.method'),'PATCH');checks++;
a.dom.window.close();m.dom.window.close();
console.log(JSON.stringify({checks,failures:0,scope:'isolated client contracts; no production orders or concurrency test'},null,2));t.dom.window.close();r.dom.window.close();})().catch(e=>{console.error(e);process.exit(1)});
