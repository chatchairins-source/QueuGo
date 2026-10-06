-- QueueGo Rider photo-only flow hardening.
-- Remove executable PIN routes, revoke anonymous photo actions, and make
-- every market pickup require a persisted pickup photo before cash handoff.
BEGIN;

CREATE OR REPLACE FUNCTION public.market_rider_confirm_pickup_cash(
  p_pickup_id uuid,
  p_amount numeric
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v public.market_order_pickups%rowtype;
  v_rider uuid;
  v_remaining integer;
  v_order_status text;
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

  SELECT mp.* INTO v
  FROM public.market_order_pickups mp
  JOIN public.orders o ON o.id=mp.order_id
  WHERE mp.id=p_pickup_id
    AND o.rider_id=v_rider
  FOR UPDATE OF mp;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'pickup unavailable';
  END IF;

  SELECT o.status INTO v_order_status
  FROM public.orders o
  WHERE o.id=v.order_id;

  IF v.status='PICKED_UP' THEN
    RETURN jsonb_build_object(
      'pickup_id',v.id,
      'status',v.status,
      'shop_amount',v.shop_amount,
      'cash_paid_amount',v.cash_paid_amount,
      'replayed',true
    );
  END IF;

  IF v.status<>'READY' OR v_order_status<>'ready' THEN
    RAISE EXCEPTION 'merchant must mark pickup ready first';
  END IF;

  IF NOT EXISTS(
    SELECT 1
    FROM public.qg_pickup_proofs p
    WHERE p.order_id=v.order_id
      AND p.rider_id=v_rider
      AND p.photo_path IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'pickup photo required';
  END IF;

  IF round(coalesce(p_amount,0),2)<>round(v.shop_amount,2) THEN
    RAISE EXCEPTION 'cash amount mismatch';
  END IF;

  INSERT INTO public.rider_cash_advances(order_id,rider_id,shop_id,amount)
  VALUES(v.order_id,v_rider,v.shop_id,v.shop_amount)
  ON CONFLICT(order_id) DO NOTHING;

  INSERT INTO public.merchant_cash_receipts(order_id,shop_id,amount,confirmed_by)
  SELECT v.order_id,v.shop_id,v.shop_amount,r.user_id
  FROM public.rider_profiles r
  WHERE r.id=v_rider
  ON CONFLICT(order_id) DO NOTHING;

  INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,metadata)
  SELECT r.user_id,'market_pickup','order',v.order_id,
    jsonb_build_object(
      'source','photo_pickup',
      'pickup_id',v.id,
      'shop_id',v.shop_id,
      'rider_id',v_rider,
      'amount',v.shop_amount,
      'pickup_photo_required',true
    )
  FROM public.rider_profiles r
  WHERE r.id=v_rider;

  UPDATE public.market_order_pickups
  SET status='PICKED_UP',
      cash_paid_amount=v.shop_amount,
      cash_paid_at=now(),
      picked_up_at=now(),
      updated_at=now()
  WHERE id=v.id;

  SELECT count(*) INTO v_remaining
  FROM public.market_order_pickups
  WHERE market_order_id=v.market_order_id
    AND status NOT IN('PICKED_UP','CANCELLED');

  IF v_remaining=0 THEN
    PERFORM set_config('queuego.v22_transition','rpc',true);

    UPDATE public.orders o
    SET status='picked_up',
        picked_up_at=coalesce(o.picked_up_at,now()),
        note='__QT_ORDER_STATUS__=picked_up'||E'\n'||
          regexp_replace(coalesce(o.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g'),
        updated_at=now()
    WHERE o.market_order_id=v.market_order_id
      AND o.status<>'cancelled';

    UPDATE public.deliveries d
    SET status='picked_up',
        picked_up_at=coalesce(d.picked_up_at,now()),
        updated_at=now()
    FROM public.orders o
    WHERE o.id=d.order_id
      AND o.market_order_id=v.market_order_id
      AND o.status<>'cancelled';
  END IF;

  RETURN jsonb_build_object(
    'pickup_id',v.id,
    'status','PICKED_UP',
    'shop_amount',v.shop_amount,
    'cash_paid_amount',v.shop_amount,
    'remaining_pickups',v_remaining,
    'all_picked_up',v_remaining=0,
    'replayed',false
  );
END
$function$;

-- The active product has no customer delivery PIN. Remove callable PIN-era RPCs.
DROP FUNCTION IF EXISTS public.qg_customer_delivery_pin(uuid);
DROP FUNCTION IF EXISTS public.qg_complete_with_proof(uuid,text,double precision,double precision,text,text);
DROP FUNCTION IF EXISTS public.qg_complete_market_with_proof(uuid,text,double precision,double precision,text,text);
DROP FUNCTION IF EXISTS public.qg_issue_delivery_pin();
DROP FUNCTION IF EXISTS public.qg_market_sync_delivery_pin();

-- Only authenticated Riders may invoke photo-backed mutation RPCs.
REVOKE ALL ON FUNCTION public.qg_pickup_with_photo(uuid,text,double precision,double precision) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.qg_market_pickup_with_photo(uuid,numeric,text,double precision,double precision) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.qg_complete_with_photo(uuid,text,double precision,double precision) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.qg_complete_market_with_photo(uuid,text,double precision,double precision) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.qg_pickup_with_photo(uuid,text,double precision,double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.qg_market_pickup_with_photo(uuid,numeric,text,double precision,double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.qg_complete_with_photo(uuid,text,double precision,double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.qg_complete_market_with_photo(uuid,text,double precision,double precision) TO authenticated;

COMMIT;
