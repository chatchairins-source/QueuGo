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

const map = read(root+'rider/src/main/java/com/queuego/rider/LongdoRiderMap.kt');
assert(map.includes('if (stopPins.keys == desiredStops) lastOverlayKey = key'));
assert(map.includes('ldmap.removePin(pin) }.onSuccess { stopPins.remove(stopKey) }'));
assert(map.includes('.append(it.job.deliveryLat).append(",").append(it.job.deliveryLng)'));
assert(!map.includes('stopPins.remove(key)?.let'));

assert(tracking.includes('existing.second.setLocation(MapLocation(point.longitude, point.latitude), true, 18)'));
assert(tracking.includes('bitmaps.getOrPut(point.role)'));
assert(!tracking.slice(tracking.indexOf('fun draw(points:'),tracking.indexOf('private fun Context.mapLifecycleOwner')).includes('clearPin()'));
assert(tracking.includes('holder.attachMap(map)'));
