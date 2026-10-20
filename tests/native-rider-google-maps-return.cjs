const assert=require('assert');
const fs=require('fs');

const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');

assert(!app.includes('Text("นำทางด้วย Google Maps"'),
  'Rider active-job UI must not expose a Google Maps navigation button');
assert(!app.includes('GOOGLE_MAPS_PACKAGE')&&!app.includes('"google.navigation:q="')&&!app.includes('openGoogleMapsInstallPage(context)'),
  'Rider workflow must not contain a Google Maps launcher path');
assert(!app.includes('RiderReturnService.start(context)'),
  'QueueGo-only navigation must not start the external-map return overlay');
assert(app.includes('Text("นำทางด้วย QueueGo"')&&
  app.includes('fun startQueueGoNavigation')&&
  app.includes('onQueueGoNavigate = {')&&
  app.includes('startQueueGoNavigation()'),
  'Normal active-job card must keep QueueGo navigation as the only navigation action');
assert(app.includes('pickupCommittedStatus == "picked_up"')&&
  app.includes('api.startDelivery(auth, job.copy(status = "picked_up"))'),
  'Legacy picked_up pickup results must still advance to delivery before QueueGo navigation continues');
assert(app.includes('completedMode == "pickup" && pickupCommittedStatus == "in_progress"')&&
  app.includes('"รับสินค้าแล้ว · นำทางต่อด้วยแผนที่ QueueGo"')&&
  !app.includes('openNavigation(context, nextStop)'),
  'Post-pickup transition must stay inside QueueGo');
assert((app.match(/riderPickupReadCanReplace\("in_progress"/g)||[]).length>=2 &&
  (app.match(/persistCommittedSnapshot\(committedSnapshot\)/g)||[]).length>=2 &&
  app.includes('pushStore.saveHomeSnapshot(auth.user.id, value, durable = true)') &&
  app.includes('kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)'),
  'Manual and Market start-delivery must commit in_progress locally and reject stale picked_up reads');
assert(!app.includes('RiderNavigationController()')&&!app.includes('RiderNavigationSpeaker(context)')&&!app.includes('if (navigationState.active)'),
  'Retired legacy navigation engine must stay removed while QueueGo navigation map remains authoritative');
const returnFlow=app.slice(
  app.indexOf('current.deliveryReturn != null -> {'),
  app.indexOf('current.activeJob == null && current.laundry.activeJob != null')
);
assert(returnFlow.includes('startQueueGoNavigation(')&&
  returnFlow.includes('นำทางกลับร้านด้วยแผนที่ QueueGo')&&
  returnFlow.includes('นำทางไปจุดคืนถัดไปด้วยแผนที่ QueueGo')&&
  !returnFlow.includes('openNavigation(context')&&
  !returnFlow.includes('google.navigation'),
  'Return-to-shop and next-return-stop navigation must stay inside QueueGo');
const acceptFlow=app.slice(app.indexOf('fun acceptTargetedOffer('),app.indexOf('// Navigation and visible UI renew'));
assert(!acceptFlow.includes('openNavigation(context'),
  'Accepting a targeted offer must remain inside QueueGo');
assert(app.includes('riderMergeLiveSnapshot(current, resolvedSnapshot)')&&
  app.includes('previousPickups[pickup.pickupId]?.takeIf { it.done && !pickup.done }'),
  'Rider refresh must reject same-job status regression and preserve completed Market pickup points');
assert(app.includes('riderResolveMissingActiveSnapshot(')&&
  app.includes('lookup?.getOrNull()')&&app.includes('lookup?.isSuccess == true')&&
  app.includes('api.notificationJob(auth, currentSnapshot.activeJob.id, id)'),
  'A stale empty refresh after claim must distinguish lookup failure from authoritative order absence before clearing the local active job');
assert(app.includes('takeIf { it.activeJob?.id == acceptedFallback.id }')&&
  app.includes('riderMergeLiveSnapshot(acceptedSnapshot, it)'),
  'Accept-offer follow-up reads must never replace the authoritative local claim with an empty or different active job');
assert(app.includes('val committedMarker = "local:" + System.currentTimeMillis()')&&
  app.includes('riderArrivalReadCanReplace(target, job.id, it)')&&
  app.includes('persistCommittedSnapshot(committedSnapshot)'),
  'Rider arrival must commit locally before accepting a server read so return/resume cannot lose arrived state');

console.log('Native Rider QueueGo-only navigation contracts: PASS');
