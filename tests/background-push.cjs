const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const runtime=read('queuego-push.js');
const sw=read('queuego-push-sw.js');
const customer=read('index.html');
const customerFeatures=read('customer-features.js');
const merchant=read('merchant/index.html');
const rider=read('rider/index.html');
const sql=read('supabase/migrations/20261006170833_queuego_background_push_unified.sql');
const nativeSql=read('supabase/migrations/20261006173656_queuego_native_push_transport.sql');
const retireLegacy=read('supabase/migrations/20261007043258_retire_legacy_rider_push_rpc_surface.sql');
const cronFix=read('supabase/migrations/20261007034201_switch_push_retry_cron_to_unified_worker.sql');
const edge=read('supabase/functions/queuego-push/index.ts');
const retired=read('supabase/functions/rider-web-push/index.ts');
const deletion=read('QueueGo-Pilot-Account-Deletion-Privacy.sql');
const riderOfferNotify=read('supabase/migrations/20261007152746_rider_offer_notify_and_retry_expired.sql');

const nativeWorkflow=read('.github/workflows/build-native-rider-pilot.yml');
const nativeSharedPush=read('native-android/shared/src/main/java/com/queuego/shared/NativePush.kt');
const nativeCustomerPush=read('native-android/customer/src/main/java/com/queuego/customer/CustomerPush.kt');
const nativeMerchantPush=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPush.kt');
const nativeRiderPush=read('native-android/rider/src/main/java/com/queuego/rider/RiderPush.kt');
const nativeCustomerManifest=read('native-android/customer/src/main/AndroidManifest.xml');
const nativeMerchantManifest=read('native-android/merchant/src/main/AndroidManifest.xml');
const nativeRiderManifest=read('native-android/rider/src/main/AndroidManifest.xml');

ok(runtime.includes("navigator.serviceWorker.register"),'shared runtime must register a Service Worker');
ok(runtime.includes("pushManager.subscribe"),'shared runtime must create a push subscription');
ok(runtime.includes("/functions/v1/queuego-push"),'shared runtime must use the unified push endpoint');
ok(runtime.includes("Notification.requestPermission"),'shared runtime must request notification permission from a user gesture');
ok(runtime.includes("unsubscribeLocal"),'shared runtime must support local cleanup after account deletion');
ok(sw.includes("addEventListener('push'"),'service worker must receive background push events');
ok(sw.includes("showNotification"),'service worker must display a system notification');
ok(sw.includes("notificationclick"),'service worker must route notification taps back into QueueGo');

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
ok(retireLegacy.includes("LEGACY_PUSH_RETIRED_USE_QUEUEGO_PUSH"),'legacy Rider push RPCs must hard-fail if called by an owner');
ok(/revoke all on function public\.qg_lease_rider_push\(\) from public, anon, authenticated, service_role/i.test(retireLegacy),'legacy Rider lease RPC must not remain executable');
ok(/revoke all on function public\.qg_finish_rider_push\(uuid,uuid,integer\) from public, anon, authenticated, service_role/i.test(retireLegacy),'legacy Rider finish RPC must not remain executable');
ok(edge.includes("FIREBASE_SERVICE_ACCOUNT_JSON")&&edge.includes("sendNativePush"),'edge worker must support authenticated FCM HTTP v1 delivery');
ok(edge.includes("function visibleOrderText"),'push worker must share visible QT-XXXX text normalization');
ok(edge.includes("title:visibleOrderText")&&edge.includes("body:visibleOrderText")&&edge.includes("message:visibleOrderText"),'push delivery must shorten canonical order numbers for native and web push');
ok(edge.includes("function visibleFour")&&edge.includes("parseInt(upper,16)%10000"),'Laundry legacy order numbers must normalize to numeric QT-XXXX in push');
ok(edge.includes("subscribe-native")&&edge.includes("unsubscribe-native"),'edge worker must own native token lifecycle');
{
  const subscribeStart=edge.indexOf("input.action==='subscribe-native'");
  const configAt=edge.indexOf('await config()',subscribeStart);
  const nativeUpsertAt=edge.indexOf("admin.from('qg_native_push_tokens').upsert",subscribeStart);
  ok(subscribeStart>=0&&configAt>subscribeStart&&nativeUpsertAt>configAt,
    'native subscription must bootstrap unified worker config before persisting an FCM token');
}
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
ok(riderOfferNotify.includes("insert into public.notifications(user_id,title,message,type,reference_id)"),'Rider offer dispatch must create a notification for unified push');
ok(riderOfferNotify.includes("h.outcome='expired' and h.resolved_at>now()-interval '30 seconds'"),'expired Rider offers must retry without the old 10-minute dead zone');
ok(riderOfferNotify.includes("h.outcome='declined' and h.resolved_at>now()-interval '10 minutes'"),'explicit Rider declines must keep the longer cooldown');

for(const [role,src,manifest,service] of [
  ['customer',nativeCustomerPush,nativeCustomerManifest,'QueueGoCustomerMessagingService'],
  ['merchant',nativeMerchantPush,nativeMerchantManifest,'QueueGoMerchantMessagingService'],
  ['rider',nativeRiderPush,nativeRiderManifest,'QueueGoRiderMessagingService'],
]){
  ok(src.includes('FirebaseMessagingService'),role+' Native app must use FirebaseMessagingService');
  ok(src.includes('onNewToken'),role+' Native app must handle FCM token rotation');
  ok(src.includes('onMessageReceived'),role+' Native app must handle foreground/data push');
  ok(manifest.includes('android.permission.POST_NOTIFICATIONS'),role+' Native manifest must declare notification permission');
  ok(manifest.includes(service),role+' Native manifest must register its FCM service');
  ok(manifest.includes('queuego_orders'),role+' Native manifest must retain the QueueGo notification channel');
}
ok(nativeSharedPush.includes('"subscribe-native"')&&nativeSharedPush.includes('"unsubscribe-native"'),'Native shared push client must bind and revoke FCM tokens through queuego-push');
ok(nativeSharedPush.includes('.put("sessionId", auth.session.sessionId)'),'Native push token registration must remain bound to the active app session');
ok(nativeWorkflow.includes('QG_FIREBASE_GOOGLE_SERVICES_JSON_B64'),'Native CI must consume Firebase config from the external Actions secret');
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider']){
  ok(nativeWorkflow.includes(id),'Native CI must validate Firebase client package '+id);
}
ok(nativeWorkflow.includes('Reject WebView'),'Native Android pipeline must retain the no-WebView gate');
ok(deletion.includes('delete from public.qg_push_subscriptions where user_id=v_user.id;'),'privacy source must remove unified web push subscriptions');
ok(deletion.includes('delete from public.qg_native_push_tokens where user_id=v_user.id;'),'privacy source must remove native push tokens');

console.log(JSON.stringify({checks,failures:0,scope:'Unified Web Push + Native Android FCM lifecycle, shared outbox, privacy cleanup and Native package gates'}));
