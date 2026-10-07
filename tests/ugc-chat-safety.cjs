const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const client=read('queuego-ugc.js');
const customer=read('customer-features.js');
const rider=read('rider/index.html');
const merchant=read('merchant/index.html');
const admin=read('admin/index.html');
const migration=read('supabase/migrations/20261007010000_queuego_ugc_chat_moderation.sql');
const snapshot=read('supabase/migrations/20261007010709_ugc_report_content_snapshot.sql');
const privateHelper=read('supabase/migrations/20261007014905_move_ugc_policy_helper_private.sql');
const deletion=read('QueueGo-Pilot-Account-Deletion-Privacy.sql');
const release=read('.github/workflows/build-queuego-apks.yml');
const pilot=read('.github/workflows/build-queuego-pilot-apks.yml');
const rules=read('docs/community-guidelines.html');

for(const fn of ['state','accept','block','unblock','report']){
  ok(new RegExp('const '+fn+'=|function '+fn+'\\(').test(client),'shared UGC client must expose '+fn);
}
ok(client.includes('qg_chat_moderation_state')&&client.includes('qg_report_chat'),'shared UGC client must call server RPCs');
for(const html of [read('index.html'),merchant,rider])ok(html.includes('queuego-ugc.js'),'all role bundles must load shared UGC client');
ok(customer.includes('customerChatGate')&&customer.includes('customerChatReport')&&customer.includes('customerChatBlock'),'Customer chat must enforce terms/report/block');
ok(rider.includes('qgChatGateScreen')&&rider.includes('qgOpenRiderChatReport')&&rider.includes('qgOpenRiderChatSafety'),'Rider chat must enforce terms/report/block');
ok(admin.includes('qgAdminUGCReports')&&admin.includes('qg_admin_ugc_report_action'),'Admin must have UGC moderation queue');
ok(admin.includes('content_snapshot'),'Admin moderation must survive chat expiry');
for(const table of ['qg_ugc_terms_acceptances','qg_user_blocks','qg_ugc_reports'])ok(migration.includes('public.'+table),'UGC migration must create '+table);
ok(migration.includes('qg_ugc_terms_no_client_access')&&migration.includes('qg_user_blocks_no_client_access')&&migration.includes('qg_ugc_reports_no_client_access'),'UGC internal tables must deny direct client table access');
ok(migration.includes('qg_chat_moderation_post_allowed')&&migration.includes('order_chat_messages_insert'),'order chat insert policy must enforce moderation server-side');
ok(privateHelper.includes('queuego_private.qg_chat_moderation_post_allowed'),'RLS-only moderation helper must live outside public schema');
ok(privateHelper.includes('drop function if exists public.qg_chat_moderation_post_allowed'),'public RLS helper RPC surface must be removed');
ok(snapshot.includes('content_snapshot')&&snapshot.includes('qg_report_chat'),'reported message evidence must persist after chat cleanup');
ok(deletion.includes('qg_ugc_terms_acceptances')&&deletion.includes('qg_user_blocks')&&deletion.includes('qg_ugc_reports'),'account deletion must scrub UGC personal state');
ok(release.includes('queuego-ugc.js')&&release.includes('community-guidelines.html'),'release bundle must include UGC safety assets');
ok(pilot.includes('queuego-ugc.js')&&pilot.includes('community-guidelines.html'),'Pilot APK bundle must include UGC safety assets');
ok(/รายงานและบล็อก/.test(rules)&&/เนื้อหาที่ห้าม/.test(rules),'community rules must define prohibited content and report/block behavior');
ok(!/dbSet\(['"]qt_order_chat_/.test(merchant),'Merchant must not have an active legacy order-chat sender without moderation UI');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo UGC terms, server enforcement, reporting, blocking, moderation, retention and Android bundling'}));
