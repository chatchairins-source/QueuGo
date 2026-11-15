'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync('map-view/index.html','utf8');
const styleJs=fs.readFileSync('map-view/queuego-map-style.js','utf8');
const serviceJs=fs.readFileSync('map-view/queuego-map-service.js','utf8');
let checks=0;
const ok=(condition,label)=>{assert.ok(condition,label);checks++};
const equal=(a,b,label)=>{assert.equal(a,b,label);checks++};

const context=vm.createContext({
  location:{origin:'http://127.0.0.1:8766',hostname:'127.0.0.1'},
  AbortController,setTimeout,clearTimeout,
  fetch:async(url,options)=>{
    context.requests.push({url,options});
    return {ok:true,json:async()=>({engine:'QueueGo/Valhalla'})};
  },
  requests:[]
});
vm.runInContext(styleJs,context);
vm.runInContext(serviceJs,context);
const style=context.QueueGoMapStyle.create('http://127.0.0.1:8766/map-api/vector.pmtiles');
equal(style.sources.buriram.type,'vector','real vector source, not raster alias');
equal(style.sources.buriram.url,'pmtiles://http://127.0.0.1:8766/map-api/vector.pmtiles','real local PMTiles URL');
ok(style.layers.some(layer=>layer.source==='buriram'&&layer['source-layer']==='streets'),'real Shortbread road layers mounted');
ok(style.layers.some(layer=>layer.source==='buriram'&&layer['source-layer']==='water_polygons'),'real Shortbread water layers mounted');
ok(style.glyphs.startsWith('http://127.0.0.1:8766/map-assets/fonts/'),'local glyph origin');
equal(context.QueueGoMapStyle.create(null).sources.osm.type,'raster','existing online-map fallback remains supported');

const script=html.match(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi);
equal(script.length,1,'one real Map View application script');
new Function(script[0].replace(/^<script[^>]*>|<\/script>$/g,''));checks++;
equal((html.match(/src="queuego-map-service.js"/g)||[]).length,1,'Map Service script imported once');
equal((html.match(/src="queuego-map-style.js"/g)||[]).length,1,'Map Style script imported once');
ok(html.includes('QueueGoMapStyle.create(new URL(packageInfo.vector_url,location.href).href)'),'installed vector source created from service');
ok(html.includes('packageInfo.vector_url!=="/map-api/vector.pmtiles"'),'unexpected vector URL rejected');
ok(html.includes('maplibregl.addProtocol("pmtiles",protocol.tile)'),'real PMTiles protocol installed');
ok(html.includes('await restoreNavigation()'),'route restoration performed after map load');
ok(html.includes('try{await restoreNavigation()}finally{window.QueueGoMapView.restored=true}'),'restoration settles even on failure');
ok(html.includes('const NAVIGATION_STORAGE_KEY="queuego_map_navigation_v1"'),'route intent persisted');
ok(html.includes('if(typeof packageInfo!=="undefined"&&packageInfo)'),'standalone search runtime remains callable');
ok(html.includes('if(!packageInfo)window.QueueGoMapWeb?.attachPlaces?.(map);'),'Supabase POI overlay disabled when local package is installed');

(async()=>{
  const points=[{lat:14.9918756,lon:103.0994795},{lat:15.0088319,lon:103.1300059}];
  await context.QueueGoMapService.call('route',{points});
  equal(context.requests.length,1,'one local API request');
  equal(context.requests[0].url,'/map-api/route','no public OSRM call');
  equal(context.requests[0].options.method,'POST','same-origin JSON POST');
  equal(JSON.parse(context.requests[0].options.body).points.length,2,'coordinates retained');
  await assert.rejects(context.QueueGoMapService.call('https://untrusted.example',{points}),/Invalid map action/);checks++;

  const start=html.indexOf('    async function engineRoute(points){'),end=html.indexOf('    function drawMultiRoute(route){',start);
  ok(start>=0&&end>start,'route, matching, matrix and optimization functions found');
  const calls=[];
  const routes=vm.createContext({
    packageInfo:{vector_map:true},
    QueueGoMapService:{call:async(action,body)=>{calls.push({action,body});return {routes:[],matrix:[]}}},
    OSRM:'https://router.project-osrm.org/route/v1/driving/',
    fetchTimeout:()=>{throw Error('Public routing called despite installed engine')},
    routeCoordinateString:()=>{throw Error('Public routing URI constructed despite installed engine')}
  });
  vm.runInContext(html.slice(start,end)+'\nthis.api={engineRoute,engineMatchTrace,engineMatrix,engineOptimizeStops}',routes);
  await routes.api.engineRoute(points);
  await routes.api.engineMatchTrace(points);
  await routes.api.engineMatrix(points);
  await routes.api.engineOptimizeStops([...points,points[0]]);
  equal(calls.map(c=>c.action).join(','),'route,match,matrix,optimize-stops','all four actions stay on installed QueueGo Map Service');
  // Exercise the actual renderer fallback code, not an invented substitute.
  const fallbackStart=html.indexOf('      // Local renderer assets are present');
  const fallbackEnd=html.indexOf('      if(packageInfo.vector_url',fallbackStart);
  ok(fallbackStart>=0&&fallbackEnd>fallbackStart,'installed renderer fallback exists in Map View');
  const fallbackBody=html.slice(fallbackStart,fallbackEnd);
  const runFallback=new Function('document','globalThis','return (async()=>{'+fallbackBody+'})()');
  const paths=[],offlineGlobal={};
  await runFallback({
    createElement:kind=>({kind}),
    head:{appendChild(node){
      paths.push(node.href||node.src);
      if(node.src)offlineGlobal.maplibregl={Map:function(){}};
      node.onload();
    }}
  },offlineGlobal);
  equal(paths.join(','),'../map-assets/maplibre-gl.css,../map-assets/maplibre-gl.js','no CDN required when offline');
  ok(typeof offlineGlobal.maplibregl.Map==='function','MapLibre global initializes from local JS');
  const onlinePaths=[];
  await runFallback({
    createElement:kind=>({kind}),
    head:{appendChild(node){onlinePaths.push(node.href||node.src);node.onload()}}
  },{maplibregl:{Map:function(){}}});
  equal(onlinePaths.join(','),'../map-assets/maplibre-gl.css','existing CDN JS is retained without duplicate local JS fetch');
  await assert.rejects(runFallback({
    createElement:kind=>({kind}),
    head:{appendChild(node){node.onerror()}}
  },{}),/Installed MapLibre CSS unavailable/);
  checks++;
  console.log(JSON.stringify({checks,failures:0,scope:'installed PMTiles source, same-origin Map API, local routing and retained Web map fallback; not physical field certification'}));
})().catch(error=>{console.error(error);process.exitCode=1});
