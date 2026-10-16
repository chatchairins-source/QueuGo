const fs=require("fs"),path=require("path"),assert=require("assert");
const html=fs.readFileSync(path.resolve(__dirname,"../map-view/index.html"),"utf8");
let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};
const has=(x,m)=>ok(html.includes(x),m||("missing "+x));
const scripts=[...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1]).filter(Boolean);
ok(scripts.length===1,"Map View must keep one inline application script");
new Function(scripts[0]);checks++;

has('id="routePlanButton"',"route planning control required");
has("alternatives=true","routing must request alternatives");
has('node["highway"="traffic_signals"]',"route audit must query traffic signals");
has("countSignals","traffic signal counting required");
has("route.signalCount","signal count must be attached to each route");
has("เร็วที่สุด","fastest route label required");
has("ไฟแดงน้อยสุด","fewest-signals route label required");
has("ช้ากว่า","route time comparison required");
has("selectedRoute","selected recommendation state required");
has("route-options-selected","selected route rendering required");
has("OSRM","OSRM research endpoint required");
has("เวลาที่แสดงเป็นเวลาประมาณการ","UI must disclose non-live ETA");
ok(!html.includes("maps.googleapis.com"),"must not ingest Google Maps");

console.log(JSON.stringify({checks,failures:0,scope:"QueueGo route alternatives + traffic-signal comparison contract"}));
