-- POS/storefront price is the base price. Delivery markup is merchant pricing policy.
-- GP remains Delivery accounting only and must never mutate product prices.
set lock_timeout='5s';

alter table public.products add column if not exists delivery_markup_percent numeric(7,3);

update public.products
set delivery_markup_percent=case
 when coalesce(pos_price,price,0)>0 and delivery_price is not null
 then greatest(0,least(1000,round(((delivery_price/coalesce(pos_price,price))-1)*100,3)))
 else 0 end
where delivery_markup_percent is null;

alter table public.products alter column delivery_markup_percent set default 0;
alter table public.products alter column delivery_markup_percent set not null;
alter table public.products drop constraint if exists products_delivery_markup_percent_check;
alter table public.products add constraint products_delivery_markup_percent_check check(delivery_markup_percent between 0 and 1000);

create or replace function public.queuego_products_lock_delivery_price()
returns trigger language plpgsql security definer set search_path to 'public','pg_temp' as $$
declare v_store_price numeric;v_markup numeric;
begin
 if tg_op='UPDATE' and new.pos_price is distinct from old.pos_price then v_store_price:=coalesce(new.pos_price,new.price);
 elsif tg_op='UPDATE' and new.price is distinct from old.price then v_store_price:=coalesce(new.price,new.pos_price);
 else v_store_price:=coalesce(new.pos_price,new.price);end if;
 if v_store_price is null or v_store_price<0 or v_store_price>999999 then raise exception 'invalid storefront price';end if;
 v_markup:=coalesce(new.delivery_markup_percent,0);
 if v_markup<0 or v_markup>1000 then raise exception 'invalid delivery markup percent';end if;
 v_store_price:=round(v_store_price,2);
 new.price:=v_store_price;new.pos_price:=v_store_price;
 new.delivery_markup_percent:=round(v_markup,3);
 new.delivery_price:=round(v_store_price*(1+v_markup/100),2);
 return new;
end $$;

drop trigger if exists queuego_products_lock_delivery_price_trg on public.products;
create trigger queuego_products_lock_delivery_price_trg
before insert or update of price,pos_price,delivery_price,delivery_markup_percent,shop_id on public.products
for each row execute function public.queuego_products_lock_delivery_price();

drop trigger if exists queuego_reprice_products_after_shop_gp_change_trg on public.shop_gp_rates;
drop trigger if exists queuego_reprice_products_after_system_gp_change_trg on public.system_settings;
drop trigger if exists queuego_reprice_products_after_platform_gp_change_trg on public.queuego_platform_rules;
drop function if exists public.queuego_reprice_products_after_shop_gp_change();
drop function if exists public.queuego_reprice_products_after_system_gp_change();
drop function if exists public.queuego_reprice_products_after_platform_gp_change();
revoke all on function public.queuego_products_lock_delivery_price() from public,anon,authenticated;
