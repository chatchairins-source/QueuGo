'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const root=require('node:path').resolve(__dirname,'..');
const source=fs.readFileSync(root+'/rider/index.html','utf8');
const start=source.indexOf('function qgRiderRegistrationPointValid(');
const end=source.indexOf('\nfunction riderStepGo(',start);
assert(start>=0&&end>start,'Rider registration location functions exist');
const elements={};
function node(){return {isConnected:true,hidden:false,disabled:false,value:'',children:[],listeners:{},textContent:'',append(...rows){this.children.push(...rows)},replaceChildren(){this.children=[]},addEventListener(name,fn){this.listeners[name]=fn}}}
for(const id of ['rr-registration-map','rr-location-status','rr-location-gps','rr-location-search','rr-location-search-button','rr-location-results'])elements[id]=node();
elements['rr-location-results'].hidden=true;elements['rr-location-search'].value='ตลาดภูเก็ต';
const events={},moves=[],map={center:{lat:15,lng:101},handlers:{},on(name,fn){this.handlers[name]=fn},getCenter(){return this.center}};
const Q={
  create:(host,options)=>{assert.equal(host,elements['rr-registration-map']);assert.equal(options.interactive,true);return map},
  setCenter:(target,lat,lng)=>{moves.push({lat,lng});target.center={lat,lng}},
  locate:async()=>({lat:18.7883,lng:98.9853}),
  destroy:target=>{events.destroyed=target===map},
  search:async query=>{assert.equal(query,'ตลาดภูเก็ต');return [
    {place_id:'phuket',name:'ตลาดภูเก็ต',label:'ตลาดภูเก็ต, ภูเก็ต',lat:7.8804,lng:98.3923},
    {place_id:'outside',name:'Outside Thailand',lat:35.6,lng:139.7}
  ]}
};
const document={getElementById:id=>elements[id]||null,createElement:()=>node()};
const context=vm.createContext({window:{QueueGoMapWeb:Q},document,Number,String,Array,Math,Promise,console});
vm.runInContext('let qgRiderRegistrationMap=null,qgRiderRegistrationPoint=null,qgRiderRegistrationSearchSerial=0;'+source.slice(start,end)+';globalThis.registrationApi={init:qgRiderRegistrationInitMap,set:qgRiderRegistrationSetPoint,dispose:qgRiderRegistrationDisposeMap,get:()=>qgRiderRegistrationPoint};',context);
const api=context.registrationApi;
assert.equal(api.init(),map);
elements['rr-location-search-button'].onclick();
setTimeout(async()=>{
  try{
    const results=elements['rr-location-results'];assert.equal(results.hidden,false);assert.equal(results.children.length,1);
    assert.equal(results.children[0].children[0].textContent,'ตลาดภูเก็ต');
    results.children[0].onclick();
    assert.deepEqual(JSON.parse(JSON.stringify(api.get())),{lat:7.8804,lng:98.3923});
    assert.deepEqual(moves.at(-1),{lat:7.8804,lng:98.3923});
    assert.match(elements['rr-location-status'].textContent,/ตลาดภูเก็ต/);
    map.center={lat:7.9,lng:98.4};map.handlers.dragstart();map.handlers.moveend();
    assert.deepEqual(JSON.parse(JSON.stringify(api.get())),{lat:7.9,lng:98.4});
    await elements['rr-location-gps'].onclick();
    assert.deepEqual(JSON.parse(JSON.stringify(api.get())),{lat:18.7883,lng:98.9853});
    assert.match(source,/latitude:qgRiderRegistrationPoint\?\.lat\?\?null,longitude:qgRiderRegistrationPoint\?\.lng\?\?null/);
    assert(source.indexOf('id="rr-location-search"')<source.indexOf('class="qg-registration-map"'));
    assert.match(source,/QueueGoMapWeb\?\.search|Q\.search\(query/);
    api.dispose();assert.equal(events.destroyed,true);
    console.log(JSON.stringify({checks:13,failures:0,scope:'Rider QueueGo Search selection, nationwide QueueGo Map pin/GPS persistence and map cleanup; no account signup or Production write'}));
  }catch(error){console.error(error);process.exitCode=1}
},0);
