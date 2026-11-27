-- REVIEW ONLY, NOT A MIGRATION. No Production SQL writes.
-- Based on exact 2026-11-21 pg_get_functiondef snapshots, fail closed on drift.
BEGIN;
DO $guard$
BEGIN
 IF md5(pg_get_functiondef('public.queuego_guard_cash_order()'::regprocedure)) <> 'faec72ee314f12ef37a2c72005c44bc0'
 THEN RAISE EXCEPTION 'queuego_guard_cash_order source drift'; END IF;
 IF md5(pg_get_functiondef('public.queuego_guard_cash_payment()'::regprocedure)) <> 'cd73f6eec0a50bb8fd02fa2684ed685d'
 THEN RAISE EXCEPTION 'queuego_guard_cash_payment source drift'; END IF;
 IF md5(pg_get_functiondef('public.queuego_guard_cash_delivery()'::regprocedure)) <> '034a0abb8a4743ae46325d1923022126'
 THEN RAISE EXCEPTION 'queuego_guard_cash_delivery source drift'; END IF;
END $guard$;
CREATE OR REPLACE FUNCTION public.queuego_guard_cash_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_role text;v_own_rider uuid;
begin
  if old.fulfillment_vertical='ride' and current_setting('queuego.ride_action',true)='rpc' then return new; end if;
  if not exists(select 1 from public.queuego_cash_order_locks where order_id=old.id) then
    return new;
  end if;

  if current_setting('queuego.market_recalc',true)='on'
     and old.market_order_id is not null
     and new.market_order_id=old.market_order_id then
    return new;
  end if;

  -- P0: trusted cron-only order terminalization; preserve all other cash guards.
  IF session_user='postgres' AND current_setting('queuego.timeout_cancel',true)='v1' AND old.status='searching_rider' AND new.status='cancelled'
  AND old.sales_channel='QUEUEGO_DELIVERY' AND old.order_type='shopping'
  AND old.market_order_id IS NULL AND old.rider_id IS NULL AND new.rider_id IS NULL
  AND old.rider_assigned_at IS NULL AND new.rider_assigned_at IS NULL
  AND old.paid_at IS NULL AND new.paid_at IS NULL AND new.cancelled_at IS NOT NULL
  AND old.created_at IS NOT NULL AND old.rider_search_started_at IS NOT NULL
 AND old.rider_search_started_at>=old.created_at AND old.rider_search_started_at<=now()
 AND (old.merchant_accepted_at IS NULL OR old.rider_search_started_at>=old.merchant_accepted_at)
 AND now()>=old.rider_search_started_at+make_interval(mins=>coalesce(
 (SELECT CASE WHEN (s.value->>'orderTimeout') ~ '^[0-9]{1,4}$'
 AND (s.value->>'orderTimeout')::integer BETWEEN 1 AND 1440
 THEN (s.value->>'orderTimeout')::integer END
 FROM public.system_settings s WHERE s.key='app'),30))
  AND (to_jsonb(new)-ARRAY['status','cancelled_at','updated_at']::text[])
     =(to_jsonb(old)-ARRAY['status','cancelled_at','updated_at']::text[])
  THEN RETURN NEW; END IF;
  v_role:=public.get_my_role();

  if current_setting('queuego.merchant_order_edit',true)='on'
     and v_role='shop'
     and new.id=old.id
     and new.status=old.status
     and new.rider_id is not distinct from old.rider_id
     and new.customer_id is not distinct from old.customer_id
     and new.shop_id is not distinct from old.shop_id
     and new.delivery_fee is not distinct from old.delivery_fee
     and new.market_order_id is null
     and old.market_order_id is null then
    return new;
  end if;

  if v_role='admin' then return new; end if;

  if current_setting('queuego.route_bundle_reprice',true)='on'
     and v_role='rider'
     and old.market_order_id is null
     and new.market_order_id is null then
    select id into v_own_rider
    from public.rider_profiles
    where user_id=public.get_my_user_id();

    if v_own_rider is not null
       and new.order_number is not distinct from old.order_number
       and new.customer_id is not distinct from old.customer_id
       and new.shop_id is not distinct from old.shop_id
       and new.order_type is not distinct from old.order_type
       and new.subtotal is not distinct from old.subtotal
       and new.gp_rate is not distinct from old.gp_rate
       and new.gp_amount is not distinct from old.gp_amount
       and new.pickup_address is not distinct from old.pickup_address
       and new.pickup_latitude is not distinct from old.pickup_latitude
       and new.pickup_longitude is not distinct from old.pickup_longitude
       and new.delivery_address is not distinct from old.delivery_address
       and new.delivery_latitude is not distinct from old.delivery_latitude
       and new.delivery_longitude is not distinct from old.delivery_longitude
       and new.created_by is not distinct from old.created_by
       and (
         (
           old.status='searching_rider' and old.rider_id is null
           and new.status='rider_assigned' and new.rider_id=v_own_rider
           and new.delivery_fee<=old.delivery_fee
           and new.total_amount=old.total_amount-old.delivery_fee+new.delivery_fee
           and new.route_bundle_id is not null
           and new.route_bundle_sequence>1
           and new.bundle_original_delivery_fee=old.delivery_fee
           and new.bundle_customer_savings=old.delivery_fee-new.delivery_fee
           and new.bundle_rider_extra_fee=new.delivery_fee
         )
         or
         (
           old.rider_id=v_own_rider and new.rider_id=old.rider_id
           and new.status=old.status
           and new.delivery_fee=old.delivery_fee
           and new.total_amount=old.total_amount
           and old.route_bundle_id is null
           and new.route_bundle_id is not null
           and new.route_bundle_sequence=1
           and new.bundle_customer_savings=0
           and new.bundle_rider_extra_fee=0
         )
       ) then
      return new;
    end if;
  end if;

  if new.order_number is distinct from old.order_number
     or new.customer_id is distinct from old.customer_id
     or new.shop_id is distinct from old.shop_id
     or new.order_type is distinct from old.order_type
     or new.subtotal is distinct from old.subtotal
     or new.delivery_fee is distinct from old.delivery_fee
     or new.total_amount is distinct from old.total_amount
     or new.gp_rate is distinct from old.gp_rate
     or new.gp_amount is distinct from old.gp_amount
     or new.pickup_address is distinct from old.pickup_address
     or new.pickup_latitude is distinct from old.pickup_latitude
     or new.pickup_longitude is distinct from old.pickup_longitude
     or new.delivery_address is distinct from old.delivery_address
     or new.delivery_latitude is distinct from old.delivery_latitude
     or new.delivery_longitude is distinct from old.delivery_longitude
     or new.created_by is distinct from old.created_by
     or new.route_bundle_id is distinct from old.route_bundle_id
     or new.route_bundle_sequence is distinct from old.route_bundle_sequence
     or new.bundle_original_delivery_fee is distinct from old.bundle_original_delivery_fee
     or new.bundle_customer_savings is distinct from old.bundle_customer_savings
     or new.bundle_rider_extra_fee is distinct from old.bundle_rider_extra_fee
     or new.bundle_detour_km is distinct from old.bundle_detour_km
     or new.bundle_added_minutes is distinct from old.bundle_added_minutes
     or new.bundle_route_sequence is distinct from old.bundle_route_sequence then
    raise exception 'order financial details are locked';
  end if;

  if new.rider_id is distinct from old.rider_id
     and not (
       v_role='rider'
       and old.rider_id is null
       and (
         (old.status='searching_rider' and new.status='rider_assigned')
         or (old.status='ready' and new.status='assigned')
       )
       and new.rider_id in(
         select id from public.rider_profiles
         where user_id=public.get_my_user_id()
       )
     ) then
    raise exception 'rider assignment is locked';
  end if;

  if new.status is distinct from old.status then
    if v_role='customer' then
      if not (
        old.status in('pending','searching_rider')
        and new.status='cancelled'
        and old.rider_id is null
      ) then
        raise exception 'customer cannot change order status';
      end if;
    elsif v_role='shop' then
      if not (
        (old.status,new.status) in(
          ('pending','searching_rider'),
          ('rider_assigned','preparing'),
          ('preparing','ready'),
          ('pending','accepted'),
          ('accepted','preparing')
        )
        or (
          new.status='cancelled'
          and old.status in(
            'pending','accepted','searching_rider',
            'rider_assigned','preparing','ready','assigned'
          )
        )
      ) then
        raise exception 'invalid shop order transition';
      end if;
    elsif v_role='rider' then
      if not (
        (old.status,new.status) in(
          ('searching_rider','rider_assigned'),
          ('ready','picked_up'),
          ('picked_up','in_progress'),
          ('in_progress','completed'),
          ('ready','assigned'),
          ('assigned','picked_up')
        )
        and (
          old.rider_id=new.rider_id
          or (old.rider_id is null and new.rider_id is not null)
        )
      ) then
        raise exception 'invalid rider order transition';
      end if;
    else
      raise exception 'order status change forbidden';
    end if;
  end if;

  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.queuego_guard_cash_payment()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_order uuid;v_role text;
begin
  v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  if not exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then
    return case when tg_op='DELETE' then old else new end;
  end if;

  if current_setting('queuego.ride_reprice',true)='on'
     and tg_op='UPDATE'
     and new.order_id is not distinct from old.order_id
     and new.payer_id is not distinct from old.payer_id
     and new.payment_method is not distinct from old.payment_method
     and new.status is not distinct from old.status
     and old.status='pending'
     and new.amount=round(old.amount,0)
     and exists(
       select 1 from public.orders o
       where o.id=old.order_id
         and o.fulfillment_vertical='ride'
         and o.total_amount=new.amount
     )
  then return new; end if;

  -- Only pending non-captured cash may be marked cancelled by trusted cron.
  IF tg_op='UPDATE' AND session_user='postgres' AND current_setting('queuego.timeout_cancel',true)='v1' AND old.status='pending' AND new.status='cancelled'
  AND old.payment_method='cash' AND old.paid_at IS NULL
  AND nullif(btrim(old.transaction_reference),'') IS NULL
  AND (to_jsonb(new)-ARRAY['status','updated_at']::text[])
     =(to_jsonb(old)-ARRAY['status','updated_at']::text[])
  AND EXISTS(SELECT 1 FROM public.orders o WHERE o.id=old.order_id
    AND o.status='cancelled' AND o.cancelled_at IS NOT NULL
    AND o.rider_id IS NULL AND o.total_amount=old.amount)
  THEN RETURN NEW; END IF;
  v_role:=public.get_my_role();

  if current_setting('queuego.merchant_order_edit',true)='on'
     and v_role='shop'
     and tg_op='UPDATE'
     and new.order_id is not distinct from old.order_id
     and new.payer_id is not distinct from old.payer_id
     and new.payment_method is not distinct from old.payment_method
     and new.status is not distinct from old.status
     and old.status='pending' then
    return new;
  end if;

  if v_role='admin' then
    return case when tg_op='DELETE' then old else new end;
  end if;

  if current_setting('queuego.route_bundle_reprice',true)='on'
     and v_role='rider' and tg_op='UPDATE'
     and new.order_id is not distinct from old.order_id
     and new.payer_id is not distinct from old.payer_id
     and new.payment_method is not distinct from old.payment_method
     and new.status is not distinct from old.status
     and old.status='pending'
     and new.amount<=old.amount
     and exists(
       select 1 from public.orders o
       join public.rider_profiles r on r.id=o.rider_id
       where o.id=old.order_id and o.route_bundle_id is not null
         and r.user_id=public.get_my_user_id()
         and new.amount=o.total_amount
     ) then
    return new;
  end if;

  if tg_op<>'UPDATE' then raise exception 'cash payment is locked'; end if;

  if new.order_id is distinct from old.order_id
     or new.payer_id is distinct from old.payer_id
     or new.amount is distinct from old.amount
     or new.payment_method is distinct from old.payment_method then
    raise exception 'cash payment amount is locked';
  end if;

  if not (
    (v_role='rider' and old.status='pending' and new.status='paid'
      and exists(
        select 1 from public.orders o
        join public.rider_profiles r on r.id=o.rider_id
        where o.id=old.order_id and o.status='completed'
          and r.user_id=public.get_my_user_id()
      ))
    or
    (old.status='pending' and new.status='cancelled' and exists(
      select 1 from public.orders o
      left join public.shop_profiles s on s.id=o.shop_id
      where o.id=old.order_id and o.status='cancelled'
        and (
          (v_role='customer' and o.customer_id=public.get_my_user_id())
          or
          (v_role='shop' and s.user_id=public.get_my_user_id())
        )
    ))
  ) then
    raise exception 'cash payment status change forbidden';
  end if;

  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.queuego_guard_cash_delivery()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_order uuid;v_src public.orders%rowtype;v_role text;
begin
 v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;

 if current_setting('queuego.ride_reprice',true)='on'
   and tg_op='UPDATE'
   and new.order_id is not distinct from old.order_id
   and new.rider_id is not distinct from old.rider_id
   and new.status is not distinct from old.status
   and new.distance_km is not distinct from old.distance_km
   and new.pickup_address is not distinct from old.pickup_address
   and new.pickup_latitude is not distinct from old.pickup_latitude
   and new.pickup_longitude is not distinct from old.pickup_longitude
   and new.delivery_address is not distinct from old.delivery_address
   and new.delivery_latitude is not distinct from old.delivery_latitude
   and new.delivery_longitude is not distinct from old.delivery_longitude
   and new.delivery_fee=round(old.delivery_fee,0)
   and exists(
     select 1 from public.orders o
     where o.id=old.order_id
       and o.fulfillment_vertical='ride'
       and o.delivery_fee=new.delivery_fee
   )
 then return new; end if;

  -- Only pending unassigned delivery may be closed by trusted cron.
  IF tg_op='UPDATE' AND session_user='postgres' AND current_setting('queuego.timeout_cancel',true)='v1' AND old.status='pending' AND new.status='cancelled'
  AND old.rider_id IS NULL AND new.rider_id IS NULL
  AND (to_jsonb(new)-ARRAY['status','updated_at']::text[])
     =(to_jsonb(old)-ARRAY['status','updated_at']::text[])
  AND EXISTS(SELECT 1 FROM public.orders o WHERE o.id=old.order_id
    AND o.status='cancelled' AND o.cancelled_at IS NOT NULL AND o.rider_id IS NULL)
  THEN RETURN NEW; END IF;
 v_role:=public.get_my_role();
 if v_role='admin' then return case when tg_op='DELETE' then old else new end; end if;
 if not exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then
   return case when tg_op='DELETE' then old else new end;
 end if;

 if tg_op='INSERT' then
   if current_setting('queuego.v22_transition',true) is distinct from 'rpc' then raise exception 'cash delivery is locked'; end if;
   select * into v_src from public.orders where id=new.order_id;
   if not found or new.rider_id is not null or new.status<>'pending'
      or new.delivery_fee is distinct from v_src.delivery_fee
      or new.pickup_address is distinct from v_src.pickup_address
      or new.pickup_latitude is distinct from v_src.pickup_latitude
      or new.pickup_longitude is distinct from v_src.pickup_longitude
      or new.delivery_address is distinct from v_src.delivery_address
      or new.delivery_latitude is distinct from v_src.delivery_latitude
      or new.delivery_longitude is distinct from v_src.delivery_longitude
   then raise exception 'invalid server delivery insert'; end if;
   return new;
 end if;

 if tg_op='DELETE' then raise exception 'cash delivery is locked'; end if;

 if current_setting('queuego.route_bundle_reprice',true)='on' and v_role='rider'
   and new.order_id is not distinct from old.order_id
   and new.distance_km is not distinct from old.distance_km
   and new.pickup_address is not distinct from old.pickup_address
   and new.pickup_latitude is not distinct from old.pickup_latitude
   and new.pickup_longitude is not distinct from old.pickup_longitude
   and new.delivery_address is not distinct from old.delivery_address
   and new.delivery_latitude is not distinct from old.delivery_latitude
   and new.delivery_longitude is not distinct from old.delivery_longitude
   and new.delivery_fee<=old.delivery_fee
   and exists(
     select 1 from public.orders o
     join public.rider_profiles r on r.id=o.rider_id
     where o.id=old.order_id and o.route_bundle_id is not null
       and r.user_id=public.get_my_user_id()
       and new.rider_id=o.rider_id
       and new.delivery_fee=o.delivery_fee
   )
 then return new; end if;

 if new.order_id is distinct from old.order_id
 or new.delivery_fee is distinct from old.delivery_fee
 or new.distance_km is distinct from old.distance_km
 or new.pickup_address is distinct from old.pickup_address
 or new.pickup_latitude is distinct from old.pickup_latitude
 or new.pickup_longitude is distinct from old.pickup_longitude
 or new.delivery_address is distinct from old.delivery_address
 or new.delivery_latitude is distinct from old.delivery_latitude
 or new.delivery_longitude is distinct from old.delivery_longitude
 then raise exception 'cash delivery amount and location are locked'; end if;

 return new;
end
$function$;

-- PRIVATE server-owned; session_user check and narrow guard context both required.
CREATE OR REPLACE FUNCTION qg_private.cancel_rider_timeout_cash(p_limit integer DEFAULT 30)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $qg$
DECLARE v record; v_minutes integer; v_count integer:=0;
BEGIN
 IF session_user IS DISTINCT FROM 'postgres' THEN
   RAISE EXCEPTION 'private cron session required' USING ERRCODE='42501';
 END IF;
 IF p_limit IS NULL OR p_limit<1 OR p_limit>100 THEN RAISE EXCEPTION 'invalid batch'; END IF;
 SELECT coalesce((SELECT CASE WHEN (value->>'orderTimeout') ~ '^[0-9]{1,4}$'
   AND (value->>'orderTimeout')::integer BETWEEN 1 AND 1440
   THEN (value->>'orderTimeout')::integer END
   FROM public.system_settings WHERE key='app'),30) INTO v_minutes;
 FOR v IN SELECT o.id,o.customer_id,o.shop_id,o.total_amount
 FROM public.orders o
 WHERE o.order_type='shopping' AND o.sales_channel='QUEUEGO_DELIVERY'
   AND o.market_order_id IS NULL AND o.status='searching_rider'
   AND o.rider_id IS NULL AND o.rider_assigned_at IS NULL AND o.paid_at IS NULL
   AND o.cancelled_at IS NULL AND o.created_at IS NOT NULL
   AND o.rider_search_started_at IS NOT NULL
   AND o.rider_search_started_at>=o.created_at AND o.rider_search_started_at<=now()
   AND (o.merchant_accepted_at IS NULL OR o.rider_search_started_at>=o.merchant_accepted_at)
   AND now()>=o.rider_search_started_at+make_interval(mins=>v_minutes)
 ORDER BY o.rider_search_started_at,o.id LIMIT p_limit FOR UPDATE OF o SKIP LOCKED
 LOOP
  BEGIN
   PERFORM 1 FROM public.payments p WHERE p.order_id=v.id FOR UPDATE;
   PERFORM 1 FROM public.deliveries d WHERE d.order_id=v.id FOR UPDATE;
   IF NOT EXISTS(SELECT 1 FROM public.queuego_cash_order_locks l WHERE l.order_id=v.id)
     OR (SELECT count(*) FROM public.payments p WHERE p.order_id=v.id)<>1
     OR (SELECT count(*) FROM public.deliveries d WHERE d.order_id=v.id)<>1
     OR NOT EXISTS(SELECT 1 FROM public.payments p WHERE p.order_id=v.id
       AND p.payment_method='cash' AND p.status='pending'
       AND p.paid_at IS NULL AND nullif(btrim(p.transaction_reference),'') IS NULL
       AND p.amount=v.total_amount AND p.amount>0
       AND p.amount::text NOT IN ('NaN','Infinity','-Infinity'))
     OR NOT EXISTS(SELECT 1 FROM public.deliveries d WHERE d.order_id=v.id
       AND d.status='pending' AND d.rider_id IS NULL)
     OR EXISTS(SELECT 1 FROM public.merchant_cash_receipts x WHERE x.order_id=v.id)
     OR EXISTS(SELECT 1 FROM public.rider_cash_advances x WHERE x.order_id=v.id)
     OR EXISTS(SELECT 1 FROM public.qg_rider_cash_return_refunds x WHERE x.order_id=v.id)
     OR EXISTS(SELECT 1 FROM public.grocery_stock_reservations x WHERE x.order_id=v.id)
     OR EXISTS(SELECT 1 FROM public.pos_inventory_reservations x WHERE x.order_id=v.id)
     OR EXISTS(SELECT 1 FROM public.orders o WHERE o.id=v.id AND
        (o.paid_at IS NOT NULL OR o.cancelled_at IS NOT NULL OR
         (o.payment_status IS NOT NULL AND lower(o.payment_status) NOT IN ('unpaid','pending'))))
   THEN CONTINUE; END IF;
   PERFORM pg_catalog.set_config('queuego.v22_transition','rpc',true);
   PERFORM pg_catalog.set_config('queuego.timeout_cancel','v1',true);
   UPDATE public.orders o SET status='cancelled',cancelled_at=now(),updated_at=now()
     WHERE o.id=v.id AND o.status='searching_rider' AND o.rider_id IS NULL;
   IF NOT FOUND THEN CONTINUE; END IF;
   -- Existing cash cancellation AFTER trigger must atomically update dependents.
   IF (SELECT count(*) FROM public.payments p WHERE p.order_id=v.id AND p.status='cancelled')<>1
     OR (SELECT count(*) FROM public.deliveries d WHERE d.order_id=v.id AND d.status='cancelled')<>1
   THEN RAISE EXCEPTION 'cash cancellation incomplete'; END IF;
   INSERT INTO public.qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
     SELECT f.order_id,f.rider_id,f.offered_at,now(),'expired',f.attempt
       FROM public.qg_rider_order_offers f WHERE f.order_id=v.id
     ON CONFLICT DO NOTHING;
   DELETE FROM public.qg_rider_order_offers WHERE order_id=v.id;
   INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
   VALUES(NULL,'no_rider_timeout','order',v.id,'cancelled after unclaimed Rider deadline',
       pg_catalog.jsonb_build_object('reason','NO_RIDER_TIMEOUT','minutes',v_minutes));
   IF v.customer_id IS NOT NULL THEN
     INSERT INTO public.notifications(user_id,title,message,type,reference_id)
     VALUES(v.customer_id,'ยกเลิกออเดอร์','ไม่มีไรเดอร์รับงานภายในเวลาที่กำหนด','order',v.id);
   END IF;
   INSERT INTO public.notifications(user_id,title,message,type,reference_id)
   SELECT s.user_id,'ยกเลิกออเดอร์','ไม่มีไรเดอร์รับงานภายในเวลาที่กำหนด','order',v.id
    FROM public.shop_profiles s WHERE s.id=v.shop_id;
   v_count:=v_count+1;
  EXCEPTION WHEN OTHERS THEN
    -- Each rejected order's update, payment, delivery, outbox roll back together.
    RAISE WARNING 'Timeout cancellation skipped, SQLSTATE %',SQLSTATE;
  END;
 END LOOP;
 RETURN v_count;
END $qg$;
REVOKE ALL ON FUNCTION qg_private.cancel_rider_timeout_cash(integer)
 FROM PUBLIC, anon, authenticated, service_role;
-- No cron scheduled here. Live deployment requires separate approved migration,
-- encrypted backup + actual isolated restore, approved finance/RLS review.

COMMIT;
