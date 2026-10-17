const assert=require('assert');
const fs=require('fs');

const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderRouteBundleUi.kt','utf8');
const map=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt','utf8');

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
assert(app.includes('snapshot = committedRead')&&
  app.includes('snapshot = localAfterSuccess')&&
  app.includes('job = mapJob')&&
  app.includes('"รับสินค้าแล้ว · นำทางต่อด้วยแผนที่ QueueGo"')&&
  !app.includes('openNavigation(context, nextStop)'),
  'Post-pickup navigation must retain the authoritative bundle snapshot in QueueGo without an automatic Google Maps handoff');
assert(api.includes('&id=in.(')&&api.includes('parseJob(row)'),
  'Bundle metadata must merge with real order rows so arrival/photo state remains authoritative');
assert(api.includes('bundle = bundle'),'Rider snapshot must carry route bundle state');

assert(app.includes('RiderRouteBundlePanel('),'Native Home must render route bundle summary/candidates');
assert(app.includes('api.claimRouteBundle(auth, candidate.orderId)'),'Native candidate action must claim through Production');
assert(ui.includes('"งานของคุณ')&&ui.includes('"มีงานพ่วงในเส้นทาง"')&&ui.includes('"เลือกเองได้ ไม่บังคับรับ"'),
  'Native stack summary and optional candidate actions must remain explicit');
assert(ui.includes('"รับร้าน')&&ui.includes('ส่งลูกค้าลำดับ')&&ui.includes('"รับงานพ่วง"'),
  'Native bundle UI must show pickup/drop order and explicit opt-in claim');
assert(ui.includes('offer.detourKm')&&ui.includes('offer.addedMinutes')&&ui.includes('offer.riderExtraFee')&&ui.includes('offer.customerSavings'),
  'Native bundle candidate must expose detour, delay, rider extra and customer savings');
assert(map.includes('bundleOrders: List<RiderBundleOrder>'),
  'QueueGo Rider navigation must accept the authoritative active bundle stops');
assert(map.includes('val rankedStops = RiderRouteBundleState(activeOrders = usable).routeStops()')&&
  map.includes('compareBy<RiderBundleOrder> { it.bundleSequence }')&&
  map.includes('compareBy<RiderBundleOrder> { it.dropRank }')&&
  map.includes('sequence = offset + index + 1'),
  'QueueGo navigation must preserve authoritative bundle stop ranks, with pickup-first legacy fallback ordering');
assert(map.includes('kind = if (stop.pickup) NavigationStopKind.PICKUP else NavigationStopKind.DROPOFF')&&
  map.includes('completed = if (stop.pickup) order.job.isDelivering'),
  'Bundle pickup stops must complete once the Rider has advanced into delivery');
assert(map.includes('id = "pickup:" + order.job.id')&&
  map.includes('id = "drop:" + order.job.id')&&
  map.includes('completed = order.job.status.lowercase() == "completed"'),
  'QueueGo bundle stop plan must rebuild from authoritative order progress for pickup and drop stops');
assert(map.includes('sequence = 1')&&map.includes('kind = NavigationStopKind.PICKUP')&&
  map.includes('sequence = 2')&&map.includes('kind = NavigationStopKind.DROPOFF')&&
  map.includes('completed = job.isDelivering'),
  'Base QueueGo Rider navigation must keep shop as point 1, customer as point 2, and retire pickup after delivery begins');
assert(app.includes('bundleOrders = snapshot?.bundle?.activeOrders.orEmpty()'),
  'Home must pass all active bundle stops to QueueGo navigation');

console.log('Native Rider Production route bundle parity contracts: PASS');
