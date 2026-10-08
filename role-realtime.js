/* Role Realtime shares the existing hydration queue and application session. */
(()=>{
  const admin=typeof QT_ADMIN_SESSION_KEY!=='undefined',role=admin?'admin':'shop',sessionKey=admin?QT_ADMIN_SESSION_KEY:QT_SHOP_SESSION_KEY;
  const tables=admin?['users','shop_profiles','technician_profiles','rider_profiles','products','orders','order_items','deliveries','payments','notifications','order_chat_messages','quotations','promotions','settlements','audit_logs','order_audit','market_orders','market_order_pickups','market_requests','laundry_orders','laundry_rider_jobs','laundry_rider_invites','queuego_platform_rules','route_bundles']:['orders','order_items','deliveries','notifications','order_chat_messages','market_orders','market_order_pickups','laundry_orders','laundry_rider_jobs','laundry_rider_invites'];
  let owner=null,version=0,client=null,channel=null,attachTask=null,retryTimer=null,refreshTimer=null,refreshing=false,queued=false,cancelSdk=null,pageActive=true;
  const desired=()=>{const s=qtSessionRead();const paused=!admin&&['pos','pos-staff-join'].includes(location.hash.slice(1).split('/')[0]);return !paused&&pageActive&&!document.hidden&&navigator.onLine&&s?.role===role&&s.authUserId&&s.userId?s.authUserId+':'+s.userId:null};
  const current=(epoch,actor)=>epoch===version&&actor===owner&&actor===desired();
  function status(value){window.QT_REALTIME_STATUS=value;document.documentElement.dataset.qtRealtime=value.toLowerCase();window.dispatchEvent(new CustomEvent('qt:realtime-status',{detail:{status:value}}))}
  function retire(){const c=client,ch=channel;client=null;channel=null;if(ch)c?.removeChannel(ch)}
  function stop(){version++;cancelSdk?.();retire();clearTimeout(retryTimer);clearTimeout(refreshTimer);retryTimer=refreshTimer=null;attachTask=null;owner=null;queued=false;status('PAUSED')}
  function retry(){if(retryTimer!==null||!desired())return;const epoch=version;retryTimer=setTimeout(()=>{retryTimer=null;if(epoch===version)sync()},15000)}
  function scheduleRefresh(reason){if(!desired())return;if(refreshing){queued=true;return}if(refreshTimer!==null)return;const epoch=version,actor=owner;refreshTimer=setTimeout(()=>{refreshTimer=null;if(current(epoch,actor))refresh(reason)},600)}
  async function refresh(reason){
    const epoch=version,actor=owner;if(!current(epoch,actor))return;
    if(QT_SYNC.busy||QT_DB_HYDRATING||QT_SYNC.pending>0||Date.now()-QT_SYNC.lastWrite<2500){scheduleRefresh(reason);return}
    refreshing=true;QT_SYNC.busy=true;QT_SYNC.dirty={};
    try{const before=qtSyncSignature(),seenNotifications=!admin?new Set((QT_DB_CACHE.qt_notifications||[]).map(n=>n.id)):null;await qtHydrateDatabase({light:false});if(!current(epoch,actor))return;Object.keys(QT_SYNC.dirty).forEach(k=>QT_DB_CACHE[k]=QT_SYNC.dirty[k]);if(!admin){if(typeof qtProcessLiveNotifications==='function')qtProcessLiveNotifications();else if(typeof qtHandleNewNotification==='function')(QT_DB_CACHE.qt_notifications||[]).filter(n=>!n.read&&!seenNotifications.has(n.id)).forEach(n=>qtHandleNewNotification(n))}qtSyncRerender(before,qtSyncSignature());if(!admin&&typeof qtUpdateNotificationBadge==='function')qtUpdateNotificationBadge();window.dispatchEvent(new CustomEvent('qt:realtime-update',{detail:{reason}}))}
    catch(e){if(current(epoch,actor)){console.warn('[QueueGo] live refresh failed');retry()}}
    finally{QT_SYNC.dirty={};QT_SYNC.busy=false;refreshing=false;if(current(epoch,actor)&&queued){queued=false;scheduleRefresh('queued')}}
  }
  function sdk(){if(window.supabase?.createClient)return Promise.resolve();return new Promise((resolve,reject)=>{
    const sc=document.createElement('script');let done=false;
    const finish=e=>{if(done)return;done=true;clearTimeout(timer);sc.onload=sc.onerror=null;if(cancelSdk===cancel)cancelSdk=null;if(e){sc.remove();reject(e)}else resolve()};
    const cancel=()=>finish(Error('Stopped'));cancelSdk=cancel;const timer=setTimeout(()=>finish(Error('SDK timeout')),10000);
    sc.src='https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.49.10/dist/umd/supabase.min.js';sc.onload=()=>finish();sc.onerror=()=>finish(Error('SDK unavailable'));document.head.append(sc);
  })}
  function attach(){
    if(attachTask||!owner)return;const epoch=version,actor=owner;
    const task=(async()=>{
      if(channel){const c=client,ch=channel,token=await qtGetAccessToken();if(current(epoch,actor)&&ch===channel&&c===client){if(!token)throw Error('Session unavailable');await c.realtime.setAuth(token)}return}
      await sdk();if(!current(epoch,actor))return;
      const c=window.supabase.createClient(QT_SUPABASE_CONFIG.URL,QT_SUPABASE_CONFIG.KEY,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false},realtime:{params:{eventsPerSecond:20}}});
      const token=await qtGetAccessToken();if(!current(epoch,actor)){await c.removeAllChannels();return}if(!token)throw Error('Session unavailable');await c.realtime.setAuth(token);if(!current(epoch,actor)){await c.removeAllChannels();return}
      client=c;const ch=c.channel('qg-'+role+'-'+crypto.randomUUID());channel=ch;
      tables.forEach(table=>ch.on('postgres_changes',{event:'*',schema:'public',table},payload=>{if(current(epoch,actor)&&channel===ch)scheduleRefresh(table+':'+payload.eventType)}));
      ch.subscribe(value=>{if(!current(epoch,actor)||channel!==ch)return;status(value);if(value==='SUBSCRIBED'){clearTimeout(retryTimer);retryTimer=null;scheduleRefresh('connected')}else if(['CHANNEL_ERROR','TIMED_OUT','CLOSED'].includes(value)){retire();retry()}});
    })().catch(()=>{if(current(epoch,actor)){status('CHANNEL_ERROR');retry()}}).finally(()=>{if(attachTask===task)attachTask=null});attachTask=task;
  }
  function sync(){const actor=desired();if(actor!==owner){stop();owner=actor}if(actor)attach()}
  function resume(){sync();scheduleRefresh('resume')}
  window.QT_REALTIME_FORCE_REFRESH=()=>{sync();scheduleRefresh('manual')};
  window.addEventListener('online',resume);window.addEventListener('offline',sync);window.addEventListener('hashchange',sync);window.addEventListener('pagehide',()=>{pageActive=false;stop()});window.addEventListener('pageshow',()=>{pageActive=true;resume()});
  window.addEventListener('storage',e=>{if(e.key===sessionKey)resume()});document.addEventListener('visibilitychange',()=>document.hidden?sync():resume());
  setInterval(()=>{sync();if(role==='shop'&&desired())scheduleRefresh('poll')},60000);setTimeout(sync,800);
})();
