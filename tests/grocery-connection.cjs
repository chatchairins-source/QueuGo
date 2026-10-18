'use strict';
const fs=require('fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function setup(handler){
 const d=new JSDOM('<div id="app"></div>',{url:'https://isolated.invalid/#shop-products',runScripts:'outside-only'}),w=d.window;
 w.shopUser=()=>({id:'owner'});w.profileId=()=> 'shop';w.qtGetAccessToken=async()=> 'isolated-token';w.navigate=()=>{};
 w.layout=(t,b)=>w.document.getElementById('app').innerHTML=b;
 w.qtSupabaseTable=async(n,o)=>n.endsWith('categories')?[]:n.endsWith('effective_gp_rate')?10:handler(n,o,w);
 w.eval(fs.readFileSync('merchant/grocery-catalog.js','utf8'));return w;
}
(async()=>{
 let calls=0;const row={id:'row',product_name_th:'มาม่า',stock_qty:0};
 let w=await setup(async()=>{if(++calls===1)throw new TypeError('Failed to fetch');return [row]});
 await w.qgRenderGroceryCatalog();assert.equal(calls,2);assert.equal(w.document.querySelectorAll('.qgg-row').length,1);w.close();
 calls=0;w=await setup(async()=>{calls++;throw Error('column x.subcategory does not exist')});await w.qgRenderGroceryCatalog();assert.equal(calls,1);assert.equal(w.document.getElementById('qgg-retry').hidden,false);assert.equal(w.document.getElementById('qgg-save').disabled,true);
 w.qtSupabaseTable=async n=>n.endsWith('merchant_page')?[row]:[];w.document.getElementById('qgg-retry').click();await sleep(30);assert.equal(w.document.querySelectorAll('.qgg-row').length,1);w.close();
 let online=false;calls=0;w=await setup(async()=>{calls++;return [row]});Object.defineProperty(w.navigator,'onLine',{get:()=>online});await w.qgRenderGroceryCatalog();assert.equal(calls,0);online=true;w.dispatchEvent(new w.Event('online'));await sleep(30);assert.equal(calls,1);
 const price=w.document.querySelector('[data-field="selling_price"]');price.value='25';price.dispatchEvent(new w.Event('input',{bubbles:true}));online=false;w.dispatchEvent(new w.Event('offline'));online=true;w.dispatchEvent(new w.Event('online'));await sleep(30);assert.equal(calls,1);assert.equal(price.value,'25');w.close();
 // A late read from a previous account never retries or restores its UI.
 let reject;calls=0;w=await setup(async()=>{calls++;return new Promise((_,r)=>reject=r)});const load=w.qgRenderGroceryCatalog();await sleep(10);w.location.hash='#login';w.document.getElementById('app').replaceChildren();reject(new TypeError('Failed to fetch'));await load;assert.equal(calls,1);assert.equal(w.document.querySelector('.qgg'),null);w.close();
 console.log('Grocery connection: 12 checks passed; isolated transport only');
})().catch(e=>{console.error(e);process.exit(1)});
