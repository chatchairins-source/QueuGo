const fs=require('fs'),path=require('path'),assert=require('assert');
let checks=0;const ok=(v,msg)=>{assert.ok(v,msg);checks++};
const admin=fs.readFileSync(path.resolve(__dirname,'../admin/index.html'),'utf8');
const realtime=fs.readFileSync(path.resolve(__dirname,'../role-realtime.js'),'utf8');
const start=admin.indexOf('async function qtHydrateAdminDirectory()');
const end=admin.indexOf('async function qtAdminRefreshScoped(',start);
ok(start>0&&end>start,'Admin directory fast path exists');
const fast=admin.slice(start,end);
ok(fast.includes('Promise.all(['),'directory reads run concurrently');
ok(fast.includes("users?select=id,auth_user_id,role,name,phone,status,metadata,created_at"),'users query is column-bounded');
ok(!fast.includes("users?select=*"),'fast path does not select all user columns');
for(const forbidden of ['orders?select=','order_items?select=','products?select=','notifications?select=','settlements?select=','order_audit?select=']){
  ok(!fast.includes(forbidden),'fast path excludes '+forbidden);
}
const restoreStart=admin.indexOf('<script id="qg-admin-control-restore">');
const restoreEnd=admin.indexOf('</script>',restoreStart);
const restore=admin.slice(restoreStart,restoreEnd);
ok(restore.includes('controlRefresh=qtAdminRefreshScoped()'),'control center uses scoped refresh');
ok(!restore.includes('controlRefresh=qtHydrateDatabase({light:false})'),'control center no longer forces full hydrate');
ok(restore.includes('qtHydrateAdminDirectory()'),'approval actions refresh only the account directory');
ok(admin.includes("window.QT_REALTIME_STATUS==='SUBSCRIBED'&&!force"),'8-second polling pauses while Admin realtime is healthy');
ok(realtime.includes("typeof window.qtRealtimeHydrate==='function'"),'Admin realtime can use the scoped hydration hook');
ok(!admin.includes('qgRefreshAdminShopStates'),'dead legacy shop-state refresher is removed');
ok(!admin.includes("shop_profiles?select=id,user_id&archived_at=is.null"),'Admin source has no all-current-shops state-refresh query');
ok(!admin.includes("shop_open_states?select=shop_id,is_open,resume_at"),'Admin source has no all-shop-open-states refresh query');
console.log(JSON.stringify({checks,failures:0,scope:'Admin governance fast-path excludes orders/catalog/history and avoids duplicate realtime polling'}));
