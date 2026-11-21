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
$function$
