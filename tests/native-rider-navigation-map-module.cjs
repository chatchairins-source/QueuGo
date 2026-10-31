const assert=require('assert');
const fs=require('fs');
const read=p=>fs.readFileSync(p,'utf8');

const settings=read('native-android/settings.gradle.kts');
const riderBuild=read('native-android/rider/build.gradle.kts');
const moduleBuild=read('native-android/rider-navigation-map/build.gradle.kts');
const map=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
const camera=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationCameraPolicy.kt');
const cameraTest=read('native-android/rider-navigation-map/src/test/java/com/queuego/ridernavigationmap/NavigationCameraPolicyTest.kt');
const mapMotion=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
const mapMotionTest=read('native-android/rider-navigation-map/src/test/java/com/queuego/ridernavigationmap/NavigationMotionPolicyTest.kt');
const route=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/QueueGoNavigationRouteClient.kt');
const routeContract=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationRouteProvider.kt');
const integration=read('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
const locationService=read('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt');
const manifest=read('native-android/rider/src/main/AndroidManifest.xml');
const runtimeTest=read('native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/NavigationMapRuntimeTest.kt');
const runtimeHost=read('native-android/rider-navigation-map/src/debug/java/com/queuego/ridernavigationmap/NavigationMapRuntimeActivity.kt');
const installedCountryTest=read('native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/InstalledThailandMapTest.kt');
const workflow=read('.github/workflows/build-native-rider-pilot.yml');
const isolatedRuntime=read('native-android/qa/run-rider-navigation-map-runtime.py');
assert(isolatedRuntime.includes('stage_verified_map_for_isolated_debug_runtime()'));
assert(isolatedRuntime.includes('src/debug/assets/maps'));
assert(isolatedRuntime.includes('hashlib.file_digest(stream, "sha256")'));
assert(isolatedRuntime.includes('shutil.rmtree(staged, ignore_errors=True)'));
assert(!isolatedRuntime.includes('src/androidTest/assets/maps'));

const webMap=read('queuego-map-web.js');
const webRider=read('rider/index.html');
const guidance=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationGuidance.kt');
const coordinator=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationRouteCoordinator.kt');
const viewMode=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationViewMode.kt');
const riderIntegration=read('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
const overviewPolicy=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationOverviewPolicy.kt');

assert(settings.includes('":rider-navigation-map"'));
assert(moduleBuild.includes('id("com.android.library")'));
assert(moduleBuild.includes('implementation(project(":shared"))'));
assert(moduleBuild.includes('org.maplibre.gl:android-sdk-opengl:13.7.1'));
assert(!/LongdoMapAndroidSDK|com\.longdo|longdo\.map/i.test(moduleBuild));

assert(riderBuild.includes('implementation(project(":rider-navigation-map"))'));
assert(riderBuild.includes('QG_MAP_SERVICE_BASE_URL'));
assert(integration.includes('QueueGoNavigationRouteClient'));
assert(integration.includes('queuego.map.service.base_url'));
assert(integration.includes('NavigationRouteCoordinator'));
assert(integration.includes('Channel.CONFLATED'));
assert(!/LongdoNavigationRouteClient|riderLongdoNavigationKey/i.test(integration));

assert(map.includes('org.maplibre.android.maps.MapView'));
assert(map.includes('MapLibre.getInstance'));
assert(map.includes('CameraUpdateFactory.newCameraPosition'));
assert(map.includes('PolylineOptions()'));
// Web Rider has no native-only work-demand pin/bubble obstructing navigation.
assert(map.includes('private fun renderDemand(state: RiderNavigationMapState, force: Boolean)'));
assert(!map.includes('demandMarkers') && !map.includes('MarkerOptions()'));
assert(!map.includes('.title("พื้นที่งานระดับ "'));
assert(map.includes('RiderNavigationMapRegistry'));
assert(map.includes('holder.retainAcrossVisibilityLoss()'));
assert(map.includes('MotionEvent.ACTION_MOVE'));
assert(map.includes('currentUserGesture.value()'));
assert(map.includes('navigationCameraDecision('));
// The Android camera must follow the existing QueueGo Web gesture contract:
// two-finger transforms preserve FOLLOW, one-finger pan enters FREE_PAN.
assert(webMap.includes('reanchorNavigationAfterGesture(map)'));
assert(webMap.includes('state.manualZoomUntil=Date.now()+12000'));
assert(viewMode.includes('navigationTouchOwnsFollow(pointerCount: Int, multiTouchStarted: Boolean)'));
assert(viewMode.includes('NAVIGATION_TRANSFORM_REANCHOR_MS = 110L'));
assert(viewMode.includes('NAVIGATION_MANUAL_ZOOM_HOLD_MS = 12_000L'));
assert(map.includes('navigationTouchOwnsFollow(event.pointerCount, multiTouchGesture)'));
assert(map.includes('if (transformGestureActive) return'));
assert(map.includes('SystemClock.elapsedRealtime() < manualZoomUntilElapsed'));
assert(map.includes('mapView.postDelayed({'));
assert(map.includes('renderCamera(it, lastState, force = true)'));
assert(map.includes('navigationOverviewViewportDecision('));
assert(camera.includes('speed >= 18.0 -> 15.0')&&camera.includes('speed >= 11.0 -> 16.0')&&
  camera.includes('speed >= 5.0 -> 17.0')&&camera.includes('speed <= 1.5 -> maxOf(base, 18.0)'));
assert(camera.includes('accuracyMeters > 60f')&&camera.includes('remainingRouteMeters <= 60.0 -> 19.0')&&
  camera.includes('remainingRouteMeters <= 180.0 -> 18.0'));
assert(cameraTest.includes('speedZoomUsesWebThresholdsAndIgnoresIndependentNextTurnDistance')&&
  cameraTest.includes('assertEquals(15.0, navigationZoom(17, 18f')&&
  cameraTest.includes('assertEquals(17.0, navigationZoom(17, 8f')&&
  cameraTest.includes('assertEquals(18.0, navigationZoom(17, 1.5f'));
assert(mapMotion.includes('interpolator = LinearInterpolator()')&&
  mapMotion.includes('navigationCameraEaseInOut(animation.animatedFraction)'));
assert(mapMotion.includes('RIDER_MARKER_MIN_ANIMATION_MS = 260L')&&
  mapMotion.includes('RIDER_MARKER_MAX_ANIMATION_MS = 1_250L')&&
  mapMotion.includes('Double = 0.01'));
assert(webMap.includes("'symbol-spacing':80")&&
  mapMotion.includes('RIDER_ROUTE_ARROW_SPACING_PX = 80.0')&&
  mapMotion.includes('toScreenLocation(LatLng(point.latitude, point.longitude))')&&
  mapMotion.includes('fromScreenLocation(')&&
  mapMotion.includes('android.graphics.PointF(x.toFloat(), y.toFloat())')&&
  !mapMotion.includes('distance += 65.0'));
assert(webRider.includes("background:'+(done?'#94a3b8':'#16a34a')")&&
  webRider.includes("background:#16a34a")&&webRider.includes("background:#e6002d")&&
  mapMotion.includes('PropertyFactory.circleRadius(10f)')&&
  mapMotion.includes('PropertyFactory.circleRadius(8f)')&&
  mapMotion.includes('PropertyFactory.circleColor(android.graphics.Color.rgb(148, 163, 184))')&&
  mapMotion.includes('val stops = state.stops.asSequence()')&&
  mapMotion.includes('.filter { it.point.valid && it.id.isNotBlank() && it.sequence > 0 }'));
assert(webRider.includes('const RIDER_ROUTE_OFF_ROUTE_METERS=30')&&
  webRider.includes('const RIDER_ROUTE_REROUTE_SUSTAIN_MS=3000')&&
  webRider.includes('const RIDER_ROUTE_REROUTE_COOLDOWN_MS=20000')&&
  guidance.includes('BASE_OFF_ROUTE_THRESHOLD_METERS = 30.0')&&
  guidance.includes('requiredOffRouteMillis: Long = 3_000L')&&
  guidance.includes('rerouteCooldownMillis: Long = 20_000L')&&
  guidance.includes('cooldownSatisfied && offRoute && sustainedOffRoute')&&
  !guidance.includes('MISSED_MANEUVER_REROUTE_COOLDOWN_MILLIS')&&
  coordinator.includes('progressTracker.reset(clearRerouteCooldown = false)'));
assert(webRider.includes('if(!S.activeOrder)riderRouteOverviewActive=false')&&
  viewMode.includes('fun onActiveStopChanged(navigationActive: Boolean)')&&
  viewMode.includes('if (!navigationActive && state.mode == NavigationViewMode.OVERVIEW)')&&
  riderIntegration.includes('viewModeController.onActiveStopChanged(navigationActive)'));
assert(overviewPolicy.includes('state.stops')&&
  overviewPolicy.includes('.filter { it.point.valid && it.id.isNotBlank() && it.sequence > 0 }')&&
  !overviewPolicy.includes('state.remainingStops().forEach { add(it.point) }'));
assert(webRider.includes("interval?.source!=='osm:maxspeed'")&&
  webRider.includes("speedLimits.push({start,end,kph,raw})")&&
  webRider.includes("cache.activeWork?qgRiderSpeedLimitAt(cache,match.progressMeters):null")&&
  webRider.includes("speedLimit.className='qg-speed-limit'")&&
  webRider.includes('qg-speed-limit')&&
  webRider.includes('qgRiderSpeedLimitAt(cache,match.progressMeters)'));
assert(mapMotionTest.includes('markerAnimationUsesTheSame260To1250MillisecondClampAsWeb')&&
  mapMotionTest.includes('everyChangedGpsFixMovesTheMarkerAsOnWeb'));
assert(!/com\.longdo|LongdoMapAndroidSDK|longdo\.map|api\.longdo|ms\.longdo/i.test(map));

assert(route.includes('class QueueGoNavigationRouteClient'));
assert(route.includes('/map-api/route'));
assert(route.includes('.put("profile", "rider_delivery")'));
assert(route.includes('.put("preference", "fastest")'));
assert(route.includes('QUEUEGO_ROUTE_RESPONSE_MAX_BYTES = 4 * 1024 * 1024'));
assert(!/api\.longdo|google\.navigation|osrm/i.test(route.toLowerCase()));
assert(routeContract.includes('interface NavigationRouteProvider'));
assert(!/longdo|google/i.test(routeContract.toLowerCase()));

assert(locationService.includes('NAVIGATION_LOCATION_INTERVAL_MS = 500L'));
assert(locationService.includes('NAVIGATION_LOCATION_MIN_INTERVAL_MS = 250L'));
assert(locationService.includes('Priority.PRIORITY_HIGH_ACCURACY'));
assert(manifest.includes('android.permission.FOREGROUND_SERVICE_LOCATION'));
assert(manifest.includes('android:name=".RiderNavigationLocationService"'));
assert(!manifest.includes('longdo.map.key'));

// Visual label diagnosis must inspect dynamically appended live symbol layers;
 // the serialized base Style JSON alone omits Style.Builder.withLayer entries.
assert(installedCountryTest.includes('map.style?.getLayer(id)') &&
  installedCountryTest.includes('symbol?.getTextField()') &&
  installedCountryTest.includes('symbol?.getTextFont()') &&
  installedCountryTest.includes('symbol?.getTextOpacity()') &&
  installedCountryTest.includes('Thread.sleep(5_000)') &&
  installedCountryTest.includes('visual_label_acceptance", false'));
// Native glyph lifecycle evidence distinguishes asset-loading failures from
// style/placement issues; querying features or hashing bundled PBFs alone cannot.
for (const callback of ['addOnGlyphsRequestedListener', 'addOnGlyphsLoadedListener',
  'addOnGlyphsErrorListener']) {
  assert(runtimeHost.includes(callback), 'Missing native glyph event callback: ' + callback);
}
const shortbreadStyle=read('native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt');
assert(shortbreadStyle.includes('PropertyFactory.textFont(arrayOf("Sans Regular"))'),
  'Country label style must request the embedded fontstack name');
for (const range of ['0-255','256-511','512-767','3584-3839','8192-8447']) {
  assert(fs.existsSync('native-android/rider-maplibre-osm/src/main/assets/fonts/Sans Regular/' + range + '.pbf'),
    'Missing bundled Sans Regular fontstack alias ' + range);
}
assert(runtimeHost.includes('offlineGlyphDiagnosticSummary()'));
assert(installedCountryTest.includes('installOfflineGlyphDiagnostics()'));
assert(installedCountryTest.includes('offlineGlyphDiagnosticSummary()'));
assert(installedCountryTest.includes('QueueGoGlyphQA'));
assert(runtimeTest.includes('gpsRefreshTabReturnAndOverlayUpdatesReuseSameMapView'));
assert(runtimeTest.includes('manualDragReportsGestureButTapDoesNot'));
assert(runtimeTest.includes('activityRecreationCreatesFreshMapViewForFreshActivityContext'));
assert(runtimeHost.includes('intent.getBooleanExtra("installedThailandMap", false)') &&
  runtimeHost.includes('queueGoMapVectorSourceUri(this)') &&
  runtimeHost.includes('else "asset://queuego-nav-runtime-tilejson.json"'),
  'Runtime fixtures must stay the default; installed-country source requires explicit opt-in');
assert(!/Longdo|MapGLSurfaceView/i.test(runtimeTest+runtimeHost));

assert(workflow.includes('navigation-map-runtime:'));
assert(workflow.includes('maplibre-osm-runtime:'));
assert(!/LongdoMapAndroidSDK|api\.longdo|ms\.longdo/i.test(workflow));

console.log('Standalone QueueGo Rider MapLibre navigation contracts: PASS');
