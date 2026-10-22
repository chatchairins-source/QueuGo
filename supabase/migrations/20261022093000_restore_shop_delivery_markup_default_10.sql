-- Delivery markup is a shop-level pricing policy, separate from GP and POS.
set lock_timeout='5s';

create table if not exists public.shop_delivery_markup_rates(
 shop_id uuid primary key references public.shop_profiles(id) on delete cascade,
 rate_percent numeric(7,3) not null check(rate_percent between 0 and 1000),
 updated_at timestamptz not null default now(),
 updated_by uuid null
);
alter table public.shop_delivery_markup_rates enable row level security;
revoke all on public.shop_delivery_markup_rates from anon,authenticated;

insert into public.system_settings(key,value,updated_at)
values('delivery_markup','{"default_rate":10}'::jsonb,now())
on conflict(key) do nothing;

create or replace function public.queuego_effective_delivery_markup(p_shop_id uuid)
returns numeric language sql stable security invoker set search_path='public','pg_temp' as $$
 select coalesce(
  (select rate_percent from public.shop_delivery_markup_rates where shop_id=p_shop_id),
  nullif((select value->>'default_rate' from public.system_settings where key='delivery_markup'),'')::numeric,
  10::numeric
 );
$$;
revoke all on function public.queuego_effective_delivery_markup(uuid) from public,anon,authenticated;

create or replace function public.queuego_products_lock_delivery_price()
returns trigger language plpgsql security definer set search_path='public','pg_temp' as $$
declare v_store_price numeric;v_markup numeric;
begin
 if tg_op='UPDATE' and new.pos_price is distinct from old.pos_price then v_store_price:=coalesce(new.pos_price,new.price);
 elsif tg_op='UPDATE' and new.price is distinct from old.price then v_store_price:=coalesce(new.price,new.pos_price);
 else v_store_price:=coalesce(new.pos_price,new.price);end if;
 if v_store_price is null or v_store_price<0 or v_store_price>999999 then raise exception 'invalid storefront price';end if;
 v_markup:=public.queuego_effective_delivery_markup(new.shop_id);
 if v_markup<0 or v_markup>1000 then raise exception 'invalid delivery markup percent';end if;
 v_store_price:=round(v_store_price,2);
 new.price:=v_store_price;new.pos_price:=v_store_price;new.delivery_markup_percent:=round(v_markup,3);
 new.delivery_price:=round(v_store_price*(1+v_markup/100),2);
 return new;
end $$;
revoke all on function public.queuego_products_lock_delivery_price() from public,anon,authenticated;

create or replace function public.queuego_reprice_products_after_delivery_markup_change()
returns trigger language plpgsql security definer set search_path='public','pg_temp' as $$
declare v_shop uuid;v_rate numeric;
begin
 v_shop:=coalesce(new.shop_id,old.shop_id);v_rate:=public.queuego_effective_delivery_markup(v_shop);
 update public.products set delivery_markup_percent=v_rate,delivery_price=round(coalesce(pos_price,price)*(1+v_rate/100),2) where shop_id=v_shop;
 return coalesce(new,old);
end $$;
revoke all on function public.queuego_reprice_products_after_delivery_markup_change() from public,anon,authenticated;

drop trigger if exists queuego_reprice_products_after_delivery_markup_change_trg on public.shop_delivery_markup_rates;
create trigger queuego_reprice_products_after_delivery_markup_change_trg
after insert or update or delete on public.shop_delivery_markup_rates
for each row execute function public.queuego_reprice_products_after_delivery_markup_change();

update public.products p set delivery_markup_percent=public.queuego_effective_delivery_markup(p.shop_id),delivery_price=round(coalesce(p.pos_price,p.price)*(1+public.queuego_effective_delivery_markup(p.shop_id)/100),2);
