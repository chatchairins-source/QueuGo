create or replace function qg_private.rider_demand_heatmap()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_session_id text;
  v_rider uuid;
  v_result jsonb;
begin
  v_session_id := auth.jwt()->>'session_id';
  if auth.uid() is null
     or v_session_id is null
     or v_session_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception 'active rider required' using errcode='42501';
  end if;

  if not exists (
    select 1
    from auth.sessions s
    where s.id = v_session_id::uuid
      and s.user_id = auth.uid()
      and (s.not_after is null or s.not_after > now())
  ) then
    raise exception 'active rider required' using errcode='42501';
  end if;

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id = r.user_id
  where u.auth_user_id = auth.uid()
    and u.role = 'rider'
    and u.status = 'active'
    and r.status = 'active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  with demand_cells as (
    select
      (round(o.pickup_latitude::numeric / 0.02) * 0.02)::double precision as latitude,
      (round(o.pickup_longitude::numeric / 0.02) * 0.02)::double precision as longitude,
      count(*)::integer as demand_count
    from public.orders o
    where o.order_type = 'shopping'
      and o.created_at >= now() - interval '24 hours'
      and o.status not in ('cancelled','no_rider_available')
      and o.pickup_latitude between 5.0 and 21.0
      and o.pickup_longitude between 97.0 and 106.0
    group by 1, 2
    having count(*) >= 2
    order by demand_count desc
    limit 25
  )
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'latitude', d.latitude,
        'longitude', d.longitude,
        'level', case
          when d.demand_count >= 7 then 3
          when d.demand_count >= 4 then 2
          else 1
        end,
        'window_hours', 24
      )
      order by d.demand_count desc, d.latitude, d.longitude
    ),
    '[]'::jsonb
  )
  into v_result
  from demand_cells d;

  return v_result;
end $$;

revoke all on function qg_private.rider_demand_heatmap() from public, anon;
grant execute on function qg_private.rider_demand_heatmap() to authenticated;

create or replace function public.qg_rider_demand_heatmap()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_demand_heatmap(); $$;

revoke all on function public.qg_rider_demand_heatmap() from public, anon;
grant execute on function public.qg_rider_demand_heatmap() to authenticated, service_role;