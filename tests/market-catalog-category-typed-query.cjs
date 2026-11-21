'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../merchant/market-catalog.js'),'utf8');
const handler=source.match(/window\.qgMarketCatalogSetCategory=category=>\{[^\n]+\};/);
assert.ok(handler,'category handler exists');
const state={query:'old search',category:'',offset:100};
let cancelled=null,loads=0,searchValue='fresh mango';
const context={
  window:{},state,searchTimer:12,
  clearTimeout:id=>{cancelled=id},
  document:{getElementById:id=>id==='market-catalog-search'?{value:searchValue}:null},
  load:()=>{loads++}
};
vm.createContext(context);
vm.runInContext(handler[0],context);
context.window.qgMarketCatalogSetCategory('ผลไม้');
assert.equal(cancelled,12,'pending search debounce must be cancelled');
assert.equal(context.searchTimer,null,'timer reference must be cleared');
assert.equal(state.query,'fresh mango','category change must preserve latest typed query');
assert.equal(state.category,'ผลไม้');
assert.equal(state.offset,0);
assert.equal(loads,1,'category change must load exactly once');
searchValue='   ';
context.window.qgMarketCatalogSetCategory('');
assert.equal(state.query,'','empty search must clear query');
assert.equal(loads,2);
context.document.getElementById=()=>null;
state.query='retained';
context.window.qgMarketCatalogSetCategory('ผักสด');
assert.equal(state.query,'retained','missing search field must not clear current query');
console.log('Market catalog category and pending search regression passed');
