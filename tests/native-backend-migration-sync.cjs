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
console.log('Native backend migration mirror: PASS');
