-- Safe adoption of pre-v2 Laundry orders without guessing price or deleting data.
create or replace function public.queuego_laundry_adopt_legacy_order(
  p_order_id uuid,
  p_service_id uuid,
  p_estimated_quantity numeric default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
  v_order public.laundry_orders%rowtype;
  v_service public.laundry_services%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_pickup numeric;v_return numeric;v_delivery numeric;v_est numeric;v_total numeric;
begin
  if v_actor is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  select o.* into v_order
  from public.laundry_orders o
  join public.laundry_hubs h on h.id=o.hub_id
  join public.shop_profiles s on s.id=h.shop_id
  where o.id=p_order_id and s.user_id=v_actor
  for update of o;
  if not found then raise exception 'laundry order unavailable'; end if;
  if v_order.status<>'pending' then raise exception 'only pending legacy orders can be reconciled'; end if;
  if v_order.service_id is not null or v_order.pricing_type_snapshot is not null then
    return jsonb_build_object('id',v_order.id,'already_reconciled',true);
  end if;

  select * into v_service from public.laundry_services
   where id=p_service_id and hub_id=v_order.hub_id and active=true;
  if not found then raise exception 'active laundry service required'; end if;
  select * into v_settings from public.laundry_shop_settings where hub_id=v_order.hub_id;
  if not found then raise exception 'laundry settings unavailable'; end if;

  if v_service.pricing_type<>'fixed' and (p_estimated_quantity is null or p_estimated_quantity<=0 or p_estimated_quantity>1000) then
    raise exception 'estimated quantity required';
  end if;

  if v_settings.delivery_fee_mode='round_trip' then
    v_delivery:=greatest(coalesce(v_settings.round_trip_fee,0),0);
    v_pickup:=round(v_delivery/2,2);v_return:=v_delivery-v_pickup;
  else
    v_pickup:=greatest(coalesce(v_settings.base_pickup_fee,0),0);
    v_return:=greatest(coalesce(v_settings.return_fee,0),0);
    v_delivery:=v_pickup+v_return;
  end if;

  if v_service.pricing_type='fixed' then
    v_est:=greatest(v_service.price,v_settings.minimum_order);
    p_estimated_quantity:=1;
  else
    v_est:=greatest(round(v_service.price*p_estimated_quantity,2),v_settings.minimum_order);
  end if;
  v_total:=v_est+v_delivery;

  update public.laundry_orders
  set service_id=v_service.id,
      service_type=v_service.code,
      service_name_snapshot=v_service.name,
      pricing_type_snapshot=v_service.pricing_type,
      unit_price_snapshot=v_service.price,
      estimated_quantity=p_estimated_quantity,
      estimated_kg=case when v_service.pricing_type='per_kg' then p_estimated_quantity else estimated_kg end,
      pickup_fee_snapshot=v_pickup,
      return_fee_snapshot=v_return,
      round_trip_fee_snapshot=v_delivery,
      delivery_fee_mode_snapshot=v_settings.delivery_fee_mode,
      delivery_fee_total_snapshot=v_delivery,
      estimated_amount=v_est,
      estimated_total_amount=v_total,
      updated_at=now()
  where id=v_order.id
  returning * into v_order;

  insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
  values(v_order.id,'pending','pending',auth.uid(),'shop','Legacy Laundry order reconciled to current service/pricing');

  return jsonb_build_object(
    'id',v_order.id,'service_id',v_order.service_id,'service_name',v_order.service_name_snapshot,
    'estimated_quantity',v_order.estimated_quantity,'estimated_total',v_order.estimated_total_amount,
    'already_reconciled',false
  );
end $$;

revoke all on function public.queuego_laundry_adopt_legacy_order(uuid,uuid,numeric) from public,anon;
grant execute on function public.queuego_laundry_adopt_legacy_order(uuid,uuid,numeric) to authenticated,service_role;
