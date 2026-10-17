const assert=require('assert');
const fs=require('fs');
const read=p=>fs.readFileSync(p,'utf8');

const map=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
const runtime=read('native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/NavigationMapRuntimeTest.kt');
const host=read('native-android/rider-navigation-map/src/debug/java/com/queuego/ridernavigationmap/NavigationMapRuntimeActivity.kt');
const shared=read('native-android/shared/src/main/java/com/queuego/shared/QueueGoMap.kt');

for(const source of [map,runtime,host,shared]){
  assert(!/com\.longdo|LongdoMapAndroidSDK|longdo\.map|api\.longdo|ms\.longdo/i.test(source),
    'Native map runtime must never depend on legacy Longdo');
}

assert(map.includes('RiderNavigationMapRegistry'));
assert(map.includes('retainAcrossVisibilityLoss()'));
assert(map.includes('viewCreatedCount')&&map.includes('mapCreatedCount'));
assert(map.includes('ValueAnimator.ofFloat(0f, 1f)'));
assert(map.includes('RIDER_MARKER_MAX_ANIMATION_MS = 1_200L'));
assert(map.includes('navigationCameraDecision('));
assert(map.includes('CameraUpdateFactory.newCameraPosition'));

assert(runtime.includes('gpsRefreshTabReturnAndOverlayUpdatesReuseSameMapView'));
assert(runtime.includes('assertEquals(1, it.latestDiagnostics.viewCreatedCount)'));
assert(runtime.includes('assertEquals(1, it.latestDiagnostics.mapCreatedCount)'));
assert(runtime.includes('manualDragReportsGestureButTapDoesNot'));
assert(runtime.includes('activityRecreationCreatesFreshMapViewForFreshActivityContext'));
assert(host.includes('mapSourceUri = "asset://queuego-nav-runtime-tilejson.json"'));
assert(fs.existsSync('native-android/rider-navigation-map/src/debug/assets/queuego-nav-runtime-tilejson.json'));
assert(fs.existsSync('native-android/rider-navigation-map/src/debug/assets/queuego-nav-runtime.mvt'));

assert(shared.includes('queueGoMapServiceBaseUrl'));
assert(shared.includes('startsWith("https://")'));
assert(shared.includes('pmtiles://'));
console.log('Native Rider QueueGo MapLibre runtime continuity contracts: PASS');
