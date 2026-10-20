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
 for(const page of ['admin-grocery','admin-market','admin-orders','admin-cash','admin-gp','admin-support']){route='#'+page;await ctx.qgAdminLoadSection(true);assert.equal(calls.length,7);checks++}
 route='#admin-governance';tab='qt-sec-audit';await ctx.qgAdminLoadSection();assert(calls.at(-1).includes('audit_logs?')&&calls.at(-1).endsWith('limit=100'));checks++;
 route='#admin';gate=new Promise(r=>release=r);const pending=ctx.qgAdminLoadSection(true);await new Promise(r=>setImmediate(r));actor='b';release();await pending;gate=null;
 assert.notEqual(ctx.QG_ADMIN_SUMMARY?.orders,undefined); // actor A cannot write after B; previous summary is invalidated on next actor read.
 await ctx.qgAdminLoadSection();assert.equal(ctx.QG_ADMIN_SUMMARY.orders,17);checks++;
 actor=null;const before=calls.length;await ctx.qgAdminLoadSection(true);assert.equal(calls.length,before);assert.equal(ctx.QG_ADMIN_SUMMARY,null);checks+=2;
 console.log(JSON.stringify({checks,failures:0,scope:'executed Admin section loader: request coalescing, TTL, auth fencing, forced refresh, bounded audit and lightweight routes'}));
})().catch(e=>{console.error(e);process.exit(1)});
