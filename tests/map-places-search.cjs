const fs=require('fs'),vm=require('vm'),assert=require('assert');
const catalog=JSON.parse(fs.readFileSync('data/maps/buriram-places.json','utf8'));
let reads=0,checks=0;const window={};const context=vm.createContext({window,console,WeakMap,Map,Set,Number,Math,Array,String,Object,URL,document:{currentScript:{src:'https://example.test/QueuGo/queuego-map-web.js'}},fetch:async url=>{
 if(url.includes('/QueuGo/data/maps/buriram-places.json')){reads++;return {ok:true,json:async()=>catalog}}
 if(url.includes('nominatim.openstreetmap.org'))return {ok:false,status:503,json:async()=>({})};
 if(url.includes('photon.komoot.io'))return {ok:true,json:async()=>({features:[{geometry:{coordinates:[103.09523,14.96691]},properties:{name:'สนามช้าง อินเตอร์เนชั่นแนล เซอร์กิต',city:'บุรีรัมย์',state:'บุรีรัมย์',country:'ประเทศไทย',osm_type:'W',osm_id:123}}]})};
 throw Error('unexpected fetch '+url);
}});
vm.runInContext(fs.readFileSync('queuego-map-web.js','utf8'),context);
const Q=window.QueueGoMapWeb,eq=(a,b)=>{assert.deepEqual(a,b);checks++};
(async()=>{
 const [a,b]=await Promise.all([Q.searchPlaceCatalog('7-Eleven'),Q.searchPlaceCatalog('7-eleven')]);eq(a.map(x=>x.id),b.map(x=>x.id));eq(reads,1);eq(a.every(x=>x.category==='grocery'),true);
 const named=catalog.records.find(x=>x.category==='food'&&x.name.length>4);
 const exact=await Q.searchPlaceCatalog(named.name);eq(exact[0].name,named.name);
 const local=await Q.search(named.name);eq(local[0].id,named.id);eq(local[0].label.includes(named.name),true);eq(reads,1);
 const fallback=await Q.search('fixture-provider-fallback-only',{center:{lat:14.99,lng:103.10},limit:6});eq(fallback[0].provider,'photon');eq(fallback[0].name,'สนามช้าง อินเตอร์เนชั่นแนล เซอร์กิต');eq([fallback[0].lng,fallback[0].lat],[103.09523,14.96691]);eq(reads,1);
 eq((await Q.searchPlaceCatalog('no-such-record-xyz-00000')).length,0);
 const near=await Q.nearbyPlaces('grocery',14.9955693,103.1079187,5000);eq(near.every(p=>p.category==='grocery'&&p.distance<=5000&&p.orderable===false),true);eq(near.length>0,true);
 eq(await Q.nearbyPlaces('not-a-category',15,103),null);
 eq((await Q.searchPlaceCatalog('')).length,0);
 const bounds=[103.10,14.99,103.11,15];const rows=await Q.searchPlaceCatalog('',{bounds,limit:100});eq(rows.every(x=>x.lng>=bounds[0]&&x.lat>=bounds[1]&&x.lng<=bounds[2]&&x.lat<=bounds[3]),true);
 console.log(JSON.stringify({checks,failures:0,scope:'actual Web search over real OSM catalog, aliases/case, one load, known nearby radius, no fabricated orderability'}));
})().catch(e=>{console.error(e);process.exitCode=1});
