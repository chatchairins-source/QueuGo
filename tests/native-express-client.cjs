const fs=require('node:fs'),assert=require('node:assert/strict');
const sql=fs.readFileSync('supabase/migrations/20261018034500_express_state_client.sql','utf8');
const model=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
let checks=0;const ok=(value,msg)=>{assert(value,msg);checks++;};
ok(sql.includes("create or replace function public.queuego_express_state"),'Express state RPC missing');
ok(sql.includes("o.fulfillment_vertical='express'"),'Express state must be vertical-scoped');
ok(sql.includes("(v_role='customer' and o.customer_id=v_user)"),'Customer Express state must be owner-scoped');
ok(sql.includes("(v_role='rider' and o.rider_id=v_rider)"),'Rider Express state must be assignment-scoped');
ok(sql.includes("revoke all on function public.queuego_express_state(uuid) from public,anon"),'Express state must reject anonymous callers');
ok(model.includes('val isExpress: Boolean get() = fulfillmentVertical == "express"'),'RiderJob Express discriminator missing');
for(const field of ['expressDurationMinutes','expressDistanceKm','expressItemType','expressWeightKg','expressSize','expressVehicleType','expressSenderName','expressReceiverName'])
  ok(model.includes('val '+field),field+' missing from RiderJob');
for(const field of ['express_distance_km','express_duration_minutes','express_item_type','express_weight_kg','express_vehicle_type','express_sender_name','express_receiver_name'])
  ok(api.includes(field),'Rider query/parser missing '+field);
ok(api.includes('suspend fun expressAction(')&&api.includes('"express"'),'Rider Express action must reuse actionOnce express kind');
ok(api.includes('qg_express_pickup_with_photo'),'Rider Express pickup proof RPC missing');
ok(api.includes('qg_express_complete_with_photo'),'Rider Express delivery proof RPC missing');
ok(api.includes('mutationProofExists(auth, "qg_pickup_proofs"'),'Express pickup recovery must verify existing proof');
ok(api.includes('mutationProofExists(auth, "qg_delivery_proofs"'),'Express completion recovery must verify existing proof');
console.log(JSON.stringify({checks,failures:0,scope:'Express authenticated state + Native Rider model/action/proof client contract; no Production mutation'}));
