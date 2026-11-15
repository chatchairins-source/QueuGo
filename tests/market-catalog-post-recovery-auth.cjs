'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const start = source.indexOf('async function load(){');
const end = source.indexOf('window.qgRenderMarketCatalog=load;', start);
assert.ok(start >= 0 && end > start, 'catalog loader is present');
const loader = source.slice(start, end);
assert.match(loader, /await recoverPending\(id,epoch\);\s*if\(!active\(epoch,id\)\)return;\s*try\s*\{/,
  'loader rechecks session immediately after asynchronous pending recovery');

async function runScenario(changeSession) {
  let principal = {type:'shop', shopProfileId:'shop-a'};
  let finishRecovery;
  const recovery = new Promise(resolve => { finishRecovery = resolve; });
  let requests = 0;
  const state = {epoch:0, shop:null, loading:false, gpRate:null, rows:[], categories:[], hasMore:false, query:'', category:'', offset:0};
  const context = {
    state, location:{hash:'#market-catalog'}, shop:()=>principal,
    profileId:u=>u?.shopProfileId, permitted:u=>u?.type==='shop',
    shell:()=>{}, recoverPending:()=>recovery,
    rpc:async()=>{ requests++; return []; },
    qgMarketReadGpRate:async()=>{ requests++; return 5; },
    active:(epoch,id)=>!!principal&&principal.type==='shop'&&principal.shopProfileId===id&&
      epoch===state.epoch&&id===state.shop&&context.location.hash==='#market-catalog',
    MARKET_MASTER_CATEGORIES:new Set(), paint:()=>{}, error:()=>{},
  };
  vm.createContext(context);
  vm.runInContext(loader + '\nthis.runLoad=load;', context);
  const loading = context.runLoad();
  if(changeSession==='switch') principal={type:'shop',shopProfileId:'shop-b'};
  if(changeSession==='signout') principal=null;
  if(changeSession==='route') context.location.hash='#market-stock';
  finishRecovery();
  await loading;
  return requests;
}

(async()=>{
  assert.equal(await runScenario('switch'),0,'no old-shop catalog requests after account switch');
  assert.equal(await runScenario('signout'),0,'no catalog requests after sign-out');
  assert.equal(await runScenario('route'),0,'no catalog requests after route departure');
  assert.equal(await runScenario(null),3,'valid session still loads categories, page and GP');
  console.log('market catalog post-recovery session regression: 4 cases passed');
})().catch(e=>{console.error(e);process.exitCode=1;});
