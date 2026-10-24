'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=require('node:path').resolve(__dirname,'..');
const mapSource=fs.readFileSync(root+'/queuego-map-web.js','utf8');
const customer=fs.readFileSync(root+'/index.html','utf8');
const merchant=fs.readFileSync(root+'/merchant/index.html','utf8');
const rider=fs.readFileSync(root+'/rider/index.html','utf8');
const calls=[];let responseRows=[];
const window={QG_SEARCH_CONFIG:{url:'https://qg.test',key:'publishable-test',providers:[]}};
const context=vm.createContext({window,console,WeakMap,Map,Set,Number,Math,Array,String,Object,URL,document:{currentScript:{src:'https://qg.test/queuego-map-web.js'}},fetch:async(url,options)=>{calls.push({url,options});return{ok:true,json:async()=>responseRows}},AbortSignal:undefined});
vm.runInContext(mapSource,context);
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
(async()=>{
  responseRows=[{place_id:'p-th-1',canonical_name:'บ้านโคก',name_th:'บ้านโคก',name_en:'Ban Khok',latitude:15.2,longitude:102.4,province:'นครราชสีมา',verification_status:'verified',source:'th_admin_open_data',source_license:'CC-BY-4.0',score:1,distance_m:null}];
  const rows=await window.QueueGoMapWeb.search('บ้านโคก',{limit:8});
  ok(calls.length===1,'all configured roles must call the shared Places RPC');
  ok(calls[0].url==='https://qg.test/rest/v1/rpc/qg_search_places','search must use qg_search_places');
  ok(calls[0].options.headers.apikey==='publishable-test','search uses publishable key only');
  ok(!('Authorization' in calls[0].options.headers),'public place search does not need a user token');
  const body=JSON.parse(calls[0].options.body);
  ok(body.p_query==='บ้านโคก'&&body.p_limit===8,'query and bounded limit reach one normalized RPC');
  ok(rows[0].name==='บ้านโคก'&&rows[0].lat===15.2&&rows[0].lng===102.4,'RPC results normalize to QueueGo Map coordinates');
  ok(rows[0].provider==='queuego-places'&&rows[0].persistable===true,'QueueGo master results have explicit provenance');

  responseRows=[];
  window.QueueGoMapWeb.configureSearch({url:'https://qg.test',key:'publishable-test',providers:[{id:'licensed-external',search:async()=>[{name:'Temporary POI',lat:14.1,lon:101.2,display_name:'Temporary POI, Thailand'}]}]});
  const fallback=await window.QueueGoMapWeb.search('Temporary POI');
  ok(fallback.length===1&&fallback[0].provider==='licensed-external','configured provider fallback runs only after QueueGo Places returns no result');
  ok(fallback[0].persistable===false&&fallback[0].place_id===null,'external results remain lookup-only and are not QueueGo master records');
  ok(!mapSource.includes('nominatim.openstreetmap.org/search'),'shared app search does not call an unconfigured public geocoder directly');
  for(const [name,source] of [['Customer',customer],['Merchant',merchant],['Rider',rider]]){
    ok(source.includes('QG_SEARCH_CONFIG'),`${name} supplies shared QueueGo Search configuration`);
    ok(source.includes('pkypiqhlrmzocysgeqew.supabase.co'),`${name} uses the same QueueGo Supabase authority`);
  }
  console.log(JSON.stringify({checks,failures:0,scope:'shared QueueGo Places RPC, normalized coordinates, provider fallback policy and Customer/Merchant/Rider configuration'}));
})().catch(error=>{console.error(error);process.exitCode=1});
