const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let n=0;n<12;n++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const html=fs.readFileSync('admin/index.html','utf8'),d=new JSDOM('<main id="app"></main>',{url:'https://isolated.invalid/admin/#admin-orders',runScripts:'outside-only'}),w=d.window;
 let actor={id:'admin',type:'admin',status:'approved'},calls=[],sent=[],failOnce=true,checks=0;
 const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
 w.currentUser=()=>actor;w.qtAdminOnly=()=>actor.type==='admin'?actor:null;w.qtUuidLike=v=>/^[\da-f-]{36}$/.test(v||'');w.qtGetAccessToken=async()=> 'fixture';w.toast=()=>{};w.qtAudit=()=>{};w.navigate=()=>{};w.qgAdminIcon=()=>'';w.qtSessionRead=()=>({role:'admin'});w.QueueGoOrderNumber={format:o=>o.order_number||o.id};w.escapeHtml=v=>String(v??'');
 w.layout=(title,body)=>w.document.getElementById('app').innerHTML=body;
 w.qtSupabaseTable=async(path,options={})=>{
  calls.push({path,options});
  if(path.startsWith('notifications?')){sent.push(options.body.map(x=>({...x})));assert.equal(options.ignoreDuplicates,true);if(failOnce){failOnce=false;throw Error('lost reply')}return null}
  if(path.startsWith('users?select=id&role='))return path.includes('&id=gt.')?(path.includes(uid(200))?Array.from({length:5},(_,i)=>({id:uid(201+i)})):[]):Array.from({length:200},(_,i)=>({id:uid(i+1)}));
  if(path.startsWith('orders?'))return Array.from({length:path.includes('offset=100')?1:101},(_,i)=>({id:uid(i+1),order_number:'QT-'+i,status:'pending',shop_id:uid(700),customer_id:uid(701),created_at:'2026-10-20T00:00:00Z'}));
  if(path.startsWith('shop_profiles?'))return [{id:uid(700),shop_name:'ชื่อร้านจริงจากหน้านี้'}];
  if(path.startsWith('users?select=id,name'))return [{id:uid(701),name:'ชื่อลูกค้าจากหน้านี้'}];
  return [];
 };
 for(const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g))if(m[1].includes('qg-v22-admin-operations')||m[1].includes('qt-v546-admin-full-control-script'))w.eval(m[2]);
 w.renderAdminOrderCenter();await tick();assert.equal(w.document.querySelectorAll('#qg-v22-order-list article').length,100);checks++;
 assert(w.document.querySelector('#qg-v22-order-list').textContent.includes('ชื่อร้านจริงจากหน้านี้'));assert(calls.some(c=>c.path.includes('limit=101&offset=0')));checks+=2;
 w.document.querySelector('#qg-orders-next').click();await tick();assert(calls.some(c=>c.path.includes('offset=100')));assert.equal(w.document.querySelectorAll('#qg-v22-order-list article').length,1);checks+=2;
 w.document.querySelector('[data-status=completed]').click();await tick();assert(calls.some(c=>c.path.includes('&status=eq.completed')&&c.path.includes('offset=0')));checks++;
 w.document.getElementById('app').innerHTML='<input id="qt-admin-broadcast-title" value="ประกาศทดสอบ"><textarea id="qt-admin-broadcast-body">ข้อความทดสอบเฉพาะ localhost</textarea>';
 w.QT_ADMIN_BROADCAST_ROLE='all';await w.qtAdminSendBroadcast();assert(w.localStorage.getItem('qg-admin-broadcast-v1:admin'));assert.equal(sent.length,1);checks+=2;
 await w.qtAdminSendBroadcast();assert.equal(sent.length,3);assert.equal(sent[0][0].id,sent[1][0].id);assert.equal(sent[2].length,5);assert.equal(w.localStorage.getItem('qg-admin-broadcast-v1:admin'),null);checks+=4;
 const before=calls.length;actor={id:'customer',type:'customer',status:'approved'};await w.qtAdminSendBroadcast();assert.equal(calls.length,before);checks++;
 d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'actual Admin order paging/server status filter/exact-page parties and paged broadcast lost-reply durable IDs; no live messages'}));
})().catch(e=>{console.error(e);process.exit(1)});
