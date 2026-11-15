'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('window.qgMarketCatalogChoose=id=>{');
const end=source.indexOf('  const r=state.rows.find',start);
assert.ok(start>=0&&end>start,'catalog choose handler must exist');
const prefix=source.slice(start,end)+'};';
function invoke(loading){
  let authReads=0;
  const context={
    window:{},
    state:{loading,resultsReady:true,shop:'shop-a',query:'apple'},
    shop:()=>{authReads++;return {type:'shop',shopProfileId:'shop-a'};},
    permitted:()=>true,profileId:u=>u.shopProfileId,
    location:{hash:'#market-catalog'},
    document:{getElementById:()=>({value:'apple'})}
  };
  vm.runInNewContext(prefix,context);
  context.window.qgMarketCatalogChoose('item-a');
  return authReads;
}
assert.equal(invoke(true),0,'in-flight search must not reach stale selection/auth lookup');
assert.equal(invoke(false),1,'settled catalog must still permit selection validation');
console.log('market catalog in-flight selection guard: 2 checks passed');
