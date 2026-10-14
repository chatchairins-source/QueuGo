const fs=require('fs');
const assert=require('assert');

const migration=fs.readFileSync('supabase/migrations/20261014101105_rider_delivery_return_to_sender_v1.sql','utf8');
const fix=fs.readFileSync('supabase/migrations/20261014101131_rider_delivery_return_to_sender_runtime_fix_v2.sql','utf8');
const hardening=fs.readFileSync('supabase/migrations/20261014101155_rider_delivery_return_to_sender_hardening_v3.sql','utf8');
const multi=fs.readFileSync('supabase/migrations/20261014102500_rider_delivery_return_multi_stop_v4.sql','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const admin=fs.readFileSync('admin/index.html','utf8');

assert.match(migration,/qg_rider_delivery_returns/);
assert.match(migration,/qg_rider_cash_return_refunds/);
assert.match(migration,/arrive at customer before reporting delivery issue/);
assert.match(migration,/qg_admin_delivery_return_action/);
assert.match(migration,/qg_rider_complete_delivery_return/);
assert.match(migration,/cash_refunded_merchant/);
assert.match(fix,/select r\.\* into v_rider/);
assert.match(hardening,/rpc_only/);
assert.match(multi,/qg_rider_delivery_return_stops/);
assert.match(multi,/qg_rider_active_delivery_return_v2/);
assert.match(multi,/qg_admin_delivery_return_cases_v2/);
assert.match(multi,/cash_paid_amount/);
assert.match(multi,/return_sequence/);
assert.match(multi,/market_order_id/);
assert.match(multi,/rider_arrived_customer_at/);
assert.match(app,/job\.customerArrived/);
assert.match(app,/RiderDeliveryReturnCard/);
assert.match(api,/qg_rider_report_delivery_issue/);
assert.match(api,/qg_rider_active_delivery_return_v2/);
assert.match(api,/qg_rider_complete_delivery_return/);
assert.match(admin,/qg_admin_delivery_return_cases_v2/);
assert.match(admin,/return_to_shop/);
assert.match(admin,/resolve_no_return/);

console.log('rider return-to-sender contracts PASS');
