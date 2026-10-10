// Closed Beta v2 gate: latest Native main + verified push compatibility + release policy.
const fs=require('fs');
const assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const workflow=read('.github/workflows/build-native-closed-beta.yml');
const pilotWorkflow=read('.github/workflows/build-native-rider-pilot.yml');
const manifest=JSON.parse(read('docs/pilot-recovery-manifest.json'));
const readiness=read('docs/play-closed-beta-readiness.md');
const privacy=read('docs/privacy.html');
const legacyWorkflow=read('.github/workflows/build-queuego-apks.yml');

const checks=[];
const ok=(name,value)=>{checks.push([name,Boolean(value)]);assert.ok(value,name);};

ok('Native Closed Beta workflow exists',/name: QueueGo Native Android Closed Beta/.test(workflow));
ok('Native Closed Beta is manual only',/workflow_dispatch:/.test(workflow)&&!/^\s*push:\s*$/m.test(workflow));
ok('Native Closed Beta checks out Native source explicitly',(workflow.match(/ref: queuego-native-android-v1/g)||[]).length===2&&/QG_NATIVE_GIT_SHA=\$native_sha/.test(workflow)&&/QG_GIT_SHA=\$\(git rev-parse HEAD\)/.test(workflow));
ok('Native Closed Beta does not use legacy Capacitor build dir',!workflow.includes('android-build/'));
ok('Native Closed Beta requires restore drill',/restore_drill_certified/.test(workflow)&&/Backup\/Restore drill is not certified/.test(workflow));
ok('Native Closed Beta requires physical push',/physical_background_notification_certified/.test(workflow)&&/Physical native background notification is not certified/.test(workflow));
ok('Native Closed Beta requires TURN certification',/turn_relay_certified/.test(workflow)&&/TURN relay is not certified/.test(workflow));
ok('Native Closed Beta requires two-device voice E2E',/two_device_audio_e2e_certified/.test(workflow)&&/two-device native voice E2E/.test(workflow));
ok('Native Closed Beta locks microphone to user-initiated call policy',/USER_INITIATED_IN_APP_CALL_ONLY/.test(workflow));

for(const secret of [
  'QG_FIREBASE_GOOGLE_SERVICES_JSON_B64',
  'QG_ANDROID_KEYSTORE_B64',
  'QG_ANDROID_STORE_PASSWORD',
  'QG_ANDROID_KEY_ALIAS',
  'QG_ANDROID_KEY_PASSWORD'
]) ok('Native release requires '+secret,workflow.includes(secret));

for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])
  ok('Firebase release config requires '+id,workflow.includes(id));

for(const role of ['customer','merchant','rider']){
  ok(role+' release APK task',workflow.includes(`:${role}:assembleRelease`));
  ok(role+' release AAB task',workflow.includes(`:${role}:bundleRelease`));
  const gradle=read(`native-android/${role}/build.gradle.kts`);
  ok(role+' workflow versionCode property',/QG_VERSION_CODE/.test(gradle)&&/qgVersionCode/.test(gradle));
  ok(role+' workflow versionName property',/QG_VERSION_NAME/.test(gradle)&&/qgVersionName/.test(gradle));
  ok(role+' conditional release signing',/signingConfigs/.test(gradle)&&/QG_STORE_PASSWORD/.test(gradle)&&/QG_KEY_ALIAS/.test(gradle)&&/QG_KEY_PASSWORD/.test(gradle));
  ok(role+' release tasks fail closed without release properties',/qgReleaseRequested/.test(gradle)&&/requires QG_VERSION_CODE/.test(gradle)&&/requires QG_VERSION_NAME/.test(gradle)&&/requires QG_STORE_FILE/.test(gradle)&&/requires QG_STORE_PASSWORD/.test(gradle)&&/requires QG_KEY_ALIAS/.test(gradle)&&/requires QG_KEY_PASSWORD/.test(gradle));
  ok(role+' release tasks require Firebase config',/release build requires role-specific google-services\.json/.test(gradle));
  ok(role+' internal compile-only bypass is explicit',/QG_INTERNAL_RELEASE_COMPILE_ONLY/.test(gradle)&&/qgInternalReleaseCompileOnly/.test(gradle)&&/qgReleaseRequested && !qgInternalReleaseCompileOnly/.test(gradle));
  ok(role+' release non-debuggable',/isDebuggable = false/.test(gradle));

  const androidManifest=read(`native-android/${role}/src/main/AndroidManifest.xml`);
  ok(role+' RECORD_AUDIO declared for in-app voice',androidManifest.includes('android.permission.RECORD_AUDIO'));
  ok(role+' POST_NOTIFICATIONS declared',androidManifest.includes('android.permission.POST_NOTIFICATIONS'));
  ok(role+' no CAMERA permission',!androidManifest.includes('android.permission.CAMERA'));
  ok(role+' no background location permission',!androidManifest.includes('android.permission.ACCESS_BACKGROUND_LOCATION'));
  ok(role+' no broad storage permission',!/READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|READ_MEDIA_IMAGES/.test(androidManifest));
}

ok('Native release versionCode uses monotonic floor',/version_code=\$\(\(1000000 \+ GITHUB_RUN_NUMBER\)\)/.test(workflow)&&/2100000000/.test(workflow)&&/ORG_GRADLE_PROJECT_QG_VERSION_CODE/.test(workflow));
ok('Native release versionName is deterministic beta',/version_name="1\.0\.0-beta\.\$GITHUB_RUN_NUMBER"/.test(workflow)&&/ORG_GRADLE_PROJECT_QG_VERSION_NAME/.test(workflow));
ok('Native release verifies package/version',/aapt/.test(workflow)&&/versionCode/.test(workflow)&&/versionName/.test(workflow));
ok('Native release rejects debuggable APK',/release APK is debuggable/.test(workflow));
ok('Native release verifies APK signature',/apksigner/.test(workflow)&&/--print-certs/.test(workflow));
ok('Native release verifies AAB signature',/jarsigner -verify -strict/.test(workflow));
ok('Native release records source provenance',/git_sha=/.test(workflow)&&/github_run_id=/.test(workflow)&&/signer_certificate_sha256=/.test(workflow)&&/apk_sha256=/.test(workflow)&&/aab_sha256=/.test(workflow));
ok('Native release uploads only gated artifacts',/needs: validate/.test(workflow)&&/Upload gated Native Closed Beta artifacts/.test(workflow));
ok('Pilot internally compiles Native release variants',
  /Compile three native release variants without publishing/.test(pilotWorkflow)&&
  [':customer:assembleRelease',':customer:bundleRelease',':merchant:assembleRelease',':merchant:bundleRelease',':rider:assembleRelease',':rider:bundleRelease'].every(task=>pilotWorkflow.includes(task)));
ok('Pilot compile-only mode is explicit and non-publishing',/ORG_GRADLE_PROJECT_QG_INTERNAL_RELEASE_COMPILE_ONLY/.test(pilotWorkflow)&&!workflow.includes('QG_INTERNAL_RELEASE_COMPILE_ONLY')&&!pilotWorkflow.includes('queuego-native-release-internal'));

ok('Recovery manifest points to Native source',manifest?.android?.native_source_of_truth==='native-android/');
ok('Recovery manifest points to Native release workflow',manifest?.android?.native_closed_beta_workflow==='.github/workflows/build-native-closed-beta.yml');
ok('Default branch hosts the manual Native release dispatcher',manifest?.android?.native_closed_beta_workflow_dispatch_host_branch==='main'&&manifest?.android?.native_closed_beta_checkout_ref==='queuego-native-android-v1');
ok('Artifact provenance comes from checked-out Native SHA',manifest?.android?.release_provenance_git_sha_source==='CHECKED_OUT_NATIVE_BRANCH_HEAD');
ok('Recovery manifest locks Native versionCode floor',manifest?.android?.release_version_code_strategy==='1000000_PLUS_NATIVE_CLOSED_BETA_WORKFLOW_RUN_NUMBER'&&manifest?.android?.release_version_code_floor===1000000);
ok('Legacy Capacitor release is historical only',/HISTORICAL_ONLY/.test(manifest?.android?.legacy_capacitor_release_policy||''));
ok('legacy Capacitor workflow is hard-blocked',/Block historical Capacitor Android release/.test(legacyWorkflow)&&/Historical Capacitor\/WebView Android release is disabled/.test(legacyWorkflow)&&/exit 1/.test(legacyWorkflow));
ok('Voice code gate PASS',manifest?.voice?.code_gate==='PASS');
ok('TURN physical gate remains false until certified',manifest?.voice?.turn_relay_certified===false);
ok('two-device voice gate remains false until certified',manifest?.voice?.two_device_audio_e2e_certified===false);
ok('Voice does not persist audio',manifest?.voice?.media_recorded_or_persisted===false);
ok('Cloudflare TURN is retained as service provider',(manifest?.play?.service_provider_exceptions||[]).includes('Cloudflare Realtime TURN'));
ok('Cloudflare TURN media remains encrypted',manifest?.play?.cloudflare_turn_media_visibility==='ENCRYPTED_MEDIA_NOT_DECRYPTABLE_BY_TURN_RELAY');
ok('Play readiness allows user-initiated RECORD_AUDIO',/RECORD_AUDIO[^\n]*user-initiated|RECORD_AUDIO[^\n]*QueueGo in-app voice/i.test(readiness));
ok('Play readiness no longer describes active Android release as WebView',!/data transmitted off-device from an app-controlled WebView/i.test(readiness)&&!/web application running inside Capacitor/i.test(readiness));
ok('Play readiness uses Native Firebase dependency evidence',/Native Customer\/Merchant\/Rider use Firebase Messaging only/i.test(readiness)&&!/android-build\/package\.json[^\n]*Capacitor Push Notifications/i.test(readiness));
ok('Play readiness still forbids background location',/ACCESS_BACKGROUND_LOCATION/.test(readiness));
ok('Play Data Safety includes real-time voice audio',/Voice or sound recordings/.test(readiness)&&/Real-time microphone audio/.test(readiness));
ok('Play readiness documents Cloudflare TURN processor',/Cloudflare Realtime TURN/.test(readiness)&&/Cloudflare DPA/.test(readiness)&&/cannot decrypt the media/i.test(readiness));
ok('Privacy policy discloses QueueGo calls',/การโทรผ่าน QueueGo/.test(privacy));
ok('Privacy policy states calls are not recorded',/ไม่บันทึกสายโทร/.test(privacy)&&/ไม่สร้างไฟล์เสียง/.test(privacy));
ok('Privacy policy discloses TURN relay',/TURN relay/.test(privacy));

console.log(JSON.stringify({checks:checks.length,failures:0,scope:'Native Closed Beta workflow, signing, permissions, voice/push hard gates, Play/privacy alignment'},null,2));
