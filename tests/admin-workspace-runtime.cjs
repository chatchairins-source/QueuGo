const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<10;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const d=new JSDOM('<main></main>',{url:'https://isolated.invalid/admin/',runScripts:'outside-only'}),w=d.window;
 let actor={id:'admin',type:'admin',status:'approved'},calls=[],home=0,services=0,pricing=0,release,gate;
 w.currentUser=()=>actor;w.navigate=()=>{};w.layout=(title,html)=>w.document.querySelector('main').innerHTML=html;w.qtGetAccessToken=async()=> 'fixture';
 w.qgAdminLoadHomeBanners=()=>home++;w.qgAdminLoadServiceBanners=()=>services++;w.qgRenderPlatformControls=()=>pricing++;
 w.qtSupabaseTable=async path=>{calls.push(path);if(gate)await gate;return Array.from({length:51},(_,i)=>({id:String(i),name_th:'สินค้า <script>'+i,category:'ปลา',active:true,image_status:'pending',catalog_preview_image:i===0?'javascript:alert(1)':'https://isolated.invalid/image.jpg',suggested_unit:'กิโลกรัม'}))};
 w.eval(fs.readFileSync('admin/workspace.js','utf8'));let checks=0;
 w.qgRenderAdminWorkspace('admin-content');assert.equal(home,1);assert.equal(services,1);assert(w.document.getElementById('qg-home-banner-manager'));assert.equal(calls.length,0);checks+=4;
 w.qgRenderAdminWorkspace('admin-pricing');assert.equal(pricing,1);assert.equal(calls.length,0);checks+=2;
 w.qgRenderAdminWorkspace('admin-catalog');assert(w.document.querySelector('button[onclick*="admin-market-master"]'));assert.equal(calls.length,0);checks+=2;
 w.qgRenderAdminWorkspace('admin-market-master');await tick();assert.equal(w.document.querySelectorAll('.qg-master-card').length,50);assert.equal(w.document.querySelectorAll('.qg-master-card script').length,0);assert.equal(w.document.querySelectorAll('.qg-master-card img[src^="javascript:"]').length,0);assert(calls[0].includes('limit=51&offset=0'));checks+=4;
 w.document.getElementById('qg-master-next').click();await tick();assert(calls.at(-1).includes('offset=50'));checks++;
 w.document.getElementById('qg-master-query').value='ปลาทู';w.document.getElementById('qg-master-category').value='ปลา';w.document.getElementById('qg-master-filter').onsubmit({preventDefault(){}});await tick();assert(calls.at(-1).includes('&name_th=ilike.'));assert(calls.at(-1).includes('&category=eq.'));assert(calls.at(-1).includes('offset=0'));checks+=3;
 gate=new Promise(r=>release=r);w.document.getElementById('qg-master-filter').onsubmit({preventDefault(){}});await tick();actor={id:'other',type:'customer',status:'approved'};w.document.getElementById('qg-master-rows').textContent='ใหม่';release();await tick();assert.equal(w.document.getElementById('qg-master-rows').textContent,'ใหม่');checks++;
 const before=calls.length;w.qgRenderAdminWorkspace('admin-market-master');await tick();assert.equal(calls.length,before);checks++;
 d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'separate existing Content/Pricing/Catalog workspaces; paged server-filtered Market master read, XSS-safe text/image and actor fencing; isolated only'}));
})().catch(e=>{console.error(e);process.exit(1)});
