'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('window.qgMarketCatalogRetryInitial=async()=>{');
const end=source.indexOf('\nwindow.qgRenderMarketCatalog=load;',start);
assert.ok(start>=0&&end>start,'retry handler present');
const snippet=source.slice(start,end);
async function scenario({superseded=false,ready=true}={}){
  const focused=[];
  const state={epoch:3,resultsReady:ready};
  let resolveLoad;
  const load=()=>{state.epoch++;return new Promise(resolve=>{resolveLoad=resolve})};
  const shop=()=>({id:'shop-1'});
  const profileId=()=> 'shop-1';
  const active=(epoch,id)=>epoch===state.epoch&&id==='shop-1';
  const document={getElementById:id=>({focus:()=>focused.push(id)})};
  const location={hash:'#market-catalog'};
  const window={};
  vm.runInNewContext(snippet,{window,shop,profileId,active,state,document,location,load});
  const retry=window.qgMarketCatalogRetryInitial();
  if(superseded)state.epoch++;
  resolveLoad();
  await retry;
  return focused;
}
(async()=>{
  assert.deepEqual(await scenario(),['market-catalog-search']);
  assert.deepEqual(await scenario({ready:false}),['market-catalog-initial-retry']);
  assert.deepEqual(await scenario({superseded:true}),[],'older retry must not steal focus');
  assert.deepEqual(await scenario({superseded:true,ready:false}),[],'older failed retry must not steal focus');
  console.log('market catalog retry epoch focus: 4 cases passed');
})().catch(err=>{console.error(err);process.exitCode=1});
