const fs=require('fs');
const assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');

const customerTracking=read('native-android/customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
const customerApp=read('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const merchantApp=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
const riderApp=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
const controller=read('native-android/shared/src/main/java/com/queuego/shared/NativeVoiceCallController.kt');
const overlay=read('native-android/shared/src/main/java/com/queuego/shared/QueueGoVoiceCallOverlay.kt');

for(const app of ['customer','merchant','rider']){
  const manifest=read(\`native-android/\${app}/src/main/AndroidManifest.xml\`);
  assert(/android\.permission\.RECORD_AUDIO/.test(manifest),\`\${app} RECORD_AUDIO missing\`);
}

assert(/onCallShop/.test(customerTracking),'Customer shop call callback missing');
assert(/onCallRider/.test(customerTracking),'Customer Rider call callback missing');
assert(!/ACTION_DIAL|Uri\.fromParts\("tel"/.test(customerTracking),'Customer tracking must not expose phone dialer');

assert(/startOutgoing\(auth, orderId, target\)/.test(customerApp),'Customer in-app voice start missing');
assert(/refreshIncoming\(auth\)/.test(customerApp),'Customer incoming call discovery missing');
assert(/QueueGoVoiceCallOverlay/.test(customerApp),'Customer voice overlay missing');

assert(/refreshIncoming\(auth\)/.test(merchantApp),'Merchant incoming call discovery missing');
assert(/QueueGoVoiceCallOverlay/.test(merchantApp),'Merchant voice overlay missing');

assert(/startOutgoing\(nativeVoiceAuth, orderId, "customer"\)/.test(riderApp),'Rider customer in-app call missing');
assert(/refreshIncoming\(nativeVoiceAuth\)/.test(riderApp),'Rider incoming call discovery missing');
assert(/QueueGoVoiceCallOverlay/.test(riderApp),'Rider voice overlay missing');

assert(/fun dismissTerminal/.test(controller),'voice terminal dismissal missing');
assert(/NativeVoicePhase\.INCOMING/.test(overlay),'incoming UI state missing');
assert(/NativeVoicePhase\.CONNECTED/.test(overlay),'connected UI state missing');
assert(/onToggleMute/.test(overlay)&&/onToggleSpeaker/.test(overlay),'mute/speaker controls missing');
assert(/รับสาย/.test(overlay)&&/ปฏิเสธ/.test(overlay)&&/วางสาย/.test(overlay),'Thai call controls missing');

console.log('Native voice UI contract: PASS');
