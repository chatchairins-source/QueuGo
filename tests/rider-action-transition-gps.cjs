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
    CREATE SCHEMA qg_private;

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
      user_id uuid NOT NULL,
      latitude double precision,
      longitude double precision,
      updated_at timestamptz NOT NULL DEFAULT now()
    );

    CREATE TABLE public.shop_profiles(
      id uuid PRIMARY KEY,
      user_id uuid
    );

    CREATE TABLE public.orders(
      id uuid PRIMARY KEY,
      order_number text,
      rider_id uuid,
      customer_id uuid,
      shop_id uuid,
      status text,
      rider_arrived_shop_at timestamptz,
      rider_arrived_customer_at timestamptz,
      updated_at timestamptz NOT NULL DEFAULT now()
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

    CREATE TABLE public.notifications(
      user_id uuid,
      title text,
      message text,
      type text,
      reference_id uuid
    );

    CREATE TABLE public.qg_pickup_proofs(
      order_id uuid PRIMARY KEY,
      rider_id uuid NOT NULL,
      gps_lat double precision,
      gps_lng double precision,
      photo_path text,
      created_at timestamptz DEFAULT now()
    );

    CREATE TABLE public.qg_delivery_proofs(
      order_id uuid PRIMARY KEY,
      rider_id uuid NOT NULL,
      gps_lat double precision,
      gps_lng double precision,
      photo_path text,
      extra_photo_path text,
      otp_verified boolean DEFAULT false,
      verified_at timestamptz
    );
  `);

  const transition=fs.readFileSync(
    path.resolve(__dirname,'../supabase/migrations/20261022151911_rider_transition_semantics_hardening.sql'),
    'utf8'
  );
  const actionGps=fs.readFileSync(
    path.resolve(__dirname,'../supabase/migrations/20261023000500_rider_action_transition_gps_sync.sql'),
    'utf8'
  );

  await db.exec(transition);
  await db.exec(actionGps);

  const rider='30000000-0000-4000-8000-000000000001';
  const riderUser='31000000-0000-4000-8000-000000000001';
  const authUser='32000000-0000-4000-8000-000000000001';
  const shop='33000000-0000-4000-8000-000000000001';
  const shopUser='34000000-0000-4000-8000-000000000001';
  const customer='35000000-0000-4000-8000-000000000001';
  const order='40000000-0000-4000-8000-000000000001';

  await db.exec(`
    INSERT INTO public.users(id,auth_user_id,role,status)
    VALUES('${riderUser}','${authUser}','rider','active');

    INSERT INTO public.rider_profiles(id,user_id,latitude,longitude,updated_at)
    VALUES('${rider}','${riderUser}',15.000000,103.000000,now()-interval '1 minute');

    INSERT INTO public.shop_profiles(id,user_id)
    VALUES('${shop}','${shopUser}');

    INSERT INTO public.orders(
      id,order_number,rider_id,customer_id,shop_id,status
    )
    VALUES(
      '${order}','QT-9001','${rider}','${customer}','${shop}','rider_assigned'
    );

    SELECT set_config('request.jwt.claim.sub','${authUser}',false);
  `);

  const before=(await db.query(`
    SELECT latitude,longitude FROM public.rider_profiles WHERE id='${rider}'
  `)).rows[0];
  eq(Number(before.latitude),15,'fixture must start from older Rider latitude');
  eq(Number(before.longitude),103,'fixture must start from older Rider longitude');

  const arrivalShop=(await db.query(`
    SELECT qg_rider_mark_arrival(
      '${order}','shop',15.111111,103.111111
    ) AS value
  `)).rows[0].value;
  eq(arrivalShop,'shop','shop arrival must commit');

  let profile=(await db.query(`
    SELECT latitude,longitude FROM public.rider_profiles WHERE id='${rider}'
  `)).rows[0];
  eq(Number(profile.latitude),15.111111,'shop-arrival GPS must become the server-known Rider latitude');
  eq(Number(profile.longitude),103.111111,'shop-arrival GPS must become the server-known Rider longitude');

  let event=(await db.query(`
    SELECT metadata
    FROM public.audit_logs
    WHERE action='rider_state_transition'
      AND entity_id='${order}'
      AND description='at_merchant'
    ORDER BY seq DESC
    LIMIT 1
  `)).rows[0];
  ok(!!event,'AT_MERCHANT transition audit must exist');
  eq(Number(event.metadata.gps_lat),15.111111,'AT_MERCHANT audit must use action latitude');
  eq(Number(event.metadata.gps_lng),103.111111,'AT_MERCHANT audit must use action longitude');
  ok(!!event.metadata.server_recorded_at,'AT_MERCHANT audit must include server timestamp');

  await db.exec(`
    INSERT INTO public.qg_pickup_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path)
    VALUES('${order}','${rider}',15.222222,103.222222,'proof-pickup.jpg');

    UPDATE public.orders
    SET status='picked_up',updated_at=now()
    WHERE id='${order}';

    UPDATE public.orders
    SET status='in_progress',updated_at=now()
    WHERE id='${order}';
  `);

  profile=(await db.query(`
    SELECT latitude,longitude FROM public.rider_profiles WHERE id='${rider}'
  `)).rows[0];
  eq(Number(profile.latitude),15.222222,'pickup proof GPS must become the server-known Rider latitude');
  eq(Number(profile.longitude),103.222222,'pickup proof GPS must become the server-known Rider longitude');

  const pickupEvents=(await db.query(`
    SELECT description,metadata
    FROM public.audit_logs
    WHERE action='rider_state_transition'
      AND entity_id='${order}'
      AND description IN ('picked_up','to_customer')
    ORDER BY seq
  `)).rows;
  eq(pickupEvents.length,2,'pickup must produce PICKED_UP and TO_CUSTOMER transition events');
  for(const row of pickupEvents){
    eq(Number(row.metadata.gps_lat),15.222222,row.description+' must use pickup action latitude');
    eq(Number(row.metadata.gps_lng),103.222222,row.description+' must use pickup action longitude');
  }

  const arrivalCustomer=(await db.query(`
    SELECT qg_rider_mark_arrival(
      '${order}','customer',15.333333,103.333333
    ) AS value
  `)).rows[0].value;
  eq(arrivalCustomer,'customer','customer arrival must commit');

  event=(await db.query(`
    SELECT metadata
    FROM public.audit_logs
    WHERE action='rider_state_transition'
      AND entity_id='${order}'
      AND description='at_customer'
    ORDER BY seq DESC
    LIMIT 1
  `)).rows[0];
  eq(Number(event.metadata.gps_lat),15.333333,'AT_CUSTOMER audit must use arrival action latitude');
  eq(Number(event.metadata.gps_lng),103.333333,'AT_CUSTOMER audit must use arrival action longitude');

  await db.exec(`
    INSERT INTO public.qg_delivery_proofs(
      order_id,rider_id,gps_lat,gps_lng,photo_path,verified_at
    )
    VALUES(
      '${order}','${rider}',15.444444,103.444444,'proof-delivery.jpg',now()
    );

    UPDATE public.orders
    SET status='completed',updated_at=now()
    WHERE id='${order}';
  `);

  event=(await db.query(`
    SELECT metadata
    FROM public.audit_logs
    WHERE action='rider_state_transition'
      AND entity_id='${order}'
      AND description='completed'
    ORDER BY seq DESC
    LIMIT 1
  `)).rows[0];
  eq(Number(event.metadata.gps_lat),15.444444,'COMPLETED audit must use delivery-proof action latitude');
  eq(Number(event.metadata.gps_lng),103.444444,'COMPLETED audit must use delivery-proof action longitude');

  await db.exec(`
    UPDATE public.qg_delivery_proofs
    SET photo_path='proof-delivery-retry.jpg'
    WHERE order_id='${order}';
  `);
  profile=(await db.query(`
    SELECT latitude,longitude FROM public.rider_profiles WHERE id='${rider}'
  `)).rows[0];
  eq(Number(profile.latitude),15.444444,'photo-only proof retry must not rewrite Rider GPS');
  eq(Number(profile.longitude),103.444444,'photo-only proof retry must preserve Rider longitude');

  const grants=(await db.query(`
    SELECT
      has_function_privilege('anon','qg_private.qg_sync_rider_proof_gps()','EXECUTE') AS anon_exec,
      has_function_privilege('authenticated','qg_private.qg_sync_rider_proof_gps()','EXECUTE') AS auth_exec
  `)).rows[0];
  eq(grants.anon_exec,false,'anonymous clients must not execute proof GPS sync directly');
  eq(grants.auth_exec,false,'authenticated clients must not execute proof GPS sync directly');

  await db.close();
  console.log(JSON.stringify({
    checks,
    failures:0,
    scope:'PGlite isolated action-specific Rider GPS -> transition audit ordering; no Production orders mutated'
  }));
})().catch(e=>{console.error(e);process.exit(1)});
