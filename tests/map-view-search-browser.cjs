const http=require("http"),fs=require("fs"),path=require("path"),assert=require("assert");
const {webkit}=require("playwright-core");

const root=path.resolve(__dirname,"..");
const onePx=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+ZQW7WQAAAABJRU5ErkJggg==","base64");

function serve(){
  return new Promise(resolve=>{
    const server=http.createServer((req,res)=>{
      const url=new URL(req.url,"http://127.0.0.1");
      if(url.pathname==="/map-view/"||url.pathname==="/map-view/index.html"){
        res.writeHead(200,{"content-type":"text/html; charset=utf-8","cache-control":"no-store"});
        res.end(fs.readFileSync(path.join(root,"map-view/index.html")));
        return;
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
      if(url.includes("nominatim.openstreetmap.org")){
        return new Response("{}",{status:503,headers:{"content-type":"application/json"}});
      }
      if(url.includes("photon.komoot.io")){
        return new Response(JSON.stringify({
          type:"FeatureCollection",
          features:[{
            type:"Feature",
            geometry:{type:"Point",coordinates:[103.09523,14.96691]},
            properties:{name:"สนามช้าง อินเตอร์เนชั่นแนล เซอร์กิต",city:"บุรีรัมย์",state:"บุรีรัมย์",country:"ประเทศไทย"}
          }]
        }),{status:200,headers:{"content-type":"application/json"}});
      }
      return nativeFetch(input,init);
    };
  });

  await page.goto("http://127.0.0.1:"+port+"/map-view/",{waitUntil:"domcontentloaded",timeout:30000});
  await page.waitForFunction(()=>window.QueueGoMapView&&window.QueueGoMapView.ready===true,null,{timeout:30000});

  await page.fill("#placeSearchInput","สนามช้าง");
  await page.click('#placeSearchForm button[type="submit"]');
  await page.waitForFunction(()=>document.querySelector("#placeSearchResults")&&!document.querySelector("#placeSearchResults").hidden&&document.querySelector("#placeSearchResults b")&&document.querySelector("#placeSearchResults b").textContent.includes("สนามช้าง"),null,{timeout:10000});

  const search=await page.evaluate(()=>({
    result:document.querySelector("#placeSearchResults b").textContent,
    provider:window.QueueGoMapView.state.lastSearchProvider
  }));
  ok(search.result.includes("สนามช้าง"),"search result must render from fallback provider");
  ok(search.provider==="photon","Photon must be used when Nominatim fails");

  await page.click("#placeSearchResults .qg-search-result");
  await page.waitForTimeout(650);
  const selected=await page.evaluate(()=>({
    center:window.QueueGoMapView.map.getCenter().toArray(),
    markerCount:document.querySelectorAll(".maplibregl-marker").length
  }));
  ok(Math.abs(selected.center[0]-103.09523)<0.02&&Math.abs(selected.center[1]-14.96691)<0.02,"clicking a result must move the map");
  ok(selected.markerCount>=1,"clicking a result must add a visible map marker");

  await page.fill("#placeSearchInput","14.993, 103.103");
  await page.click('#placeSearchForm button[type="submit"]');
  await page.waitForFunction(()=>document.querySelector("#placeSearchResults b")&&document.querySelector("#placeSearchResults b").textContent.includes("พิกัด"),null,{timeout:5000});
  const coordProvider=await page.evaluate(()=>window.QueueGoMapView.state.lastSearchProvider);
  ok(coordProvider==="coordinates","coordinate search must work without an external provider");

  await context.close();await browser.close();await new Promise(r=>server.close(r));
  console.log(JSON.stringify({checks,failures:0,scope:"QueueGo Map search fallback and result selection"}));
})().catch(err=>{console.error(err&&err.stack||err);process.exit(1)});
