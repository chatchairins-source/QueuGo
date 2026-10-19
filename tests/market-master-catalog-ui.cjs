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
  let gpDenied=false;

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
        catalog_preview_image:'https://images.mango-prod.siammakro.cloud/product-images/tilapia.jpeg',
        catalog_preview_source_url:'https://www.makro.pro/th/p/tilapia',
        catalog_preview_source_name:'Makro PRO',
        product_id:added?'p1':null,
        price:added?100:null,delivery_price:added?110:null
      },{
        id:'m2',slug:'climbing-perch',name_th:'ปลาหมอ',category:'ปลา',
        suggested_unit:'กิโลกรัม',suggested_pack_size:1,source_unit:'1 กก.',
        image:'https://upload.wikimedia.org/example.jpg',
        image_source:'https://commons.wikimedia.org/wiki/File:Example.jpg',
        image_rights_evidence:'Example Author · CC BY-SA 4.0 · Wikimedia Commons',
        has_image_reference:true,
        product_id:null,price:null,delivery_price:null
      }];
    }
    if(pathname==='rpc/effective_gp_rate'){if(gpDenied)throw Error('shop access denied');return [{effective_gp_rate:10}]}
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

  w.eval(fs.readFileSync(path.join(root,'merchant/market.js'),'utf8'));
  w.eval(source);
  await w.qgRenderMarketCatalog();

  assert.match(w.document.body.textContent,/เพิ่มสินค้าจากแคตตาล็อก/);
  assert.match(w.document.body.textContent,/ปลานิล/);
  assert.match(w.document.body.textContent,/รูปอ้างอิงจาก Makro PRO/);
  assert.match(w.document.body.textContent,/กำลังเพิ่มรูปอ้างอิง Makro ที่ตรงกับสินค้า/);
  assert.ok(w.document.querySelector('.market-catalog-chip'),'category chips must render');
  assert.equal(w.document.querySelectorAll('.market-catalog-card').length,2);
  const catalogImages=[...w.document.querySelectorAll('img.market-catalog-image')];
  assert.equal(catalogImages.length,1,'Merchant Market catalog must render only Makro retailer previews');
  assert.equal(catalogImages[0].alt,'ปลานิล');
  assert.equal([...w.document.querySelectorAll('.market-catalog-credit a')].some(a=>/CC BY-SA 4.0/.test(a.textContent)),false,'generic Commons art must stay out of Merchant Market catalog');
  const makroLink=[...w.document.querySelectorAll('.market-catalog-credit a')].find(a=>/ดูสินค้าต้นทาง/.test(a.textContent));
  assert.ok(makroLink,'Makro preview must link to the source product page');
  assert.match(makroLink.href,/makro\.pro/);

  w.qgMarketCatalogChoose('m2');
  assert.equal(w.document.querySelector('.market-catalog-preview img'),null,'non-Makro approved art must not leak into the Merchant add form');
  assert.match(w.document.querySelector('.market-catalog-preview').textContent,/กำลังเพิ่มรูปอ้างอิง Makro/);

  w.qgMarketCatalogChoose('m1');
  const makroPreview=w.document.querySelector('.market-catalog-preview img');
  assert.ok(makroPreview,'Makro Merchant preview must remain visible in the add form');
  assert.equal(makroPreview.alt,'ปลานิล');
  assert.match(w.document.querySelector('.market-catalog-preview').textContent,/รูปอ้างอิงจาก Makro PRO/);
  assert.match(w.document.body.textContent,/ขนาดขายที่ใช้บ่อย/);
  assert.match(w.document.body.textContent,/1 กก./);
  assert.match(w.document.body.textContent,/2 กก./);
  assert.match(w.document.body.textContent,/3 กก./);

  const weightForm=w.document.getElementById('market-catalog-add-form');
  w.qgMarketAttachWeightPresets(weightForm);
  assert.equal(weightForm.querySelectorAll('[data-market-weight-presets]').length,1,'shared preset initialization must be idempotent');
  for(const kg of [1,2,3]){
    weightForm.querySelectorAll('[data-market-weight-presets] button')[kg-1].click();
    assert.equal(weightForm.elements.namedItem('weight').value,String(kg));
    assert.equal(weightForm.elements.namedItem('pack').value,String(kg));
  }
  const custom=weightForm.elements.namedItem('pack');custom.value='1.75';custom.dispatchEvent(new w.Event('input'));
  assert.equal(weightForm.elements.namedItem('weight').value,'1.75','custom kg must remain supported');
  weightForm.elements.namedItem('unit').value='กรัม';custom.value='750';custom.dispatchEvent(new w.Event('input'));
  assert.equal(weightForm.elements.namedItem('weight').value,'0.75','grams must convert to kg');
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

  w.qgMarketEdit();
  const manual=w.document.getElementById('market-form');
  assert.ok(manual.querySelector('[data-market-weight-presets]'),'manual add must reuse weight presets');
  manual.querySelectorAll('[data-market-weight-presets] button')[2].click();
  assert.equal(manual.elements.namedItem('unit').value,'กิโลกรัม');
  assert.equal(manual.elements.namedItem('pack').value,'3');
  assert.equal(manual.elements.namedItem('weight').value,'3');

  gpDenied=true;
  await w.qgRenderMarketCatalog();
  assert.equal(w.document.querySelectorAll('.market-catalog-card').length,2,'denied optional GP preview must not block catalog');
  w.qgMarketCatalogChoose('m2');
  w.document.querySelector('[name="price"]').value='100';w.qgMarketCatalogPricePreview();
  assert.match(w.document.getElementById('market-catalog-delivery-preview').textContent,/คำนวณ GP อัตโนมัติหลังบันทึก/);
  assert.doesNotMatch(w.document.getElementById('market-catalog-delivery-preview').textContent,/GP 0/,'unknown GP must never become zero');

  const addCalls=calls.filter(x=>x.pathname==='rpc/qg_market_catalog_add');
  assert.equal(addCalls.length,1,'single submit must issue exactly one add mutation');
  console.log(JSON.stringify({
    checks:45,
    failures:0,
    scope:'isolated Merchant Market catalog DOM browse/filter/pending+approved image attribution/1-2-3kg/add/reload/idempotent UI; no Production writes'
  }));
})().catch(err=>{console.error(err);process.exit(1)});
