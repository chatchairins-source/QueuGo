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
assert(nav.includes('PolylineOptions()'));
// Web-parity map uses styled GeoJSON stop markers, never the retired native demand pins.
assert(nav.includes('GeoJsonSource('));
assert(!nav.includes('MarkerOptions()') && !nav.includes('demandMarkers'));
assert(nav.includes('GeoJsonSource(')&&nav.includes('CircleLayer(STOP_SHOP_BODY_LAYER_ID, STOP_SOURCE_ID)'));
assert(nav.includes('CircleLayer(STOP_CUSTOMER_BODY_LAYER_ID, STOP_SOURCE_ID)'));
assert(nav.includes('textField(Expression.get("sequence"))'));
assert(nav.includes('source.setGeoJson(stopFeatureCollectionJson(stops))'));
assert(nav.includes('PropertyFactory.circleRadius(10f)'));
assert(nav.includes('PropertyFactory.circleColor(android.graphics.Color.rgb(22, 163, 74))'));
assert(nav.includes('PropertyFactory.circleColor(android.graphics.Color.rgb(230, 0, 45))'));
assert(nav.includes('PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)'));
assert(nav.includes('Expression.get("numbered")'));
assert(nav.includes('Expression.get("completed")'));
assert(nav.includes('STOP_SHOP_DONE_LAYER_ID'));
assert(nav.includes('val stops = state.stops.asSequence()'));
assert(integration.includes('numbered = true'));
assert(integration.includes('marketPickups.filter { it.status.uppercase() != "CANCELLED" }'));
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
