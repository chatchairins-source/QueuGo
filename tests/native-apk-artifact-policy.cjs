'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const yaml=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
const policy=yaml.indexOf('      - name: Verify fail-closed Native pilot APK artifact publication');
assert(policy>=0,'exact workflow must exercise this gate on each CI run');
const build=yaml.indexOf('      - name: Record exact-source debug APK provenance and per-file SHA-256');
const run=yaml.indexOf('      - name: Test installed country archive and camera without internet');
const publish=yaml.indexOf('          name: queuego-native-pilot-apks');
const exportGate=yaml.indexOf('      - name: Export freshly built role APKs after runtime gates');
assert(build>=0 && run>build && publish>run && exportGate>publish,
  'APK artifact must publish only after installed nationwide Android Runtime Gate');
assert.equal(yaml.split('name: queuego-native-pilot-apks').length-1,1,
  'only one source of native-pilot APK artifacts is allowed');
const region=yaml.slice(yaml.lastIndexOf('      - uses: actions/upload-artifact@',publish),exportGate);
assert(region.includes("if: ${{ env.QG_OFFLINE_MAP_ASSETS != '' }}"),
  'country map gate cannot be bypassed when running generic Native CI');
for(const role of ['customer','merchant','rider']){
  assert(region.includes('artifacts/native-pilot-apks/'+role+'-debug.apk'),
    'portable APK artifact missing role '+role);
}
assert(region.includes('artifacts/native-pilot-apks/SHA256SUMS') &&
  region.includes('artifacts/native-pilot-apks/PROVENANCE.txt'),
  'checksum and exact-source provenance must accompany APKs');
const prepared=yaml.slice(build,run);
assert(prepared.includes('cp "$apk" "artifacts/native-pilot-apks/$role-debug.apk"'),
  'SHA manifest must hash the same APK copy that will be uploaded');
assert(prepared.includes('cd artifacts/native-pilot-apks') &&
  prepared.includes('sha256sum customer-debug.apk merchant-debug.apk rider-debug.apk > SHA256SUMS') &&
  prepared.includes('sha256sum --check SHA256SUMS'),
  'downloaded artifact checksum must be self-contained and verified');
assert(yaml.includes("if: ${{ env.QG_OFFLINE_MAP_ASSETS != '' }}\n        timeout-minutes: 20"),
  'installed map runtime must remain enabled for a country build');
assert(yaml.includes('run: python3 native-android/qa/run-rider-navigation-map-runtime.py'),
  'must run real installed map instrumentation instead of a stub');
console.log('Native APK publication is gated on nationwide runtime and portable hashes');
