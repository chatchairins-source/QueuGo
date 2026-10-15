const assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
(async()=>{
 const dom=new JSDOM('<div id="host"></div>',{url:'https://queuego.invalid',runScripts:'outside-only'}),w=dom.window;w.AbortSignal=AbortSignal;let checks=0,requests=[],fail=false;
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 let summary={enabled:true,cash_minor:12300,promo_minor:100000,held_minor:1000,available_minor:111300,entries:[],topups:[]};
 w.fetch=async(url,o)=>{requests.push([url,JSON.parse(o.body)]);if(fail)throw Error('connection lost');return Response.json(url.endsWith('queuego_credit_summary')?summary:{status:'pending'})};
 w.eval(fs.readFileSync('queuego-credit.js','utf8'));
 const host=w.document.getElementById('host');await w.QueueGoCredit.mount(host,{kind:'merchant',owner:'u1',getToken:async()=>'token',base:'https://backend.invalid',key:'publishable'});
 eq(host.textContent.includes('เงินจริง ฿123.00'),true);eq(host.querySelector('form').hidden,false);
 host.querySelector('input').value='100.05';fail=true;host.querySelector('form').dispatchEvent(new w.Event('submit',{cancelable:true}));await new Promise(r=>setTimeout(r,20));
 const first=JSON.parse(w.localStorage.getItem('qg-credit-request-v1:u1:merchant'));eq(first.amount_minor,10005);
 fail=false;host.querySelector('form').dispatchEvent(new w.Event('submit',{cancelable:true}));await new Promise(r=>setTimeout(r,20));
 eq(requests.filter(([u])=>u.endsWith('/queuego-credit')).every(([,b])=>b.id===first.id),true);
 summary={...summary,topups:[{id:first.id,status:'successful',amount_minor:10005}],entries:[{event_type:'topup',cash_delta:10005,promo_delta:0,created_at:'2026-10-15T00:00:00Z'}]};
 host.querySelector('[data-refresh]').click();await new Promise(r=>setTimeout(r,20));eq(host.querySelector('[data-new]').hidden,false);eq(host.textContent.includes('เติมเครดิตสำเร็จ'),true);
 host.querySelector('[data-new]').click();await new Promise(r=>setTimeout(r,20));eq(w.localStorage.getItem('qg-credit-request-v1:u1:merchant'),null);
 summary={...summary,enabled:false};host.querySelector('[data-refresh]').click();await new Promise(r=>setTimeout(r,20));eq(host.querySelector('form').hidden,true);
 // Provider data is text, never HTML; hostile URLs never become image sources.
 summary={...summary,enabled:true,entries:[{event_type:'<img src=x onerror=alert(1)>',cash_delta:0,promo_delta:0}],topups:[]};host.querySelector('[data-refresh]').click();await new Promise(r=>setTimeout(r,20));eq(host.querySelectorAll('[onerror]').length,0);
 dom.window.close();console.log('Credit Web: '+checks+' checks PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
