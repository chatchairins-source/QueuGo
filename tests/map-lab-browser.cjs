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
    vw:innerWidth,
    vh:innerHeight,
    rect:document.getElementById("map").getBoundingClientRect().toJSON(),
    bodyChildren:[...document.body.children].map(x=>x.tagName+"#"+(x.id||""))
  }));
  const cameraGestures=await page.evaluate(()=>{
    const map=window.QueueGoMapLab.map;
    return {rotation:map.dragRotate.isEnabled(),touchRotation:map.touchZoomRotate.isEnabled(),pitch:map.touchPitch.isEnabled(),maxPitch:map.getMaxPitch()};
  });
  ok(cameraGestures.rotation&&cameraGestures.touchRotation&&cameraGestures.pitch&&cameraGestures.maxPitch>=45,"Map Lab must allow touch/mouse rotation and 3D tilt");
  ok(startup.rect.width===startup.vw&&Math.abs(startup.rect.height-startup.vh)<2,"map must fill the entire viewport");
  ok(startup.bodyChildren.filter(x=>x.startsWith("DIV#")).length===1,"only the map div may be present as page UI");

  const points=[[8,8],[60,120],[190,220],[370,320],[20,500],[200,650],[365,790]];
  const hits=await page.evaluate(points=>points.map(([x,y])=>{
    const map=document.getElementById("map"),hit=document.elementFromPoint(x,y);
    return {x,y,onMap:!!hit&&map.contains(hit)};
  }),points);
  ok(hits.every(p=>p.onMap),"every sampled screen point must hit the map");

  const before=await page.evaluate(()=>window.QueueGoMapLab.map.getCenter().toArray());
  await page.mouse.move(190,400);await page.mouse.down();await page.mouse.move(250,455,{steps:8});await page.mouse.up();
  await page.waitForTimeout(200);
  const after=await page.evaluate(()=>window.QueueGoMapLab.map.getCenter().toArray());
  ok(Math.abs(after[0]-before[0])>0.00005||Math.abs(after[1]-before[1])>0.00005,"drag must pan the map");

  const z0=await page.evaluate(()=>window.QueueGoMapLab.map.getZoom());
  await page.mouse.dblclick(190,400,{delay:60});
  await page.waitForTimeout(220);
  const z1=await page.evaluate(()=>window.QueueGoMapLab.map.getZoom());
  ok(z1>z0,"double tap/double click must zoom the map");

  await context.close();await browser.close();await new Promise(r=>server.close(r));
  console.log(JSON.stringify({checks,failures:0,scope:"bare full-screen mobile map pan/zoom hit-test"}));
})().catch(err=>{console.error(err&&err.stack||err);process.exitCode=1});
