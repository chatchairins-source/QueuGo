const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
const reliability=fs.readFileSync(path.join(root,'rider/reliability.js'),'utf8');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261005212500_rider_laundry_action_receipts.sql'),'utf8');
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

ok(rider.includes('<script src="reliability.js?v=20261023-laundryfix1"></script>'),'Rider page must actually load the durable reliability layer');
ok(rider.indexOf('<script src="reliability.js?v=20261023-laundryfix1"></script>')<rider.indexOf('<script>boot();</script>'),'durable reliability layer must load before Rider boot');
ok(rider.indexOf('<script src="reliability.js?v=20261023-laundryfix1"></script>')<rider.indexOf('<script id="qg-rider-order-actions">'),'durable wrapper must exist before order-action UI can be used');
ok(reliability.includes("['mode','invite-accept','invite-reject'].includes(laundry)"),'stale snapshot must not lock Laundry mode or invite decisions');
ok(reliability.includes("pending||S.snapshotStale&&staleSensitive(target)"),'click guard must only block stale-sensitive controls while snapshot is stale');
ok(reliability.includes('[data-laundry-mutation]'),'pending recovery must lock Laundry action controls');
ok((rider.match(/data-laundry-mutation/g)||[]).length>=4,'Laundry UI controls must opt into recovery lock');
ok(rider.includes('button[data-accept="1"]{background:#e6002d;color:#fff}'),'Laundry accept button must be visibly actionable in QueueGo red');
ok(rider.includes('button[data-accept="0"]{background:#fff;color:#e6002d}'),'Laundry reject button must remain a clear secondary action');
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

console.log(JSON.stringify({checks,failures:0,scope:'Rider Laundry durable one-tap intents and non-fatal post-commit refresh'}));
