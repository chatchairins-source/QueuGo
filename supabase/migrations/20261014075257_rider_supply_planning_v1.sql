create or replace function qg_private.rider_supply_plan()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_sample integer := 0;
  v_windows jsonb := '[]'::jsonb;
  v_confidence text := 'low';
begin
  perform qg_private.assert_live_session();

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  select count(distinct coalesce(o.market_order_id,o.id))::integer
  into v_sample
  from public.orders o
  where o.order_type='shopping'
    and o.created_at >= now()-interval '28 days'
    and o.status <> 'cancelled';

  v_confidence := case
    when v_sample >= 100 then 'high'
    when v_sample >= 40 then 'medium'
    else 'low'
  end;

  if v_sample >= 20 then
    with trip_slots as (
      select distinct
        coalesce(o.market_order_id,o.id) as trip_id,
        extract(isodow from timezone('Asia/Bangkok',o.created_at))::integer as day_of_week,
        (floor(extract(hour from timezone('Asia/Bangkok',o.created_at))/2.0)*2)::integer as start_hour
      from public.orders o
      where o.order_type='shopping'
        and o.created_at >= now()-interval '28 days'
        and o.status <> 'cancelled'
    ),
    slot_counts as (
      select day_of_week,start_hour,count(*)::integer as demand_count
      from trip_slots
      group by day_of_week,start_hour
    ),
    scored as (
      select
        day_of_week,
        start_hour,
        demand_count,
        max(demand_count) over() as max_count
      from slot_counts
    ),
    top_slots as (
      select
        day_of_week,
        start_hour,
        case
          when demand_count >= greatest(1,ceil(max_count*0.67)) then 3
          when demand_count >= greatest(1,ceil(max_count*0.34)) then 2
          else 1
        end as level
      from scored
      order by demand_count desc,day_of_week,start_hour
      limit 6
    )
    select coalesce(
      jsonb_agg(
        jsonb_build_object(
          'day_of_week',day_of_week,
          'start_hour',start_hour,
          'end_hour',least(start_hour+2,24),
          'level',level
        )
        order by level desc,day_of_week,start_hour
      ),
      '[]'::jsonb
    )
    into v_windows
    from top_slots;
  end if;

  return jsonb_build_object(
    'ready',v_sample >= 20,
    'confidence',v_confidence,
    'source_days',28,
    'windows',v_windows,
    'forecast',false,
    'basis','historical_orders'
  );
end $$;

revoke all on function qg_private.rider_supply_plan() from public, anon;
grant execute on function qg_private.rider_supply_plan() to authenticated;

create or replace function public.qg_rider_supply_plan()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_supply_plan(); $$;

revoke all on function public.qg_rider_supply_plan() from public, anon;
grant execute on function public.qg_rider_supply_plan() to authenticated, service_role;
