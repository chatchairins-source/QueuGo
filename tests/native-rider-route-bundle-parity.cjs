const assert=require('assert');
const fs=require('fs');

const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderRouteBundleUi.kt','utf8');

assert(models.includes('data class RiderRouteBundleState('),'Native Rider must carry Production route bundle state');
assert(models.includes('val activeOrders: List<RiderBundleOrder>')&&models.includes('val candidates: List<RiderBundleCandidate>'),
  'Route bundle state must keep active orders and optional candidates');
assert(models.includes('live.firstOrNull { it.job.status == "in_progress" }'),
  'Bundle current order must prioritize an in-progress delivery like Production Web');
assert(models.includes('minByOrNull { it.bundleSequence }')&&models.includes('minByOrNull { it.dropRank }'),
  'Bundle routing must preserve pickup sequence then customer drop rank');

for(const rpc of ['queuego_route_bundle_active_orders','queuego_route_bundle_candidates'])
  assert(api.includes('"'+rpc+'"'),'Native Rider must read Production '+rpc);
assert(api.includes('actionOnce(')&&api.includes('"bundle_claim"')&&api.includes('suspend fun claimRouteBundle('),
  'Bundle claim must use the existing idempotent Rider action receipt path');
assert(api.includes('bundle.currentOrder()?.let { active = it.job }'),
  'Rider snapshot must replace arbitrary latest-order selection with the authoritative bundle next job');
assert(api.includes('&id=in.(')&&api.includes('parseJob(row)'),
  'Bundle metadata must merge with real order rows so arrival/photo state remains authoritative');
assert(api.includes('bundle = bundle'),'Rider snapshot must carry route bundle state');

assert(app.includes('RiderRouteBundlePanel('),'Native Home must render route bundle summary/candidates');
assert(app.includes('api.claimRouteBundle(auth, candidate.orderId)'),'Native candidate action must claim through Production');
assert(ui.includes('"งานพ่วง')&&ui.includes('"มีงานพ่วงในเส้นทาง"')&&ui.includes('"เลือกเองได้ ไม่บังคับรับ"'),
  'Native bundle copy must preserve Production Home semantics');
assert(ui.includes('"รับร้าน')&&ui.includes('"ส่งลูกค้าลำดับ')&&ui.includes('"รับงานพ่วง"'),
  'Native bundle UI must show pickup/drop order and explicit opt-in claim');
assert(ui.includes('offer.detourKm')&&ui.includes('offer.addedMinutes')&&ui.includes('offer.riderExtraFee')&&ui.includes('offer.customerSavings'),
  'Native bundle candidate must expose detour, delay, rider extra and customer savings');

console.log('Native Rider Production route bundle parity contracts: PASS');
