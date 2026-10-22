const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'..');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
  const dom=new JSDOM('<div id="app"></div>',{url:'https://queuego.test/merchant/#pos',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window;let fullReads=0,fallbackReads=0;
  w.toast=()=>{};w.qtSessionRead=()=>({authUserId:'actor',accessToken:'token'});w.qtGetAccessToken=async()=>'token';w.QT_SUPABASE_CONFIG={URL:'https://test.invalid',KEY:'fixture'};
  w.supabase={createClient:()=>({realtime:{setAuth:async()=>{}},channel:()=>({on(){return this},subscribe(){return this}}),removeChannel:async()=>{}})};
  w.qtSupabaseTable=async query=>{
    if(query==='rpc/pos_my_shop')return 'shop';
    if(query==='rpc/pos_is_owner')return true;
    if(query.startsWith('shop_profiles?'))return [{shop_name:'Shop'}];
    if(query.startsWith('products?')){
      if(query.includes('inventory_stock_tracking')){fullReads++;const e=Error('column products.inventory_stock_tracking does not exist');e.code='42703';throw e}
      fallbackReads++;return [{id:'product-1',name:'ชาไทย',price:45,pos_price:45,available:true,pos_available:true,stock:0,grocery_stock_tracking:false}];
    }
    return [];
  };
  vm.runInContext(fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8'),dom.getInternalVMContext());
  w.qgPosRoute();for(let i=0;i<12;i++)await tick();
  assert.equal(fullReads,1,'first read checks for the generic inventory schema');
  assert.equal(fallbackReads,1,'undefined-column response retries using the installed Production schema');
  assert(w.document.querySelector('.qg-pos-products')?.textContent.includes('ชาไทย'),'POS catalog renders after schema-compatible fallback');
  assert(!w.document.querySelector('#qg-pos-error')?.textContent.includes('inventory_stock_tracking'),'missing optional column is not shown as a fatal POS error');
  dom.window.close();
  console.log(JSON.stringify({checks:4,failures:0,scope:'POS can load against deployed schema without generic inventory column; installed grocery stock fields remain queried'}));
})().catch(error=>{console.error(error);process.exit(1)});
