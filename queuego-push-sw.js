'use strict';
function ownerStore(mode,fn){
  return new Promise((resolve,reject)=>{
    const request=indexedDB.open('queuego-push',1);
    request.onupgradeneeded=()=>request.result.createObjectStore('settings');
    request.onerror=()=>reject(request.error);
    request.onsuccess=()=>{
      const db=request.result,tx=db.transaction('settings',mode),store=tx.objectStore('settings');
      let value;const operation=fn(store);
      operation.onsuccess=()=>value=operation.result;
      tx.oncomplete=()=>{db.close();resolve(value)};
      tx.onerror=()=>{db.close();reject(tx.error)};
    };
  });
}
const getOwner=()=>ownerStore('readonly',s=>s.get('owner'));
function targetUrl(data){
  const base=new URL(self.registration.scope);
  if(data?.role==='shop')return new URL('merchant/#orders',base).href;
  if(data?.role==='rider')return new URL('rider/#home',base).href;
  if(data?.referenceId)return new URL('#order/'+encodeURIComponent(data.referenceId),base).href;
  return new URL('#notifications',base).href;
}
self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('message',event=>{
  if(event.data?.type==='QUEUEGO_OWNER'){
    event.waitUntil(ownerStore('readwrite',s=>s.put(event.data.userId||null,'owner')));
  }
});
self.addEventListener('push',event=>{
  event.waitUntil((async()=>{
    let data={};
    try{data=event.data?.json()||{}}catch(_){data={message:event.data?.text()||'มีรายการอัปเดต'}}
    if(!data.authUserId||await getOwner()!==data.authUserId)return;
    const title=String(data.title||'QueueGo').slice(0,80);
    const body=String(data.message||'มีรายการอัปเดต').slice(0,220);
    await self.registration.showNotification(title,{
      body,
      tag:'qg-'+String(data.notificationId||Date.now()),
      renotify:true,
      data:{authUserId:data.authUserId,role:data.role||'',referenceId:data.referenceId||null}
    });
    const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
    windows.forEach(client=>client.postMessage({type:'QUEUEGO_REFRESH',authUserId:data.authUserId}));
  })());
});
self.addEventListener('notificationclick',event=>{
  event.notification.close();
  event.waitUntil((async()=>{
    const data=event.notification.data||{};
    if(!data.authUserId||await getOwner()!==data.authUserId)return;
    const url=targetUrl(data);
    const windows=await clients.matchAll({type:'window',includeUncontrolled:true});
    for(const client of windows){
      if(new URL(client.url).origin===new URL(url).origin){
        await client.focus();
        if('navigate' in client)await client.navigate(url);
        return;
      }
    }
    await clients.openWindow(url);
  })());
});
