-- QueueGo Express authenticated state surface for Customer/Rider/Admin clients.
-- Source-only staging. No feature enablement or Production mutation.
begin;

create or replace function public.queuego_express_state(p_order_id uuid default null)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_user uuid;
  v_role text;
  v_rider uuid;
  v_rows jsonb;
begin
  perform qg_private.assert_live_session();
  select id,role into v_user,v_role
  from public.users
  where auth_user_id=auth.uid() and status='active';

  if v_user is null or v_role not in ('customer','rider','admin') then
    raise exception 'active account required';
  end if;

  if v_role='rider' then
    select id into v_rider from public.rider_profiles where user_id=v_user;
  end if;

  select coalesce(jsonb_agg(x.data order by x.created_at desc),'[]'::jsonb)
  into v_rows
  from (
    select o.created_at,
      jsonb_build_object(
        'id',o.id,
        'order_number',o.order_number,
        'status',o.status,
        'rider_id',o.rider_id,
        'pickup_address',o.pickup_address,
        'pickup_latitude',o.pickup_latitude,
        'pickup_longitude',o.pickup_longitude,
        'delivery_address',o.delivery_address,
        'delivery_latitude',o.delivery_latitude,
        'delivery_longitude',o.delivery_longitude,
        'fare',o.total_amount,
        'rider_payout',o.rider_delivery_payout,
        'distance_km',o.express_distance_km,
        'duration_minutes',o.express_duration_minutes,
        'item_type',o.express_item_type,
        'weight_kg',o.express_weight_kg,
        'item_size',o.express_size,
        'vehicle_type',o.express_vehicle_type,
        'instructions',o.express_instructions,
        'sender_name',o.express_sender_name,
        'sender_phone',case when v_role in ('rider','admin') or o.customer_id=v_user then o.express_sender_phone end,
        'receiver_name',o.express_receiver_name,
        'receiver_phone',case when v_role in ('rider','admin') or o.customer_id=v_user then o.express_receiver_phone end,
        'arrived_pickup_at',o.rider_arrived_shop_at,
        'arrived_dropoff_at',o.rider_arrived_customer_at,
        'rider_name',ru.name,
        'rider_latitude',case when o.status in ('rider_assigned','picked_up','in_progress') then r.latitude end,
        'rider_longitude',case when o.status in ('rider_assigned','picked_up','in_progress') then r.longitude end
      ) data
    from public.orders o
    left join public.rider_profiles r on r.id=o.rider_id
    left join public.users ru on ru.id=r.user_id
    where o.fulfillment_vertical='express'
      and (p_order_id is null or o.id=p_order_id)
      and (
        v_role='admin'
        or (v_role='customer' and o.customer_id=v_user)
        or (v_role='rider' and o.rider_id=v_rider)
      )
    order by o.created_at desc
    limit 30
  ) x;

  return jsonb_build_object('express_orders',v_rows);
end $$;

revoke all on function public.queuego_express_state(uuid) from public,anon;
grant execute on function public.queuego_express_state(uuid) to authenticated;

commit;
