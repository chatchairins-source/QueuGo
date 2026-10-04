const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom');
const source=fs.readFileSync(path.resolve(__dirname,'../rider/index.html'),'utf8');
const gate=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}};
const flush=async()=>{for(let i=0;i<20;i++)await Promise.resolve()};let checks=0;const eq=(a,b)=>{assert.deepStrictEqual(a,b);checks++};
(async()=>{
 const dom=new JSDOM(source,{url:'https://fixture.test/rider/',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,ctx=dom.getInternalVMContext(),run=s=>vm.runInContext(s,ctx),timers=new Map();let serial=0;
 w.fetch=async()=>({ok:true,json:async()=>[]});w.AbortSignal=AbortSignal;w.setInterval=(f,ms)=>{const id=++serial;timers.set(id,{f,ms});return id};w.clearInterval=id=>timers.delete(id);w.setTimeout=()=>0;w.clearTimeout=()=>{};w.requestAnimationFrame=()=>0;w.alert=()=>{};
 for(const script of w.document.querySelectorAll('script'))if(!script.src&&script.textContent.trim())vm.runInContext(script.textContent,ctx);
 run("S.session={authUserId:'a',sessionId:'sa',accessToken:'fixture'};writeSession(S.session);S.user={id:'rider'};getAccessToken=async()=>'fixture';window.notices=[];toast=m=>notices.push(m);qtBadge=()=>{};window.order={id:'one',customer_id:'customer',status:'completed'};");
 const app=w.document.getElementById('app');app.innerHTML='<section class="stage"></section>';
 const now='2026-01-02T00:00:00Z',message={id:'m1',sender_id:'customer',message:'hello',created_at:now};let writes=0,reads=0,writeGate=null,readGate=null;
 w.sbTable=async(p,options={})=>{if(p==='order_chat_messages'){writes++;if(writeGate)await writeGate.promise;return []}if(p.startsWith('deliveries?'))return [{delivered_at:'2026-01-01T00:00:00Z'}];if(p.startsWith('order_chat_messages?')){reads++;return readGate?readGate.promise:[message]}return []};
 await run('openChat(order)');eq(w.document.querySelectorAll('#chat-screen').length,1);eq(w.document.getElementById('chat-box').textContent.includes('hello'),true);
 w.document.getElementById('chat-input').value='reply';await run('sendRiderChat(order)');eq(writes,1);eq(w.document.getElementById('chat-input').value,'');eq(run('notices.length'),0);
 // A repeated click posts once and never clears a new draft typed during the request.
 writeGate=gate();w.document.getElementById('chat-input').value='original';const send=run('sendRiderChat(order)');await flush();await run('sendRiderChat(order)');eq(writes,2);w.document.getElementById('chat-input').value='new draft';writeGate.resolve();await send;writeGate=null;eq(w.document.getElementById('chat-input').value,'new draft');eq(w.document.querySelector('button[type=submit]').disabled,false);
 // Completed-order replies require an actual customer message after delivery.
 message.created_at='2025-12-31T00:00:00Z';await run('sendRiderChat(order)');eq(writes,2);eq(w.document.getElementById('chat-input').value,'new draft');eq(run('notices.length'),1);message.created_at=now;
 // An old conversation response cannot render into the newly opened room.
 readGate=gate();const old=run('refreshRiderChat()');await flush();const before=reads;const next=run("openChat({id:'two',customer_id:'customer',status:'assigned'})");eq(w.document.querySelectorAll('#chat-screen').length,1);readGate.resolve([{...message,message:'OLD ROOM'}]);readGate=null;await Promise.all([old,next]);eq(w.document.getElementById('chat-box').textContent.includes('OLD ROOM'),false);eq(reads,before+1);
 // Background ticks share a pending read without starting another trailing poll.
 readGate=gate();const poll=run('refreshRiderChat(true)');await flush();const count=reads;for(let i=0;i<20;i++)run('refreshRiderChat(true)');eq(reads,count);readGate.resolve([message]);readGate=null;await poll;eq(reads,count);
 // Failed sends preserve the draft and release the button for an explicit retry.
 const workingTransport=w.sbTable;w.document.getElementById('chat-input').value='keep me';w.sbTable=async(p,opts)=>{if(p==='order_chat_messages')throw Error('offline');return workingTransport(p,opts)};
 await run('sendRiderChat(S.chatOrder)');eq(w.document.getElementById('chat-input').value,'keep me');eq(w.document.querySelector('button[type=submit]').disabled,false);w.sbTable=workingTransport;
 // Logout during a request discards its response; the watcher is stopped immediately.
 run('qtStartMessageWatch();qtStartMessageWatch();qtStartNewJobAlert({id:"pending-alert"})');eq([...timers.values()].filter(t=>t.ms===7000).length,1);eq([...timers.values()].filter(t=>t.ms===4000).length,1);
 readGate=gate();const retired=run('refreshRiderChat()');await flush();run("forceSessionLogout('test')");readGate.resolve([message]);readGate=null;await retired;eq([...timers.values()].filter(t=>t.ms===4000).length,0);eq([...timers.values()].filter(t=>t.ms===7000).length,0);eq(run('S.newJobAlert.active'),false);eq(run('S.chatOrder'),null);eq(w.document.getElementById('chat-screen'),null);
 // Inbox rendering and completed unread badges share the same scoped batch.
 app.innerHTML='<section class="stage"></section>';run("S.session={authUserId:'a',sessionId:'sa2',accessToken:'fixture'};writeSession(S.session);S.user={id:'rider'};S.activeTab='chat';S.chatOrder=null;S.activeOrder=null;S.history=[{id:'done',customer_id:'customer',status:'completed'}];qgClearRiderInbox();");
 let inboxCalls=0;const inboxGate=gate();w.sbTable=async p=>{inboxCalls++;await inboxGate.promise;return p.startsWith('deliveries?')?[{order_id:'done',delivered_at:'2026-01-01T00:00:00Z'}]:[{...message,order_id:'done'}]};
 const badge=run('qgWatchCompletedChat()'),inbox=run('qgRenderMessageInbox()');await flush();eq(inboxCalls,2);inboxGate.resolve();await Promise.all([badge,inbox]);eq(w.document.querySelectorAll('.qg-inbox-item').length,1);await run('qgRenderMessageInbox()');eq(inboxCalls,2);
 run('qgClearRiderInbox()');const lateGate=gate();w.sbTable=async()=>lateGate.promise;const lateBadge=run('qgWatchCompletedChat()'),lateInbox=run('qgRenderMessageInbox()');await flush();run("forceSessionLogout('test')");lateGate.resolve([{...message,order_id:'done'}]);await Promise.all([lateBadge,lateInbox]);eq(run('qgCompletedUnread()'),0);eq(w.document.getElementById('qg-rider-inbox'),null);
 dom.window.close();console.log(JSON.stringify({checks,failures:0,scope:'isolated Rider chat sending, completed-order gate, draft retention, room ownership and one polling timer; real chat/RLS not certified'}));
})().catch(e=>{console.error(e);process.exit(1)});
