-- QueueGo Rider identity/rating context v1.
-- Applied to Production as migration 20261014054747.
-- Additive RPCs only: no table/RLS mutation.

create schema if not exists qg_private;
revoke all on schema qg_private from public, anon;
grant usage on schema qg_private to authenticated;

create or replace function qg_private.rider_rating_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_session_id text;
  v_count integer := 0;
  v_average numeric;
  v_one integer := 0;
  v_two integer := 0;
  v_three integer := 0;
  v_four integer := 0;
  v_five integer := 0;
begin
  v_session_id := auth.jwt()->>'session_id';
  if auth.uid() is null
     or v_session_id is null
     or v_session_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception 'active rider required' using errcode='42501';
  end if;
  if not exists (
    select 1 from auth.sessions s
    where s.id=v_session_id::uuid
      and s.user_id=auth.uid()
      and (s.not_after is null or s.not_after > now())
  ) then
    raise exception 'active rider required' using errcode='42501';
  end if;

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

  select
    count(*)::integer,
    round(avg(rv.rider_rating)::numeric, 2),
    count(*) filter (where rv.rider_rating=1)::integer,
    count(*) filter (where rv.rider_rating=2)::integer,
    count(*) filter (where rv.rider_rating=3)::integer,
    count(*) filter (where rv.rider_rating=4)::integer,
    count(*) filter (where rv.rider_rating=5)::integer
  into v_count, v_average, v_one, v_two, v_three, v_four, v_five
  from public.reviews rv
  join public.orders o on o.id=rv.order_id
  where o.rider_id=v_rider
    and o.status='completed'
    and rv.rider_rating between 1 and 5;

  return jsonb_build_object(
    'average', v_average,
    'count', v_count,
    'distribution', jsonb_build_object(
      '1', v_one, '2', v_two, '3', v_three, '4', v_four, '5', v_five
    )
  );
end $$;

revoke all on function qg_private.rider_rating_summary() from public, anon;
grant execute on function qg_private.rider_rating_summary() to authenticated;

create or replace function public.qg_rider_rating_summary()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_rating_summary(); $$;

revoke all on function public.qg_rider_rating_summary() from public, anon;
grant execute on function public.qg_rider_rating_summary() to authenticated, service_role;

create or replace function qg_private.merchant_order_rider_context(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_order public.orders%rowtype;
  v_rider public.rider_profiles%rowtype;
  v_session_id text;
  v_phone text;
begin
  v_session_id := auth.jwt()->>'session_id';
  if auth.uid() is null
     or v_session_id is null
     or v_session_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception 'active shop order required' using errcode='42501';
  end if;
  if not exists (
    select 1 from auth.sessions s
    where s.id=v_session_id::uuid
      and s.user_id=auth.uid()
      and (s.not_after is null or s.not_after > now())
  ) then
    raise exception 'active shop order required' using errcode='42501';
  end if;

  select o.* into v_order
  from public.orders o
  where o.id=p_order_id
    and exists (
      select 1
      from public.shop_profiles sp
      join public.users u on u.id=sp.user_id
      where sp.id=o.shop_id
        and sp.archived_at is null
        and u.auth_user_id=auth.uid()
        and u.role='shop'
        and u.status='active'
    );

  if not found then
    raise exception 'active shop order required' using errcode='42501';
  end if;

  if v_order.rider_id is null then
    return '{}'::jsonb;
  end if;

  select r.* into v_rider
  from public.rider_profiles r
  where r.id=v_order.rider_id
    and r.status='active';

  if not found then
    return '{}'::jsonb;
  end if;

  v_phone := case
    when v_order.status in ('rider_assigned','assigned','preparing','ready','picked_up','in_progress') then v_rider.phone
    when v_order.status='completed' and exists (
      select 1 from public.deliveries d
      where d.order_id=v_order.id
        and d.delivered_at is not null
        and d.delivered_at <= now()
        and now() < d.delivered_at + interval '30 minutes'
    ) then v_rider.phone
    else null
  end;

  return jsonb_build_object(
    'name', v_rider.rider_name,
    'phone', v_phone,
    'photo', coalesce(
      v_rider.metadata->>'profileImage',
      v_rider.metadata->>'profile_image',
      v_rider.metadata->>'avatarUrl'
    ),
    'vehicle_type', v_rider.vehicle_type,
    'vehicle_plate', v_rider.vehicle_plate,
    'vehicle_verified', (v_rider.vehicle_verified_at is not null)
  );
end $$;

revoke all on function qg_private.merchant_order_rider_context(uuid) from public, anon;
grant execute on function qg_private.merchant_order_rider_context(uuid) to authenticated;

create or replace function public.qg_merchant_order_rider_context(p_order_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.merchant_order_rider_context(p_order_id); $$;

revoke all on function public.qg_merchant_order_rider_context(uuid) from public, anon;
grant execute on function public.qg_merchant_order_rider_context(uuid) to authenticated, service_role;
