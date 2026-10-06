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
const workflow=read('.github/workflows/build-queuego-apks.yml');
const sql=read('supabase/migrations/20261006170833_queuego_background_push_unified.sql');
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
ok(retired.includes("status:410"),'old Rider-only worker must be retired');

ok(workflow.includes('customer-features.js ../customer-features.css ../customer-laundry.js ../account-deletion.js ../queuego-push.js ../queuego-push-sw.js'),'Customer Android bundle must include shared runtime files');
ok(workflow.includes("s=s.replace('../queuego-push.js','queuego-push.js')"),'Android nested role bundles must rewrite shared push paths');
ok(deletion.includes('delete from public.qg_push_subscriptions where user_id=v_user.id;'),'privacy source must remove unified push subscriptions');

console.log(JSON.stringify({checks,failures:0,scope:'Unified Customer/Merchant/Rider Web Push lifecycle, server outbox, privacy cleanup and Android bundle assets'}));
