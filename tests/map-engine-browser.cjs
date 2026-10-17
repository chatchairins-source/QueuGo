// Real browser + actual QueueGo API/graph/vector data. No route or tile fixtures.
const {chromium}=require('playwright-core'),assert=require('assert'),fs=require('fs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.QUEUEGO_CHROME||chromium.executablePath(),headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 try{
 const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
 const page=await context.newPage(),errors=[],external=[];
 page.on('pageerror',e=>{errors.push(e.message);console.error('PAGE',e.message)});page.on('console',m=>{if(m.type()==='error')console.error('CONSOLE',m.text())});page.on('requestfailed',r=>console.error('REQUEST',r.url(),r.failure()));
 await page.route('**/*',r=>{if(!r.request().url().startsWith('http://127.0.0.1:8766/')&&!r.request().url().startsWith('blob:')){external.push(r.request().url());return r.abort()}return r.continue()});
 await page.goto('http://127.0.0.1:8766/map-view/',{waitUntil:'domcontentloaded'});
 await page.waitForFunction(()=>window.QueueGoMapView&&QueueGoMapView.ready,null,{timeout:45000});
 await page.waitForFunction(()=>QueueGoMapView.map.isSourceLoaded('buriram'),null,{timeout:45000});
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
 const report={...result,restoration_recomputed:true,retained_on_visibility:true,external_requests_blocked:external,page_errors:errors,tests:'actual Chromium, real graph and vector data; external requests blocked',physical_device:false};fs.writeFileSync('evidence/map-engine/browser-validation.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
