'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const load = source.match(/async function load\(\)\{[\s\S]*?\n\}\n(?=window\.qgRenderMarketCatalog)/);
assert.ok(load, 'catalog load function exists');
async function scenario(focused, staleInput=false) {
  const calls = {shell:0,paint:0,focus:0,selection:0};
  const input = {value:staleInput?'mango fresh':'mango',selectionStart:2,selectionEnd:4,selectionDirection:'forward',focus:()=>calls.focus++,setSelectionRange:(start,end,direction)=>{assert.deepEqual([start,end,direction],[2,4,'forward']);calls.selection++}};
  const state = {epoch:0,shop:null,loading:false,query:'mango',category:'',offset:0};
  const ctx = {
    state, shop:()=>({id:'u',shopProfileId:'merchant',type:'shop'}),
    profileId:u=>u.shopProfileId,permitted:()=>true,
    document:{activeElement:{id:focused?'market-catalog-search':'other'},getElementById:()=>input},
    shell:()=>calls.shell++,recoverPending:async()=>{},
    active:(epoch,id)=>epoch===state.epoch&&id===state.shop,
    rpc:async name=>name==='qg_market_master_categories'?[]:[],
    qgMarketReadGpRate:async()=>5,
    MARKET_MASTER_CATEGORIES:new Set(),paint:()=>calls.paint++,
    error:e=>{throw e;}
  };
  vm.createContext(ctx);
  vm.runInContext(load[0],ctx);
  await vm.runInContext('load()',ctx);
  assert.equal(calls.paint,staleInput?0:1,'stale results must not replace newer user input');
  assert.equal(calls.shell,focused?0:1,'focused search avoids intermediate loading replacement');
  assert.equal(calls.focus,focused&&!staleInput?1:0,'restore keyboard focus only for current results');
  assert.equal(calls.selection,focused&&!staleInput?1:0,'restore original selection range and direction');
}
(async()=>{await scenario(true);await scenario(false);await scenario(true,true);console.log('Catalog search keyboard focus regression passed')})().catch(e=>{console.error(e);process.exitCode=1});
