-- QueueGo Route Bundle v2: group visibility and safe delivery sequencing.
-- Additive to v1. Does not enable the feature; Admin switch remains authoritative.

create or replace function public.queuego_route_bundle_active_orders()
returns table(
  bundle_id uuid,
  order_id uuid,
  order_number text,
  bundle_sequence integer,
  drop_rank integer,
  status text,
  shop_id uuid,
  shop_name text,
  pickup_address text,
  pickup_latitude double precision,
  pickup_longitude double precision,
  delivery_address text,
  delivery_latitude double precision,
  delivery_longitude double precision,
  delivery_fee numeric,
  original_delivery_fee numeric,
  customer_savings numeric,
  rider_extra_fee numeric,
  detour_km numeric,
  added_minutes integer,
  route_sequence text
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_bundle uuid;
  v_flip boolean:=false;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider' and u.status='active'
    and r.status='active';
  if v_rider is null then return; end if;

  select rb.id into v_bundle
  from public.route_bundles rb
  where rb.rider_id=v_rider and rb.status='ACTIVE'
  order by rb.created_at
  limit 1;
  if v_bundle is null then return; end if;

  select exists(
    select 1 from public.orders o
    where o.route_bundle_id=v_bundle
      and o.route_bundle_sequence=2
      and o.bundle_route_sequence='BUNDLE_DROP_THEN_PRIMARY_DROP'
      and o.status<>'cancelled'
  ) into v_flip;

  return query
  select
    v_bundle,
    o.id,
    o.order_number,
    o.route_bundle_sequence,
    case
      when v_flip and o.route_bundle_sequence=2 then 1
      when v_flip and o.route_bundle_sequence=1 then 2
      else o.route_bundle_sequence
    end as drop_rank,
    o.status,
    o.shop_id,
    s.shop_name,
    o.pickup_address,
    o.pickup_latitude,
    o.pickup_longitude,
    o.delivery_address,
    o.delivery_latitude,
    o.delivery_longitude,
    o.delivery_fee,
    o.bundle_original_delivery_fee,
    o.bundle_customer_savings,
    o.bundle_rider_extra_fee,
    o.bundle_detour_km,
    o.bundle_added_minutes,
    o.bundle_route_sequence
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  where o.route_bundle_id=v_bundle
    and o.rider_id=v_rider
    and o.status<>'cancelled'
  order by o.route_bundle_sequence,o.created_at,o.id;
end
$$;

revoke all on function public.queuego_route_bundle_active_orders() from public,anon;
grant execute on function public.queuego_route_bundle_active_orders() to authenticated,service_role;

create or replace function public.qg_route_bundle_delivery_guard()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_first uuid;
  v_flip boolean:=false;
begin
  if old.route_bundle_id is null then return new; end if;
  if old.status is not distinct from new.status then return new; end if;

  -- Pickup remains per order. Delivery may start only after every live order
  -- in the bundle has been picked up, and only one customer is served at a time.
  if old.status='picked_up' and new.status='in_progress' then
    if exists(
      select 1 from public.orders o
      where o.route_bundle_id=old.route_bundle_id
        and o.status not in ('picked_up','completed','cancelled')
        and o.id<>old.id
    ) then
      raise exception 'all bundled pickups must be completed first';
    end if;

    if exists(
      select 1 from public.orders o
      where o.route_bundle_id=old.route_bundle_id
        and o.status='in_progress'
        and o.id<>old.id
    ) then
      raise exception 'finish current bundled delivery first';
    end if;

    select exists(
      select 1 from public.orders o
      where o.route_bundle_id=old.route_bundle_id
        and o.route_bundle_sequence=2
        and o.bundle_route_sequence='BUNDLE_DROP_THEN_PRIMARY_DROP'
        and o.status<>'cancelled'
    ) into v_flip;

    select o.id into v_first
    from public.orders o
    where o.route_bundle_id=old.route_bundle_id
      and o.status='picked_up'
    order by
      case
        when v_flip and o.route_bundle_sequence=2 then 1
        when v_flip and o.route_bundle_sequence=1 then 2
        else o.route_bundle_sequence
      end,
      o.created_at,o.id
    limit 1;

    if v_first is distinct from old.id then
      raise exception 'deliver bundled orders in route sequence';
    end if;
  end if;

  return new;
end
$$;

drop trigger if exists qg_route_bundle_delivery_guard_trg on public.orders;
create trigger qg_route_bundle_delivery_guard_trg
before update of status on public.orders
for each row
when (old.route_bundle_id is not null and old.status is distinct from new.status)
execute function public.qg_route_bundle_delivery_guard();

revoke all on function public.qg_route_bundle_delivery_guard() from public,anon,authenticated;
grant execute on function public.qg_route_bundle_delivery_guard() to service_role;

-- Candidate discovery should match claim requirements so the Rider does not see
-- an offer that immediately fails because the profile is not available.
create or replace function public.queuego_route_bundle_candidates()
returns table(
  order_id uuid,
  order_number text,
  shop_id uuid,
  shop_name text,
  pickup_address text,
  delivery_address text,
  pickup_latitude double precision,
  pickup_longitude double precision,
  delivery_latitude double precision,
  delivery_longitude double precision,
  detour_km numeric,
  added_minutes integer,
  standalone_delivery_fee numeric,
  bundled_delivery_fee numeric,
  customer_savings numeric,
  rider_extra_fee numeric,
  route_sequence text
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_primary uuid;
  v_bundle uuid;
  v_count integer;
  v_max integer;
  v_used_detour numeric:=0;
  v_used_minutes integer:=0;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider' and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true;
  if v_rider is null then return; end if;
  if not public.queuego_feature_enabled('route_bundle',now()) then return; end if;

  select rb.id,rb.primary_order_id
    into v_bundle,v_primary
  from public.route_bundles rb
  join public.orders po on po.id=rb.primary_order_id
  where rb.rider_id=v_rider and rb.status='ACTIVE'
    and po.status in ('rider_assigned','preparing','ready','picked_up','in_progress')
  order by rb.created_at
  limit 1;

  if v_primary is null then
    select o.id into v_primary
    from public.orders o
    join public.shop_profiles s on s.id=o.shop_id
    where o.rider_id=v_rider
      and o.status in ('rider_assigned','preparing','ready')
      and o.market_order_id is null
      and lower(coalesce(s.public_category,'')) in ('food','cafe','grocery')
    order by o.rider_assigned_at nulls last,o.created_at,o.id
    limit 1;
  end if;
  if v_primary is null then return; end if;

  -- Do not add another order after any order in the bundle was picked up.
  if v_bundle is not null and exists(
    select 1 from public.orders
    where route_bundle_id=v_bundle and status in ('picked_up','in_progress','completed')
  ) then return; end if;

  select count(*) into v_count
  from public.orders
  where rider_id=v_rider
    and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
  v_max:=greatest(1,least(5,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
  if v_count>=v_max then return; end if;

  if v_bundle is not null then
    select coalesce(sum(bundle_detour_km),0),coalesce(sum(bundle_added_minutes),0)
      into v_used_detour,v_used_minutes
    from public.orders
    where route_bundle_id=v_bundle and route_bundle_sequence>1 and status<>'cancelled';
  end if;

  return query
  with candidates as (
    select o.id,o.order_number,o.shop_id,s.shop_name,o.pickup_address,o.delivery_address,
           o.pickup_latitude,o.pickup_longitude,o.delivery_latitude,o.delivery_longitude,
           public.queuego_route_bundle_quote_orders(v_primary,o.id,now()) q
    from public.orders o
    join public.shop_profiles s on s.id=o.shop_id
    where o.status='searching_rider'
      and o.rider_id is null
      and o.order_type='shopping'
      and o.market_order_id is null
      and lower(coalesce(s.public_category,'')) in ('food','cafe','grocery')
      and o.id<>v_primary
  )
  select c.id,c.order_number,c.shop_id,c.shop_name,c.pickup_address,c.delivery_address,
         c.pickup_latitude,c.pickup_longitude,c.delivery_latitude,c.delivery_longitude,
         (c.q->>'detour_km')::numeric,
         (c.q->>'added_minutes')::integer,
         (c.q->>'standalone_delivery_fee')::numeric,
         (c.q->>'bundled_delivery_fee')::numeric,
         (c.q->>'customer_savings')::numeric,
         (c.q->>'rider_extra_fee')::numeric,
         c.q->>'route_sequence'
  from candidates c
  where coalesce((c.q->>'eligible')::boolean,false)
    and v_used_detour+(c.q->>'detour_km')::numeric <= public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,now())
    and v_used_minutes+(c.q->>'added_minutes')::integer <= public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,now())
  order by (c.q->>'detour_km')::numeric,c.order_number
  limit greatest(v_max-v_count,0);
end
$$;

revoke all on function public.queuego_route_bundle_candidates() from public,anon;
grant execute on function public.queuego_route_bundle_candidates() to authenticated,service_role;
