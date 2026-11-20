const fs=require('fs'),path=require('path'),assert=require('assert'),{PGlite}=require('@electric-sql/pglite');

(async()=>{
  let checks=0;
  const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++};
  const ok=(v,m)=>{assert.ok(v,m);checks++};
  const db=new PGlite();

  await db.exec(`
    CREATE ROLE anon;
    CREATE ROLE authenticated;
    CREATE SCHEMA qg_private;

    CREATE TABLE public.rider_profiles(
      id uuid PRIMARY KEY,
      user_id uuid NOT NULL,
      latitude double precision,
      longitude double precision,
      updated_at timestamptz NOT NULL DEFAULT now()
    );

    CREATE TABLE public.orders(
      id uuid PRIMARY KEY,
      rider_id uuid,
      status text,
      rider_arrived_shop_at timestamptz,
      rider_arrived_customer_at timestamptz
    );

    CREATE TABLE public.audit_logs(
      seq bigserial PRIMARY KEY,
      user_id uuid,
      action text,
      entity_type text,
      entity_id uuid,
      description text,
      metadata jsonb,
      created_at timestamptz NOT NULL DEFAULT now()
    );
  `);

  const migration=fs.readFileSync(
    path.resolve(__dirname,'../supabase/migrations/20261022151911_rider_transition_semantics_hardening.sql'),
    'utf8'
  );
  const claimMigration=fs.readFileSync(
    path.resolve(__dirname,'../supabase/migrations/20261022153500_rider_claim_transition_gps.sql'),
    'utf8'
  );
  ok(!claimMigration.includes('isfinite('),
    'Rider claim GPS migration must use PostgreSQL-compatible float8 validation');
  ok(claimMigration.includes('v_lat not between -90 and 90')&&
     claimMigration.includes('v_lng not between -180 and 180'),
    'Rider claim GPS migration must reject NaN, infinity and out-of-range coordinates with range checks');
  ok(claimMigration.indexOf('update public.rider_profiles')<
     claimMigration.indexOf('public.rider_claim_order(v_order)'),
    'fresh Rider claim coordinates must be persisted before the order transition audit trigger fires');


  ok(migration.includes("when 'preparing' then null")&&migration.includes("when 'ready' then null"),
    'merchant preparation states must not impersonate Rider arrival');
  ok(migration.includes("'server_recorded_at', clock_timestamp()"),
    'transition metadata must carry an explicit server-side event timestamp');
  ok(migration.includes("al.metadata->>'rider_id' = new.rider_id::text"),
    'transition audit must fence duplicate Rider/order transitions');
  await db.exec(migration);

  const rider='30000000-0000-4000-8000-000000000001';
  const user='31000000-0000-4000-8000-000000000001';
  const order='40000000-0000-4000-8000-000000000001';

  await db.exec(`
    INSERT INTO public.rider_profiles(id,user_id,latitude,longitude,updated_at)
    VALUES('${rider}','${user}',15.123456,103.123456,now());

    INSERT INTO public.orders(id,rider_id,status)
    VALUES('${order}',NULL,'searching_rider');

    UPDATE public.orders
    SET rider_id='${rider}', status='rider_assigned'
    WHERE id='${order}';

    UPDATE public.orders SET status='preparing' WHERE id='${order}';
    UPDATE public.orders SET status='ready' WHERE id='${order}';

    UPDATE public.orders
    SET rider_arrived_shop_at=now()
    WHERE id='${order}';

    UPDATE public.orders
    SET rider_arrived_shop_at=rider_arrived_shop_at
    WHERE id='${order}';

    UPDATE public.orders SET status='picked_up' WHERE id='${order}';
    UPDATE public.orders SET status='in_progress' WHERE id='${order}';

    UPDATE public.orders
    SET rider_arrived_customer_at=now()
    WHERE id='${order}';

    UPDATE public.orders SET status='completed' WHERE id='${order}';
    UPDATE public.orders SET status='completed' WHERE id='${order}';
  `);

  const rows=(await db.query(`
    SELECT description,metadata,created_at
    FROM public.audit_logs
    WHERE action='rider_state_transition' AND entity_id='${order}'
    ORDER BY seq
  `)).rows;

  eq(rows.map(x=>x.description),[
    'to_merchant',
    'at_merchant',
    'picked_up',
    'to_customer',
    'at_customer',
    'completed'
  ],'Rider state audit must follow the physical Rider workflow only');

  eq(rows.filter(x=>x.description==='at_merchant').length,1,
    'PREPARING/READY must never duplicate AT_MERCHANT');
  eq(rows.length,6,'repeat-safe updates must not duplicate state events');

  for(const row of rows){
    eq(Number(row.metadata.gps_lat),15.123456,'transition must include Rider latitude');
    eq(Number(row.metadata.gps_lng),103.123456,'transition must include Rider longitude');
    ok(!!row.metadata.server_recorded_at,'transition must include server_recorded_at');
    ok(!!row.created_at,'audit row must retain database server timestamp');
  }

  const privileges=(await db.query(`
    SELECT
      has_function_privilege('anon','qg_private.qg_audit_rider_order_transition()','EXECUTE') AS anon_exec,
      has_function_privilege('authenticated','qg_private.qg_audit_rider_order_transition()','EXECUTE') AS auth_exec
  `)).rows[0];
  eq(privileges.anon_exec,false,'anonymous clients must not execute the audit trigger function');
  eq(privileges.auth_exec,false,'authenticated clients must not execute the audit trigger function directly');

  await db.close();
  console.log(JSON.stringify({
    checks,
    failures:0,
    scope:'PGlite isolated Rider transition semantics, GPS/server timestamps and replay fence; no Production orders mutated'
  }));
})().catch(e=>{console.error(e);process.exit(1)});
