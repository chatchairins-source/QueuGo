-- QueueGo Rider strict active-order flow
-- Authoritative states only: READY -> PICKED_UP -> IN_PROGRESS -> COMPLETED.
-- Also lets the assigned Rider read the order items that the UI must verify.
BEGIN;

DROP POLICY IF EXISTS order_items_rider_select ON public.order_items;
CREATE POLICY order_items_rider_select
ON public.order_items
FOR SELECT
TO authenticated
USING (
  EXISTS (
    SELECT 1
    FROM public.orders o
    JOIN public.rider_profiles r ON r.id=o.rider_id
    WHERE o.id=order_items.order_id
      AND r.user_id=public.get_my_user_id()
  )
);

CREATE OR REPLACE FUNCTION public.rider_order_action(p_order_id uuid, p_action text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_user uuid;
  v_order public.orders%rowtype;
  v_next text;
  v_clean_note text;
  v_action text:=lower(trim(coalesce(p_action,'')));
BEGIN
  SELECT r.id,r.user_id INTO v_rider,v_user
  FROM public.rider_profiles r
  JOIN public.users u ON u.id=r.user_id
  WHERE u.auth_user_id=auth.uid()
    AND u.role='rider'
    AND u.status='active';

  IF v_rider IS NULL THEN RAISE EXCEPTION 'active rider login required'; END IF;

  SELECT * INTO v_order
  FROM public.orders
  WHERE id=p_order_id AND rider_id=v_rider
  FOR UPDATE;

  IF NOT FOUND THEN RAISE EXCEPTION 'order unavailable'; END IF;

  IF v_action='arrive_shop' THEN
    IF v_order.rider_arrived_shop_at IS NOT NULL THEN RETURN v_order.status; END IF;
    IF v_order.status NOT IN ('rider_assigned','assigned','preparing','ready') THEN
      RAISE EXCEPTION 'order is not at pickup stage';
    END IF;

    PERFORM set_config('queuego.v22_transition','rpc',true);
    UPDATE public.orders
    SET rider_arrived_shop_at=now(),updated_at=now()
    WHERE id=p_order_id;

    INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
    VALUES(v_user,'rider_arrived_shop','order',p_order_id,'rider arrived at pickup',
      jsonb_build_object('rider_id',v_rider,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id,'order_status',v_order.status));

    INSERT INTO public.notifications(user_id,title,message,type,reference_id)
    SELECT sp.user_id,'ไรเดอร์ถึงร้านแล้ว','ไรเดอร์มาถึงร้านเพื่อรับออเดอร์ '||coalesce(v_order.order_number,p_order_id::text),'order',p_order_id
    FROM public.shop_profiles sp
    WHERE sp.id=v_order.shop_id;

    RETURN v_order.status;
  END IF;

  -- Old cached clients may still send "arrive"; collapse it into the real completion action.
  IF v_action='arrive' THEN v_action:='complete'; END IF;

  IF v_order.status='completed' THEN RETURN 'completed'; END IF;

  -- Repeat-safe responses for retried one-tap actions.
  IF v_action='pickup_cash' AND v_order.status IN ('picked_up','in_progress') THEN
    RETURN v_order.status;
  ELSIF v_action='deliver' AND v_order.status='in_progress' THEN
    RETURN 'in_progress';
  END IF;

  IF v_action='pickup_cash' AND v_order.status='ready' THEN
    v_next:='picked_up';

    INSERT INTO public.rider_cash_advances(order_id,rider_id,shop_id,amount)
    VALUES(p_order_id,v_rider,v_order.shop_id,v_order.subtotal)
    ON CONFLICT (order_id) DO NOTHING;

    INSERT INTO public.merchant_cash_receipts(order_id,shop_id,amount,confirmed_by)
    VALUES(p_order_id,v_order.shop_id,v_order.subtotal,v_user)
    ON CONFLICT (order_id) DO NOTHING;

    UPDATE public.deliveries
    SET status='picked_up',picked_up_at=coalesce(picked_up_at,now()),updated_at=now()
    WHERE order_id=p_order_id;

  ELSIF v_action='deliver' AND v_order.status='picked_up' THEN
    IF NOT EXISTS(SELECT 1 FROM public.rider_cash_advances WHERE order_id=p_order_id) THEN
      RAISE EXCEPTION 'cash advance not recorded';
    END IF;
    v_next:='in_progress';

  ELSIF v_action='complete' AND v_order.status='in_progress' THEN
    IF NOT EXISTS(SELECT 1 FROM public.rider_cash_advances WHERE order_id=p_order_id) THEN
      RAISE EXCEPTION 'cash advance not recorded';
    END IF;
    v_next:='completed';

    UPDATE public.deliveries
    SET status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now()
    WHERE order_id=p_order_id;
  ELSE
    RAISE EXCEPTION 'order status changed; refresh';
  END IF;

  v_clean_note:=regexp_replace(
    coalesce(v_order.note,''),
    E'^(__QT_ORDER_STATUS__=[^\\n]*\\n?)+',
    '',
    'g'
  );

  PERFORM set_config('queuego.v22_transition','rpc',true);

  UPDATE public.orders
  SET status=v_next,
      note='__QT_ORDER_STATUS__='||v_next||E'\n'||v_clean_note,
      updated_at=now(),
      picked_up_at=CASE WHEN v_next='picked_up' THEN coalesce(picked_up_at,now()) ELSE picked_up_at END,
      delivering_at=CASE WHEN v_next='in_progress' THEN coalesce(delivering_at,now()) ELSE delivering_at END,
      completed_at=CASE WHEN v_next='completed' THEN coalesce(completed_at,now()) ELSE completed_at END
  WHERE id=p_order_id;

  IF v_next='completed' THEN
    UPDATE public.payments
    SET status='paid',paid_at=coalesce(paid_at,now()),updated_at=now()
    WHERE order_id=p_order_id
      AND payment_method='cash'
      AND status='pending';
  END IF;

  INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  VALUES(v_user,'rider_order_action','order',p_order_id,v_action,
    jsonb_build_object(
      'from',v_order.status,
      'to',v_next,
      'cash_paid_to_shop',v_action='pickup_cash',
      'cash_collected',v_next='completed',
      'source','order_action',
      'customer_id',v_order.customer_id,
      'shop_id',v_order.shop_id,
      'rider_id',v_rider
    ));

  IF v_order.customer_id IS NOT NULL THEN
    INSERT INTO public.notifications(user_id,title,message,type,reference_id)
    VALUES(
      v_order.customer_id,
      'อัปเดตออเดอร์',
      CASE v_action
        WHEN 'pickup_cash' THEN 'ไรเดอร์ชำระเงินสดให้ร้านและรับสินค้าแล้ว'
        WHEN 'deliver' THEN 'ไรเดอร์กำลังนำสินค้าไปส่ง'
        ELSE 'จัดส่งสำเร็จ'
      END,
      'order',
      p_order_id
    );
  END IF;

  RETURN v_next;
END
$function$;

CREATE OR REPLACE FUNCTION public.market_rider_group_action(p_market_order_id uuid, p_action text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_rider uuid;
  v_order record;
  v_action text:=lower(trim(coalesce(p_action,'')));
BEGIN
  SELECT r.id INTO v_rider
  FROM public.rider_profiles r
  JOIN public.users u ON u.id=r.user_id
  WHERE u.auth_user_id=auth.uid()
    AND u.role='rider'
    AND u.status='active';

  IF v_rider IS NULL THEN RAISE EXCEPTION 'active rider required'; END IF;
  IF NOT EXISTS(
    SELECT 1 FROM public.orders
    WHERE market_order_id=p_market_order_id
      AND rider_id=v_rider
      AND status<>'cancelled'
  ) THEN
    RAISE EXCEPTION 'market order unavailable';
  END IF;

  -- Collapse stale "arrive" calls into the real completion action.
  IF v_action='arrive' THEN v_action:='complete'; END IF;

  PERFORM set_config('queuego.v22_transition','rpc',true);

  IF v_action='deliver' THEN
    IF NOT public.qg_market_group_can_deliver(p_market_order_id,v_rider) THEN
      RAISE EXCEPTION 'all pickups must be completed first';
    END IF;
    IF EXISTS(
      SELECT 1 FROM public.orders
      WHERE market_order_id=p_market_order_id
        AND status NOT IN ('picked_up','cancelled')
    ) THEN
      RAISE EXCEPTION 'market group not ready to deliver';
    END IF;

    UPDATE public.orders
    SET status='in_progress',
        delivering_at=coalesce(delivering_at,now()),
        note='__QT_ORDER_STATUS__=in_progress'||E'\n'||
          regexp_replace(coalesce(note,''),E'^(__QT_ORDER_STATUS__=[^\\n]*\\n?)+','','g'),
        updated_at=now()
    WHERE market_order_id=p_market_order_id
      AND status='picked_up';

    UPDATE public.deliveries d
    SET status='delivering',updated_at=now()
    FROM public.orders o
    WHERE o.id=d.order_id
      AND o.market_order_id=p_market_order_id
      AND o.status='in_progress';

    UPDATE public.market_orders
    SET status='IN_PROGRESS',updated_at=now()
    WHERE id=p_market_order_id;

    RETURN 'in_progress';

  ELSIF v_action='complete' THEN
    PERFORM 1
    FROM public.orders
    WHERE market_order_id=p_market_order_id
    ORDER BY id
    FOR UPDATE;

    IF EXISTS(
      SELECT 1
      FROM public.orders
      WHERE market_order_id=p_market_order_id
        AND status<>'cancelled'
        AND (rider_id IS DISTINCT FROM v_rider OR status NOT IN ('in_progress','completed'))
    ) THEN
      RAISE EXCEPTION 'market group not in progress';
    END IF;

    FOR v_order IN
      SELECT id FROM public.orders
      WHERE market_order_id=p_market_order_id
        AND status='in_progress'
      ORDER BY id
    LOOP
      PERFORM public.rider_order_action(v_order.id,'complete');
    END LOOP;

    UPDATE public.market_orders
    SET status='COMPLETED',updated_at=now()
    WHERE id=p_market_order_id;

    RETURN 'completed';
  ELSE
    RAISE EXCEPTION 'unsupported market action';
  END IF;
END
$function$;

-- Keep the optional proof RPC compatible, but it no longer requires an artificial "arrived" note.
CREATE OR REPLACE FUNCTION public.qg_complete_with_proof(
  p_order_id uuid,
  p_pin text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_photo_path text DEFAULT NULL
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

  IF v_rider IS NULL THEN RAISE EXCEPTION 'active rider required'; END IF;

  SELECT * INTO v_order
  FROM public.orders
  WHERE id=p_order_id AND rider_id=v_rider
  FOR UPDATE;

  IF FOUND AND v_order.status='completed' THEN RETURN 'completed'; END IF;
  IF NOT FOUND OR v_order.status<>'in_progress' THEN
    RAISE EXCEPTION 'order is not ready to complete';
  END IF;

  SELECT pin INTO v_pin FROM public.qg_delivery_pins WHERE order_id=p_order_id;
  IF v_pin IS NULL OR p_pin IS DISTINCT FROM v_pin THEN RAISE EXCEPTION 'incorrect delivery PIN'; END IF;
  IF (p_lat IS NULL)<>(p_lng IS NULL)
     OR (p_lat IS NOT NULL AND (abs(p_lat)>90 OR abs(p_lng)>180)) THEN
    RAISE EXCEPTION 'invalid GPS';
  END IF;
  IF p_photo_path IS NOT NULL AND (
    p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
    OR NOT EXISTS(
      SELECT 1 FROM storage.objects
      WHERE bucket_id='qg-evidence' AND name=p_photo_path
    )
  ) THEN
    RAISE EXCEPTION 'invalid delivery evidence';
  END IF;

  INSERT INTO public.qg_delivery_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path,otp_verified)
  VALUES(p_order_id,v_rider,p_lat,p_lng,p_photo_path,true)
  ON CONFLICT DO NOTHING;

  RETURN public.rider_order_action(p_order_id,'complete');
END
$function$;

COMMIT;
