const fs=require('fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const code=fs.readFileSync('queuego-rider-settings.js','utf8');
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
const flush=async()=>{for(let i=0;i<20;i++)await Promise.resolve()};
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {resolve,reject,promise}};
function boot(){
 const dom=new JSDOM('<button id="opener">ตั้งค่า</button>',{url:'https://queuego.invalid/rider/',runScripts:'outside-only'}),w=dom.window;
 const state={actor:'rider:session',calls:[],events:[],row:{configured:true,enabled:false,label:'บ้าน',latitude:15,longitude:103},center:{lat:15,lng:103},handlers:{},maps:0,searches:[]};
 w.eval(code);w.document.querySelector('button').focus();
 const deps={getActor:()=>state.actor,getAccessToken:async()=> 'isolated-token',toast:s=>state.events.push(s),
  rpc:async(name,body,token)=>{state.calls.push({name,body,token});if(name==='qg_rider_destination_preference')return state.row;return state.write?state.write(body):{configured:true,enabled:body.p_enabled,label:body.p_label,latitude:body.p_latitude,longitude:body.p_longitude}},
  map:{create:()=>{state.maps++;return {on:(key,fn)=>state.handlers[key]=fn,getCenter:()=>state.center}},destroy:()=>state.events.push('destroy'),locate:async()=>({lat:15.1,lng:103.1}),search:async(query,options)=>{state.searches.push({query,options});return state.searchRows||[]},setCenter:(map,lat,lng,zoom)=>{state.center={lat,lng};state.events.push(['center',lat,lng,zoom])}}
 };
 const open=()=>w.QueueGoRiderSettings.openDestination(deps),query=s=>w.document.querySelector(s),submit=()=>query('form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
 return {dom,w,state,deps,open,query,submit};
}
(async()=>{
 const t=boot();await t.open();eq(t.state.calls[0].name,'qg_rider_destination_preference');eq(t.query('[name=label]').value,'บ้าน');eq(t.query('[name=enabled]').checked,false);eq(t.query('[role=dialog]').getAttribute('aria-modal'),'true');
 // Reopening does not create another map or another request.
 await t.open();eq(t.state.maps,1);eq(t.state.calls.length,1);
 t.state.handlers.load();t.query('[name=enabled]').checked=true;
 const write=deferred();t.state.write=()=>write.promise;t.submit();await flush();t.submit();eq(t.state.calls.length,2);eq(t.query('[type=submit]').disabled,true);eq(t.query('[data-close]').disabled,true);
 t.state.center={lat:15.2,lng:103.2};t.state.handlers.moveend();
 const body=t.state.calls[1].body;eq(body.p_latitude,15);eq(body.p_longitude,103);eq(body.p_enabled,true);
 write.resolve({configured:true,enabled:true,label:'บ้าน',latitude:15,longitude:103});await flush();eq(t.query('[role=dialog]'),null);eq(t.state.events.includes('บันทึกจุดหมายแล้ว'),true);eq(t.w.document.activeElement.id,'opener');t.dom.window.close();
 const search=boot();await search.open();search.state.handlers.load();search.state.searchRows=[{place_id:'place-1',name:'บ้านโคก',lat:15.25,lng:102.5,provider:'queuego-places'}];search.query('[data-search-input]').value='บ้านโคก';search.query('[data-search]').click();await flush();eq(search.state.searches[0].query,'บ้านโคก');eq(search.query('[data-search-results] button').textContent,'บ้านโคก');search.query('[data-search-results] button').click();eq(search.state.events.some(x=>Array.isArray(x)&&x[0]==='center'&&x[1]===15.25&&x[2]===102.5),true);eq(search.query('[name=label]').value,'บ้านโคก');eq(search.query('[data-search-results]').hidden,true);search.query('[data-close]').click();search.dom.window.close();
 // A committed response lost in transport is reconciled by the same existing read RPC.
 const lost=boot();lost.state.write=async body=>{lost.state.row={configured:true,enabled:body.p_enabled,label:body.p_label,latitude:body.p_latitude,longitude:body.p_longitude};throw Error('lost response')};await lost.open();lost.submit();await flush();eq(lost.state.calls.map(x=>x.name),['qg_rider_destination_preference','qg_rider_set_destination_preference','qg_rider_destination_preference']);eq(lost.query('[role=dialog]'),null);lost.dom.window.close();
 // A rejected write never produces a saved message or mutates the acknowledged preference.
 const failed=boot();failed.state.write=async()=>{throw Error('network')};await failed.open();failed.query('[name=enabled]').checked=true;failed.submit();await flush();eq(failed.state.events.includes('บันทึกจุดหมายแล้ว'),false);eq(failed.state.row.enabled,false);eq(failed.query('[type=submit]').disabled,false);eq(failed.query('[data-message]').textContent.includes('ยังยืนยัน'),true);failed.query('[data-close]').click();failed.dom.window.close();
 // Stale account/token and close/dispose responses cannot write or render into another session.
 const stale=boot(),token=deferred();stale.deps.getAccessToken=()=>token.promise;const opening=stale.open();stale.state.actor='another';token.resolve('old-token');await opening;eq(stale.state.calls.length,0);stale.dom.window.close();
 const closed=boot(),read=deferred();closed.deps.rpc=()=>read.promise;const pending=closed.open();await flush();closed.query('[data-close]').click();read.resolve(closed.state.row);await pending;eq(closed.state.maps,0);eq(closed.query('[role=dialog]'),null);closed.dom.window.close();
 // Unconfigured destinations need an actual ready map and valid selected coordinates.
 const fresh=boot();fresh.state.row={configured:false,enabled:false};await fresh.open();fresh.query('[name=label]').value='จุดหมาย';fresh.query('[name=label]').dispatchEvent(new fresh.w.Event('input'));eq(fresh.query('[type=submit]').disabled,true);fresh.state.handlers.load();eq(fresh.query('[type=submit]').disabled,false);fresh.state.center={lat:0,lng:0};fresh.state.handlers.moveend();eq(fresh.query('[type=submit]').disabled,true);fresh.query('[data-close]').click();fresh.dom.window.close();
 for(const row of [{configured:true,enabled:true,label:'bad',latitude:null,longitude:103},{configured:true,enabled:true,label:'bad',latitude:91,longitude:103},null]){
  const bad=boot();bad.state.row=row;await bad.open();eq(bad.query('form').hidden,true);eq(bad.state.maps,0);bad.query('[data-close]').click();bad.dom.window.close();
 }
 // A GPS response after disposal cannot mutate the retired map.
 const gps=boot(),fix=deferred();gps.deps.map.locate=()=>fix.promise;await gps.open();gps.state.handlers.load();gps.query('[data-gps]').click();gps.query('[data-close]').click();fix.resolve({lat:15.1,lng:103.1});await flush();eq(gps.state.events.includes('center'),false);gps.dom.window.close();
 // Availability is never read or changed by a route preference screen.
 eq(code.includes('metadata.online'),false);eq(code.includes('qg_rider_set_destination_preference'),true);
 console.log(JSON.stringify({checks,failures:0,scope:'actual Web Settings DOM and existing destination RPC read/write/acknowledgement/reconciliation, duplicate-submit/session/map-disposal guards; isolated, not authenticated Production acceptance'}));
})().catch(e=>{console.error(e);process.exitCode=1});
