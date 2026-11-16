'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../merchant/market-catalog.js'),'utf8');
const choose=source.match(/window\.qgMarketCatalogChoose=id=>\{[\s\S]*?\n\};\n\}\)\(\);/);
assert.ok(choose,'catalog choose handler exists');
const focused=[];
const form={onsubmit:null};
const heading={focus:options=>focused.push(options)};
const state={loading:false,resultsReady:true,shop:'merchant',rows:[{id:'p1',name_th:'มะม่วง',category:'ผลไม้',source_unit:'ลูก',suggested_unit:'ลูก',suggested_pack_size:1}],gpRate:5,query:''};
const ctx={
  window:{},state,shop:()=>({id:'u',shopProfileId:'merchant',type:'shop'}),
  permitted:()=>true,profileId:u=>u.shopProfileId,
  location:{hash:'#market-catalog'},
  document:{
    getElementById:id=>id==='market-catalog-add-form'?form:id==='market-catalog-add-heading'?heading:null,
    querySelectorAll:()=>[]
  },
  shell:html=>{assert.match(html,/id="market-catalog-add-heading" tabindex="-1"/,'heading must be programmatically focusable')},
  E:v=>String(v??''),productImage:()=>'',UNITS:['ลูก'],qgMarketAttachWeightPresets:()=>{},
  qgMarketCatalogPricePreview:()=>{},crypto:{randomUUID:()=> 'id'},Number,FormData,
  active:()=>true
};
vm.createContext(ctx);
vm.runInContext(choose[0].replace(/\n\}\)\(\);$/,''),ctx);
ctx.window.qgMarketCatalogChoose('p1');
assert.equal(focused.length,1,'opening add form must focus its heading');
assert.equal(focused[0].preventScroll,true,'focus must not unexpectedly scroll the viewport');
assert.equal(typeof form.onsubmit,'function','form submission remains attached');
console.log('market catalog add form keyboard focus regression: passed');
