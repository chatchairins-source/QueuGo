const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const chat=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderChat.kt','utf8');

assert(chat.includes('onCallCustomer: () -> Unit'),'Rider chat must accept the existing QueueGo voice action');
assert(chat.includes('contentDescription = "โทรหาลูกค้าผ่าน QueueGo"'),'Rider chat header must expose the QueueGo call control');
assert(chat.includes('R.drawable.qg_rider_flow_phone'),'Rider chat must use the existing professional phone icon');
assert(chat.includes('job.status.lowercase() !in setOf("completed", "cancelled", "no_rider_available")'),
  'Rider chat must hide calling after terminal order states');
assert(app.includes('onCallCustomer = { chatJob?.id?.let(::startCustomerVoice) }'),
  'Rider chat call action must reuse the existing native voice controller and microphone permission flow');
assert(!chat.includes('Intent.ACTION_DIAL')&&!chat.includes('Intent.ACTION_CALL'),
  'Rider chat call must not fall back to carrier phone calling');

console.log('Native Rider chat QueueGo voice parity contracts: PASS');
