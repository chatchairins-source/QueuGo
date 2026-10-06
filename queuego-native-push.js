(() => {
  'use strict';
  const SUPABASE_URL='https://pkypiqhlrmzocysgeqew.supabase.co';
  const PUBLISHABLE_KEY='sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre';
  const api=SUPABASE_URL+'/functions/v1/queuego-push';
  let cfg={role:'',getAccessToken:null};
  let listenersReady=false;
  let pendingRegistration=null;

  const plugin=()=>window.Capacitor?.Plugins?.PushNotifications||null;
  const platform=()=>String(window.Capacitor?.getPlatform?.()||'').toLowerCase();
  const supported=()=>Boolean(
    window.Capacitor?.isNativePlatform?.()&&
    ['android','ios'].includes(platform())&&
    plugin()
  );
  const deviceKey=()=>cfg.role?'qg_native_push_device_v1:'+cfg.role:'qg_native_push_device_v1';
  const enabledKey=()=>cfg.role?'qg_native_push_enabled_v1:'+cfg.role:'qg_native_push_enabled_v1';
  const deviceId=()=>{
    let id='';
    try{id=localStorage.getItem(deviceKey())||''}catch(_){}
    if(!/^[0-9a-f-]{36}$/i.test(id)){
      id=crypto.randomUUID();
      try{localStorage.setItem(deviceKey(),id)}catch(_){}
    }
    return id;
  };
  const setEnabled=value=>{
    try{localStorage.setItem(enabledKey(),value?'1':'0')}catch(_){}
  };
  const isEnabled=()=>{
    try{return localStorage.getItem(enabledKey())==='1'}catch(_){return false}
  };
  async function token(){
    if(typeof cfg.getAccessToken!=='function')throw Error('ยังไม่ได้เชื่อมบัญชีกับระบบแจ้งเตือน');
    const value=await cfg.getAccessToken();
    if(!value)throw Error('กรุณาเข้าสู่ระบบใหม่');
    return value;
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
  function routeFromData(data={}){
    const role=cfg.role;
    if(role==='shop'){location.hash='#orders';return}
    if(role==='rider'){location.hash='#home';return}
    const ref=String(data.referenceId||data.reference_id||'').trim();
    location.hash=ref?'#order/'+encodeURIComponent(ref):'#notifications';
  }
  async function ensureListeners(){
    if(listenersReady||!supported())return;
    const p=plugin();
    await p.addListener('registration',async registration=>{
      try{
        const value=String(registration?.value||'').trim();
        if(!value)throw Error('ไม่ได้รับ push token');
        await call('subscribe-native',{
          deviceId:deviceId(),
          platform:platform(),
          token:value
        });
        setEnabled(true);
        pendingRegistration?.resolve(true);
      }catch(error){
        pendingRegistration?.reject(error);
      }finally{
        pendingRegistration=null;
      }
    });
    await p.addListener('registrationError',error=>{
      setEnabled(false);
      pendingRegistration?.reject(Error(error?.error||'ลงทะเบียนการแจ้งเตือนไม่สำเร็จ'));
      pendingRegistration=null;
    });
    await p.addListener('pushNotificationActionPerformed',event=>{
      routeFromData(event?.notification?.data||{});
      window.dispatchEvent(new CustomEvent('queuego:native-push-action',{detail:event}));
    });
    await p.addListener('pushNotificationReceived',notification=>{
      window.dispatchEvent(new CustomEvent('queuego:native-push',{detail:notification}));
    });
    listenersReady=true;
  }
  async function permission(prompt=true){
    const p=plugin();
    let status=await p.checkPermissions();
    if(status?.receive==='prompt'&&prompt)status=await p.requestPermissions();
    return status?.receive==='granted';
  }
  async function ensureChannel(){
    if(platform()!=='android')return;
    try{
      await plugin().createChannel({
        id:'queuego_orders',
        name:'QueueGo',
        description:'แจ้งเตือนออเดอร์และงาน QueueGo',
        importance:5,
        visibility:1,
        vibration:true,
        sound:'default'
      });
    }catch(_){}
  }
  async function register(){
    await ensureListeners();
    await ensureChannel();
    return new Promise(async(resolve,reject)=>{
      const timeout=setTimeout(()=>{
        if(pendingRegistration){
          pendingRegistration=null;
          reject(Error('ลงทะเบียนการแจ้งเตือนหมดเวลา'));
        }
      },15000);
      pendingRegistration={
        resolve:value=>{clearTimeout(timeout);resolve(value)},
        reject:error=>{clearTimeout(timeout);reject(error)}
      };
      try{await plugin().register()}
      catch(error){
        clearTimeout(timeout);
        pendingRegistration=null;
        reject(error);
      }
    });
  }
  async function enable(options={}){
    if(!supported())throw Error('อุปกรณ์นี้ยังไม่รองรับ native push');
    const ok=await permission(options.prompt!==false);
    if(!ok){setEnabled(false);return false}
    return register();
  }
  async function resume(){
    if(!supported())return false;
    try{
      const ok=await permission(false);
      return ok?await register():false;
    }catch(error){
      console.warn('QueueGo native push resume unavailable',error);
      return false;
    }
  }
  async function disable(){
    if(!supported())return true;
    try{await call('unsubscribe-native',{deviceId:deviceId()})}catch(_){}
    try{await plugin().unregister()}catch(_){}
    try{await plugin().removeAllDeliveredNotifications()}catch(_){}
    setEnabled(false);
    return true;
  }
  async function unsubscribeLocal(){
    if(!supported())return true;
    try{await plugin().unregister()}catch(_){}
    try{await plugin().removeAllDeliveredNotifications()}catch(_){}
    setEnabled(false);
    return true;
  }
  async function toggle(button){
    if(button)button.disabled=true;
    try{
      if(isEnabled()){
        await disable();
        if(button)button.textContent='เปิดการแจ้งเตือนเบื้องหลัง';
        return false;
      }
      const ok=await enable({prompt:true});
      if(button)button.textContent=ok?'ปิดการแจ้งเตือนเบื้องหลัง':'เปิดการแจ้งเตือนเบื้องหลัง';
      return ok;
    }finally{if(button?.isConnected)button.disabled=false}
  }
  async function syncButton(button){
    const el=typeof button==='string'?document.querySelector(button):button;
    if(!el)return;
    if(!supported()){el.disabled=true;el.textContent='อุปกรณ์นี้ไม่รองรับการแจ้งเตือน';return}
    el.textContent=isEnabled()?'ปิดการแจ้งเตือนเบื้องหลัง':'เปิดการแจ้งเตือนเบื้องหลัง';
  }
  function configure(options){
    cfg={...cfg,...options};
    if(!['customer','shop','rider'].includes(cfg.role))throw Error('invalid QueueGo native push role');
  }
  window.QueueGoNativePush=Object.freeze({
    configure,supported,enable,resume,disable,unsubscribeLocal,toggle,syncButton,isEnabled
  });
})();