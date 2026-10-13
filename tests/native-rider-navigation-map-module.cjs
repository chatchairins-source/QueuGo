const assert=require('assert');
const fs=require('fs');

const settings=fs.readFileSync('native-android/settings.gradle.kts','utf8');
const riderBuild=fs.readFileSync('native-android/rider/build.gradle.kts','utf8');
const riderApp=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const moduleBuild=fs.readFileSync('native-android/rider-navigation-map/build.gradle.kts','utf8');
const map=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt','utf8');
const route=fs.readFileSync('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/LongdoNavigationRouteClient.kt','utf8');
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
assert(map.includes('val holder = remember(stateKey)')&&map.includes('AndroidView(')&&map.includes('factory =')&&map.includes('update ='),
  'MapView owner must survive normal Compose recomposition and only update overlays');
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
assert(map.includes('setPreserveEGLContextOnPause(true)'),
  'Navigation map must preserve the GL context across background/resume to avoid tile reconstruction');
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
assert(runtimeTest.includes('GPS refresh recreated MapGLSurfaceView')&&
  runtimeTest.includes('GPS refresh rebuilt unchanged route layers')&&
  runtimeTest.includes('Resume recreated Longdo map')&&
  runtimeTest.includes('Equivalent backend refresh rebuilt unchanged route layers')&&
  runtimeActivity.includes('refreshEquivalentOrderSnapshot'),
  'Android runtime test must prove GPS/backend refresh and resume reuse the exact map objects and overlays');
assert(runtimeActivity.includes('there are no QueueGo accounts, jobs, orders or Production writes')&&
  runtimeActivity.includes('refreshRiderFix')&&runtimeActivity.includes('advanceToSecondStop'),
  'Runtime host must remain isolated from QueueGo business data while exercising navigation state');
assert(workflow.includes('navigation-map-runtime:')&&
  workflow.includes('run-rider-navigation-map-runtime.py')&&
  runtimeRunner.includes(':rider-navigation-map:connectedDebugAndroidTest'),
  'Standalone navigation map must have a dedicated Android runtime gate');

console.log('Standalone Rider navigation map module contracts: PASS');
