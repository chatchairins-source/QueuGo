const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
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
assert.match(verifier,/evidence_rel\.is_absolute\(\)/,'release evidence must reject absolute paths');
assert.match(verifier,/"\.\." in evidence_rel\.parts/,'release evidence must reject parent traversal');
assert.match(verifier,/\[0-9a-fA-F\]\{64\}/,'release evidence must require a SHA-256 digest');
assert.match(verifier,/gate evidence file is unavailable/,'release evidence must require a regular evidence file');
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
