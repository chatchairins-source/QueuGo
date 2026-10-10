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
has(edge,/if\(Object\.prototype\.hasOwnProperty\.call\(input,'sessionId'\)\)/,'legacy fallback must require an omitted sessionId');
has(edge,/typeof input\.sessionId!=='string'\|\|!uuid\(sessionId\)/,'explicit invalid sessionId must fail closed');
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

// Exercise the actual registration authorization block in isolation. No live
// Production rows or tokens are created by this regression.
async function registrationAuthorizationRegression(){
  const start=edge.indexOf("      const nativeToken=",edge.indexOf("if(input.action==='subscribe-native')"));
  const end=edge.indexOf('      // Native FCM registration',start);
  assert(start>=0&&end>start,'registration authorization block missing');
  const body=edge.slice(start,end).replace('legacySessions.data![0]','legacySessions.data[0]');
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  const run=new AsyncFunction('input','admin','auth','allowedPlatforms','uuid','respond',body+'\nreturn {sessionId};');
  const session='11111111-1111-4111-8111-111111111111';
  const other='22222222-2222-4222-8222-222222222222';
  const actor='33333333-3333-4333-8333-333333333333';
  async function check(fields,rows,error=null){
    const filters=[];
    let reads=0;
    const query={
      select(){return this},eq(k,v){filters.push([k,v]);return this},
      is(k,v){filters.push([k,v]);return this},order(){return this},
      async limit(n){assert.equal(n,2);reads++;return {data:rows,error}},
      async maybeSingle(){reads++;return {data:rows.find(r=>r.session_id===fields.sessionId)||null,error}}
    };
    const result=await run({token:'registration-unit-token',platform:'android',...fields},
      {from(table){assert.equal(table,'user_active_sessions');return query}},
      {data:{user:{id:actor}}},new Set(['android']),
      value=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value),
      (payload,status)=>({payload,status}));
    if(reads){assert(filters.some(([k,v])=>k==='user_id'&&v===actor));assert(filters.some(([k,v])=>k==='revoked_at'&&v===null));}
    return {result,reads};
  }
  for(const value of ['',null,false,17,{},'invalid','   ']){
    const {result,reads}=await check({sessionId:value},[{session_id:session}]);
    assert.equal(result.status,400,'explicit malformed session must reject');
    assert.equal(reads,0,'explicit malformed session must never derive');
  }
  assert.equal((await check({},[{session_id:session}])).result.sessionId,session);
  for(const rows of [[],[{session_id:session},{session_id:other}],[{session_id:'invalid'}]]){
    assert.equal((await check({},rows)).result.status,403,'ambiguous/missing legacy session must reject');
  }
  assert.equal((await check({sessionId:session},[{session_id:session},{session_id:other}])).result.sessionId,session);
  assert.equal((await check({sessionId:other},[{session_id:session}])).result.status,403,'foreign/revoked session must reject');
  await assert.rejects(check({},[],new Error('session read unavailable')),/session read unavailable/);
  console.log('Native push session binding contract and authorization regression: PASS');
}
registrationAuthorizationRegression().catch(error=>{console.error(error);process.exitCode=1});
