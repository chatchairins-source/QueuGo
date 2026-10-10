const fs=require('fs');
const assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const shared=read('native-android/shared/src/main/java/com/queuego/shared/NativePush.kt');
const rider=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt');
const edge=read('supabase/functions/queuego-push/index.ts');
const sql=read('ops/native-push-session-binding-proposal-20261010.sql');

function has(text,re,msg){assert(re.test(text),msg)}

has(shared,/\.put\("sessionId", auth\.session\.sessionId\)/,'shared native push must send sessionId');
has(rider,/\.put\("sessionId", auth\.session\.sessionId\)/,'Rider native push must send sessionId');

has(edge,/let sessionId=String\(input\.sessionId\|\|''\)\.trim\(\)/,'Edge must parse native push sessionId');
has(edge,/if\(uuid\(sessionId\)\)/,'Edge must validate explicit native sessionId when provided');
has(edge,/legacySessions[\s\S]*limit\(2\)[\s\S]*length!==1[\s\S]*active session required/s,
  'legacy native clients may derive a session only when exactly one live session exists');
has(edge,/user_active_sessions[\s\S]*eq\('user_id',auth\.data\.user\.id\)[\s\S]*eq\('session_id',sessionId\)[\s\S]*is\('revoked_at',null\)/,
  'Edge registration must require current active session');
has(edge,/session_id:sessionId/,'Edge must persist session binding on native token');
has(edge,/user_active_sessions[\s\S]*eq\('user_id',context\.user\.auth_user_id\)[\s\S]*eq\('session_id',t\.session_id\)[\s\S]*is\('revoked_at',null\)/,
  'Edge dispatch must revalidate native token session');
has(edge,/status=active\.error\|\|!active\.data\s*\?410/s,
  'stale native push session must be retired');

has(sql,/add column if not exists session_id uuid/i,'session_id additive column missing');
has(sql,/set enabled=false[\s\S]*session_id is null/i,'legacy unbound tokens must be disabled');
has(sql,/check \(not enabled or session_id is not null\)/i,'enabled token session constraint missing');
has(sql,/join public\.user_active_sessions a[\s\S]*a\.user_id=u\.auth_user_id[\s\S]*a\.session_id=t\.session_id[\s\S]*a\.revoked_at is null/i,
  'native enqueue must join live app session');
assert(!/drop table\s+public\.qg_native_push_tokens/i.test(sql),'push hardening must not replace the token table');

console.log('Native push session binding contract: PASS');
