'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const match=source.match(/retry\.addEventListener\('click',(\(\)=>\{[\s\S]*?\})\);/);
assert.ok(match,'focused catalog load failure must wire a retry click callback');
for(const [focusedCategory,expected] of [
  [false,'market-catalog-search'],
  [true,'market-catalog-category']
]){
  const events=[];
  const controls={
    'market-catalog-search':{focus:opts=>events.push(['focus','market-catalog-search',opts.preventScroll])},
    'market-catalog-category':{focus:opts=>events.push(['focus','market-catalog-category',opts.preventScroll])}
  };
  const context={
    focusedCategory,
    document:{getElementById:id=>controls[id]||null},
    qgRenderMarketCatalog:()=>events.push(['reload'])
  };
  vm.createContext(context);
  const retry=vm.runInContext(match[1],context);
  retry();
  assert.deepEqual(events,[
    ['focus',expected,true],['reload']
  ],'retry must restore the initiating filter focus before repaint');
}
console.log('market catalog failed-load retry keyboard focus regression: passed');
