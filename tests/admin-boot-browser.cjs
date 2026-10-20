'use strict';
// Whole shipped Admin document in real mobile WebKit. Localhost-only transport fixtures.
const fs=require('fs'),http=require('http'),path=require('path'),assert=require('assert/strict'),cp=require('child_process');
const {webkit}=require('playwright-core');
const root=path.resolve(__dirname,'..'),owner='00000000-0000-4000-8000-000000000001';
(async()=>{
 let baseline=false;const server=http.createServer((req,res)=>{
  const pathname=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+pathname+(pathname.endsWith('/')?'index.html':''));if(!file.startsWith(root+path.sep))return res.writeHead(403).end();
  try{const body=baseline&&pathname==='/admin/'?cp.execFileSync('git',['show','b8b9f9883b50d7da3efbd0d37d1e0080bc4b158e:admin/index.html']):fs.readFileSync(file);res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(body)}catch(e){res.writeHead(404).end()}
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await webkit.launch();let checks=0;const results=[];
 try{for(const mode of ['before','after']){
 baseline=mode==='before';const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true});const page=await context.newPage(),calls=[],errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await context.route('https://**/*',async route=>{
  const req=route.request(),u=new URL(req.url());if(!u.hostname.endsWith('.supabase.co'))return route.abort();
  const headers={'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Headers':'content-type,apikey,authorization,prefer','Access-Control-Allow-Methods':'GET,HEAD,POST,PATCH,OPTIONS','Access-Control-Expose-Headers':'Content-Range','Content-Range':'0-0/1'};
  if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});calls.push({method:req.method(),path:u.pathname+u.search});
  let data=[];
  if(u.pathname.endsWith('/users'))data=[{id:owner,auth_user_id:owner,role:'admin',name:'Local QA Admin',status:'active',metadata:{},created_at:'2026-10-01T00:00:00Z'}];
  if(u.pathname.endsWith('/system_settings'))data=[{value:{}}];
  await new Promise(r=>setTimeout(r,50));
  return route.fulfill({status:200,headers,contentType:'application/json',body:req.method()==='HEAD'?'':JSON.stringify(data)});
 });
 await context.addInitScript(({owner})=>{localStorage.setItem('qg_admin_session_v1',JSON.stringify({userId:owner,authUserId:owner,role:'admin',accessToken:'qa-token',expiresAt:Math.floor(Date.now()/1000)+3600}));},{owner});
 const start=Date.now();await page.goto(origin+'/admin/#admin',{waitUntil:'domcontentloaded'});
 await page.locator('.qg-dashboard').waitFor();const shellMs=Date.now()-start;
 if(mode==='after')await page.waitForFunction(()=>window.QG_ADMIN_SUMMARY?.orders===1);
 else await page.waitForFunction(()=>typeof QT_DB_FULL_TRIED!=='undefined'&&QT_DB_FULL_TRIED);
 await page.waitForLoadState('networkidle');const readyMs=Date.now()-start,boot=calls.slice();
 if(mode==='after'){
  assert(boot.filter(x=>x.method==='GET').every(x=>!/(order_items|order_chat_messages|products|settlements|rider_profiles)/.test(x.path)));checks++;
  assert.equal(boot.filter(x=>x.method==='HEAD'&&x.path.includes('/orders?')).length,1);checks++;
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);checks++;
  await page.evaluate(()=>qgAdminSection('qt-sec-users'));await page.waitForFunction(()=>location.hash==='#admin-governance'&&window.QG_ADMIN_DIRECTORY_PAGE);await page.waitForLoadState('networkidle');
  const reads=calls.length;await page.evaluate(()=>{qtAdminTab('qt-sec-security');qtAdminTab('qt-sec-users')});await page.waitForLoadState('networkidle');assert.equal(calls.length,reads,'cached directory tab must not re-fetch: '+JSON.stringify(calls.slice(reads)));checks++;
  await page.evaluate(()=>qtAdminTab('qt-sec-audit'));await page.waitForFunction(()=>document.querySelector('#qt-sec-audit').classList.contains('active'));await page.waitForLoadState('networkidle');assert(calls.some(x=>x.path.includes('/audit_logs?')&&x.path.includes('limit=100')));checks++;
 }
 assert.deepEqual(errors,[]);checks++;results.push({mode,shellMs,readyMs,bootRequests:boot.length,boot});await context.close();
 }
 assert(results[1].bootRequests<results[0].bootRequests);checks++;
 fs.writeFileSync(process.env.ADMIN_METRICS_PATH||'/tmp/queuego-admin-metrics.json',JSON.stringify({checks,results,scope:'50ms/request localhost fixture; not Production latency or physical iPhone'},null,2));
 console.log(JSON.stringify({checks,results:results.map(({boot,...x})=>x),scope:'whole-document mobile WebKit; isolated transport, not live authorization'}));
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exit(1)});
