const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'..'),tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
  const dom=new JSDOM('<div id="app"></div>',{url:'https://queuego.test/merchant/#pos',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window;let category='food';
  w.toast=()=>{};w.qtSessionRead=()=>({authUserId:'owner',accessToken:'token'});w.qtGetAccessToken=async()=>'token';w.crypto.randomUUID=()=>crypto.randomUUID();
  w.QT_SUPABASE_CONFIG={URL:'https://test.invalid',KEY:'fixture'};w.AbortSignal=AbortSignal;
  const tables=[
    {id:'t-available',label:'โต๊ะ 1',active:true},
    {id:'t-ready',label:'โต๊ะ 2',active:true},
    {id:'t-served',label:'โต๊ะ 3',active:true},
    {id:'t-disabled',label:'โต๊ะ 4',active:false}
  ];
  const orders=[
    {id:'o-ready',order_number:'Q-2',order_type:'DINE_IN',table_id:'t-ready',status:'accepted',payment_status:'UNPAID',bill_status:'OPEN',kitchen_status:'READY'},
    {id:'o-served',order_number:'Q-3',order_type:'DINE_IN',table_id:'t-served',status:'accepted',payment_status:'UNPAID',bill_status:'OPEN',kitchen_status:'SERVED'}
  ];
  w.qtSupabaseTable=async query=>{
    if(query==='rpc/pos_my_shop')return 'shop';
    if(query==='rpc/pos_is_owner')return true;
    if(query.startsWith('shop_profiles?'))return [{shop_name:'Shop',public_category:category}];
    if(query.startsWith('pos_tables?'))return tables;
    if(query.startsWith('products?'))return [];
    if(query.startsWith('orders?'))return orders;
    if(query.startsWith('pos_staff?'))return [];
    if(query.startsWith('order_items?'))return [];
    return [];
  };
  w.supabase={createClient:()=>({realtime:{setAuth:async()=>{}},removeAllChannels:async()=>{},removeChannel:async()=>{},channel:()=>({on(){return this},subscribe(){return this}})})};
  vm.runInContext(fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8'),dom.getInternalVMContext());
  w.qgPosRoute();for(let i=0;i<10;i++)await tick();
  w.qgPosView('tables');
  const tableHtml=w.document.querySelector('.qg-pos-table-list').innerHTML;
  assert.match(tableHtml,/สถานะ โต๊ะ 1: ว่าง/);
  assert.match(tableHtml,/รอชำระเงิน · Q-3/);
  assert.match(tableHtml,/พร้อมเสิร์ฟ · Q-2/);
  assert.match(tableHtml,/ปิดใช้งาน/);
  w.qgPosView('counter');
  assert.match(w.document.querySelector('.qg-pos-tables').textContent,/รอชำระเงิน · Q-3/);
  category='market';await w.qgPosReload();for(let i=0;i<10;i++)await tick();
  w.qgPosView('tables');
  assert.doesNotMatch(w.document.querySelector('.qg-pos-table-list').textContent,/รอชำระเงิน|พร้อมเสิร์ฟ/,'non-food shops do not infer kitchen states');
  dom.window.close();
  console.log(JSON.stringify({checks:6,failures:0,scope:'POS table availability, disabled, kitchen preparation, ready, and payment-waiting state labels in both table views'}));
})().catch(error=>{console.error(error);process.exit(1)});
