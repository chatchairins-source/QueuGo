const http=require("http"),fs=require("fs"),path=require("path"),assert=require("assert");
const {webkit}=require("playwright-core");

const root=path.resolve(__dirname,"..");
const onePx=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+ZQW7WQAAAABJRU5ErkJggg==","base64");

function serve(){
  return new Promise(resolve=>{
    const server=http.createServer((req,res)=>{
      const url=new URL(req.url,"http://127.0.0.1");
      if(url.pathname==="/map-lab/"||url.pathname==="/map-lab/index.html"){
        res.writeHead(200,{"content-type":"text/html; charset=utf-8","cache-control":"no-store"});
        res.end(fs.readFileSync(path.join(root,"map-lab/index.html")));
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
  await page.goto("http://127.0.0.1:"+port+"/map-lab/",{waitUntil:"domcontentloaded",timeout:30000});
  await page.waitForFunction(()=>window.QueueGoMapLab&&window.QueueGoMapLab.ready===true,null,{timeout:30000});

  const startup=await page.evaluate(()=>({
    vw:innerWidth,vh:innerHeight,
    map:document.getElementById("map").getBoundingClientRect().toJSON(),
    sheet:document.getElementById("sheet").getBoundingClientRect().toJSON(),
    sheetState:document.getElementById("sheet").dataset.state,
    searchHidden:document.getElementById("searchResults").hidden,
    toastVisible:document.getElementById("toast").classList.contains("show"),
    start:window.QueueGoMapLab.state.start,
    end:window.QueueGoMapLab.state.end
  }));
  ok(startup.map.width===startup.vw&&Math.abs(startup.map.height-startup.vh)<2,"map must fill the mobile viewport");
  ok(startup.sheetState==="collapsed","bottom sheet must start collapsed");
  ok(startup.sheet.top>=startup.vh-78,"collapsed sheet may expose only a small peek");
  ok(startup.searchHidden,"search result overlay must be hidden on startup");
  ok(!startup.toastVisible,"toast must not block startup");
  ok(startup.start===null&&startup.end===null,"route selection must be inactive on startup");

  const sampled=await page.evaluate(()=>{
    const controls=[...document.querySelectorAll(".search-pill,.fab,.sheet")].map(el=>el.getBoundingClientRect());
    const map=document.getElementById("map");
    const points=[];
    for(const y of [105,210,320,430,540,650,745]){
      for(const x of [24,95,170,245,320,370]){
        const blocked=controls.some(r=>x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom);
        if(blocked)continue;
        const hit=document.elementFromPoint(x,y);
        points.push({x,y,onMap:!!hit&&map.contains(hit),tag:hit&&hit.tagName,cls:hit&&hit.className});
      }
    }
    return points;
  });
  ok(sampled.length>=20,"touch grid must cover the unobstructed viewport");
  ok(sampled.every(p=>p.onMap),"every sampled non-control point must hit the map, not a transparent overlay");

  const before=await page.evaluate(()=>window.QueueGoMapLab.map.getCenter().toArray());
  await page.mouse.move(190,400);
  await page.mouse.down();
  await page.mouse.move(245,445,{steps:8});
  await page.mouse.up();
  await page.waitForTimeout(220);
  const after=await page.evaluate(()=>window.QueueGoMapLab.map.getCenter().toArray());
  ok(Math.abs(after[0]-before[0])>0.00005||Math.abs(after[1]-before[1])>0.00005,"dragging an unobstructed map point must pan the map");

  const zoom0=await page.evaluate(()=>window.QueueGoMapLab.map.getZoom());
  await page.mouse.dblclick(190,400,{delay:60});
  await page.waitForTimeout(260);
  const zoom1=await page.evaluate(()=>window.QueueGoMapLab.map.getZoom());
  ok(zoom1>zoom0,"double-tap/double-click area must zoom the map");

  await page.mouse.click(190,400);
  await page.waitForTimeout(100);
  const ordinaryTap=await page.evaluate(()=>({start:window.QueueGoMapLab.state.start,end:window.QueueGoMapLab.state.end,pick:window.QueueGoMapLab.pickMode}));
  ok(ordinaryTap.start===null&&ordinaryTap.end===null&&ordinaryTap.pick===null,"ordinary map taps must not silently start route selection");

  const handle=page.locator("#sheetHandle");
  const box=await handle.boundingBox();
  ok(!!box,"sheet drag handle must be visible");
  await page.mouse.move(box.x+70,box.y+22);
  await page.mouse.down();
  await page.mouse.move(box.x+70,box.y-250,{steps:10});
  await page.mouse.up();
  await page.waitForTimeout(180);
  const raised=await page.locator("#sheet").getAttribute("data-state");
  ok(raised==="half"||raised==="expanded","bottom sheet must drag upward to an open snap point");

  await page.locator("#sheetClose").click();
  await page.waitForTimeout(160);
  const closed=await page.locator("#sheet").getAttribute("data-state");
  ok(closed==="collapsed","sheet close control must return to collapsed state");

  await context.close();await browser.close();await new Promise(r=>server.close(r));
  console.log(JSON.stringify({checks,failures:0,scope:"physical WebKit mobile hit-test, pan, zoom and bottom-sheet gestures"}));
})().catch(err=>{
  console.error(err&&err.stack||err);
  process.exitCode=1;
});
