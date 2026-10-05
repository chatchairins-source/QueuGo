const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const realtime=fs.readFileSync(path.join(root,'role-realtime.js'),'utf8');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261005214200_realtime_publication_alignment.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const mustPublish=[
 'market_orders','market_order_pickups','market_requests',
 'laundry_orders','laundry_rider_jobs','laundry_rider_invites',
 'queuego_platform_rules','route_bundles',
 'users','shop_profiles','technician_profiles','products','payments',
 'quotations','promotions','settlements','audit_logs','order_audit'
];

for(const table of mustPublish){
  ok(realtime.includes("'"+table+"'"),table+' must be a subscribed QueueGo Realtime table');
  ok(sql.includes("'"+table+"'"),table+' must be aligned into supabase_realtime publication');
}
ok(sql.includes("pg_publication_tables"),'migration must be idempotent against existing publication membership');
ok(sql.includes("alter publication supabase_realtime add table"),'migration must only add subscribed tables to Supabase Realtime');
ok(!/\bdrop\s+(table|schema|column)\b/i.test(sql),'Realtime alignment must not destructively drop data');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Realtime publication alignment for Merchant/Admin Market/Laundry and governance subscriptions'}));
