// Real browser + actual QueueGo API/graph/vector data. No route or tile fixtures.
const {chromium}=require('playwright-core'),assert=require('assert'),fs=require('fs'),crypto=require('node:crypto');
// The browser remains network-isolated; it receives real, SHA-256-verified MapLibre bytes
// from the same pinned asset bundle used by the local map service.
const webAssets=JSON.parse(fs.readFileSync('ops/maps/web-assets.json','utf8')).files;
const pinnedMaplibre=new Map();
for(const name of ['maplibre-gl.css','maplibre-gl.js']){
 const url='https://unpkg.com/maplibre-gl@5.7.1/dist/'+name;
 const entry=webAssets.find(asset=>asset.path===name);
 assert(entry&&entry.url===url,'MapLibre asset must have an exact pinned source: '+name);
 const body=fs.readFileSync('map-assets/'+name);
 assert.equal(body.length,entry.bytes,'MapLibre asset byte length mismatch: '+name);
 assert.equal(crypto.createHash('sha256').update(body).digest('hex'),entry.sha256,'MapLibre asset checksum mismatch: '+name);
 pinnedMaplibre.set(url,{body,contentType:name.endsWith('.css')?'text/css':'application/javascript'});
}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.QUEUEGO_CHROME||chromium.executablePath(),headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 try{
 const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
 const page=await context.newPage(),errors=[],external=[],pinnedServed=new Set();
 page.on('pageerror',e=>{errors.push(e.message);console.error('PAGE',e.message)});page.on('console',m=>{if(m.type()==='error')console.error('CONSOLE',m.text())});page.on('requestfailed',r=>console.error('REQUEST',r.url(),r.failure()));
 await page.route('**/*',r=>{
 const url=r.request().url(),asset=pinnedMaplibre.get(url);
 if(asset){pinnedServed.add(url);return r.fulfill({status:200,body:asset.body,contentType:asset.contentType});}
 if(!url.startsWith('http://127.0.0.1:8766/')&&!url.startsWith('blob:')){external.push(url);return r.abort();}
 return r.continue();
});
 await page.goto('http://127.0.0.1:8766/map-view/',{waitUntil:'domcontentloaded'});
 await page.waitForFunction(()=>window.QueueGoMapView&&QueueGoMapView.ready,null,{timeout:45000});
 assert.deepEqual([...pinnedServed].sort(),[...pinnedMaplibre.keys()].sort(),'Browser must load both verified local MapLibre assets');
 // Fail immediately when the *application* has not installed the real offline Buriram
 // vector source. A raster OSM fallback cannot satisfy the PMTiles render gate.
 const sourceIds=await page.evaluate(()=>Object.keys(QueueGoMapView.map.getStyle()?.sources||{}));
 assert(sourceIds.includes('buriram'),'Map View must mount actual Buriram PMTiles source; found: '+sourceIds.join(', '));
 await page.waitForFunction(()=>QueueGoMapView.map.getSource('buriram')&&QueueGoMapView.map.isSourceLoaded('buriram'),null,{timeout:45000});
 const result=await page.evaluate(async()=>{
  const points=[{lat:14.9918756,lon:103.0994795},{lat:15.0088319,lon:103.1300059}];
  const route=await QueueGoMapEngine.route(points),matrix=await QueueGoMapEngine.routeMatrix(points),nearest=await QueueGoMapEngine.nearestRoad(points[0].lat,points[0].lon),geocode=await QueueGoMapEngine.geocode('บุรีรัมย์');
  const identity=QueueGoMapView.map;await new Promise(r=>setTimeout(r,200));
  return {distance:route.routes[0].distance,engine:route.data.engine,matrix:matrix.durations,nearest:nearest.osm_way_id,places:geocode.length,sameMap:identity===QueueGoMapView.map,pitch:identity.getPitch(),bearing:identity.getBearing(),rendered:identity.queryRenderedFeatures().length};
 });
 assert(result.distance>1000&&result.engine==='QueueGo/Valhalla');assert(result.matrix[0][1]>0);assert(result.nearest&&result.places>0);assert(result.sameMap&&result.pitch===0&&result.bearing===0);assert(result.rendered>0,'actual local PMTiles features must render');assert.deepEqual(errors,[]);
 await page.evaluate(()=>{const view=QueueGoMapView,identity=view.map;document.dispatchEvent(new Event('visibilitychange'));if(identity!==QueueGoMapView.map)throw Error('visibility recreated map');view.state.routeStart={lat:14.9918756,lon:103.0994795};view.state.routeEnd={lat:15.0088319,lon:103.1300059}});
 await page.reload({waitUntil:'domcontentloaded'});
 await page.waitForFunction(()=>window.QueueGoMapView&&QueueGoMapView.restored&&QueueGoMapView.state.routes.length>0,null,{timeout:45000});
 const restored=await page.evaluate(()=>({engine:QueueGoMapView.state.routes[0].data.engine,routeId:QueueGoMapView.state.routes[0].route_id,routeFeatures:QueueGoMapView.map.getSource('route-options')._data.features.length}));
 assert(restored.engine==='QueueGo/Valhalla'&&restored.routeId&&restored.routeFeatures>0);
 await page.evaluate(()=>document.fonts.ready);
 await page.screenshot({path:'/tmp/queuego-map-engine-browser.png'});
 const report={...result,restoration_recomputed:true,retained_on_visibility:true,verified_maplibre_assets_served_locally:[...pinnedServed].sort(),external_requests_blocked:external,page_errors:errors,tests:'actual Chromium, real graph, verified local MapLibre and vector data; external requests blocked',physical_device:false};fs.writeFileSync('evidence/map-engine/browser-validation.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
