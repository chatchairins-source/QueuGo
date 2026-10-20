/* Admin read orchestration only. Existing RLS/RPC authorization remains authoritative. */
(()=>{
'use strict';
const reads=new Map();let readEpoch=0;
window.qgAdminClearReadCache=function(){readEpoch++;reads.clear()};
window.qgAdminRead=async function(path,options,read){
 if(String(options.method||'GET').toUpperCase()!=='GET'){window.qgAdminClearReadCache();try{return await read()}finally{window.qgAdminClearReadCache()}}
 const key=String(options.accessToken||'')+':'+path,entry=reads.get(key);
 if(entry&&Date.now()-entry.at<5000)return entry.promise;
 const version=readEpoch,item={at:Date.now()};
 item.promise=Promise.resolve().then(read).catch(e=>{if(reads.get(key)===item)reads.delete(key);throw e});
 if(reads.size>=100)reads.delete(reads.keys().next().value);reads.set(key,item);
 const value=await item.promise;if(version!==readEpoch&&reads.get(key)===item)reads.delete(key);return value;
};
const cache=new Map(),ttl=30000;
let actor=null,epoch=0;
const identity=()=>{const s=qtSessionRead();return s?.role==='admin'?s.authUserId+':'+s.userId:null};
const section=()=>location.hash==='#admin-governance'?qtAdminCurrentSection():(location.hash.slice(1).split('/')[0]||'admin');
const directory=new Set(['qt-sec-approval','qt-sec-users','qt-sec-shops','qt-sec-security','qt-sec-delivery','admin-gp-rates']);
const metrics=window.QG_ADMIN_LOAD_METRICS=[];
function reset(){epoch++;cache.clear();window.qgAdminClearReadCache();actor=identity();window.QG_ADMIN_SUMMARY=null;window.QG_ADMIN_DIRECTORY_CURSOR='';window.QG_ADMIN_DIRECTORY_PAGE=null;}
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
window.qgAdminRealtimeTables=function(){
 const key=section();
 if(key==='admin'||key==='qt-sec-overview')return ['users','orders','promotions','market_requests','shop_profiles'];
 if(directory.has(key))return ['users','shop_profiles','rider_profiles','technician_profiles'];
 const scopes={
  'admin-orders':['orders','deliveries','payments','market_orders','market_order_pickups','laundry_orders'],
  'admin-gp':['settlements','orders'],'admin-cash':['orders','payments','settlements'],
  'admin-support':['order_chat_messages'],'admin-gp-rates':['shop_profiles'],
  'admin-market':['market_requests','shop_profiles'],'admin-grocery':['products'],
  'notifications':['notifications'],'qt-sec-audit':['audit_logs'],'qt-sec-promote':['promotions'],
  'qt-sec-settings':['queuego_platform_rules'],'qt-sec-finance':['orders','settlements']
 };
 return scopes[key]||[];
};
window.qgAdminLoadSection=async function(force=false,reason=''){
 const key=section();
 if(reason&&reason.includes(':')){
  const table=reason.split(':')[0];
  if(!window.qgAdminRealtimeTables().includes(table))return false;
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
 if(key==='qt-sec-finance')return resource(key,async valid=>{const rows=await read('orders?select=id,order_number,metadata,total_amount,created_at&metadata->>refundStatus=eq.requested&order=created_at.desc&limit=100');if(valid())QT_DB_CACHE.qt_orders=(rows||[]).map(qtOrderFromRow)},force);
 if(key==='qt-sec-settings')return resource(key,()=>qtPullSystemSettings(),force);
 if(key==='notifications')return resource(key,async valid=>{
  const rows=await read('notifications?select=id,user_id,title,message,type,is_read,created_at,reference_id&user_id=eq.'+encodeURIComponent(qtSessionRead().userId)+'&order=created_at.desc&limit=100');
  if(valid())QT_DB_CACHE.qt_notifications=(rows||[]).map(n=>({id:n.id,userId:n.user_id,title:n.title,message:n.message,body:n.message,type:n.type,read:n.is_read,createdAt:new Date(n.created_at).getTime()}));
 },force);
 if(force||reason){
  const loaders={'admin-orders':()=>qgLoadAdminOrders(),'admin-gp':()=>qgLoadAdminGp(),'admin-cash':()=>qgLoadAdminCash(undefined),'admin-market':()=>qgLoadMarketOperations(),'admin-support':()=>qgLoadAdminSupport(),'admin-gp-rates':()=>qtPullGpRates()};
  if(loaders[key])await resource(key,loaders[key],force||!!reason);
 }
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
const history=[];
window.qgAdminDirectoryPage=async function(direction){
 const page=window.QG_ADMIN_DIRECTORY_PAGE;if(!page||!directory.has(section()))return;
 if(direction>0){if(!page.more)return;history.push(page.cursor);window.QG_ADMIN_DIRECTORY_CURSOR=page.last;}
 else{if(!history.length)return;window.QG_ADMIN_DIRECTORY_CURSOR=history.pop();}
 try{await window.qgAdminLoadSection(true);router()}catch(e){toast(e.message)}
};
window.addEventListener('storage',e=>{if(e.key===QT_ADMIN_SESSION_KEY)reset()});
})();
