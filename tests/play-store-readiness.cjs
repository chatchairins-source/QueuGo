const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
for(const p of [
  'docs/play-closed-beta-readiness.md',
  'docs/play-store-listing-draft.md',
  'docs/play-app-access-content-rating.md',
  'docs/privacy.html',
  'docs/account-deletion.html',
  'docs/community-guidelines.html',
  '.github/workflows/release-secret-readiness.yml'
])ok(fs.existsSync(p),'Play readiness source missing: '+p);
const listing=read('docs/play-store-listing-draft.md');
for(const app of ['QueueGo','QueueGo Merchant','QueueGo Rider'])ok(listing.includes(app),'Store listing must cover '+app);
const access=read('docs/play-app-access-content-rating.md');
const readiness=read('docs/play-closed-beta-readiness.md');
const manifest=JSON.parse(read('docs/pilot-recovery-manifest.json'));
const releaseSecrets=read('.github/workflows/release-secret-readiness.yml');
const releaseCertification=read('.github/workflows/native-release-certification.yml');
const nativeRootGradle=read('native-android/build.gradle.kts');
for(const role of ['customer','merchant','rider']){
  const appGradle=read(`native-android/${role}/build.gradle.kts`);
  ok(/compileSdk\s*=\s*36/.test(appGradle),role+' must compile against API 36');
  ok(/targetSdk\s*=\s*36/.test(appGradle),role+' must target API 36 for current Google Play submissions');
}
ok(/com\.android\.application"\) version "9\.4\.0"/.test(nativeRootGradle),'Native Android must retain an AGP release with 16 KB packaging support');
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])ok(access.includes(id),'App Access draft must cover '+id);
ok(/workflow_dispatch/.test(releaseSecrets),'Release secret preflight must be manually runnable after owner configures secrets');
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider'])ok(releaseSecrets.includes(id),'Firebase preflight must require Android client '+id);
ok(releaseSecrets.includes('base64 --decode > /tmp/google-services.json'),'Release preflight must decode Firebase config without committing it');
ok(releaseSecrets.includes("mobilesdk_app_id"),'Firebase preflight must reject incomplete Android clients without mobilesdk_app_id');
ok(releaseSecrets.includes('len(matches) != 1'),'Firebase preflight must require exactly one client per QueueGo package');
ok(releaseSecrets.includes("project_id"),'Firebase preflight must require explicit Firebase project identity');
ok(/keytool[\s\S]{0,180}-list -v/.test(releaseSecrets)&&releaseSecrets.includes('keytool -importkeystore'),'Release preflight must validate keystore, alias and key password');
ok(releaseSecrets.includes('-storepass:env QG_ANDROID_STORE_PASSWORD')&&releaseSecrets.includes('-srckeypass:env QG_ANDROID_KEY_PASSWORD'),'Signing passwords must stay out of keytool argv');
ok(releaseSecrets.includes('Android signing certificate SHA-256: $fingerprint'),'Release preflight must expose only the signing certificate fingerprint for identity pinning');
ok(releaseSecrets.includes('Clean temporary credentials'),'Release preflight must remove decoded credentials from the runner');
ok(releaseSecrets.includes('vars.QG_SUPABASE_URL')&&releaseSecrets.includes('QG_SUPABASE_SERVICE_ROLE_KEY'),'Release preflight must require the private evidence Storage origin and server-side credential');
ok(releaseSecrets.includes('/storage/v1/object/list/queuego-native-release-evidence'),'Release preflight must verify private evidence bucket access');
ok(releaseSecrets.includes('https://pkypiqhlrmzocysgeqew.supabase.co'),'Release preflight must pin the QueueGo Production Supabase origin');
ok(releaseSecrets.includes('/storage/v1/bucket/queuego-native-release-evidence')&&releaseSecrets.includes('Release evidence bucket must remain private'),'Release preflight must verify the fixed evidence bucket exists and remains private');
ok(releaseCertification.includes('/storage/v1/object/authenticated/')&&releaseCertification.includes('queuego-native-release-evidence'),'Release certification must download evidence only from the fixed private Storage bucket');
ok(releaseCertification.includes('sha256sum -c -')&&releaseCertification.includes('Evidence ZIP contains path traversal'),'Release certification must verify bundle integrity and reject ZIP traversal');
ok(releaseCertification.includes('certified-release-metadata.json'),'Release certification must bind exact release metadata');
ok(/dedicated Play-review accounts/i.test(access),'Reviewer access must use dedicated reusable accounts');
ok(/Do not use an actual customer/i.test(access),'Reviewer access must forbid real-user credentials');
ok(access.includes('native-android/customer/src/main/java/com/queuego/customer/CustomerChat.kt'),'Play reviewer evidence must include Native Customer chat safety');
ok(access.includes('native-android/rider/src/main/java/com/queuego/rider/RiderChat.kt'),'Play reviewer evidence must include Native Rider chat safety');
ok(/actual current Native Android app/i.test(listing)&&!/Screenshots must be from the actual current app, not mock UI/.test(listing),'Store screenshot guidance must target Native Android, not legacy build wording');
ok(/Contains ads.*RESOLVED FOR CURRENT BUILD/is.test(access),'Contains Ads decision must remain resolved for current build');
ok(/QueueGo Customer[\s\S]*Yes — Contains ads/i.test(access),'Customer app must declare Contains ads = Yes');
ok(/QueueGo Merchant[\s\S]*No\./i.test(access),'Merchant app must declare Contains ads = No');
ok(/QueueGo Rider[\s\S]*No\./i.test(access),'Rider app must declare Contains ads = No');
ok(manifest?.play?.contains_ads?.customer==='YES'&&manifest?.play?.contains_ads?.merchant==='NO'&&manifest?.play?.contains_ads?.rider==='NO','Manifest must lock per-app Contains Ads answers');
ok(/Target Audience decision.*CLOSED BETA/is.test(access),'Target Audience decision must remain explicit for Closed Beta');
ok(/QueueGo Customer[\s\S]*Ages 18 and over only/i.test(access)&&/QueueGo Merchant[\s\S]*Ages 18 and over only/i.test(access)&&/QueueGo Rider[\s\S]*Ages 18 and over only/i.test(access),'All three Play apps must remain 18+ only for Closed Beta');
ok(manifest?.play?.target_audience?.customer==='18_PLUS_ONLY'&&manifest?.play?.target_audience?.merchant==='18_PLUS_ONLY'&&manifest?.play?.target_audience?.rider==='18_PLUS_ONLY','Target Audience must remain 18+ only for all apps');
ok(manifest?.play?.restrict_minor_access==='ENABLE_FOR_CLOSED_BETA','Restrict Minor Access must remain enabled for Closed Beta');
ok(/Longdo[\s\S]*Shared\s*=\s*Yes for location/i.test(readiness),'Data Safety must conservatively declare Longdo location sharing');
ok(/Data sharing:\s*\*\*Yes \(conservative\)\*\*/i.test(readiness),'Play readiness must keep conservative data-sharing answer');

ok(/Native Android Customer, Merchant, Rider apps/i.test(readiness),'Play readiness must identify Native apps as the certification scope');
ok(/RECORD_AUDIO for optional order-scoped audio calls/i.test(readiness),'Native voice microphone disclosure must remain explicit');
ok(/FOREGROUND_SERVICE_MICROPHONE/i.test(readiness),'Native microphone foreground-service permission must remain documented');
ok(/Incoming FCM does not start microphone access in the background/i.test(readiness),'Play readiness must preserve visible-user-action microphone start boundary');
ok(!/Explicitly forbidden by the release workflow:[\s\S]{0,300}RECORD_AUDIO/i.test(readiness),'Legacy Capacitor RECORD_AUDIO prohibition must not return');
ok(/Native CI rejects WebView/i.test(readiness),'Data Safety draft must use Native no-WebView certification scope');
ok(!/data collected by the web application running inside Capacitor/i.test(readiness),'Legacy Capacitor Data Safety collection rule must not certify Native apps');
ok(!/android-build\/package\.json[^\n]*Capacitor Push Notifications/i.test(readiness),'Legacy Capacitor push inventory must not remain Native Play evidence');

ok(manifest?.play?.provider_sharing_classification==='RESOLVED_CONSERVATIVE_LONGDO_LOCATION_SHARED','Provider sharing classification must not regress to pending');
ok(manifest?.play?.data_safety_sharing==='YES_LONGDO_LOCATION_APP_FUNCTIONALITY','Manifest must lock Longdo location sharing for app functionality');
ok(Array.isArray(manifest?.play?.service_provider_exceptions)&&manifest.play.service_provider_exceptions.includes('Supabase')&&manifest.play.service_provider_exceptions.includes('Firebase Cloud Messaging / Google'),'Manifest must retain Supabase/Firebase service-provider treatment');
ok(manifest?.android?.platform==='NATIVE_ANDROID','Recovery manifest Android platform must be Native Android');
ok(manifest?.android?.native_ci_rejects_webview===true,'Recovery manifest must preserve Native no-WebView gate');
ok(manifest?.android?.legacy_capacitor_build_workflows_retired===true,'Legacy Capacitor build workflows must remain retired');
ok(Array.isArray(manifest?.android?.allowed_android_build_workflows)&&
  manifest.android.allowed_android_build_workflows.length===2&&
  manifest.android.allowed_android_build_workflows.includes('.github/workflows/build-native-rider-pilot.yml')&&
  manifest.android.allowed_android_build_workflows.includes('.github/workflows/build-native-release.yml'),
  'Only the Native pilot and fail-closed certified release workflows may be active Android build workflows');
ok(manifest?.android?.release_version_code_strategy==='EXPLICIT_PER_APP_GT_OBSERVED_PLAY_MAX','Recovery manifest must preserve Play-history versionCode strategy');
ok(manifest?.android?.release_gate_requires_backup_restore===true,'Recovery manifest must preserve Backup/Restore as a Native release hard gate');
ok(manifest?.android?.target_api_level===36,'Recovery manifest must record Native target API 36');
ok(manifest?.android?.page_size_16kb_gate===true,'Recovery manifest must preserve the Native 16 KB page-size gate');
ok(manifest?.android?.release_bundletool_required===true&&manifest?.android?.release_bundletool_version==='1.18.3','Recovery manifest must require pinned bundletool AAB verification');
ok(manifest?.android?.release_bundletool_sha256==='a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29','Recovery manifest must pin the certified bundletool SHA-256');
ok(manifest?.android?.release_artifact_evidence_external_to_source===true,'Release artifact evidence must remain outside the source checkout');
ok(manifest?.android?.release_certification_workflow==='.github/workflows/native-release-certification.yml','Recovery manifest must identify the trusted Native certification producer');
ok(manifest?.android?.release_certification_private_bucket==='queuego-native-release-evidence','Recovery manifest must pin the private evidence bucket');
ok(manifest?.android?.release_certification_private_bucket_present===true&&manifest?.android?.release_certification_bucket_observed_in_production===true,'Recovery manifest must record the observed Production evidence bucket');
ok(manifest?.android?.release_certification_bucket_public===false,'Release evidence bucket must remain private');
ok(Array.isArray(manifest?.android?.release_certification_bucket_allowed_mime_types)&&manifest.android.release_certification_bucket_allowed_mime_types.includes('application/zip'),'Release evidence bucket must retain ZIP-only MIME restrictions');
ok(manifest?.android?.release_certification_runtime_gate==='BLOCKED_SECRETS_AND_PHYSICAL_EVIDENCE','Recovery manifest must keep certification runtime blocked only on remaining credentials and physical evidence');
ok(manifest?.android?.release_certification_requires_exact_head_native_pilot_ci===true,'Release certification must require exact-HEAD Native Pilot CI');
ok(manifest?.android?.release_certification_native_pilot_workflow==='.github/workflows/build-native-rider-pilot.yml','Recovery manifest must pin the Native Pilot workflow identity for certification');
ok(manifest?.android?.release_certification_final_release_revalidates_native_pilot_run===true,'Final release must revalidate the Native Pilot run recorded by certification');
ok(manifest?.android?.native_scope==='Customer/Merchant/Rider','Recovery manifest must identify Native Android as the active release scope');
ok(manifest?.android?.legacy_capacitor_release_workflows_retired===true,'Legacy Capacitor release workflows must remain retired');
ok(!('capacitor_version' in (manifest.android||{}))&&!('push_plugin' in (manifest.android||{})),'Native recovery manifest must not treat Capacitor as the active Android runtime');
ok(Array.isArray(manifest?.android?.allowed_android_build_workflows)&&
  manifest.android.allowed_android_build_workflows.length===2&&
  manifest.android.allowed_android_build_workflows.includes('.github/workflows/build-native-rider-pilot.yml')&&
  manifest.android.allowed_android_build_workflows.includes('.github/workflows/build-native-release.yml'),
  'Only the Native pilot and fail-closed certified release workflows may be active Android build workflows');
console.log(JSON.stringify({checks,failures:0,scope:'Play listing, app access, Data Safety, Contains Ads, 18+ Target Audience, privacy/account deletion and content-rating preparation'}));
