'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('async function load(){');
const end=source.indexOf('window.qgRenderMarketCatalog=load;',start);
assert.ok(start>=0&&end>start,'catalog load exists');
const load=source.slice(start,end);
const loading=load.match(/if\(!focusedSearch[^\n]*shell\('<p>กำลังโหลดแคตตาล็อกตลาด\.\.\.<\/p>'\);/);
assert.ok(loading,'loading shell conditional found');
const error=load.match(/if\(focusedSearch[^\n]*\{\n\s*const status=document\.getElementById\('market-catalog-status'\);/);
assert.ok(error,'network error status conditional found');
function check(expression,focusedSearch,focusedCategory){
  const ctx={focusedSearch,focusedCategory};
  return vm.runInNewContext(expression,ctx);
}
const loadingCondition=loading[0].slice(3,loading[0].indexOf(')shell'));
const errorCondition=error[0].slice(3,error[0].indexOf('){'));
for(const [search,category,keep] of [[true,false,true],[false,true,true],[false,false,false]]){
  assert.equal(!check(loadingCondition,search,category),keep,'loading preserves focused control');
  assert.equal(!!check(errorCondition,search,category),keep,'RPC error preserves focused control');
}
console.log('market catalog category loading/error focus: 8 checks passed');
