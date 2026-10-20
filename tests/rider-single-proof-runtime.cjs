const fs=require('node:fs'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom');
const source=process.env.QG_RIDER_SOURCE?fs.readFileSync(process.env.QG_RIDER_SOURCE,'utf8'):fs.readFileSync('rider/index.html','utf8');
let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};
(async()=>{
 assert(source.includes('async function qgPrepareEvidenceImage(file)'),'web proof uploader must normalize camera images before upload');
 assert(source.includes("if(supported&&file.size<=5*1024*1024)return {blob:file"),'already-safe evidence should avoid needless recompression');
 assert(source.includes("/\\.(?:jpe?g|png|webp|heic|heif)$/i.test(name)"),'iPhone HEIC/HEIF files must enter normalization path');
 assert.equal(source.includes("if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>5*1024*1024)throw Error"),false,'legacy hard reject must stay removed');
 checks+=4;
 assert(source.includes('grid-template-columns:minmax(0,1fr) minmax(0,1fr)'),'proof preview should use compact two-column layout');
 assert(source.includes('height:124px;min-height:0'),'proof thumbnail should stay compact on normal mobile screens');
 checks+=2;
 // Execute the real uploader, including camera normalization and Storage errors.
 // Only browser decode and the network are replaced; no Production calls occur.
 {
  const dom=new JSDOM('<body></body>',{url:'https://queuego.invalid/rider/',runScripts:'outside-only'}),w=dom.window;
  const sent=[],converted=[];let decodeFailure=false,serverFailure=false;
  const token='header.'+Buffer.from(JSON.stringify({sub:'rider-proof-test'})).toString('base64url')+'.signature';
  Object.assign(w,{SB:{URL:'https://storage.invalid',KEY:'isolated-public-key'},getAccessToken:async()=>token,
   compressRiderImage:async(file,size,quality)=>{converted.push({file,size,quality});if(decodeFailure)throw Error('decode');return 'data:image/jpeg;base64,YWJj';},
   fetch:async(url,options)=>{sent.push({url,options});return {ok:!serverFailure,json:async()=>({message:'isolated storage denied'})};}
  });
  w.eval(source.match(/<script id="qg-rider-support">([\s\S]*?)<\/script>/)[1]);
  const safe=new w.File(['safe'],'proof.png',{type:'image/png'});
  eq(await w.qgUploadEvidence(null),null,'cancelled camera never uploads');
  await w.qgUploadEvidence(safe);
  eq(sent[0].options.body,safe,'safe image uploads unchanged');
  eq(converted.length,0,'safe image avoids decode');
  eq(sent[0].options.headers['Content-Type'],'image/png','safe MIME preserved');
  eq(sent[0].options.headers['x-upsert'],'false','proof cannot overwrite an existing object');
  assert.match(sent[0].url,/\/qg-evidence\/rider-proof-test\/.+\.png$/);checks++;
  for(const file of [new w.File(['heic'],'camera.HEIC',{type:'image/heic'}),{name:'camera.HEIF',type:'',size:10},{name:'large.jpg',type:'image/jpeg',size:6*1024*1024}]){
   await w.qgUploadEvidence(file);
   const last=sent.at(-1);
   eq(last.options.headers['Content-Type'],'image/jpeg','normalized MIME matches JPEG body');
   eq(last.options.body.type,'image/jpeg','decoded image uploads as JPEG Blob');
   eq(last.options.body.size,3,'normalized body is the decoded image bytes');
   assert.match(last.url,/\.jpg$/);checks++;
  }
  eq(converted.length,3,'HEIC, blank-MIME HEIF and oversized JPEG all normalize');
  const count=sent.length;
  for(const file of [{name:'document.pdf',type:'application/pdf',size:3},{name:'huge.jpg',type:'image/jpeg',size:31*1024*1024}]){
   await assert.rejects(w.qgUploadEvidence(file));checks++;
  }
  eq(sent.length,count,'invalid or oversized sources never reach Storage');
  decodeFailure=true;
  await assert.rejects(w.qgUploadEvidence({name:'bad.heic',type:'image/heic',size:10}),/แปลงรูป/);checks++;
  eq(sent.length,count,'failed camera decode never uploads');
  decodeFailure=false;serverFailure=true;
  await assert.rejects(w.qgUploadEvidence(safe),/isolated storage denied/);checks++;
  w.close();
 }
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
