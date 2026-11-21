'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../merchant/market-catalog.js'),'utf8');
const load=source.match(/async function load\(\)\{[\s\S]*?\n\}\n(?=window\.qgRenderMarketCatalog)/);
assert.ok(load,'load() must exist');
async function scenario(focused){
  const calls={shell:0,error:0};
  const status={textContent:''};
  const state={epoch:0,shop:null,loading:false,rows:[],categories:[],query:'mango',category:'',offset:0};
  const ctx={
    state,shop:()=>({shopProfileId:'shop-a',type:'shop'}),profileId:u=>u.shopProfileId,
    permitted:()=>true,document:{activeElement:{id:focused?'market-catalog-search':'other'},getElementById:id=>id==='market-catalog-status'?status:null},
    shell:()=>calls.shell++,recoverPending:async()=>{},active:(epoch,id)=>epoch===state.epoch&&id===state.shop,
    rpc:async()=>{throw Error('offline')},qgMarketReadGpRate:async()=>5,
    MARKET_MASTER_CATEGORIES:new Set(),paint:()=>{throw Error('unexpected paint')},
    error:()=>calls.error++
  };
  vm.createContext(ctx);vm.runInContext(load[0],ctx);await vm.runInContext('load()',ctx);
  assert.equal(calls.error,1,'error must be reported');
  assert.equal(calls.shell,focused?0:2,'focused input stays mounted on RPC failure');
  if(focused)assert.match(status.textContent,/โหลดแคตตาล็อกไม่สำเร็จ/);
  assert.equal(state.loading,false,'loading resets after failure');
}
(async()=>{await scenario(true);await scenario(false);console.log('Catalog failed-search focus regression passed')})().catch(e=>{console.error(e);process.exitCode=1});
