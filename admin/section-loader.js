/* Admin read orchestration only. Existing RLS/RPC authorization remains authoritative. */
(()=>{
'use strict';
const cache=new Map(),ttl=30000;
let actor=null,epoch=0;
const identity=()=>{const s=qtSessionRead();return s?.role==='admin'?s.authUserId+':'+s.userId:null};
const section=()=>location.hash==='#admin-governance'?qtAdminCurrentSection():(location.hash.slice(1).split('/')[0]||'admin');
const directory=new Set(['qt-sec-approval','qt-sec-users','qt-sec-shops','qt-sec-security','qt-sec-delivery']);
const metrics=window.QG_ADMIN_LOAD_METRICS=[];
function reset(){epoch++;cache.clear();actor=identity();window.QG_ADMIN_SUMMARY=null;}
async function resource(key,load,force){
 if(identity()!==actor)reset();if(!actor)return false;
 const now=Date.now(),old=cache.get(key);if(old?.promise)return old.promise;
 if(!force&&old?.at&&now-old.at<ttl)return false;
 const version=epoch,owner=actor,start=performance.now();
 const item={at:old?.at||0};
 item.promise=(async()=>{try{await load(()=>epoch===version&&identity()===owner);if(epoch!==version||identity()!==owner)return false;item.at=Date.now();metrics.push({section:key,durationMs:Math.round(performance.now()-start)});if(metrics.length>100)metrics.shift();return true}finally{item.promise=null}})();
 cache.set(key,item);return item.promise;
}
async function count(path,token){
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
 try{const response=await fetch(QT_SUPABASE_CONFIG.URL+'/rest/v1/'+path,{method:'HEAD',headers:{...qtSupabaseHeaders(token),Prefer:'count=exact'},signal:controller.signal});
 if(!response.ok)throw Error('โหลดสรุปไม่สำเร็จ ('+response.status+')');
 const total=response.headers.get('content-range')?.split('/')[1];if(!/^\d+$/.test(total||''))throw Error('ระบบไม่ส่งจำนวนรายการ');return Number(total);
 }finally{clearTimeout(timer)}
}
async function summary(valid){
 const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
 const paths={orders:'orders?select=id',shops:'users?select=id&role=eq.shop',riders:'users?select=id&role=eq.rider',customers:'users?select=id&role=eq.customer',pendingShops:'users?select=id&role=eq.shop&status=eq.pending',pendingRiders:'users?select=id&role=eq.rider&status=eq.pending',promotions:'promotions?select=id&status=eq.pending'};
 const entries=await Promise.all(Object.entries(paths).map(async([k,p])=>[k,await count(p,token)]));
 if(valid())window.QG_ADMIN_SUMMARY=Object.fromEntries(entries);
}
async function read(path){return qtSupabaseTable(path,{accessToken:await qtGetAccessToken()})}
window.qgAdminLoadSection=async function(force=false,reason=''){
 const key=section();
 if(reason&&reason.includes(':')){
  const table=reason.split(':')[0];
  const used=key==='admin'||key==='qt-sec-overview'?['users','orders','promotions','market_requests','shop_profiles']:directory.has(key)?['users','shop_profiles','rider_profiles','technician_profiles','qg_rider_presence_events']:key==='qt-sec-audit'?['audit_logs']:key==='qt-sec-promote'?['promotions']:[];
  if(!used.includes(table))return false;
  const entry=cache.get(key==='qt-sec-overview'?'admin':directory.has(key)?'directory':key);if(entry)entry.at=0;
 }
 if(key==='admin'||key==='qt-sec-overview')return resource('admin',summary,force);
 if(directory.has(key)){
  const changed=await resource('directory',()=>qtHydrateAdminDirectory(),force);
  if(key==='qt-sec-delivery')await resource('presence',()=>qtRenderAdminRiderPresence(),force);
  return changed;
 }
 if(key==='qt-sec-audit')return resource(key,async valid=>{
  const rows=await read('audit_logs?select=id,user_id,action,entity_type,entity_id,description,created_at&order=created_at.desc,id.desc&limit=100');
  if(valid())QT_DB_CACHE.qt_audit_log=(rows||[]).map(x=>({id:x.id,actorId:x.user_id,action:x.action,entityType:x.entity_type,entityId:x.entity_id,description:x.description,at:new Date(x.created_at).getTime()}));
 },force);
 if(key==='qt-sec-promote')return resource(key,async valid=>{
  const rows=await read('promotions?select=*&order=created_at.desc&limit=100');
  if(valid())QT_DB_CACHE.qt_promotions=(rows||[]).map(x=>Object.assign({},x.metadata||{},{id:x.id,shopId:x.shop_id,shopName:x.shop_name,bidPrice:x.bid_price,status:x.status}));
 },force);
 if(key==='qt-sec-settings')return resource(key,()=>qtPullSystemSettings(),force);
 if(key==='notifications')return resource(key,async valid=>{
  const rows=await read('notifications?select=id,user_id,title,message,type,is_read,created_at,reference_id&user_id=eq.'+encodeURIComponent(qtSessionRead().userId)+'&order=created_at.desc&limit=100');
  if(valid())QT_DB_CACHE.qt_notifications=(rows||[]).map(n=>({id:n.id,userId:n.user_id,title:n.title,message:n.message,body:n.message,type:n.type,read:n.is_read,createdAt:new Date(n.created_at).getTime()}));
 },force);
 // Orders, GP, cash, catalog and support have their own bounded loaders. Never hydrate unrelated tables here.
 return false;
};
let rendering=false;
window.qgAdminEnsureSection=function(){
 if(rendering||!identity())return;
 const before=section(),owner=identity();
 window.qgAdminLoadSection().then(changed=>{
  if(!changed||before!==section()||owner!==identity())return;
  const focus=document.activeElement;
  if(focus&&/^(INPUT|SELECT|TEXTAREA)$/.test(focus.tagName))return;
  rendering=true;try{router()}finally{rendering=false}
 }).catch(e=>{const note=document.getElementById('qg-admin-refresh-note');if(note)note.textContent=e.message;else toast(e.message)});
};
window.addEventListener('storage',e=>{if(e.key===QT_ADMIN_SESSION_KEY)reset()});
})();
