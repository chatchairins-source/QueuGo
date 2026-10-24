const fs=require("fs"),path=require("path"),assert=require("assert");
const html=fs.readFileSync(path.resolve(__dirname,"../map-lab/index.html"),"utf8");
let checks=0;
function ok(v,msg){assert.ok(v,msg);checks++}
function has(v,msg){ok(html.includes(v),msg||("missing "+v))}
function not(v,msg){ok(!html.includes(v),msg||("unexpected "+v))}

const scripts=[...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1]).filter(Boolean);
ok(scripts.length===1,"Map Lab must keep one inline application script");
new Function(scripts[0]);checks++;

has('id="map"',"map element required");
has("height:100dvh","map must fill dynamic viewport height");
has('container:"map"',"MapLibre must target the full-screen map");
has('tiles:["https://tile.openstreetmap.org/{z}/{x}/{y}.png"]',"OSM base map required");
has("BURIRAM_CENTER=[103.1029,14.9930]","Buriram start view required");
has("map.touchZoomRotate.enableRotation()","touch rotation must be enabled");
has("map.touchPitch.enable()","touch pitch must be enabled");
has("NavigationControl({showCompass:true,showZoom:false,visualizePitch:true})","compass and pitch controls must be visible");

not("search-pill","no search overlay on startup");
not("bottom sheet","no bottom-sheet copy");
not('id="sheet"',"no bottom sheet element");
not("qgm-panel","no technical panel");
not("qgm-route","no route card");
not("<button","no floating buttons");
not("<dialog","no modal/dialog");
not("position:absolute;z-index:","no custom overlay UI");
not("maps.googleapis.com","must not ingest Google Maps");

console.log(JSON.stringify({checks,failures:0,scope:"bare full-screen QueueGo Map Lab contract"}));
