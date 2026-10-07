const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const runtime=read('queuego-push.js');
const sw=read('queuego-push-sw.js');
const native=read('queuego-native-push.js');
const customer=read('index.html');
const customerFeatures=read('customer-features.js');
const merchant=read('merchant/index.html');
const rider=read('rider/index.html');
const workflow=read('.github/workflows/build-queuego-apks.yml');
const sql=read('supabase/migrations/20261006170833_queuego_background_push_unified.sql');
const nativeSql=read('supabase/migrations/20261006173656_queuego_native_push_transport.sql');
const cronFix=read('supabase/migrations/20261007034201_switch_push_retry_cron_to_unified_worker.sql');
const androidPkg=JSON.parse(read('android-build/package.json'));
const capacitor=JSON.parse(read('android-build/capacitor.config.json'));
const edge=read('supabase/functions/queuego-push/index.ts');
const retired=read('supabase/functions/rider-web-push/index.ts');
const deletion=read('QueueGo-Pilot-Account-Deletion-Privacy.sql');

ok(runtime.includes("navigator.serviceWorker.register"),'shared runtime must register a Service Worker');
ok(runtime.includes("pushManager.subscribe"),'shared runtime must create a push subscription');
ok(runtime.includes("/functions/v1/queuego-push"),'shared runtime must use the unified push endpoint');
ok(runtime.includes("Notification.requestPermission"),'shared runtime must request notification permission from a user gesture');
ok(runtime.includes("unsubscribeLocal"),'shared runtime must support local cleanup after account deletion');
ok(sw.includes("addEventListener('push'"),'service worker must receive background push events');
ok(sw.includes("showNotification"),'service worker must display a system notification');
ok(sw.includes("notificationclick"),'service worker must route notification taps back into QueueGo');
ok(runtime.includes('nativeSupported')&&runtime.includes('QueueGoNativePush.enable'),'shared QueueGoPush interface must delegate to native transport in Capacitor');
ok(native.includes("checkPermissions")&&native.includes("requestPermissions"),'native transport must enforce Android notification permission flow');
ok(native.includes("plugin().register()")&&native.includes("plugin().unregister()"),'native transport must register and revoke the Firebase token');
ok(native.includes("subscribe-native")&&native.includes("unsubscribe-native"),'native transport must bind tokens to the authenticated QueueGo account');
ok(native.includes("createChannel")&&native.includes("queuego_orders"),'native Android notifications must use an explicit channel');

ok(customer.includes("QueueGoPush.configure({role:'customer'"),'Customer must configure unified push');
ok(customerFeatures.includes('การแจ้งเตือนเบื้องหลัง'),'Customer profile must expose background push control');
ok(customerFeatures.includes('QueueGoPush.disable().finally'),'Customer logout must disable the current subscription');
ok(merchant.includes("QueueGoPush.configure({role:'shop'"),'Merchant must configure unified push');
ok(merchant.includes('การแจ้งเตือนเบื้องหลัง'),'Merchant profile must expose background push control');
ok(merchant.includes('await QueueGoPush.disable()'),'Merchant logout must disable the current subscription');
ok(rider.includes("QueueGoPush.configure({role:'rider'"),'Rider must configure unified push');
ok(rider.includes('qg-push-setting'),'Rider profile must expose background push control');
ok(rider.includes('await QueueGoPush.disable()'),'Rider logout must disable the current subscription');

ok(sql.includes('create table if not exists public.qg_push_subscriptions'),'unified subscription table must exist');
ok(sql.includes("role text not null check (role in ('customer','shop','rider'))"),'push subscriptions must be role bounded');
ok(sql.includes('create trigger qg_notifications_queuego_push'),'notifications must enqueue background push');
ok(sql.includes('drop trigger if exists qg_notifications_rider_push'),'rider-only runtime trigger must be retired');
ok(sql.includes('qg_push_subscriptions where user_id=v_user.id'),'account deletion must remove push subscriptions');
ok(edge.includes("allowedRoles=new Set(['customer','shop','rider'])"),'edge worker must restrict supported roles');
ok(edge.includes("qg_lease_push"),'edge worker must lease the unified outbox');
ok(edge.includes("qg_finish_push"),'edge worker must finish/retry leased jobs');
ok(edge.includes("FIREBASE_SERVICE_ACCOUNT_JSON")&&edge.includes("sendNativePush"),'edge worker must support authenticated FCM HTTP v1 delivery');
ok(edge.includes("subscribe-native")&&edge.includes("unsubscribe-native"),'edge worker must own native token lifecycle');
ok(edge.includes("input.action==='test'")&&edge.includes("type:'push_test'"),'edge worker must provide a rate-limited physical notification test');
ok(edge.includes("if((nativeCount||0)>0)")&&edge.includes("await firebaseAccess()")&&edge.includes("ระบบแจ้งเตือน Android ยังไม่พร้อม"),'native physical test must validate Firebase OAuth before scheduling a test notification');
ok(edge.includes("setTimeout(resolve,7000)"),'physical notification test must delay delivery long enough to background the app');
ok(customerFeatures.includes('ทดสอบการแจ้งเตือน'),'Customer profile must expose physical notification test');
ok(merchant.includes('ทดสอบการแจ้งเตือน'),'Merchant profile must expose physical notification test');
ok(rider.includes('qg-push-test'),'Rider profile must expose physical notification test');
ok(nativeSql.includes('create table if not exists public.qg_native_push_tokens'),'native push tokens must be server-owned');
ok(nativeSql.includes('native_token_id')&&nativeSql.includes('num_nonnulls(subscription_id,native_token_id)=1'),'one outbox must support exactly one web or native transport');
ok(nativeSql.includes('insert into public.qg_push_outbox(notification_id,native_token_id)'),'notification enqueue must include native tokens in the same outbox');
ok(cronFix.includes("'queuego-push-retry'")&&cronFix.includes("'SELECT public.qg_wake_push();'"),'push retry cron must wake unified worker');
ok(!cronFix.includes('/functions/v1/rider-web-push'),'push retry cron migration must not call retired rider-web-push');
ok(/revoke all on function public\.qg_wake_rider_push\(\) from public, anon, authenticated/i.test(cronFix),'legacy wake wrapper must stay client-inaccessible');
ok(/perform public\.qg_wake_push\(\)/i.test(cronFix),'legacy wake wrapper must delegate to unified worker');
ok(retired.includes("status:410"),'old Rider-only worker must be retired');

for(const asset of ['customer-features.js','customer-features.css','customer-laundry.js','account-deletion.js','queuego-ugc.js','queuego-native-push.js','queuego-push.js','queuego-push-sw.js']){
  ok(workflow.includes(asset),'Customer Android bundle must include '+asset);
}
const copyLines=workflow.split('\n').filter(line=>line.trim().startsWith('cp '));
const merchantCopy=copyLines.find(line=>line.includes('../role-realtime.js'))||'';
const riderCopy=copyLines.find(line=>line.includes('../queuego-native-push.js')&&!line.includes('../role-realtime.js')&&!line.includes('../customer-features.js'))||'';
for(const asset of ['../queuego-password-policy.js','../account-deletion.js','../queuego-ugc.js','../queuego-native-push.js','../queuego-push.js','../queuego-push-sw.js']){
  ok(merchantCopy.includes(asset),'Merchant Android bundle must copy '+asset);
  ok(riderCopy.includes(asset),'Rider Android bundle must copy '+asset);
}
ok(workflow.includes("s=s.replace('../queuego-password-policy.js','queuego-password-policy.js')")&&workflow.includes("s=s.replace('../queuego-ugc.js','queuego-ugc.js')")&&workflow.includes("s=s.replace('../queuego-native-push.js','queuego-native-push.js')")&&workflow.includes("s=s.replace('../queuego-push.js','queuego-push.js')"),'Android nested role bundles must rewrite shared UGC/push/password paths');
ok(workflow.includes('QG_FIREBASE_GOOGLE_SERVICES_JSON_B64')&&workflow.includes('android/app/google-services.json'),'Android release must inject Firebase app configuration securely');
ok(androidPkg.dependencies?.['@capacitor/push-notifications']==='8.0.0','Android build must install Capacitor 8 Push Notifications');
ok(Array.isArray(capacitor.plugins?.PushNotifications?.presentationOptions),'Capacitor config must enable native push presentation options');
ok(deletion.includes('delete from public.qg_push_subscriptions where user_id=v_user.id;'),'privacy source must remove unified web push subscriptions');
ok(deletion.includes('delete from public.qg_native_push_tokens where user_id=v_user.id;'),'privacy source must remove native push tokens');

console.log(JSON.stringify({checks,failures:0,scope:'Unified Customer/Merchant/Rider Web Push + Capacitor FCM lifecycle, shared outbox, privacy cleanup and Android bundle assets'}));
