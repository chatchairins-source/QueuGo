'use strict';
function targetUrl(data){
  const base=new URL(self.registration.scope);
  if(data?.role==='shop')return new URL('merchant/#orders',base).href;
  if(data?.role==='rider')return new URL('rider/#home',base).href;
  if(data?.referenceId)return new URL('#order/'+encodeURIComponent(data.referenceId),base).href;
  return new URL('#notifications',base).href;
}
self.addEventListener('push',event=>{
  event.waitUntil((async()=>{
    let data={};
    try{data=event.data?.json()||{}}catch(_){data={message:event.data?.text()||'มีรายการอัปเดต'}}
    const title=String(data.title||'QueueGo').slice(0,80);
    const body=String(data.message||'มีรายการอัปเดต').slice(0,220);
    await self.registration.showNotification(title,{
      body,
      tag:'queuego:'+String(data.notificationId||Date.now()),
      renotify:true,
      data:{role:data.role||'',referenceId:data.referenceId||null}
    });
  })());
});
self.addEventListener('notificationclick',event=>{
  event.notification.close();
  event.waitUntil((async()=>{
    const url=targetUrl(event.notification.data||{});
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