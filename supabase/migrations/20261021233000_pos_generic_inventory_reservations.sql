begin;

-- Generic merchant products share one stock counter across POS, table QR and delivery.
-- Grocery Master listings keep their existing, separate reservation owner.
alter table public.products
  add column inventory_stock_tracking boolean not null default false,
  add constraint products_inventory_stock_owner_check
    check (grocery_master_id is null or not inventory_stock_tracking);

create table public.pos_inventory_reservations (
  item_id uuid primary key,
  order_id uuid not null,
  product_id uuid not null references public.products(id),
  quantity integer not null check (quantity > 0),
  released boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index pos_inventory_reservations_open_order
  on public.pos_inventory_reservations(order_id,product_id,item_id) where not released;
create index pos_inventory_reservations_product
  on public.pos_inventory_reservations(product_id) where not released;
alter table public.pos_inventory_reservations enable row level security;
revoke all on public.pos_inventory_reservations from public,anon,authenticated;

-- Do not enable tracking while an existing line is awaiting payment/fulfilment:
-- an owner must first finish or cancel those orders so no stock is retroactively lost.
create function public.qg_inventory_tracking_guard() returns trigger
language plpgsql set search_path=public,pg_temp as $$
begin
  if new.grocery_master_id is not null and new.inventory_stock_tracking then
    raise exception 'Grocery Master owns stock tracking for this product';
  end if;
  if new.inventory_stock_tracking is distinct from old.inventory_stock_tracking
     and exists (
       select 1 from public.order_items i join public.orders o on o.id=i.order_id
       where i.product_id=new.id
         and o.status not in ('completed','cancelled','no_rider_available')
     ) then
    raise exception 'finish or cancel open orders before changing inventory tracking';
  end if;
  return new;
end $$;
create trigger qg_inventory_tracking_guard
before update of inventory_stock_tracking,grocery_master_id on public.products
for each row execute function public.qg_inventory_tracking_guard();

create function public.qg_pos_generic_item_stock() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare
  item uuid; order_key uuid; product uuid; p public.products%rowtype;
  o public.orders%rowtype; r public.pos_inventory_reservations%rowtype;
  qty integer; delta integer;
begin
  item:=case when tg_op='DELETE' then old.id else new.id end;
  order_key:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  product:=case when tg_op='DELETE' then old.product_id else new.product_id end;

  if tg_op='UPDATE' and (new.product_id is distinct from old.product_id or new.order_id is distinct from old.order_id)
     and (exists(select 1 from public.pos_inventory_reservations where item_id=item)
       or exists(select 1 from public.products where id in (new.product_id,old.product_id) and inventory_stock_tracking)) then
    raise exception 'tracked inventory order item identity is locked';
  end if;
  -- Grocery Master has an independent trigger, ledger and stock owner.
  if exists(select 1 from public.products where id=product and grocery_master_id is not null) then
    return case when tg_op='DELETE' then old else new end;
  end if;

  select * into o from public.orders where id=order_key for update;
  if not found then raise exception 'inventory order not found'; end if;
  select * into p from public.products where id=product for update;
  if not found or p.grocery_master_id is not null then
    return case when tg_op='DELETE' then old else new end;
  end if;
  select * into r from public.pos_inventory_reservations where item_id=item for update;
  if not p.inventory_stock_tracking and r.item_id is null then
    return case when tg_op='DELETE' then old else new end;
  end if;

  if o.status='completed' then raise exception 'completed inventory item is immutable'; end if;
  if p.shop_id is distinct from o.shop_id then raise exception 'inventory product/shop mismatch'; end if;
  if tg_op='INSERT' and not p.available then raise exception 'inventory product unavailable'; end if;

  if o.status in ('cancelled','no_rider_available') then
    if tg_op='INSERT' then raise exception 'closed order cannot reserve inventory'; end if;
    return case when tg_op='DELETE' then old else new end;
  end if;
  if tg_op<>'DELETE' and (new.quantity<1 or new.quantity<>trunc(new.quantity)) then
    raise exception 'inventory quantity must be a positive integer';
  end if;

  qty:=case when tg_op='DELETE' then 0 else new.quantity::integer end;
  delta:=qty-case when coalesce(r.released,false) then 0 else coalesce(r.quantity,0) end;
  if delta<>0 then
    update public.products set stock=stock-delta where id=product and stock>=delta;
    if not found then raise exception 'insufficient stock'; end if;
  end if;
  if tg_op='DELETE' then
    delete from public.pos_inventory_reservations where item_id=item;
  else
    insert into public.pos_inventory_reservations(item_id,order_id,product_id,quantity,released)
    values(item,order_key,product,qty,false)
    on conflict(item_id) do update set quantity=excluded.quantity,released=false,updated_at=now();
  end if;
  return case when tg_op='DELETE' then old else new end;
end $$;
create trigger qg_pos_generic_item_stock
after insert or delete or update of quantity,product_id,order_id on public.order_items
for each row execute function public.qg_pos_generic_item_stock();

create function public.qg_pos_generic_cancel_stock() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare r record;
begin
  if new.status not in ('cancelled','no_rider_available') or old.status in ('cancelled','no_rider_available') then
    return new;
  end if;
  for r in select * from public.pos_inventory_reservations
    where order_id=new.id and not released order by product_id,item_id for update
  loop
    -- A refund is not proof that a physical item was returned in sellable condition.
    if old.status<>'completed' then
      update public.products set stock=stock+r.quantity where id=r.product_id;
    end if;
    update public.pos_inventory_reservations set released=true,updated_at=now() where item_id=r.item_id;
  end loop;
  return new;
end $$;
create trigger qg_pos_generic_cancel_stock
after update of status on public.orders for each row execute function public.qg_pos_generic_cancel_stock();

-- Bring Grocery Master cancellation behavior in line: completed POS refunds consume the
-- sold stock unless a future explicit return workflow accepts and restocks the physical item.
create or replace function public.qg_grocery_cancel_stock() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare r record;
begin
  if new.status not in ('cancelled','no_rider_available') or old.status in ('cancelled','no_rider_available') then return new; end if;
  for r in select * from public.grocery_stock_reservations where order_id=new.id and not released order by product_id,item_id for update loop
    if old.status<>'completed' then
      update public.products set stock=stock+r.quantity where id=r.product_id;
    end if;
    update public.grocery_stock_reservations set released=true,updated_at=now() where item_id=r.item_id;
  end loop;
  return new;
end $$;

revoke all on function public.qg_inventory_tracking_guard(),public.qg_pos_generic_item_stock(),public.qg_pos_generic_cancel_stock(),public.qg_grocery_cancel_stock()
  from public,anon,authenticated;

commit;
