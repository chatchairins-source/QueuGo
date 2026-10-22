const fs=require('node:fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const source=process.env.QG_RIDER_SOURCE?fs.readFileSync(process.env.QG_RIDER_SOURCE,'utf8'):fs.readFileSync('rider/index.html','utf8');
let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};
(async()=>{
 for(const mode of ['pickup','delivery']){
  const dom=new JSDOM('<body></body>',{url:'https://queuego.invalid/rider/',runScripts:'outside-only'}),w=dom.window;
  let uploads=0,mutations=0,failNext=true,persists=0,clears=0,confirmations=0;
  const paths=[];const order={id:'order-'+mode,status:mode==='pickup'?'ready':'in_progress',subtotal:100,total_amount:125,_shop:{shop_name:'Isolated Merchant'},_customer:{name:'Isolated Customer'}};
  Object.assign(w,{
   S:{user:{id:'rider-a'},session:{authUserId:'auth-a',sessionId:'session-a'},pos:{lat:15,lng:103}},
   riderActionInFlight:new Set(),QTICON:{back:'',loc:'',jobs:'',user:'',phone:'',chat:''},esc:v=>String(v??''),statusFromRow:o=>o.status,
   qgShortOrder:o=>o.id,qgCleanOrderNote:()=>'',riderEarning:()=>25,toast(){},getAccessToken:async()=> 'isolated-token',
   qgRiderRefreshAfterCommit:async()=>{},qgRiderVoiceCall(){},openChat(){},
   qgPersistProofFile:async(_kind,_order,file)=>{persists++;return {file,data:'data:image/jpeg;base64,AA=='}},
   qgRestoreProofFile:async()=>null,
   qgClearProofFile:async()=>{clears++},
   qgConfirmPickupReceived:async()=>{confirmations++;return true},
   qgBindSlideConfirm:(root,onComplete)=>{root._isolatedComplete=onComplete},
   setTimeout:()=>0,
   sbTable:async(path,options)=>{
    if(path.startsWith('order_items?'))return [];
    if(path==='rpc/qg_rider_rating_summary')return [{average:4.9,count:10}];
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
  eq(page.querySelectorAll('input[type=file]').length,1,'one camera input per proof stage');
  const file=new w.File(['isolated-image'],'proof.jpg',{type:'image/jpeg'});
  Object.defineProperty(input,'files',{configurable:true,value:[file]});await input.onchange();
  eq(persists,1,'selected proof is durably persisted before progression');
  eq(button.disabled,false,'one selected photo enables the only proof-stage action');
  Object.defineProperty(input,'files',{configurable:true,value:[]});await input.onchange();
  eq(button.disabled,false,'cancelling retake keeps the existing photo');
  eq(page.querySelector('img').hidden,false,'cancelling retake keeps the preview');

  // Re-entering the same flow must not destroy the File or construct another camera page.
  await open(order);eq(w.document.getElementById(id),page,'same order resumes original proof page');
  page.querySelector('header button').onclick();eq(page.hidden,true,'back preserves proof in the current session');
  await open(order);eq(page.hidden,false,'reopen resumes selected proof');

  if(mode==='pickup'){
    await Promise.all([button.onclick(),button.onclick()]);
    eq(confirmations,1,'pickup double tap opens one received-food confirmation');
    eq(uploads,1,'pickup double tap uploads one proof');
    eq(mutations,1,'pickup double tap executes one mutation');
    eq(button.disabled,false,'failed pickup permits retry without camera');
    await button.onclick();
    eq(confirmations,2,'explicit pickup retry confirms received food again');
    eq(uploads,1,'pickup RPC retry reuses successfully uploaded proof');
    eq(mutations,2,'pickup explicit retry executes one more mutation');
    eq(paths[0],paths[1],'pickup retry reuses the same proof path');
    eq(w.document.getElementById(id),null,'successful pickup closes proof page');
  }else{
    await Promise.all([button.onclick(),button.onclick()]);
    const cash=w.document.getElementById('qg-cash-confirm');
    eq(!!cash,true,'delivery proof advances to the dedicated cash page');
    eq(mutations,0,'opening cash page does not complete delivery');
    eq(cash.querySelectorAll('input[type=range]').length,1,'cash page has one slide confirmation control');
    const slide=cash.querySelector('.qg-cash-confirm-slide');
    await slide._isolatedComplete().catch(()=>{});
    eq(uploads,1,'first cash confirmation uploads one proof');
    eq(mutations,1,'first cash confirmation executes one completion mutation');
    eq(!!w.document.getElementById('qg-cash-confirm'),true,'failed completion keeps the cash page for retry');
    await slide._isolatedComplete();
    eq(uploads,1,'delivery retry reuses successfully uploaded proof');
    eq(mutations,2,'delivery explicit retry executes one more mutation');
    eq(paths[0],paths[1],'delivery retry reuses the same proof path');
    eq(clears,1,'successful delivery clears durable proof');
    eq(w.document.getElementById('qg-cash-confirm'),null,'successful delivery closes cash page');
    eq(w.document.getElementById(id),null,'successful delivery closes proof page');
  }

  // Proofs cannot leak to a different signed-in account, order or session.
  order.status=mode==='pickup'?'ready':'in_progress';
  await open(order);const old=w.document.getElementById(id);
  w.S.session={authUserId:'auth-b',sessionId:'session-b'};w.S.user={id:'rider-b'};
  await open(order);eq(w.document.getElementById(id)===old,false,'new account gets a fresh page without the old photo');
  eq(w.document.getElementById(id).querySelector('.qg-flow-next').disabled,true,'new session cannot reuse old proof');
  w.close();
 }
 console.log(JSON.stringify({checks,failures:0,scope:'actual Rider DOM pickup/delivery: durable single proof, merchant confirmation, dedicated cash stage, retry same proof path and session fencing; no Production writes'}));
})().catch(e=>{console.error(e);process.exit(1)});
