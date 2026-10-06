-- QueueGo market-group proof completion audit alignment.
-- Preserve one atomic customer delivery while recording per-order Rider audit history.
BEGIN;

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
  v_user uuid;
  v_lead uuid;
  v_pin text;
  v_order record;
  v_customer uuid;
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

  -- Validate the full trip before mutating any order.
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

  INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  SELECT
    v_user,
    'rider_order_action',
    'order',
    o.id,
    'complete',
    jsonb_build_object(
      'from','in_progress',
      'to','completed',
      'cash_collected',true,
      'proof_required',true,
      'handoff_pin_verified',true,
      'source','market_proof',
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
        AND a.metadata->>'source'='market_proof'
    );

  UPDATE public.market_orders
  SET status='COMPLETED',updated_at=now()
  WHERE id=p_market_order_id;

  IF v_customer IS NOT NULL THEN
    INSERT INTO public.notifications(user_id,title,message,type,reference_id)
    SELECT v_customer,'จัดส่งสำเร็จ','Rider ส่งสินค้าจากตลาดถึงจุดส่งเรียบร้อยแล้ว','order',v_lead
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

REVOKE ALL ON FUNCTION public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text,text) TO authenticated;

COMMIT;
