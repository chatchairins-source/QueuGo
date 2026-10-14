create or replace function qg_private.rider_order_load_summary(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_order public.orders%rowtype;
  v_capacity numeric;
  v_weight numeric;
  v_ratio numeric;
begin
  if p_order_id is null then
    raise exception 'order required';
  end if;

  select o.*
  into v_order
  from public.orders o
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

  if v_order.id is null then
    raise exception 'load summary unavailable' using errcode='42501';
  end if;

  select r.vehicle_capacity_kg
  into v_capacity
  from public.rider_profiles r
  where r.id=v_rider;

  if v_order.market_order_id is not null then
    select coalesce(sum(public.market_order_weight(o.id)),0)
    into v_weight
    from public.orders o
    where o.market_order_id=v_order.market_order_id
      and o.status<>'cancelled';
  elsif v_order.fulfillment_vertical in ('market','grocery') then
    v_weight := public.market_order_weight(v_order.id);
  else
    v_weight := null;
  end if;

  if v_weight is not null and v_weight<=0 then
    v_weight := null;
  end if;
  if v_capacity is not null and v_capacity<=0 then
    v_capacity := null;
  end if;

  if v_weight is not null and v_capacity is not null then
    v_ratio := round((v_weight/v_capacity)::numeric,4);
  else
    v_ratio := null;
  end if;

  return jsonb_build_object(
    'weight_kg',v_weight,
    'vehicle_capacity_kg',v_capacity,
    'utilization_ratio',v_ratio,
    'state',case
      when v_weight is null then 'unknown'
      when v_capacity is null then 'capacity_unknown'
      when v_ratio>1 then 'over_capacity'
      when v_ratio>=0.8 then 'near_capacity'
      else 'normal'
    end
  );
end $$;

revoke all on function qg_private.rider_order_load_summary(uuid) from public, anon;
grant execute on function qg_private.rider_order_load_summary(uuid) to authenticated;

create or replace function public.qg_rider_order_load_summary(p_order_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_order_load_summary(p_order_id); $$;

revoke all on function public.qg_rider_order_load_summary(uuid) from public, anon;
grant execute on function public.qg_rider_order_load_summary(uuid) to authenticated, service_role;