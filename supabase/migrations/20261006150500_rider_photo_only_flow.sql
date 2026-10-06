-- QueueGo Rider photo-only operational proof flow.
-- User-approved flow: one pickup photo + one delivery photo. No customer PIN.
BEGIN;

CREATE TABLE IF NOT EXISTS public.qg_pickup_proofs (
  order_id uuid PRIMARY KEY REFERENCES public.orders(id) ON DELETE CASCADE,
  rider_id uuid NOT NULL REFERENCES public.rider_profiles(id) ON DELETE RESTRICT,
  gps_lat double precision,
  gps_lng double precision,
  photo_path text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.qg_pickup_proofs ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.qg_guard_pickup_photo()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
BEGIN
  IF NEW.rider_id IS NOT NULL
     AND NEW.status='picked_up'
     AND OLD.status IS DISTINCT FROM 'picked_up'
  THEN
    IF public.is_active_admin() THEN
      RETURN NEW;
    END IF;

    IF NOT EXISTS(
      SELECT 1
      FROM public.qg_pickup_proofs p
      WHERE p.order_id=NEW.id
        AND p.rider_id=NEW.rider_id
        AND p.photo_path IS NOT NULL
    ) THEN
      RAISE EXCEPTION 'pickup photo required';
    END IF;
  END IF;
  RETURN NEW;
END
$function$;

DROP TRIGGER IF EXISTS qg_guard_pickup_photo_trg ON public.orders;
CREATE TRIGGER qg_guard_pickup_photo_trg
BEFORE UPDATE OF status ON public.orders
FOR EACH ROW
EXECUTE FUNCTION public.qg_guard_pickup_photo();

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
        AND p.photo_path IS NOT NULL
    ) THEN
      RAISE EXCEPTION 'delivery photo required';
    END IF;
  END IF;
  RETURN NEW;
END
$function$;

CREATE OR REPLACE FUNCTION public.qg_pickup_with_photo(
  p_order_id uuid,
  p_photo_path text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_order public.orders%rowtype;
  v_status text;
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
  WHERE id=p_order_id AND rider_id=v_rider
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'order unavailable';
  END IF;

  IF v_order.status IN ('picked_up','in_progress') THEN
    RETURN v_order.status;
  END IF;

  IF v_order.status<>'ready' THEN
    RAISE EXCEPTION 'order is not ready for pickup';
  END IF;

  IF p_photo_path IS NULL
     OR p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     OR NOT EXISTS(
       SELECT 1 FROM storage.objects
       WHERE bucket_id='qg-evidence' AND name=p_photo_path
     )
  THEN
    RAISE EXCEPTION 'valid pickup photo required';
  END IF;

  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180))
  THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;

  INSERT INTO public.qg_pickup_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path)
  VALUES(p_order_id,v_rider,p_lat,p_lng,p_photo_path)
  ON CONFLICT(order_id) DO UPDATE
    SET rider_id=excluded.rider_id,
        gps_lat=excluded.gps_lat,
        gps_lng=excluded.gps_lng,
        photo_path=excluded.photo_path,
        created_at=now()
    WHERE public.qg_pickup_proofs.rider_id=excluded.rider_id;

  v_status:=public.rider_order_action(p_order_id,'pickup_cash');
  IF v_status='picked_up' THEN
    v_status:=public.rider_order_action(p_order_id,'deliver');
  END IF;

  RETURN v_status;
END
$function$;

CREATE OR REPLACE FUNCTION public.qg_market_pickup_with_photo(
  p_pickup_id uuid,
  p_amount numeric,
  p_photo_path text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_order_id uuid;
  v_status text;
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

  SELECT mp.order_id,mp.status INTO v_order_id,v_status
  FROM public.market_order_pickups mp
  JOIN public.orders o ON o.id=mp.order_id
  WHERE mp.id=p_pickup_id
    AND o.rider_id=v_rider
  FOR UPDATE OF mp;

  IF v_order_id IS NULL THEN
    RAISE EXCEPTION 'pickup unavailable';
  END IF;

  IF v_status='PICKED_UP' THEN
    RETURN public.market_rider_confirm_pickup_cash(p_pickup_id,p_amount);
  END IF;

  IF p_photo_path IS NULL
     OR p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     OR NOT EXISTS(
       SELECT 1 FROM storage.objects
       WHERE bucket_id='qg-evidence' AND name=p_photo_path
     )
  THEN
    RAISE EXCEPTION 'valid pickup photo required';
  END IF;

  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180))
  THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;

  INSERT INTO public.qg_pickup_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path)
  VALUES(v_order_id,v_rider,p_lat,p_lng,p_photo_path)
  ON CONFLICT(order_id) DO UPDATE
    SET rider_id=excluded.rider_id,
        gps_lat=excluded.gps_lat,
        gps_lng=excluded.gps_lng,
        photo_path=excluded.photo_path,
        created_at=now()
    WHERE public.qg_pickup_proofs.rider_id=excluded.rider_id;

  RETURN public.market_rider_confirm_pickup_cash(p_pickup_id,p_amount);
END
$function$;

CREATE OR REPLACE FUNCTION public.qg_complete_with_photo(
  p_order_id uuid,
  p_photo_path text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_order public.orders%rowtype;
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
  WHERE id=p_order_id AND rider_id=v_rider
  FOR UPDATE;

  IF FOUND AND v_order.status='completed' THEN
    RETURN 'completed';
  END IF;

  IF NOT FOUND OR v_order.status<>'in_progress' THEN
    RAISE EXCEPTION 'order is not ready to complete';
  END IF;

  IF p_photo_path IS NULL
     OR p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     OR NOT EXISTS(
       SELECT 1 FROM storage.objects
       WHERE bucket_id='qg-evidence' AND name=p_photo_path
     )
  THEN
    RAISE EXCEPTION 'valid delivery photo required';
  END IF;

  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180))
  THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;

  INSERT INTO public.qg_delivery_proofs(
    order_id,rider_id,gps_lat,gps_lng,photo_path,extra_photo_path,otp_verified,verified_at
  )
  VALUES(p_order_id,v_rider,p_lat,p_lng,p_photo_path,NULL,false,now())
  ON CONFLICT(order_id) DO UPDATE
    SET rider_id=excluded.rider_id,
        gps_lat=excluded.gps_lat,
        gps_lng=excluded.gps_lng,
        photo_path=excluded.photo_path,
        extra_photo_path=NULL,
        otp_verified=false,
        verified_at=now()
    WHERE public.qg_delivery_proofs.rider_id=excluded.rider_id;

  RETURN public.rider_order_action(p_order_id,'complete');
END
$function$;

CREATE OR REPLACE FUNCTION public.qg_complete_market_with_photo(
  p_market_order_id uuid,
  p_photo_path text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_user uuid;
  v_lead uuid;
  v_customer uuid;
  v_order record;
BEGIN
  SELECT r.id,r.user_id INTO v_rider,v_user
  FROM public.rider_profiles r
  JOIN public.users u ON u.id=r.user_id
  WHERE u.auth_user_id=auth.uid()
    AND u.role='rider'
    AND u.status='active';

  IF v_rider IS NULL THEN
    RAISE EXCEPTION 'active rider required';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(p_market_order_id::text,991));

  SELECT id,customer_id INTO v_lead,v_customer
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
    SELECT 1 FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND status<>'cancelled'
      AND status<>'completed'
  ) THEN
    RETURN 'completed';
  END IF;

  IF EXISTS(
    SELECT 1 FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND status<>'cancelled'
      AND (rider_id IS DISTINCT FROM v_rider OR status<>'in_progress')
  ) THEN
    RAISE EXCEPTION 'market order is not ready to complete';
  END IF;

  IF p_photo_path IS NULL
     OR p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     OR NOT EXISTS(
       SELECT 1 FROM storage.objects
       WHERE bucket_id='qg-evidence' AND name=p_photo_path
     )
  THEN
    RAISE EXCEPTION 'valid delivery photo required';
  END IF;

  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180))
  THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;

  IF EXISTS(
    SELECT 1
    FROM public.orders o
    WHERE o.market_order_id=p_market_order_id
      AND o.status<>'cancelled'
      AND NOT EXISTS(
        SELECT 1 FROM public.rider_cash_advances a
        WHERE a.order_id=o.id
          AND a.rider_id=v_rider
          AND a.amount=o.subtotal
      )
  ) THEN
    RAISE EXCEPTION 'cash advance missing for shop';
  END IF;

  FOR v_order IN
    SELECT *
    FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND status<>'cancelled'
    ORDER BY created_at,id
    FOR UPDATE
  LOOP
    INSERT INTO public.qg_delivery_proofs(
      order_id,rider_id,gps_lat,gps_lng,photo_path,extra_photo_path,otp_verified,verified_at
    )
    VALUES(v_order.id,v_rider,p_lat,p_lng,p_photo_path,NULL,false,now())
    ON CONFLICT(order_id) DO UPDATE
      SET rider_id=excluded.rider_id,
          gps_lat=excluded.gps_lat,
          gps_lng=excluded.gps_lng,
          photo_path=excluded.photo_path,
          extra_photo_path=NULL,
          otp_verified=false,
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

  INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  SELECT v_user,'rider_order_action','order',o.id,'complete',
    jsonb_build_object(
      'from','in_progress',
      'to','completed',
      'cash_collected',true,
      'delivery_photo_required',true,
      'source','market_photo',
      'market_order_id',p_market_order_id,
      'customer_id',o.customer_id,
      'shop_id',o.shop_id,
      'rider_id',v_rider
    )
  FROM public.orders o
  WHERE o.market_order_id=p_market_order_id
    AND o.status='completed'
    AND NOT EXISTS(
      SELECT 1 FROM public.audit_logs a
      WHERE a.entity_id=o.id
        AND a.action='rider_order_action'
        AND a.description='complete'
        AND a.metadata->>'source'='market_photo'
    );

  UPDATE public.market_orders
  SET status='COMPLETED',updated_at=now()
  WHERE id=p_market_order_id;

  IF v_customer IS NOT NULL THEN
    INSERT INTO public.notifications(user_id,title,message,type,reference_id)
    SELECT v_customer,'จัดส่งสำเร็จ','Rider ส่งสินค้าถึงจุดส่งเรียบร้อยแล้ว','order',v_lead
    WHERE NOT EXISTS(
      SELECT 1 FROM public.notifications n
      WHERE n.user_id=v_customer
        AND n.reference_id=v_lead
        AND n.type='order'
        AND n.title='จัดส่งสำเร็จ'
    );
  END IF;

  RETURN 'completed';
END
$function$;

REVOKE ALL ON FUNCTION public.qg_pickup_with_photo(uuid,text,double precision,double precision) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_pickup_with_photo(uuid,text,double precision,double precision) TO authenticated;

REVOKE ALL ON FUNCTION public.qg_market_pickup_with_photo(uuid,numeric,text,double precision,double precision) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_market_pickup_with_photo(uuid,numeric,text,double precision,double precision) TO authenticated;

REVOKE ALL ON FUNCTION public.qg_complete_with_photo(uuid,text,double precision,double precision) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_complete_with_photo(uuid,text,double precision,double precision) TO authenticated;

REVOKE ALL ON FUNCTION public.qg_complete_market_with_photo(uuid,text,double precision,double precision) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_complete_market_with_photo(uuid,text,double precision,double precision) TO authenticated;

-- Retire PIN-based application routes without dropping historical structures.
REVOKE EXECUTE ON FUNCTION public.qg_complete_with_proof(uuid,text,double precision,double precision,text,text) FROM authenticated;
REVOKE EXECUTE ON FUNCTION public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text,text) FROM authenticated;
REVOKE EXECUTE ON FUNCTION public.qg_customer_delivery_pin(uuid) FROM authenticated;

COMMIT;
