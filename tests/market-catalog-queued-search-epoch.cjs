'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('window.qgMarketCatalogQueueSearch=value=>');
const end=source.indexOf('\nwindow.qgMarketCatalogSetCategory=',start);
assert.ok(start>=0&&end>start,'search handler exists');
const handler=source.slice(start,end);
function scenario(changes){
  const state={epoch:4,shop:'shop-a',query:'old',offset:50};
  let callback,loads=0;
  const user={id:'user-a',type:'shop',shopProfileId:'shop-a',category:'market'};
  const context={
    window:{},state,searchTimer:null,clearTimeout(){},
    setTimeout(fn){callback=fn;return 1},
    shop:()=>user,profileId:u=>u?.shopProfileId,
    active:(epoch,id)=>epoch===state.epoch&&id===state.shop&&user.type==='shop'&&user.shopProfileId===id,
    location:{hash:'#market-catalog'},load:()=>{loads++}
  };
  vm.createContext(context);
  vm.runInContext(handler,context);
  context.window.qgMarketCatalogQueueSearch('fresh');
  changes?.({state,user,location:context.location});
  callback();
  return {state,loads};
}
let result=scenario();
assert.equal(result.loads,1,'current merchant and epoch must search');
assert.equal(result.state.query,'fresh');
assert.equal(result.state.offset,0);
result=scenario(({state})=>{state.epoch++});
assert.equal(result.loads,0,'stale render must not trigger a search');
assert.equal(result.state.query,'old','stale timer must not change query');
result=scenario(({user})=>{user.shopProfileId='shop-b'});
assert.equal(result.loads,0,'merchant switch must cancel queued search');
result=scenario(({location})=>{location.hash='#market-stock'});
assert.equal(result.loads,0,'navigation away must cancel queued search');
console.log('Market catalog queued search epoch regression passed');
