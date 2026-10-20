const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let n=0;n<12;n++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const html=fs.readFileSync('admin/index.html','utf8'),d=new JSDOM('<main id="app"></main>',{url:'https://isolated.invalid/admin/#admin-orders',runScripts:'outside-only'}),w=d.window;
 let actor={id:'admin',type:'admin',status:'approved'},calls=[],sent=[],failOnce=true,checks=0;
 const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0'),qOf=path=>new URL('https://isolated.invalid/'+path).searchParams;
 w.currentUser=()=>actor;w.qtAdminOnly=()=>actor.type==='admin'?actor:null;w.qtUuidLike=v=>/^[\da-f-]{36}$/.test(v||'');w.qtGetAccessToken=async()=> 'fixture';w.toast=()=>{};w.qtAudit=()=>{};w.navigate=()=>{};w.qgAdminIcon=()=>'';w.qtSessionRead=()=>({role:'admin'});w.QueueGoOrderNumber={format:o=>o.order_number||o.id};w.escapeHtml=v=>String(v??'');
 w.layout=(title,body)=>w.document.getElementById('app').innerHTML=body;
 w.qtSupabaseTable=async(path,options={})=>{
  calls.push({path,options});
  if(path.startsWith('notifications?')){sent.push(options.body.map(x=>({...x})));assert.equal(options.ignoreDuplicates,true);if(failOnce){failOnce=false;throw Error('lost reply')}return null}
  if(path.startsWith('users?select=id&role='))return path.includes('&id=gt.')?(path.includes(uid(200))?Array.from({length:5},(_,i)=>({id:uid(201+i)})):[]):Array.from({length:200},(_,i)=>({id:uid(i+1)}));
  if(path.startsWith('orders?')){
    const p=qOf(path),cursor=p.get('or')||'';
    if(cursor)return [{id:uid(101),order_number:'QT-101',status:'pending',shop_id:uid(700),customer_id:uid(701),created_at:'2026-10-19T23:59:59Z'}];
    return Array.from({length:101},(_,i)=>({id:uid(i+1),order_number:'QT-'+i,status:'pending',shop_id:uid(700),customer_id:uid(701),created_at:'2026-10-20T00:00:00Z'}));
  }
  if(path.startsWith('shop_profiles?'))return [{id:uid(700),shop_name:'ชื่อร้านจริงจากหน้านี้'}];
  if(path.startsWith('users?select=id,name'))return [{id:uid(701),name:'ชื่อลูกค้าจากหน้านี้'}];
  return [];
 };
 for(const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g))if(m[1].includes('qg-v22-admin-operations')||m[1].includes('qt-v546-admin-full-control-script'))w.eval(m[2]);
 w.renderAdminOrderCenter();await tick();assert.equal(w.document.querySelectorAll('#qg-v22-order-list article').length,100);checks++;
 const firstOrderCall=calls.find(c=>c.path.startsWith('orders?'));assert(firstOrderCall);assert(firstOrderCall.path.includes('limit=101'));assert(!firstOrderCall.path.includes('offset='));assert(!qOf(firstOrderCall.path).get('or'));checks+=4;
 assert(w.document.querySelector('#qg-v22-order-list').textContent.includes('ชื่อร้านจริงจากหน้านี้'));checks++;
 w.document.querySelector('#qg-orders-next').click();await tick();
 const orderCalls=()=>calls.filter(c=>c.path.startsWith('orders?')),secondOrderCall=orderCalls().at(-1),cursor=qOf(secondOrderCall.path).get('or')||'';
 assert(cursor.includes('created_at.lt.2026-10-20T00:00:00Z'));assert(cursor.includes('created_at.eq.2026-10-20T00:00:00Z'));assert(cursor.includes('id.lt.'+uid(100)));assert(!secondOrderCall.path.includes('offset='));checks+=4;
 assert.equal(w.document.querySelectorAll('#qg-v22-order-list article').length,1);assert.equal(w.qgAdminOrderPage,1);checks+=2;
 w.document.querySelector('#qg-orders-prev').click();await tick();const prevOrderCall=orderCalls().at(-1);assert(!qOf(prevOrderCall.path).get('or'));assert.equal(w.qgAdminOrderPage,0);assert.equal(w.document.querySelectorAll('#qg-v22-order-list article').length,100);checks+=3;
 w.document.querySelector('#qg-orders-next').click();await tick();w.document.querySelector('[data-status=completed]').click();await tick();
 const filteredCall=orderCalls().at(-1);assert(filteredCall.path.includes('&status=eq.completed'));assert(!qOf(filteredCall.path).get('or'));assert.equal(w.qgAdminOrderPage,0);checks+=3;
 assert(orderCalls().every(c=>!c.path.includes('offset=')));checks++;
 w.document.getElementById('app').innerHTML='<input id="qt-admin-broadcast-title" value="ประกาศทดสอบ"><textarea id="qt-admin-broadcast-body">ข้อความทดสอบเฉพาะ localhost</textarea>';
 w.QT_ADMIN_BROADCAST_ROLE='all';await w.qtAdminSendBroadcast();assert(w.localStorage.getItem('qg-admin-broadcast-v1:admin'));assert.equal(sent.length,1);checks+=2;
 await w.qtAdminSendBroadcast();assert.equal(sent.length,3);assert.equal(sent[0][0].id,sent[1][0].id);assert.equal(sent[2].length,5);assert.equal(w.localStorage.getItem('qg-admin-broadcast-v1:admin'),null);checks+=4;
 const before=calls.length;actor={id:'customer',type:'customer',status:'approved'};await w.qtAdminSendBroadcast();assert.equal(calls.length,before);checks++;
 assert(!html.includes("&offset='+(window.qgAdminOrderPage*100)"));assert(html.includes('qgAdminOrderCursorHistory'));assert(html.includes('qgAdminOrderLoadVersion'));checks+=3;
 d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'actual Admin order keyset paging/server status filter/exact-page parties, stale-load fencing and paged broadcast lost-reply durable IDs; no live messages'}));
})().catch(e=>{console.error(e);process.exit(1)});
