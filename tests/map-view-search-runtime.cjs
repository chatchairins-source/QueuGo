'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync('map-view/index.html','utf8');
const start=html.indexOf('    function coordinateSearch(query){');
const geocodeEnd=html.indexOf('    async function engineReverse(',start);
const markerStart=html.indexOf('    function searchMarkerElement(){',geocodeEnd);
const searchEnd=html.indexOf('    async function handleToolMapClick(',markerStart);
assert(start>=0&&geocodeEnd>start&&markerStart>geocodeEnd&&searchEnd>markerStart,'Map View QueueGo Search and selection functions exist');
const calls=[],flyTos=[],markers=[];
const elements={
  placeSearchResults:{innerHTML:'',hidden:true,children:[],appendChild(node){this.children.push(node)}},
  placeSearchInput:{value:''}
};
const map={getCenter:()=>({lat:14.99,lng:103.1}),flyTo:options=>flyTos.push(options)};
class Marker{
  constructor(options){this.options=options;markers.push(this)}
  setLngLat(point){this.point=point;return this}
  addTo(target){this.map=target;return this}
  remove(){this.removed=true}
}
const document={createElement(tag){return {tag,style:{},className:'',innerHTML:'',onclick:null}}};
const context=vm.createContext({
  window:{QueueGoMapWeb:{async search(query,options){calls.push({query,options});return [{place_id:'phuket-market',name:'ตลาดภูเก็ต',label:'ตลาดภูเก็ต, ภูเก็ต',lat:7.8804,lng:98.3923,provider:'queuego-places'}]}}},
  document,state:{searchMarker:null},map,maplibregl:{Marker},
  $:id=>elements[id],esc:value=>String(value).replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])),
  toast:()=>{},Number,String,Math,Array,Promise
});
vm.runInContext(`${html.slice(start,geocodeEnd)}${html.slice(markerStart,searchEnd)};globalThis.api={engineGeocode,searchPlaces,state};`,context);
(async()=>{
  const rows=await context.api.engineGeocode('ตลาดภูเก็ต');
  assert.equal(calls[0].query,'ตลาดภูเก็ต');
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0].options)),{latitude:14.99,longitude:103.1,limit:20});
  assert.equal(rows[0].provider,'queuego-places');assert.equal(rows[0].lon,'98.3923');assert.equal(context.api.state.lastSearchProvider,'queuego-places');
  await context.api.searchPlaces('ตลาดภูเก็ต');
  const host=elements.placeSearchResults;assert.equal(host.children.length,1);
  host.children[0].onclick();
  assert.deepEqual(JSON.parse(JSON.stringify(flyTos[0])),{center:[98.3923,7.8804],zoom:16,duration:500});
  assert.equal(markers.length,1);assert.deepEqual(JSON.parse(JSON.stringify(markers[0].point)),[98.3923,7.8804]);assert.equal(elements.placeSearchInput.value,'ตลาดภูเก็ต');assert.equal(host.hidden,true);
  console.log(JSON.stringify({checks:10,failures:0,scope:'Map View QueueGo Search query/ranking source and nationwide result-to-map pin behavior'}));
})().catch(error=>{console.error(error);process.exitCode=1});
