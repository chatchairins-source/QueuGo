const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<16;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const html=fs.readFileSync('admin/index.html','utf8'),start=html.indexOf(' let selected=null,version=0,owner=null'),end=html.indexOf('\n})();',start),d=new JSDOM('<main></main>',{url:'https://isolated.invalid/admin/#admin-support',runScripts:'outside-only'}),w=d.window;
 const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');let actor={id:uid(1),type:'admin',status:'approved'},calls=[],timers=[],gate,release,failRefresh=false,toasts=[];let checks=0;
 Object.defineProperty(w.document,'hidden',{value:false,configurable:true});
 Object.defineProperty(w.navigator,'onLine',{value:true,configurable:true});
 w.currentUser=()=>actor;w.navigate=()=>{};w.qtUuidLike=v=>/^[a-f0-9-]{36}$/.test(v);w.qtGetAccessToken=async()=> 'fixture';w.escapeHtml=v=>String(v??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');w.toast=m=>toasts.push(m);w.layout=(title,body)=>w.document.querySelector('main').innerHTML=body;w.setInterval=(fn,ms)=>timers.push({fn,ms});
 const message=(i,shop=uid(2))=>({id:uid(1000+i),shop_user_id:shop,sender_user_id:uid(2),body:'<img src=x onerror=alert(1)> '+i,created_at:'2026-10-20T00:00:00.000Z'});
 w.qtSupabaseTable=async(path,options={})=>{calls.push({path,options});if(gate)await gate;if(path.startsWith('shop_profiles?'))return [{user_id:uid(2),shop_name:'ร้านทดสอบ'}];if(options.method==='POST'){failRefresh=true;return []}if(failRefresh&&path.includes('shop_user_id=eq.')){failRefresh=false;throw Error('refresh unavailable')}if(path.includes('shop_user_id=eq.'))return Array.from({length:path.includes('&or=')?2:101},(_,i)=>message(i));return Array.from({length:path.includes('&or=')?2:201},(_,i)=>message(i))};
 w.eval('(function(){'+html.slice(start,end)+'\n})();');w.renderAdminSupportInbox();await tick();
 assert.equal(calls.length,3);checks++;assert(calls[0].path.includes('limit=201'));checks++;assert(calls[2].path.includes('shop_user_id=eq.'+uid(2))&&calls[2].path.includes('limit=101'));checks++;
 assert.equal(w.document.querySelectorAll('.qg-support-bubble').length,100);checks++;assert.equal(w.document.querySelectorAll('#qg-support-messages img').length,0);checks++;assert.equal(w.document.getElementById('qg-thread-older').disabled,false);checks++;
 calls=[];w.qgSelectAdminSupport(uid(2));await tick();assert.equal(calls.length,1);assert(calls[0].path.includes('limit=101'));checks+=2;
 calls=[];w.qgSupportThreadPage(1);await tick();assert(calls[0].path.includes('&or='));assert.equal(w.document.querySelectorAll('.qg-support-bubble').length,2);assert.equal(w.document.getElementById('qg-thread-older').disabled,true);checks+=3;
 calls=[];w.qgSupportPage(1);await tick();assert(calls[0].path.includes('limit=201')&&calls[0].path.includes('&or='));assert.equal(w.document.getElementById('qg-support-older').disabled,true);checks+=2;
 calls=[];const form=w.document.getElementById('qg-support-form'),input=w.document.getElementById('qg-support-reply');input.value='fixture reply';const event={preventDefault(){},target:form};w.qgAdminReply(event);w.qgAdminReply(event);await tick();assert.equal(calls.filter(x=>x.options.method==='POST').length,1);assert.equal(input.value,'');assert.equal(toasts.length,0);assert.equal(form.querySelector('button').disabled,false);checks+=4;
 assert.equal(timers.length,1);assert.equal(timers[0].ms,60000);checks+=2;
 // Auto refresh must not jump/overwrite historical pages.
 calls=[];timers[0].fn();await tick();assert.equal(calls.length,0);checks++;
 // Return the inbox to newest, then a draft must suppress fallback refresh.
 w.qgSupportPage(-1);await tick();assert.equal(w.document.getElementById('qg-support-newer').disabled,true);checks++;
 calls=[];input.value='กำลังพิมพ์คำตอบ';timers[0].fn();await tick();assert.equal(calls.length,0);checks++;
 // Newest + idle visible support view is eligible for one bounded fallback refresh.
 input.value='';calls=[];timers[0].fn();await tick();assert(calls.some(x=>x.path.includes('shop_support_messages?select=id,shop_user_id,created_at')&&x.path.includes('limit=201')));checks++;
 calls=[];w.location.hash='#admin';timers[0].fn();await tick();assert.equal(calls.length,0);checks++;
 w.location.hash='#admin-support';gate=new Promise(r=>release=r);const task=w.qgLoadAdminSupport();await tick();actor={id:uid(3),type:'customer',status:'approved'};release();await task;assert(!w.document.getElementById('qg-support-status').textContent.includes('แสดงข้อความ'));checks++;gate=null;calls=[];await w.qgLoadAdminSupport();assert.equal(calls.length,0);checks++;
 assert(html.includes('qgAdminSupportCanAutoRefresh'));assert(html.includes('if(inboxCursor||threadCursor)return false'));checks+=2;
 const indexSql=fs.readFileSync('supabase/migrations/20261020041534_admin_support_inbox_paging_index.sql','utf8');
 assert(indexSql.includes('shop_support_messages_recent_idx'));assert(/\(created_at desc, id desc\)/i.test(indexSql));checks+=2;
 w.close();console.log(JSON.stringify({checks,failures:0,scope:'actual Support DOM, bounded inbox/thread server queries, keyset paging, newest-idle 60s fallback, draft/history suppression, stale-actor fencing and global inbox index contract; isolated'}));
})().catch(e=>{console.error(e);process.exit(1)});
