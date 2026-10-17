const assert=require('assert');
const fs=require('fs');

const manifest=fs.readFileSync('native-android/rider/src/main/AndroidManifest.xml','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const service=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderReturnService.kt','utf8');
const main=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/MainActivity.kt','utf8');

assert(manifest.includes('<package android:name="com.google.android.apps.maps" />'),
  'Rider must declare Google Maps package visibility');
assert(app.includes('setPackage(GOOGLE_MAPS_PACKAGE)')&&app.includes('"google.navigation:q="'),
  'Rider navigation must open the installed Google Maps app explicitly');
assert(!app.includes('https://www.google.com/maps/dir/'),
  'Rider must not fall back to browser-based Google Maps');
assert(!app.includes('"ต้องติดตั้ง Google Maps ก่อนเปิดรับงาน"')&&
  !app.includes('enabled && !Settings.canDrawOverlays(context)'),
  'Rider must be able to go online and work entirely inside QueueGo without Google Maps or overlay permission');
assert(app.includes('openGoogleMapsInstallPage(context)')&&app.includes('Settings.canDrawOverlays(context)'),
  'Optional Google Maps action must still handle missing app/return-control setup when the Rider chooses it');
assert(app.includes('RiderReturnService.start(context)'),
  'Opening Google Maps must start the QueueGo return overlay service');
assert(!app.includes('.putBoolean(navigationKey, true)')&&
  app.includes('if (!opened) {\n        RiderReturnService.stop(context)'),
  'Optional Google Maps handoff must never mutate Rider workflow state; a failed handoff only stops the return service');
assert(app.includes('pickupCommittedStatus == "picked_up"')&&
  app.includes('api.startDelivery(auth, job.copy(status = "picked_up"))'),
  'Legacy picked_up pickup results must advance to delivery before customer Maps handoff');
assert(app.includes('completedMode == "pickup" && pickupCommittedStatus == "in_progress"')&&
  app.includes('"รับสินค้าแล้ว · นำทางต่อด้วยแผนที่ QueueGo"')&&
  !app.includes('openNavigation(context, nextStop)'),
  'Post-pickup transition must stay in QueueGo; Google Maps opens only from an explicit Rider action');
assert((app.match(/riderPickupReadCanReplace\("in_progress"/g)||[]).length>=2 &&
  (app.match(/persistCommittedSnapshot\(committedSnapshot\)/g)||[]).length>=2 &&
  app.includes('pushStore.saveHomeSnapshot(auth.user.id, value, durable = true)') &&
  app.includes('kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)'),
  'Manual and Market start-delivery must commit in_progress locally and reject stale picked_up reads before optional external navigation');
assert(!app.includes('RiderNavigationController()')&&!app.includes('RiderNavigationSpeaker(context)')&&!app.includes('if (navigationState.active)'),
  'Rider Home must keep the retired legacy navigation engine removed while using the current QueueGo navigation map module');
assert(app.includes('Text("นำทางด้วย Google Maps"'),
  'Normal active-job card must show a distinct Google Maps button');
const acceptFlow=app.slice(app.indexOf('fun acceptTargetedOffer('),app.indexOf('// Navigation and visible UI renew'));
assert(!acceptFlow.includes('openNavigation(context'),
  'Accepting a targeted offer must never force-open Google Maps');
assert(!fs.existsSync('native-android/rider/src/main/java/com/queuego/rider/RiderNavigation.kt'),
  'Retired Rider in-app navigation engine source must stay removed');
assert(service.includes('TYPE_APPLICATION_OVERLAY')&&service.includes('R.drawable.qg_rider_brand_q')&&service.includes('MainActivity::class.java'),
  'Rider must expose a draggable Q overlay that returns to QueueGo');
assert(service.includes('snapToNearestEdge')&&service.includes('PREF_Y_RATIO')&&service.includes('putFloat(PREF_Y_RATIO'),
  'Rider return overlay must snap to an edge and persist its vertical position');
assert(service.includes('"กลับ QueueGo"')&&service.includes('PREF_HINT_SHOWN')&&service.includes('2400L'),
  'Rider return overlay must teach the return action once, then collapse to the Q');
assert(service.includes('premiumOverlayBackground')&&service.includes('Color.rgb(230, 0, 45)')&&service.includes('setStroke'),
  'Rider return overlay must keep the premium QueueGo red-Q treatment');
assert(service.includes('setSmallIcon(R.drawable.qg_notification)'),
  'Rider return foreground service must use the QueueGo notification asset, not an Android debug icon');
assert(main.includes('override fun onResume()')&&main.includes('RiderReturnService.stop(this)'),
  'Returning to QueueGo must remove the floating overlay');
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

console.log('Native Rider optional Google Maps return overlay contracts: PASS');
