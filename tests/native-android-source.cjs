const fs=require('fs'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

for(const p of [
  'settings.gradle.kts',
  'build.gradle.kts',
  'shared/build.gradle.kts',
  'shared/src/main/java/com/queuego/shared/QueueGoAccountDeletion.kt',
  'customer/build.gradle.kts',
  'merchant/build.gradle.kts',
  'rider/build.gradle.kts',
  'rider/src/main/AndroidManifest.xml',
  'rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt',
  'rider/src/main/java/com/queuego/rider/QueueGoApi.kt',
  'rider/src/main/java/com/queuego/rider/RiderHistoryCard.kt',
  'customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt',
  'customer/src/main/java/com/queuego/customer/CustomerApi.kt',
  'customer/src/main/java/com/queuego/customer/CustomerMarketApi.kt',
  'customer/src/main/java/com/queuego/customer/CustomerLaundryApi.kt',
  'customer/src/main/java/com/queuego/customer/CustomerExtras.kt',
  'customer/src/main/java/com/queuego/customer/CustomerSupportScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantApi.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantLaundryApi.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantSupportScreen.kt'
]) ok(fs.existsSync(path.join(root,p)),'missing '+p);

const customerGradle=read('customer/build.gradle.kts');
const merchantGradle=read('merchant/build.gradle.kts');
ok(customerGradle.includes('applicationId = "com.queuego.customer"'),'Customer package id');
ok(merchantGradle.includes('applicationId = "com.queuego.merchant"'),'Merchant package id');
ok(/targetSdk\s*=\s*36/.test(customerGradle)&&/targetSdk\s*=\s*36/.test(merchantGradle),'Customer/Merchant targetSdk 36');
const gradle=read('rider/build.gradle.kts');
ok(gradle.includes('applicationId = "com.queuego.rider"'),'Rider package id');
ok(/compileSdk\s*=\s*36/.test(gradle),'compileSdk 36');
ok(/targetSdk\s*=\s*36/.test(gradle),'targetSdk 36');

const all=[];
(function walk(dir){
  for(const name of fs.readdirSync(dir)){
    const p=path.join(dir,name),st=fs.statSync(p);
    if(st.isDirectory())walk(p); else if(/\.(kt|kts|xml)$/.test(name))all.push(fs.readFileSync(p,'utf8'));
  }
})(root);
const source=all.join('\n');

ok(!/android\.webkit\.WebView|<WebView\b|loadUrl\(/.test(source),'native apps must not use WebView UI');
ok(source.includes('expectedRole = "customer"')&&source.includes('expectedRole = "shop"'),'Customer/Merchant must validate real QueueGo roles');
const customerMain=read('customer/src/main/java/com/queuego/customer/MainActivity.kt');
const merchantMain=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
ok(customerMain.includes('QueueGoCustomerApp()')&&!customerMain.includes('QueueGoRoleNativeApp'),'Customer must launch full native app, not role shell');
ok(merchantMain.includes('QueueGoMerchantApp()')&&!merchantMain.includes('QueueGoRoleNativeApp'),'Merchant must launch full native app, not role shell');
ok(source.includes('queuego_place_cash_order'),'Customer native checkout must use production cash-order RPC');
ok(source.includes('market_public_catalog_v2')&&source.includes('queuego_place_market_order'),'Customer native Market must use production market RPCs');
ok(source.includes('queuego_add_market_order_shops'),'Customer native Market must preserve add-shop flow');
ok(source.includes('queuego_place_laundry_order_v2'),'Customer native Laundry must use production laundry order RPC');
ok(source.includes('notifications?select=id,title,message,type,reference_id,is_read,created_at'),'Customer native notifications must use production notifications table');
ok(source.includes('qg_support_tickets?select=id,category,details,status,order_id,admin_note,created_at')&&source.includes('qg_create_ticket'),'Customer native support must use production ticket RPC');
ok(source.includes('qg_customer_order_context')&&source.includes('CustomerTrackingRider'),'Customer native tracking must use production order context');
ok(source.includes('qg_customer_cancel_order'),'Customer native cancellation must use production RPC');
ok(source.includes('CustomerSearchScreen')&&source.includes('QgNavItem("search"'),'Customer native must preserve global search navigation');
ok(source.includes('qg_merchant_action_once'),'Merchant native order actions must stay idempotent');
ok(source.includes('get_my_shop_orders'),'Merchant native Orders must use production order source');
ok(source.includes('ToneGenerator')&&source.includes('STREAM_NOTIFICATION'),'Merchant native must alert on new orders');
ok(source.includes('shop_support_messages?select=id,sender_user_id,body,created_at'),'Merchant native must preserve Admin support messaging');
ok(source.includes('queuego_laundry_merchant_state')&&source.includes('queuego_laundry_shop_action_v2'),'Merchant native Laundry must use production laundry state/actions');
ok(source.includes('พร้อมส่ง · เหลือ'),'Merchant preparation countdown must remain on ready button');
ok(source.includes('SecureRoleSessionStore')&&source.includes('NativeAuthApi'),'Customer/Merchant must share native auth/session implementation');
ok(source.includes('functions/v1/account-delete')&&source.includes('DELETE_ACCOUNT'),'native apps must expose account deletion through production edge function');
ok((source.match(/QgAccountDeletionSection\(/g)||[]).length>=4,'Customer Merchant Rider must wire account deletion UI');
ok(source.includes('claim_active_session'),'reuse active-session claim');
ok(source.includes('check_active_session')&&source.includes('touch_active_session'),'reuse session guard');
ok(source.includes('get_rider_delivery_pool')&&source.includes('qg_get_my_rider_offer'),'reuse sequential server dispatch');
ok(source.includes('status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),'reuse active order states');
ok(source.includes('AndroidKeyStore')&&source.includes('AES/GCM/NoPadding'),'encrypted session storage');
ok(source.includes('qg_rider_mark_arrival'),'native Rider must reuse arrival RPC');
ok(source.includes('qg_pickup_with_photo')&&source.includes('qg_complete_with_photo'),'native Rider must reuse photo proof RPCs');
ok(source.includes('/storage/v1/object/qg-evidence/'),'native Rider must upload proof to existing evidence bucket');
ok(source.includes('qg_rider_decline_offer'),'native Rider must preserve sequential offer decline');
ok(source.includes('status=eq.completed&order=updated_at.desc'),'Rider native must expose completed history');
ok(source.includes('market_pickup_route_summary')&&source.includes('qg_market_pickup_with_photo'),'native Rider must preserve market multi-stop pickup flow');
ok(source.includes('market_claim')&&source.includes('qg_complete_market_with_photo'),'native Rider must claim and complete market groups through existing RPCs');
ok(source.includes('FileProvider')&&source.includes('TakePicture'),'native Rider must use Android camera flow');
ok(source.includes('SYSTEM_ALERT_WINDOW')&&source.includes('TYPE_APPLICATION_OVERLAY'),'native Rider must provide Android cross-app Q return overlay');
ok(source.includes('FOREGROUND_SERVICE_SPECIAL_USE')&&source.includes('RiderReturnService'),'navigation return overlay must run as explicit foreground special-use service');
ok(source.includes('ACTION_MANAGE_OVERLAY_PERMISSION'),'overlay permission must be explicitly user-controlled');
ok(source.includes('RiderReturnService.stop'),'overlay service must have cleanup path');
ok(!/cash-confirm|ยืนยันชำระเงินให้ร้าน|ยืนยันเก็บเงินจากลูกค้า/i.test(source),'native Rider must not reintroduce manual cash confirmation screens');
ok(!/service_role|sb_secret_/i.test(source),'no privileged Supabase secret');
console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Customer Merchant Rider native Android source'}));
