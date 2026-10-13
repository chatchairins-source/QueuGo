const assert=require('assert');
const fs=require('fs');

const source=fs.readFileSync('native-android/rider/src/androidTest/java/com/queuego/rider/RiderMapRuntimeTest.kt','utf8');
const capture=fs.readFileSync('native-android/qa/capture-rider-launch.py','utf8');
const map=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/LongdoRiderMap.kt','utf8');

assert(source.includes('System.currentTimeMillis() - started < 60_000'),
  'Longdo tile evidence must allow up to 60 seconds for CI/emulator network latency');
assert(source.includes('finalFraction <= 0.01'),
  'Longdo tile evidence must retain the strict <=1% gray-tile completion threshold');
assert(source.includes('Real map tiles still incomplete after 60 seconds'),
  'Longdo tile failure must remain fail-closed after the extended evidence window');
assert(source.includes('first_frame_gray_fraction')&&source.includes('final_gray_fraction'),
  'Longdo runtime evidence must keep first/final tile completeness measurements');
assert(!source.includes('System.currentTimeMillis() - started < 30_000'),
  'The known-flaky 30 second Longdo tile deadline must not return');
assert(capture.indexOf('retain_real_map_frames(output / "map-frames")') <
  capture.indexOf('assert "OK (1 test)" in (after.stdout + after.stderr)'),
  'Actual first/final map frames and camera metrics must survive failed instrumentation');
assert(capture.includes('"rm", "-rf", "files/map-runtime"'),
  'Generated map frames must be cleared before testing to prevent stale evidence');
assert(map.includes('layerURL = "https://ms.longdo.com/map/msn-server/tile.php"')&&!map.includes('ms2.longdo.com/mmmap/img.php'),
  'Initial native layer must match the endpoint selected by the actual key checker');
assert(capture.includes('len(retained_frames["files"]) == 9'),
  'A successful instrumentation run must retain all nine valid original evidence files');
require('child_process').execFileSync('python3',['tests/native-map-evidence-retention.py'],{stdio:'inherit'});
assert(map.includes('R.drawable.qg_rider_current_location')&&
  map.includes('latestDeviceLocation')&&map.includes('recenterOnNextFix = true'),
  'Rider map must use the QueueGo current-location marker and refresh/recenter again on the next fresh location fix');
assert(map.includes('currentLocationPin')&&
  map.includes('existing.setLocation(mapLocation, true, RIDER_MAP_LOCATION_ANIMATION_SECONDS)')&&
  map.includes('pin.setOffset(PointF(0f, -0.4f))')&&
  !map.includes('append(location?.first).append(",").append(location?.second)'),
  'Rider GPS updates must move the current-location pin smoothly without rebuilding numbered stops on every fix');
assert(map.includes('private const val RIDER_MAP_LOCATION_ANIMATION_SECONDS = 2')&&
  !map.includes('RIDER_MAP_LOCATION_ANIMATION_SECONDS = 1.8'),
  'Rider Longdo marker animation duration must remain the SDK-compatible Int value');
assert(map.includes('RIDER_MAP_LOCATION_REFRESH_MS = 2_500L')&&
  map.includes('RIDER_MAP_LOCATION_REFRESH_METERS = 2f')&&
  map.includes('RIDER_MAP_LAST_KNOWN_MAX_AGE_MS = 120_000L')&&
  map.includes('RIDER_MAP_LOCATION_REFRESH_MS,')&&
  map.includes('ageMs in 0L..RIDER_MAP_LAST_KNOWN_MAX_AGE_MS'),
  'Rider map must follow a fresh foreground GPS fix instead of presenting an old last-known location as current');
assert(map.includes('latestDeviceLocation = null')&&
  map.includes('refreshOverlays(context, currentJob, currentPickups, currentBundleOrders)'),
  'Rider map must discard an in-memory pre-background fix and redraw from fresh location state on resume');
for(const marker of ['qg_rider_stop_1','qg_rider_stop_2','qg_rider_stop_3','qg_rider_stop_4']){
  assert(fs.existsSync('native-android/rider/src/main/res/drawable/'+marker+'.xml'),
    'Numbered Rider stop marker missing: '+marker);
}


console.log('Native Rider Longdo runtime evidence budget contracts: PASS');
