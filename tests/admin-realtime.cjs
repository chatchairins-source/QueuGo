const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom');
const code=fs.readFileSync(path.resolve(__dirname,'../role-realtime.js'),'utf8');
const tick=()=>new Promise(r=>setImmediate(r));
(async()=>{let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
const d=new JSDOM('<main></main>',{url:'https://queuego.test/admin/#admin',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;
let session=null,online=true,hidden=false,auth=[],channels=[],options=[],removed=0,reads=0,renders=0,gate=null,release;
Object.defineProperty(w.navigator,'onLine',{get:()=>online});Object.defineProperty(w.document,'hidden',{get:()=>hidden});
const timers=new Map(),intervals=[];let id=0;w.setTimeout=(fn,delay)=>{timers.set(++id,{fn,delay});return id};w.clearTimeout=n=>timers.delete(n);w.setInterval=fn=>intervals.push(fn);
const run=async delay=>{const entries=[...timers].filter(([,t])=>t.delay===delay);for(const [n,t] of entries){timers.delete(n);t.fn()}for(let i=0;i<8;i++)await tick()};
w.qtSessionRead=()=>session;w.qtGetAccessToken=async()=>session?.accessToken;w.QT_ADMIN_SESSION_KEY='admin-session';w.QT_SUPABASE_CONFIG={URL:'https://test.invalid',KEY:'fixture'};w.QT_SYNC={busy:false,pending:0,lastWrite:0,dirty:{}};w.QT_DB_HYDRATING=false;w.QT_DB_CACHE={};w.qtSyncSignature=()=>reads;w.qtSyncRerender=()=>renders++;w.qtHydrateDatabase=async()=>{reads++;if(gate)await gate};
w.supabase={createClient:(url,key,opts)=>{options.push(opts);return {realtime:{setAuth:async token=>auth.push(token)},removeAllChannels:async()=>{},removeChannel:async()=>{removed++},channel:()=>{const c={events:[],on(...a){c.events.push(a);return c},subscribe(cb){c.cb=cb;return c}};channels.push(c);return c}}}};
vm.runInContext(code,d.getInternalVMContext());await run(800);eq(channels.length,0);eq(timers.size,0);
session={role:'customer',authUserId:'a',userId:'u',accessToken:'token'};w.QT_REALTIME_FORCE_REFRESH();await tick();eq(channels.length,0);
session.role='admin';w.QT_REALTIME_FORCE_REFRESH();for(let i=0;i<8;i++)await tick();eq(channels.length,1);eq(auth.at(-1),'token');eq(options[0].auth.persistSession,false);eq(options[0].auth.autoRefreshToken,false);eq(channels[0].events.length,16);
channels[0].cb('SUBSCRIBED');for(let i=0;i<4;i++)channels[0].events[0][2]({eventType:'UPDATE'});eq([...timers.values()].filter(t=>t.delay===600).length,1);await run(600);eq(reads,1);eq(renders,1);
w.QT_SYNC.busy=true;channels[0].events[0][2]({eventType:'UPDATE'});await run(600);eq(reads,1);eq([...timers.values()].filter(t=>t.delay===600).length,1);w.QT_SYNC.busy=false;await run(600);eq(reads,2);
gate=new Promise(r=>release=r);channels[0].events[0][2]({eventType:'UPDATE'});await run(600);eq(reads,3);channels[0].events[0][2]({eventType:'UPDATE'});channels[0].events[0][2]({eventType:'UPDATE'});eq(reads,3);release();gate=null;for(let i=0;i<8;i++)await tick();eq([...timers.values()].filter(t=>t.delay===600).length,1);await run(600);eq(reads,4);
channels[0].cb('CLOSED');eq(removed,1);eq([...timers.values()].filter(t=>t.delay===15000).length,1);channels[0].cb('CHANNEL_ERROR');eq([...timers.values()].filter(t=>t.delay===15000).length,1);channels[0].events[0][2]({eventType:'UPDATE'});eq([...timers.values()].filter(t=>t.delay===600).length,0);await run(15000);eq(channels.length,2);
session.accessToken='refreshed';intervals[0]();for(let i=0;i<8;i++)await tick();eq(channels.length,2);eq(auth.at(-1),'refreshed');
hidden=true;w.document.dispatchEvent(new w.Event('visibilitychange'));eq(removed,2);eq(timers.size,0);hidden=false;w.document.dispatchEvent(new w.Event('visibilitychange'));for(let i=0;i<8;i++)await tick();eq(channels.length,3);
session={role:'admin',authUserId:'b',userId:'v',accessToken:'other'};w.QT_REALTIME_FORCE_REFRESH();for(let i=0;i<8;i++)await tick();eq(removed,3);eq(channels.length,4);eq(auth.at(-1),'other');channels[2].cb('SUBSCRIBED');eq(w.QT_REALTIME_STATUS,'PAUSED');
online=false;w.dispatchEvent(new w.Event('offline'));eq(removed,4);eq(timers.size,0);online=true;w.dispatchEvent(new w.Event('online'));for(let i=0;i<8;i++)await tick();eq(channels.length,5);
w.dispatchEvent(new w.Event('pagehide'));eq(removed,5);eq(timers.size,0);intervals[0]();await tick();eq(channels.length,5);w.dispatchEvent(new w.Event('pageshow'));for(let i=0;i<8;i++)await tick();eq(channels.length,6);
session=null;w.QT_REALTIME_FORCE_REFRESH();eq(removed,6);eq(timers.size,0);
// Approval/catalog/settlement changes must enter the shared render signature.
const acorn=require('acorn'),html=fs.readFileSync(path.resolve(__dirname,'../admin/index.html'),'utf8');for(const m of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)){for(const node of acorn.parse(m[1],{ecmaVersion:'latest'}).body)if(node.type==='FunctionDeclaration'&&['qtSyncHash','qtSyncSignature'].includes(node.id.name))vm.runInContext(m[1].slice(node.start,node.end),d.getInternalVMContext())}
w.currentUser=()=>({status:'approved'});const cache={};w.dbGet=(key,empty)=>cache[key]||empty;for(const key of ['qt_users','qt_products','qt_settlements']){const before=w.qtSyncSignature().main;cache[key]=[{id:'changed',status:'active',price:25}];eq(w.qtSyncSignature().main!==before,true)}d.window.close();

console.log(JSON.stringify({checks,failures:0,scope:'isolated Admin session, single hydration queue, retired socket and lifecycle; real JWT/RLS/socket not certified'}));
})().catch(e=>{console.error(e);process.exit(1)});
