const fs=require('fs');
const assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
assert.strictEqual(
  read('supabase/migrations/20261010073956_native_voice_call_backend_v1.sql'),
  read('ops/native-voice-call-schema-proposal-20261010.sql'),
  'deployed voice migration mirror must match gated source exactly'
);
assert.strictEqual(
  read('supabase/migrations/20261010074009_native_push_session_binding_v1.sql'),
  read('ops/native-push-session-binding-proposal-20261010.sql'),
  'deployed push migration mirror must match gated source exactly'
);
console.log('Native backend migration mirror: PASS');
