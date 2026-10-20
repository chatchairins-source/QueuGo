const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
(async()=>{
 let actor='a',route='#admin',tab='qt-sec-users',calls=[],directory=0,release,gate;
 const listeners={},cache={};let checks=0;
 const ctx={window:null,location:{get hash(){return route}},qtSessionRead:()=>actor?{role:'admin',authUserId:actor,userId:actor}:null,qtGetAccessToken:async()=>actor,qtSupabaseHeaders:()=>({}),QT_SUPABASE_CONFIG:{URL:'https://isolated.invalid'},QT_DB_CACHE:cache,performance,AbortController,setTimeout,clearTimeout,Date,qtAdminCurrentSection:()=>tab,qtHydrateAdminDirectory:async()=>{directory++;if(gate)await gate},qtRenderAdminRiderPresence:async()=>{},qtPullSystemSettings:async()=>{},qtSupabaseTable:async p=>{calls.push(p);return []},document:{getElementById:()=>null,activeElement:null},toast:()=>{},router:()=>{},QT_ADMIN_SESSION_KEY:'session',fetch:async(u,o)=>{calls.push(o.method+' '+u);if(gate)await gate;return {ok:true,headers:{get:()=> '0-0/17'}}},addEventListener:(name,fn)=>listeners[name]=fn};ctx.window=ctx;
 vm.createContext(ctx);vm.runInContext(fs.readFileSync('admin/section-loader.js','utf8'),ctx);
 await Promise.all([ctx.qgAdminLoadSection(),ctx.qgAdminLoadSection()]);assert.equal(calls.length,7);checks++;
 assert.equal(ctx.QG_ADMIN_SUMMARY.orders,17);checks++;
 assert(calls.every(p=>p.startsWith('HEAD ')));checks++;
 await ctx.qgAdminLoadSection();assert.equal(calls.length,7);checks++;
 route='#admin-governance';await ctx.qgAdminLoadSection();assert.equal(directory,1);checks++;
 tab='qt-sec-security';await ctx.qgAdminLoadSection();assert.equal(directory,1);checks++;
 await ctx.qgAdminLoadSection(false,'orders:UPDATE');assert.equal(directory,1);checks++;
 await ctx.qgAdminLoadSection(false,'users:UPDATE');assert.equal(directory,2);checks++;
 await ctx.qgAdminLoadSection(true);assert.equal(directory,3);checks++;
 for(const page of ['admin-grocery','admin-market','admin-orders','admin-cash','admin-gp','admin-support']){route='#'+page;await ctx.qgAdminLoadSection();assert.equal(calls.length,7);checks++}
 route='#admin-governance';tab='qt-sec-audit';await ctx.qgAdminLoadSection();assert(calls.at(-1).includes('audit_logs?')&&calls.at(-1).endsWith('limit=100'));checks++;
 route='#admin';gate=new Promise(r=>release=r);const pending=ctx.qgAdminLoadSection(true);await new Promise(r=>setImmediate(r));actor='b';release();await pending;gate=null;
 assert.notEqual(ctx.QG_ADMIN_SUMMARY?.orders,undefined); // actor A cannot write after B; previous summary is invalidated on next actor read.
 await ctx.qgAdminLoadSection();assert.equal(ctx.QG_ADMIN_SUMMARY.orders,17);checks++;
 actor=null;const before=calls.length;await ctx.qgAdminLoadSection(true);assert.equal(calls.length,before);assert.equal(ctx.QG_ADMIN_SUMMARY,null);checks+=2;
 actor='a';route='#admin-orders';let signal,finish,readCount=0;
 const get=()=>ctx.qgAdminRead('orders?select=id&limit=100',{},s=>{signal=s;readCount++;return new Promise(r=>finish=r)});
 const one=get(),two=get(),deniedOne=assert.rejects(one,e=>e.name==='AbortError'),deniedTwo=assert.rejects(two,e=>e.name==='AbortError');await new Promise(r=>setImmediate(r));assert.equal(readCount,1);checks++;
 route='#admin-catalog';listeners.hashchange();assert.equal(signal.aborted,true);finish([]);await Promise.all([deniedOne,deniedTwo]);checks+=3;
 const fresh=await ctx.qgAdminRead('orders?select=id&limit=100',{},async s=>{assert.equal(s.aborted,false);return ['fresh']});assert.equal(fresh[0],'fresh');checks++;
 let finishEarly,earlySignal;route='#admin-orders';const early=ctx.qgAdminRead('same-before-hash-event',{},s=>{earlySignal=s;return new Promise(r=>finishEarly=r)}),earlyDenied=assert.rejects(early,e=>e.name==='AbortError');await new Promise(r=>setImmediate(r));route='#admin-catalog';const next=await ctx.qgAdminRead('same-before-hash-event',{},async()=>['new-route']);assert.equal(earlySignal.aborted,true);assert.equal(next[0],'new-route');finishEarly([]);await earlyDenied;checks+=2;
 route='#admin-governance';tab='qt-sec-users';let directorySignal,finishDirectory;
 const sameDirectory=ctx.qgAdminRead('users?select=id&limit=201',{},s=>{directorySignal=s;return new Promise(r=>finishDirectory=r)});await new Promise(r=>setImmediate(r));tab='qt-sec-shops';listeners['qg:admin-section']();assert.equal(directorySignal.aborted,false);finishDirectory([]);await sameDirectory;checks++;
 let finishMutation,mutationSignal='unset';const mutation=ctx.qgAdminRead('rpc/guarded_action',{method:'POST'},s=>{mutationSignal=s;return new Promise(r=>finishMutation=r)});await new Promise(r=>setImmediate(r));route='#admin';listeners.hashchange();assert.equal(mutationSignal,undefined);finishMutation(true);assert.equal(await mutation,true);checks+=2;
 let authSignal,finishAuth;const authRead=ctx.qgAdminRead('audit_logs?select=id&limit=100',{},s=>{authSignal=s;return new Promise(r=>finishAuth=r)}),authDenied=assert.rejects(authRead,e=>e.name==='AbortError');await new Promise(r=>setImmediate(r));actor='b';listeners.storage({key:'session'});assert.equal(authSignal.aborted,true);finishAuth([]);await authDenied;checks+=2;
 let headSignals=[];ctx.fetch=async(u,o)=>{headSignals.push(o.signal);return new Promise((resolve,reject)=>o.signal.addEventListener('abort',()=>reject(Object.assign(new Error('cancelled'),{name:'AbortError'})),{once:true}))};route='#admin';
 const heads=ctx.qgAdminLoadSection(true),headsDenied=assert.rejects(heads,e=>e.name==='AbortError');await new Promise(r=>setImmediate(r));route='#admin-orders';listeners.hashchange();await headsDenied;assert.equal(headSignals.length,7);assert(headSignals.every(s=>s.aborted));assert.equal(ctx.QG_ADMIN_SUMMARY,null);checks+=3;
 console.log(JSON.stringify({checks,failures:0,scope:'executed Admin section loader: request coalescing, TTL, auth fencing, forced refresh, bounded audit, lightweight routes, GET/HEAD cancellation, pending coalescing and mutation completion'}));
})().catch(e=>{console.error(e);process.exit(1)});
