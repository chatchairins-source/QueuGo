const fs=require('fs');
const assert=require('assert');

function read(path){return fs.readFileSync(path,'utf8')}
function has(text,re,msg){assert(re.test(text),msg)}

for(const app of ['customer','merchant']){
  const gradle=read('native-android/'+app+'/build.gradle.kts');
  has(gradle,/if \(file\("google-services\.json"\)\.exists\(\)\)/,'conditional google-services missing for '+app);
  has(gradle,/firebase-bom:35\.0\.0/,'Firebase BOM missing for '+app);
  has(gradle,/firebase-messaging/,'Firebase Messaging missing for '+app);

  const manifest=read('native-android/'+app+'/src/main/AndroidManifest.xml');
  has(manifest,/android\.permission\.POST_NOTIFICATIONS/,'notification permission missing for '+app);
  has(manifest,/android\.permission\.RECORD_AUDIO/,'microphone permission missing for '+app);
  has(manifest,/queuego_orders/,'QueueGo notification channel metadata missing for '+app);
  assert(!/USE_FULL_SCREEN_INTENT|fullScreenIntent/i.test(manifest),'full-screen call intent forbidden for '+app);
}
const customerPush=read('native-android/customer/src/main/java/com/queuego/customer/CustomerPush.kt');
const merchantPush=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPush.kt');
for(const [name,text] of [['customer',customerPush],['merchant',merchantPush]]){
  has(text,/FirebaseApp\.getApps/,'Firebase configuration guard missing for '+name);
  has(text,/NativePushApi\(\)\.subscribe|push\.subscribe/,'native push subscription missing for '+name);
  has(text,/NotificationCompat\.CATEGORY_CALL/,'voice-call notification category missing for '+name);
  assert(!/setFullScreenIntent|fullScreenIntent/i.test(text),'full-screen intent forbidden for '+name);
}
const customerApp=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const merchantApp=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
has(customerApp,/syncCustomerNativePush\(context, auth\)/,'Customer push registration not wired');
has(customerApp,/disableCustomerNativePush\(context, auth\)/,'Customer push logout cleanup missing');
has(merchantApp,/syncMerchantNativePush\(context, auth\)/,'Merchant push registration not wired');
has(merchantApp,/disableMerchantNativePush\(context, auth\)/,'Merchant push logout cleanup missing');

const worker=read('supabase/functions/queuego-push/index.ts');
has(worker,/n\.type==='voice_call'[^?]*\?'high':'normal'/,'voice calls must use high FCM priority');
has(worker,/urgency:\(user\.role==='rider'\|\|n\.type==='voice_call'\)\?'high':'normal'/,'voice calls must use high Web Push urgency');

const shared=read('native-android/shared/src/main/java/com/queuego/shared/NativePushRegistration.kt');
has(shared,/UUID\.randomUUID\(\)/,'durable UUID push device id missing');
has(shared,/subscribe-native/,'shared native subscribe action missing');
has(shared,/unsubscribe-native/,'shared native unsubscribe action missing');

console.log('Native Customer/Merchant push contract: PASS');
