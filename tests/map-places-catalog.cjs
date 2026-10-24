const fs=require('fs'),assert=require('assert');let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
const source=fs.readFileSync('queuego-map-web.js','utf8');
ok(!source.includes('buriram-places.json'),'runtime must not load a Buriram-only static POI catalog');
ok(!source.includes('legacy-local-catalog'),'search must fail closed rather than use a regional fallback');
ok(source.includes("'/rest/v1/rpc/qg_search_places'"),'map and search lookups use the shared QueueGo Places authority');
ok(source.includes('searchQueueGoPlaces(\'\',{latitude:center.lat,longitude:center.lng'),'map POIs are loaded from nationwide QueueGo Places near the viewport');
console.log(JSON.stringify({checks,failures:0,scope:'no static regional POI catalog; shared QueueGo Places viewport lookup'}));
