'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { PGlite } = require('@electric-sql/pglite');
const sourcePath = 'supabase/migrations/20261014234500_rider_continuous_dispatch_phase1.sql';
const source = fs.readFileSync(sourcePath, 'utf8');
const start = source.indexOf('create or replace function public.qg_rider_dispatch_preference(');
const end = source.indexOf('\n\n-- Only privileged', start);
assert(start >= 0 && end > start, 'existing authoritative function/grants must be extractable');
const migration = source.slice(start, end);
(async () => {
  const db = new PGlite();
  let checks = 0;
  const eq = (a, b, label) => { assert.deepEqual(a, b, label); checks++; };
  const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');
  try {
    await db.exec(`
      create role anon; create role authenticated;
      create schema auth; create schema qg_private;
      create function auth.uid() returns uuid language sql stable as
        $$select nullif(current_setting('test.uid',true),'')::uuid$$;
      create table public.users(id uuid primary key,auth_user_id uuid,role text,status text);
      create table public.rider_profiles(id uuid primary key,user_id uuid,status text);
      create table qg_private.rider_dispatch_preferences(
        rider_id uuid primary key,auto_accept boolean not null default false,
        stop_after_work boolean not null default false,
        ready_until timestamptz not null default '-infinity',updated_at timestamptz not null default now(),
        services text[] not null default array['food','grocery','market'],
        bundle_enabled boolean not null default false);
      alter table qg_private.rider_dispatch_preferences enable row level security;
      revoke all on qg_private.rider_dispatch_preferences from public,anon,authenticated;
      grant usage on schema public,auth to anon,authenticated;
      insert into public.users values('${id(1)}','${id(101)}','rider','active'),
        ('${id(2)}','${id(102)}','rider','active');
      insert into public.rider_profiles values('${id(11)}','${id(1)}','active'),
        ('${id(12)}','${id(2)}','active');
      insert into qg_private.rider_dispatch_preferences(rider_id,services,bundle_enabled)
        values('${id(11)}',array['market'],true);
    `);
    await db.exec(migration);
    const as = async who => {
      await db.exec('reset role');
      await db.query("select set_config('test.uid',$1,false)", [who ? id(who) : '']);
      await db.exec('set role authenticated');
    };
    const call = (auto, stop, ready) => db.query(
      'select public.qg_rider_dispatch_preference($1,$2,$3) result', [auto, stop, ready]);
    await as(101);
    eq((await call(true,false,true)).rows[0].result,{acknowledged:true,lease_seconds:60},'exact Android acknowledgement');
    await db.exec('reset role');
    const row = async () => (await db.query('select *, ready_until > now() as ready, ready_until <= now()+interval \'60 seconds\' as bounded from qg_private.rider_dispatch_preferences where rider_id=$1',[id(11)])).rows[0];
    let saved = await row();
    eq(saved.services,['market'],'existing Web services preserved');
    eq(saved.bundle_enabled,true,'existing Web bundle setting preserved');
    eq(saved.ready,true,'fresh ready lease'); eq(saved.bounded,true,'lease bounded to 60 seconds');
    await as(101); await call(true,true,true); await db.exec('reset role');
    eq((await row()).ready,false,'stop-after-work wins over ready');
    await as(101); await call(false,false,false); await db.exec('reset role');
    saved=await row(); eq(saved.ready,false,'not-ready expires lease'); eq(saved.auto_accept,false,'manual choice preserved');
    await as(102); await call(true,false,true); await db.exec('reset role');
    eq((await db.query('select count(*)::int n from qg_private.rider_dispatch_preferences')).rows[0].n,2,'one row per Rider');
    eq((await row()).auto_accept,false,'another Rider cannot change first Rider');
    for (const args of [[null,false,true],[true,null,true],[true,false,null]]) {
      await as(101); await assert.rejects(call(...args),/active rider and explicit preference/); checks++;
    }
    await as(null); await assert.rejects(call(true,false,true),/active rider/); checks++;
    await db.exec(`reset role; update public.users set status='suspended' where id='${id(1)}'`);
    await as(101); await assert.rejects(call(true,false,true),/active rider/); checks++;
    await db.exec(`reset role; update public.users set status='active',role='customer' where id='${id(1)}'`);
    await as(101); await assert.rejects(call(true,false,true),/active rider/); checks++;
    await db.exec(`reset role; update public.users set role='rider' where id='${id(1)}'; update public.rider_profiles set status='suspended' where id='${id(11)}'`);
    await as(101); await assert.rejects(call(true,false,true),/active rider/); checks++;
    await db.exec('reset role'); await db.exec(migration);
    eq((await db.query("select has_function_privilege('anon','public.qg_rider_dispatch_preference(boolean,boolean,boolean)','EXECUTE') v")).rows[0].v,false,'anon denied after replay');
    eq((await row()).services,['market'],'migration replay preserves services');
    await db.exec('set role anon'); await assert.rejects(call(true,false,true),/permission denied/); checks++;
    console.log(JSON.stringify({checks,failures:0,scope:'isolated original dispatch-preference function with newer Web service/bundle fields'}));
  } finally { await db.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
