/* Durable rider intents, authoritative recovery, and lifecycle restoration. */
(function(){
 const key=(type)=>S.user?.id?'qg-rider-'+type+':'+S.user.id:null;
 const read=type=>{try{return JSON.parse(localStorage.getItem(key(type))||'null')}catch(e){return null}};
 const write=(type,value)=>{const k=key(type);if(!k)return;if(value===null)localStorage.removeItem(k);else localStorage.setItem(k,JSON.stringify(value))};
 let mutationTask=null,resumeTask=null;
 const kinds={'rpc/rider_claim_order':'claim','rpc/market_rider_claim_group':'market_claim','rpc/rider_order_action':'order','rpc/market_rider_group_action':'market','rpc/market_rider_confirm_pickup_cash':'market_pickup'};
 function status(){const pending=read('intent'),box=document.getElementById('qg-rider-sync');if(box){box.textContent=pending?'กำลังตรวจสอบการบันทึกงาน — ไม่ต้องกดซ้ำ':S.snapshotStale?'ข้อมูลงานล่าสุดที่บันทึกไว้ · กำลังเชื่อมต่อ':'';box.hidden=!box.textContent}document.querySelectorAll('[id^="accept-"],[id^="next-"],[id^="complete-"],[id^="arrive-shop-"],[data-pickup]').forEach(b=>{if(pending||S.snapshotStale){b.dataset.recoveryLocked='1';b.disabled=true}else if(b.dataset.recoveryLocked){delete b.dataset.recoveryLocked;const isDone=b.dataset.pickup&&['PICKED_UP','CANCELLED'].includes((S.activeOrder?._marketPickups||[]).find(x=>x.pickup_id===b.dataset.pickup)?.status);b.disabled=!!isDone}})}
 const current=(user,session)=>S.user?.id===user&&riderSessionMatches(session);
 async function send(intent,token){return sbTable('rpc/qg_rider_action_once',{method:'POST',token,body:{p_request_id:intent.id,p_kind:intent.kind,p_payload:intent.payload}})}
 function completed(intent){if(intent.payload.p_action==='complete'&&S.activeOrder)S.completedSummary={userId:S.user.id,order:{...S.activeOrder,status:'completed'},at:Date.now()}}
 window.qgRiderMutation=async function(path,options){
  if(!kinds[path])return sbTable(path,options);
  if(mutationTask)throw Error('กำลังบันทึกงาน กรุณารอสักครู่');
  if(read('intent')){qgRecoverRiderIntent();throw Error('กำลังตรวจสอบรายการก่อนหน้า ไม่ต้องกดซ้ำ')}
  if(!navigator.onLine)throw Error('ไม่มีอินเทอร์เน็ต กรุณาเชื่อมต่อแล้วลองใหม่');
  const user=S.user?.id,session=S.session;if(!user||!current(user,session))throw Error('กรุณาเข้าสู่ระบบใหม่');
  const intent={id:crypto.randomUUID(),kind:kinds[path],payload:options.body,at:Date.now()};
  write('intent',intent);status();
  mutationTask=(async()=>{try{const result=await send(intent,options.token);if(!current(user,session))return;completed(intent);write('intent',null);return result}catch(e){if(current(user,session)){if(e.definitive){write('intent',null);throw e}e.pending=true;e.message='กำลังตรวจสอบการบันทึกงาน ไม่ต้องกดซ้ำ';toast(e.message);setTimeout(()=>qgRecoverRiderIntent(),1200)}throw e}finally{mutationTask=null;if(current(user,session))status()}})();return mutationTask;
 };
 window.qgRecoverRiderIntent=async function(){
  const intent=read('intent');if(!intent||mutationTask||!navigator.onLine)return;
  const user=S.user?.id,session=S.session;
  mutationTask=(async()=>{try{const token=await getAccessToken();if(!token||!current(user,session))return;
   const receipts=await sbTable('qg_rider_action_receipts?select=result&request_id=eq.'+encodeURIComponent(intent.id)+'&user_id=eq.'+encodeURIComponent(user),{token});if(!current(user,session))return;
   if(!receipts?.length)await send(intent,token);if(!current(user,session))return;
   completed(intent);write('intent',null);toast('ตรวจสอบแล้ว ระบบบันทึกงานเรียบร้อย');
  }catch(e){if(current(user,session)&&e.definitive){write('intent',null);toast('ยังไม่ได้บันทึกงาน: '+e.message)}}finally{mutationTask=null;if(current(user,session)){status();await refreshData();}}})();return mutationTask;
 };
 window.qgSaveRiderSnapshot=function(){if(!S.user||!S.riderProfile)return;S.snapshotStale=false;const order=S.activeOrder;try{write('snapshot',{at:Date.now(),profileId:S.riderProfile.id,order:order?{...order,_customer:order._customer?{id:order._customer.id,name:order._customer.name,phone:order._customer.phone}:null}:null});status()}catch(e){}}
 window.qgRestoreRiderSnapshot=function(){const saved=read('snapshot');if(saved?.profileId===S.riderProfile?.id&&saved.order&&Date.now()-saved.at<86400000&&!S.activeOrder){S.activeOrder=saved.order;S.snapshotStale=true}status()};
 window.qgRiderSyncHTML=()=>'<div id="qg-rider-sync" role="status" aria-live="polite" '+(!read('intent')&&!S.snapshotStale?'hidden':'')+'>'+(read('intent')?'กำลังตรวจสอบการบันทึกงาน — ไม่ต้องกดซ้ำ':S.snapshotStale?'ข้อมูลงานล่าสุดที่บันทึกไว้ · กำลังเชื่อมต่อ':'')+'</div>';
 window.qgRiderResume=function(){if(resumeTask||document.hidden||!navigator.onLine||!S.user)return resumeTask;const user=S.user.id,session=S.session;
  resumeTask=(async()=>{if(!await checkActiveSession()||!current(user,session))return;await qgRecoverRiderIntent();if(!current(user,session))return;await refreshData();if(!current(user,session))return;refreshRiderChat();if(read('navigation')||new URL(location.href).searchParams.has('order')){document.getElementById('chat-screen')?.remove();S.chatOrder=null;const stage=document.querySelector('.stage');if(stage)stage.style.display='block';switchTab('home');renderSheet();write('navigation',null)}window.qgSyncPushOwner?.()})().catch(()=>{}).finally(()=>{resumeTask=null});return resumeTask};
 document.addEventListener('click',e=>{const link=e.target.closest('a[href]');if(link&&S.activeOrder&&link.href.startsWith('https://www.google.com/maps/'))try{write('navigation',{orderId:S.activeOrder.id,at:Date.now()})}catch(e){}if((read('intent')||S.snapshotStale)&&e.target.closest('[id^=accept-],[id^=next-],[id^=complete-],[id^=arrive-shop-],[data-pickup]')){e.preventDefault();e.stopImmediatePropagation();toast('กำลังตรวจสอบข้อมูลงาน ไม่ต้องกดซ้ำ');qgRecoverRiderIntent()}},true);
 for(const name of ['online','pageshow','focus'])addEventListener(name,()=>qgRiderResume());
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)qgRiderResume()});
 const oldLogout=forceSessionLogout;forceSessionLogout=function(message){window.qgDisableRiderPush?.();try{write('snapshot',null)}catch(e){}return oldLogout(message)};
 const oldRender=renderSheet;renderSheet=function(){oldRender();status()};
})();
const qgReliabilityStyle=document.createElement('style');qgReliabilityStyle.textContent='#qg-rider-sync{padding:10px;border-radius:10px;background:#fff3cd;color:#745500;font-size:13px;margin:8px 0}.qg-delivery-details{padding:10px 0;font-size:14px;overflow-wrap:anywhere}.qg-delivery-details p{white-space:pre-line;line-height:1.5}.qg-delivery-details a{color:#b60c29}';document.head.appendChild(qgReliabilityStyle);
