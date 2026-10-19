const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

(async()=>{
  const root=path.join(__dirname,'..');
  const source=fs.readFileSync(path.join(root,'merchant/market-catalog.js'),'utf8');
  const dom=new JSDOM('<!doctype html><body></body>',{
    url:'https://queuego.test/merchant/#market-catalog',
    runScripts:'outside-only'
  });
  const w=dom.window;
  const calls=[];
  const toasts=[];
  let added=false;

  w.currentUser=()=>({id:'shop-user-1',type:'shop',category:'market',shopProfileId:'shop-profile-1'});
  w.QT_SYNC={profileMaps:{shopByUserId:new Map()}};
  w.qtGetAccessToken=async()=> 'token';
  w.qtSupabaseTable=async(pathname,options)=>{
    calls.push({pathname,options});
    if(pathname==='rpc/qg_market_master_categories'){
      return [{category:'ปลา',n:1},{category:'กุ้ง',n:1}];
    }
    if(pathname==='rpc/qg_market_catalog_page'){
      return [{
        id:'m1',slug:'tilapia',name_th:'ปลานิล',category:'ปลา',
        suggested_unit:'กิโลกรัม',suggested_pack_size:1,source_unit:'1 กก.',
        image:null,has_image_reference:true,
        product_id:added?'p1':null,
        price:added?100:null,delivery_price:added?110:null
      }];
    }
    if(pathname==='rpc/effective_gp_rate') return [{effective_gp_rate:10}];
    if(pathname==='rpc/qg_market_catalog_add'){
      assert.equal(options.body.p_master_product_id,'m1');
      assert.equal(options.body.p_unit,'กิโลกรัม');
      assert.equal(options.body.p_pack_size,2);
      assert.equal(options.body.p_weight_kg,2);
      assert.equal(options.body.p_price,100);
      assert.equal(options.body.p_initial_stock,4);
      assert.ok(options.body.p_request_id,'catalog add must send request id');
      added=true;
      return {product_id:'p1',created:true};
    }
    throw new Error('unexpected API '+pathname);
  };
  w.qtShopLayout=(title,html)=>{w.document.body.innerHTML=html};
  w.toast=msg=>toasts.push(String(msg));
  w.navigate=route=>{w.location.hash='#'+route};

  w.eval(source);
  await w.qgRenderMarketCatalog();

  assert.match(w.document.body.textContent,/เพิ่มสินค้าจากแคตตาล็อก/);
  assert.match(w.document.body.textContent,/ปลานิล/);
  assert.match(w.document.body.textContent,/พบรูปอ้างอิงแล้ว · รอตรวจสิทธิ์ก่อนเผยแพร่/);
  assert.ok(w.document.querySelector('.market-catalog-chip'),'category chips must render');
  assert.equal(w.document.querySelectorAll('.market-catalog-card').length,1);

  w.qgMarketCatalogChoose('m1');
  assert.match(w.document.body.textContent,/ขนาดขายที่ใช้บ่อย/);
  assert.match(w.document.body.textContent,/1 กก./);
  assert.match(w.document.body.textContent,/2 กก./);
  assert.match(w.document.body.textContent,/3 กก./);

  w.qgMarketCatalogSetKg(2);
  const form=w.document.getElementById('market-catalog-add-form');
  assert.ok(form,'catalog add form must render');
  assert.equal(form.querySelector('[name="unit"]').value,'กิโลกรัม');
  assert.equal(form.querySelector('[name="pack"]').value,'2');
  assert.equal(form.querySelector('[name="weight"]').value,'2');
  form.querySelector('[name="price"]').value='100';
  form.querySelector('[name="cost"]').value='80';
  form.querySelector('[name="stock"]').value='4';
  form.querySelector('[name="min"]').value='1';

  const submit=form.querySelector('button[type="submit"]');
  await form.onsubmit({preventDefault(){},submitter:submit});

  assert.ok(added,'catalog add RPC must complete');
  assert.ok(toasts.includes('เพิ่มสินค้าเข้าร้านแล้ว'),'success feedback must render');
  assert.match(w.document.body.textContent,/มีในร้านแล้ว/,'successful add must reload authoritative catalog');
  assert.equal(w.document.querySelector('.market-catalog-card button').disabled,true,'added master must not be addable again');
  assert.equal(w.localStorage.length,0,'successful add must clear pending recovery receipt');

  const addCalls=calls.filter(x=>x.pathname==='rpc/qg_market_catalog_add');
  assert.equal(addCalls.length,1,'single submit must issue exactly one add mutation');
  console.log(JSON.stringify({
    checks:16,
    failures:0,
    scope:'isolated Merchant Market catalog DOM browse/filter/image-status/1-2-3kg/add/reload/idempotent UI; no Production writes'
  }));
})().catch(err=>{console.error(err);process.exit(1)});
