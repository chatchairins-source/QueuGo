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
