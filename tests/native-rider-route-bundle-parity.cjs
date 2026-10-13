const assert=require('assert');
const fs=require('fs');

const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderRouteBundleUi.kt','utf8');
const map=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/LongdoRiderMap.kt','utf8');

assert(models.includes('data class RiderRouteBundleState('),'Native Rider must carry Production route bundle state');
assert(models.includes('val activeOrders: List<RiderBundleOrder>')&&models.includes('val candidates: List<RiderBundleCandidate>'),
  'Route bundle state must keep active orders and optional candidates');
assert(models.includes('it.job.status in setOf("rider_assigned", "preparing", "ready", "assigned")')&&
  models.indexOf('minByOrNull { it.bundleSequence }') < models.indexOf('it.job.status in setOf("picked_up", "in_progress")'),
  'Bundle current order must finish pending pickup stops before any customer delivery');
assert(models.includes('it.job.status in setOf("picked_up", "in_progress")')&&
  models.includes('minByOrNull { it.dropRank }'),
  'After all bundle pickups, Rider must choose customer delivery by authoritative drop rank');

for(const rpc of ['queuego_route_bundle_active_orders','queuego_route_bundle_candidates'])
  assert(api.includes('"'+rpc+'"'),'Native Rider must read Production '+rpc);
assert(api.includes('actionOnce(')&&api.includes('"bundle_claim"')&&api.includes('suspend fun claimRouteBundle('),
  'Bundle claim must use the existing idempotent Rider action receipt path');
assert(api.includes('bundle.currentOrder()?.let { active = it.job }'),
  'Rider snapshot must replace arbitrary latest-order selection with the authoritative bundle next job');
assert(app.includes('riderSnapshotJobForCommit(updated, jobId)')&&
  app.includes('riderApplyPickupCommit('),
  'Pickup commit recovery must remain bundle-aware when the authoritative active job advances to the next stop');
assert(app.includes('val nextStop = committedRead?.activeJob')&&
  app.includes('?: localAfterSuccess?.activeJob')&&
  !app.includes('val delivering = committedRead?.activeJob'),
  'Post-pickup Google Maps handoff must use the authoritative next bundle stop instead of forcing the just-picked customer');
assert(api.includes('&id=in.(')&&api.includes('parseJob(row)'),
  'Bundle metadata must merge with real order rows so arrival/photo state remains authoritative');
assert(api.includes('bundle = bundle'),'Rider snapshot must carry route bundle state');

assert(app.includes('RiderRouteBundlePanel('),'Native Home must render route bundle summary/candidates');
assert(app.includes('api.claimRouteBundle(auth, candidate.orderId)'),'Native candidate action must claim through Production');
assert(ui.includes('"งานพ่วง')&&ui.includes('"มีงานพ่วงในเส้นทาง"')&&ui.includes('"เลือกเองได้ ไม่บังคับรับ"'),
  'Native bundle copy must preserve Production Home semantics');
assert(ui.includes('"รับร้าน')&&ui.includes('ส่งลูกค้าลำดับ')&&ui.includes('"รับงานพ่วง"'),
  'Native bundle UI must show pickup/drop order and explicit opt-in claim');
assert(ui.includes('offer.detourKm')&&ui.includes('offer.addedMinutes')&&ui.includes('offer.riderExtraFee')&&ui.includes('offer.customerSavings'),
  'Native bundle candidate must expose detour, delay, rider extra and customer savings');
assert(map.includes('bundleOrders: List<RiderBundleOrder> = emptyList()'),
  'Longdo map must accept the authoritative active bundle stops');
assert(map.includes('val pickupOrder = numberedOrders.sortedWith')&&
  map.includes('val dropOrder = numberedOrders.sortedWith')&&
  map.includes('pickupOrder.size + index + 1'),
  'Longdo map must number bundled pickup stops first and customer drops after them');
assert(map.includes('!bundled.job.shopArrived && !bundled.job.isDelivering'),
  'Bundle pickup marker must disappear once that Rider reaches or passes the shop stop');
assert(map.includes('append(job?.shopArrived ?: false)')&&
  map.includes('.append(it.job.shopArrived).append(":")')&&
  map.includes('job?.arrivedShopAt'),
  'Rider stop cache must invalidate immediately when shop arrival changes for base and bundled jobs');
assert(map.includes('addStopPin(job.deliveryLat, job.deliveryLng, 2, false)')&&
  map.includes('!job.shopArrived && !job.isDelivering'),
  'Base Rider map must keep shop as point 1, customer as point 2, and hide point 1 after shop arrival');
assert(app.includes('bundleOrders = snapshot?.bundle?.activeOrders.orEmpty()'),
  'Home must pass all active bundle stops to Longdo');

console.log('Native Rider Production route bundle parity contracts: PASS');
