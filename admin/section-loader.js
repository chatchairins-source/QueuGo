/* Admin read orchestration only. Existing RLS/RPC authorization remains authoritative. */
(()=>{
'use strict';
const reads=new Map(),inflight=new Set();let readEpoch=0;
const abortError=()=>Object.assign(new Error('ยกเลิกการอ่านจากหมวดก่อนหน้า'),{name:'AbortError'});
function track(controller){const item={controller,scope:readScope()};inflight.add(item);return ()=>inflight.delete(item)}
function stopStale(all=false){
 const active=readScope();for(const [key,item]of reads)if(item.pending&&(all||item.scope!==active))reads.delete(key);
 for(const [key,item]of cache)if(item.promise&&(all||item.scope!==active))cache.delete(key);
 for(const item of inflight)if(all||item.scope!==active)item.controller.abort();
}
window.qgAdminClearReadCache=function(){readEpoch++;reads.clear()};
window.qgAdminRead=async function(path,options,read){
 if(String(options.method||'GET').toUpperCase()!=='GET'){window.qgAdminClearReadCache();try{return await read()}finally{window.qgAdminClearReadCache()}}
 const key=String(options.accessToken||'')+':'+path,entry=reads.get(key);
 if(entry?.pending&&entry.scope!==readScope()){entry.controller.abort();reads.delete(key)}
 else if(entry&&(entry.pending||Date.now()-entry.at<5000))return entry.promise;
 const version=readEpoch,controller=new AbortController(),release=track(controller),item={at:Date.now(),pending:true,scope:readScope(),controller};
 const abort=()=>controller.abort();if(options.signal?.aborted)abort();else options.signal?.addEventListener('abort',abort,{once:true});
 const timer=setTimeout(()=>controller.abort(Object.assign(new Error('อ่านข้อมูลนานเกินกำหนด'),{name:'TimeoutError'})),15000);
 item.promise=Promise.resolve().then(()=>{if(controller.signal.aborted)throw controller.signal.reason||abortError();return read(controller.signal)}).then(value=>{if(controller.signal.aborted)throw controller.signal.reason||abortError();return value}).catch(e=>{if(reads.get(key)===item)reads.delete(key);throw e}).finally(()=>{item.pending=false;clearTimeout(timer);release();options.signal?.removeEventListener('abort',abort)});
 if(reads.size>=100)reads.delete(reads.keys().next().value);reads.set(key,item);
 const value=await item.promise;if(version!==readEpoch&&reads.get(key)===item)reads.delete(key);return value;
};
const cache=new Map(),ttl=30000;
window.QG_ADMIN_AUDIT_PAGE=window.QG_ADMIN_AUDIT_PAGE||0;window.QG_ADMIN_NOTIFICATION_PAGE=window.QG_ADMIN_NOTIFICATION_PAGE||0;
let actor=null,epoch=0;
const identity=()=>{const s=qtSessionRead();return s?.role==='admin'?s.authUserId+':'+s.userId:null};
const section=()=>location.hash==='#admin-governance'?qtAdminCurrentSection():(location.hash.slice(1).split('/')[0]||'admin');
const directory=new Set(['qt-sec-approval','qt-sec-users','qt-sec-shops','qt-sec-security','qt-sec-delivery','admin-gp-rates']);
function readScope(){const key=section();return directory.has(key)?'directory:'+JSON.stringify([window.QG_ADMIN_DIRECTORY_QUERY||'',window.QG_ADMIN_DIRECTORY_CURSOR||'']):key==='qt-sec-overview'?'admin':key}
window.addEventListener('hashchange',()=>stopStale());window.addEventListener('qg:admin-section',()=>stopStale());
const metrics=window.QG_ADMIN_LOAD_METRICS=[];
function reset(){epoch++;stopStale(true);cache.clear();window.qgAdminClearReadCache();actor=identity();window.QG_ADMIN_SUMMARY=null;window.QG_ADMIN_DIRECTORY_CURSOR='';window.QG_ADMIN_DIRECTORY_QUERY='';history.length=0;window.QG_ADMIN_DIRECTORY_PAGE=null;window.QG_ADMIN_AUDIT_PAGE=0;window.QG_ADMIN_NOTIFICATION_PAGE=0;window.QG_ADMIN_AUDIT_PAGE_INFO=null;window.QG_ADMIN_NOTIFICATION_PAGE_INFO=null;}
async function resource(key,load,force){
 if(identity()!==actor)reset();if(!actor)return false;
 stopStale();const now=Date.now(),old=cache.get(key);if(old?.promise&&old.scope===readScope())return old.promise.then(changed=>{if(old.dirty&&identity()===actor&&readScope()===old.scope){old.dirty=false;return resource(key,load,true)}return changed});
 if(!force&&old?.at&&now-old.at<ttl)return false;
 const version=epoch,owner=actor,scope=readScope(),start=performance.now();
 const item={at:old?.at||0,scope};
 item.promise=(async()=>{try{await load(()=>epoch===version&&identity()===owner&&readScope()===scope);if(epoch!==version||identity()!==owner||readScope()!==scope)return false;item.at=item.dirty?0:Date.now();metrics.push({section:key,durationMs:Math.round(performance.now()-start)});if(metrics.length>100)metrics.shift();return true}finally{item.promise=null}})();
 cache.set(key,item);return item.promise;
}
async function count(path,token){
 const controller=new AbortController(),release=track(controller),timer=setTimeout(()=>controller.abort(Object.assign(new Error('อ่านสรุปนานเกินกำหนด'),{name:'TimeoutError'})),15000);
 try{const response=await fetch(QT_SUPABASE_CONFIG.URL+'/rest/v1/'+path,{method:'HEAD',headers:{...qtSupabaseHeaders(token),Prefer:'count=exact'},signal:controller.signal});
 if(!response.ok)throw Error('โหลดสรุปไม่สำเร็จ ('+response.status+')');
 const total=response.headers.get('content-range')?.split('/')[1];if(!/^\d+$/.test(total||''))throw Error('ระบบไม่ส่งจำนวนรายการ');return Number(total);
 }finally{clearTimeout(timer);release()}
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
  'admin-support':[],'admin-gp-rates':['shop_profiles'],
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
  window.qgAdminClearReadCache();const entry=cache.get(key==='qt-sec-overview'?'admin':directory.has(key)?'directory':key);if(entry){entry.at=0;entry.dirty=true;}
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
  const loaders={'admin-orders':()=>qgLoadAdminOrders(),'admin-gp':()=>qgLoadAdminGp(),'admin-cash':()=>qgLoadAdminCash(undefined),'admin-market':()=>qgLoadMarketOperations(),'admin-gp-rates':()=>qtPullGpRates()};
  if(loaders[key])await resource(key,loaders[key],force||!!reason);
 }
 // Orders, GP, cash, catalog and support have their own bounded loaders. Never hydrate unrelated tables here.
 return false;
};
let rendering=false;const ensures=new Map();
window.qgAdminEnsureSection=function(){
 if(rendering||!identity())return;
 const before=section(),owner=identity(),ensureKey=before+':'+owner;if(ensures.has(ensureKey))return;
 const task=window.qgAdminLoadSection().then(changed=>{
  if(!changed||before!==section()||owner!==identity())return;
  const focus=document.activeElement;
  if(focus&&/^(INPUT|SELECT|TEXTAREA)$/.test(focus.tagName))return;
  rendering=true;try{router()}finally{rendering=false}
 }).catch(e=>{if(e.name==='AbortError')return;const note=document.getElementById('qg-admin-refresh-note');if(note)note.textContent=e.message;else toast(e.message)}).finally(()=>{if(ensures.get(ensureKey)===task)ensures.delete(ensureKey)});ensures.set(ensureKey,task);
};
window.qgAdminAuditPage=async function(direction){
 if(section()!=='qt-sec-audit'||!identity())return;const current=Math.max(0,Number(window.QG_ADMIN_AUDIT_PAGE)||0),info=window.QG_ADMIN_AUDIT_PAGE_INFO||{};
 if(direction>0&&!info.more)return;window.QG_ADMIN_AUDIT_PAGE=Math.max(0,current+(direction>0?1:-1));window.qgAdminClearReadCache();cache.delete('qt-sec-audit');
 try{await window.qgAdminLoadSection(true);router()}catch(e){if(e.name!=='AbortError')toast(e.message)}
};
window.qgAdminNotificationPage=async function(direction){
 if(section()!=='notifications'||!identity())return;const current=Math.max(0,Number(window.QG_ADMIN_NOTIFICATION_PAGE)||0),info=window.QG_ADMIN_NOTIFICATION_PAGE_INFO||{};
 if(direction>0&&!info.more)return;window.QG_ADMIN_NOTIFICATION_PAGE=Math.max(0,current+(direction>0?1:-1));window.qgAdminClearReadCache();cache.delete('notifications');
 try{await window.qgAdminLoadSection(true);router()}catch(e){if(e.name!=='AbortError')toast(e.message)}
};
const history=[];
window.qgAdminDirectorySearch=async function(value){
 if(!identity()||!directory.has(section()))return;if(identity()!==actor)reset();
 const query=qgAdminDirectoryNormalizeQuery(value);if(query&&query.length<2)return toast('กรุณาระบุชื่อหรือเบอร์โทรอย่างน้อย 2 ตัวอักษร');
 window.QG_ADMIN_DIRECTORY_QUERY=query;window.QG_ADMIN_DIRECTORY_CURSOR='';history.length=0;stopStale();
 try{await window.qgAdminLoadSection(true);router()}catch(e){if(e.name!=='AbortError')toast(e.message)}
};
window.qgAdminDirectoryPage=async function(direction){
 const page=window.QG_ADMIN_DIRECTORY_PAGE;if(!page||!directory.has(section()))return;
 if(direction>0){if(!page.more)return;history.push(page.cursor);window.QG_ADMIN_DIRECTORY_CURSOR=page.last;}
 else{if(!history.length)return;window.QG_ADMIN_DIRECTORY_CURSOR=history.pop();}
 try{await window.qgAdminLoadSection(true);router()}catch(e){toast(e.message)}
};
window.addEventListener('storage',e=>{if(e.key===QT_ADMIN_SESSION_KEY)reset()});
})();
