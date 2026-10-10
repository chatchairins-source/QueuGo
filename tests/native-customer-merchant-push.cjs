const fs=require('fs');
const assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const customer=read('native-android/customer/src/main/java/com/queuego/customer/CustomerPush.kt');
const merchant=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPush.kt');
const shared=read('native-android/shared/src/main/java/com/queuego/shared/NativePush.kt');
const customerApp=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const merchantApp=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
const customerManifest=read('native-android/customer/src/main/AndroidManifest.xml');
const merchantManifest=read('native-android/merchant/src/main/AndroidManifest.xml');
const pushEdge=read('supabase/functions/queuego-push/index.ts');
const workflow=read('.github/workflows/build-native-rider-pilot.yml');

function has(text,re,msg){assert(re.test(text),msg)}

has(shared,/UUID\.randomUUID\(\)\.toString\(\)/,'push device id must be UUID');
has(shared,/action", "subscribe-native"/,'shared subscribe-native action missing');
has(shared,/action", "unsubscribe-native"/,'shared unsubscribe-native action missing');
has(shared,/platform", "android"/,'native push platform must be android');

for(const [role,src,app,manifest,service] of [
  ['customer',customer,customerApp,customerManifest,'QueueGoCustomerMessagingService'],
  ['merchant',merchant,merchantApp,merchantManifest,'QueueGoMerchantMessagingService'],
]){
  has(src,/FirebaseMessagingService/,\`\${role} FCM service missing\`);
  has(src,/onNewToken/,\`\${role} token rotation handling missing\`);
  has(src,/onMessageReceived/,\`\${role} foreground push handling missing\`);
  has(src,/NativePushApi\(\)\.subscribe/,\`\${role} backend token registration missing\`);
  has(src,/NativePushApi\(\)\.unsubscribe|api\.unsubscribe/,\`\${role} backend token cleanup missing\`);
  has(src,/CATEGORY_CALL/,\`\${role} voice call notification category missing\`);
  has(app,/POST_NOTIFICATIONS/,\`\${role} notification runtime permission missing\`);
  has(app,/nativeLogoutScope/,\`\${role} lifecycle-safe logout cleanup missing\`);
  has(app,/unsubscribe\(auth, pushStore\.deviceId\(\)\)/,\`\${role} logout must unsubscribe device\`);
  has(manifest,/android\.permission\.POST_NOTIFICATIONS/,\`\${role} manifest notification permission missing\`);
  has(manifest,new RegExp(service),\`\${role} messaging service not registered\`);
  has(manifest,/queuego_orders/,\`\${role} notification channel metadata missing\`);
}
has(customer,/auth\.user\.role != "customer"/,'Customer role guard missing');
has(merchant,/auth\.user\.role != "shop"/,'Merchant role guard missing');
has(customer,/auth\.user\.status != "active"/,'Customer active-status guard missing');
has(merchant,/auth\.user\.status != "active"/,'Merchant active-status guard missing');
has(pushEdge,/user\.role==='rider'\|\|n\.type==='voice_call'/,'voice_call push must be high priority for all roles');
has(workflow,/com\.queuego\.customer/,'CI must discover Customer Firebase client');
has(workflow,/com\.queuego\.merchant/,'CI must discover Merchant Firebase client');
has(workflow,/com\.queuego\.rider/,'CI must retain Rider Firebase client gate');

console.log('Native Customer/Merchant push contract: PASS');
