// Rider vehicle inspection release gate exact-head checkpoint.
const fs=require('fs');
const assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const voiceSource=read('ops/native-voice-call-schema-proposal-20261010.sql');
const pushSource=read('ops/native-push-session-binding-proposal-20261010.sql');

for(const path of [
  'supabase/migrations/20261010073956_native_voice_call_backend_v1.sql',
  'supabase/migrations/20261010074125_native_voice_call_backend_v1.sql'
]){
  assert.strictEqual(read(path),voiceSource,path+' must match gated voice source exactly');
}
for(const path of [
  'supabase/migrations/20261010074009_native_push_session_binding_v1.sql',
  'supabase/migrations/20261010074132_native_push_session_binding_v1.sql'
]){
  assert.strictEqual(read(path),pushSource,path+' must match gated push source exactly');
}
assert.strictEqual(
  read('supabase/migrations/20261012124318_native_voice_block_revocation.sql'),
  read('supabase/migrations/20261012121736_native_voice_block_revocation.sql'),
  'Production Voice block-revocation history must exactly match the gated CLI source'
);
// Preserve the actual remote version alongside the CLI-created idempotent source.
const complimentMigration=read('supabase/migrations/20261014061234_rider_compliments_v1.sql');
for(const needle of [
  'qg_private.rider_review_compliments',
  'qg_customer_save_rider_compliments',
  'qg_customer_rider_compliments',
  'qg_rider_compliment_summary',
  "revoke all on function public.qg_rider_compliment_summary() from public, anon",
  "cardinality(compliments) between 1 and 3"
]){
  assert.ok(complimentMigration.includes(needle),'Rider compliments migration missing contract: '+needle);
}
const riderProfilePhotoMigration=read('supabase/migrations/20261014062708_rider_public_profile_photo_v1.sql');
for(const needle of [
  "'rider-profile'",
  'rider_profile_owner_insert',
  'rider_profile_owner_update',
  'qg_rider_set_profile_photo',
  "v_expected_path := v_uid::text || '/profile'",
  "revoke all on function public.qg_rider_set_profile_photo(text) from public, anon"
]){
  assert.ok(riderProfilePhotoMigration.includes(needle),'Rider profile photo migration missing contract: '+needle);
}
const reverificationMigration=read('supabase/migrations/20261014062750_rider_reverification_v1.sql');
for(const needle of [
  'qg_private.rider_reverification_requests',
  'qg_rider_reverification_status',
  'qg_rider_submit_reverification',
  'qg_admin_request_rider_reverification',
  'qg_admin_review_rider_reverification',
  "revoke all on function public.qg_rider_reverification_status() from public, anon",
  "revoke all on function public.qg_rider_submit_reverification(text) from public, anon"
]){
  assert.ok(reverificationMigration.includes(needle),'Rider reverification migration missing contract: '+needle);
}
const adminReverificationQueueMigration=read('supabase/migrations/20261014063649_admin_rider_reverification_queue_v1.sql');
for(const needle of [
  'qg_admin_rider_reverification_queue',
  'admin required',
  'public.is_active_admin()',
  "revoke all on function public.qg_admin_rider_reverification_queue() from public, anon"
]){
  assert.ok(adminReverificationQueueMigration.includes(needle),'Admin Rider reverification queue migration missing contract: '+needle);
}
const reverificationOnlineGateMigration=read('supabase/migrations/20261014063832_rider_reverification_online_gate_v1.sql');
for(const needle of [
  'qg_guard_rider_reverification_online',
  "rr.status in ('required','pending','rejected')",
  'rider identity reverification required'
]){
  assert.ok(reverificationOnlineGateMigration.includes(needle),'Rider reverification online gate migration missing contract: '+needle);
}
const reverificationReasonMigration=read('supabase/migrations/20261014064554_rider_reverification_reason_required_v1.sql');
for(const needle of [
  'v_reason := nullif(btrim(p_reason)',
  'reverification reason required',
  'reason=v_reason',
  "values(p_rider_id,'required',v_reason)"
]){
  assert.ok(reverificationReasonMigration.includes(needle),'Rider reverification reason migration missing contract: '+needle);
}
const riderMissionsMigration=read('supabase/migrations/20261014064750_rider_missions_v1.sql');
for(const needle of [
  'qg_private.rider_missions',
  'qg_rider_missions',
  'qg_admin_rider_missions',
  'qg_admin_create_rider_mission',
  'qg_admin_set_rider_mission_active',
  "count(distinct coalesce(o.market_order_id::text,o.id::text))",
  "revoke all on function public.qg_rider_missions() from public, anon",
  "revoke all on table qg_private.rider_missions from public, anon, authenticated"
]){
  assert.ok(riderMissionsMigration.includes(needle),'Rider missions migration missing contract: '+needle);
}
const emergencyContactMigration=read('supabase/migrations/20261014065058_rider_emergency_contact_v1.sql');
for(const needle of [
  'qg_private.rider_emergency_contacts',
  'qg_rider_emergency_contact',
  'qg_rider_set_emergency_contact',
  "revoke all on table qg_private.rider_emergency_contacts from public, anon, authenticated",
  "revoke all on function public.qg_rider_emergency_contact() from public, anon",
  "revoke all on function public.qg_rider_set_emergency_contact(text,text) from public, anon"
]){
  assert.ok(emergencyContactMigration.includes(needle),'Rider emergency contact v1 missing contract: '+needle);
}
const emergencyPhoneFix=read('supabase/migrations/20261014070308_rider_emergency_contact_phone_regex_fix_v2.sql');
for(const needle of [
  'rider_emergency_contact_phone_format',
  "contact_phone ~ '^[+]?[0-9]{8,15}$'",
  "v_phone !~ '^[+]?[0-9]{8,15}$'",
  "revoke all on function qg_private.rider_set_emergency_contact(text,text) from public, anon"
]){
  assert.ok(emergencyPhoneFix.includes(needle),'Rider emergency contact v2 missing contract: '+needle);
}
const offerPerformanceMigration=read('supabase/migrations/20261014071245_rider_offer_performance_v1.sql');
for(const needle of [
  'qg_private.rider_offer_performance',
  'qg_rider_offer_performance',
  'qg_rider_offer_history',
  "v_days not in (1, 7, 30)",
  "revoke all on function public.qg_rider_offer_performance(integer) from public, anon"
]){
  assert.ok(offerPerformanceMigration.includes(needle),'Rider offer performance migration missing contract: '+needle);
}
const destinationMigration=read('supabase/migrations/20261014071752_rider_my_destination_soft_dispatch_v1.sql');
for(const needle of [
  'qg_private.rider_destination_preferences',
  'qg_rider_destination_preference',
  'qg_rider_set_destination_preference',
  'left join qg_private.rider_destination_preferences',
  "now()+interval '30 seconds'",
  'qg_private.approx_distance_km',
  ') >= 1.0',
  "revoke all on table qg_private.rider_destination_preferences from public, anon, authenticated",
  "revoke all on function public.qg_rider_destination_preference() from public, anon"
]){
  assert.ok(destinationMigration.includes(needle),'Rider My Destination migration missing contract: '+needle);
}
const fatigueMigration=read('supabase/migrations/20261014073631_rider_fatigue_status_v1.sql');
for(const needle of [
  'qg_private.rider_fatigue_status',
  'qg_rider_fatigue_status',
  'qg_rider_presence_events',
  "'strong_rest_recommended'",
  "'rest_recommended'",
  "'medical_detection', false",
  "revoke all on function public.qg_rider_fatigue_status() from public, anon"
]){
  assert.ok(fatigueMigration.includes(needle),'Rider fatigue migration missing contract: '+needle);
}
const mapFeedbackMigration=read('supabase/migrations/20261014074523_rider_map_feedback_v1.sql');
for(const needle of [
  'qg_private.rider_map_feedback',
  'qg_rider_submit_map_feedback',
  'qg_admin_rider_map_feedback_queue',
  'qg_admin_review_rider_map_feedback',
  "interval '1 hour'",
  ") >= 5",
  "'accepted_for_review'",
  "revoke all on table qg_private.rider_map_feedback from public, anon, authenticated",
  "revoke all on function public.qg_rider_submit_map_feedback(text,double precision,double precision,text)",
  "revoke all on function public.qg_admin_rider_map_feedback_queue(text,integer)",
  "revoke all on function public.qg_admin_review_rider_map_feedback(uuid,text,text)"
]){
  assert.ok(mapFeedbackMigration.includes(needle),'Rider map feedback migration missing contract: '+needle);
}
const supplyPlanningMigration=read('supabase/migrations/20261014075257_rider_supply_planning_v1.sql');
for(const needle of [
  'qg_private.rider_supply_plan',
  'qg_rider_supply_plan',
  "interval '28 days'",
  "v_sample >= 20",
  "'forecast',false",
  "'basis','historical_orders'",
  "revoke all on function public.qg_rider_supply_plan() from public, anon"
]){
  assert.ok(supplyPlanningMigration.includes(needle),'Rider supply planning migration missing contract: '+needle);
}
const vehicleInspectionMigration=read('supabase/migrations/20261014080810_rider_vehicle_inspection_v1.sql');
for(const needle of [
  'qg_private.rider_vehicle_inspection_requests',
  'qg_rider_vehicle_inspection_status',
  'qg_rider_submit_vehicle_inspection',
  'qg_admin_rider_vehicle_inspection_queue',
  'qg_admin_request_rider_vehicle_inspection',
  'qg_admin_review_rider_vehicle_inspection',
  'qg_guard_rider_vehicle_inspection_online',
  "ri.status in ('required','pending','rejected')",
  'rider vehicle inspection required',
  "revoke all on function public.qg_rider_vehicle_inspection_status() from public, anon"
]){
  assert.ok(vehicleInspectionMigration.includes(needle),'Rider vehicle inspection migration missing contract: '+needle);
}
const vehicleInspectionOfflineRequest=read('supabase/migrations/20261014081405_rider_vehicle_inspection_offline_request_v2.sql');
for(const needle of [
  'rider must be offline before vehicle inspection request',
  'rider has active delivery',
  "o.status not in ('completed','cancelled','no_rider_available')",
  "revoke all on function qg_private.admin_request_rider_vehicle_inspection(uuid,text) from public, anon"
]){
  assert.ok(vehicleInspectionOfflineRequest.includes(needle),'Rider vehicle inspection offline-request guard missing contract: '+needle);
}
const multipleVehiclesMigration=read('supabase/migrations/20261014081916_rider_multiple_vehicles_v1.sql');
for(const needle of [
  'qg_private.rider_vehicles',
  'qg_rider_vehicle_list',
  'qg_rider_add_vehicle',
  'qg_rider_select_vehicle',
  'qg_admin_rider_vehicle_queue',
  'qg_admin_review_rider_vehicle',
  'qg_guard_rider_selected_vehicle_online',
  'rider must be offline before switching vehicle',
  'rider has active delivery',
  "revoke all on table qg_private.rider_vehicles from public, anon, authenticated",
  "revoke all on function public.qg_rider_vehicle_list() from public, anon"
]){
  assert.ok(multipleVehiclesMigration.includes(needle),'Rider multiple vehicles v1 missing contract: '+needle);
}
const multipleVehiclesCompat=read('supabase/migrations/20261014082029_rider_multiple_vehicles_legacy_compat_v2.sql');
for(const needle of [
  'legacy_seed boolean not null default false',
  'and status<>\'rejected\'',
  'and not v_existing.legacy_seed',
  'and not v.legacy_seed',
  "'legacy_seed',v.legacy_seed"
]){
  assert.ok(multipleVehiclesCompat.includes(needle),'Rider multiple vehicles v2 missing compatibility contract: '+needle);
}
const multipleVehiclesLegacyGuard=read('supabase/migrations/20261014082657_rider_multiple_vehicles_selected_legacy_guard_v3.sql');
for(const needle of [
  'v_existing.legacy_seed and v_existing.is_selected',
  'rider must be offline before verifying selected vehicle',
  'rider has active delivery'
]){
  assert.ok(multipleVehiclesLegacyGuard.includes(needle),'Rider multiple vehicles v3 missing selected legacy guard: '+needle);
}
console.log('Native backend migration mirror: PASS');
