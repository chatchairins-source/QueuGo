-- Extend the existing guard, rather than introducing another checkout system.
-- Applies equally to Web/Native and single-shop/Market delivery creation.
create or replace function public.qg_market_membership_order_guard()
returns trigger language plpgsql security definer
set search_path to 'public', 'pg_temp'
as $$
declare
  v_shop public.shop_profiles%rowtype;
  v_market public.markets%rowtype;
begin
  if new.sales_channel is distinct from 'QUEUEGO_DELIVERY' and new.market_order_id is null then
    return new;
  end if;
  select * into v_shop from public.shop_profiles where id=new.shop_id for share;
  if not found or v_shop.status is distinct from 'active' or v_shop.delivery_enabled is distinct from true then
    raise exception 'shop unavailable for delivery';
  end if;
  if v_shop.market_id is null or v_shop.market_membership_status is distinct from 'approved' then
    raise exception 'shop market membership is not approved';
  end if;
  select * into v_market from public.markets where id=v_shop.market_id for share;
  if not found or v_market.active is distinct from true then
    raise exception 'market unavailable';
  end if;
  if new.market_order_id is not null and not exists(
    select 1 from public.market_orders mo where mo.id=new.market_order_id and mo.market_id=v_shop.market_id
  ) then
    raise exception 'order market does not match shop';
  end if;
  if exists(select 1 from public.shop_open_states x where x.shop_id=v_shop.id
    and x.is_open=false and (x.resume_at is null or x.resume_at>now())) then
    raise exception 'shop closed';
  end if;
  return new;
end $$;

-- Changing status of an existing delivery does not re-check shop opening.
-- Historical orders can still be delivered/cancelled after a shop closes.
drop trigger if exists qg_market_membership_order_guard_trg on public.orders;
create trigger qg_market_membership_order_guard_trg
before insert or update of market_order_id,shop_id,sales_channel on public.orders
for each row execute function public.qg_market_membership_order_guard();
revoke all on function public.qg_market_membership_order_guard() from public,anon,authenticated;
grant execute on function public.qg_market_membership_order_guard() to service_role;
