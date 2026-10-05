const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261005210500_rider_order_action_repeat_safe.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(sql.includes("create or replace function public.rider_claim_order"),'claim function must be replaced safely');
ok(sql.includes("v_order.rider_id=v_rider"),'same rider replay must be detected');
ok(sql.includes("id<>p_order_id"),'active-order guard must exclude the replayed order');
ok(sql.includes("return v_order.status;"),'repeat claim must return authoritative state');
ok(sql.includes("create or replace function public.rider_order_action"),'rider action function must be replaced safely');
ok(sql.includes("if v_order.rider_arrived_shop_at is not null then return v_order.status; end if;"),'arrive-shop replay must be repeat-safe');
ok(sql.includes("p_action='pickup_cash' and v_order.status in ('picked_up','in_progress')"),'pickup replay must be repeat-safe');
ok(sql.includes("p_action='deliver' and v_order.status='in_progress'"),'deliver replay must be repeat-safe');
ok(sql.includes("p_action='arrive'")&&sql.includes("like '__QT_ORDER_STATUS__=arrived%'"),'arrival replay must be repeat-safe');
ok(sql.includes("if v_order.status='completed' then return 'completed'; end if;"),'completion replay must be repeat-safe');
ok(sql.includes("revoke all on function public.rider_claim_order(uuid) from anon"),'claim RPC must remain unavailable to anon');
ok(sql.includes("revoke all on function public.rider_order_action(uuid,text) from anon"),'action RPC must remain unavailable to anon');

console.log(JSON.stringify({checks,failures:0,scope:'legacy Rider direct-RPC repeat safety after committed actions'}));
