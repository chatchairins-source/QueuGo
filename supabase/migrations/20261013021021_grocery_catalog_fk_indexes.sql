-- Grocery-only foreign-key lookup indexes identified by Production advisors.
-- Existing rows, RLS, prices and order behavior are unchanged.
set lock_timeout = '3s';
create index if not exists grocery_master_merged_into_fk
  on public.grocery_master_products(merged_into);
create index if not exists grocery_stock_product_fk
  on public.grocery_stock_reservations(product_id);
create index if not exists products_grocery_master_fk
  on public.products(grocery_master_id);
