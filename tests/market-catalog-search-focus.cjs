'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const load = source.match(/async function load\(\)\{[\s\S]*?\n\}\n(?=window\.qgRenderMarketCatalog)/);
assert.ok(load, 'catalog load function exists');
async function scenario(focused) {
  const calls = {shell:0,paint:0,focus:0,selection:0};
  const input = {value:'mango',focus:()=>calls.focus++,setSelectionRange:()=>calls.selection++};
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
  assert.equal(calls.paint,1,'catalog results render');
  assert.equal(calls.shell,focused?0:1,'focused search avoids intermediate loading replacement');
  assert.equal(calls.focus,focused?1:0,'restore keyboard focus only when search was active');
  assert.equal(calls.selection,focused?1:0,'restore text insertion position');
}
(async()=>{await scenario(true);await scenario(false);console.log('Catalog search keyboard focus regression passed')})().catch(e=>{console.error(e);process.exitCode=1});
