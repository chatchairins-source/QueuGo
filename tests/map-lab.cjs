const fs=require("fs"),path=require("path"),assert=require("assert");
const html=fs.readFileSync(path.resolve(__dirname,"../map-lab/index.html"),"utf8");
let checks=0;
function ok(v,msg){assert.ok(v,msg);checks++}
function has(x,msg){ok(html.includes(x),msg||("missing "+x))}
function not(x,msg){ok(!html.includes(x),msg||("unexpected "+x))}

const inlineScripts=[...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1]).filter(Boolean);
ok(inlineScripts.length===1,"Map Lab must keep application JS inline in the single HTML file");
new Function(inlineScripts[0]);checks++;

has("<style>","inline CSS required");
not("./style.css","must not depend on split Map Lab CSS");
not("./app.js","must not depend on split Map Lab JS");
has("height:100dvh","map shell must use 100dvh");
has('id="map"',"map element required");
has('container:"map"',"MapLibre map must target full-screen map");
has('tiles:["https://tile.openstreetmap.org/{z}/{x}/{y}.png"]',"OSM base tiles required");
has("BURIRAM_CENTER=[103.1029,14.9930]","Buriram start view required");

not("<dialog","startup must not use modal/dialog UI");
not("qgm-panel","old blocking panel must be gone");
not("qgm-topbar","old blocking header must be gone");
has('class="search-pill"',"small floating search pill required");
has('id="layersButton"',"one floating layer button required");
has('id="locateButton"',"current-location button required");
has('id="sheet" class="sheet" data-state="collapsed"',"bottom sheet must start collapsed");
has("transform:translateY(calc(100% - var(--sheet-peek)))","bottom sheet must expose only collapsed peek initially");
has("bindSheetDrag","bottom sheet must support drag");
has('id="sheetClose"',"bottom sheet must be closable");
has('setSheet("collapsed")',"sheet collapse path required");

has("pointer-events:none","transparent UI wrappers must not steal map touches");
has(".search-pill,.fab,.search-results{pointer-events:auto}","only top controls opt back into pointer events");
has("touch-action:none","MapLibre canvas and drag handle interaction contract required");
has("map.touchZoomRotate.disableRotation()","touch zoom must remain enabled while rotation is disabled");
has('map.on("click",onMapClick)',"map taps must be handled only when route picking is active");
has("if(!state.pick)return","ordinary map taps must not trigger route selection");

has("loadOsmLayer","OSM layers must be lazy");
has("loadNgis","NGIS layer must be lazy");
has("loadDrr","DRR layer must be lazy");
has("loadOverture","Overture layer must be lazy");
not("loadOsmLayer(\"signals\");","signal layer must not auto-load on startup");
not("loadNgis();","NGIS must not auto-load on startup");
not("loadDrr();","DRR must not auto-load on startup");
not("loadOverture();","Overture must not auto-load on startup");
has("toast(","non-blocking toast status required");
has("pointer-events:none;position:fixed;z-index:20","toast/top wrappers are non-blocking");

has("<svg","controls must use SVG icons");
not("😀","emoji prohibited");
not("📍","emoji prohibited");
not("🗺","emoji prohibited");
not("⚙","emoji prohibited");

has('id="pickStart"',"route start tool required inside sheet");
has('id="pickEnd"',"route end tool required inside sheet");
has('id="routeButton"',"route calculation tool required inside sheet");
has("router.project-osrm.org","research route endpoint required");
has("signal_timing:\"unknown\"","traffic signal timing must remain unknown without controller feed");
not("maps.googleapis.com","must not ingest Google Maps");

has("env(safe-area-inset-top,0px)","top safe-area required");
has("env(safe-area-inset-bottom,0px)","bottom safe-area required");
has("@media(min-width:760px)","responsive desktop fallback required");

console.log(JSON.stringify({checks,failures:0,scope:"single-file QueueGo Map Lab map-first/lazy/mobile/touch contract"}));
