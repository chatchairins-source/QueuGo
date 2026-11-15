'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('window.qgMarketCatalogChoose=id=>{');
const end=source.indexOf('  const r=state.rows.find',start);
assert.ok(start>=0&&end>start,'catalog selection handler exists');
const handler=source.slice(start,end)+'};';
function attempt({loading=false,resultsReady=false}={}){
  let authReads=0;
  const ctx={
    window:{},state:{loading,resultsReady,shop:'shop-a',query:'apple'},
    shop:()=>{authReads++;return {type:'shop',shopProfileId:'shop-a'};},
    permitted:()=>true,profileId:u=>u.shopProfileId,
    location:{hash:'#market-catalog'},
    document:{getElementById:()=>({value:'apple'})}
  };
  vm.runInNewContext(handler,ctx);
  ctx.window.qgMarketCatalogChoose('item-a');
  return authReads;
}
assert.equal(attempt({loading:true,resultsReady:true}),0,'in-flight cards cannot be selected');
assert.equal(attempt({loading:false,resultsReady:false}),0,'failed refresh must not reactivate stale cards');
assert.equal(attempt({loading:false,resultsReady:true}),1,'successful refresh allows normal selection');
assert.match(source,/state\.loading=true;state\.resultsReady=false;/,'refresh invalidates old cards before RPC');
assert.match(source,/paint\(\);\s*state\.resultsReady=true;/,'only successfully painted results become selectable');
assert.match(source,/state\.loading=false;state\.resultsReady=false;state\.gpRate=null;/,'ineligible session invalidates results');
console.log('market catalog stale results selection: 6 checks passed');
