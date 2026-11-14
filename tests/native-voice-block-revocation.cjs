// Isolated PostgreSQL authorization fixtures; no Production users/orders/calls are written.
const assert = require('assert');
const fs = require('fs');
const { PGlite } = require('@electric-sql/pglite');
const migrationPath = 'supabase/migrations/20261012121736_native_voice_block_revocation.sql';
const migration = fs.readFileSync(migrationPath, 'utf8');
const baseline = fs.readFileSync('ops/native-voice-before-block-revocation-20261012.sql', 'utf8');
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');

(async () => {
  const db = new PGlite();
  try {
    await db.exec(`
      CREATE ROLE anon; CREATE ROLE authenticated;
      CREATE SCHEMA auth; CREATE SCHEMA qg_private;
      CREATE TABLE public.users(id uuid PRIMARY KEY,auth_user_id uuid UNIQUE,name text,role text,status text);
      CREATE TABLE public.user_active_sessions(user_id uuid PRIMARY KEY,session_id uuid,revoked_at timestamptz);
      CREATE TABLE public.rider_profiles(id uuid PRIMARY KEY,user_id uuid,status text);
      CREATE TABLE public.shop_profiles(id uuid PRIMARY KEY,user_id uuid,status text);
      CREATE TABLE public.orders(id uuid PRIMARY KEY,customer_id uuid,rider_id uuid,shop_id uuid,status text,order_number text);
      CREATE TABLE public.qg_user_blocks(blocker_user_id uuid,blocked_user_id uuid,PRIMARY KEY(blocker_user_id,blocked_user_id));
      CREATE TABLE public.notifications(id uuid,user_id uuid,title text,message text,type text,reference_id uuid);
      CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$
        SELECT nullif(current_setting('test.auth_uid',true),'')::uuid $$;
      CREATE FUNCTION public.get_my_user_id() RETURNS uuid LANGUAGE sql STABLE AS $$
        SELECT id FROM public.users WHERE auth_user_id=auth.uid() $$;
      CREATE FUNCTION public.check_active_session(p_session_id uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
        SELECT EXISTS(SELECT 1 FROM public.user_active_sessions WHERE user_id=auth.uid() AND session_id=p_session_id AND revoked_at IS NULL) $$;
    `);
    // Retain actual table constraints/defaults from the existing Voice implementation.
    const original = fs.readFileSync('ops/native-voice-call-schema-proposal-20261010.sql','utf8');
    await db.exec(original.match(/create table if not exists public\.qg_call_sessions \([\s\S]*?\n\);/i)[0]);
    await db.exec(`CREATE FUNCTION qg_private.qg_voice_call_json(p_call_id uuid) RETURNS jsonb LANGUAGE sql STABLE AS $$
      SELECT jsonb_build_object('id',id,'status',status) FROM public.qg_call_sessions WHERE id=p_call_id $$;`);
    await db.exec(baseline);
    // Mirror the inspected Production client grants before replacing functions.
    for (const signature of ['public.qg_call_start(uuid,text,uuid)','public.qg_call_active(uuid,uuid)',
      'public.qg_call_ice_config(uuid,uuid)','qg_private.qg_voice_realtime_allowed(text)']) {
      await db.exec(`REVOKE ALL ON FUNCTION ${signature} FROM PUBLIC,anon; GRANT EXECUTE ON FUNCTION ${signature} TO authenticated;`);
    }
    for (const n of [1,2,3,4]) {
      await db.query('INSERT INTO public.users VALUES($1,$2,$3,$4,$5)',[id(n),id(100+n),'fixture '+n,n===2?'rider':'customer','active']);
      await db.query('INSERT INTO public.user_active_sessions VALUES($1,$2,NULL)',[id(100+n),id(200+n)]);
    }
    await db.query('INSERT INTO public.rider_profiles VALUES($1,$2,$3)',[id(302),id(2),'active']);
    for (const n of [10,11,12]) await db.query('INSERT INTO public.orders VALUES($1,$2,$3,NULL,$4,$5)',[id(n),id(1),id(302),'in_progress','QT-0010']);
    const call = async (n,order,caller,callee,status) => db.query(`INSERT INTO public.qg_call_sessions
      (id,order_id,caller_user_id,callee_user_id,caller_session_id,callee_session_id,target,status,topic,ended_at)
      VALUES($1,$2,$3,$4,$5,$6,'customer',$7,$8,CASE WHEN $7='ended' THEN now()-interval '1 hour' ELSE NULL END)`,
      [id(n),id(order),id(caller),id(callee),id(200+caller),status==='ringing'?null:id(200+callee),status,'qg-call:'+id(n)]);
    await call(401,10,1,2,'accepted'); await call(402,12,2,1,'ringing');
    await call(403,10,1,2,'ended'); await call(404,11,3,4,'accepted');
    const actor = async n => db.query("SELECT set_config('test.auth_uid',$1,false)",[id(100+n)]);
    const read = async (sql,params=[]) => (await db.query(sql,params)).rows;
    const rejected = async (sql,params=[]) => {
      await assert.rejects(db.query(sql,params), e => e.code==='42501');
    };
    await actor(2);
    assert.equal((await read('SELECT qg_private.qg_voice_realtime_allowed($1) AS allowed',['qg-call:'+id(401)]))[0].allowed,true);
    assert.equal((await read('SELECT public.qg_call_ice_config($1,$2) AS value',[id(401),id(202)]))[0].value.authorized,true);
    const historyAt = (await read('SELECT ended_at FROM public.qg_call_sessions WHERE id=$1',[id(403)]))[0].ended_at;

    // Existing blocked calls are reconciled when upgrading; unrelated/history survive.
    await db.query('INSERT INTO public.qg_user_blocks VALUES($1,$2)',[id(1),id(2)]);
    await db.exec(migration);
    assert.deepEqual((await read('SELECT status FROM public.qg_call_sessions WHERE id IN ($1,$2) ORDER BY id',[id(401),id(402)])).map(x=>x.status),['ended','ended']);
    assert.equal((await read('SELECT status FROM public.qg_call_sessions WHERE id=$1',[id(404)]))[0].status,'accepted');
    assert.deepEqual((await read('SELECT ended_at FROM public.qg_call_sessions WHERE id=$1',[id(403)]))[0].ended_at,historyAt);
    assert.equal((await read('SELECT qg_private.qg_voice_realtime_allowed($1) AS allowed',['qg-call:'+id(401)]))[0].allowed,false);
    await rejected('SELECT public.qg_call_ice_config($1,$2)',[id(401),id(202)]);
    assert.equal((await read('SELECT public.qg_call_active($1,$2) AS value',[id(10),id(202)]))[0].value.none,true);
    await rejected('SELECT public.qg_call_start($1,$2,$3)',[id(10),'customer',id(202)]);

    // Removing a block must not revive a terminated call, but a new call may start.
    await db.exec('DELETE FROM public.qg_user_blocks');
    const fresh = (await read('SELECT public.qg_call_start($1,$2,$3) AS value',[id(10),'customer',id(202)]))[0].value;
    assert.equal(fresh.status,'ringing');
    assert.equal((await read('SELECT status FROM public.qg_call_sessions WHERE id=$1',[id(401)]))[0].status,'ended');
    await db.query('UPDATE public.qg_call_sessions SET status=$1,callee_session_id=$2 WHERE id=$3',['accepted',id(201),fresh.id]);
    await call(406,12,1,2,'ringing');
    // Reverse-direction blocks revoke immediately across all orders and both statuses.
    await db.query('INSERT INTO public.qg_user_blocks VALUES($1,$2)',[id(2),id(1)]);
    assert.equal((await read('SELECT status FROM public.qg_call_sessions WHERE id=$1',[fresh.id]))[0].status,'ended');
    assert.equal((await read('SELECT status FROM public.qg_call_sessions WHERE id=$1',[id(406)]))[0].status,'ended');
    await rejected('SELECT public.qg_call_start($1,$2,$3)',[id(10),'customer',id(202)]);

    // Even if a stale live row appears, signaling/ICE and active lookup fail closed.
    await call(405,10,1,2,'accepted');
    assert.equal((await read('SELECT qg_private.qg_voice_realtime_allowed($1) AS allowed',['qg-call:'+id(405)]))[0].allowed,false);
    await rejected('SELECT public.qg_call_ice_config($1,$2)',[id(405),id(202)]);
    assert.equal((await read('SELECT public.qg_call_active($1,$2) AS value',[id(10),id(202)]))[0].value.none,true);
    assert.equal((await read('SELECT status FROM public.qg_call_sessions WHERE id=$1',[id(405)]))[0].status,'ended');

    // Existing order/session authorization remains intact for an unblocked live call.
    await actor(3);
    assert.equal((await read('SELECT qg_private.qg_voice_realtime_allowed($1) AS allowed',['qg-call:'+id(404)]))[0].allowed,true);
    await db.query('UPDATE public.user_active_sessions SET revoked_at=now() WHERE user_id=$1',[id(104)]);
    assert.equal((await read('SELECT qg_private.qg_voice_realtime_allowed($1) AS allowed',['qg-call:'+id(404)]))[0].allowed,false);
    await rejected('SELECT public.qg_call_ice_config($1,$2)',[id(404),id(203)]);
    await db.query('UPDATE public.user_active_sessions SET revoked_at=NULL WHERE user_id=$1',[id(104)]);
    await rejected('SELECT public.qg_call_ice_config($1,$2)',[id(404),id(202)]);
    await db.query('UPDATE public.orders SET status=$1 WHERE id=$2',['completed',id(11)]);
    assert.equal((await read('SELECT qg_private.qg_voice_realtime_allowed($1) AS allowed',['qg-call:'+id(404)]))[0].allowed,false);
    await rejected('SELECT public.qg_call_ice_config($1,$2)',[id(404),id(203)]);

    for (const signature of ['qg_private.qg_voice_lock_pair(uuid,uuid)','qg_private.qg_voice_revoke_on_block()']) {
      const permissions = (await read(`SELECT has_function_privilege('anon',$1,'EXECUTE') AS anon,
        has_function_privilege('authenticated',$1,'EXECUTE') AS authenticated`,[signature]))[0];
      assert.deepEqual(permissions,{anon:false,authenticated:false});
    }
    // Check the same pair lock is taken before call row updates and by the block trigger.
    const start = migration.slice(migration.indexOf('CREATE OR REPLACE FUNCTION public.qg_call_start'));
    assert(start.indexOf('perform qg_private.qg_voice_lock_pair') < start.indexOf('update public.qg_call_sessions'));
    assert.equal((await read(`SELECT hashtextextended('qg-voice-pair:'||least($1::uuid,$2::uuid)::text||':'||greatest($1::uuid,$2::uuid)::text,0)
      =hashtextextended('qg-voice-pair:'||least($2::uuid,$1::uuid)::text||':'||greatest($2::uuid,$1::uuid)::text,0) AS same`,[id(1),id(2)]))[0].same,true);
    await db.exec(migration); // Additive migration is repeatable; no duplicate trigger/history loss.
    assert.equal((await read("SELECT count(*)::int AS count FROM pg_trigger WHERE tgname='qg_voice_revoke_on_block'"))[0].count,1);
    const activeDefinition = migration.match(/CREATE OR REPLACE FUNCTION public\.qg_call_active\([\s\S]*?\n\$function\$;/)[0];
    await db.exec(activeDefinition.replace('begin\n','begin\n  -- later implementation change\n'));
    await assert.rejects(db.exec(migration),/Voice definition changed; re-read Production before migrating/);
    assert((await read("SELECT pg_get_functiondef('public.qg_call_active(uuid,uuid)'::regprocedure) AS definition"))[0].definition.includes('later implementation change'));
    console.log('Native Voice PostgreSQL block revocation, preserved authorization and pair-lock contracts: PASS (isolated fixtures; no physical certification)');
  } finally { await db.close(); }
})().catch(error=>{console.error(error);process.exit(1)});
