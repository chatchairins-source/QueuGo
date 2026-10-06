const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom');
(async()=>{
 let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const d=new JSDOM('<div id="root"></div>'),w=d.window;
 const shops=[
  {id:'food',shop_name:'ร้านข้าว',public_category:'food',public_description:'ข้าวแกง',address:'เมือง'},
  {id:'cafe',shop_name:'Coffee Shop',public_category:'cafe',public_description:'กาแฟ',address:'ตลาด'},
  {id:'market',shop_name:'ร้านตลาดสด',public_category:'market',public_description:'ผักสด',address:'ตลาดสด'}
 ];
 const ctx=vm.createContext({
  V:{},routeVersion:1,q:'',CATS:{food:'อาหาร',cafe:'เครื่องดื่ม',market:'ตลาดสด'},
  DEDICATED_MARKET_CATS:new Set(['market','fresh','fresh_market','meat','fish','vegetable','fruit']),
  currentPos:null,qgIsValidCoordinate:()=>false,calculateDistanceKm:()=>Infinity,
  loadShops:async()=>shops,
  layout:html=>w.document.getElementById('root').innerHTML=html,
  $:id=>w.document.getElementById(id),
  esc:s=>String(s??'').replace(/[&<>"]/g,''),
  photo:()=>'<span class="ph"></span>',
  ico:()=>'<i></i>',go:()=>{},
  document:w.document,history:w.history
 });
 const source=fs.readFileSync(path.resolve(__dirname,'../index.html'),'utf8');
 const start=source.indexOf('function globalShopSearch');
 const end=source.indexOf('V.food=',start);
 vm.runInContext(source.slice(start,end),ctx);
 await vm.runInContext('V.search()',ctx);
 const input=w.document.getElementById('global-search');
 const count=()=>w.document.getElementById('global-search-count').textContent;
 const visible=()=>[...w.document.querySelectorAll('#global-shop-list .sc')].filter(x=>!x.hidden).map(x=>x.textContent);
 eq(!!input,true);
 eq(w.document.querySelectorAll('#global-shop-list .sc').length,2);
 eq(w.document.getElementById('root').textContent.includes('ร้านตลาดสด'),false);
 eq(count(),'2 ร้าน');
 vm.runInContext("globalShopSearch('กาแฟ')",ctx);
 eq(visible().length,1);
 eq(visible()[0].includes('Coffee Shop'),true);
 eq(count(),'1 ร้าน');
 vm.runInContext("globalShopSearch('อาหาร')",ctx);
 eq(visible().length,1);
 eq(visible()[0].includes('ร้านข้าว'),true);
 vm.runInContext("globalShopSearch('ไม่พบ')",ctx);
 eq(visible().length,0);
 eq(w.document.getElementById('global-search-empty').hidden,false);
 vm.runInContext("globalShopSearch('')",ctx);
 eq(visible().length,2);
 eq(w.document.getElementById('global-search-empty').hidden,true);
 ctx.routeVersion=2;
 await vm.runInContext('V.search(null,1)',ctx);
 eq(w.document.getElementById('global-shop-list')!==null,true);
 d.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'isolated global shop search and dedicated market-category separation; no live directory latency certification'}));
})().catch(e=>{console.error(e);process.exit(1)});
