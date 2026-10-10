const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const cert=read('.github/workflows/native-release-certification.yml');
const release=read('.github/workflows/build-native-release.yml');

ok(/workflow_dispatch:/.test(cert),'release certification must be manual only');
ok(!/\n\s*push:/.test(cert),'release certification must never run from push');
ok(cert.includes('refs/heads/queuego-native-android-v1'),'certification must be restricted to Native Source of Truth');
ok(cert.includes('queuego-native-release-evidence'),'certification must use the fixed private evidence bucket');
ok(cert.includes('vars.QG_SUPABASE_URL'),'certification must source the Supabase project URL from Actions variables');
ok(cert.includes('secrets.QG_SUPABASE_SERVICE_ROLE_KEY'),'certification must use service role only from Actions secrets');
ok(cert.includes('/storage/v1/object/authenticated/'),'certification must download evidence from private authenticated Storage');
ok(cert.includes('Authorization: Bearer $QG_SUPABASE_SERVICE_ROLE_KEY')&&cert.includes('apikey: $QG_SUPABASE_SERVICE_ROLE_KEY'),'private evidence download must authenticate server-side');
ok(cert.includes('evidence_bundle_sha256')&&cert.includes('sha256sum -c -'),'certification must verify the evidence ZIP SHA-256 before extraction');
ok(cert.includes('Evidence ZIP contains path traversal'),'certification must reject ZIP traversal');
ok(cert.includes('Evidence ZIP may not contain symlinks'),'certification must reject ZIP symlinks');
ok(cert.includes('Evidence ZIP exceeds 4 GiB uncompressed limit'),'certification must bound extracted evidence size');
ok(cert.includes('native-release-evidence.json must exist at the evidence bundle root'),'certification report must be at the bundle root');
for(const pkg of ['com.queuego.customer','com.queuego.merchant','com.queuego.rider']){
  ok(cert.includes(pkg),'certification must validate Firebase client '+pkg);
}
ok(cert.includes('python3 native-android/qa/verify-native-release-gate.py'),'certification must use the canonical fail-closed Native release verifier');
ok(cert.includes('certified-release-metadata.json'),'certification must bind exact release metadata');
for(const key of [
  '"source_sha": os.environ["GITHUB_SHA"]',
  '"certification_run_id": os.environ["GITHUB_RUN_ID"]',
  '"version_name": os.environ["QG_NATIVE_VERSION_NAME"]',
  '"customer_version_code"',
  '"merchant_version_code"',
  '"rider_version_code"',
  '"customer_play_max_version_code"',
  '"merchant_play_max_version_code"',
  '"rider_play_max_version_code"',
  '"signing_certificate_sha256"'
]) ok(cert.includes(key),'certification metadata missing binding: '+key);
ok(cert.includes('name: queuego-native-release-certification'),'certification must emit the fixed trusted artifact name');
ok(cert.includes('if: always()')&&cert.includes('queuego-release.keystore')&&cert.includes('queuego-native-physical-evidence.zip'),'certification must clean decoded credentials and downloaded evidence');

ok(release.includes('run_path')&&release.includes('.github/workflows/native-release-certification.yml'),'final release must pin certification workflow identity');
ok(release.includes('run_event')&&release.includes('workflow_dispatch'),'final release must require manual certification dispatch');
ok(release.includes('run_branch')&&release.includes('queuego-native-android-v1'),'final release must require certification from Native Source of Truth');
ok(release.includes('certified-release-metadata.json'),'final release must require certification metadata');
ok(release.includes('Certification metadata mismatch'),'final release must fail closed when certified metadata differs');
ok(release.includes('Certification signing fingerprint does not match release input'),'final release must bind the certified signing identity');

console.log(JSON.stringify({checks,failures:0,scope:'Trusted external physical evidence intake and exact-metadata Native release certification'}));
