const http=require("http"),fs=require("fs"),path=require("path"),assert=require("assert");
const {webkit}=require("playwright-core");

const root=path.resolve(__dirname,"..");
const onePx=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGN49+rhfwAJPAO5kbCVAwAAAABJRU5ErkJggg==","base64");

function serve(){
  return new Promise(resolve=>{
    const server=http.createServer((req,res)=>{
      const url=new URL(req.url,"http://127.0.0.1");
      if(url.pathname==="/map-view/"||url.pathname==="/map-view/index.html"){
        res.writeHead(200,{"content-type":"text/html; charset=utf-8","cache-control":"no-store"});
        res.end(fs.readFileSync(path.join(root,"map-view/index.html")));
        return;
      }
      const file=path.resolve(root,"."+url.pathname);
      if((url.pathname.startsWith("/map-assets/")||url.pathname.startsWith("/map-view/")||url.pathname.startsWith("/data/maps/")||url.pathname==="/queuego-map-web.js")&&file.startsWith(root+path.sep)&&fs.existsSync(file)&&fs.statSync(file).isFile()){
        const type=file.endsWith(".js")?"application/javascript":file.endsWith(".css")?"text/css":"application/octet-stream";
        res.writeHead(200,{"content-type":type});res.end(fs.readFileSync(file));return;
      }
      res.writeHead(404);res.end("not found");
    });
    server.listen(0,"127.0.0.1",()=>resolve(server));
  });
}

(async()=>{
  const server=await serve();
  const port=server.address().port;
  const browser=await webkit.launch({headless:true});
  const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:2});
  const page=await context.newPage();
  let checks=0;
  const ok=(v,msg)=>{assert.ok(v,msg);checks++};

  await page.route("https://tile.openstreetmap.org/**",route=>route.fulfill({status:200,contentType:"image/png",body:onePx}));
  await page.addInitScript(() => {
    const nativeFetch=window.fetch.bind(window);
    window.fetch=async function(input,init){
      const url=String(typeof input==="string"?input:(input&&input.url)||"");
      if(url.includes("/rest/v1/rpc/qg_search_places")){
        const body=JSON.parse(init.body||"{}");
        const rows=String(body.p_query||"").toLowerCase().includes("7-eleven")
          ?[{place_id:"qg-711-phuket",canonical_name:"7-Eleven Phuket",name_th:"7-Eleven ภูเก็ต",latitude:7.8804,longitude:98.3923,province:"ภูเก็ต",source:"osm",source_license:"ODbL-1.0",verification_status:"verified"}]
          :[{place_id:"qg-phuket-market",canonical_name:"ตลาดภูเก็ต",name_th:"ตลาดภูเก็ต",latitude:7.8804,longitude:98.3923,province:"ภูเก็ต",source:"osm",source_license:"ODbL-1.0",verification_status:"verified"}];
        return new Response(JSON.stringify(rows),{status:200,headers:{"content-type":"application/json"}});
      }
      return nativeFetch(input,init);
    };
  });

  await page.goto("http://127.0.0.1:"+port+"/map-view/",{waitUntil:"domcontentloaded",timeout:30000});
  await page.waitForFunction(()=>window.QueueGoMapView&&window.QueueGoMapView.ready===true,null,{timeout:30000});
  const beforeFocus=await page.evaluate(()=>({
    center:window.QueueGoMapView.map.getCenter().toArray(),
    zoom:window.QueueGoMapView.map.getZoom(),
    fontSize:parseFloat(getComputedStyle(document.getElementById("placeSearchInput")).fontSize)
  }));
  await page.focus("#placeSearchInput");
  await page.waitForTimeout(250);
  const afterFocus=await page.evaluate(()=>({
    center:window.QueueGoMapView.map.getCenter().toArray(),
    zoom:window.QueueGoMapView.map.getZoom()
  }));
  ok(beforeFocus.fontSize>=16,"search input must stay at least 16px to prevent iPhone focus zoom");
  ok(Math.abs(afterFocus.zoom-beforeFocus.zoom)<0.000001,"focusing search must not change map zoom");
  ok(Math.abs(afterFocus.center[0]-beforeFocus.center[0])<0.000001&&Math.abs(afterFocus.center[1]-beforeFocus.center[1])<0.000001,"focusing search must not move map center");


  await page.fill("#placeSearchInput","fixture-provider-fallback-only");
  await page.click('#placeSearchForm button[type="submit"]');
  await page.waitForFunction(()=>document.querySelector("#placeSearchResults")&&!document.querySelector("#placeSearchResults").hidden&&document.querySelector("#placeSearchResults b")&&document.querySelector("#placeSearchResults b").textContent.includes("ตลาดภูเก็ต"),null,{timeout:10000});

  const search=await page.evaluate(()=>({
    result:document.querySelector("#placeSearchResults b").textContent,
    provider:window.QueueGoMapView.state.lastSearchProvider
  }));
  ok(search.result.includes("ตลาดภูเก็ต"),"nationwide result must render from QueueGo Places");
  ok(search.provider==="queuego-places","all address search must use QueueGo Places authority");

  await page.click("#placeSearchResults .qg-search-result");
  await page.waitForTimeout(650);
  const selected=await page.evaluate(()=>({
    center:window.QueueGoMapView.map.getCenter().toArray(),
    markerCount:document.querySelectorAll(".maplibregl-marker").length
  }));
  ok(Math.abs(selected.center[0]-98.3923)<0.02&&Math.abs(selected.center[1]-7.8804)<0.02,"selecting a Phuket result must move the map nationwide");
  ok(selected.markerCount>=1,"clicking a result must add a visible map marker");

  await page.fill("#placeSearchInput","7-Eleven");
  await page.click('#placeSearchForm button[type="submit"]');
  await page.waitForFunction(()=>window.QueueGoMapView.state.lastSearchProvider==="queuego-places",null,{timeout:10000});
  const localResult=await page.locator("#placeSearchResults b").first().textContent();
  ok(localResult.toLowerCase().includes("7-eleven"),"QueueGo Places result must contain the requested business");
  await page.fill("#placeSearchInput","14.993, 103.103");
  await page.click('#placeSearchForm button[type="submit"]');
  await page.waitForFunction(()=>document.querySelector("#placeSearchResults b")&&document.querySelector("#placeSearchResults b").textContent.includes("พิกัด"),null,{timeout:5000});
  const coordProvider=await page.evaluate(()=>window.QueueGoMapView.state.lastSearchProvider);
  ok(coordProvider==="coordinates","coordinate search must work without an external provider");

  await context.close();await browser.close();await new Promise(r=>server.close(r));
  console.log(JSON.stringify({checks,failures:0,scope:"QueueGo Map nationwide QueueGo Places search and result selection"}));
})().catch(err=>{console.error(err&&err.stack||err);process.exit(1)});
