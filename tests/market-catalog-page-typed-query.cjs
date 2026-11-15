'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../merchant/market-catalog.js'),'utf8');
const handler=source.match(/window\.qgMarketCatalogPage=dir=>\{[^\n]+\};/);
assert.ok(handler,'pagination handler exists');
function run({query,typed,offset,hasMore,dir,loading=false}){
 const state={query,offset,hasMore,loading};
 let calls=0,cancelled=[],searchTimer=17;
 const ctx={window:{},state,searchTimer,clearTimeout:id=>cancelled.push(id),document:{getElementById:id=>id==='market-catalog-search'?{value:typed}:null},load:()=>{calls++}};
 vm.createContext(ctx);vm.runInContext(handler[0],ctx);ctx.window.qgMarketCatalogPage(dir);
 return {state,calls,cancelled,searchTimer:ctx.searchTimer};
}
let r=run({query:'old',typed:'fresh mango',offset:50,hasMore:true,dir:1});
assert.equal(r.state.query,'fresh mango');assert.equal(r.state.offset,0);assert.equal(r.calls,1);assert.deepEqual(r.cancelled,[17]);assert.equal(r.searchTimer,null);
r=run({query:'mango',typed:'mango',offset:50,hasMore:true,dir:1});
assert.equal(r.state.offset,100);assert.equal(r.calls,1);assert.equal(r.cancelled.length,0);
r=run({query:'mango',typed:'mango',offset:50,hasMore:false,dir:1});
assert.equal(r.state.offset,50);assert.equal(r.calls,0);
r=run({query:'old',typed:'new',offset:50,hasMore:true,dir:-1,loading:true});
assert.equal(r.state.query,'old');assert.equal(r.calls,0);
console.log('Market catalog pagination pending-query regression passed');
