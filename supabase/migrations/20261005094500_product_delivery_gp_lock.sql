-- QueueGo: one merchant storefront price; Delivery price is locked to storefront + effective GP.
-- Additive/safe migration: preserves existing columns and order/payment state machines.

create or replace function public.queuego_products_lock_delivery_price()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_store_price numeric;
  v_gp_rate numeric;
begin
  if tg_op='UPDATE' and new.pos_price is distinct from old.pos_price then
    v_store_price := coalesce(new.pos_price,new.price);
  elsif tg_op='UPDATE' and new.price is distinct from old.price then
    v_store_price := coalesce(new.price,new.pos_price);
  else
    v_store_price := coalesce(new.pos_price,new.price);
  end if;

  if v_store_price is null or v_store_price < 0 or v_store_price > 999999 then
    raise exception 'invalid storefront price';
  end if;

  v_store_price := round(v_store_price,2);
  v_gp_rate := coalesce(public.effective_gp_rate_at(new.shop_id,now()),0);

  if v_gp_rate < 0 or v_gp_rate > 100 then
    raise exception 'invalid effective GP rate';
  end if;

  -- One source price for the merchant. Delivery is system-derived and cannot be overridden.
  new.price := v_store_price;
  new.pos_price := v_store_price;
  new.delivery_price := round(v_store_price * (1 + v_gp_rate / 100),2);
  return new;
end
$$;

drop trigger if exists queuego_products_lock_delivery_price_trg on public.products;
create trigger queuego_products_lock_delivery_price_trg
before insert or update of price,pos_price,delivery_price,shop_id
on public.products
for each row execute function public.queuego_products_lock_delivery_price();

create or replace function public.queuego_reprice_products_after_shop_gp_change()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_old_user uuid;
  v_new_user uuid;
begin
  if tg_op <> 'INSERT' then v_old_user := old.shop_user_id; end if;
  if tg_op <> 'DELETE' then v_new_user := new.shop_user_id; end if;

  if v_old_user is not null then
    update public.products p
       set pos_price=coalesce(p.pos_price,p.price)
      from public.shop_profiles sp
     where p.shop_id=sp.id and sp.user_id=v_old_user;
  end if;

  if v_new_user is not null and v_new_user is distinct from v_old_user then
    update public.products p
       set pos_price=coalesce(p.pos_price,p.price)
      from public.shop_profiles sp
     where p.shop_id=sp.id and sp.user_id=v_new_user;
  end if;
  return null;
end
$$;

drop trigger if exists queuego_reprice_products_after_shop_gp_change_trg on public.shop_gp_rates;
create trigger queuego_reprice_products_after_shop_gp_change_trg
after insert or update or delete on public.shop_gp_rates
for each row execute function public.queuego_reprice_products_after_shop_gp_change();

create or replace function public.queuego_reprice_products_after_system_gp_change()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_old_key text;
  v_new_key text;
begin
  if tg_op <> 'INSERT' then v_old_key := old.key; end if;
  if tg_op <> 'DELETE' then v_new_key := new.key; end if;

  if v_old_key='gp' or v_new_key='gp' then
    update public.products set pos_price=coalesce(pos_price,price);
  end if;
  return null;
end
$$;

drop trigger if exists queuego_reprice_products_after_system_gp_change_trg on public.system_settings;
create trigger queuego_reprice_products_after_system_gp_change_trg
after insert or update or delete on public.system_settings
for each row execute function public.queuego_reprice_products_after_system_gp_change();

create or replace function public.queuego_reprice_products_after_platform_gp_change()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_old_key text;
  v_new_key text;
begin
  if tg_op <> 'INSERT' then v_old_key := old.rule_key; end if;
  if tg_op <> 'DELETE' then v_new_key := new.rule_key; end if;

  if v_old_key in ('feature.gp','pricing.gp_default_rate')
     or v_new_key in ('feature.gp','pricing.gp_default_rate') then
    update public.products set pos_price=coalesce(pos_price,price);
  end if;
  return null;
end
$$;

drop trigger if exists queuego_reprice_products_after_platform_gp_change_trg on public.queuego_platform_rules;
create trigger queuego_reprice_products_after_platform_gp_change_trg
after insert or update or delete on public.queuego_platform_rules
for each row execute function public.queuego_reprice_products_after_platform_gp_change();

revoke all on function public.queuego_products_lock_delivery_price() from public,anon,authenticated;
revoke all on function public.queuego_reprice_products_after_shop_gp_change() from public,anon,authenticated;
revoke all on function public.queuego_reprice_products_after_system_gp_change() from public,anon,authenticated;
revoke all on function public.queuego_reprice_products_after_platform_gp_change() from public,anon,authenticated;

-- Normalize existing products immediately to the new single-price rule.
update public.products
   set pos_price=coalesce(pos_price,price);
