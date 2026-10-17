const fs=require('fs'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom'),path=require('path');

(async()=>{
 let checks=0;
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};

 // Customer now uses QueueGo Map / MapLibre for every location picker.
 {
  const source=fs.readFileSync(path.resolve(__dirname,'../index.html'),'utf8');
  const dom=new JSDOM('<div id="map"></div><div id="other"></div>');
  const document=dom.window.document;
  let success,error,changes=0,created=0,removed=0,moves=0;
  const activeEvents=new Map();

  class MockMapLibreMap {
   constructor({container,center}){
    created++;
    this.container=container;
    this.center={lng:center[0],lat:center[1]};
    this.touchZoomRotate={disableRotation(){}};
    const node=document.createElement('canvas');node.className='qg-maplibre';container.appendChild(node);this.node=node;
   }
   addControl(){}
   on(name,fn){
    if(name==='load'){fn();return}
    activeEvents.set(name,fn);
   }
   off(name,fn){if(activeEvents.get(name)===fn)activeEvents.delete(name)}
   getCenter(){return this.center}
   easeTo({center}){moves++;this.center={lng:center[0],lat:center[1]}}
   remove(){removed++;activeEvents.clear();this.node?.remove()}
  }
  class MockMarker {
   setLngLat(){return this}
   addTo(){return this}
   remove(){}
  }
  const maplibregl={
   Map:MockMapLibreMap,Marker:MockMarker,
   NavigationControl:function(){},AttributionControl:function(){}
  };
  const ctx=vm.createContext({
   document,window:{maplibregl},maplibregl,
   navigator:{geolocation:{getCurrentPosition(s,e){success=s;error=e}}},
   toast(){},
   qgIsValidCoordinate:(a,b)=>a!==null&&a!==undefined&&b!==null&&b!==undefined,
   AbortSignal,
   fetch:async()=>({ok:true,json:async()=>({road:'Road'})}),
   encodeURIComponent,
   onChange:()=>changes++
  });
  const start=source.indexOf('let customerPickerMap=null;');
  const end=source.indexOf('async function openCustomerLocationPicker(');
  vm.runInContext(source.slice(start,end),ctx);
  const run=s=>vm.runInContext(s,ctx);

  const first=run('initLocationPicker("map",13,100,onChange)');
  eq(created,1);eq(changes,1);eq(activeEvents.has('moveend'),true);
  first.locate();first.destroy();first.destroy();
  const before=moves;
  success({coords:{latitude:14,longitude:101}});error({code:3});
  eq(moves,before);eq(changes,1);eq(activeEvents.size,0);eq(removed,1);

  const next=run('initLocationPicker("other",14,101,onChange)');
  eq(created,2);eq(next.map===first.map,false);eq(next.map.container.id,'other');
  next.locate();success({coords:{latitude:15,longitude:102}});
  eq(changes,3);eq(moves,1);
  next.destroy();eq(activeEvents.size,0);eq(removed,2);

  eq(await run('qtReverseGeocodeCustomer(13,100)'),'Road');
  let signal;ctx.fetch=async(_,opts)=>{signal=opts.signal;return {ok:false}};
  eq(await run('qtReverseGeocodeCustomer(13,100)'),'');
  eq(signal instanceof AbortSignal,true);
  dom.window.close();
 }

 // Merchant still uses its existing Longdo picker; keep lifecycle regression coverage.
 {
  const source=fs.readFileSync(path.resolve(__dirname,'../merchant/index.html'),'utf8');
  const dom=new JSDOM('<div id="map"></div><div id="other"></div>');
  const document=dom.window.document;
  let success,error,moved=0,changes=0,created=0;
  const events=new Map();
  function MockMap({placeholder}){
   created++;
   const node=document.createElement('div');placeholder.appendChild(node);
   this.placeholder=()=>node;this.pause=()=>{};this.resize=()=>{};
   this.Ui={LayerSelector:{visible(){}}};
   this.location=p=>{if(p)moved++;return p||{lat:13,lon:100}};
   this.zoom=()=>{};this.Overlays={add(){},remove(){},clear(){}};
   this.Event={bind:(e,f)=>events.set(e,f),unbind:(e,f)=>{if(events.get(e)===f)events.delete(e)}};
  }
  const longdo={Map:MockMap,Marker:function(){},OverlayWeight:{Top:1},UiComponent:{Mobile:1}};
  const ctx=vm.createContext({
   document,window:{longdo},longdo,
   navigator:{geolocation:{getCurrentPosition(s,e){success=s;error=e}}},
   toast(){},qgIsValidCoordinate:(a,b)=>a!==null&&a!==undefined&&b!==null&&b!==undefined,
   AbortSignal,fetch:async()=>({ok:true,json:async()=>({road:'Road'})}),encodeURIComponent,onChange:()=>changes++
  });
  const start=source.indexOf('let merchantPicker='),end=source.indexOf('/* ---------- Shop module catalog');
  vm.runInContext(source.slice(start,end),ctx);
  const run=s=>vm.runInContext(s,ctx),first=run('initLocationPicker("map",13,100,onChange)');
  eq(created,1);eq(changes,1);eq(events.size,1);
  first.locate();first.destroy();first.destroy();const before=moved;
  success({coords:{latitude:14,longitude:101}});error({code:3});
  eq(moved,before);eq(changes,1);eq(events.size,0);
  const next=run('initLocationPicker("other",14,101,onChange)');
  eq(created,1);eq(next.map,first.map);eq(next.map.placeholder().parentNode.id,'other');eq(events.size,1);
  next.locate();success({coords:{latitude:15,longitude:102}});
  eq(changes,3);next.destroy();eq(events.size,0);
  dom.window.close();
 }

 console.log(JSON.stringify({
  checks,failures:0,
  scope:'QueueGo MapLibre Customer picker + Merchant Longdo lifecycle, stale GPS and bounded customer geocoding; physical GPS not certified'
 }));
})().catch(e=>{console.error(e);process.exit(1)});
