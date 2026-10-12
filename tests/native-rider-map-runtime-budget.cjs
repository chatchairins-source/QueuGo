const assert=require('assert');
const fs=require('fs');

const source=fs.readFileSync('native-android/rider/src/androidTest/java/com/queuego/rider/RiderMapRuntimeTest.kt','utf8');

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

console.log('Native Rider Longdo runtime evidence budget contracts: PASS');
