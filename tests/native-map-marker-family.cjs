const assert=require('assert');
const fs=require('fs');
const read=p=>fs.readFileSync(p,'utf8');
const root='native-android/';
const sharedMap=read(root+'shared/src/main/java/com/queuego/shared/QueueGoMap.kt');
const trackingPolicy=read(root+'customer/src/main/java/com/queuego/customer/CustomerTrackingPolicy.kt');
const riderMap=read(root+'rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');

assert(sharedMap.includes('org.maplibre.android.maps.MapView'));
assert(sharedMap.includes('queueGoMapVectorSourceUri'));
assert(sharedMap.includes('queueGoMapStyle'));
assert(sharedMap.includes('GeoJsonSource')&&sharedMap.includes('CircleLayer'));
assert(!/com\.longdo|LongdoMapAndroidSDK|longdo\.map|api\.longdo/i.test(sharedMap));

assert(trackingPolicy.includes('QgMapRole.MERCHANT'));
assert(trackingPolicy.includes('QgMapRole.CUSTOMER'));
assert(trackingPolicy.includes('QgMapRole.RIDER'));
assert(trackingPolicy.includes('0xFFE6002D')&&trackingPolicy.includes('0xFF007F99'));

assert(riderMap.includes('SymbolLayer(RIDER_LAYER_ID, RIDER_SOURCE_ID)'));
assert(riderMap.includes('PropertyFactory.iconImage(RIDER_IMAGE_ID)'));
assert(riderMap.includes('PropertyFactory.iconRotate(Expression.get("bearing"))'));
assert(riderMap.includes('stop.sequence'));
assert(!/com\.longdo|LongdoMapAndroidSDK|longdo\.map/i.test(riderMap));

require('./native-map-marker-design.cjs');
require('./native-rider-route-bundle-parity.cjs');
console.log('Shared QueueGo MapLibre semantic marker family contracts: PASS');
