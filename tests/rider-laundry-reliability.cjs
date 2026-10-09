const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
const reliability=fs.readFileSync(path.join(root,'rider/reliability.js'),'utf8');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261005212500_rider_laundry_action_receipts.sql'),'utf8');
const dispatchSql=fs.readFileSync(path.join(root,'supabase/migrations/20261009044500_rider_dispatch_excludes_active_laundry.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

for(const [rpc,kind] of Object.entries({
  "rpc/queuego_set_laundry_rider_mode":"laundry_mode",
  "rpc/queuego_laundry_rider_invite_action":"laundry_invite",
  "rpc/queuego_claim_laundry_job":"laundry_claim",
  "rpc/queuego_laundry_rider_action":"laundry_action"
})){
  ok(reliability.includes("'"+rpc+"':'"+kind+"'"),rpc+' must use durable Rider intent');
  ok(rider.includes("qgRiderMutation('"+rpc+"'"),rpc+' UI must use qgRiderMutation');
}

ok(reliability.includes('[data-laundry-mutation]'),'pending recovery must lock Laundry action controls');
ok((rider.match(/data-laundry-mutation/g)||[]).length>=4,'Laundry UI controls must opt into recovery lock');
ok(rider.includes("qgLaundryMutationInFlight"),'Laundry actions must ignore rapid duplicate taps');
ok(rider.includes("qgLaundryRefreshAfterCommit"),'Laundry post-commit refresh must be non-fatal');
ok(!rider.includes("await sbTable('rpc/queuego_claim_laundry_job'"),'Laundry claim must not bypass durable action wrapper');
ok(!/toast\([^;]{0,180}\);await refreshData\(\)/.test(rider),'successful Rider mutation must not be turned into a false failure by refresh');
for(const kind of ['bundle_claim','laundry_mode','laundry_invite','laundry_claim','laundry_action']){
  ok(sql.includes("when '"+kind+"'"),kind+' must be supported by qg_rider_action_once');
}
ok(sql.includes('pg_advisory_xact_lock'),'Rider receipt wrapper must serialize the request id');
ok(sql.includes('request id already in use'),'request id payload mismatch must stay blocked');
ok(!/\bdrop\s+(table|function|schema)\b/i.test(sql),'migration must be additive');
ok(dispatchSql.includes("from public.laundry_rider_jobs lj")&&dispatchSql.includes("lj.status in('assigned','accepted','arrived','collected')"),'automatic Rider dispatch must exclude Riders performing active Laundry legs');
ok(dispatchSql.includes("now()+interval '30 seconds'"),'Laundry availability guard must preserve the 30-second sequential offer lease');
ok(dispatchSql.includes("insert into public.notifications(user_id,title,message,type,reference_id)"),'Laundry availability guard must preserve selected-Rider push notification creation');
ok(!/\bdrop\s+(table|function|schema)\b/i.test(dispatchSql),'dispatch safety migration must not drop schema objects');

console.log(JSON.stringify({checks,failures:0,scope:'Rider Laundry durable one-tap intents, automatic-dispatch exclusivity and non-fatal post-commit refresh'}));
