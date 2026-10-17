const assert=require('assert');
const fs=require('fs');
const read=p=>fs.readFileSync(p,'utf8');

const settings=read('native-android/settings.gradle.kts');
const riderBuild=read('native-android/rider/build.gradle.kts');
const moduleBuild=read('native-android/rider-navigation-map/build.gradle.kts');
const map=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
const route=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/QueueGoNavigationRouteClient.kt');
const routeContract=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationRouteProvider.kt');
const integration=read('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
const locationService=read('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt');
const manifest=read('native-android/rider/src/main/AndroidManifest.xml');
const runtimeTest=read('native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/NavigationMapRuntimeTest.kt');
const runtimeHost=read('native-android/rider-navigation-map/src/debug/java/com/queuego/ridernavigationmap/NavigationMapRuntimeActivity.kt');
const workflow=read('.github/workflows/build-native-rider-pilot.yml');

assert(settings.includes('":rider-navigation-map"'));
assert(moduleBuild.includes('id("com.android.library")'));
assert(moduleBuild.includes('implementation(project(":shared"))'));
assert(moduleBuild.includes('org.maplibre.gl:android-sdk-opengl:13.5.3'));
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
assert(map.includes('MarkerOptions()')&&map.includes('PolylineOptions()'));
assert(map.includes('RiderNavigationMapRegistry'));
assert(map.includes('holder.retainAcrossVisibilityLoss()'));
assert(map.includes('MotionEvent.ACTION_MOVE'));
assert(map.includes('currentUserGesture.value()'));
assert(map.includes('navigationCameraDecision('));
assert(map.includes('navigationOverviewViewportDecision('));
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

assert(runtimeTest.includes('gpsRefreshTabReturnAndOverlayUpdatesReuseSameMapView'));
assert(runtimeTest.includes('manualDragReportsGestureButTapDoesNot'));
assert(runtimeTest.includes('activityRecreationCreatesFreshMapViewForFreshActivityContext'));
assert(runtimeHost.includes('mapSourceUri = "asset://queuego-nav-runtime-tilejson.json"'));
assert(!/Longdo|MapGLSurfaceView/i.test(runtimeTest+runtimeHost));

assert(workflow.includes('navigation-map-runtime:'));
assert(workflow.includes('maplibre-osm-runtime:'));
assert(!/LongdoMapAndroidSDK|api\.longdo|ms\.longdo/i.test(workflow));

console.log('Standalone QueueGo Rider MapLibre navigation contracts: PASS');
