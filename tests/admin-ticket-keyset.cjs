const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<14;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const html=fs.readFileSync('admin/index.html','utf8');
 const start=html.indexOf(' let qgAdminTicketCursor=null');
 const end=html.indexOf(' window.qgAdminUGCReports=function()',start);
 assert(start>0&&end>start,'ticket block missing');
 const block=html.slice(start,end);
 const d=new JSDOM('<main></main>',{url:'https://isolated.invalid/admin/#admin',runScripts:'outside-only'}),w=d.window;
 const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
 let actor={id:uid(1),type:'admin',status:'approved'},calls=[],holdNext=false,releaseHeld=null,toasts=[],checks=0;
 const ticket=(i,at='2026-10-20T04:00:00.000Z')=>({id:uid(1000+i),user_id:uid(2000+i),role:'customer',order_id:null,original_order_id:null,category:'other',details:'ticket '+i,status:'OPEN',admin_id:null,admin_note:null,evidence_path:null,created_at:at});
 const first=Array.from({length:101},(_,i)=>ticket(i));
 const second=[ticket(101,'2026-10-19T03:59:59.000Z'),ticket(102,'2026-10-19T03:59:58.000Z')];
 const safe=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const req=async(path,method='GET',body=null)=>{
   calls.push({path,method,body});
   if(holdNext&&path.startsWith('qg_support_tickets?')){holdNext=false;await new Promise(r=>{releaseHeld=r})}
   if(path.startsWith('qg_support_tickets?')){
     const q=new URL('https://isolated.invalid/'+path).searchParams;
     return q.get('or')?second:first;
   }
   if(path==='rpc/qg_admin_ticket_action'&&method==='POST')return {ok:true};
   throw Error('unexpected request '+method+' '+path);
 };
 w.currentUser=()=>actor;w.toast=x=>toasts.push(x);w.qgAdminTrace=()=>{};w.qgAdminOpenEvidence=()=>{};w.__ticketReq=req;w.__ticketSafe=safe;
 w.eval('(function(){const active=()=>currentUser()?.type==="admin"&&currentUser()?.status==="approved";const safe=v=>window.__ticketSafe(v);const req=(...args)=>window.__ticketReq(...args);const category={other:"อื่น ๆ"};'+block+'\n})();');
 w.qgAdminTickets();await tick();
 let orderCalls=()=>calls.filter(x=>x.path.startsWith('qg_support_tickets?'));
 assert.equal(w.document.querySelectorAll('#qg-v22-admin-tickets article').length,100);checks++;
 let firstCall=orderCalls()[0];assert(firstCall.path.includes('order=created_at.desc,id.desc'));assert(firstCall.path.includes('limit=101'));assert(!firstCall.path.includes('offset='));checks+=3;
 assert.equal(w.document.getElementById('qg-ticket-newer').disabled,true);assert.equal(w.document.getElementById('qg-ticket-older').disabled,false);checks+=2;
 w.document.getElementById('qg-ticket-older').click();await tick();
 let older=orderCalls().at(-1),or=new URL('https://isolated.invalid/'+older.path).searchParams.get('or')||'';
 assert(or.includes('created_at.lt.2026-10-20T04:00:00.000Z'));assert(or.includes('id.lt.'+uid(1099)));checks+=2;
 assert.equal(w.document.querySelectorAll('#qg-v22-admin-tickets article').length,2);assert.equal(w.document.getElementById('qg-ticket-newer').disabled,false);assert.equal(w.document.getElementById('qg-ticket-older').disabled,true);checks+=3;
 // Mutation on an older page must refresh the same cursor, not jump to newest.
 calls=[];const btn=w.document.querySelector('#qg-v22-admin-tickets [data-action]');const sel=w.document.querySelector('#qg-v22-admin-tickets select[data-ticket]');sel.value='IN_REVIEW';btn.click();await tick();
 assert(calls.some(x=>x.path==='rpc/qg_admin_ticket_action'&&x.method==='POST'));const refresh=calls.filter(x=>x.path.startsWith('qg_support_tickets?')).at(-1);assert(new URL('https://isolated.invalid/'+refresh.path).searchParams.get('or'));checks+=2;
 w.document.getElementById('qg-ticket-newer').click();await tick();assert(!new URL('https://isolated.invalid/'+orderCalls().at(-1).path).searchParams.get('or'));checks++;
 // Old-account response must not repaint the modal.
 holdNext=true;calls=[];const before=w.document.querySelector('.qg-content').textContent;const pending=w.qgAdminLoadTickets();await tick();actor={id:uid(9),type:'customer',status:'approved'};assert.equal(typeof releaseHeld,'function');releaseHeld();await pending;await tick();assert.equal(w.document.querySelector('.qg-content').textContent,before);checks+=2;
 assert(!block.includes('limit=200'));assert(!block.includes('offset='));checks+=2;
 const sql=fs.readFileSync('supabase/migrations/20261020042500_admin_ticket_paging_index.sql','utf8');
 assert(sql.includes('qg_support_tickets_recent_idx'));assert(/\(created_at desc, id desc\)/i.test(sql));checks+=2;
 d.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'Admin complaint tickets use 100/101 keyset paging, preserve cursor after mutation, fence stale accounts and lock global recent-ticket index'}));
})().catch(e=>{console.error(e);process.exit(1)});
