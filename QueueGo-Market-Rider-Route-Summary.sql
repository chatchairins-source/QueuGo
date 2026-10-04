-- Rider-facing Market Trip pickup route summary.
create or replace function public.market_pickup_route_summary(p_market_order_id uuid)
returns table(
  pickup_id uuid,
  pickup_sequence integer,
  shop_id uuid,
  shop_name text,
  shop_address text,
  latitude double precision,
  longitude double precision,
  shop_amount numeric,
  cash_paid_amount numeric,
  status text,
  cash_paid_at timestamptz
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare v_uid uuid;v_rider uuid;
begin
  v_uid:=public.get_my_user_id();
  if v_uid is null then raise exception 'authentication required'; end if;
  select id into v_rider from public.rider_profiles where user_id=v_uid and status='active';

  if not (
    public.is_active_admin()
    or exists(select 1 from public.market_orders mo where mo.id=p_market_order_id and mo.customer_id=v_uid)
    or exists(
      select 1 from public.orders o
      join public.shop_profiles s on s.id=o.shop_id
      where o.market_order_id=p_market_order_id and s.user_id=v_uid
    )
    or (
      v_rider is not null and exists(
        select 1 from public.orders o
        where o.market_order_id=p_market_order_id and o.rider_id=v_rider
      )
    )
  ) then
    raise exception 'market pickup access denied';
  end if;

  return query
  select mp.id,mp.pickup_sequence,mp.shop_id,s.shop_name,s.address,
         s.latitude,s.longitude,mp.shop_amount,mp.cash_paid_amount,mp.status,mp.cash_paid_at
  from public.market_order_pickups mp
  join public.shop_profiles s on s.id=mp.shop_id
  where mp.market_order_id=p_market_order_id
  order by mp.pickup_sequence,mp.created_at;
end $$;

revoke all on function public.market_pickup_route_summary(uuid) from public,anon;
grant execute on function public.market_pickup_route_summary(uuid) to authenticated,service_role;
