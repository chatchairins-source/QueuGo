-- Tie a dine-in order's table to the same shop at the database boundary.
-- Existing pos_tables, orders.table_id, owner-scoped RPC and RLS remain in use.
begin;
alter table public.orders
  add constraint orders_table_same_shop_fkey
  foreign key (table_id, shop_id)
  references public.pos_tables(id, shop_id);
commit;
