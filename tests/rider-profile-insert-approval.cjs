'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');

(async () => {
  const db = new PGlite();
  await db.exec(`
    create role authenticated; create role anon;
    create schema auth;
    create function auth.uid() returns uuid language sql as
      $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    create table public.users(id uuid primary key,auth_user_id uuid,role text,status text);
    create table public.rider_profiles(id uuid primary key,user_id uuid,status text);
    create function public.is_active_admin() returns boolean language sql as
      $$select exists(select 1 from public.users where auth_user_id=auth.uid() and role='admin' and status='active')$$;
    grant usage on schema auth to authenticated;
    grant select on public.users to authenticated;
    grant select,insert on public.rider_profiles to authenticated;
  `);
  const ids = [1,2,3,4].map(n => '00000000-0000-0000-0000-' + String(n).padStart(12,'0'));
  for (let i=0;i<ids.length;i++) await db.query(
    'insert into users values($1,$1,$2,$3)', [ids[i],['rider','rider','customer','admin'][i],i===0?'pending':'active']);
  await db.exec(fs.readFileSync(path.join(__dirname,'../supabase/migrations/20261009150531_guard_rider_profile_insert_approval.sql'),'utf8'));
  await db.exec('set role authenticated');
  let checks=0;
  async function identity(i) { await db.query("select set_config('request.jwt.claim.sub',$1,false)",[ids[i]]); }
  async function insert(i,status) { return db.query('insert into rider_profiles values(gen_random_uuid(),$1,$2)',[ids[i],status]); }
  async function denied(i,status) {
    await assert.rejects(insert(i,status), e => e.code==='42501'); checks++;
  }
  await identity(0);
  await denied(0,'active'); await denied(0,'suspended'); await denied(1,'active');
  await insert(0,'pending'); checks++;
  await identity(2); await denied(2,'pending'); await denied(2,'active');
  await identity(1); await insert(1,'active'); checks++;
  await denied(1,'pending');
  await identity(3); await insert(0,'active'); checks++;
  await db.exec('reset role');
  const acl = await db.query("select has_function_privilege('anon','public.qg_guard_rider_profile_insert()','EXECUTE') a,has_function_privilege('authenticated','public.qg_guard_rider_profile_insert()','EXECUTE') b");
  assert.equal(acl.rows[0].a,false); assert.equal(acl.rows[0].b,false); checks+=2;
  await db.close();
  console.log(JSON.stringify({checks,failures:0,scope:'isolated INSERT role/ownership/approval and internal trigger grants'}));
})().catch(e=>{ console.error(e); process.exitCode=1; });
