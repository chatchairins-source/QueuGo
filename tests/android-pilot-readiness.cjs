'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const workflow=read('.github/workflows/build-native-rider-pilot.yml');
let checks=0;
const ok=(value,why)=>{assert.ok(value,why);checks++};
ok(!fs.existsSync(path.join(root,'.github/workflows/build-queuego-pilot-apks.yml')),'obsolete Capacitor pilot APK workflow must remain retired');
ok(!fs.existsSync(path.join(root,'.github/workflows/build-queuego-apks.yml')),'obsolete Capacitor release workflow must remain retired');
ok(workflow.includes('name: QueueGo Native Android Pilot'),'Pilot must be identified as Native Android');
ok(workflow.includes('Full QueueGo regression')&&workflow.includes('npm test'),'Pilot must run complete QueueGo regression');
ok(workflow.includes('parallel-role-smoke:')&&workflow.includes('role: [customer, merchant, rider]'),'Pilot must test all three Native roles');
ok(workflow.includes('max-parallel: 2')&&workflow.includes('fail-fast: false'),'All role jobs remain isolated with bounded simultaneous dependency downloads');
ok(workflow.includes('github.event.pull_request.head.sha || github.sha'),'Pilot must checkout exact candidate SHA');
ok(workflow.includes(':testDebugUnitTest')&&workflow.includes(':assembleDebug'),'Pilot must run Kotlin/JVM tests and assemble real Debug APKs');
ok(workflow.includes(':shared:testDebugUnitTest')&&workflow.includes(':rider-navigation-map:testDebugUnitTest'),'Pilot must verify shared and Rider map unit tests');
ok(workflow.includes('Verify Android 16 KB page-size packaging')&&workflow.includes('verify-16kb-page-size.py'),'Pilot must validate 16 KB APK packaging');
ok(workflow.includes('QG_FIREBASE_GOOGLE_SERVICES_JSON_B64'),'Pilot must use real Firebase configuration when available');
ok(workflow.includes('Firebase config has no com.queuego.rider Android client'),'Pilot must fail closed on mismatched Rider Firebase identity');
ok(workflow.includes('Native Firebase config:')&&workflow.includes('native-android/rider/google-services.json'),'Pilot must provision role-bound Native Firebase configuration');
ok(!workflow.includes('QG_ANDROID_KEYSTORE_B64')&&!workflow.includes('QG_ANDROID_STORE_PASSWORD'),'Pilot must not consume release signing secrets');
ok(!workflow.includes(':customer:bundleRelease')&&!workflow.includes(':merchant:bundleRelease')&&!workflow.includes(':rider:bundleRelease'),'Pilot must not fabricate certified AABs');
ok(workflow.includes('Reject WebView'),'Pilot must reject embedded WebView paths');
ok(workflow.includes('Capture native launch, map and voice service lifecycle on Android'),'Pilot must retain installed-APK lifecycle evidence');
ok(workflow.includes('Test installed country archive and camera without internet'),'Pilot must verify actual offline country map runtime');
ok(workflow.includes('queuego-parallel-')&&workflow.includes('-jvm-apk-evidence'),'Role-specific test evidence must be retained');
ok(workflow.includes('queuego-native-pilot-apks'),'Pilot debug APK artifacts must be tagged as test-only');
for(const [role,appId] of [
  ['customer','com.queuego.customer'],['merchant','com.queuego.merchant'],['rider','com.queuego.rider']
]){
  const gradle=read('native-android/'+role+'/build.gradle.kts');
  ok(gradle.includes('applicationId = "'+appId+'"'),'Native '+role+' must retain production package identity');
  ok(gradle.includes('compileSdk = 36')&&gradle.includes('targetSdk = 36'),'Native '+role+' must target Android API 36');
  ok(workflow.includes("'"+appId+"'"),'Pilot Firebase mapping must explicitly include '+appId);
}
console.log(JSON.stringify({checks,failures:0,scope:'Native Pilot Kotlin/JVM Debug APKs, Firebase identity, offline/launch and signing isolation; physical certification remains separate'}));