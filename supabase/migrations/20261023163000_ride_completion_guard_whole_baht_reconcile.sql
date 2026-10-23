-- QueueGo Passenger Ride successor reconciliation.
-- Keeps PR #675 as the UI/backend successor, then closes two production gaps:
-- 1) the order transition guard must allow in_progress -> completed without a prior dropoff/geofence flag;
-- 2) any still-active legacy fractional Ride snapshot is normalized atomically to whole baht.
-- The narrow queuego.ride_reprice mode only permits round(old_amount,0) normalization and keeps all other snapshot fields locked.

create or replace function qg_private.guard_ride_order_write()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $function$
begin
 if tg_op='UPDATE' and old.fulfillment_vertical='ride' then
   if current_setting('queuego.ride_reprice',true)='on'
      and new.fulfillment_vertical is not distinct from old.fulfillment_vertical
      and new.customer_id is not distinct from old.customer_id
      and new.shop_id is not distinct from old.shop_id
      and new.subtotal is not distinct from old.subtotal
      and new.ride_distance_km is not distinct from old.ride_distance_km
      and new.ride_duration_minutes is not distinct from old.ride_duration_minutes
      and new.ride_platform_fee is not distinct from old.ride_platform_fee
      and new.status is not distinct from old.status
      and new.rider_id is not distinct from old.rider_id
      and new.delivery_fee=round(old.delivery_fee,0)
      and new.total_amount=round(old.total_amount,0)
      and new.rider_delivery_payout=new.total_amount-coalesce(new.ride_platform_fee,0)
   then
     return new;
   end if;
   if current_setting('queuego.ride_action',true) is distinct from 'rpc' and not public.is_active_admin() then
     raise exception 'passenger ride transition requires authoritative RPC';
   end if;
   if new.fulfillment_vertical is distinct from old.fulfillment_vertical
      or new.customer_id is distinct from old.customer_id
      or new.shop_id is distinct from old.shop_id
      or new.subtotal is distinct from old.subtotal
      or new.delivery_fee is distinct from old.delivery_fee
      or new.total_amount is distinct from old.total_amount
      or new.ride_distance_km is distinct from old.ride_distance_km
      or new.ride_duration_minutes is distinct from old.ride_duration_minutes
      or new.ride_platform_fee is distinct from old.ride_platform_fee
      or new.rider_delivery_payout is distinct from old.rider_delivery_payout
   then raise exception 'passenger ride snapshot is immutable'; end if;
   if new.status is distinct from old.status and not (
      (old.status='searching_rider' and new.status in ('rider_assigned','cancelled','no_rider_available'))
      or (old.status='rider_assigned' and new.status='in_progress' and old.rider_arrived_shop_at is not null)
      or (old.status='in_progress' and new.status='completed')
   ) then raise exception 'invalid passenger ride transition'; end if;
 end if;
 return new;
end
$function$;

create or replace function public.queuego_guard_cash_payment()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
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
     and exists(select 1 from public.orders o where o.id=old.order_id and o.fulfillment_vertical='ride' and o.total_amount=new.amount)
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

create or replace function public.queuego_guard_cash_delivery()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
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
   and exists(select 1 from public.orders o where o.id=old.order_id and o.fulfillment_vertical='ride' and o.delivery_fee=new.delivery_fee)
 then return new; end if;

 v_role:=public.get_my_role();
 if v_role='admin' then return case when tg_op='DELETE' then old else new end; end if;
 if not exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then return case when tg_op='DELETE' then old else new end; end if;
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

 if new.order_id is distinct from old.order_id or new.delivery_fee is distinct from old.delivery_fee
 or new.distance_km is distinct from old.distance_km
 or new.pickup_address is distinct from old.pickup_address
 or new.pickup_latitude is distinct from old.pickup_latitude or new.pickup_longitude is distinct from old.pickup_longitude
 or new.delivery_address is distinct from old.delivery_address
 or new.delivery_latitude is distinct from old.delivery_latitude or new.delivery_longitude is distinct from old.delivery_longitude
 then raise exception 'cash delivery amount and location are locked'; end if;
 return new;
end
$function$;

select set_config('queuego.ride_reprice','on',true);
select set_config('queuego.ride_action','rpc',true);

with targets as materialized (
  select id,customer_id,total_amount as old_fare,round(total_amount,0) as new_fare
  from public.orders
  where fulfillment_vertical='ride'
    and status not in ('completed','cancelled','no_rider_available')
    and total_amount<>round(total_amount,0)
), updated as (
  update public.orders o
  set delivery_fee=t.new_fare,
      total_amount=t.new_fare,
      rider_delivery_payout=t.new_fare-coalesce(o.ride_platform_fee,0),
      updated_at=now()
  from targets t
  where o.id=t.id
  returning o.id,o.customer_id
)
insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
select t.customer_id,'ride_fare_normalized','order',t.id,'Active passenger ride fare normalized to whole baht',
       jsonb_build_object('from',t.old_fare,'to',t.new_fare,'source','whole_baht_policy_20261023')
from targets t
join updated u on u.id=t.id;

update public.payments p
set amount=o.total_amount,updated_at=now()
from public.orders o
where p.order_id=o.id
  and o.fulfillment_vertical='ride'
  and o.status not in ('completed','cancelled','no_rider_available')
  and p.payment_method='cash'
  and p.status='pending'
  and p.amount is distinct from o.total_amount;

update public.deliveries d
set delivery_fee=o.delivery_fee,updated_at=now()
from public.orders o
where d.order_id=o.id
  and o.fulfillment_vertical='ride'
  and o.status not in ('completed','cancelled','no_rider_available')
  and d.delivery_fee is distinct from o.delivery_fee;

update qg_private.ride_quotes q
set fare=o.total_amount,platform_fee=o.ride_platform_fee
from public.orders o
where q.order_id=o.id
  and o.fulfillment_vertical='ride'
  and o.status not in ('completed','cancelled','no_rider_available')
  and q.fare is distinct from o.total_amount;
