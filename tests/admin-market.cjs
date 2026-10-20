const fs=require('fs'),path=require('path'),assert=require('assert'),{JSDOM}=require('jsdom');
(async()=>{
 let checks=0;const ok=(v)=>{assert.ok(v);checks++};
 const d=new JSDOM('<b id="qg-home-pending-markets"></b><div id="qg-market-admin"></div>',{url:'https://queuego.test/admin/#admin-market',runScripts:'outside-only'}),w=d.window;
 let user={id:'admin-a',type:'admin',status:'approved'},fail=false,delay=null,calls=[],reads=0;
 w.qgAdminShopCategory=s=>s?.public_category==='market'?'ตลาดสด':'ยังไม่ระบุประเภทร้าน';w.currentUser=()=>user;w.qtGetAccessToken=async()=> 'isolated-token';w.navigate=()=>{};w.toast=()=>{};w.confirm=()=>true;w.qgAdminInput=async()=> '2';
 w.qtSupabaseTable=async p=>{reads++;if(delay)await delay;if(p.startsWith('market_requests')){if(fail)throw Error('request read failed');return [{id:'request-a',requested_name:'ตลาดจริง',latitude:14,longitude:103,created_at:'2026-10-05',note:'ตรวจที่ตั้ง'}]}if(p.startsWith('shop_profiles'))return [{id:'shop-a',shop_name:'ร้าน',latitude:14,longitude:103,market_proof_path:'https://example.org/stall.jpg',markets:{name:'ตลาดจริง'}}];return []};
 w.qtSupabaseRpc=async(name,payload)=>{calls.push({name,payload});return {status:'approved'}};
 w.eval(fs.readFileSync(path.join(__dirname,'../admin/market.js'),'utf8'));
 await w.qgLoadMarketPendingCount();ok(w.document.querySelector('b').textContent==='2');
 await w.qgLoadMarketOperations();ok(w.document.querySelector('#qg-market-admin').textContent.includes('คำขอเพิ่มตลาดใหม่ (1)'));ok(w.document.querySelector('img').src==='https://example.org/stall.jpg');ok(w.document.querySelectorAll('button[onclick^="qgAdminViewLocation"]').length===2);ok(!w.document.querySelector('a[href*="google"]'));
 fail=true;await w.qgLoadMarketOperations();ok(w.document.querySelector('#qg-market-admin').textContent.includes('request read failed'));ok(!w.document.querySelector('#qg-market-admin').textContent.includes('ไม่มีคำขอ'));await w.qgLoadMarketPendingCount();ok(w.document.querySelector('b').textContent.includes('โหลดไม่ได้'));fail=false;
 await w.qgAdminReviewMarketRequest('request-a',true);ok(calls.length===1);ok(calls[0].name==='queuego_admin_review_market_request');ok(calls[0].payload.p_request_id==='request-a'&&calls[0].payload.p_assignment_radius_km===2);
 await w.qgAdminReviewMarket('shop-a',true);ok(calls.length===2&&calls[1].name==='queuego_admin_review_market_membership');
 let release;delay=new Promise(r=>release=r);const before=w.document.querySelector('b').textContent,task=w.qgLoadMarketPendingCount();user={...user,id:'admin-b'};release();await task;delay=null;ok(w.document.querySelector('b').textContent===before);
 // Fetch applicant labels only for visible request owners, never the full shop directory.
 user={id:'admin-b',type:'admin',status:'approved'};let paths=[];
 const ownerId='00000000-0000-4000-8000-000000000012';
 w.qtSupabaseTable=async p=>{paths.push(p);if(p.startsWith('market_requests?'))return [{id:'r',requested_name:'ตลาดใหม่',requester_shop_user_id:ownerId,created_at:'2026-10-20'}];if(p.includes('&user_id=in.('))return [{user_id:ownerId,shop_name:'ร้านผู้ขอที่ตรงกัน',public_category:'market'}];return []};
 await w.qgLoadMarketOperations();
 ok(paths.some(p=>p.includes('&user_id=in.('+ownerId+')&limit=50')));
 ok(!paths.includes('shop_profiles?select=user_id,shop_name,public_category,metadata&archived_at=is.null'));
 ok(w.document.querySelector('#qg-market-admin').textContent.includes('ร้านผู้ขอที่ตรงกัน'));
 // An older same-account request cannot paint over the newest refreshed queue.
 let hold;const gate=new Promise(r=>hold=r);let requestReads=0;
 w.qtSupabaseTable=async p=>{if(p.startsWith('market_requests?')){requestReads++;if(requestReads===1){await gate;return [{id:'old',requested_name:'OLD QUEUE',created_at:'2026-10-19'}]}return [{id:'new',requested_name:'NEW QUEUE',created_at:'2026-10-20'}]}return []};
 const oldLoad=w.qgLoadMarketOperations();await Promise.resolve();await w.qgLoadMarketOperations();hold();await oldLoad;
 ok(w.document.querySelector('#qg-market-admin').textContent.includes('NEW QUEUE'));
 ok(!w.document.querySelector('#qg-market-admin').textContent.includes('OLD QUEUE'));
 user={id:'customer',type:'customer',status:'approved'};const n=reads;await w.qgLoadMarketOperations();await w.qgAdminReviewMarketRequest('request-a',true);ok(reads===n&&calls.length===2);
 d.window.close();console.log(JSON.stringify({checks,failures:0,scope:'isolated existing Admin market RPC wiring, read errors, evidence and account race'}));
})().catch(e=>{console.error(e);process.exit(1)});
