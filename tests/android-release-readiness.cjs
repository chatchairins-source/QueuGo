'use strict';
// QueueGo ships three Kotlin/Jetpack Compose Android apps, not Capacitor WebViews.
// Keep this legacy-named test as an independent Native release-readiness gate.
const fs=require('node:fs'),assert=require('node:assert/strict');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;
const ok=(condition,description)=>{assert.ok(condition,description);checks++};
for(const old of [
  '.github/workflows/build-queuego-apks.yml',
  '.github/workflows/build-queuego-pilot-apks.yml',
  '.github/workflows/publish-approved-icon-rc.yml',
  '.github/workflows/publish-approved-apks.yml'
]) ok(!fs.existsSync(old),'retired Capacitor or legacy APK publication workflow must stay absent: '+old);

const release=read('.github/workflows/build-native-release.yml');
const certification=read('.github/workflows/native-release-certification.yml');
const pilot=read('.github/workflows/build-native-rider-pilot.yml');
const evidence=read('native-android/qa/verify-native-release-artifacts.py');
const gate=read('native-android/qa/verify-native-release-gate.py');

ok(release.includes('workflow_dispatch:'),'certified Native release must be manually dispatched');
ok(!/^\s+push:/m.test(release),'certified Native release must not run automatically from push');
ok(release.includes('refs/heads/main'),'release is restricted to reviewed main');
ok(release.includes('certification_run_id'),'release requires independent certification evidence');
ok(release.includes('QG_NATIVE_CERTIFICATION_RUN_ID'),'release binds the certification run');
ok(release.includes('QG_CERTIFIED_NATIVE_PILOT_RUN_ID'),'release binds successful exact-HEAD Native Pilot CI');
ok(release.includes('QG_CERTIFIED_BACKUP_RESTORE_RUN_ID'),'release requires an independently verified restore drill');
ok(release.includes('QG_ANDROID_SIGNING_CERT_SHA256'),'release requires expected signer certificate fingerprint');
ok(release.includes('QG_ANDROID_KEYSTORE_B64'),'release requires the real keystore secret');
ok(release.includes('QG_ANDROID_STORE_PASSWORD')&&release.includes('QG_ANDROID_KEY_PASSWORD'),'release requires private signing credentials');
ok(release.includes('QG_FIREBASE_GOOGLE_SERVICES_JSON_B64'),'release requires Firebase application configuration');
ok(release.includes('release versionCode must exceed observed Play maximum'),'release rejects stale Play Store version codes');
ok(release.includes('map_service_base_url must be an HTTPS QueueGo Map base URL'),'release validates its production map-service URL');
ok(release.includes('Reject WebView before release packaging')&&release.includes('android\\.webkit\\.WebView'),'Native release rejects WebViews rather than bundling a website');
ok(release.includes('verify-native-release-gate.py'),'release runs hard certification gate before packaging');
ok(release.includes('verify-native-release-artifacts.py'),'release verifies the built signed artifacts');
ok(release.includes('verify-16kb-page-size.py'),'release checks Android 16 KB packaging');
ok(release.includes('bundletool-all-1.18.3.jar')&&release.includes('sha256sum -c -'),'release pins and hashes bundletool');
ok(release.includes('node tests/native-launcher-assets.cjs')&&release.includes('node tests/native-category-assets.cjs'),'release verifies branded assets');
ok(pilot.includes('Full QueueGo regression')&&pilot.includes('npm test'),'Native Pilot retains full integration regression');
ok(pilot.includes('testDebugUnitTest')&&pilot.includes('assembleDebug'),'Native Pilot compiles and tests the actual Android roles');

for(const [role,applicationId] of [
  ['customer','com.queuego.customer'],
  ['merchant','com.queuego.merchant'],
  ['rider','com.queuego.rider']
]){
  const gradle=read('native-android/'+role+'/build.gradle.kts');
  const manifest=read('native-android/'+role+'/src/main/AndroidManifest.xml');
  ok(gradle.includes('namespace = "'+applicationId+'"')&&gradle.includes('applicationId = "'+applicationId+'"'),role+' has canonical package identity');
  ok(gradle.includes('compileSdk = 36')&&gradle.includes('targetSdk = 36'),role+' compiles and targets Android API 36');
  ok(gradle.includes('org.jetbrains.kotlin.plugin.compose'),role+' uses Native Compose rather than Capacitor');
  ok(gradle.includes('QG_'+role.toUpperCase()+'_VERSION_CODE')&&gradle.includes('QG_NATIVE_VERSION_NAME'),role+' reads approved version metadata');
  ok(release.includes(':'+role+':assembleRelease')&&release.includes(':'+role+':bundleRelease'),role+' builds real release APK and AAB');
  ok(manifest.includes('android:allowBackup="false"')&&manifest.includes('android:usesCleartextTraffic="false"'),role+' disables unsafe backup and cleartext traffic');
  ok(manifest.includes('android.permission.ACCESS_FINE_LOCATION')&&manifest.includes('android.permission.ACCESS_COARSE_LOCATION'),role+' declares explicit GPS permissions');
  ok(manifest.includes('android.permission.POST_NOTIFICATIONS'),role+' declares notification permission');
  ok(!manifest.includes('android.permission.ACCESS_BACKGROUND_LOCATION'),role+' does not silently request always-on background location');
  ok(manifest.includes('queuego.map.service.base_url'),role+' uses configured map service origin');
  ok(manifest.includes('android:name=".MainActivity"'),role+' uses a native launcher activity');
}
const riderManifest=read('native-android/rider/src/main/AndroidManifest.xml');
ok(riderManifest.includes('android.permission.FOREGROUND_SERVICE_LOCATION')&&riderManifest.includes('android:foregroundServiceType="location"'),'Rider navigation has explicit location foreground-service contract');

ok(certification.includes('Require successful exact-HEAD Native Pilot CI'),'certification requires successful real Native CI');
ok(certification.includes('Require successful exact-HEAD Backup Restore Drill'),'certification requires verified backup recovery');
ok(certification.includes('Require reconciled Production migration ledger'),'certification checks Production migration consistency');
ok(certification.includes('queuego-native-release-evidence'),'certification uses private role-bound evidence');
ok(gate.includes('PHYSICAL_GATE_REQUIRED_ROLES')&&gate.includes('physical gate must be explicitly operator-certified'),'release demands explicit physical-device certification');
ok(gate.includes('requires two distinct devices')&&gate.includes('requires two distinct networks'),'voice release needs two real devices on separate networks');
ok(evidence.includes('application-debuggable')&&evidence.includes('--print-certs'),'post-build verifier rejects debug builds and checks signing certificates');
ok(evidence.includes('apk_aab_signer_match')&&evidence.includes('QG_ANDROID_SIGNING_CERT_SHA256'),'APK/AAB signer must match certified signing identity');
ok(evidence.includes('github_run_id')&&evidence.includes('aab_sha256')&&evidence.includes('apk_sha256'),'release evidence preserves source run and artifact hashes');
console.log(JSON.stringify({checks,failures:0,scope:'Native Kotlin/Compose Android release readiness; no Capacitor fallback or Production writes'}));