const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderDeliverySuccess.kt','utf8');

assert(app.includes('var deliverySuccessJob by remember { mutableStateOf<RiderJob?>(null) }'),
  'Rider Home must retain the just-completed delivery for success feedback');
assert(
  app.includes('deliverySuccessJob = job') &&
  app.includes('RiderDeliverySuccess(') &&
  app.includes('job = completed') &&
  app.includes('recommendation = afterWorkRecommendation'),
  'Successful delivery must surface the Production-style success card with optional real after-work recommendation'
);
assert(app.includes('if (completedMode == "delivery") {')&&app.includes('closeVerification()'),
  'Success feedback must happen after the existing delivery completion path');

assert(ui.includes('"จัดส่งสำเร็จ"'),'Success card must preserve Production completion copy');
assert(ui.includes('job.numberLabel'),'Success card must show the same order number');
assert(ui.includes('"รายได้ค่าส่ง ฿"')&&ui.includes('job.deliveryFee ?: 0.0'),
  'Success card must show the authoritative Native Rider delivery earning');
assert(ui.includes('"รับทราบ"')&&ui.includes('if (recommendation == null)')&&ui.includes('delay(4_500)'),
  'Success feedback must keep acknowledgement and auto-dismiss only when no real recommendation exists');
assert(
  ui.includes('onRecommendation: (RiderAfterWorkRecommendation) -> Unit') &&
  ui.includes('onRecommendation(item)'),
  'Success card recommendation CTA must pass the exact real recommendation selected by Rider Home'
);

console.log('Native Rider delivery success parity contracts: PASS');
