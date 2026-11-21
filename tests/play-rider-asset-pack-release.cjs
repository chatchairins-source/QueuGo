'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const get=x=>fs.readFileSync(x,'utf8');
const root=get('native-android/build.gradle.kts');
const rider=get('native-android/rider/build.gradle.kts');
const settings=get('native-android/settings.gradle.kts');
const flow=get('.github/workflows/build-native-release.yml');
const source=get('native-android/qa/fetch-rider-offline-candidate.py');
const stage=get('native-android/qa/stage-play-rider-assets.py');
assert.match(root,/com\.android\.asset-pack.*version "9\.4\.0"/,'asset pack plugin must match AGP');
for(const name of ['rider_country_map','rider_country_routing']){
  assert.ok(settings.includes(':'+name),'settings must include '+name);
  const spec=get('native-android/'+name+'/build.gradle.kts');
  assert.ok(spec.includes('packName.set("'+name+'")'),'pack module name missing');
  assert.ok(spec.includes('deliveryType.set("install-time")'),'must be install-time for airplane mode from first launch');
}
assert.ok(rider.includes('QG_RIDER_PLAY_ASSET_PACKS')&&rider.includes('assetPacks += listOf('),'Play flag and packs missing');
assert.ok(rider.includes('if (!playAssetPackAab)'),'debug and QA APKs must keep existing assets');
assert.ok(flow.includes('QG_RIDER_PLAY_ASSET_PACKS=1 gradle -p native-android'),'certified AAB must build packs');
assert.ok(flow.includes(':rider:assembleRelease --no-build-cache'),'signed QA APK still packages offline assets');
assert.ok(flow.includes('stage-play-rider-assets.py --verify-bundle'),'verify exact pack contents after build');
assert.ok(flow.includes('fetch-rider-offline-candidate.py'),'reuse exact-HEAD offline-tested APK not synthetic assets');
assert.ok(flow.includes('QG_REQUIRED_OFFLINE_RUN_ID'),'release must pin certified offline run');
assert.ok(source.includes('QG_REQUIRED_OFFLINE_RUN_ID'),'offline candidate retrieval must honor certification run');
for(const flag of ['GITHUB_SHA','rider_country_map','rider_country_routing',
                   'zipfile.ZipFile','digest(apk)','PMTiles','ustar','signed']){
  assert.ok(stage.toLowerCase().includes(flag.toLowerCase()),'missing Rider assets verifier: '+flag);
}
assert.ok(!flow.includes('QG_RIDER_PLAY_ASSET_PACKS=1 gradle -p native-android :rider:assembleRelease'),'do not make a release APK that loses native offline map');
console.log('QueueGo Play Rider nationwide asset packs: SOURCE CONTRACT PASS; actual signed Play AAB + device tests still REQUIRED');
