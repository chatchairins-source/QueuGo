(() => {
  'use strict';
  const SUPABASE_URL='https://pkypiqhlrmzocysgeqew.supabase.co';
  const PUBLISHABLE_KEY='sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre';
  const scriptUrl=document.currentScript?.src||new URL('queuego-push.js',document.baseURI).href;
  const root=new URL('.',scriptUrl);
  const api=SUPABASE_URL+'/functions/v1/queuego-push';
  let cfg={role:'',getAccessToken:null};

  const supported=()=>('serviceWorker' in navigator)&&('PushManager' in window)&&('Notification' in window);
  const deviceKey=()=>cfg.role?'qg_push_device_v1:'+cfg.role:'qg_push_device_v1';
  const deviceId=()=>{
    let id='';
    try{id=localStorage.getItem(deviceKey())||''}catch(_){}
    if(!/^[0-9a-f-]{36}$/i.test(id)){
      id=crypto.randomUUID();
      try{localStorage.setItem(deviceKey(),id)}catch(_){}
    }
    return id;
  };
  const bytes=value=>{
    const pad='='.repeat((4-value.length%4)%4);
    const base64=(value+pad).replace(/-/g,'+').replace(/_/g,'/');
    const raw=atob(base64),out=new Uint8Array(raw.length);
    for(let i=0;i<raw.length;i++)out[i]=raw.charCodeAt(i);
    return out;
  };
  async function token(){
    if(typeof cfg.getAccessToken!=='function')throw Error('ยังไม่ได้เชื่อมบัญชีกับระบบแจ้งเตือน');
    const t=await cfg.getAccessToken();
    if(!t)throw Error('กรุณาเข้าสู่ระบบใหม่');
    return t;
  }
  async function call(action,body={}){
    const accessToken=await token();
    const response=await fetch(api,{
      method:'POST',
      headers:{apikey:PUBLISHABLE_KEY,Authorization:'Bearer '+accessToken,'Content-Type':'application/json'},
      body:JSON.stringify({action,...body}),
      signal:AbortSignal.timeout(15000)
    });
    const data=await response.json().catch(()=>({}));
    if(!response.ok)throw Error(data.error||'บริการแจ้งเตือนยังไม่พร้อม');
    return data;
  }
  async function registration(){
    if(!supported())throw Error('อุปกรณ์นี้ยังไม่รองรับการแจ้งเตือนเบื้องหลัง');
    const swUrl=new URL('queuego-push-sw.js',root);
    const reg=await navigator.serviceWorker.register(swUrl.href,{scope:root.pathname});
    await navigator.serviceWorker.ready;
    return reg;
  }
  async function currentSubscription(){
    if(!supported())return null;
    const reg=await registration();
    return reg.pushManager.getSubscription();
  }
  async function enable(options={}){
    const prompt=options.prompt!==false;
    if(!supported())throw Error('อุปกรณ์นี้ยังไม่รองรับการแจ้งเตือนเบื้องหลัง');
    let permission=Notification.permission;
    if(permission==='default'&&prompt)permission=await Notification.requestPermission();
    if(permission!=='granted')return false;
    const reg=await registration();
    const key=await call('public-key');
    let sub=await reg.pushManager.getSubscription();
    if(!sub){
      sub=await reg.pushManager.subscribe({
        userVisibleOnly:true,
        applicationServerKey:bytes(key.publicKey)
      });
    }
    const json=sub.toJSON();
    await call('subscribe',{
      deviceId:deviceId(),
      subscription:{endpoint:sub.endpoint,keys:{p256dh:json.keys?.p256dh||'',auth:json.keys?.auth||''}}
    });
    return true;
  }
  async function resume(){
    if(!supported()||Notification.permission!=='granted')return false;
    try{return await enable({prompt:false})}catch(e){console.warn('QueueGo push resume unavailable',e);return false}
  }
  async function disable(){
    if(!supported())return true;
    const sub=await currentSubscription().catch(()=>null);
    try{await call('unsubscribe',{deviceId:deviceId()})}catch(_){}
    if(sub)await sub.unsubscribe().catch(()=>false);
    return true;
  }
  async function unsubscribeLocal(){
    if(!supported())return true;
    const sub=await currentSubscription().catch(()=>null);
    if(sub)await sub.unsubscribe().catch(()=>false);
    return true;
  }
  async function toggle(button){
    if(button)button.disabled=true;
    try{
      const sub=await currentSubscription().catch(()=>null);
      if(sub){
        await disable();
        if(button)button.textContent='เปิดการแจ้งเตือนเบื้องหลัง';
        return false;
      }
      const ok=await enable();
      if(button)button.textContent=ok?'ปิดการแจ้งเตือนเบื้องหลัง':'เปิดการแจ้งเตือนเบื้องหลัง';
      return ok;
    }finally{if(button?.isConnected)button.disabled=false}
  }
  async function syncButton(button){
    const el=typeof button==='string'?document.querySelector(button):button;
    if(!el)return;
    if(!supported()){el.disabled=true;el.textContent='อุปกรณ์นี้ไม่รองรับการแจ้งเตือน';return}
    const sub=await currentSubscription().catch(()=>null);
    el.textContent=sub?'ปิดการแจ้งเตือนเบื้องหลัง':'เปิดการแจ้งเตือนเบื้องหลัง';
  }
  function configure(options){
    cfg={...cfg,...options};
    if(!['customer','shop','rider'].includes(cfg.role))throw Error('invalid QueueGo push role');
    queueMicrotask(()=>resume());
  }
  window.QueueGoPush=Object.freeze({configure,enable,resume,disable,toggle,syncButton,unsubscribeLocal,supported});
})();