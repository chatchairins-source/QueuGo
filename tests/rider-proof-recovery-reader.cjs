const fs=require('fs'),path=require('path'),assert=require('assert'),{PGlite}=require('@electric-sql/pglite');

(async()=>{
  let checks=0;
  const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++};
  const ok=(v,m)=>{assert.ok(v,m);checks++};
  const db=new PGlite();

  await db.exec(`
    CREATE ROLE anon;
    CREATE ROLE authenticated;
    CREATE SCHEMA auth;

    CREATE FUNCTION auth.uid() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;

    CREATE TABLE public.users(
      id uuid PRIMARY KEY,
      auth_user_id uuid,
      role text,
      status text
    );

    CREATE TABLE public.rider_profiles(
      id uuid PRIMARY KEY,
      user_id uuid NOT NULL
    );

    CREATE TABLE public.orders(
      id uuid PRIMARY KEY,
      rider_id uuid
    );

    CREATE TABLE public.qg_pickup_proofs(
      order_id uuid PRIMARY KEY,
      rider_id uuid NOT NULL,
      photo_path text NOT NULL
    );

    CREATE TABLE public.qg_delivery_proofs(
      order_id uuid PRIMARY KEY,
      rider_id uuid NOT NULL,
      photo_path text NOT NULL
    );

    ALTER TABLE public.qg_pickup_proofs ENABLE ROW LEVEL SECURITY;
    CREATE POLICY qg_server_only_no_client_access
      ON public.qg_pickup_proofs
      FOR ALL TO anon,authenticated
      USING (false) WITH CHECK (false);

    ALTER TABLE public.qg_delivery_proofs ENABLE ROW LEVEL SECURITY;
    CREATE POLICY qg_server_only_delivery_test
      ON public.qg_delivery_proofs
      FOR ALL TO anon,authenticated
      USING (false) WITH CHECK (false);

    GRANT USAGE ON SCHEMA public,auth TO anon,authenticated;
    GRANT SELECT ON public.qg_pickup_proofs,public.qg_delivery_proofs TO anon,authenticated;
  `);

  const migration=fs.readFileSync(
    path.resolve(__dirname,'../supabase/migrations/20261023001200_rider_proof_recovery_reader.sql'),
    'utf8'
  );
  const nativeApi=fs.readFileSync(
    path.resolve(__dirname,'../native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt'),
    'utf8'
  );

  await db.exec(migration);

  const riderA='30000000-0000-4000-8000-000000000001';
  const userA='31000000-0000-4000-8000-000000000001';
  const authA='32000000-0000-4000-8000-000000000001';
  const riderB='30000000-0000-4000-8000-000000000002';
  const userB='31000000-0000-4000-8000-000000000002';
  const authB='32000000-0000-4000-8000-000000000002';
  const order='40000000-0000-4000-8000-000000000001';

  await db.exec(`
    INSERT INTO public.users(id,auth_user_id,role,status) VALUES
      ('${userA}','${authA}','rider','active'),
      ('${userB}','${authB}','rider','active');

    INSERT INTO public.rider_profiles(id,user_id) VALUES
      ('${riderA}','${userA}'),
      ('${riderB}','${userB}');

    INSERT INTO public.orders(id,rider_id)
    VALUES('${order}','${riderA}');

    INSERT INTO public.qg_pickup_proofs(order_id,rider_id,photo_path)
    VALUES('${order}','${riderA}','proof-pickup.jpg');

    INSERT INTO public.qg_delivery_proofs(order_id,rider_id,photo_path)
    VALUES('${order}','${riderA}','proof-delivery.jpg');
  `);

  await db.exec(`
    SELECT set_config('request.jwt.claim.sub','${authA}',false);
    SET ROLE authenticated;
  `);

  eq(
    (await db.query(`SELECT count(*)::int AS n FROM public.qg_pickup_proofs`)).rows[0].n,
    0,
    'server-only pickup proof table must remain unreadable directly'
  );
  eq(
    (await db.query(`SELECT count(*)::int AS n FROM public.qg_delivery_proofs`)).rows[0].n,
    0,
    'server-only delivery proof fixture must remain unreadable directly'
  );

  eq(
    (await db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','proof-pickup.jpg') AS v`)).rows[0].v,
    true,
    'assigned Rider must recover own pickup proof through scoped RPC'
  );
  eq(
    (await db.query(`SELECT public.qg_rider_proof_committed('${order}','delivery','proof-delivery.jpg') AS v`)).rows[0].v,
    true,
    'assigned Rider must recover own delivery proof through scoped RPC'
  );
  eq(
    (await db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','wrong.jpg') AS v`)).rows[0].v,
    false,
    'proof recovery must require exact immutable photo path'
  );

  await assert.rejects(
    db.query(`SELECT public.qg_rider_proof_committed('${order}','other','proof-pickup.jpg')`),
    /unsupported proof stage/
  );
  checks++;

  await db.exec(`
    RESET ROLE;
    SELECT set_config('request.jwt.claim.sub','${authB}',false);
    SET ROLE authenticated;
  `);
  eq(
    (await db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','proof-pickup.jpg') AS v`)).rows[0].v,
    false,
    'different Rider must never recover another Rider proof'
  );

  await db.exec('RESET ROLE');
  const grants=(await db.query(`
    SELECT
      has_function_privilege('anon','public.qg_rider_proof_committed(uuid,text,text)','EXECUTE') AS anon_exec,
      has_function_privilege('authenticated','public.qg_rider_proof_committed(uuid,text,text)','EXECUTE') AS auth_exec
  `)).rows[0];
  eq(grants.anon_exec,false,'anonymous proof recovery must be denied');
  eq(grants.auth_exec,true,'authenticated Rider proof recovery RPC must remain callable');

  // Reapplying the exact reviewed migration must preserve its contract and grants.
  await db.exec(migration);
  eq((await db.query(`SELECT count(*)::int AS n FROM public.qg_pickup_proofs`)).rows[0].n,
    1,'migration replay must not alter proof data');
  await db.exec(`SELECT set_config('request.jwt.claim.sub','${authA}',false); SET ROLE authenticated;`);
  for (const [orderId,stage,photo] of [
    [null,'pickup','proof-pickup.jpg'],
    [order,'pickup',null],
    [order,'pickup',''],
    ['40000000-0000-4000-8000-000000000099','pickup','proof-pickup.jpg'],
  ]) {
    eq((await db.query('SELECT public.qg_rider_proof_committed($1,$2,$3) AS v',
      [orderId,stage,photo])).rows[0].v,false,'incomplete/unowned proof must not be confirmed');
  }
  await db.exec(`RESET ROLE; SELECT set_config('request.jwt.claim.sub','',false); SET ROLE authenticated;`);
  eq((await db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','proof-pickup.jpg') AS v`)).rows[0].v,
    false,'authenticated role without a subject must not recover proof');
  await db.exec(`RESET ROLE; UPDATE public.users SET role='customer' WHERE id='${userA}';
    SELECT set_config('request.jwt.claim.sub','${authA}',false); SET ROLE authenticated;`);
  eq((await db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','proof-pickup.jpg') AS v`)).rows[0].v,
    false,'a customer with a stale Rider profile must not recover proof');
  await db.exec(`RESET ROLE; UPDATE public.users SET role='rider' WHERE id='${userA}';
    UPDATE public.orders SET rider_id='${riderB}' WHERE id='${order}'; SET ROLE authenticated;`);
  eq((await db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','proof-pickup.jpg') AS v`)).rows[0].v,
    false,'previous assigned Rider must lose access after reassignment');
  await db.exec(`RESET ROLE; UPDATE public.orders SET rider_id='${riderA}' WHERE id='${order}';
    SET ROLE anon;`);
  await assert.rejects(db.query(`SELECT public.qg_rider_proof_committed('${order}','pickup','proof-pickup.jpg')`),
    /permission denied/);
  checks++;
  await db.exec('RESET ROLE');
  eq((await db.query(`SELECT count(*)::int AS n FROM public.qg_delivery_proofs`)).rows[0].n,
    1,'denied/retried recovery must not create or remove proof rows');

  const helperStart=nativeApi.indexOf('private fun mutationProofExists(');
  const helperEnd=nativeApi.indexOf('private fun uploadedEvidenceMatches(',helperStart);
  const helper=nativeApi.slice(helperStart,helperEnd);
  ok(helperStart>=0&&helperEnd>helperStart,'Native proof recovery helper must remain discoverable');
  ok(helper.includes('"qg_rider_proof_committed"'),'Native ambiguous recovery must use scoped proof RPC');
  ok(helper.includes('"qg_pickup_proofs" -> "pickup"')&&helper.includes('"qg_delivery_proofs" -> "delivery"'),
    'Native proof recovery must whitelist known proof stages');
  ok(!helper.includes('/rest/v1/$table'),'Native recovery must not query dynamic proof tables directly');

  await db.close();
  console.log(JSON.stringify({
    checks,
    failures:0,
    scope:'PGlite Rider-owned proof recovery through SECURITY DEFINER RPC while direct proof-table reads stay closed'
  }));
})().catch(e=>{console.error(e);process.exit(1)});
