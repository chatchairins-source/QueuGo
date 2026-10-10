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
console.log('Native backend migration mirror: PASS');
