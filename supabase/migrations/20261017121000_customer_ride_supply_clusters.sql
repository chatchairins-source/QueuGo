-- Customer-visible Passenger Ride supply is privacy-preserving:
-- exact Rider coordinates and Rider identity are never returned before assignment.
-- A map cluster is emitted only when at least two available Riders occupy the same ~2 km cell.

create or replace function public.queuego_customer_ride_supply(
  p_lat double precision,
  p_lng double precision
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_customer uuid;
  v_nearby_count integer := 0;
  v_cells jsonb := '[]'::jsonb;
begin
  perform qg_private.assert_live_session();

  select u.id into v_customer
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='customer'
    and u.status='active'
  limit 1;

  if v_customer is null then
    raise exception 'active customer required' using errcode='42501';
  end if;

  if p_lat is null or p_lng is null
     or p_lat not between 5 and 21
     or p_lng not between 97 and 106 then
    raise exception 'valid pickup coordinate required';
  end if;

  with eligible as (
    select
      r.latitude,
      r.longitude,
      public.queuego_distance_km(p_lat,p_lng,r.latitude,r.longitude) as distance_km
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    where r.status='active'
      and u.role='rider'
      and u.status='active'
      and r.latitude between 5 and 21
      and r.longitude between 97 and 106
      and lower(coalesce(r.metadata->>'online','false'))='true'
      and lower(coalesce(r.metadata->>'available','false'))='true'
      and qg_private.ride_owner_authorized(r.id)
      and r.updated_at >= now()-interval '3 minutes'
      and not exists (
        select 1 from public.orders o
        where o.rider_id=r.id
          and o.status not in ('completed','cancelled','no_rider_available')
      )
  )
  select count(*)::integer into v_nearby_count
  from eligible
  where distance_km <= 8;

  with eligible as (
    select
      r.latitude,
      r.longitude,
      public.queuego_distance_km(p_lat,p_lng,r.latitude,r.longitude) as distance_km
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    where r.status='active'
      and u.role='rider'
      and u.status='active'
      and r.latitude between 5 and 21
      and r.longitude between 97 and 106
      and lower(coalesce(r.metadata->>'online','false'))='true'
      and lower(coalesce(r.metadata->>'available','false'))='true'
      and qg_private.ride_owner_authorized(r.id)
      and r.updated_at >= now()-interval '3 minutes'
      and not exists (
        select 1 from public.orders o
        where o.rider_id=r.id
          and o.status not in ('completed','cancelled','no_rider_available')
      )
      and public.queuego_distance_km(p_lat,p_lng,r.latitude,r.longitude) <= 8
  ),
  cells as (
    select
      (round(latitude::numeric / 0.02) * 0.02)::double precision as latitude,
      (round(longitude::numeric / 0.02) * 0.02)::double precision as longitude,
      count(*)::integer as rider_count
    from eligible
    group by 1,2
    having count(*) >= 2
    order by rider_count desc
    limit 12
  )
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'latitude',c.latitude,
        'longitude',c.longitude,
        'count',c.rider_count,
        'approximate',true
      )
      order by c.rider_count desc,c.latitude,c.longitude
    ),
    '[]'::jsonb
  ) into v_cells
  from cells c;

  return jsonb_build_object(
    'nearby_count',v_nearby_count,
    'radius_km',8,
    'clusters',v_cells,
    'precision','approximate_cluster',
    'refresh_seconds',15
  );
end
$$;

revoke all on function public.queuego_customer_ride_supply(double precision,double precision)
  from public,anon;
grant execute on function public.queuego_customer_ride_supply(double precision,double precision)
  to authenticated,service_role;
