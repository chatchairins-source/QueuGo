create or replace function qg_private.guard_ride_order_write()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $function$
begin
 if tg_op='UPDATE' and old.fulfillment_vertical='ride' then
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
