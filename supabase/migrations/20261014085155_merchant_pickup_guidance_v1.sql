create or replace function qg_private.rider_pickup_guide(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_result jsonb;
begin
  if p_order_id is null then
    raise exception 'order required';
  end if;

  select jsonb_build_object(
    'pickup_instructions',
      nullif(left(btrim(coalesce(s.metadata->>'riderPickupInstructions','')),300),''),
    'parking_instructions',
      nullif(left(btrim(coalesce(s.metadata->>'riderParkingInstructions','')),300),''),
    'market_zone', nullif(btrim(coalesce(s.market_zone,'')),''),
    'market_stall_no', nullif(btrim(coalesce(s.market_stall_no,'')),'')
  )
  into v_result
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  where o.id=p_order_id
    and (
      o.rider_id=v_rider
      or exists (
        select 1
        from public.qg_rider_order_offers f
        where f.order_id=o.id
          and f.rider_id=v_rider
          and f.expires_at>now()
      )
    )
  limit 1;

  if v_result is null then
    raise exception 'pickup guide unavailable' using errcode='42501';
  end if;

  return v_result;
end $$;

revoke all on function qg_private.rider_pickup_guide(uuid) from public, anon;
grant execute on function qg_private.rider_pickup_guide(uuid) to authenticated;

create or replace function public.qg_rider_pickup_guide(p_order_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_pickup_guide(p_order_id); $$;

revoke all on function public.qg_rider_pickup_guide(uuid) from public, anon;
grant execute on function public.qg_rider_pickup_guide(uuid) to authenticated, service_role;

create or replace function qg_private.rider_market_pickup_guides(p_market_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_result jsonb;
begin
  if p_market_order_id is null then
    raise exception 'market order required';
  end if;

  if not exists (
    select 1
    from public.orders o
    where o.market_order_id=p_market_order_id
      and o.rider_id=v_rider
      and o.status<>'cancelled'
  ) then
    raise exception 'market pickup guide unavailable' using errcode='42501';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'pickup_id',mp.id,
        'pickup_instructions',
          nullif(left(btrim(coalesce(s.metadata->>'riderPickupInstructions','')),300),''),
        'parking_instructions',
          nullif(left(btrim(coalesce(s.metadata->>'riderParkingInstructions','')),300),''),
        'market_zone', nullif(btrim(coalesce(s.market_zone,'')),''),
        'market_stall_no', nullif(btrim(coalesce(s.market_stall_no,'')),'')
      )
      order by mp.pickup_sequence,mp.created_at,mp.id
    ),
    '[]'::jsonb
  )
  into v_result
  from public.market_order_pickups mp
  join public.shop_profiles s on s.id=mp.shop_id
  where mp.market_order_id=p_market_order_id;

  return v_result;
end $$;

revoke all on function qg_private.rider_market_pickup_guides(uuid) from public, anon;
grant execute on function qg_private.rider_market_pickup_guides(uuid) to authenticated;

create or replace function public.qg_rider_market_pickup_guides(p_market_order_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_market_pickup_guides(p_market_order_id); $$;

revoke all on function public.qg_rider_market_pickup_guides(uuid) from public, anon;
grant execute on function public.qg_rider_market_pickup_guides(uuid) to authenticated, service_role;