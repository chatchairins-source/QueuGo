const assert=require('assert');
const fs=require('fs');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');
const riderApp=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const moduleBuild=fs.readFileSync('native-android/rider-navigation-map/build.gradle.kts','utf8');
const map=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt','utf8');
const route=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/LongdoNavigationRouteClient.kt','utf8');
const cameraPolicy=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationCameraPolicy.kt','utf8');
const models=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationMapModels.kt','utf8');
const workflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
const runtimeTest=fs.readFileSync('native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/NavigationMapRuntimeTest.kt','utf8');
const runtimeActivity=fs.readFileSync('native-android/rider-navigation-map/src/debug/java/com/queuego/ridernavigationmap/NavigationMapRuntimeActivity.kt','utf8');
const runtimeRunner=fs.readFileSync('native-android/qa/run-rider-navigation-map-runtime.py','utf8');

assert(settings.includes('":rider-navigation-map"'),
  'Standalone Rider navigation map module must be registered');
assert(moduleBuild.includes('id("com.android.library")')&&moduleBuild.includes('implementation(project(":shared"))'),
  'Navigation map must stay an Android library reusing the shared Longdo SDK');
assert(moduleBuild.includes('com.google.android.gms:play-services-location:21.3.0'),
  'Standalone Longdo runtime must include the Google Play Services location classes required by MapLocationManager');
assert(!riderBuild.includes('project(":rider-navigation-map")')&&!riderApp.includes('com.queuego.ridernavigationmap'),
  'New navigation map must remain isolated from the current Rider APK until its map gate passes');
assert(map.includes('val holder = remember(stateKey, lifecycle)')&&map.includes('AndroidView(')&&map.includes('factory =')&&map.includes('update ='),
  'MapView owner must survive normal Compose recomposition and lifecycle-safe tab remounts while only updating overlays');
assert(map.includes('Deliberately keep lastRiderFix, map, pins and route layers')&&
  !map.includes('latestDeviceLocation = null')&&!map.includes('clearPin()'),
  'Refresh/resume must not clear the existing map or rebuild every marker');
assert(!map.includes('LaunchedEffect(state)')&&
  map.includes('state.stops == stopSource')&&map.includes('state.routes == routeSource')&&
  !map.includes('state.stops === stopSource')&&!map.includes('state.routes === routeSource')&&
  !map.includes('routes.hashCode()'),
  'GPS/order refresh must not render twice or rebuild structurally-equivalent route geometry');
assert(map.includes('stopSource = null')&&map.includes('routeSource = null')&&
  map.includes('if (!pushed) renderSucceeded = false')&&map.includes('failedToRemove.isNotEmpty()'),
  'Transient Longdo pin/line failures must remain retryable instead of caching a partial frame');
assert(map.includes('fun attachMap(nextMap: com.longdo.api.Map): Boolean')&&
  map.includes('if (map === nextMap) return false')&&
  map.includes('if (newSdkMap) {')&&
  map.includes('never reset base/camera or force the visible map to assemble again'),
  'Repeated Longdo onMapCreated callbacks for the same SDK map must not reinitialize base/camera');
assert(map.includes('RiderNavigationMapRegistry')&&
  map.includes('holder.container?.takeIf { it.context === context }')&&
  map.includes('holder.retainAcrossVisibilityLoss()')&&
  runtimeActivity.includes('hideMapForAnotherTab')&&runtimeActivity.includes('showMapAfterReturningHome')&&
  runtimeActivity.includes('hasMapView')&&runtimeTest.includes('awaitMapPresence')&&
  runtimeTest.includes('expected = false')&&runtimeTest.includes('expected = true')&&
  runtimeTest.includes('Rider tab return recreated MapGLSurfaceView')&&
  runtimeTest.includes('Rider tab return recreated Longdo map')&&
  runtimeTest.includes('Tab return continuity snapshot remained stuck over live map'),
  'Leaving Home for another Rider tab must actually unmount/remount the map and reuse the same map objects without leaking an old Activity context');
assert(map.includes('activeDestroyObserver')&&
  map.includes('Keep the lifecycle observer registered while Home is unmounted')&&
  map.includes('remember(stateKey, lifecycle)')&&
  runtimeActivity.includes('mapViewUsesActivityContext')&&
  runtimeTest.includes('retainedMapIsDisposedWhenActivityDiesOffHome')&&
  runtimeTest.includes('destroyed Activity'),
  'A retained off-Home map must be destroyed with its Activity and recreated with the new Activity context');
assert(map.includes('fun retainAcrossVisibilityLoss()')&&
  map.includes('Capture a frozen frame quietly for emergency continuity')&&
  map.includes('Lifecycle.Event.ON_PAUSE -> holder.retainAcrossVisibilityLoss()')&&
  map.includes('holder.retainAcrossVisibilityLoss()')&&
  map.includes('fun shutdownSdk()')&&
  map.includes('activeView.onPause()')&&
  map.indexOf('activeView.onPause()')===map.lastIndexOf('activeView.onPause()')&&
  !map.includes('view?.onPause()')&&!map.includes('createdView.onPause()')&&
  !map.includes('activeView.onResume()')&&
  map.includes('Calling it after ordinary background/foreground transitions can replace renderer.map'),
  'Ordinary tab/background visibility loss must not call Longdo onPause/onResume; SDK shutdown is reserved for real surface disposal');
assert(map.includes('setPreserveEGLContextOnPause(true)'),
  'Navigation map must preserve the GL context across background/resume when Android allows it');
assert(map.includes('PixelCopy.request')&&map.includes('snapshotOverlay')&&
  map.includes('showContinuityBridge')&&map.includes('settleContinuityBridge')&&
  map.includes('The bridge is shown only when attachMap() detects a real map')&&
  models.includes('snapshotBridgeCount: Int = 0')&&models.includes('snapshotVisible: Boolean = false'),
  'When Android recreates the Longdo renderer, a captured SurfaceView frame must hide visible map reassembly');
assert(map.includes('map.setDoTileAnimation(false)')&&map.includes('map.setDoPreShowCache(true)')&&
  map.includes('setDoPreDrawCache(true)'),
  'Longdo replacement maps must prefer cached tiles and disable tile-appearance animation');
assert(cameraPolicy.includes('navigationCameraDecision')&&
  cameraPolicy.includes('speedMetersPerSecond >= 18f')&&
  cameraPolicy.includes('smoothNavigationBearing')&&
  cameraPolicy.includes('navigationPointAtDistance')&&
  map.includes('activeMap.setZoom(decision.zoom)')&&
  map.includes('decision.bearingDegrees?.let { activeMap.rotate(it, true) }'),
  'Navigation camera must adapt zoom to speed/arrival, smooth heading and look ahead on the matched road');
assert(map.includes('ACTIVE_ROUTE_COLOR')&&map.includes('FUTURE_ROUTE_COLOR')&&
  map.includes('setWidth(if (segment.style == NavigationRouteStyle.ACTIVE) 8 else 6)'),
  'Current route must be dark red and later route legs visibly faded');
assert(models.includes('resolvedActiveStopId')&&models.includes('styledRemainingRoutes'),
  'Module must support numbered multi-stop progression independently of RiderJob');
assert(route.includes('private val apiKey: String')&&route.includes('RouteService/geojson/route')&&
  !route.includes('347649ca13f49db0d8a599ae35f8de2b'),
  'Routing key must be injected and never hard-coded into the standalone module');
assert(workflow.includes(':rider-navigation-map:testDebugUnitTest'),
  'Native Pilot must compile and unit-test the standalone map module');
assert(models.includes('renderPassCount: Int = 0')&&
  map.includes('renderPassCount += 1')&&
  map.includes('state update that never reached the map'),
  'Diagnostics must expose applied render passes without forcing an overlay rebuild');
assert(runtimeTest.includes('awaitAppliedRender')&&runtimeTest.includes('awaitStopProgression')&&
  runtimeTest.includes('bounded GPS retries')&&
  runtimeTest.includes('GPS refresh recreated MapGLSurfaceView')&&
  runtimeTest.includes('GPS refresh rebuilt unchanged route layers')&&
  runtimeTest.includes('Healthy tab remount should not invoke the continuity bridge')&&
  runtimeTest.includes('Healthy background/resume should not invoke the continuity bridge')&&
  runtimeTest.includes('Longdo map replacement was not recorded')&&
  runtimeTest.includes('Equivalent backend refresh rebuilt unchanged route layers')&&
  runtimeTest.includes('Structurally-equal backend refresh should be suppressed before map render')&&
  runtimeActivity.includes('refreshEquivalentOrderSnapshot'),
  'Android runtime test must prove GPS/backend refresh stays in place and renderer replacement is visually bridged instead of exposed');
assert(runtimeActivity.includes('there are no QueueGo accounts, jobs, orders or Production writes')&&
  runtimeActivity.includes('refreshRiderFix')&&runtimeActivity.includes('advanceToSecondStop'),
  'Runtime host must remain isolated from QueueGo business data while exercising navigation state');
assert(workflow.includes('navigation-map-runtime:')&&
  workflow.includes('run-rider-navigation-map-runtime.py')&&
  runtimeRunner.includes(':rider-navigation-map:connectedDebugAndroidTest'),
  'Standalone navigation map must have a dedicated Android runtime gate');

console.log('Standalone Rider navigation map module contracts: PASS');
