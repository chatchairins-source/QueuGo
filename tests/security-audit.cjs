const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..'),sql=fs.readFileSync(path.join(root,'QueueGo-Pilot-Security-Audit.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
for(const fn of ['qg_guard_pickup_photo()','qg_snapshot_order_item_image()','pos_guard_delivery_enabled()','pos_guard_write()','pos_record_event()','pos_set_new_item_state()']){
 ok(sql.includes('revoke all on function public.'+fn+' from public,anon,authenticated'),'trigger '+fn+' must reject direct client execution');
}
for(const sig of ['merchant_confirm_cash(uuid)','market_rider_confirm_pickup_cash(uuid,numeric)','claim_delivery(uuid)']){
 ok(sql.includes('revoke execute on function public.'+sig+' from authenticated'),'retired '+sig+' must not be client-callable');
}
for(const sig of ['effective_gp_rate_at(uuid,timestamptz)','queuego_route_bundle_quote_orders(uuid,uuid,timestamptz)']){
 ok(sql.includes('revoke execute on function public.'+sig+' from authenticated'),'internal helper '+sig+' must be service-only');
}
for(const table of ['admin_order_delete_archive','market_order_add_requests','pos_request_keys','qg_delivery_pins','qg_merchant_action_receipts','qg_pickup_proofs','qg_rider_offer_history','qg_rider_order_offers','qg_rider_push_config','qg_rider_push_outbox','qg_rider_push_subscriptions','qg_table_requests','qg_table_sessions','queuego_cash_order_locks']){
 ok(sql.includes("'"+table+"'"),'server-only table '+table+' must be in explicit deny list');
}
ok(sql.includes('qg_server_only_no_client_access'),'explicit deny RLS policy must be source-controlled');
ok(sql.includes('Intentional anonymous SECURITY DEFINER allowlist'),'public definer endpoints must have a reviewed allowlist');
ok(!/grant\s+execute[^;]+to\s+(public|anon)\s*;/i.test(sql),'hardening migration must not add anonymous execute grants');
console.log(JSON.stringify({checks,failures:0,scope:'QueueGo pilot Data API/SECURITY DEFINER surface hardening and explicit server-only RLS deny'}));
