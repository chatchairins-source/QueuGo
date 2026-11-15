'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const loadStart=source.indexOf('async function load(){');
const loadEnd=source.indexOf('window.qgRenderMarketCatalog=load;',loadStart);
assert.ok(loadStart>=0&&loadEnd>loadStart,'catalog load must exist');
const load=source.slice(loadStart,loadEnd);
assert.match(load,/const focusedCategory=document\.activeElement\?\.id==='market-catalog-category'/,'capture category focus before async load');
const focusStart=load.indexOf('    if(focusedSearch){\n      const input=document.getElementById',load.indexOf('    paint();'));
const focusEnd=load.indexOf('\n  }catch(e)',focusStart);
assert.ok(focusStart>=0&&focusEnd>focusStart,'focus restoration follows repaint');
const snippet=load.slice(focusStart,focusEnd);
function run(focusedSearch,focusedCategory){
  const calls=[];
  const elements={
    'market-catalog-search':{focus:()=>calls.push('search'),setSelectionRange:()=>calls.push('selection')},
    'market-catalog-category':{focus:()=>calls.push('category')}
  };
  const ctx={focusedSearch,focusedCategory,selection:null,document:{getElementById:id=>elements[id]}};
  vm.runInNewContext(snippet,ctx);
  return calls;
}
assert.deepEqual(run(false,true),['category'],'category select retains focus after repaint');
assert.deepEqual(run(true,false),['search'],'search focus behavior remains intact');
assert.deepEqual(run(false,false),[],'no unexpected focus on unrelated interactions');
console.log('market catalog category keyboard focus: 5 checks passed');
