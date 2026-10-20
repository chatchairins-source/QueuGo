const fs=require('node:fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const source=process.env.QG_RIDER_SOURCE?fs.readFileSync(process.env.QG_RIDER_SOURCE,'utf8'):fs.readFileSync('rider/index.html','utf8');
let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};
(async()=>{
 assert(source.includes('async function qgPrepareEvidenceImage(file)'),'web proof uploader must normalize camera images before upload');
 assert(source.includes("if(supported&&file.size<=5*1024*1024)return {blob:file"),'already-safe evidence should avoid needless recompression');
 assert(source.includes("/\\.(?:jpe?g|png|webp|heic|heif)$/i.test(name)"),'iPhone HEIC/HEIF files must enter normalization path');
 assert.equal(source.includes("if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>5*1024*1024)throw Error"),false,'legacy hard reject must stay removed');
 checks+=4;
 for(const mode of ['pickup','delivery']){
  const dom=new JSDOM('<body></body>',{url:'https://queuego.invalid/rider/',runScripts:'outside-only'}),w=dom.window;
  let uploads=0,mutations=0,failNext=true;
  const paths=[];const order={id:'order-'+mode,status:mode==='pickup'?'ready':'in_progress',subtotal:100,total_amount:125,_shop:{shop_name:'Isolated Merchant'},_customer:{name:'Isolated Customer'}};
  Object.assign(w,{S:{user:{id:'rider-a'},session:{authUserId:'auth-a',sessionId:'session-a'},pos:{lat:15,lng:103}},
   riderActionInFlight:new Set(),QTICON:{back:'',loc:'',jobs:'',user:'',phone:'',chat:''},esc:v=>String(v??''),statusFromRow:o=>o.status,
   qgShortOrder:o=>o.id,qgCleanOrderNote:()=>'',riderEarning:()=>25,toast(){},getAccessToken:async()=> 'isolated-token',
   qgRiderRefreshAfterCommit:async()=>{},qgRiderVoiceCall(){},openChat(){},
   sbTable:async(path,options)=>{
    if(path.startsWith('order_items?'))return [];
    mutations++;paths.push(options.body.p_photo_path);
    if(failNext){failNext=false;throw Error('isolated lost response')}
    return 'in_progress';
   }
  });
  w.URL.createObjectURL=()=> 'blob:isolated-proof';w.URL.revokeObjectURL=()=>{};
  w.qgUploadEvidence=async file=>{assert(file);uploads++;return 'rider-a/single-proof.jpg';};
  w.eval(source.slice(source.indexOf('function qgCloseRiderFlowPage('),source.indexOf('function qgCleanOrderNote(')));
  const open=mode==='pickup'?w.qgOpenPickupVerify:w.qgOpenDeliveryConfirm;
  await open(order);
  const id=mode==='pickup'?'qg-pickup-verify':'qg-delivery-confirm',page=w.document.getElementById(id),input=page.querySelector('input[type=file]'),button=page.querySelector('.qg-flow-next');
  eq(page.querySelectorAll('input[type=file]').length,1,'one camera input per stage');
  const file=new w.File(['isolated-image'],'proof.jpg',{type:'image/jpeg'});
  Object.defineProperty(input,'files',{configurable:true,value:[file]});input.onchange();
  eq(button.disabled,false,'one selected photo enables the only submit button');
  Object.defineProperty(input,'files',{configurable:true,value:[]});input.onchange();
  eq(button.disabled,false,'cancelling retake keeps the existing photo');
  eq(page.querySelector('img').hidden,false,'cancelling retake keeps the preview');
  // Re-entering the same flow must not destroy the File or construct another camera page.
  await open(order);eq(w.document.getElementById(id),page,'same order resumes original proof page');
  page.querySelector('header button').onclick();eq(page.hidden,true,'back preserves proof in the current session');
  await open(order);eq(page.hidden,false,'reopen resumes selected proof');
  await Promise.all([button.onclick(),button.onclick()]);
  eq(uploads,1,'double tap uploads one photo');eq(mutations,1,'double tap executes one mutation');
  eq(button.disabled,false,'failed submission permits retry without camera');
  eq(w.document.getElementById('qg-rider-cash-confirm'),null,'no second cash/proof screen');
  await button.onclick();
  eq(uploads,1,'RPC retry reuses successfully uploaded proof');eq(mutations,2,'one explicit retry');eq(paths[0],paths[1],'same proof path on retry');
  eq(w.document.getElementById(id),null,'success closes proof page');
  // Proofs cannot leak to a different signed-in account, order or session.
  await open(order);const old=w.document.getElementById(id);
  w.S.session={authUserId:'auth-b',sessionId:'session-b'};w.S.user={id:'rider-b'};
  await open(order);eq(w.document.getElementById(id)===old,false,'new account gets a fresh page without the old photo');
  eq(w.document.getElementById(id).querySelector('.qg-flow-next').disabled,true,'new session cannot reuse old proof');
  w.close();
 }
 console.log(JSON.stringify({checks,failures:0,scope:'actual Rider DOM pickup/delivery: one photo/input/submit, cancel/back/reopen retention, duplicate click, failed RPC retry same path, session fencing; no Production writes'}));
})().catch(e=>{console.error(e);process.exit(1)});
