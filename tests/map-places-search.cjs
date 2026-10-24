const fs=require('fs'),vm=require('vm'),assert=require('assert');
let calls=0,checks=0;const rows=[{place_id:'place-1',canonical_name:'7-Eleven',name_th:'เซเว่น อีเลฟเว่น',name_en:'7-Eleven',category:'grocery',latitude:15.2,longitude:102.4,province:'นครราชสีมา',source:'queuego-places',source_license:'ODbL-1.0',verification_status:'verified',score:1,distance_m:230}];
const window={QG_SEARCH_CONFIG:{url:'https://qg.test',key:'publishable'}};
const context=vm.createContext({window,console,WeakMap,Map,Set,Number,Math,Array,String,Object,URL,document:{currentScript:{src:'https://qg.test/queuego-map-web.js'}},fetch:async(url,options)=>{calls++;assert(url.endsWith('/rest/v1/rpc/qg_search_places'));return{ok:true,json:async()=>rows}}});
vm.runInContext(fs.readFileSync('queuego-map-web.js','utf8'),context);const Q=window.QueueGoMapWeb,eq=(a,b)=>{assert.deepEqual(a,b);checks++};
(async()=>{
 eq(await Q.searchPlaceCatalog(''),[]);eq(calls,0);
 const [a,b]=await Promise.all([Q.searchPlaceCatalog('7-Eleven'),Q.searchPlaceCatalog('บ้านโคก',{province:'นครราชสีมา'})]);eq(calls,2);eq(a[0].place_id,'place-1');eq(b[0].provider,'queuego-places');eq(a[0].persistable,true);
 eq(await Q.nearbyPlaces('not-a-category',15,103),null);const near=await Q.nearbyPlaces('grocery',15.2,102.4,5000);eq(near[0].distance,0);eq(calls,3);
 window.QueueGoMapWeb.configureSearch({});eq(await Q.search('no-configuration'),[]);
 console.log(JSON.stringify({checks,failures:0,scope:'shared QueueGo Places RPC for text, admin filters and nearby lookups; no regional static catalog fallback'}));
})().catch(e=>{console.error(e);process.exitCode=1});
