-- QueueGo Laundry customer catalog.
-- Exposes only enabled Laundry shops/services that currently have at least one accepted active Laundry Rider.
create or replace function public.queuego_laundry_customer_catalog()
returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare v_user uuid;
begin
  if auth.uid() is null then raise exception 'customer login required'; end if;
  select id into v_user from public.users
   where auth_user_id=auth.uid() and role='customer' and status='active';
  if v_user is null then raise exception 'active customer required'; end if;
  if not public.queuego_feature_enabled('laundry',now()) then
    return jsonb_build_object('enabled',false,'hubs','[]'::jsonb);
  end if;

  return jsonb_build_object(
    'enabled',true,
    'hubs',coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'id',h.id,
          'name',h.name,
          'shop_id',s.id,
          'shop_name',s.shop_name,
          'address',s.address,
          'latitude',s.latitude,
          'longitude',s.longitude,
          'settings',jsonb_build_object(
            'minimum_order',st.minimum_order,
            'pickup_fee',st.base_pickup_fee,
            'return_fee',st.return_fee,
            'round_trip_fee',st.round_trip_fee,
            'delivery_fee_mode',st.delivery_fee_mode
          ),
          'services',coalesce((
            select jsonb_agg(
              jsonb_build_object(
                'id',sv.id,'code',sv.code,'name',sv.name,'description',sv.description,
                'pricing_type',sv.pricing_type,'price',sv.price,
                'estimated_minutes',sv.estimated_minutes
              )
              order by sv.sort_order,sv.created_at
            )
            from public.laundry_services sv
            where sv.hub_id=h.id and sv.active=true
          ),'[]'::jsonb)
        )
        order by h.name
      )
      from public.laundry_hubs h
      join public.shop_profiles s on s.id=h.shop_id
      join public.laundry_shop_settings st on st.hub_id=h.id
      where h.active=true
        and s.status='active'
        and st.enabled=true
        and st.accepts_pickup=true
        and st.accepts_return=true
        and exists(select 1 from public.laundry_services sv where sv.hub_id=h.id and sv.active=true)
        and exists(
          select 1
          from public.laundry_hub_riders hr
          join public.rider_profiles r on r.id=hr.rider_id
          join public.users u on u.id=r.user_id
          where hr.hub_id=h.id and hr.active=true
            and r.status='active' and u.status='active' and u.role='rider'
        )
    ),'[]'::jsonb)
  );
end $$;

revoke all on function public.queuego_laundry_customer_catalog() from public,anon;
grant execute on function public.queuego_laundry_customer_catalog() to authenticated,service_role;
