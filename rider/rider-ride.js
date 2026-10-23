
/* QueueGo Rider Web Passenger Ride parity.
   Reuses shared dispatch, action receipts, QueueGo navigation, chat and cash confirmation. */
(function(){
  const baseEarning=riderEarning;
  const baseRouteTarget=qgRouteTarget;
  const baseJobCardHTML=jobCardHTML;
  const basePrimaryAction=qgPrimaryAction;

  window.qgIsPassengerRide=function(o){return o&&o.fulfillment_vertical==='ride'};

  riderEarning=function(o){
    if(window.qgIsPassengerRide(o)&&Number.isFinite(Number(o.rider_delivery_payout)))return Number(o.rider_delivery_payout);
    return baseEarning(o);
  };

  window.qgRideAction=async function(o,action,message){
    if(!window.qgIsPassengerRide(o)||riderActionInFlight.has(o.id))return false;
    riderActionInFlight.add(o.id);
    try{
      const token=await getAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
      const result=await qgRiderActionOnce('ride',{p_order_id:o.id,p_action:action},token);
      if(result&&result.status==='searching_rider'){
        toast('สิทธิ์หรือเครดิตรับเที่ยวไม่พร้อม · ระบบส่งเที่ยวกลับไปหาคนขับใหม่แล้ว');
        await qgRiderRefreshAfterCommit('passenger Ride requeue refresh failed');
        return true;
      }
      toast(message||'อัปเดตเที่ยวแล้ว');
      await qgRiderRefreshAfterCommit('passenger Ride action refresh failed');
      return true;
    }catch(e){
      toast('อัปเดตเที่ยวไม่สำเร็จ: '+(e.message||e));
      await qgRiderRefreshAfterCommit('passenger Ride failed-action reconciliation').catch(()=>{});
      return false;
    }finally{riderActionInFlight.delete(o.id)}
  };

  function qgRideDropoffDistanceMeters(o){
    const lat=Number(S.pos?.lat),lng=Number(S.pos?.lng),dlat=Number(o?.delivery_latitude),dlng=Number(o?.delivery_longitude);
    if(![lat,lng,dlat,dlng].every(Number.isFinite))return NaN;
    return haversineKm(lat,lng,dlat,dlng)*1000;
  }

  async function qgCompleteRideNow(o){
    return window.qgRideAction(o,'complete','จบเที่ยวแล้ว');
  }

  function qgOpenRideCompleteConfirm(o){
    document.getElementById('qg-ride-complete-confirm')?.remove();
    const meters=qgRideDropoffDistanceMeters(o);
    if(!Number.isFinite(meters)||meters<=100)return qgCompleteRideNow(o);
    const cash=Number(o?.total_amount||0);
    const overlay=document.createElement('section');
    overlay.id='qg-ride-complete-confirm';
    overlay.className='qg-confirm-overlay';
    overlay.innerHTML='<div class="qg-confirm-card" role="dialog" aria-modal="true" aria-labelledby="qg-ride-complete-title">'+
      '<h2 id="qg-ride-complete-title">ยังห่างจากจุดส่ง '+Math.round(meters).toLocaleString('th-TH')+' เมตร</h2>'+
      '<p>คุณอยู่ห่างจากจุดส่งเกิน 100 เมตร หากผู้โดยสารขอลงตรงนี้และเก็บเงินสด <b>฿'+cash.toLocaleString('th-TH')+'</b> แล้ว สามารถยืนยันจบเที่ยวได้</p>'+
      '<div class="qg-confirm-actions"><button type="button" class="qg-confirm-no">ยังไม่จบ</button><button type="button" class="qg-confirm-yes">ยืนยันจบเที่ยว</button></div></div>';
    const cancel=overlay.querySelector('.qg-confirm-no'),confirm=overlay.querySelector('.qg-confirm-yes');
    cancel.onclick=()=>overlay.remove();
    confirm.onclick=async()=>{
      if(confirm.disabled)return;
      confirm.disabled=true;cancel.disabled=true;confirm.textContent='กำลังจบเที่ยว...';
      const ok=await qgCompleteRideNow(o);
      if(ok)overlay.remove();
      else{confirm.disabled=false;cancel.disabled=false;confirm.textContent='ยืนยันจบเที่ยว'}
    };
    document.body.appendChild(overlay);
    return true;
  }

  qgRouteTarget=function(o){
    if(window.qgIsPassengerRide(o)){
      const pickup=statusFromRow(o)==='rider_assigned';
      return pickup
        ?{lat:Number(o?.pickup_latitude),lng:Number(o?.pickup_longitude),kind:'ridePickup',label:'นำทางไปจุดรับผู้โดยสาร'}
        :{lat:Number(o?.delivery_latitude),lng:Number(o?.delivery_longitude),kind:'rideDropoff',label:'นำทางไปจุดหมาย'};
    }
    return baseRouteTarget(o);
  };

  function rideCard(o,meta,active){
    const st=statusFromRow(o),fee=riderEarning(o),km=meta?.km??(S.pos?haversineKm(S.pos.lat,S.pos.lng,o.pickup_latitude,o.pickup_longitude):NaN);
    const mins=Number(o.ride_duration_minutes),code=qgShortOrder(o),cust=o._customer||{};
    if(!active){
      return '<article class="qg-offer-card"><div class="qg-offer-head"><div><span class="qg-kicker">เที่ยวโดยสาร</span><h2>'+esc(code)+'</h2></div><span class="qg-offer-badge">฿'+Number(fee||0).toLocaleString('th-TH')+'</span></div>'+
        '<div class="qg-job-row"><span>'+QTICON.user+'</span><div><small>รับผู้โดยสาร</small><b>'+esc(o.pickup_address||'จุดรับผู้โดยสาร')+'</b></div><em>จุดรับ</em></div>'+
        '<div class="qg-job-row"><span>'+QTICON.loc+'</span><div><small>ไปยังจุดหมาย</small><b>'+esc(o.delivery_address||'จุดหมาย')+'</b></div><em>จุดส่ง</em></div>'+
        '<div class="qg-offer-metrics"><div><span>ระยะไปจุดรับ</span><b>'+(Number.isFinite(km)?km.toFixed(1):'-')+' กม.</b></div><div><span>เวลาประมาณ</span><b>'+(Number.isFinite(mins)?Math.ceil(mins):'-')+' นาที</b></div><div><span>รายได้</span><b class="qg-red">฿'+Number(fee||0).toLocaleString('th-TH')+'</b></div></div>'+
        '<div class="qg-dual-actions"><button class="qg-btn qg-btn-secondary" id="decline-'+esc(o.id)+'">ปฏิเสธ</button><button class="qg-btn qg-btn-primary" id="accept-'+esc(o.id)+'">รับเที่ยว</button></div></article>';
    }
    const atPickup=!!o.rider_arrived_shop_at,atDrop=!!o.rider_arrived_customer_at,cash=Number(o.total_amount||0),dropMeters=qgRideDropoffDistanceMeters(o),nearDrop=Number.isFinite(dropMeters)&&dropMeters<=100;
    let title='เที่ยวโดยสาร',label='ทำรายการต่อ',disabled=false;
    if(st==='rider_assigned'){
      title=atPickup?'ถึงจุดรับผู้โดยสารแล้ว':'กำลังไปรับผู้โดยสาร';
      label=atPickup?'ผู้โดยสารขึ้นรถแล้ว · เริ่มเดินทาง':'ถึงจุดรับแล้ว';
    }else if(st==='in_progress'){
      title=atDrop?'ถึงจุดหมายแล้ว':'กำลังเดินทาง';
      label='จบเที่ยว · เก็บเงินสด ฿'+cash.toLocaleString('th-TH');
    }else disabled=true;
    return '<article class="qg-active-card"><div class="qg-active-head"><div><span class="qg-kicker">'+esc(title)+'</span><h2>'+esc(code)+'</h2></div><span class="qg-income-badge">฿'+Number(fee||0).toLocaleString('th-TH')+'</span></div>'+
      '<div class="qg-flow-card"><div class="qg-pickup-row"><span>'+QTICON.user+'</span><div><small>ผู้โดยสาร</small><b>'+esc(cust.name||'ผู้โดยสาร')+'</b></div></div>'+
      '<div class="qg-pickup-row"><span>'+QTICON.loc+'</span><div><small>จุดรับ</small><b>'+esc(o.pickup_address||'จุดรับผู้โดยสาร')+'</b></div></div>'+
      '<div class="qg-pickup-row"><span>'+QTICON.loc+'</span><div><small>จุดหมาย</small><b>'+esc(o.delivery_address||'จุดหมาย')+'</b></div></div></div>'+
      (st==='in_progress'?'<div class="qg-flow-cash"><small>เก็บเงินสดค่าโดยสาร</small><b>฿'+cash.toLocaleString('th-TH')+'</b><span>กดจบเที่ยวแล้วระบบจะถามยืนยันอีกครั้ง</span></div>':'')+
      '<div class="qg-job-tools"><button class="qg-job-tool" id="chat-'+esc(o.id)+'">'+QTICON.chat+'<span>ติดต่อผู้โดยสาร</span></button></div>'+
      '<button class="qg-btn qg-btn-primary qg-main-action '+(st==='in_progress'&&nearDrop?'qg-ride-complete-ready':'')+'" id="primary-action-'+esc(o.id)+'" '+(disabled?'disabled':'')+'>'+QTICON.loc+'<span>'+esc(label)+'</span></button></article>';
  }

  jobCardHTML=function(o,meta){
    const active=!!S.activeOrder&&String(S.activeOrder.id)===String(o?.id);
    return window.qgIsPassengerRide(o)?rideCard(o,meta,active):baseJobCardHTML(o,meta);
  };

  qgPrimaryAction=async function(o){
    if(!window.qgIsPassengerRide(o))return basePrimaryAction(o);
    const st=statusFromRow(o);
    if(st==='rider_assigned'){
      if(o.rider_arrived_shop_at)return window.qgRideAction(o,'start','เริ่มเดินทางแล้ว');
      return window.qgRideAction(o,'arrive_pickup','ถึงจุดรับผู้โดยสารแล้ว');
    }
    if(st==='in_progress')return qgOpenRideCompleteConfirm(o);
    return false;
  };
})();
