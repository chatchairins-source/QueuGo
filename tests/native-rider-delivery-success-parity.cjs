const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderDeliverySuccess.kt','utf8');

assert(app.includes('var deliverySuccessJob by remember { mutableStateOf<RiderJob?>(null) }'),
  'Rider Home must retain the just-completed delivery for success feedback');
assert(app.includes('deliverySuccessJob = job')&&app.includes('RiderDeliverySuccess(completed)'),
  'Successful delivery must surface the Production-style success card');
assert(app.includes('if (completedMode == "delivery") {')&&app.includes('closeVerification()'),
  'Success feedback must happen after the existing delivery completion path');

assert(ui.includes('"จัดส่งสำเร็จ"'),'Success card must preserve Production completion copy');
assert(ui.includes('job.numberLabel'),'Success card must show the same order number');
assert(ui.includes('"รายได้ค่าส่ง ฿"')&&ui.includes('job.deliveryFee ?: 0.0'),
  'Success card must show the authoritative Native Rider delivery earning');
assert(ui.includes('"รับทราบ"')&&ui.includes('delay(4_500)'),
  'Success feedback must support acknowledgement and Production-style auto dismissal');

console.log('Native Rider delivery success parity contracts: PASS');
