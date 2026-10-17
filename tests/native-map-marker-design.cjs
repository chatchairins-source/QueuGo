const assert=require('assert');
const fs=require('fs');
const read=p=>fs.readFileSync(p,'utf8');
const riderRes='native-android/rider/src/main/res/drawable/';
const asset=name=>read(riderRes+name+'.xml');
const nav=read('native-android/rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
const integration=read('native-android/rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
const overlay=read('native-android/rider/src/main/java/com/queuego/rider/RiderReturnService.kt');

assert(asset('qg_marker_pickup').includes('#E6002D'));
assert(asset('qg_marker_dropoff').includes('#007F99'));
assert(asset('qg_marker_pickup')!==asset('qg_marker_dropoff'));
assert(asset('qg_rider_current_location')!==asset('qg_marker_pickup'));

assert(nav.includes('org.maplibre.android.maps.MapView'));
assert(nav.includes('MarkerOptions()')&&nav.includes('PolylineOptions()'));
assert(nav.includes('NavigationRouteStyle.ACTIVE')&&nav.includes('NavigationRouteStyle.FUTURE'));
assert(nav.includes('RiderNavigationMapRegistry')&&nav.includes('retainAcrossVisibilityLoss()'));
assert(!/com\.longdo|LongdoMapAndroidSDK|longdo\.map/i.test(nav));

assert(integration.includes('riderNavigationStops('));
assert(integration.includes('compareBy<RiderBundleOrder> { it.bundleSequence }'));
assert(integration.includes('compareBy<RiderBundleOrder> { it.dropRank }'));
assert(overlay.includes('R.drawable.qg_rider_brand_q'));
assert(!overlay.includes('text = "Q"'));

require('./native-rider-google-maps-return.cjs');
require('./native-android-source.cjs');
console.log('Native QueueGo MapLibre marker / overlay contracts: PASS');
