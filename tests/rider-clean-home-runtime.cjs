const fs=require('fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom'),acorn=require('acorn');
const source=fs.readFileSync(process.env.QG_RIDER_SOURCE||'rider/index.html','utf8');
const script=[...source.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1]).find(x=>x.includes('function renderHomeDock()'));
const ast=acorn.parse(script,{ecmaVersion:'latest'});
const names=['renderHomeDock','qgSetHomeDockHTML','qgApplyHomeDockCollapse','updateStatusUI','toggleOnline','qgSyncRiderAvailability'];
const code=names.map(name=>{const n=ast.body.find(n=>n.type==='FunctionDeclaration'&&n.id.name===name);assert(n,name);return script.slice(n.start,n.end)}).join('\n');
let checks=0;function eq(a,b){assert.deepEqual(a,b);checks++}
(async()=>{
const dom=new JSDOM('<main id="home-dock"></main>',{url:'https://queuego.invalid/rider/',runScripts:'outside-only'}),w=dom.window;
let writes=0,ack,fail=false;const bodies=[];
Object.assign(w,{S:{user:{id:'r'},session:{sessionId:'s'},riderProfile:{id:'rp',metadata:{online:false,available:false,other:'preserve'}},online:false,openJobs:[],history:[]},
 toast(){},getAccessToken:async()=> 'test-only',refreshData:async()=>{},requestAnimationFrame:f=>f(),qgFollowRiderCamera(){},
 sbTable:async(path,opts)=>{writes++;bodies.push(opts.body);if(fail)throw Error('lost network');if(ack)await ack;return [{id:'rp',metadata:opts.body.metadata}]},
 jobCardHTML:()=>'<article id="active-job">Current work</article>',qgRouteBundleHTML:()=>'',bindJobCardEvents(){},qgBindRouteBundleUI(){},qgLaundryPoolHTML:()=>''});
w.eval('let riderAvailabilityBusy=false,riderAvailabilityRevision=0,riderHomeDockCollapsed=false,riderRouteOverviewActive=false;let qgActiveItemPreview={orderId:null};function qgLoadActiveItemPreview(){}function qgResetActiveItemPreview(){qgActiveItemPreview={orderId:null}};\n'+code);
w.renderHomeDock();eq(w.document.querySelectorAll('[role=switch]').length,1);eq(w.document.querySelectorAll('#go-online,#go-offline,.qg-home-stats').length,0);
eq(w.document.getElementById('status-switch').getAttribute('aria-checked'),'false');
let release;ack=new Promise(r=>release=r);const pending=w.toggleOnline();await Promise.resolve();await w.toggleOnline();eq(writes,1);eq(w.S.online,false);eq(w.document.getElementById('status-switch').disabled,true);release();await pending;ack=null;
eq(w.S.online,true);eq(bodies[0].metadata.available,true);eq(bodies[0].metadata.other,'preserve');eq(w.document.querySelector('[role=switch]').getAttribute('aria-checked'),'true');
w.renderHomeDock();eq(w.document.querySelectorAll('[role=switch]').length,1);
fail=true;await w.toggleOnline();eq(w.S.online,true);eq(w.document.querySelector('[role=switch]').getAttribute('aria-checked'),'true');fail=false;
await w.toggleOnline();eq(w.S.online,false);eq(bodies.at(-1).metadata.available,false);
w.S.activeOrder={id:'active'};w.renderHomeDock();await w.toggleOnline();eq(w.S.activeOrder.id,'active');eq(writes,3);eq(!!w.document.getElementById('active-job'),true);
w.S.activeOrder=null;let end;ack=new Promise(r=>end=r);const old=w.toggleOnline();await Promise.resolve();w.S.user={id:'different'};end();await old;eq(w.S.online,false);
// An older profile read must not overwrite a newer acknowledged switch mutation.
Object.defineProperty(w.document,'hidden',{value:false});
w.S.user={id:'r'};w.S.activeTab='home';ack=null;
let resolveRead;const readWait=new Promise(r=>resolveRead=r),writeTransport=w.sbTable;
w.sbTable=async(path,opts)=>opts?.method==='PATCH'?writeTransport(path,opts):readWait;
const staleRead=w.qgSyncRiderAvailability();await Promise.resolve();await w.toggleOnline();eq(w.S.online,true);
resolveRead([{id:'rp',metadata:{online:false,available:false}}]);await staleRead;eq(w.S.online,true);
const native=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
eq(native.includes('RiderBlueprintTopBar('),false);eq(native.includes('Text("เปิดรับงาน",'),false);eq(native.includes('Text("ปิดรับงาน",'),false);
const home=native.slice(native.indexOf('BoxWithConstraints('),native.indexOf('private fun RiderReadyCard'));
eq(home.includes('RiderAcceptanceModeControl('),false);eq(home.includes('RiderHomeActionCards('),false);eq(native.includes('workSettings = {\n                        RiderAcceptanceModeControl('),true);
console.log(JSON.stringify({checks,failures:0,scope:'actual Home DOM/switch acknowledgement, coalescing, rollback, session fencing, current job preservation and Native single-control placement; isolated only'}));
})().catch(e=>{console.error(e);process.exit(1)});
