const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const workflow=read('.github/workflows/build-native-release.yml');

ok(/workflow_dispatch:/.test(workflow),'certified release must be manual only');
ok(!/\n\s*push:/.test(workflow),'certified release must never run from push');
ok(workflow.includes('refs/heads/queuego-native-android-v1'),'release must be restricted to Native Source of Truth branch');
ok(workflow.includes('actions: read'),'release must have Actions read permission for certification evidence');
ok(workflow.includes('certification_run_id'),'release must require an external certification run');
ok(workflow.includes('QG_NATIVE_CERTIFICATION_RUN_ID'),'release artifact provenance must retain the certification run id');
ok(workflow.includes('queuego-native-release-certification'),'release must download the fixed certification artifact');
ok(workflow.includes('native-release-evidence.json'),'release must require the certification report');
ok(workflow.includes('run_head')&&workflow.includes('GITHUB_SHA'),'certification evidence must match exact release HEAD');
ok(workflow.includes('run_conclusion')&&workflow.includes('success'),'certification run must have succeeded');
const metadataAt=workflow.indexOf('Validate certified release metadata');
const sdkAt=workflow.indexOf('Install Android SDK');
ok(metadataAt>=0&&sdkAt>metadataAt,'release metadata must fail fast before Android SDK setup');
ok(workflow.includes('release versionCode must exceed observed Play maximum'),'workflow must fail fast on stale Play versionCode history');
ok(workflow.includes('signing_certificate_sha256 must be a 32-byte SHA-256 fingerprint'),'workflow must fail fast on malformed signing identity');
for(const secret of [
  'QG_FIREBASE_GOOGLE_SERVICES_JSON_B64',
  'QG_ANDROID_KEYSTORE_B64',
  'QG_ANDROID_STORE_PASSWORD',
  'QG_ANDROID_KEY_ALIAS',
  'QG_ANDROID_KEY_PASSWORD'
]) ok(workflow.includes(secret),'release workflow missing secret gate: '+secret);
for(const pkg of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider']){
  ok(workflow.includes(pkg),'release workflow must validate Firebase client '+pkg);
}
ok(workflow.includes('mobilesdk_app_id'),'release workflow must reject incomplete Firebase clients');
for(const env of [
  'QG_NATIVE_VERSION_NAME',
  'QG_CUSTOMER_VERSION_CODE',
  'QG_MERCHANT_VERSION_CODE',
  'QG_RIDER_VERSION_CODE',
  'QG_CUSTOMER_PLAY_MAX_VERSION_CODE',
  'QG_MERCHANT_PLAY_MAX_VERSION_CODE',
  'QG_RIDER_PLAY_MAX_VERSION_CODE',
  'QG_ANDROID_SIGNING_CERT_SHA256'
]) ok(workflow.includes(env),'release workflow missing certified version/signing input '+env);
const gateAt=workflow.indexOf('python3 native-android/qa/verify-native-release-gate.py');
const buildAt=workflow.indexOf(':customer:assembleRelease');
const artifactAt=workflow.indexOf('python3 native-android/qa/verify-native-release-artifacts.py');
ok(gateAt>=0&&buildAt>gateAt,'hard release gate must run before release packaging');
ok(artifactAt>buildAt,'signed artifact verifier must run after packaging');
ok(workflow.includes('node tests/native-launcher-assets.cjs')&&workflow.includes('node tests/native-category-assets.cjs'),'certified release must re-verify launcher and Production category assets');
ok(workflow.includes('Reject WebView before release packaging')&&workflow.includes('android\\.webkit\\.WebView'),'certified release must independently reject WebView before packaging');
ok(workflow.includes('bundletool-all-1.18.3.jar'),'release workflow must pin bundletool 1.18.3');
ok(workflow.includes('a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'),'release workflow must pin the certified bundletool SHA-256');
ok(workflow.includes('QG_BUNDLETOOL_JAR')&&workflow.includes('QG_BUNDLETOOL_SHA256'),'release workflow must pass pinned bundletool identity to artifact verification');
for(const task of [
  ':customer:assembleRelease',':customer:bundleRelease',
  ':merchant:assembleRelease',':merchant:bundleRelease',
  ':rider:assembleRelease',':rider:bundleRelease'
]) ok(workflow.includes(task),'release workflow missing task '+task);
ok(workflow.includes('actions/upload-artifact@v4'),'certified outputs must remain internal Actions artifacts');
ok(!/gh release|create-release|softprops\/action-gh-release|ncipollo\/release-action/i.test(workflow),'workflow must not publish a GitHub Release');
ok(workflow.includes('if: always()')&&workflow.includes('queuego-release.keystore'),'decoded release credentials must be cleaned on every exit');
ok(!/assembleDebug|bundleDebug/.test(workflow),'certified release workflow must not package debug artifacts');

console.log(JSON.stringify({checks,failures:0,scope:'Fail-closed Native certified release workflow'}));
