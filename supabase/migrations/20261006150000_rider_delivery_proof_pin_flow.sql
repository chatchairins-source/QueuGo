-- QueueGo Rider professional delivery completion
-- Enforces customer handoff PIN + required delivery photo before COMPLETED.
-- Keeps the existing order state machine: READY -> PICKED_UP -> IN_PROGRESS -> COMPLETED.
BEGIN;

ALTER TABLE public.qg_delivery_proofs
  ADD COLUMN IF NOT EXISTS extra_photo_path text;

DROP POLICY IF EXISTS qg_evidence_owner_delete ON storage.objects;
CREATE POLICY qg_evidence_owner_delete
ON storage.objects
FOR DELETE
TO authenticated
USING (
  bucket_id='qg-evidence'
  AND (storage.foldername(name))[1]=auth.uid()::text
  AND EXISTS(
    SELECT 1 FROM public.users u
    WHERE u.auth_user_id=auth.uid()
      AND u.role='rider'
      AND u.status='active'
  )
);

CREATE OR REPLACE FUNCTION public.qg_guard_delivery_proof()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
BEGIN
  IF NEW.order_type='shopping'
     AND NEW.status='completed'
     AND OLD.status IS DISTINCT FROM 'completed'
     AND EXISTS(SELECT 1 FROM public.queuego_cash_order_locks WHERE order_id=NEW.id)
  THEN
    IF public.is_active_admin() THEN
      RETURN NEW;
    END IF;

    IF NOT EXISTS(
      SELECT 1
      FROM public.qg_delivery_proofs p
      WHERE p.order_id=NEW.id
        AND p.rider_id=NEW.rider_id
        AND p.otp_verified=true
        AND p.photo_path IS NOT NULL
    ) THEN
      RAISE EXCEPTION 'delivery proof and handoff PIN required';
    END IF;
  END IF;

  RETURN NEW;
END
$function$;

DROP FUNCTION IF EXISTS public.qg_complete_with_proof(uuid,text,double precision,double precision,text);

CREATE OR REPLACE FUNCTION public.qg_complete_with_proof(
  p_order_id uuid,
  p_pin text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_photo_path text DEFAULT NULL,
  p_extra_photo_path text DEFAULT NULL
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_order public.orders%rowtype;
  v_pin text;
BEGIN
  SELECT r.id INTO v_rider
  FROM public.rider_profiles r
  JOIN public.users u ON u.id=r.user_id
  WHERE u.auth_user_id=auth.uid()
    AND u.role='rider'
    AND u.status='active';

  IF v_rider IS NULL THEN
    RAISE EXCEPTION 'active rider required';
  END IF;

  SELECT * INTO v_order
  FROM public.orders
  WHERE id=p_order_id
    AND rider_id=v_rider
  FOR UPDATE;

  IF FOUND AND v_order.status='completed' THEN
    RETURN 'completed';
  END IF;

  IF NOT FOUND OR v_order.status<>'in_progress' THEN
    RAISE EXCEPTION 'order is not ready to complete';
  END IF;

  SELECT pin INTO v_pin
  FROM public.qg_delivery_pins
  WHERE order_id=p_order_id;

  IF v_pin IS NULL OR trim(coalesce(p_pin,'')) IS DISTINCT FROM v_pin THEN
    RAISE EXCEPTION 'incorrect delivery PIN';
  END IF;

  IF p_photo_path IS NULL THEN
    RAISE EXCEPTION 'delivery photo required';
  END IF;

  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180))
  THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;

  IF p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     OR NOT EXISTS(
       SELECT 1
       FROM storage.objects
       WHERE bucket_id='qg-evidence'
         AND name=p_photo_path
     )
  THEN
    RAISE EXCEPTION 'invalid delivery evidence';
  END IF;

  IF p_extra_photo_path IS NOT NULL AND (
       p_extra_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
       OR NOT EXISTS(
         SELECT 1
         FROM storage.objects
         WHERE bucket_id='qg-evidence'
           AND name=p_extra_photo_path
       )
     )
  THEN
    RAISE EXCEPTION 'invalid extra delivery evidence';
  END IF;

  INSERT INTO public.qg_delivery_proofs(
    order_id,rider_id,gps_lat,gps_lng,photo_path,extra_photo_path,otp_verified,verified_at
  )
  VALUES(
    p_order_id,v_rider,p_lat,p_lng,p_photo_path,p_extra_photo_path,true,now()
  )
  ON CONFLICT(order_id) DO UPDATE
    SET rider_id=excluded.rider_id,
        gps_lat=excluded.gps_lat,
        gps_lng=excluded.gps_lng,
        photo_path=excluded.photo_path,
        extra_photo_path=excluded.extra_photo_path,
        otp_verified=true,
        verified_at=now()
    WHERE public.qg_delivery_proofs.rider_id=excluded.rider_id;

  RETURN public.rider_order_action(p_order_id,'complete');
END
$function$;

DROP FUNCTION IF EXISTS public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text);

CREATE OR REPLACE FUNCTION public.qg_complete_market_with_proof(
  p_market_order_id uuid,
  p_pin text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_photo_path text DEFAULT NULL,
  p_extra_photo_path text DEFAULT NULL
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_lead uuid;
  v_pin text;
  v_order record;
BEGIN
  SELECT r.id INTO v_rider
  FROM public.rider_profiles r
  JOIN public.users u ON u.id=r.user_id
  WHERE u.auth_user_id=auth.uid()
    AND u.role='rider'
    AND u.status='active';

  IF v_rider IS NULL THEN
    RAISE EXCEPTION 'active rider required';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(p_market_order_id::text,991));

  SELECT id INTO v_lead
  FROM public.orders
  WHERE market_order_id=p_market_order_id
    AND rider_id=v_rider
    AND status<>'cancelled'
  ORDER BY created_at,id
  LIMIT 1;

  IF v_lead IS NULL THEN
    RAISE EXCEPTION 'market order unavailable';
  END IF;

  IF NOT EXISTS(
    SELECT 1
    FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND status<>'cancelled'
      AND status<>'completed'
  ) THEN
    RETURN 'completed';
  END IF;

  IF EXISTS(
    SELECT 1
    FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND status<>'cancelled'
      AND (rider_id IS DISTINCT FROM v_rider OR status<>'in_progress')
  ) THEN
    RAISE EXCEPTION 'market order is not ready to complete';
  END IF;

  SELECT pin INTO v_pin
  FROM public.qg_delivery_pins
  WHERE order_id=v_lead;

  IF v_pin IS NULL OR trim(coalesce(p_pin,'')) IS DISTINCT FROM v_pin THEN
    RAISE EXCEPTION 'incorrect delivery PIN';
  END IF;

  IF p_photo_path IS NULL THEN
    RAISE EXCEPTION 'delivery photo required';
  END IF;

  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180))
  THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;

  IF p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     OR NOT EXISTS(
       SELECT 1 FROM storage.objects
       WHERE bucket_id='qg-evidence' AND name=p_photo_path
     )
  THEN
    RAISE EXCEPTION 'invalid delivery evidence';
  END IF;

  IF p_extra_photo_path IS NOT NULL AND (
       p_extra_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
       OR NOT EXISTS(
         SELECT 1 FROM storage.objects
         WHERE bucket_id='qg-evidence' AND name=p_extra_photo_path
       )
     )
  THEN
    RAISE EXCEPTION 'invalid extra delivery evidence';
  END IF;

  FOR v_order IN
    SELECT *
    FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND status<>'cancelled'
    ORDER BY created_at,id
    FOR UPDATE
  LOOP
    IF NOT EXISTS(
      SELECT 1
      FROM public.rider_cash_advances
      WHERE order_id=v_order.id
        AND rider_id=v_rider
        AND amount=v_order.subtotal
    ) THEN
      RAISE EXCEPTION 'cash advance missing for shop';
    END IF;

    INSERT INTO public.qg_delivery_proofs(
      order_id,rider_id,gps_lat,gps_lng,photo_path,extra_photo_path,otp_verified,verified_at
    )
    VALUES(
      v_order.id,v_rider,p_lat,p_lng,p_photo_path,p_extra_photo_path,true,now()
    )
    ON CONFLICT(order_id) DO UPDATE
      SET rider_id=excluded.rider_id,
          gps_lat=excluded.gps_lat,
          gps_lng=excluded.gps_lng,
          photo_path=excluded.photo_path,
          extra_photo_path=excluded.extra_photo_path,
          otp_verified=true,
          verified_at=now()
      WHERE public.qg_delivery_proofs.rider_id=excluded.rider_id;
  END LOOP;

  PERFORM set_config('queuego.v22_transition','rpc',true);

  UPDATE public.orders
  SET status='completed',
      completed_at=coalesce(completed_at,now()),
      note='__QT_ORDER_STATUS__=completed'||E'\n'||
        regexp_replace(coalesce(note,''),E'^(__QT_ORDER_STATUS__=[^\n]*\n?)+','','g'),
      updated_at=now()
  WHERE market_order_id=p_market_order_id
    AND status='in_progress';

  UPDATE public.deliveries d
  SET status='delivered',
      delivered_at=coalesce(d.delivered_at,now()),
      updated_at=now()
  FROM public.orders o
  WHERE o.id=d.order_id
    AND o.market_order_id=p_market_order_id
    AND o.status='completed';

  UPDATE public.payments p
  SET status='paid',
      paid_at=coalesce(p.paid_at,now()),
      updated_at=now()
  FROM public.orders o
  WHERE o.id=p.order_id
    AND o.market_order_id=p_market_order_id
    AND p.payment_method='cash'
    AND p.status='pending';

  UPDATE public.market_orders
  SET status='COMPLETED',updated_at=now()
  WHERE id=p_market_order_id;

  RETURN 'completed';
END
$function$;

REVOKE ALL ON FUNCTION public.qg_complete_with_proof(uuid,text,double precision,double precision,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_complete_with_proof(uuid,text,double precision,double precision,text,text) TO authenticated;

REVOKE ALL ON FUNCTION public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text,text) TO authenticated;

REVOKE ALL ON FUNCTION public.qg_customer_delivery_pin(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_customer_delivery_pin(uuid) TO authenticated;

COMMIT;
