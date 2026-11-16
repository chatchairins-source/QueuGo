'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('window.qgMarketCatalogRetryInitial=async()=>{');
const end=source.indexOf('\nwindow.qgRenderMarketCatalog=load;',start);
assert.ok(start>=0&&end>start,'retry handler found');
const snippet=source.slice(start,end);
async function scenario({route='market-catalog',sameSession=true,ready=true}={}){
  const focused=[];
  const state={epoch:5,resultsReady:ready};
  const shop=()=>({id:'shop-1'});
  const profileId=()=> 'shop-1';
  const active=()=>sameSession;
  const document={getElementById:id=>({focus:()=>focused.push(id)})};
  const location={hash:'#'+route};
  const window={};
  const load=async()=>{};
  vm.runInNewContext(snippet,{window,shop,profileId,active,state,document,location,load});
  await window.qgMarketCatalogRetryInitial();
  return focused;
}
(async()=>{
  assert.deepEqual(await scenario(),['market-catalog-search']);
  assert.deepEqual(await scenario({ready:false}),['market-catalog-initial-retry']);
  assert.deepEqual(await scenario({route:'orders'}),[]);
  assert.deepEqual(await scenario({sameSession:false}),[]);
  console.log('market catalog retry session focus: 4 cases passed');
})().catch(e=>{console.error(e);process.exitCode=1});
