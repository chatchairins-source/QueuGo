-- POS generic reservations must not compete with the Market Master stock ledger.
begin;

do $$
begin
  if exists (
    select 1 from public.market_products mp
    join public.products p on p.id=mp.product_id and p.shop_id=mp.shop_id
    where p.inventory_stock_tracking
  ) then
    raise exception 'disable generic tracking on Market Master products before enabling its stock owner guard';
  end if;
end $$;

create or replace function public.qg_inventory_tracking_guard() returns trigger
language plpgsql set search_path=public,pg_temp as $$
begin
  if new.grocery_master_id is not null and new.inventory_stock_tracking then
    raise exception 'Grocery Master owns stock tracking for this product';
  end if;
  if new.inventory_stock_tracking and exists (
    select 1 from public.market_products where product_id=new.id
  ) then
    raise exception 'Market Master owns stock tracking for this product';
  end if;
  if tg_op='INSERT' then
    if new.inventory_stock_tracking
       and ((select auth.uid()) is null or not public.pos_allowed('manage_staff')) then
      raise exception 'inventory tracking permission denied';
    end if;
    return new;
  end if;
  if new.inventory_stock_tracking is distinct from old.inventory_stock_tracking then
    if (select auth.uid()) is null or not public.pos_allowed('manage_staff') then
      raise exception 'inventory tracking permission denied';
    end if;
    if exists (
      select 1 from public.order_items i join public.orders o on o.id=i.order_id
      where i.product_id=new.id
        and o.status not in ('completed','cancelled','no_rider_available')
    ) then
      raise exception 'finish or cancel open orders before changing inventory tracking';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists qg_inventory_tracking_guard on public.products;
create trigger qg_inventory_tracking_guard
before insert or update of inventory_stock_tracking,grocery_master_id on public.products
for each row execute function public.qg_inventory_tracking_guard();

-- Serialize Market Master mapping against the product tracking switch. The Market
-- item trigger owns stock_quantity and movement rows for these mapped products.
create function public.qg_market_inventory_owner_guard() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_tracking boolean;
begin
  select p.inventory_stock_tracking into v_tracking
  from public.products p
  where p.id=new.product_id and p.shop_id=new.shop_id
  for update;
  if not found then raise exception 'market product/shop mismatch'; end if;
  if v_tracking then raise exception 'disable generic tracking before assigning Market stock ownership'; end if;
  return new;
end $$;

create trigger qg_market_inventory_owner_guard
before insert or update of product_id,shop_id on public.market_products
for each row execute function public.qg_market_inventory_owner_guard();

-- Keep the existing Market trigger authoritative. Check after locking the product
-- row so a concurrent Market mapping cannot race and decrement products.stock too.
create or replace function public.qg_pos_generic_item_stock() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare
  item uuid; order_key uuid; product uuid; p public.products%rowtype;
  o public.orders%rowtype; r public.pos_inventory_reservations%rowtype;
  qty integer; delta integer; v_source text; v_order_context text;
begin
  item:=case when tg_op='DELETE' then old.id else new.id end;
  order_key:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  product:=case when tg_op='DELETE' then old.product_id else new.product_id end;
  if tg_op='UPDATE' and (new.product_id is distinct from old.product_id or new.order_id is distinct from old.order_id)
     and (exists(select 1 from public.pos_inventory_reservations where item_id=item)
       or exists(select 1 from public.products where id in (new.product_id,old.product_id) and inventory_stock_tracking)) then
    raise exception 'tracked inventory order item identity is locked';
  end if;
  if exists(select 1 from public.products where id=product and grocery_master_id is not null) then
    return case when tg_op='DELETE' then old else new end;
  end if;
  select * into o from public.orders where id=order_key for update;
  if not found then raise exception 'inventory order not found'; end if;
  select * into p from public.products where id=product for update;
  if not found or p.grocery_master_id is not null then return case when tg_op='DELETE' then old else new end; end if;
  if exists(select 1 from public.market_products where product_id=product) then
    return case when tg_op='DELETE' then old else new end;
  end if;
  select * into r from public.pos_inventory_reservations where item_id=item for update;
  if not p.inventory_stock_tracking and r.item_id is null then return case when tg_op='DELETE' then old else new end; end if;
  if o.status='completed' then raise exception 'completed inventory item is immutable'; end if;
  if p.shop_id is distinct from o.shop_id then raise exception 'inventory product/shop mismatch'; end if;
  if tg_op='INSERT' and not p.available then raise exception 'inventory product unavailable'; end if;
  if o.status in ('cancelled','no_rider_available') then
    if tg_op='INSERT' then raise exception 'closed order cannot reserve inventory'; end if;
    return case when tg_op='DELETE' then old else new end;
  end if;
  if tg_op<>'DELETE' and (new.quantity<1 or new.quantity<>trunc(new.quantity)) then raise exception 'inventory quantity must be a positive integer'; end if;
  qty:=case when tg_op='DELETE' then 0 else new.quantity::integer end;
  delta:=qty-case when coalesce(r.released,false) then 0 else coalesce(r.quantity,0) end;
  if delta<>0 then
    v_source:=current_setting('queuego.pos_inventory_event_type',true);
    v_order_context:=current_setting('queuego.pos_inventory_order_id',true);
    perform set_config('queuego.pos_inventory_event_type',case when delta>0 then 'sale_reservation' else 'reservation_release' end,true);
    perform set_config('queuego.pos_inventory_order_id',order_key::text,true);
    update public.products set stock=stock-delta where id=product and stock>=delta;
    if not found then raise exception 'insufficient stock'; end if;
    perform set_config('queuego.pos_inventory_event_type',coalesce(v_source,''),true);
    perform set_config('queuego.pos_inventory_order_id',coalesce(v_order_context,''),true);
  end if;
  if tg_op='DELETE' then delete from public.pos_inventory_reservations where item_id=item;
  else insert into public.pos_inventory_reservations(item_id,order_id,product_id,quantity,released)
    values(item,order_key,product,qty,false)
    on conflict(item_id) do update set quantity=excluded.quantity,released=false,updated_at=now(); end if;
  return case when tg_op='DELETE' then old else new end;
end $$;

revoke all on function public.qg_inventory_tracking_guard(),public.qg_market_inventory_owner_guard(),public.qg_pos_generic_item_stock()
  from public,anon,authenticated;

commit;
