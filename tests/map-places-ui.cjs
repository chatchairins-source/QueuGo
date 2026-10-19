const fs=require('fs'),assert=require('assert'),{JSDOM}=require('jsdom');
const catalog=JSON.parse(fs.readFileSync('data/maps/buriram-places.json'));
const dom=new JSDOM('<div id="map"></div>',{url:'https://example.test/QueuGo/',runScripts:'outside-only'});
const w=dom.window;let resolve;w.fetch=()=>new Promise(r=>{resolve=()=>r({ok:true,json:async()=>catalog})});
w.eval(fs.readFileSync('queuego-map-web.js','utf8'));const Q=w.QueueGoMapWeb;let checks=0;
const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++};const flush=async()=>{for(let i=0;i<20;i++)await Promise.resolve()};
(async()=>{
 const map={flyTo:x=>{map.last=x},getCenter:()=>({lat:14.9955693,lng:103.1079187}),addControl(c){this.control=c;w.document.getElementById('map').append(c.onAdd())}};
 let selected=null;Q.addPlaceSearchControl(map,{onSelect:p=>selected=p});
 const input=w.document.querySelector('input'),button=w.document.querySelector('button');
 input.value='7-Eleven';button.click();input.value='no-such-place-xyz';input.dispatchEvent(new w.Event('input'));resolve();await flush();
 eq(w.document.querySelectorAll('button').length,1,'late search cannot repaint stale input');
 input.value='7-Eleven';button.click();await flush();eq(w.document.querySelectorAll('button').length>1,true);
 w.document.querySelectorAll('button')[1].click();eq(selected.category,'grocery');eq(selected.orderable,false);eq(map.last.center,[selected.lng,selected.lat]);eq(input.value,selected.name);
 map.control.onRemove();eq(w.document.querySelectorAll('input').length,0);
 console.log(JSON.stringify({checks,failures:0,scope:'actual DOM local place search, stale result fence, source-backed coordinate selection and cleanup; no account/order writes'}));
})().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>w.close());
