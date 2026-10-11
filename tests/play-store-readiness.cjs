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
const nativeVoicePeer=read('native-android/shared/src/main/java/com/queuego/shared/NativeVoicePeer.kt');
const nativeTurnEdge=read('supabase/functions/queuego-turn/index.ts');
const nativeReleaseGate=read('native-android/qa/verify-native-release-gate.py');
const productionPush=read('supabase/functions/queuego-push/index.ts');
const sharedManifest=read('native-android/shared/src/main/AndroidManifest.xml');
const riderManifest=read('native-android/rider/src/main/AndroidManifest.xml');
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
ok(releaseSecrets.includes("--proto '=https'")&&releaseSecrets.includes('--tlsv1.2'),'Release evidence Storage preflight must require HTTPS/TLS');
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
ok(/Play Console foreground-service declaration draft/i.test(readiness),'Play readiness must retain an explicit foreground-service declaration draft');
ok(/Android developer verification \/ package registration/i.test(readiness),'Play readiness must cover the active Android developer verification requirement');
for(const check of ['developer_identity_verified','customer_package_registered','merchant_package_registered','rider_package_registered']){
  ok(nativeReleaseGate.includes('"'+check+'"'),'Play preflight gate must require '+check);
}
for(const id of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider']){
  ok(readiness.includes(id),'Android developer verification guidance must name package '+id);
}
ok(nativeReleaseGate.includes('registered_packages')&&nativeReleaseGate.includes('expected_play_packages'),'Play gate must structurally bind registration evidence to exact package names');
ok(/Customer \/ Merchant \/ Rider — `microphone`/i.test(readiness),'All three Native apps must have a microphone FGS declaration draft');
ok(/Rider only — `specialUse`/i.test(readiness)&&/Active QueueGo Rider navigation return control/i.test(readiness),'Rider specialUse declaration must retain the reviewed subtype and use case');
ok(/Demo video evidence required/i.test(readiness),'Foreground-service declaration must require real-device demo video evidence');
ok(/Play Console declaration status remains \*\*BLOCKED\*\*/i.test(readiness),'FGS source readiness must not certify the external Play Console gate');
ok((readiness.match(/\| Voice or sound recordings \|/g)||[]).length===3,'Customer, Merchant and Rider Data Safety tables must each review Native voice media');
ok(/Google Play E2EE exception remains satisfied/i.test(readiness),'Native voice media must retain the Google Play E2EE collection-exception boundary');
ok(/Cloudflare Realtime TURN[\s\S]*cannot inspect media content/i.test(readiness),'Play readiness must document the Cloudflare TURN encrypted-media boundary');
ok(/re-review if any intermediary can read media, or if recording\/transcription\/SFU\/server media processing is introduced/i.test(readiness),'Voice Data Safety exception must fail closed on media architecture changes');
ok(manifest?.play?.voice_audio_data_safety==='E2EE_EXCEPTION_WHILE_PEER_ONLY_WEBRTC','Recovery manifest must preserve the reviewed voice-media E2EE exception');
ok(Array.isArray(manifest?.play?.voice_audio_roles)&&manifest.play.voice_audio_roles.length===3,'Voice Data Safety review must cover all three Native roles');
ok(manifest?.play?.voice_audio_reassessment_required_on_architecture_change===true,'Voice Data Safety must require reassessment on architecture changes');
ok(manifest?.play?.voice_audio_media_readable_by_turn_provider===false,'Recovery manifest must preserve the reviewed encrypted TURN media boundary');
ok(manifest?.play?.cloudflare_turn_relay_metadata_review==='PENDING_FINAL_PLAY_CONSOLE_METADATA_CLASSIFICATION','Cloudflare TURN relay metadata must remain pending final Play classification');
ok(manifest?.play?.foreground_service_declaration_draft_ready===true&&manifest?.play?.foreground_service_console_status==='BLOCKED_PENDING_REAL_DEVICE_VIDEO_AND_PLAY_ENTRY','FGS declaration source readiness must stay separate from external Play completion');
ok(nativeVoicePeer.includes('PeerConnection.RTCConfiguration')&&nativeVoicePeer.includes('createAudioTrack("queuego-audio"'),'Data Safety E2EE exception must remain tied to the Native peer-to-peer WebRTC audio path');
ok(nativeTurnEdge.includes('rtc.live.cloudflare.com/v1/turn/')&&nativeTurnEdge.includes('generate-ice-servers'),'Native voice relay must remain the reviewed Cloudflare TURN credential path');
ok(manifest?.android?.turn_edge_production_deployed===true&&manifest?.android?.turn_edge_production_status==='ACTIVE'&&manifest?.android?.turn_edge_production_version===2,'Recovery manifest must retain the verified Production queuego-turn deployment');
ok(manifest?.android?.turn_edge_verify_jwt===true&&manifest?.android?.turn_edge_source_exact_match===true,'Production queuego-turn must stay JWT-protected and source-identical to GitHub');
ok(manifest?.android?.turn_edge_bundle_sha256==='747116e17f5c4a2feb2852851e1e346b5a8e456c5a6b521682ab1a5f02ed894c','Recovery manifest must pin the observed Production TURN Edge bundle SHA-256');
ok(manifest?.android?.turn_runtime_secret_status==='UNVERIFIED_NO_PRODUCTION_REQUESTS_OBSERVED','TURN secret readiness must not be inferred without Production request evidence');
ok(manifest?.android?.turn_forced_relay_two_device_gate==='OPEN','Real two-device forced TURN relay gate must remain open until physical evidence exists');
ok(manifest?.notifications?.edge_function_version===16&&manifest?.notifications?.edge_function_status==='ACTIVE'&&manifest?.notifications?.edge_source_matches_github===true,'Recovery manifest must retain the verified Production queuego-push v16 deployment');
ok(manifest?.notifications?.edge_function_bundle_sha256==='e1ae93f6cc142a77fa7408b894703ed9ac8ad7f9fdaa9b32d40cee2a28758079','Recovery manifest must pin the observed Production push Edge bundle SHA-256');
ok(manifest?.notifications?.edge_function_verify_jwt===false&&manifest?.notifications?.edge_custom_auth_mode==='WORKER_TOKEN_OR_AUTHENTICATED_ACTIVE_USER','verify_jwt=false must remain paired with the reviewed custom push authentication boundary');
ok(manifest?.notifications?.edge_dispatch_requires_worker_token===true&&manifest?.notifications?.edge_user_actions_verify_auth_user===true&&manifest?.notifications?.edge_native_registration_requires_active_session_binding===true,'Production push custom auth must retain worker/user/session checks');
ok(manifest?.notifications?.firebase_edge_runtime_status==='UNVERIFIED_NO_NATIVE_TOKEN_OR_SUCCESSFUL_FCM_SEND_EVIDENCE','Production push deployment evidence must not certify Firebase runtime without a real Native token/send');
ok(manifest?.notifications?.physical_background_notification_certified===false,'Physical background push certification must remain open until observed on real Android');
ok(productionPush.includes("req.headers.get('x-queuego-worker')!==c.worker_token"),'queuego-push dispatch must retain worker-token authentication');
ok(productionPush.includes('admin.auth.getUser(token)')&&productionPush.includes("user.status!=='active'"),'queuego-push user actions must retain authenticated active-user enforcement');
ok(productionPush.includes("admin.from('user_active_sessions')")&&productionPush.includes(".eq('session_id',sessionId)"),'Native push registration must retain active-session binding');
ok(!/MediaRecorder|FileOutputStream|recordToFile|transcription|Realtime SFU|\/sfu\//i.test(nativeVoicePeer+'\n'+nativeTurnEdge),'Voice Data Safety E2EE exception must fail if media recording, transcription or SFU/server media processing appears in the certified source');
ok(sharedManifest.includes('android:foregroundServiceType="microphone"')&&sharedManifest.includes('android.permission.FOREGROUND_SERVICE_MICROPHONE'),'Shared Native voice FGS manifest contract must remain microphone-scoped');
ok(riderManifest.includes('android:foregroundServiceType="specialUse"')&&riderManifest.includes('Active QueueGo Rider navigation return control'),'Rider specialUse FGS declaration must remain tied to its reviewed subtype');
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
ok(Array.isArray(manifest?.android?.release_certification_bucket_allowed_mime_types)&&manifest.android.release_certification_bucket_allowed_mime_types.includes('application/zip'),'Release evidence bucket must remain ZIP-only');
ok(manifest?.android?.release_certification_bucket_client_policies_present===false,'Release evidence bucket must remain server-side only with no client Storage policy');
ok(manifest?.android?.release_certification_runtime_gate==='BLOCKED_SECRETS_AND_PHYSICAL_EVIDENCE','Certification runtime must remain blocked only on external credentials and real physical evidence after bucket provisioning');
ok(manifest?.android?.release_gate_evidence_envelope_schema===true&&manifest?.android?.release_gate_evidence_envelope_version===1,'Release evidence must use the structured gate envelope schema');
ok(manifest?.android?.release_gate_physical_device_id_hash_required===true&&manifest?.android?.release_gate_physical_role_binding===true,'Physical evidence must use hashed device identity and role binding');
ok(manifest?.android?.physical_evidence_capture_helper==='native-android/qa/capture-physical-release-evidence.py','Recovery manifest must point to the supported physical evidence helper');
ok(manifest?.android?.physical_evidence_capture_default_status==='DRAFT'&&manifest?.android?.physical_evidence_capture_auto_certifies===false,'Physical evidence helper must never auto-certify release gates');
ok(manifest?.android?.physical_evidence_capture_rejects_emulator===true&&manifest?.android?.physical_evidence_capture_external_output_required===true,'Physical evidence helper must reject emulator capture and keep evidence outside source');
ok(manifest?.android?.physical_evidence_capture_identity_hash_salt==='PER_CAPTURE_RANDOM_256_BIT_NOT_STORED'&&manifest?.android?.physical_evidence_capture_identity_hash_domain_separated===true,'Physical evidence identity hashes must remain per-capture salted and domain-separated');
ok(manifest?.android?.release_evidence_bundler==='native-android/qa/finalize-native-release-evidence.py','Recovery manifest must point to the supported release evidence bundler');
ok(manifest?.android?.release_evidence_bundler_requires_p0_p1_zero===true&&manifest?.android?.release_evidence_bundler_auto_certifies===false,'Evidence bundler must never bypass P0/P1 or auto-certify gates');
ok(manifest?.android?.release_evidence_bundler_max_files===1000&&manifest?.android?.release_evidence_bundler_max_uncompressed_bytes===4294967296,'Evidence bundler limits must match certification extraction bounds');
ok(manifest?.android?.release_evidence_bundler_output_status==='READY_FOR_PRIVATE_STORAGE_UPLOAD','Bundler output must remain upload-ready rather than release-certified');
ok(manifest?.android?.release_gate_lifecycle_requires_all_roles===true,'Lifecycle certification must cover Customer Merchant and Rider');
ok(manifest?.android?.release_gate_voice_requires_two_distinct_devices===true&&manifest?.android?.release_gate_voice_requires_two_distinct_networks===true,'Voice certification must require two devices and two networks');
ok(manifest?.android?.release_gate_full_native_ci_attested_run_binding===true,'full_native_ci evidence must bind the attested Native Pilot run');
ok(manifest?.android?.release_gate_production_supabase_project_ref==='pkypiqhlrmzocysgeqew','Production backend evidence must pin the QueueGo Production project');
ok(manifest?.android?.release_gate_firebase_project_binding===true,'Firebase evidence must bind the loaded Production Firebase project');
ok(manifest?.android?.release_gate_play_version_metadata_binding===true,'Play preflight evidence must bind release version metadata and observed history');
ok(manifest?.android?.release_gate_signing_certificate_binding===true,'Release signing evidence must bind the certified certificate fingerprint');
ok(manifest?.android?.release_certification_requires_exact_head_backup_restore===true,'Release certification must require exact-HEAD Backup Restore');
ok(manifest?.android?.release_certification_backup_restore_workflow==='.github/workflows/backup-restore-drill.yml','Recovery manifest must pin the Backup Restore workflow identity');
ok(manifest?.android?.release_certification_backup_artifact_live_required===true&&manifest?.android?.release_certification_backup_artifact_prefix==='queuego-backup-','Certification must require a live encrypted backup artifact');
ok(manifest?.android?.release_certification_final_release_revalidates_backup_restore_run===true,'Final release must revalidate the Backup Restore run recorded by certification');
ok(manifest?.android?.release_gate_backup_restore_attested_run_binding===true,'backup_restore gate evidence must bind the attested restore-drill run');
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
