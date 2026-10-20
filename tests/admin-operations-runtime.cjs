const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let n=0;n<12;n++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const dom=new JSDOM('<main id="app"></main>',{url:'https://isolated.invalid/admin/',runScripts:'outside-only'}),w=dom.window;
 let user={id:'00000000-0000-4000-8000-000000000001',type:'admin',status:'approved'},calls=[],denied=false,checks=0;
 w.currentUser=()=>user;w.navigate=()=>{};w.toast=()=>{};w.qgAdminInput=async()=> 'ตรวจเอกสารแล้ว';w.escapeHtml=v=>String(v??'');w.qtGetAccessToken=async()=> 'fixture';
 w.layout=(title,html)=>w.document.getElementById('app').innerHTML=html;
 w.qtSupabaseTable=async p=>{calls.push(p);return []};
 w.qtSupabaseRpc=async(name,args)=>{calls.push({name,args});if(denied)throw Error('permission denied');if(name==='queuego_admin_credit_accounts')return [];if(name==='queuego_admin_merchant_credit_targets')return [{user_id:'00000000-0000-4000-8000-000000000002',shops:'ร้าน',name:'เจ้าของ',promo_minor:0}];if(name==='queuego_admin_grant_merchant_credit')throw Error('lost response');if(name==='queuego_ride_config')return {enabled:false};if(name==='qg_admin_live_counts')return {shops_open:2,riders_online:1,orders:{pending:1}};if(name==='qg_admin_rider_reverification_queue')return [{rider_id:'rider',request_id:'request',status:'pending',selfie_path:'private/proof.jpg'}];return []};
 w.qgAdminOpenEvidence=()=>{};
 for(const file of ['admin/credit.js','admin/passenger-ride.js','admin/operations.js'])w.eval(fs.readFileSync(file,'utf8'));
 for(const page of ['admin-dispatch','admin-express','admin-laundry','admin-system']){calls=[];w.qgRenderAdminOperations(page);await tick();assert(w.document.querySelector('#qg-ops-status').textContent.includes('ล่าสุด'));assert(calls.length===1);checks+=2;if(page!=='admin-system'){assert(calls[0].includes('limit=100'));checks++}}
 calls=[];w.qgRenderAdminOperations('admin-ride');await tick();assert(calls.some(x=>x.name==='queuego_admin_public_licenses'));assert(w.document.querySelector('#qg-ride-policy'));checks+=2;
 const policy=w.document.querySelector('#qg-ride-policy');user={...user,id:'other'};await policy.onsubmit({preventDefault(){},currentTarget:policy});assert(!calls.some(x=>x.name==='queuego_admin_configure_ride'));checks++;
 user={id:'00000000-0000-4000-8000-000000000001',type:'admin',status:'approved'};calls=[];w.qgRenderAdminOperations('admin-rider-review');await tick();assert(calls.some(x=>x.name==='qg_admin_rider_reverification_queue'));checks++;
 const approve=[...w.document.querySelectorAll('#qg-review-list button')].find(b=>b.textContent==='อนุมัติ');await approve.onclick();assert(calls.some(x=>x.name==='qg_admin_review_rider_reverification'&&x.args.p_decision==='approved'&&x.args.p_note));checks++;
 w.renderAdminCreditCenter();await tick();const forms=w.document.querySelectorAll('#qg-credit-admin form'),form=forms[1];assert(form);checks++;
 const controls=form.querySelectorAll('select,input,textarea');controls[0].value='00000000-0000-4000-8000-000000000002';controls[1].value='1000';controls[2].value='เครดิตร้านใหม่';
 await form.onsubmit({preventDefault(){}});const first=calls.find(x=>x.name==='queuego_admin_grant_merchant_credit');assert(first);assert(w.localStorage.getItem('qg-admin-merchant-promo-v1:'+user.id));checks+=2;
 await form.onsubmit({preventDefault(){}});const grants=calls.filter(x=>x.name==='queuego_admin_grant_merchant_credit');assert.equal(grants.length,2);assert.equal(grants[0].args.p_request,grants[1].args.p_request);assert.equal(grants[0].args.p_amount_minor,100000);checks+=3;
 calls=[];user={...user,type:'customer'};await form.onsubmit({preventDefault(){}});w.qgRenderAdminOperations('admin-rider-review');await w.qgAdminLoadCredit();assert.equal(calls.length,0);checks++;
 user={...user,type:'admin'};denied=true;w.qgRenderAdminOperations('admin-system');await tick();assert(w.document.querySelector('#qg-ops-status').textContent.includes('permission denied'));checks++;
 dom.window.close();console.log(JSON.stringify({checks,failures:0,scope:'actual Credit/Ride/Rider/Express/Laundry/System DOM and existing RPC contracts, credit lost-response UUID reuse, actor fence and permission errors; isolated only'}));
})().catch(e=>{console.error(e);process.exit(1)});
