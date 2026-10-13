const assert = require('assert');
const fs = require('fs');
const read = p => fs.readFileSync(p, 'utf8');
const root = 'native-android/';
const assets = root+'shared/src/main/res/drawable/';
for(const [shared,rider] of [['qg_map_pickup','qg_marker_pickup'],['qg_map_dropoff','qg_marker_dropoff'],['qg_map_rider','qg_rider_current_location']]){
 assert.strictEqual(read(assets+shared+'.xml'),read(root+'rider/src/main/res/drawable/'+rider+'.xml'),
  'Shared Customer marker must render the exact authoritative Rider icon family');
}
const tracking = read(root+'shared/src/main/java/com/queuego/shared/QueueGoLongdoMap.kt');
assert(tracking.includes('when (point.role)') && !tracking.includes('drawCircle'));
const customer = read(root+'customer/src/main/java/com/queuego/customer/CustomerTrackingPolicy.kt');
assert(customer.includes('QgMapRole.MERCHANT') && customer.includes('QgMapRole.CUSTOMER') && customer.includes('QgMapRole.RIDER'));
assert(customer.includes('0xFFE6002D') && customer.includes('0xFF007F99'));
assert(tracking.includes('runCatching { view?.renderer?.map != null }') && tracking.includes('.getOrDefault(false)'));
require('./native-map-marker-design.cjs');
require('./native-rider-route-bundle-parity.cjs');
console.log('Shared Customer/Rider semantic marker family and preserved crash guards: PASS');
