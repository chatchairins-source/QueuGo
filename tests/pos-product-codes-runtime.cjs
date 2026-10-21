const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const {JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'..'),tick=()=>new Promise(r=>setImmediate(r));
(async()=>{
  const dom=new JSDOM('<div id="app"></div>',{url:'https://queuego.test/merchant/#pos',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window;
  const product={id:'product-1',name:'ชาไทย',price:35,pos_price:30,available:true,pos_available:true,variants:[]};
  const optionProduct={id:'product-2',name:'ข้าวไข่เจียว',price:35,pos_price:35,available:true,pos_available:true,variants:[{schema:'queuego.menu-options.v1',key:'portion',name:'ขนาด',type:'single',required:true,options:[{key:'normal',name:'ธรรมด',price_delta:0},{key:'special',name:'พิเศษ',price_delta:20}]},{schema:'queuego.menu-options.v1',key:'toppings',name:'ท็อปปิ้ง',type:'multi',required:true,min:1,max:1,options:[{key:'egg',name:'ไข่ดาว',price_delta:5},{key:'meat',name:'เพิ่มเนื้อ',price_delta:10}]}]};
  const optionalProduct={id:'product-3',name:'น้ำจิ้ม',price:10,pos_price:10,available:true,pos_available:true,variants:[{schema:'queuego.menu-options.v1',key:'sauce',name:'น้ำจิ้ม',type:'single',required:false,options:[{key:'sweet',name:'หวาน',price_delta:0},{key:'spicy',name:'เผ็ด',price_delta:0}]}]};
  const bill={id:'bill-1',shop_id:'shop-1',order_number:'POS-1',order_type:'TAKEAWAY',status:'accepted',kitchen_status:'NEW',payment_status:'UNPAID',total_amount:30,created_at:new Date().toISOString()};
  const calls=[],codes=[];let billExists=false,lookupGate=null;
  w.toast=()=>{};w.qtSessionRead=()=>({authUserId:'owner-1',accessToken:'token'});w.qtGetAccessToken=async()=>'token';w.QT_SUPABASE_CONFIG={URL:'https://test.invalid',KEY:'fixture'};w.crypto.randomUUID=()=>`request-${calls.length}`;w.AbortSignal=AbortSignal;w.confirm=()=>true;
  w.supabase={createClient:()=>({realtime:{setAuth:async()=>{}},removeAllChannels:async()=>{},removeChannel:async()=>{},channel:()=>({on(){return this},subscribe(){return this}})})};
  w.qtSupabaseTable=async(q,opts)=>{
    calls.push({q,body:opts?.body});
    if(q==='rpc/pos_my_shop')return 'shop-1';if(q==='rpc/pos_is_owner')return true;
    if(q.startsWith('shop_profiles?'))return [{shop_name:'Shop'}];if(q.startsWith('pos_tables?'))return [];
    if(q.startsWith('products?'))return [product,optionProduct,optionalProduct];if(q.startsWith('pos_staff?'))return [];
    if(q.startsWith('orders?'))return billExists&&q.includes('sales_channel=eq.POS')?[bill]:[];
    if(q.startsWith('order_items?'))return [];
    if(q==='rpc/pos_product_code_lookup'){if(lookupGate)await lookupGate;return opts.body.p_code==='0001234567890'?{code:'0001234567890',code_type:'barcode',product_id:product.id,name:product.name,price:30,available:true,pos_available:true,variants:product.variants}:null}
    if(q==='rpc/pos_create_bill_once'){billExists=true;return bill.id}
    if(q==='rpc/pos_edit_bill')return bill.id;
    if(q==='rpc/pos_add_product_with_options')return {order_id:bill.id,unit_price:60,description:'ขนาด: พิเศษ · ท็อปปิ้ง: ไข่ดาว'};
    if(q==='rpc/pos_product_codes_for_shop')return codes;
    if(q==='rpc/pos_product_code_save'){codes.push({id:'code-1',product_id:product.id,product_name:product.name,code_type:opts.body.p_code_type,code:opts.body.p_code.toUpperCase()});return {id:'code-1'}}
    if(q==='rpc/pos_product_code_remove')return true;
    return [];
  };
  vm.runInContext(fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8'),dom.getInternalVMContext());
  w.qgPosRoute();for(let i=0;i<12;i++)await tick();
  assert.match(w.document.querySelector('#app').textContent,/สแกนบาร์โค้ด \/ SKU/);
  w.qgPosNewTakeaway();let releaseLookup;lookupGate=new Promise(resolve=>releaseLookup=resolve);let input=w.document.querySelector('#qg-pos-code-input');input.value='0001234567890';w.qgPosScanLookup();for(let i=0;i<5;i++)await tick();
  input=w.document.querySelector('#qg-pos-code-input');input.value='0001234567890';w.qgPosScanLookup();releaseLookup();lookupGate=null;for(let i=0;i<50;i++)await tick();
  assert.equal(calls.filter(c=>c.q==='rpc/pos_product_code_lookup').length,2);
  assert.equal(calls.filter(c=>c.q==='rpc/pos_create_bill_once').length,1);
  assert.equal(calls.filter(c=>c.q==='rpc/pos_edit_bill').length,1);
  assert.equal(calls.find(c=>c.q==='rpc/pos_product_code_lookup').body.p_code,'0001234567890');
  assert.match(w.document.querySelector('#qg-pos-scan-status').textContent,/เพิ่ม ชาไทย แล้ว/);
  const optionAdd=w.qgPosAdd(optionProduct.id);for(let i=0;i<5;i++)await tick();
  const dialog=w.document.querySelector('.qg-pos-option-overlay');assert.ok(dialog);assert.equal(dialog.querySelectorAll('input[type=radio]').length,2);assert.equal(dialog.querySelector('input[value=special]').checked,false);
  assert.match(dialog.textContent,/เลือกอย่างน้อย 1/);dialog.querySelector('input[value=special]').checked=true;w.qgPosOptionFinish(true);assert.ok(w.document.querySelector('.qg-pos-option-overlay'),'required multi-select must keep the choice dialog open');assert.equal(calls.some(c=>c.q==='rpc/pos_add_product_with_options'),false);
  dialog.querySelector('input[value=egg]').checked=true;dialog.querySelector('input[value=meat]').checked=true;w.qgPosOptionFinish(true);assert.ok(w.document.querySelector('.qg-pos-option-overlay'),'multi-select max must be enforced before write');assert.equal(calls.some(c=>c.q==='rpc/pos_add_product_with_options'),false);
  dialog.querySelector('input[value=meat]').checked=false;w.qgPosOptionFinish(true);await optionAdd;
  const optionCall=calls.find(c=>c.q==='rpc/pos_add_product_with_options');assert.equal(JSON.stringify(optionCall.body.p_options),JSON.stringify({portion:'special',toppings:['egg']}));assert.equal(optionCall.body.p_product,optionProduct.id);assert.equal(optionCall.body.p_order,bill.id);
  const optionalAdd=w.qgPosAdd(optionalProduct.id);for(let i=0;i<5;i++)await tick();const optionalDialog=w.document.querySelector('.qg-pos-option-overlay');assert.ok(optionalDialog);assert.equal(optionalDialog.querySelector('[data-option-group=sauce]').dataset.minSelect,'0');w.qgPosOptionFinish(true);await optionalAdd;const optionalCall=calls.filter(c=>c.q==='rpc/pos_add_product_with_options').at(-1);assert.equal(optionalCall.body.p_product,optionalProduct.id);assert.equal(optionalCall.body.p_options.sauce,undefined);
  w.qgPosView('codes');for(let i=0;i<10;i++)await tick();
  assert.match(w.document.querySelector('#app').textContent,/SKU \/ บาร์โค้ด/);
  w.qgPosCodeSelect(product.id);w.document.querySelector('#qg-pos-code-value').value='tea-main';await w.qgPosCodeSave();
  assert.equal(calls.filter(c=>c.q==='rpc/pos_product_code_save').length,1);
  assert.equal(calls.find(c=>c.q==='rpc/pos_product_code_save').body.p_product,product.id);
  assert.match(w.document.querySelector('#app').textContent,/TEA-MAIN/);
  w.qgPosDisconnect();dom.window.close();
  console.log(JSON.stringify({checks:20,failures:0,scope:'POS barcode scan through shop lookup into existing idempotent bill flow, manager SKU registry, required/bounded multi-select validation and optional single-choice behavior'}));
})().catch(e=>{console.error(e);process.exit(1)});
