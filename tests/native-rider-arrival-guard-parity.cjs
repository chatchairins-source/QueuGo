const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderArrivalGuard.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');

assert(app.includes('var arrivalGuard by remember { mutableStateOf<RiderArrivalGuardState?>(null) }'),
  'Rider Home must retain arrival warning state');
assert(app.includes('if (km != null && km <= 0.2)'),
  'Arrival within 200 meters must proceed immediately like Production Web');
assert(app.includes('arrivalGuard = RiderArrivalGuardState(job, target, meters)'),
  'Arrival beyond the guard radius or without a measurable GPS distance must warn instead of silently committing');
assert(app.includes('requestArrival(current.activeJob, "shop")')&&
       app.includes('requestArrival(current.activeJob, "customer")'),
  'Both shop and customer arrival actions must use the same guard');
assert(app.includes('api.markArrival(')&&api.includes('"qg_rider_mark_arrival"'),
  'Arrival confirmation must keep the authoritative Production RPC');
assert(app.includes('if (target == "customer")')&&
       app.includes('openVerification("delivery", updated?.activeJob ?: job)'),
  'Customer arrival must continue directly into delivery verification after the RPC commits');
assert(app.includes('RiderArrivalGuard(')&&app.includes('onProceed = { commitArrival(guard.job, guard.target) }'),
  'Warning sheet confirmation must reuse the same arrival commit path');

assert(ui.includes('ถึง${place}จริงหรือเปล่า?'),'Arrival sheet must keep Production warning copy');
assert(ui.includes('เกิน 200 เมตร'),'Arrival sheet must explain the 200 meter threshold');
assert(ui.includes('คำเตือนนี้ไม่ล็อกงาน คุณยังยืนยันต่อได้'),
  'Arrival guard must warn without locking the Rider job');
assert(ui.includes('"ยืนยันว่าถึงแล้ว"')&&ui.includes('"ย้อนกลับ"'),
  'Arrival sheet must expose proceed and cancel actions');
assert(ui.includes('state.meters != null')&&ui.includes('"GPS ยังไม่พร้อม"'),
  'Arrival sheet must distinguish measured distance from unavailable GPS');

console.log('Native Rider arrival guard parity contracts: PASS');
