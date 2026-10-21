-- Product-row RLS is intentionally broad enough for a shop to edit catalog fields.
-- Keep the inventory ownership switch manager-only even through direct REST writes.
begin;

create or replace function public.qg_inventory_tracking_guard() returns trigger
language plpgsql set search_path=public,pg_temp as $$
begin
  if new.grocery_master_id is not null and new.inventory_stock_tracking then
    raise exception 'Grocery Master owns stock tracking for this product';
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

revoke all on function public.qg_inventory_tracking_guard() from public,anon,authenticated;

commit;
