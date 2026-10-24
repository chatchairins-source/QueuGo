const fs=require('fs'),assert=require('assert'),{JSDOM}=require('jsdom');
const dom=new JSDOM('<div id="map"></div>',{url:'https://example.test/QueuGo/',runScripts:'outside-only'});const w=dom.window;let calls=0;
w.QG_SEARCH_CONFIG={url:'https://qg.test',key:'publishable'};w.fetch=async(url,options)=>{calls++;assert(url.endsWith('/rest/v1/rpc/qg_search_places'));return{ok:true,json:async()=>[{place_id:'place-th',canonical_name:'บ้านโคก',name_th:'บ้านโคก',latitude:15.2,longitude:102.4,category:'place',source:'queuego-places'}]}};
w.eval(fs.readFileSync('queuego-map-web.js','utf8'));const Q=w.QueueGoMapWeb;let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
(async()=>{
 ok(Q.addPlaceSearchControl({})===null,'embedded map does not mount a search overlay');
 const results=await Q.search('บ้านโคก');ok(results[0].lat===15.2&&results[0].lng===102.4,'selected QueueGo Search result supplies map coordinates');
 const map={events:{},center:{lat:15.2,lng:102.4},bounds:{getNorth:()=>15.3,getEast:()=>102.5,contains:()=>true},on(n,f){this.events[n]=f;if(n==='load')f()},off(){},getBounds(){return this.bounds},getCenter(){return this.center},getZoom(){return 12},getSource(){return null},addSource(id,source){this.source=source},getLayer(){return null},addLayer(layer){this.layer=layer},removeLayer(){},removeSource(){}};
 Q.attachPlaces(map);await new Promise(r=>setTimeout(r,0));ok(calls===2,'map POI viewport shares QueueGo Search backend');ok(map.source.data.features[0].geometry.coordinates[0]===102.4,'map receives QueueGo Places coordinates');ok(!map.control,'map canvas receives no search control');map.__qgPlaces.dispose();
 console.log(JSON.stringify({checks,failures:0,scope:'map/search separation, shared nationwide RPC lookup and QueueGo Maps POI coordinates'}));
})().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>w.close());
