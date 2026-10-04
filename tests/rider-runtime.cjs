const fs=require('fs'),vm=require('vm'),assert=require('assert');
const src=fs.readFileSync(require('path').resolve(__dirname,'../rider/index.html'),'utf8');
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}};
const flush=async()=>{for(let i=0;i<20;i++)await Promise.resolve()};
let checks=0;const eq=(a,b)=>{assert.deepStrictEqual(a,b);checks++};
function boot(){
 let stored=null;const events=[],calls=[];
 const ctx=vm.createContext({Date,JSON,Map,Set,Number,String,Array,Promise,Error,encodeURIComponent,AbortSignal,
 S:{user:{id:'a'},riderProfile:{id:'ra'},session:{authUserId:'a',sessionId:'sa'},online:false,activeTab:'home',history:[],openJobs:[],pos:{lat:13,lng:100}},
 navigator:{onLine:true},document:{hidden:false},SB:{URL:'https://fixture.test'},sbHeaders:()=>({}),
 readSession:()=>stored,writeSession:s=>stored=s,jwtExpiryMs:()=>0,
 fetch:async()=>{throw Error('unexpected fetch')},
 sbTable:async(path,options)=>{calls.push({path,options});return []},sbRpc:async()=>[],
 NEARBY_RADIUS_KM:2,haversineKm:(_a,_b,lat)=>Math.abs(lat-13)*100,
 renderSheet:()=>events.push('render'),placeJobMarkers:()=>{},renderHistoryPanel:()=>events.push('history'),qgRiderHistoryKey:()=>null,
 localStorage:{setItem:()=>events.push('cache')},qgRenderMessageInbox:()=>{},qgWatchCompletedChat:()=>{},toast:()=>events.push('error'),console:{error:()=>{}},window:{qtCheckNewJobs:()=>{}}});
 vm.runInContext(src.slice(src.indexOf('let riderTokenRefresh='),src.indexOf('function esc(v)')),ctx);
 vm.runInContext(src.slice(src.indexOf('let riderRefreshTask='),src.indexOf('function placeJobMarkers()')),ctx);
 return {ctx,events,calls,run:s=>vm.runInContext(s,ctx),setSession:s=>{stored=s;ctx.S.session=s},session:()=>stored};
}
(async()=>{
 const t=boot(),c=t.ctx,g=deferred();let count=0;
 t.setSession({authUserId:'a',sessionId:'sa',accessToken:'expired',refreshToken:'refresh-a',expiresAt:Date.now()-1});
 c.fetch=async(_url,options)=>{count++;assert(options.signal);return g.promise};
 const pending=[t.run('getAccessToken()'),t.run('getAccessToken()'),t.run('getAccessToken()')];eq(count,1);
 g.resolve({ok:true,json:async()=>({access_token:'new',refresh_token:'new-refresh',expires_at:Math.floor(Date.now()/1000)+3600})});
 eq(await Promise.all(pending),['new','new','new']);eq(t.session().accessToken,'new');eq(c.S.session.accessToken,'new');
 // An old refresh cannot restore a signed-out or replaced account.
 for(const replacement of [null,{authUserId:'b',sessionId:'sb',accessToken:'b-token',refreshToken:'b-refresh',expiresAt:Date.now()+3600000}]){
  const hold=deferred();t.setSession({authUserId:'a',sessionId:'sa',accessToken:'expired',refreshToken:'refresh-a',expiresAt:1});c.fetch=async()=>hold.promise;
  const p=t.run('getAccessToken()');t.setSession(replacement);hold.resolve({ok:true,json:async()=>({access_token:'late-a'})});eq(await p,'');eq(t.session(),replacement);
 }
 const hold=deferred();t.setSession({authUserId:'a',sessionId:'sa',accessToken:'expired',refreshToken:'old',expiresAt:1});c.fetch=async()=>hold.promise;const old=t.run('getAccessToken()');
 t.setSession({authUserId:'a',sessionId:'sa',accessToken:'newer',refreshToken:'rotated',expiresAt:Date.now()+3600000});hold.resolve({ok:true,json:async()=>({access_token:'late',refresh_token:'late-refresh'})});eq(await old,'newer');eq(t.session().refreshToken,'rotated');
 c.fetch=async()=>{throw Error('offline')};t.setSession({authUserId:'a',sessionId:'sa',accessToken:'valid',refreshToken:'r',expiresAt:Date.now()+30000});eq(await t.run('getAccessToken()'),'valid');
 t.setSession({...t.session(),expiresAt:1});await assert.rejects(t.run('getAccessToken()'),/offline/);checks++;eq(t.session().accessToken,'valid');
 c.fetch=async()=>({ok:true,json:async()=>({access_token:'recovered',expires_at:Math.floor(Date.now()/1000)+3600})});eq(await t.run('getAccessToken()'),'recovered');
 // An aborted refresh releases the single-flight slot and preserves the recoverable session.
 const abort=new AbortController();let budget;c.AbortSignal={timeout:ms=>{budget=ms;return abort.signal}};
 t.setSession({authUserId:'a',sessionId:'sa',accessToken:'expired',refreshToken:'retryable',expiresAt:1});
 c.fetch=async(_url,{signal})=>new Promise((_resolve,reject)=>signal.addEventListener('abort',()=>reject(Error('timeout')),{once:true}));
 const timeout=t.run('getAccessToken()');abort.abort();await assert.rejects(timeout,/timeout/);checks++;eq(budget,15000);eq(t.session().refreshToken,'retryable');
 c.AbortSignal=AbortSignal;c.fetch=async()=>({ok:true,json:async()=>({access_token:'after-timeout',expires_at:Math.floor(Date.now()/1000)+3600})});eq(await t.run('getAccessToken()'),'after-timeout');
 // Session guard responses and logout revocation cannot erase a newer login.
 const a=boot(),y=a.ctx;const sessionA={authUserId:'a',sessionId:'sa',accessToken:'a-token',expiresAt:Date.now()+3600000};
 a.setSession(sessionA);let loggedOut=0,stageRenders=0,pendingRenders=0;y.console.warn=()=>{};
 y.forceSessionLogout=()=>{loggedOut++;a.setSession(null);y.S.user=null};y.renderStage=()=>stageRenders++;y.renderPending=()=>pendingRenders++;
 y.localStorage.getItem=()=>null;y.qgRiderHistoryKey=()=>null;
 vm.runInContext(src.slice(src.indexOf('function riderSessionMatches('),src.indexOf('function startSessionGuard()')),y);
 vm.runInContext(src.slice(src.indexOf('let riderResumeTask='),src.indexOf('/* ---------- LOGIN ---------- */')),y);
 vm.runInContext(src.slice(src.indexOf('async function doLogout()'),src.indexOf('/* ---------- MAIN STAGE ---------- */')),y);
 let authGate=deferred(),rpcCalls=0;y.sbRpc=async()=>{rpcCalls++;return authGate.promise};
 const check=a.run('checkActiveSession()');await flush();for(let i=0;i<10;i++)a.run('checkActiveSession()');eq(rpcCalls,1);
 const sessionB={...sessionA,authUserId:'b',sessionId:'sb'};a.setSession(sessionB);authGate.resolve(false);eq(await check,false);eq(loggedOut,0);eq(a.session().authUserId,'b');
 a.setSession(sessionA);y.sbRpc=async()=>false;eq(await a.run('checkActiveSession()'),false);eq(loggedOut,1);eq(a.session(),null);
 a.setSession(sessionA);authGate=deferred();y.sbRpc=async()=>authGate.promise;const logout=a.run('doLogout()');eq(a.session(),null);
 a.setSession(sessionB);authGate.resolve(true);await logout;eq(a.session().authUserId,'b');
 // Resume is single-flight and does not render a retired user/profile.
 a.setSession(sessionA);y.S.user={id:'a'};y.sbRpc=async()=>true;authGate=deferred();let profileReads=0;
 y.sbTable=async path=>{profileReads++;return path.startsWith('users?')?authGate.promise:[{id:'ra',metadata:{online:true}}]};
 const restore=a.run('resumeSession()');a.run('resumeSession()');await flush();eq(profileReads,1);a.setSession(sessionB);authGate.resolve([{id:'a',role:'rider',status:'active'}]);await restore;eq(stageRenders,0);eq(profileReads,1);
 a.setSession(sessionA);authGate=deferred();y.S.user={id:'a'};y.sbTable=async()=>authGate.promise;const profile=a.run('loadRiderProfile()');await flush();a.setSession(sessionB);y.S.user={id:'b'};y.S.riderProfile={id:'rb'};authGate.resolve([{id:'ra',metadata:{online:true}}]);eq(await profile,false);eq(y.S.riderProfile.id,'rb');
 a.setSession(sessionA);y.sbTable=async path=>path.startsWith('users?')?[{id:'a',role:'rider',status:'active'}]:[{id:'ra',metadata:{online:true}}];await a.run('resumeSession()');eq(stageRenders,1);eq(y.S.riderProfile.id,'ra');eq(y.S.online,true);
 y.sbTable=async()=>[{id:'a',role:'admin',status:'active'}];await a.run('resumeSession()');eq(a.session(),null);eq(stageRenders,1);eq(pendingRenders,0);
 // Multiple timer ticks share a pending read. Explicit refresh gets one trailing snapshot.
 const j=boot(),x=j.ctx; x.getAccessToken=async()=>x.S.session.authUserId;let gate=deferred(),mine=0,history=0;
 x.sbTable=async(path)=>{if(path.includes('status=in.')){mine++;if(mine===1)return gate.promise;return []}history++;return []};
 const first=j.run('refreshData(true)');await flush();for(let i=0;i<20;i++)j.run('refreshData(true)');eq(mine,1);gate.resolve([]);await first;eq(mine,1);eq(history,1);
 gate=deferred();mine=0;const fg=j.run('refreshData()');await flush();for(let i=0;i<20;i++)j.run('refreshData()');eq(mine,1);gate.resolve([]);await fg;eq(mine,2);
 // A new actor queues its own read; old actor state never renders.
 gate=deferred();mine=0;const readPaths=[];x.sbTable=async(path,options)=>{readPaths.push([path,options.token]);if(path.includes('status=in.')){mine++;return mine===1?gate.promise:[{id:'new-order'}]}return []};j.events.length=0;
 const race=j.run('refreshData()');await flush();x.S.user={id:'b'};x.S.riderProfile={id:'rb'};x.S.session={authUserId:'b',sessionId:'sb'};j.run('refreshData()');gate.resolve([{id:'old-order'}]);await race;
 eq(x.S.activeOrder.id,'new-order');eq(j.events.filter(e=>e==='render').length,1);eq(readPaths[1][1],'b');eq(readPaths[1][0].includes('rider_id=eq.rb'),true);
 // Pending history must not populate another actor's cache or history.
 gate=deferred();x.sbTable=async(path)=>path.includes('status=in.')?[]:gate.promise;x.S.history=[{id:'previous'}];
 const hist=j.run('refreshData()');await flush();x.S.user=null;gate.resolve([{id:'late-history'}]);await hist;eq(x.S.history[0].id,'previous');
 // Loss of online status during pool fetch discards the old candidate set.
 x.S.user={id:'b'};x.S.online=true;gate=deferred();x.sbTable=async()=>[];x.sbRpc=async()=>gate.promise;
 const pool=j.run('refreshData()');await flush();x.S.online=false;gate.resolve([{order_id:'open'}]);await pool;eq(x.S.openJobs.length,0);
 // Background and offline reads do not hit the transport.
 let offlineCalls=0;x.sbTable=async()=>{offlineCalls++;return []};x.document.hidden=true;await j.run('refreshData(true)');eq(offlineCalls,0);x.document.hidden=false;x.navigator.onLine=false;await j.run('refreshData()');eq(offlineCalls,0);x.navigator.onLine=true;
 // Many jobs share just two detail queries; server eligibility and nearest-job choice remain.
 const b=boot(),z=b.ctx;z.getAccessToken=async()=>'a';z.S.online=true;const jobs=Array.from({length:25},(_,i)=>({id:'job-'+i,shop_id:'shop-'+i%2,customer_id:'customer-'+i%3,pickup_latitude:13.1+i/100,pickup_longitude:100}));
 z.sbRpc=async()=>jobs.map(o=>({order_id:o.id}));z.sbTable=async(path,opts)=>{b.calls.push({path,opts});if(path.includes('status=in.')||path.includes('status=eq.completed'))return [];if(path.startsWith('orders?'))return jobs;if(path.startsWith('shop_profiles?'))return [{id:'shop-0',shop_name:'Shop zero'},{id:'shop-1',shop_name:'Shop one'}];return [0,1,2].map(i=>({id:'customer-'+i,name:'Customer '+i}))};
 await b.run('refreshData()');eq(b.calls.filter(q=>q.path.startsWith('shop_profiles?')).length,1);eq(b.calls.filter(q=>q.path.startsWith('users?')).length,1);eq(z.S.openJobs.length,1);eq(z.S.openJobs[0].order.id,'job-0');eq(jobs[24]._shop.shop_name,'Shop zero');eq(jobs[24]._customer.name,'Customer 0');
 // A failed read is recoverable and retains the last successful snapshot.
 z.sbTable=async()=>{throw Error('network')};await b.run('refreshData()');eq(z.S.openJobs[0].order.id,'job-0');eq(b.events.filter(e=>e==='error').length,1);z.S.online=false;z.sbTable=async()=>[];await b.run('refreshData()');eq(z.S.openJobs.length,0);
 console.log(JSON.stringify({checks,failures:0,scope:'isolated Rider refresh ownership, coalescing, batched detail reads and token rotation; real Auth/RLS/network not certified'}));
})().catch(e=>{console.error(e);process.exit(1)});
