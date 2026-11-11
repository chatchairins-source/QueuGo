'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = path => fs.readFileSync(path, 'utf8');
const settings = read('native-android/settings.gradle.kts');
const rider = read('native-android/rider/build.gradle.kts');
const mapPack = read('native-android/rider_offline_map_pack/build.gradle.kts');
const routePack = read('native-android/rider_offline_routing_pack/build.gradle.kts');
const packageJson = JSON.parse(read('package.json'));
const routeWorkflow = read('.github/workflows/rider-offline-routing.yml');
const certification = read('.github/workflows/native-release-certification.yml');
const release = read('.github/workflows/build-native-release.yml');
const verifier = read('native-android/qa/verify-native-release-artifacts.py');
const packTool = read('native-android/qa/rider_offline_asset_pack.py');

assert.ok(packageJson.scripts.test.includes('node tests/native-offline-asset-pack.cjs'), 'asset-pack contract must run in npm test');
assert.ok(packageJson.scripts.test.includes('python3 tests/rider-offline-asset-pack.py'), 'asset-pack integrity fixtures must run in npm test');
for (const name of ['rider_offline_map_pack', 'rider_offline_routing_pack']) {
  assert.ok(settings.includes(`:${name}`), `Gradle settings must include ${name}`);
  assert.ok(rider.includes(`:${name}`), `Rider AAB must attach ${name}`);
}
for (const [name, source] of [['map', mapPack], ['routing', routePack]]) {
  assert.ok(source.includes('com.android.asset-pack'), `${name} pack must use the Android asset-pack plugin`);
  assert.ok(source.includes('deliveryType.set("install-time")'), `${name} pack must be ready with Rider on first install`);
}
assert.ok(routeWorkflow.includes('rider_offline_asset_pack.py prepare'), 'offline routing CI must prepare both source-bound asset packs');
assert.ok(routeWorkflow.includes(':rider:bundleDebug'), 'offline routing CI must build the Rider Play AAB');
assert.ok(routeWorkflow.includes('rider_offline_asset_pack.py verify-aab'), 'offline routing CI must verify pack contents in the exact-source AAB');
assert.ok(certification.includes('Require exact-HEAD Rider offline routing and asset-pack evidence'), 'release certification must require exact-head offline data');
assert.ok(certification.includes('rider-unreleased-country-part'), 'release certification must retain the exact-source APK parts needed to build release packs');
assert.ok(release.includes('Rebuild Rider Play Asset Packs from the exact certified field APK'), 'release build must derive pack inputs from the attested field APK');
assert.ok(release.includes('rider_offline_asset_pack.py prepare') && release.includes('--build-commit-file'), 'release must verify exact APK SHA and commit before extracting map/route assets');
assert.ok(release.includes(':rider:bundleRelease') && release.includes(':rider:assembleRelease'), 'release must produce Play AAB and standalone offline Rider APK separately');
assert.ok(release.includes('verify-aab') && release.includes('verify-apk'), 'release must verify offline data in both Rider delivery artifacts');
assert.ok(packTool.includes('1_500_000_000') && packTool.includes('ODbL-1.0'), 'pack preparation must enforce size and ODbL provenance');
assert.ok(packTool.includes('Rider AAB must deliver nationwide data from asset packs'), 'Play AAB must keep nationwide files outside the base module');
assert.ok(verifier.includes('verify_rider_offline_aab') && verifier.includes('verify_rider_standalone_apk'), 'signed artifact verification must check both Rider offline delivery forms');

console.log(JSON.stringify({checks: 23, failures: 0, scope: 'Rider install-time Play Asset Delivery source binding and size gate'}));
