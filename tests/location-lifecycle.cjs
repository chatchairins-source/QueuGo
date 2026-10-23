const fs=require('fs'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom'),path=require('path');

(async()=>{
 let checks=0;
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const root=path.resolve(__dirname,'..');
 const sharedMap=fs.readFileSync(root+'/queuego-map-web.js','utf8');
 eq(sharedMap.includes("new maplibregl.AttributionControl({compact:true})"),true);
 eq(sharedMap.includes("font:9px/13px"),true);
 eq(sharedMap.includes("width:18px!important"),true);
 eq(sharedMap.includes("searchControl=false"),true);
 eq(sharedMap.includes("attachPlaces(map,{searchControl:!!searchControl})"),true);

 function queueGoHarness(document){
  let success,error,created=0,removed=0,moves=0,changes=0;
  const events=new Map();
  class MockMap {
   constructor({container,center}){
    created++;
    this.container=container;
    this.center={lng:center[0],lat:center[1]};
    this.touchZoomRotate={disableRotation(){}};
    this.node=document.createElement('canvas');
    this.node.className='qg-maplibre';
    container.appendChild(this.node);
   }
   addControl(){}
   on(name,fn){events.set(name,fn);if(name==='load')fn()}
   once(name,fn){if(name==='load')fn();else events.set(name,fn)}
   off(name,fn){if(events.get(name)===fn)events.delete(name)}
   getCenter(){return this.center}
   easeTo({center}){moves++;this.center={lng:center[0],lat:center[1]}}
   fitBounds(){}
   isStyleLoaded(){return true}
   getLayer(){return null}
   getSource(){return null}
   addSource(){}
   addLayer(){}
   removeLayer(){}
   removeSource(){}
   remove(){removed++;events.clear();this.node?.remove()}
  }
  class MockMarker{
   constructor(){this.location=null}
   setLngLat(v){this.location=v;return this}
   addTo(){return this}
   remove(){}
  }
  class MockBounds{
   extend(){return this}
   isEmpty(){return false}
  }
  const maplibregl={
   Map:MockMap,Marker:MockMarker,LngLatBounds:MockBounds,
   NavigationControl:function(){},AttributionControl:function(){}
  };
  const QueueGoMapWeb={
   style:{version:8,sources:{},layers:[]},
   create(host,{lat=15,lng=101,zoom=6}={}){
    return new MockMap({container:host,center:[lng,lat],zoom});
   },
   marker(map,p){return new MockMarker().setLngLat([p.lng,p.lat]).addTo(map)},
   setCenter(map,lat,lng){map.easeTo({center:[lng,lat]})},
   locate(map,onPoint){
    return new Promise((resolve,reject)=>{
     if(!success&&!error){error=reject}
     success=p=>{const lat=p.coords.latitude,lng=p.coords.longitude;map.easeTo({center:[lng,lat]});onPoint?.(lat,lng);resolve({lat,lng})};
     error=e=>reject(e);
    });
   },
   reverse:async()=> 'Road',
   destroy(map){map?.remove?.()}
  };
  return {
   maplibregl,QueueGoMapWeb,events,
   get success(){return success},set success(v){success=v},
   get error(){return error},set error(v){error=v},
   get created(){return created},get removed(){return removed},
   get moves(){return moves},get changes(){return changes},inc(){changes++}
  };
 }

 // Customer location picker: QueueGo MapLibre only.
 {
  const source=fs.readFileSync(root+'/index.html','utf8');
  eq(source.includes('id="customer-location-search"'),true);
  eq(source.includes('window.QueueGoMapWeb?.attachPlaces?.(map,{onSelect:selectPlace,searchControl:false})'),true);
  const dom=new JSDOM('<div id="map"></div><div id="other"></div>');
  const document=dom.window.document;
  const h=queueGoHarness(document);
  let gpsSuccess,gpsError;
  const ctx=vm.createContext({
   document,
   window:{maplibregl:h.maplibregl,QueueGoMapWeb:h.QueueGoMapWeb},
   maplibregl:h.maplibregl,
   navigator:{geolocation:{getCurrentPosition(s,e){gpsSuccess=s;gpsError=e}}},
   toast(){},
   qgIsValidCoordinate:(a,b)=>a!==null&&a!==undefined&&b!==null&&b!==undefined,
   AbortSignal,encodeURIComponent,onChange:()=>h.inc()
  });
  const start=source.indexOf('let customerPickerMap=null;');
  const end=source.indexOf('async function openCustomerLocationPicker(');
  vm.runInContext(source.slice(start,end),ctx);
  const run=s=>vm.runInContext(s,ctx);

  const first=run('initLocationPicker("map",13,100,onChange)');
  eq(h.created,1);eq(h.changes,1);eq(h.events.has('click'),true);eq(h.events.has('moveend'),false);
  h.events.get('click')({lngLat:{lat:13.5,lng:100.5}});eq(h.changes,2);
  first.locate();first.destroy();first.destroy();
  const before=h.moves;
  gpsSuccess({coords:{latitude:14,longitude:101}});
  gpsError({code:3});
  eq(h.moves,before);eq(h.changes,2);eq(h.events.size,0);eq(h.removed,1);

  const next=run('initLocationPicker("other",14,101,onChange)');
  eq(h.created,2);eq(next.map===first.map,false);eq(next.map.container.id,'other');
  next.locate();gpsSuccess({coords:{latitude:15,longitude:102}});
  eq(h.changes,4);eq(h.moves,1);
  next.destroy();eq(h.events.size,0);eq(h.removed,2);
  eq(await run('qtReverseGeocodeCustomer(13,100)'),'Road');
  dom.window.close();
 }

 // Merchant location picker now shares QueueGo MapLibre lifecycle.
 {
  const source=fs.readFileSync(root+'/merchant/index.html','utf8');
  eq(source.includes('id="qgs-location-search"'),true);
  eq(source.includes("Q.create(host,{lat:viewLat,lng:viewLng,zoom:hasInitial?15:6,searchControl:false})"),true);
  const dom=new JSDOM('<div id="map"></div><div id="other"></div>');
  const document=dom.window.document;
  const h=queueGoHarness(document);
  let gpsSuccess,gpsError;
  h.QueueGoMapWeb.locate=(map,onPoint)=>new Promise((resolve,reject)=>{
   gpsSuccess=p=>{const lat=p.coords.latitude,lng=p.coords.longitude;map.easeTo({center:[lng,lat]});onPoint?.(lat,lng);resolve({lat,lng})};
   gpsError=reject;
  });
  const ctx=vm.createContext({
   document,
   window:{maplibregl:h.maplibregl,QueueGoMapWeb:h.QueueGoMapWeb},
   maplibregl:h.maplibregl,
   navigator:{geolocation:{getCurrentPosition(s,e){gpsSuccess=s;gpsError=e}}},
   toast(){},
   qgIsValidShopCoordinate:(a,b)=>a!==null&&a!==undefined&&b!==null&&b!==undefined,
   qgShopCoordinateProblem:()=> '',
   onChange:()=>h.inc()
  });
  const start=source.indexOf('let merchantPicker=null;');
  const end=source.indexOf('/* ---------- Shop module catalog');
  vm.runInContext(source.slice(start,end),ctx);
  const run=s=>vm.runInContext(s,ctx);

  const first=run('initLocationPicker("map",13,100,onChange)');
  eq(h.created,1);eq(h.changes,1);eq(h.events.has('moveend'),true);
  first.locate();gpsSuccess({coords:{latitude:14,longitude:101}});
  await Promise.resolve();eq(h.changes,2);
  first.destroy();first.destroy();eq(h.removed,1);eq(h.events.size,0);

  const next=run('initLocationPicker("other",14,101,onChange)');
  eq(h.created,2);eq(next.map===first.map,false);eq(next.map.container.id,'other');
  next.destroy();eq(h.removed,2);
  dom.window.close();
 }

 console.log(JSON.stringify({
  checks,failures:0,
  scope:'QueueGo MapLibre Customer + Merchant picker lifecycle, stale GPS cleanup and shared reverse geocoding; physical GPS not certified'
 }));
})().catch(e=>{console.error(e);process.exit(1)});
