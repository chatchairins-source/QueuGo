const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
const customer=fs.readFileSync(path.join(root,'customer-features.js'),'utf8');
const activeSql=fs.readFileSync(path.join(root,'supabase/migrations/20261006134500_rider_authoritative_active_flow.sql'),'utf8');
const photoOnlySql=fs.readFileSync(path.join(root,'supabase/migrations/20261006150500_rider_photo_only_flow.sql'),'utf8');
const retirePinSql=fs.readFileSync(path.join(root,'supabase/migrations/20261006153500_retire_delivery_pin_generation.sql'),'utf8');
const imageSnapshotSql=fs.readFileSync(path.join(root,'supabase/migrations/20261006152500_order_item_image_snapshot.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(rider.includes("function qgRiderRefreshAfterCommit(label)"),'rider must have non-fatal post-commit refresh helper');
ok(rider.includes("qgRiderRefreshAfterCommit('rider claim refresh failed')"),'normal claim refresh must be isolated after commit');
ok(rider.includes("qgRiderRefreshAfterCommit('rider market claim refresh failed')"),'market claim refresh must be isolated after commit');

ok(rider.includes("function qgOpenPickupVerify(o)"),'ready orders must open a dedicated pickup screen');
ok(rider.includes('id="qg-pickup-photo"'),'pickup screen must require one photo');
ok(rider.includes("rpc/qg_pickup_with_photo"),'normal pickup must use the photo-backed RPC');
ok(rider.includes("rpc/qg_market_pickup_with_photo"),'market pickup must use the photo-backed RPC');
ok(rider.includes('id="qg-pickup-confirm-btn"'),'pickup must use a direct compact confirm button');
ok(!rider.includes('data-pick-check'),'pickup must not require redundant checkbox confirmations');
ok(!rider.includes('qg-flow-slide'),'pickup/delivery must not use stretched slide controls');
ok(rider.includes('qg-sheet-grab-static'),'home job sheet must be static, not manually stretched');
ok(rider.includes('function qgBindSheetToolbar(){ /* static sheet: intentionally no drag/tap expansion */ }'),'sheet expansion handler must be disabled');

ok(rider.includes("function qgOpenDeliveryConfirm(o)"),'arrival must open a dedicated delivery confirmation screen');
ok(rider.includes('id="qg-delivery-photo"'),'delivery completion must require one photo');
ok(rider.includes('id="qg-delivery-complete-btn"'),'delivery completion must use a direct compact button');
ok(rider.includes("qg_complete_with_photo"),'normal completion must use the photo-only RPC');
ok(rider.includes("qg_complete_market_with_photo"),'market completion must use the photo-only RPC');
ok(!rider.includes('qg-delivery-photo-extra'),'delivery must not request a second photo');
ok(!rider.includes('qg-pin-digits'),'Rider must not request a customer PIN');
ok(!rider.includes('รหัสส่งมอบ 6 หลัก'),'Rider UI must not mention a handoff PIN');
ok(!rider.includes('qg_complete_with_proof'),'Rider client must not call retired PIN proof RPCs');
ok(!customer.includes('qg_customer_delivery_pin'),'Customer must not fetch a delivery PIN');
ok(!customer.includes('qg-handoff-pin'),'Customer must not render a delivery PIN card');

ok(rider.includes("'phone': '<svg"),'pickup and delivery screens must retain a real phone icon');
ok(rider.includes("const APP_VERSION='4.1.2';"),'photo-only Rider rebuild must expose the current app version');
ok(rider.includes("function qgOpenAccountSettings()"),'profile must expose real account settings');
ok(rider.includes("ข้อมูลรถที่อนุมัติ"),'approved vehicle identity must remain protected');
ok(!rider.includes("vehicle_type:vehicle,vehicle_plate:nextPlate"),'account settings must not silently change approved vehicle identity');
ok(rider.includes("item_image&order_id=eq."),'pickup/delivery verification must read immutable item image snapshots when available');
ok(rider.includes("const QG_ARRIVAL_GUARD_KM = 0.25;"),'arrival action must keep a declared GPS warning threshold');
ok(rider.includes('id="details-'),'active-order card must expose order details');
ok(!rider.includes("qgOpenOrderChecklist("),'obsolete legacy order-check page must stay removed');
ok(!rider.includes("qg-ordercheck-page"),'obsolete ordercheck markup must stay removed');
ok(!rider.includes('id="more-'),'legacy active-order more button must stay removed');
ok(!rider.includes("action={ready:'pickup_cash',picked_up:'deliver',in_progress:'complete'}"),'direct completion must never bypass delivery photo');

ok(activeSql.includes('CREATE POLICY order_items_rider_select'),'assigned Rider must be able to read order items');
ok(activeSql.includes("v_action='complete' AND v_order.status='in_progress'"),'server state machine must keep the authoritative completion transition');
ok(!activeSql.includes('__QT_ORDER_STATUS__=arrived'),'active state machine must not recreate pseudo arrived status');

ok(photoOnlySql.includes('CREATE TABLE IF NOT EXISTS public.qg_pickup_proofs'),'pickup photo must be persisted server-side');
ok(photoOnlySql.includes("RAISE EXCEPTION 'pickup photo required'"),'server must block picked_up without a pickup photo');
ok(photoOnlySql.includes("RAISE EXCEPTION 'delivery photo required'"),'server must block completed without a delivery photo');
ok(photoOnlySql.includes('CREATE OR REPLACE FUNCTION public.qg_pickup_with_photo'),'normal pickup photo RPC must exist');
ok(photoOnlySql.includes('CREATE OR REPLACE FUNCTION public.qg_market_pickup_with_photo'),'market pickup photo RPC must exist');
ok(photoOnlySql.includes('CREATE OR REPLACE FUNCTION public.qg_complete_with_photo'),'normal delivery photo RPC must exist');
ok(photoOnlySql.includes('CREATE OR REPLACE FUNCTION public.qg_complete_market_with_photo'),'market delivery photo RPC must exist');
ok(photoOnlySql.includes('REVOKE EXECUTE ON FUNCTION public.qg_customer_delivery_pin'),'customer PIN route must be retired');
ok(!photoOnlySql.includes('incorrect delivery PIN'),'photo-only migration must not validate a PIN');
ok(retirePinSql.includes('DROP TRIGGER IF EXISTS qg_issue_pin_on_assignment'),'delivery PIN assignment trigger must be retired');
ok(retirePinSql.includes('DROP TRIGGER IF EXISTS trg_qg_market_sync_delivery_pin'),'market PIN sync trigger must be retired');
ok(retirePinSql.includes('REVOKE EXECUTE ON FUNCTION public.qg_customer_delivery_pin'),'customer PIN RPC must remain inaccessible');

ok(imageSnapshotSql.includes('ADD COLUMN IF NOT EXISTS item_image text'),'order items must have an additive image snapshot field');
ok(imageSnapshotSql.includes('BEFORE INSERT ON public.order_items'),'future orders must snapshot image at insert time');
ok(!imageSnapshotSql.includes('UPDATE public.order_items oi'),'migration must not rewrite locked historical order items');

console.log(JSON.stringify({checks,failures:0,scope:'Rider photo-only pickup and delivery proof, compact actions, state-machine and customer PIN removal'}));
