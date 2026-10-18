
/* QueueGo Rider Web Express parity.
   Reuses shared dispatch, QueueGo navigation, cash confirmation and qg-evidence. */
(function(){
  const baseEarning=riderEarning;
  const baseRouteTarget=qgRouteTarget;
  const baseJobCardHTML=jobCardHTML;
  const basePrimaryAction=qgPrimaryAction;
  const baseAdvanceOrder=window.advanceOrder;

  window.qgIsExpress=function(o){return o&&o.fulfillment_vertical==='express'};

  riderEarning=function(o){
    if(window.qgIsExpress(o)&&Number.isFinite(Number(o.rider_delivery_payout)))return Number(o.rider_delivery_payout);
    return baseEarning(o);
  };

  function proofKey(o,mode){return 'qg-rider-express-proof-v1:'+(S.user?.id||'rider')+':'+String(o?.id||'')+':'+mode}
  function readProof(o,mode){try{return localStorage.getItem(proofKey(o,mode))||null}catch(_){return null}}
  function saveProof(o,mode,path){try{localStorage.setItem(proofKey(o,mode),String(path))}catch(_){}}
  function clearProof(o,mode){try{localStorage.removeItem(proofKey(o,mode))}catch(_){}}

  window.qgExpressAction=async function(o,action,message){
    if(!window.qgIsExpress(o)||riderActionInFlight.has(o.id))return false;
    riderActionInFlight.add(o.id);
    try{
      const token=await getAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
      await qgRiderActionOnce('express',{p_order_id:o.id,p_action:action},token);
      toast(message||'อัปเดตงาน Express แล้ว');
      await qgRiderRefreshAfterCommit('rider Express action refresh failed');
      return true;
    }catch(e){toast('อัปเดตงาน Express ไม่สำเร็จ: '+(e.message||e));return false}
    finally{riderActionInFlight.delete(o.id)}
  };

  async function proofMutation(o,mode,file,preparedPath){
    if(!window.qgIsExpress(o))throw Error('ต้องเป็นงาน Express');
    if(!preparedPath&&!file)throw Error('กรุณาถ่ายรูปหลักฐานก่อน');
    if(typeof window.qgUploadEvidence!=='function')throw Error('ระบบอัปโหลดรูปยังไม่พร้อม');
    const path=preparedPath||await window.qgUploadEvidence(file);
    saveProof(o,mode,path);
    const token=await getAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
    const lat=Number.isFinite(Number(S.pos?.lat))?Number(S.pos.lat):null;
    const lng=Number.isFinite(Number(S.pos?.lng))?Number(S.pos.lng):null;
    const rpc=mode==='pickup'?'qg_express_pickup_with_photo':'qg_express_complete_with_photo';
    const kind=mode==='pickup'?'express-pickup-proof':'express-delivery-proof';
    const body={p_request_id:qgActionRequestId(kind,{p_order_id:o.id,p_photo_path:path}),p_order_id:o.id,p_photo_path:path,p_lat:lat,p_lng:lng};
    try{
      await sbTable('rpc/'+rpc,{method:'POST',token,body});
    }catch(failure){
      const rows=await sbTable('orders?select=id,status&id=eq.'+encodeURIComponent(o.id)+'&limit=1',{token}).catch(()=>[]);
      const status=rows?.[0]?.status;
      const table=mode==='pickup'?'qg_pickup_proofs':'qg_delivery_proofs';
      const proofs=await sbTable(table+'?select=order_id&order_id=eq.'+encodeURIComponent(o.id)+'&photo_path=eq.'+encodeURIComponent(path)+'&limit=1',{token}).catch(()=>[]);
      const committed=mode==='pickup'?['picked_up','in_progress','completed'].includes(status):status==='completed';
      if(!committed||!Array.isArray(proofs)||proofs.length!==1)throw failure;
    }
    clearProof(o,mode);
    return true;
  }

  function parcelSummary(o){
    return esc(o.express_item_type||'พัสดุ')+' · '+Number(o.express_weight_kg||0).toLocaleString('th-TH')+' กก. · '+esc(o.express_size||'');
  }

  window.qgExpressProofPage=function(o,mode){
    if(!window.qgIsExpress(o))return false;
    const pickup=mode==='pickup';
    if(pickup&&(statusFromRow(o)!=='rider_assigned'||!o.rider_arrived_shop_at))return false;
    if(!pickup&&(statusFromRow(o)!=='in_progress'||!o.rider_arrived_customer_at))return false;
    qgCloseRiderFlowPage('qg-express-proof');
    const page=document.createElement('section');page.id='qg-express-proof';page.className='qg-rider-flow-page qg-rider-flow-compact';
    const saved=readProof(o,mode),cash=Number(o.total_amount||0);
    const title=pickup?'รับพัสดุที่จุดรับ':'ส่งพัสดุที่จุดส่ง';
    const party=pickup?(o.express_sender_name||'ผู้ส่ง'):(o.express_receiver_name||'ผู้รับ');
    const address=pickup?(o.pickup_address||'จุดรับพัสดุ'):(o.delivery_address||'จุดส่งพัสดุ');
    const body=[];
    body.push('<header class="qg-flow-head"><button type="button" aria-label="ย้อนกลับ">'+QTICON.back+'</button><div><b>'+title+'</b><small>'+esc(qgShortOrder(o))+'</small></div><span></span></header>');
    body.push('<main class="qg-flow-body"><section class="qg-flow-hero"><span>Express ส่งด่วน</span><h1>'+(pickup?'ตรวจพัสดุและถ่ายรูป':'ถ่ายรูปก่อนส่งมอบ')+'</h1><p>ใช้รูปหลักฐานหนึ่งรูปต่อจุด ห้ามข้ามขั้นตอน</p></section>');
    body.push('<section class="qg-flow-card"><div class="qg-pickup-row"><span>'+QTICON.jobs+'</span><div><small>พัสดุ</small><b>'+parcelSummary(o)+'</b></div></div>');
    body.push('<div class="qg-pickup-row"><span>'+QTICON.user+'</span><div><small>'+(pickup?'ผู้ส่ง':'ผู้รับ')+'</small><b>'+esc(party)+'</b></div></div>');
    body.push('<div class="qg-pickup-row"><span>'+QTICON.loc+'</span><div><small>'+(pickup?'จุดรับ':'จุดส่ง')+'</small><b>'+esc(address)+'</b></div></div>');
    if(o.express_instructions)body.push('<div class="qg-pickup-row"><span>'+QTICON.jobs+'</span><div><small>หมายเหตุ</small><b>'+esc(o.express_instructions)+'</b></div></div>');
    body.push('</section>');
    if(!pickup)body.push('<section class="qg-flow-cash"><small>ค่าส่งเงินสดที่ต้องเก็บ</small><b>฿'+cash.toLocaleString('th-TH')+'</b><span>เก็บเงินตามยอดนี้ก่อนกดจบงาน</span></section>');
    if(saved)body.push('<section class="qg-flow-note"><small>กู้คืนหลักฐาน</small><b>มีรูปที่อัปโหลดไว้แล้วจากครั้งก่อน สามารถยืนยันต่อโดยไม่อัปโหลดซ้ำ</b></section>');
    body.push('<section class="qg-proof-box qg-proof-single '+(saved?'has-proof':'')+'"><div class="qg-flow-card-head"><b>'+(pickup?'รูปตอนรับพัสดุ':'รูปตอนส่งพัสดุ')+'</b><span>จำเป็น</span></div>');
    body.push('<label class="qg-proof-pick"><input id="qg-express-photo" type="file" accept="image/*" capture="environment" aria-label="'+(pickup?'ถ่ายรูปพัสดุที่รับ':'ถ่ายรูปพัสดุที่ส่ง')+'"><span class="qg-proof-plus">＋</span><b>'+(saved?'ถ่ายใหม่':pickup?'ถ่ายรูปพัสดุที่รับ':'ถ่ายรูปพัสดุที่ส่ง')+'</b><small>ให้เห็นพัสดุและจุด'+(pickup?'รับ':'ส่ง')+'ชัดเจน</small></label><img id="qg-express-preview" hidden alt="รูปหลักฐาน Express"></section>');
    body.push('<button type="button" class="qg-btn qg-btn-primary qg-flow-next" id="qg-express-proof-next" '+(saved?'':'disabled hidden')+'>'+(pickup?'ยืนยันรับพัสดุ':'ส่งพัสดุและจบงาน')+'</button>');
    body.push('<small class="qg-slide-caption">'+(pickup?'ถ่ายรูปพัสดุก่อนยืนยันรับ':'ถ่ายรูปที่จุดส่งและเก็บค่าส่งก่อนจบงาน')+'</small></main>');
    page.innerHTML=body.join('');
    document.body.appendChild(page);
    const back=page.querySelector('header button'),input=page.querySelector('#qg-express-photo'),preview=page.querySelector('#qg-express-preview'),next=page.querySelector('#qg-express-proof-next');
    back.onclick=()=>page.remove();
    input.onchange=()=>{
      const file=input.files?.[0],box=input.closest('.qg-proof-single');
      page._expressUploadPromise=null;
      if(page._expressProofUrl){URL.revokeObjectURL(page._expressProofUrl);page._expressProofUrl=null}
      if(!file){
        const retained=readProof(o,mode);preview.hidden=true;box?.classList.toggle('has-proof',!!retained);next.disabled=!retained;next.hidden=!retained;return;
      }
      page._expressProofUrl=URL.createObjectURL(file);preview.src=page._expressProofUrl;preview.hidden=false;box?.classList.add('has-proof');next.disabled=false;next.hidden=false;
      page._expressUploadPromise=window.qgUploadEvidence(file).then(path=>{saveProof(o,mode,path);return path});
    };
    next.onclick=async()=>{
      if(next.disabled)return;
      const commit=async()=>{
        next.disabled=true;
        try{
          const file=input.files?.[0]||null;
          let path=readProof(o,mode);
          if(page._expressUploadPromise)path=await page._expressUploadPromise;
          await proofMutation(o,mode,file,path);
          if(page._expressProofUrl)URL.revokeObjectURL(page._expressProofUrl);
          page.remove();
          if(!pickup)qgShowDeliverySuccess(o);else toast('รับพัสดุแล้ว · กดเริ่มนำส่ง');
          await qgRiderRefreshAfterCommit('rider Express proof refresh failed');
          return true;
        }catch(e){toast((pickup?'รับพัสดุ':'จบงาน Express')+'ไม่สำเร็จ: '+(e.message||e));next.disabled=false;return false}
      };
      if(!pickup){
        page.hidden=true;
        qgOpenCashConfirmPage({o,mode:'customer',amount:cash,onBack:()=>{page.hidden=false},onConfirm:async()=>{const ok=await commit();if(!ok)page.hidden=false;return ok}});
      }else await commit();
    };
    return true;
  };

  qgRouteTarget=function(o){
    if(window.qgIsExpress(o)){
      const pickup=statusFromRow(o)==='rider_assigned';
      return pickup?{lat:Number(o?.pickup_latitude),lng:Number(o?.pickup_longitude),kind:'expressPickup',label:'นำทางไปจุดรับพัสดุ'}:{lat:Number(o?.delivery_latitude),lng:Number(o?.delivery_longitude),kind:'expressDropoff',label:'นำทางไปจุดส่งพัสดุ'};
    }
    return baseRouteTarget(o);
  };

  function expressCard(o,meta,active){
    const st=statusFromRow(o),fee=riderEarning(o),km=meta?.km??(S.pos?haversineKm(S.pos.lat,S.pos.lng,o.pickup_latitude,o.pickup_longitude):NaN);
    const mins=Number(o.express_duration_minutes),code=qgShortOrder(o);
    if(!active){
      return '<article class="qg-offer-card"><div class="qg-offer-head"><div><span class="qg-kicker">Express ส่งด่วน</span><h2>'+esc(code)+'</h2></div><span class="qg-offer-badge">฿'+Number(fee||0).toLocaleString('th-TH')+'</span></div>'+
        '<div class="qg-job-row"><span>'+QTICON.jobs+'</span><div><small>รับพัสดุ</small><b>'+esc(o.pickup_address||'จุดรับพัสดุ')+'</b></div><em>จุดรับ</em></div>'+
        '<div class="qg-job-row"><span>'+QTICON.loc+'</span><div><small>ส่งพัสดุ</small><b>'+esc(o.delivery_address||'จุดส่งพัสดุ')+'</b></div><em>จุดส่ง</em></div>'+
        '<div class="qg-offer-metrics"><div><span>ระยะไปจุดรับ</span><b>'+(Number.isFinite(km)?km.toFixed(1):'-')+' กม.</b></div><div><span>เวลาประมาณ</span><b>'+(Number.isFinite(mins)?Math.ceil(mins):'-')+' นาที</b></div><div><span>รายได้</span><b class="qg-red">฿'+Number(fee||0).toLocaleString('th-TH')+'</b></div></div>'+
        '<div class="qg-dual-actions"><button class="qg-btn qg-btn-secondary" id="decline-'+esc(o.id)+'">ปฏิเสธ</button><button class="qg-btn qg-btn-primary" id="accept-'+esc(o.id)+'">รับงาน</button></div></article>';
    }
    const arrivedPickup=!!o.rider_arrived_shop_at,arrivedDrop=!!o.rider_arrived_customer_at;
    let title='งาน Express',label='นำทาง',disabled=false;
    if(st==='rider_assigned'){title=arrivedPickup?'ถึงจุดรับพัสดุแล้ว':'กำลังไปรับพัสดุ';label=arrivedPickup?'ถ่ายรูปและรับพัสดุ':qgNavigationStarted(o,'expressPickup')?'ถึงจุดรับแล้ว':'นำทางไปจุดรับพัสดุ'}
    else if(st==='picked_up'){title='รับพัสดุแล้ว';label='เริ่มนำส่ง'}
    else if(st==='in_progress'){title=arrivedDrop?'ถึงจุดส่งพัสดุแล้ว':'กำลังนำส่งพัสดุ';label=arrivedDrop?'ถ่ายรูปส่งและจบงาน':qgNavigationStarted(o,'expressDropoff')?'ถึงจุดส่งแล้ว':'นำทางไปจุดส่งพัสดุ'}
    else disabled=true;
    return '<article class="qg-active-card"><div class="qg-active-head"><div><span class="qg-kicker">'+esc(title)+'</span><h2>'+esc(code)+'</h2></div><span class="qg-income-badge">฿'+Number(fee||0).toLocaleString('th-TH')+'</span></div>'+
      '<div class="qg-flow-card"><div class="qg-pickup-row"><span>'+QTICON.jobs+'</span><div><small>พัสดุ</small><b>'+parcelSummary(o)+'</b></div></div>'+
      '<div class="qg-pickup-row"><span>'+QTICON.user+'</span><div><small>ผู้ส่ง</small><b>'+esc(o.express_sender_name||'ผู้ส่ง')+'</b></div></div>'+
      '<div class="qg-pickup-row"><span>'+QTICON.user+'</span><div><small>ผู้รับ</small><b>'+esc(o.express_receiver_name||'ผู้รับ')+'</b></div></div></div>'+
      '<div class="qg-active-status"><small>จุดรับพัสดุ</small><b>'+esc(o.pickup_address||'จุดรับพัสดุ')+'</b></div>'+
      '<div class="qg-active-status"><small>จุดส่งพัสดุ</small><b>'+esc(o.delivery_address||'จุดส่งพัสดุ')+'</b></div>'+
      (o.express_instructions?'<div class="qg-customer-note"><small>หมายเหตุ</small><b>'+esc(o.express_instructions)+'</b></div>':'')+
      '<div class="qg-job-tools"><button class="qg-job-tool" id="chat-'+esc(o.id)+'">'+QTICON.chat+'<span>แชต</span></button></div>'+
      '<button class="qg-btn qg-btn-primary qg-main-action" id="primary-nav-'+esc(o.id)+'" '+(disabled?'disabled':'')+'>'+QTICON.loc+'<span>'+esc(label)+'</span></button></article>';
  }

  jobCardHTML=function(o,meta){
    const active=!!S.activeOrder&&String(S.activeOrder.id)===String(o?.id);
    return window.qgIsExpress(o)?expressCard(o,meta,active):baseJobCardHTML(o,meta);
  };

  qgPrimaryAction=async function(o){
    if(!window.qgIsExpress(o))return basePrimaryAction(o);
    const st=statusFromRow(o);
    if(st==='rider_assigned'){
      if(o.rider_arrived_shop_at)return window.qgExpressProofPage(o,'pickup');
      if(qgNavigationStarted(o,'expressPickup'))return window.qgExpressAction(o,'arrive_pickup','ถึงจุดรับพัสดุแล้ว');
      return qgPrimaryNavigate(o);
    }
    if(st==='picked_up')return qgPrimaryNavigate(o);
    if(st==='in_progress'){
      if(o.rider_arrived_customer_at)return window.qgExpressProofPage(o,'delivery');
      if(qgNavigationStarted(o,'expressDropoff'))return window.qgExpressAction(o,'arrive_dropoff','ถึงจุดส่งพัสดุแล้ว');
      return qgPrimaryNavigate(o);
    }
    return false;
  };

  window.advanceOrder=async function(o){
    if(!window.qgIsExpress(o))return baseAdvanceOrder(o);
    const st=statusFromRow(o);
    if(st==='picked_up')return window.qgExpressAction(o,'start_transit','เริ่มนำส่งพัสดุแล้ว');
    if(st==='in_progress'){toast('กด “ถึงจุดส่งแล้ว” และถ่ายรูปเพื่อจบงาน');return false}
    return false;
  };
})();
