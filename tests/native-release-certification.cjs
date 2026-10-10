const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const cert=read('.github/workflows/native-release-certification.yml');
const release=read('.github/workflows/build-native-release.yml');
const bucketConfig=JSON.parse(read('native-android/qa/release-evidence-storage.json'));

ok(/workflow_dispatch:/.test(cert),'release certification must be manual only');
ok(!/\n\s*push:/.test(cert),'release certification must never run from push');
ok(cert.includes('refs/heads/queuego-native-android-v1'),'certification must be restricted to Native Source of Truth');
ok(cert.includes('actions: read'),'certification must have read-only Actions permission for exact-HEAD CI attestation');
ok(cert.includes('Require successful exact-HEAD Native Pilot CI'),'certification must require automated Native Pilot CI attestation');
ok(cert.includes('.github/workflows/build-native-rider-pilot.yml')&&cert.includes('.event == "push"')&&cert.includes('.conclusion == "success"'),'certification must pin the successful main-branch Native Pilot workflow identity');
ok(cert.includes('QG_CERTIFIED_NATIVE_PILOT_RUN_ID'),'certification must retain the attested Native Pilot run id');
ok(cert.includes('Require successful exact-HEAD Backup Restore Drill'),'certification must require an exact-HEAD Backup Restore Drill');
ok(cert.includes('.github/workflows/backup-restore-drill.yml')&&cert.includes('.event == "workflow_dispatch"')&&cert.includes('.conclusion == "success"'),'certification must pin the successful Backup Restore workflow identity');
ok(cert.includes('QG_CERTIFIED_BACKUP_RESTORE_RUN_ID'),'certification must retain the attested Backup Restore run id');
ok(cert.includes('queuego-backup-')&&cert.includes('.expired == false')&&cert.includes('.size_in_bytes > 0'),'certification must require a live non-empty encrypted backup artifact');
ok(cert.includes('queuego-native-release-evidence'),'certification must use the fixed private evidence bucket');
ok(bucketConfig.project_ref==='pkypiqhlrmzocysgeqew'&&bucketConfig.bucket_id==='queuego-native-release-evidence','Source mirror must pin the Production release evidence bucket');
ok(bucketConfig.public===false&&bucketConfig.client_storage_policies_present===false,'Source mirror must record private server-side-only Storage access');
ok(Array.isArray(bucketConfig.allowed_mime_types)&&bucketConfig.allowed_mime_types.includes('application/zip'),'Source mirror must keep release evidence ZIP-only');
ok(bucketConfig.provisioning_mode==='SUPABASE_STORAGE_API_OR_DASHBOARD_ONLY'&&bucketConfig.storage_schema_sql_mutation_forbidden===true,'Release evidence bucket provisioning must not mutate the Supabase storage schema through SQL');
ok(Array.isArray(bucketConfig.runtime_verification)&&bucketConfig.runtime_verification.includes('.github/workflows/native-release-certification.yml')&&bucketConfig.runtime_verification.includes('.github/workflows/release-secret-readiness.yml'),'Bucket source mirror must retain runtime verification in certification and readiness workflows');
ok(!fs.existsSync('supabase/migrations/20261010133000_native_release_evidence_bucket.sql'),'Unsafe direct storage-schema migration must stay retired');
ok(cert.includes('vars.QG_SUPABASE_URL'),'certification must source the Supabase project URL from Actions variables');
ok(cert.includes('https://pkypiqhlrmzocysgeqew.supabase.co'),'certification must pin the QueueGo Production Supabase origin');
ok(cert.includes('/storage/v1/bucket/$QG_EVIDENCE_BUCKET'),'certification must verify the evidence bucket metadata before download');
ok(cert.includes('Release evidence bucket must remain private'),'certification must reject a public evidence bucket');
ok(cert.includes('secrets.QG_SUPABASE_SERVICE_ROLE_KEY'),'certification must use service role only from Actions secrets');
ok(cert.includes('/storage/v1/object/authenticated/'),'certification must download evidence from private authenticated Storage');
ok(cert.includes('Authorization: Bearer $QG_SUPABASE_SERVICE_ROLE_KEY')&&cert.includes('apikey: $QG_SUPABASE_SERVICE_ROLE_KEY'),'private evidence download must authenticate server-side');
ok(cert.includes('evidence_bundle_sha256')&&cert.includes('sha256sum -c -'),'certification must verify the evidence ZIP SHA-256 before extraction');
ok(cert.includes("--proto '=https'")&&cert.includes('--tlsv1.2'),'certification Storage requests must require HTTPS/TLS');
ok(!cert.includes('--location'),'certification must not follow redirects while sending service-role headers');
ok(cert.includes('Evidence ZIP contains path traversal'),'certification must reject ZIP traversal');
ok(cert.includes('Evidence ZIP contains duplicate paths'),'certification must reject duplicate ZIP entry paths');
ok(cert.includes('Evidence ZIP contains non-portable backslash paths'),'certification must reject backslash ZIP paths');
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
  '"native_pilot_run_id": int(os.environ["QG_CERTIFIED_NATIVE_PILOT_RUN_ID"])',
  '"backup_restore_run_id": int(os.environ["QG_CERTIFIED_BACKUP_RESTORE_RUN_ID"])',
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
ok(release.includes('Certification Native Pilot run id is missing or invalid'),'final release must require a recorded Native Pilot CI run id');
ok(release.includes('Certification references an invalid Native Pilot CI run'),'final release must independently revalidate the certified Native Pilot CI run');
ok(release.includes('Certification Backup Restore run id is missing or invalid'),'final release must require a recorded Backup Restore run id');
ok(release.includes('Certification references an invalid Backup Restore Drill run'),'final release must independently revalidate the certified Backup Restore Drill');
ok(release.includes('Certification Backup Restore Drill artifact is unavailable or expired'),'final release must require the backup artifact to remain live');

console.log(JSON.stringify({checks,failures:0,scope:'Trusted external physical evidence intake and exact-metadata Native release certification'}));
