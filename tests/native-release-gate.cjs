const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const script='native-android/qa/verify-native-release-gate.py';
const env={...process.env};
delete env.QG_NATIVE_RELEASE_EVIDENCE;
const absent=spawnSync('python3',[script],{env,encoding:'utf8'});
assert.equal(absent.status,1,'release must fail without physical evidence');
assert.match(absent.stderr,/physical and Play gates remain OPEN/);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-release-negative-'));
try{
  const stale=path.join(dir,'stale.json');
  fs.writeFileSync(stale,JSON.stringify({source_sha:'invalid'}));
  const result=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:stale},encoding:'utf8'});
  assert.equal(result.status,1,'stale certification must fail');
  assert.match(result.stderr,/exact release HEAD/);
}finally{fs.rmSync(dir,{recursive:true,force:true});}

const evidenceDir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-release-envelope-'));
try{
  const head=spawnSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).stdout.trim();
  const artifact='artifact.txt';
  fs.writeFileSync(path.join(evidenceDir,artifact),'observed evidence');
  const artifactSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(evidenceDir,artifact))).digest('hex');
  const observedAt='2026-10-10T00:00:00Z';

  const badEnvelope={
    gate:'wrong_gate',
    source_sha:head,
    status:'PASS',
    observed_at:observedAt,
    checks:{observed:true},
    artifacts:[{file:artifact,sha256:artifactSha,kind:'log'}]
  };
  const badEnvelopePath=path.join(evidenceDir,'full_native_ci.json');
  fs.writeFileSync(badEnvelopePath,JSON.stringify(badEnvelope));
  const badEnvelopeSha=crypto.createHash('sha256').update(fs.readFileSync(badEnvelopePath)).digest('hex');
  const badReport=path.join(evidenceDir,'bad-report.json');
  fs.writeFileSync(badReport,JSON.stringify({
    source_sha:head,p0:0,p1:0,
    gates:{full_native_ci:{status:'PASS',evidence_file:'full_native_ci.json',sha256:badEnvelopeSha}}
  }));
  const badResult=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:badReport},encoding:'utf8'});
  assert.equal(badResult.status,1,'mismatched gate envelope must fail');
  assert.match(badResult.stderr,/gate evidence envelope mismatch: full_native_ci/);

  const preceding=[
    'full_native_ci','production_backend','backup_restore','security_regression',
    'service_area','ugc_chat_safety','security_platform_auth','firebase_three_packages'
  ];
  const gates={};
  for(const gate of preceding){
    const envelope={
      gate,source_sha:head,status:'PASS',observed_at:observedAt,
      checks:{observed:true},
      artifacts:[{file:artifact,sha256:artifactSha,kind:'log'}]
    };
    const file=`${gate}.json`;
    fs.writeFileSync(path.join(evidenceDir,file),JSON.stringify(envelope));
    const sha=crypto.createHash('sha256').update(fs.readFileSync(path.join(evidenceDir,file))).digest('hex');
    gates[gate]={status:'PASS',evidence_file:file,sha256:sha};
  }
  const physicalEnvelope={
    gate:'physical_push_customer',source_sha:head,status:'PASS',observed_at:observedAt,
    operator_certified:true,
    checks:{
      foreground:true,background:true,killed:true,refresh_login:true,
      logout_revocation:true,stale_token_exclusion:true,
      order_notification:true,call_notification:true
    },
    artifacts:[{file:artifact,sha256:artifactSha,kind:'log'}]
  };
  fs.writeFileSync(path.join(evidenceDir,'physical_push_customer.json'),JSON.stringify(physicalEnvelope));
  const physicalSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(evidenceDir,'physical_push_customer.json'))).digest('hex');
  gates.physical_push_customer={status:'PASS',evidence_file:'physical_push_customer.json',sha256:physicalSha};
  const physicalReport=path.join(evidenceDir,'physical-report.json');
  fs.writeFileSync(physicalReport,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const physicalResult=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:physicalReport},encoding:'utf8'});
  assert.equal(physicalResult.status,1,'physical gate without device identity must fail');
  assert.match(physicalResult.stderr,/physical gate evidence must identify at least one device: physical_push_customer/);

  physicalEnvelope.devices=[{
    device_id_hash:'a'.repeat(64),
    android_api:36,
    model:'Test Android Device',
    role:'rider',
    physical:true,
    emulator:false
  }];
  fs.writeFileSync(path.join(evidenceDir,'physical_push_customer.json'),JSON.stringify(physicalEnvelope));
  const wrongRoleSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(evidenceDir,'physical_push_customer.json'))).digest('hex');
  gates.physical_push_customer={status:'PASS',evidence_file:'physical_push_customer.json',sha256:wrongRoleSha};
  fs.writeFileSync(physicalReport,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const wrongRoleResult=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:physicalReport},encoding:'utf8'});
  assert.equal(wrongRoleResult.status,1,'Customer push evidence with only a Rider device must fail');
  assert.match(wrongRoleResult.stderr,/physical gate evidence is missing required roles \(customer\): physical_push_customer/);

  physicalEnvelope.devices[0].role='customer';
  delete physicalEnvelope.checks.call_notification;
  fs.writeFileSync(path.join(evidenceDir,'physical_push_customer.json'),JSON.stringify(physicalEnvelope));
  const missingSemanticSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(evidenceDir,'physical_push_customer.json'))).digest('hex');
  gates.physical_push_customer={status:'PASS',evidence_file:'physical_push_customer.json',sha256:missingSemanticSha};
  fs.writeFileSync(physicalReport,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const missingSemanticResult=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:physicalReport},encoding:'utf8'});
  assert.equal(missingSemanticResult.status,1,'Customer push evidence must include every required observed check');
  assert.match(missingSemanticResult.stderr,/physical gate required check missing: physical_push_customer\.call_notification/);

  physicalEnvelope.checks.call_notification=true;
  physicalEnvelope.devices[0].emulator=true;
  fs.writeFileSync(path.join(evidenceDir,'physical_push_customer.json'),JSON.stringify(physicalEnvelope));
  const emulatorSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(evidenceDir,'physical_push_customer.json'))).digest('hex');
  gates.physical_push_customer={status:'PASS',evidence_file:'physical_push_customer.json',sha256:emulatorSha};
  fs.writeFileSync(physicalReport,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const emulatorResult=spawnSync('python3',[script],{env:{...env,QG_NATIVE_RELEASE_EVIDENCE:physicalReport},encoding:'utf8'});
  assert.equal(emulatorResult.status,1,'Emulator evidence must never certify a physical gate');
  assert.match(emulatorResult.stderr,/physical gate device must be a real non-emulator device: physical_push_customer/);
}finally{fs.rmSync(evidenceDir,{recursive:true,force:true});}

const bindingDir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-release-binding-'));
try{
  const head=spawnSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).stdout.trim();
  const artifact='artifact.txt';
  fs.writeFileSync(path.join(bindingDir,artifact),'binding evidence');
  const artifactSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(bindingDir,artifact))).digest('hex');
  const observedAt='2026-10-10T00:00:00Z';
  const gateNames=[
    'full_native_ci','production_backend','backup_restore','security_regression',
    'service_area','ugc_chat_safety','security_platform_auth','firebase_three_packages',
    'physical_push_customer','physical_push_merchant','physical_push_rider',
    'physical_voice_two_devices_two_networks','turn_relay','voice_session_order_block_authorization',
    'rider_floating_q','customer_blueprint','merchant_blueprint','rider_blueprint',
    'android_lifecycle_permissions_upload_location','privacy_data_safety_account_deletion',
    'play_store_preflight','release_signing'
  ];
  const physical=new Set([
    'physical_push_customer','physical_push_merchant','physical_push_rider',
    'physical_voice_two_devices_two_networks','turn_relay',
    'voice_session_order_block_authorization','rider_floating_q',
    'customer_blueprint','merchant_blueprint','rider_blueprint',
    'android_lifecycle_permissions_upload_location'
  ]);
  const pushChecks={
    foreground:true,background:true,killed:true,refresh_login:true,
    logout_revocation:true,stale_token_exclusion:true,
    order_notification:true,call_notification:true
  };
  const semanticChecks={
    physical_push_customer:pushChecks,
    physical_push_merchant:pushChecks,
    physical_push_rider:pushChecks,
    physical_voice_two_devices_two_networks:{
      two_devices:true,two_networks:true,bidirectional_audio:true,
      foreground_background:true,controls_complete:true,hangup_cleanup:true
    },
    turn_relay:{forced_turn_relay:true,relay_candidate_observed:true,relay_path_selected:true},
    voice_session_order_block_authorization:{
      real_order:true,session_authorization:true,order_authorization:true,
      block_authorization:true,session_revocation:true,
      block_revocation:true,private_topic_revocation:true
    },
    rider_floating_q:{
      overlay_granted_path:true,overlay_denied_path:true,
      tap_returns_to_active_job:true,persistent_notification_return:true,
      stops_outside_active_work:true
    },
    customer_blueprint:{all_required_screens_observed:true,all_required_states_observed:true,pixel_diff_reviewed:true},
    merchant_blueprint:{all_required_screens_observed:true,all_required_states_observed:true,pixel_diff_reviewed:true},
    rider_blueprint:{all_required_screens_observed:true,all_required_states_observed:true,pixel_diff_reviewed:true},
    android_lifecycle_permissions_upload_location:{
      customer_permissions:true,customer_single_back_to_home:true,customer_upload:true,customer_location:true,
      customer_session_persistence:true,customer_offline_timeout:true,customer_reconnect:true,customer_background_foreground:true,
      merchant_permissions:true,merchant_single_back_to_home:true,merchant_upload:true,merchant_location:true,
      merchant_session_persistence:true,merchant_offline_timeout:true,merchant_reconnect:true,merchant_background_foreground:true,
      rider_permissions:true,rider_single_back_to_home:true,rider_upload:true,rider_location:true,
      rider_session_persistence:true,rider_offline_timeout:true,rider_reconnect:true,rider_background_foreground:true
    },
    play_store_preflight:{
      developer_identity_verified:true,
      customer_package_registered:true,
      merchant_package_registered:true,
      rider_package_registered:true
    }
  };
  const gates={};
  for(const gate of gateNames){
    const envelope={
      gate,source_sha:head,status:'PASS',observed_at:observedAt,
      checks:semanticChecks[gate]||{observed:true},
      artifacts:[{file:artifact,sha256:artifactSha,kind:'log'}]
    };
    if(physical.has(gate)) envelope.operator_certified=true;
    if(['customer_blueprint','merchant_blueprint','rider_blueprint'].includes(gate)) envelope.blocking_differences=0;
    if(gate==='full_native_ci') envelope.github_run_id=456;
    if(gate==='backup_restore') envelope.github_run_id=456;
    if(gate==='production_backend') envelope.supabase_project_ref='pkypiqhlrmzocysgeqew';
    if(gate==='firebase_three_packages') envelope.firebase_project_id='placeholder-project';
    if(gate==='play_store_preflight'){
      envelope.version_name='1.0.0';
      envelope.release_version_codes={customer:1,merchant:1,rider:1};
      envelope.observed_play_max_version_codes={customer:0,merchant:0,rider:0};
      envelope.registered_packages=['com.queuego.customer','com.queuego.merchant','com.queuego.rider'];
    }
    if(gate==='release_signing') envelope.signing_certificate_sha256='b'.repeat(64);
    if(physical.has(gate)){
      envelope.devices=[
        {device_id_hash:'1'.repeat(64),android_api:36,model:'Customer Device',role:'customer',physical:true,emulator:false},
        {device_id_hash:'2'.repeat(64),android_api:36,model:'Merchant Device',role:'merchant',physical:true,emulator:false},
        {device_id_hash:'3'.repeat(64),android_api:36,model:'Rider Device',role:'rider',physical:true,emulator:false}
      ];
    }
    if(gate==='physical_voice_two_devices_two_networks'||gate==='turn_relay'){
      envelope.networks=[
        {network_id_hash:'4'.repeat(64),type:'wifi'},
        {network_id_hash:'5'.repeat(64),type:'mobile'}
      ];
    }
    const file=`${gate}.json`;
    fs.writeFileSync(path.join(bindingDir,file),JSON.stringify(envelope));
    const sha=crypto.createHash('sha256').update(fs.readFileSync(path.join(bindingDir,file))).digest('hex');
    gates[gate]={status:'PASS',evidence_file:file,sha256:sha};
  }
  const report=path.join(bindingDir,'native-release-evidence.json');
  fs.writeFileSync(report,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));

  const playEnvelopePath=path.join(bindingDir,'play_store_preflight.json');
  const playEnvelope=JSON.parse(fs.readFileSync(playEnvelopePath,'utf8'));
  delete playEnvelope.checks.rider_package_registered;
  fs.writeFileSync(playEnvelopePath,JSON.stringify(playEnvelope));
  gates.play_store_preflight={
    status:'PASS',
    evidence_file:'play_store_preflight.json',
    sha256:crypto.createHash('sha256').update(fs.readFileSync(playEnvelopePath)).digest('hex')
  };
  fs.writeFileSync(report,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const missingPlayRegistration=spawnSync('python3',[script],{
    env:{...env,QG_NATIVE_RELEASE_EVIDENCE:report,QG_CERTIFIED_NATIVE_PILOT_RUN_ID:'456',QG_CERTIFIED_BACKUP_RESTORE_RUN_ID:'456'},
    encoding:'utf8'
  });
  assert.equal(missingPlayRegistration.status,1,'Play preflight must fail if one QueueGo package registration is uncertified');
  assert.match(missingPlayRegistration.stderr,/gate required check missing: play_store_preflight\.rider_package_registered/);

  playEnvelope.checks.rider_package_registered=true;
  fs.writeFileSync(playEnvelopePath,JSON.stringify(playEnvelope));
  gates.play_store_preflight={
    status:'PASS',
    evidence_file:'play_store_preflight.json',
    sha256:crypto.createHash('sha256').update(fs.readFileSync(playEnvelopePath)).digest('hex')
  };
  fs.writeFileSync(report,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const result=spawnSync('python3',[script],{
    env:{...env,QG_NATIVE_RELEASE_EVIDENCE:report,QG_CERTIFIED_NATIVE_PILOT_RUN_ID:'123',QG_CERTIFIED_BACKUP_RESTORE_RUN_ID:'789'},
    encoding:'utf8'
  });
  assert.equal(result.status,1,'full_native_ci evidence must be bound to the attested Native Pilot run');
  assert.match(result.stderr,/full_native_ci evidence does not match the attested Native Pilot run/);

  const fullNativeEnvelope=JSON.parse(fs.readFileSync(path.join(bindingDir,'full_native_ci.json'),'utf8'));
  fullNativeEnvelope.github_run_id=123;
  fs.writeFileSync(path.join(bindingDir,'full_native_ci.json'),JSON.stringify(fullNativeEnvelope));
  const fullNativeSha=crypto.createHash('sha256').update(fs.readFileSync(path.join(bindingDir,'full_native_ci.json'))).digest('hex');
  gates.full_native_ci={status:'PASS',evidence_file:'full_native_ci.json',sha256:fullNativeSha};
  fs.writeFileSync(report,JSON.stringify({source_sha:head,p0:0,p1:0,gates}));
  const backupResult=spawnSync('python3',[script],{
    env:{...env,QG_NATIVE_RELEASE_EVIDENCE:report,QG_CERTIFIED_NATIVE_PILOT_RUN_ID:'123',QG_CERTIFIED_BACKUP_RESTORE_RUN_ID:'789'},
    encoding:'utf8'
  });
  assert.equal(backupResult.status,1,'backup_restore evidence must be bound to the attested Backup Restore Drill');
  assert.match(backupResult.stderr,/backup_restore evidence does not match the attested Backup Restore Drill/);
}finally{fs.rmSync(bindingDir,{recursive:true,force:true});}

const verifier=fs.readFileSync(script,'utf8');
assert.ok(verifier.includes('for role in ("customer", "merchant", "rider")'),'release verifier must apply Play history checks to Customer, Merchant and Rider');
assert.ok(verifier.includes('play_max_name = f"QG_{role.upper()}_PLAY_MAX_VERSION_CODE"'),'release verifier must derive the per-role observed Play max environment name');
assert.ok(verifier.includes('version_code <= play_max'),'release verifier must reject a versionCode that does not exceed observed Play history');
assert.doesNotMatch(verifier,/for role, previous_code in/,'release verifier must not trust hard-coded prior Play versionCodes');
assert.match(verifier,/"backup_restore"/,'Native release must require certified Backup/Restore evidence');
for(const gate of ['service_area','ugc_chat_safety','security_platform_auth']){
  assert.match(verifier,new RegExp(`"${gate}"`),`Native release must require explicit ${gate} evidence`);
}
assert.match(verifier,/PASS_FREE_PLAN_CONTROLS/,'Native release verifier must honor the documented Free-plan Auth compensating-control status');
assert.match(verifier,/name == "security_platform_auth"/,'PASS_FREE_PLAN_CONTROLS exception must be scoped only to the Auth gate');
assert.match(verifier,/QG_ANDROID_SIGNING_CERT_SHA256/,'Native release must require the certified signing certificate fingerprint');
assert.match(verifier,/release signing certificate does not match the certified identity/,'Native release must reject the wrong signing identity');
assert.match(verifier,/def bundle_file\(bundle_root: Path, relative: str, label: str\)/,'release evidence must resolve all bundle paths through one safe helper');
assert.match(verifier,/rel\.is_absolute\(\)/,'release evidence must reject absolute paths');
assert.match(verifier,/"\.\." in rel\.parts/,'release evidence must reject parent traversal');
assert.match(verifier,/\[0-9a-fA-F\]\{64\}/,'release evidence must require a SHA-256 digest');
assert.match(verifier,/\{label\} file is unavailable/,'release evidence helper must require a regular evidence file');
assert.match(verifier,/def sha256_file\(path: Path\)/,'release evidence hashing must stream through a dedicated file helper');
assert.match(verifier,/handle\.read\(1024 \* 1024\)/,'release evidence hashing must use bounded streaming chunks');
assert.doesNotMatch(verifier,/path\.read_bytes\(\)/,'release evidence verifier must not load large evidence files fully into RAM');
assert.match(verifier,/def verify_gate_envelope/,'release verifier must validate structured gate evidence envelopes');
assert.match(verifier,/gate evidence must be a JSON envelope/,'release evidence must use JSON envelopes');
assert.match(verifier,/gate evidence observed_at must be UTC ISO-8601 seconds/,'gate evidence must include a strict UTC observation timestamp');
assert.match(verifier,/gate evidence checks must be non-empty and all true/,'gate evidence must record explicit successful checks');
assert.match(verifier,/gate evidence must reference at least one artifact/,'gate evidence must reference hashed artifacts');
assert.match(verifier,/physical gate evidence must identify at least one device/,'physical evidence must identify a device');
assert.match(verifier,/PHYSICAL_GATE_REQUIRED_ROLES/,'physical evidence must bind gates to the expected app roles');
assert.match(verifier,/physical gate device role is invalid/,'physical evidence must use Customer Merchant or Rider role identity');
assert.match(verifier,/physical gate evidence is missing required roles/,'physical evidence must fail when a required app role is absent');
assert.match(verifier,/requires two distinct devices/,'voice and TURN certification must require two distinct devices');
assert.match(verifier,/requires two distinct networks/,'voice and TURN certification must require two distinct networks');
assert.match(verifier,/PHYSICAL_GATE_REQUIRED_CHECKS/,'physical release gates must define explicit semantic check contracts');
assert.match(verifier,/physical gate must be explicitly operator-certified/,'physical gates must require explicit operator certification');
assert.match(verifier,/physical gate required check missing/,'physical gates must reject missing observed semantics');
assert.match(verifier,/real non-emulator device/,'physical certification must reject emulator-only evidence');
assert.match(verifier,/physical blueprint blocking_differences must equal zero/,'Blueprint certification must reject blocking differences');
assert.match(verifier,/QG_CERTIFIED_NATIVE_PILOT_RUN_ID/,'release evidence must bind full_native_ci to the attested Native Pilot run');
assert.match(verifier,/QG_CERTIFIED_BACKUP_RESTORE_RUN_ID/,'release evidence must bind backup_restore to the attested restore drill');
assert.match(verifier,/backup_restore evidence does not match the attested Backup Restore Drill/,'backup evidence must reject a mismatched restore-drill run');
assert.match(verifier,/production_backend evidence must identify QueueGo Production Supabase/,'production backend evidence must bind the Production Supabase project');
assert.match(verifier,/firebase_three_packages evidence does not match the loaded Firebase project/,'Firebase evidence must bind the loaded project identity');
assert.match(verifier,/play_store_preflight evidence versionName mismatch/,'Play evidence must bind certified versionName');
assert.match(verifier,/play_store_preflight evidence release versionCodes mismatch/,'Play evidence must bind release versionCodes');
assert.match(verifier,/play_store_preflight evidence Play history mismatch/,'Play evidence must bind observed Play version history');
assert.match(verifier,/play_store_preflight evidence registered package names mismatch/,'Play evidence must bind exact QueueGo package names');
assert.match(verifier,/release_signing evidence does not match the certified signing identity/,'signing evidence must bind the certified certificate fingerprint');
for(const legacy of ['.github/workflows/build-queuego-apks.yml','.github/workflows/build-queuego-pilot-apks.yml']){
  assert.equal(fs.existsSync(legacy),false,`legacy Capacitor Android build workflow must stay retired: ${legacy}`);
}

const artifactVerifierPath='native-android/qa/verify-native-release-artifacts.py';
assert.ok(fs.existsSync(artifactVerifierPath),'signed Native release artifact verifier must exist');
const artifactVerifier=fs.readFileSync(artifactVerifierPath,'utf8');
for(const token of [
  '"customer": "com.queuego.customer"',
  '"merchant": "com.queuego.merchant"',
  '"rider": "com.queuego.rider"',
  '"application-debuggable"',
  '"--print-certs"',
  '"jarsigner"',
  '"keytool"',
  '"apk_sha256"',
  '"aab_sha256"',
  '"signer_certificate_sha256"',
  '"aab_signer_certificate_sha256"',
  '"apk_aab_signer_match"',
  '"github_run_id"',
  '"certification_run_id"'
]){
  assert.ok(artifactVerifier.includes(token),`signed artifact verifier missing required check: ${token}`);
}
assert.match(artifactVerifier,/APK\/AAB signer mismatch/,'release artifact verifier must reject per-role APK/AAB signer mismatch');
assert.match(artifactVerifier,/len\(signer_digests\) != 1/,'all three release apps must use one certified signing identity');
assert.ok(artifactVerifier.includes('QG_ANDROID_SIGNING_CERT_SHA256'),'post-build verifier must require the certified signing identity');
assert.ok(artifactVerifier.includes('QG_{role.upper()}_PLAY_MAX_VERSION_CODE'),'post-build verifier must re-check Play version history');
assert.match(artifactVerifier,/code <= play_max/,'post-build verifier must reject stale Play versionCodes');
assert.match(artifactVerifier,/git", "status", "--porcelain"/,'post-build verifier must require clean source provenance');
for(const token of ['QG_BUNDLETOOL_JAR','QG_BUNDLETOOL_SHA256','--xpath=/manifest/@package','--xpath=/manifest/@android:versionCode','--xpath=/manifest/@android:versionName','aab_manifest_metadata_verified']){
  assert.ok(artifactVerifier.includes(token),`post-build verifier missing pinned AAB metadata check: ${token}`);
}
assert.match(artifactVerifier,/AAB package mismatch/,'post-build verifier must reject swapped-role AABs');
assert.match(artifactVerifier,/AAB versionCode mismatch/,'post-build verifier must reject wrong AAB versionCode');
assert.match(artifactVerifier,/AAB versionName mismatch/,'post-build verifier must reject wrong AAB versionName');
assert.match(artifactVerifier,/release artifact evidence must be written outside the source checkout/,'post-build evidence must remain external to source');

const pageSizeVerifierPath='native-android/qa/verify-16kb-page-size.py';
assert.ok(fs.existsSync(pageSizeVerifierPath),'16 KB page-size verifier must exist');
const pageSizeVerifier=fs.readFileSync(pageSizeVerifierPath,'utf8');
for(const token of ['"-P", "16"','MIN_ALIGNMENT = 0x4000','arm64-v8a','x86_64','readelf']){
  assert.ok(pageSizeVerifier.includes(token),`16 KB verifier missing ${token}`);
}
assert.match(artifactVerifier,/PAGE_ALIGNMENT_16K/,'signed AAB verifier must require 16 KB page alignment');
assert.ok(artifactVerifier.includes('"aab_page_alignment_16kb": True'),'signed artifact evidence must record 16 KB AAB alignment');

const nativeCi=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
for(const trigger of ["'tests/**'","'package.json'","'package-lock.json'"]){
  assert.ok(nativeCi.includes(trigger),`Native pilot CI must cover full regression input ${trigger}`);
}

const nativePilotWorkflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
assert.ok(nativePilotWorkflow.includes("- 'work-native-**'"),'Native pilot CI must cover every work-native branch');
assert.ok(nativePilotWorkflow.includes('0918c23673ba6c7a349746f005525aca2ff3470f8e2966d5d2a4cb633ae5980c'),'Native pilot CI must pin the observed Longdo SDK SHA-256');
assert.ok(nativePilotWorkflow.includes('sha256sum -c -'),'Native pilot Longdo integrity gate must use SHA-256');
assert.doesNotMatch(nativePilotWorkflow,/md5sum -c -/,'Native pilot Longdo integrity gate must not fall back to MD5');

const gradle=fs.readFileSync('native-android/build.gradle.kts','utf8');
assert.match(gradle,/isDebuggable = false/);
assert.match(gradle,/signingConfig = nativeReleaseSigning/);
assert.match(gradle,/name\.contains\("Release"\)/,'direct internal release tasks must also require the gate');
assert.match(gradle,/dependsOn\(verifyNativeReleaseGate\)/);
assert(!/signingConfigs.getByName\("debug"\)/.test(gradle),'release must not use debug signing');

const roles=[
  ['customer','QueueGo'],
  ['merchant','QueueGo Merchant'],
  ['rider','QueueGo Rider'],
];
for(const [role,label] of roles){
  const appGradle=fs.readFileSync(`native-android/${role}/build.gradle.kts`,'utf8');
  assert.match(appGradle,new RegExp(`namespace\\s*=\\s*"com\\.queuego\\.${role}"`));
  assert.match(appGradle,new RegExp(`applicationId\\s*=\\s*"com\\.queuego\\.${role}"`));
  assert.ok(appGradle.includes(`providers.environmentVariable("QG_${role.toUpperCase()}_VERSION_CODE")`),`${role} release versionCode must come from the certified environment`);
  assert.ok(appGradle.includes('providers.environmentVariable("QG_NATIVE_VERSION_NAME")'),`${role} release versionName must come from the certified environment`);
  const manifest=fs.readFileSync(`native-android/${role}/src/main/AndroidManifest.xml`,'utf8');
  assert.match(manifest,new RegExp(`android:label="${label}"`));
  assert.match(manifest,/android:icon="@mipmap\/ic_queuego_launcher"/);
  assert.match(manifest,/android:roundIcon="@mipmap\/ic_queuego_launcher"/);
  assert.ok(fs.existsSync(`native-android/branding/${role}-play-store-512.png`),`${role} Play Store 512px icon missing`);
  assert.ok(fs.existsSync(`native-android/branding/${role}-play-store.svg`),`${role} Play Store vector icon missing`);
  for(const density of ['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi']){
    assert.ok(fs.existsSync(`native-android/${role}/src/main/res/mipmap-${density}/ic_queuego_launcher.png`),`${role} launcher ${density} missing`);
  }
}
const sharedManifest=fs.readFileSync('native-android/shared/src/main/AndroidManifest.xml','utf8');
assert.match(sharedManifest,/android\.permission\.FOREGROUND_SERVICE/);
assert.match(sharedManifest,/android\.permission\.FOREGROUND_SERVICE_MICROPHONE/);
assert.match(sharedManifest,/NativeVoiceForegroundService[\s\S]*android:exported="false"[\s\S]*android:foregroundServiceType="microphone"/);
const riderManifest=fs.readFileSync('native-android/rider/src/main/AndroidManifest.xml','utf8');
assert.match(riderManifest,/android\.permission\.SYSTEM_ALERT_WINDOW/);
assert.match(riderManifest,/RiderReturnService[\s\S]*android:foregroundServiceType="specialUse"/);

console.log('Native release packaging guard: PASS (negative authorization checks; physical/signing certification remains OPEN)');
