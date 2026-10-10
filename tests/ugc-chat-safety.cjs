const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const client=read('queuego-ugc.js');
const customer=read('customer-features.js');
const rider=read('rider/index.html');
const merchant=read('merchant/index.html');
const admin=read('admin/index.html');
const migration=read('supabase/migrations/20261007005927_queuego_ugc_chat_moderation.sql');
const snapshot=read('supabase/migrations/20261007010709_ugc_report_content_snapshot.sql');
const privateHelper=read('supabase/migrations/20261007014905_move_ugc_policy_helper_private.sql');
const anonPrivilege=read('supabase/migrations/20261007023348_ugc_chat_revoke_anon_table_privileges.sql');
const deletion=read('QueueGo-Pilot-Account-Deletion-Privacy.sql');
const nativeCustomerChat=read('native-android/customer/src/main/java/com/queuego/customer/CustomerChat.kt');
const nativeRiderChat=read('native-android/rider/src/main/java/com/queuego/rider/RiderChat.kt');
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
ok(!/qgAdminLoadUGCReports[\s\S]{0,2600}order_chat_messages\?/.test(admin),'Admin UGC queue must not read live chat rooms');
ok(/data:image\\\/(?:jpeg\|png\|webp)/.test(admin)||admin.includes('data:image\\/(?:jpeg|png|webp)'), 'Admin must render only reported image snapshots with a strict image data URL allowlist');
for(const table of ['qg_ugc_terms_acceptances','qg_user_blocks','qg_ugc_reports'])ok(migration.includes('public.'+table),'UGC migration must create '+table);
ok(migration.includes('qg_ugc_terms_no_client_access')&&migration.includes('qg_user_blocks_no_client_access')&&migration.includes('qg_ugc_reports_no_client_access'),'UGC internal tables must deny direct client table access');
ok(migration.includes('qg_chat_moderation_post_allowed')&&migration.includes('order_chat_messages_insert'),'order chat insert policy must enforce moderation server-side');
ok(privateHelper.includes('queuego_private.qg_chat_moderation_post_allowed'),'RLS-only moderation helper must live outside public schema');
ok(privateHelper.includes('drop function if exists public.qg_chat_moderation_post_allowed'),'public RLS helper RPC surface must be removed');
ok(/revoke all privileges on table public\.order_chat_messages from anon/i.test(anonPrivilege),'Anonymous Data API role must have no direct order-chat table privileges');
ok(/grant select, insert, update, delete on table public\.order_chat_messages to authenticated/i.test(anonPrivilege),'Authenticated order-chat table privileges must remain explicit');
ok(snapshot.includes('content_snapshot')&&snapshot.includes('qg_report_chat'),'reported message evidence must persist after chat cleanup');
ok(deletion.includes('qg_ugc_terms_acceptances')&&deletion.includes('qg_user_blocks')&&deletion.includes('qg_ugc_reports'),'account deletion must scrub UGC personal state');
for(const [role,source] of [['Customer',nativeCustomerChat],['Rider',nativeRiderChat]]){
  for(const rpc of ['qg_chat_moderation_state','qg_accept_ugc_terms','qg_block_chat_counterpart','qg_unblock_chat_counterpart','qg_report_chat']){
    ok(source.includes(rpc),role+' Native chat must use UGC safety RPC '+rpc);
  }
  ok(source.includes('community-guidelines.html'),role+' Native chat must expose QueueGo community guidelines');
}
ok(!fs.existsSync(path.join(root,'.github/workflows/build-queuego-apks.yml')),'legacy Capacitor release workflow must stay retired');
ok(!fs.existsSync(path.join(root,'.github/workflows/build-queuego-pilot-apks.yml')),'legacy Capacitor pilot workflow must stay retired');
ok(/รายงานและบล็อก/.test(rules)&&/เนื้อหาที่ห้าม/.test(rules),'community rules must define prohibited content and report/block behavior');
ok(!/dbSet\(['"]qt_order_chat_/.test(merchant),'Merchant must not have an active legacy order-chat sender without moderation UI');
ok(!/qtPersistChat|qt_order_chat_|qt-shop-chat|qt-chat-open/.test(merchant),'Merchant must not retain dead legacy order-chat implementation');
ok(!merchant.includes('function qtPersistChat'),'Merchant dead legacy chat persistence must stay removed');
ok(!merchant.includes('qt_order_chat_'),'Merchant dead legacy chat cache keys must stay removed');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo UGC terms, server enforcement, reporting, blocking, moderation, retention and Native Customer/Rider safety'}));
