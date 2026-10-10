const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const workflow=read('.github/workflows/backup-restore-drill.yml');
const nativeReleaseVerifier=read('native-android/qa/verify-native-release-gate.py');
const exporter=read('ops/backup-storage.mjs');
const restorer=read('ops/restore-storage.mjs');
const inventory=read('ops/backup-inventory.sql');
const verify=read('ops/restore-verify.sql');

ok(/workflow_dispatch/.test(workflow),'backup drill must be manually runnable');
ok(/supabase@2\.120\.0 db dump/.test(workflow),'backup drill must use pinned Supabase-aware database dump');
ok(!/supabase@latest/.test(workflow),'backup workflow must pin Supabase CLI and never use @latest');
ok(/--role-only/.test(workflow)&&/--data-only/.test(workflow)&&/--use-copy/.test(workflow),'backup must export roles, schema and data');
ok(/supabase@2\.120\.0 start/.test(workflow),'restore drill must start a clean local Supabase with the pinned CLI');
ok(/--single-transaction/.test(workflow)&&/ON_ERROR_STOP/.test(workflow),'database restore must be atomic and stop on errors');
ok(/restore-storage\.mjs/.test(workflow),'Storage objects must be restored during the drill');
ok(/aes-256-cbc/.test(workflow)&&/pbkdf2/.test(workflow),'off-site artifact must be encrypted before upload');
ok(/actions\/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02/.test(workflow)&&/\.tar\.gz\.enc/.test(workflow),'only encrypted backup artifact should be uploaded through the reviewed pinned artifact action');
ok(!/upload-artifact@[\s\S]{0,400}backup\/data\.sql/.test(workflow),'plaintext database dump must never be uploaded');
ok(/QG_BACKUP_PASSPHRASE/.test(workflow)&&/QG_SUPABASE_DB_URL/.test(workflow)&&/QG_SUPABASE_SERVICE_ROLE_KEY/.test(workflow),'backup secrets must be externalized');
ok(/missing=0[\s\S]*Missing required secret: \$name[\s\S]*missing=1[\s\S]*if \[ "\$missing" -ne 0 \]/.test(workflow),'backup secret preflight must report all missing secrets before failing');
ok(exporter.includes('storage-manifest.json')&&exporter.includes('sha256'),'Storage exporter must hash every object');
ok(restorer.includes('upsert:true')&&restorer.includes('Restored object verification failed'),'Storage restore must re-upload and checksum verify objects');
ok(inventory.includes('auth.users')&&inventory.includes('storage.objects'),'backup inventory must cover Auth and Storage metadata');
ok(inventory.includes('public.qg_ugc_terms_acceptances')&&inventory.includes('public.qg_user_blocks')&&inventory.includes('public.qg_ugc_reports'),'backup inventory must cover UGC safety state');
ok(verify.includes("queuego_private.qg_chat_moderation_post_allowed"),'restore must verify private UGC moderation helper');
ok(verify.includes("public.qg_chat_moderation_post_allowed(uuid,uuid)")&&verify.includes("Restore exposed deprecated public chat moderation helper"),'restore must reject deprecated public UGC helper');
ok(verify.includes("content_snapshot")&&verify.includes("qg_ugc_reports"),'restore must verify UGC moderation evidence snapshot');
ok(verify.includes('queuego_restore_verification_ok'),'restore SQL must emit a success sentinel');
ok(verify.includes('qg_notifications_queuego_push'),'restore must verify unified notification trigger');
ok(/"backup_restore"/.test(nativeReleaseVerifier),'Native Closed Beta release must depend on certified Backup/Restore evidence');

console.log(JSON.stringify({checks,failures:0,scope:'Encrypted external database + Storage backup and local restore drill'}));
