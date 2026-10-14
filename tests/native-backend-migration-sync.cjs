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
  "revoke all on function public.qg_rider_set_emergency_contact(text,text) from public, anon",
  "contact_phone ~ '^\\\\+?[0-9]{8,15}]){
  assert.ok(emergencyContactMigration.includes(needle),'Rider emergency contact migration missing contract: '+needle);
}
console.log('Native backend migration mirror: PASS');
"
]){
  assert.ok(emergencyContactMigration.includes(needle),'Rider emergency contact migration missing contract: '+needle);
}
console.log('Native backend migration mirror: PASS');
