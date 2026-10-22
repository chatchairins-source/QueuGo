const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom'),{indexedDB}=require('fake-indexeddb');
const root=path.resolve(__dirname,'..'),tick=()=>new Promise(r=>setImmediate(r));let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++},ok=(v,m)=>{assert.equal(v,true,m);checks++};
(async()=>{
  const d=new JSDOM('<div id="app"></div>',{url:'https://queuego.test/merchant/#pos',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;
  Object.defineProperty(w,'indexedDB',{value:indexedDB});Object.defineProperty(w.navigator,'onLine',{value:true,configurable:true});w.crypto.randomUUID=()=>`10000000-0000-4000-8000-${String(Math.floor(Math.random()*1e12)).padStart(12,'0')}`;w.toast=()=>{};w.confirm=()=>true;w.qtSessionRead=()=>({authUserId:'actor-a',accessToken:'token'});w.qtGetAccessToken=async()=>'token';w.QT_SUPABASE_CONFIG={URL:'https://test.invalid',KEY:'fixture'};w.AbortSignal=AbortSignal;
  const storeSource=fs.readFileSync(path.join(root,'merchant/pos-offline-drafts.js'),'utf8'),posSource=fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8');vm.runInContext(storeSource,d.getInternalVMContext());
  const writes=[],reads=[],submissions=[];let failOnline=false,price=35,submitCommitted=false;
  const product={id:'product-a',name:'ชาไทย',price,pos_price:price,available:true,pos_available:true,variants:[],stock:0};
  w.qtSupabaseTable=async(path,opts)=>{
    if(failOnline)throw Error('network unavailable');reads.push(path);
    if(path==='rpc/pos_my_shop')return 'shop-a';if(path==='rpc/pos_is_owner')return true;if(path.startsWith('shop_profiles?'))return[{shop_name:'ร้านทดสอบ'}];
    if(path.startsWith('products?'))return[{...product,pos_price:price}];if(path.startsWith('pos_tables?'))return[];if(path.startsWith('pos_staff?'))return[];
    if(path.startsWith('orders?'))return[];if(path.startsWith('pos_register_cash_events?'))return[];
    if(path==='rpc/pos_submit_offline_draft'){submissions.push(opts.body);if(!submitCommitted){submitCommitted=true;throw Error('network timeout after server accepted')}return {order_id:'order-offline',replayed:true}}
    if(path.startsWith('rpc/')){writes.push(path);return null}return[];
  };
  w.supabase={createClient:()=>({realtime:{setAuth:async()=>{}},removeAllChannels:async()=>{},removeChannel:async()=>{},channel:()=>({on(){return this},subscribe(){return this}})})};
  vm.runInContext(posSource,d.getInternalVMContext());w.qgPosRoute();for(let i=0;i<12;i++)await tick();
  ok(w.document.body.textContent.includes('เริ่มร่างตะกร้า'),'online POS exposes explicit draft action');
  w.qgPosNoTable();await w.qgPosDraftStart();ok(w.document.body.textContent.includes('ร่างตะกร้า · ยังไม่ส่ง'),'new draft is explicitly not submitted');
  await w.qgPosAdd('product-a');eq(writes.some(x=>/pos_edit_bill|pos_create_bill_once|pos_add_product_with_options|pos_take_payment|pos_bill_action/.test(x)),false,'draft item must never call order or payment RPCs');
  let scope={shopId:'shop-a',actorId:'actor-a',deviceId:w.QGPosOfflineDraftStore.deviceId()},drafts=await w.QGPosOfflineDraftStore.list(scope);eq(drafts.length,1);eq(drafts[0].items[0].quantity,1);eq(drafts[0].items[0].priceSnapshot,35);ok(w.document.body.textContent.includes('ยังไม่ใช่ยอดขาย'),'display distinguishes a price snapshot from sales');
  Object.defineProperty(w.navigator,'onLine',{value:false,configurable:true});failOnline=true;await w.qgPosAdd('product-a');drafts=await w.QGPosOfflineDraftStore.list(scope);eq(drafts[0].items[0].quantity,2,'offline tap changes only the local draft');eq(writes.some(x=>/pos_edit_bill|pos_create_bill_once|pos_add_product_with_options|pos_take_payment|pos_bill_action|pos_delivery_kitchen_action|pos_record_inventory/.test(x)),false,'offline cart never writes order, payment, kitchen, or inventory data');
  await w.qgPosReload();ok(w.document.body.textContent.includes('ออฟไลน์'),'cached POS view restores after a network failure');
  w.qtSessionRead=()=>({authUserId:'actor-b',accessToken:'token'});const foreign=await w.QGPosOfflineDraftStore.list({shopId:'shop-a',actorId:'actor-b',deviceId:scope.deviceId});eq(foreign.length,0,'another signed-in actor cannot read this draft');
  w.qtSessionRead=()=>({authUserId:'actor-a',accessToken:'token'});w.qgPosDraftResume(drafts[0].id);ok(w.document.body.textContent.includes('ร่างตะกร้า · ยังไม่ส่ง'),'draft can be reopened after recovery');
  failOnline=false;Object.defineProperty(w.navigator,'onLine',{value:true,configurable:true});price=40;await w.qgPosReload();ok(w.document.body.textContent.includes('ชาไทย ราคาเปลี่ยน'),'changed price is surfaced for cashier review after reconnection');
  ok(!writes.some(x=>/pos_create_bill_once|pos_add_product_with_options|pos_take_payment/.test(x)),'reconnection does not auto-submit the draft');
  await w.qgPosDraftSubmit(drafts[0].id);eq(submissions.length,1,'cashier review explicitly submits the draft');
  await w.qgPosDraftSubmit(drafts[0].id);eq(submissions.length,2,'retry reuses the submitted draft after an ambiguous response');eq(JSON.stringify(submissions[0]),JSON.stringify(submissions[1]),'retry keeps identical request ids and payload');
  const submittedDrafts=await w.QGPosOfflineDraftStore.list(scope);eq(submittedDrafts.length,0,'confirmed server receipt clears the local draft');eq(submissions[0].p_items[0].request_ids.length,2,'each unit has a stable idempotency key');
  d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'POS local cart draft reload, actor scope, price review, no order/payment/kitchen/inventory writes; atomic online submission, timeout replay with identical request ids and no offline writes'}));
})().catch(e=>{console.error(e);process.exit(1)});
