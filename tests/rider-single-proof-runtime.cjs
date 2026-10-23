const fs=require('node:fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const source=process.env.QG_RIDER_SOURCE?fs.readFileSync(process.env.QG_RIDER_SOURCE,'utf8'):fs.readFileSync('rider/index.html','utf8');
let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};
(async()=>{
 for(const mode of ['pickup','delivery']){
  const dom=new JSDOM('<body></body>',{url:'https://queuego.invalid/rider/',runScripts:'outside-only'}),w=dom.window;
  let uploads=0,mutations=0,clears=0,failNext=true;
  const paths=[];const order={id:'order-'+mode,status:mode==='pickup'?'ready':'in_progress',subtotal:100,total_amount:125,_shop:{shop_name:'Isolated Merchant'},_customer:{name:'Isolated Customer'}};
  Object.assign(w,{S:{user:{id:'rider-a'},session:{authUserId:'auth-a',sessionId:'session-a'},pos:{lat:15,lng:103}},
   riderActionInFlight:new Set(),QTICON:{back:'',loc:'',jobs:'',user:'',phone:'',chat:''},qgHeaderWordmark:()=>'',esc:v=>String(v??''),statusFromRow:o=>o.status,
   qgShortOrder:o=>o.id,qgCleanOrderNote:()=>'',riderEarning:()=>25,qgHeaderWordmark:(className='qg-header-wordmark')=>`<span class="${className}" aria-label="QueueGo"><span class="qg-wordmark-queue">Queue</span><span class="qg-wordmark-go">Go</span></span>`,toast(){},getAccessToken:async()=> 'isolated-token',
   qgRiderRefreshAfterCommit:async()=>{},qgRiderVoiceCall(){},openChat(){},
   qgPersistProofFile:async(kind,o,file)=>({file,data:'data:image/jpeg;base64,AA'}),
   qgRestoreProofFile:async()=>null,
   qgClearProofFile:async()=>{clears++},
   qgConfirmPickupReceived:async()=>true,
   qgBindSlideConfirm:(root,onComplete)=>{
    const input=root?.querySelector('input[type=range]');if(!input)return;
    let busy=false;
    input.oninput=()=>{};
    input.onchange=async()=>{
      if(busy)return;
      if(Number(input.value)!==100){input.value='0';return;}
      busy=true;input.disabled=true;
      try{await onComplete()}catch(_){input.disabled=false;input.value='0';busy=false}
    };
   },
   sbTable:async(path,options)=>{
    if(path.startsWith('order_items?'))return [];
    if(path==='rpc/qg_rider_rating_summary')return {average:4.9,count:12};
    mutations++;paths.push(options.body.p_photo_path);
    if(failNext){failNext=false;throw Error('isolated lost response')}
    return mode==='pickup'?'in_progress':'completed';
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
  Object.defineProperty(input,'files',{configurable:true,value:[file]});await input.onchange();
  eq(button.disabled,false,'one durably saved photo enables the only next button');
  Object.defineProperty(input,'files',{configurable:true,value:[]});await input.onchange();
  eq(button.disabled,false,'cancelling retake keeps the existing photo');
  eq(page.querySelector('img').hidden,false,'cancelling retake keeps the preview');
  await open(order);eq(w.document.getElementById(id),page,'same order resumes original proof page');
  page.querySelector('header button').onclick();eq(page.hidden,true,'back preserves proof in the current session');
  await open(order);eq(page.hidden,false,'reopen resumes selected proof');

  if(mode==='pickup'){
    await button.onclick();
    eq(uploads,1,'pickup uploads the durable proof once');
    eq(mutations,1,'pickup runs one mutation');
    eq(w.document.getElementById(id)!==null,true,'ambiguous pickup result keeps proof page for retry');
    await button.onclick();
    eq(uploads,1,'pickup RPC retry reuses successfully uploaded proof');
    eq(mutations,2,'pickup performs one explicit retry');
    eq(paths[0],paths[1],'pickup retry keeps the same immutable proof path');
    eq(clears,1,'pickup clears durable proof only after committed success');
    eq(w.document.getElementById(id),null,'pickup success closes proof page');
  }else{
    await button.onclick();
    const cash=w.document.getElementById('qg-cash-confirm'),slide=cash?.querySelector('input[type=range]');
    eq(!!cash,true,'delivery photo advances to the cash collection page');
    eq(mutations,0,'delivery does not complete before the cash slide');
    slide.value='100';await slide.onchange();
    eq(uploads,1,'delivery cash confirmation uploads the durable proof once');
    eq(mutations,1,'delivery cash slide runs one completion mutation');
    eq(w.document.getElementById(id)!==null,true,'ambiguous delivery result keeps proof page for retry');
    slide.value='100';await slide.onchange();
    eq(uploads,1,'delivery RPC retry reuses successfully uploaded proof');
    eq(mutations,2,'delivery performs one explicit retry');
    eq(paths[0],paths[1],'delivery retry keeps the same immutable proof path');
    eq(clears,1,'delivery clears durable proof only after committed success');
    eq(w.document.getElementById(id),null,'delivery success closes proof page');
  }

  await open(order);const old=w.document.getElementById(id);
  w.S.session={authUserId:'auth-b',sessionId:'session-b'};w.S.user={id:'rider-b'};
  await open(order);eq(w.document.getElementById(id)===old,false,'new account gets a fresh page without the old photo');
  eq(w.document.getElementById(id).querySelector('.qg-flow-next').disabled,true,'new session cannot reuse old proof');
  w.close();
 }
 console.log(JSON.stringify({checks,failures:0,scope:'actual Rider DOM pickup/delivery: one durable photo per stage, retake/back/session retention, pickup confirmation, delivery cash gate and immutable proof retry; no Production writes'}));
})().catch(e=>{console.error(e);process.exit(1)});
