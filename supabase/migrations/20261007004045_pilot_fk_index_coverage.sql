-- QueueGo Pilot: covering indexes for foreign keys used by active Market/Laundry/Order/Merchant cash paths.

create index if not exists laundry_hub_riders_rider_fk_idx
  on public.laundry_hub_riders(rider_id);
create index if not exists laundry_orders_customer_fk_idx
  on public.laundry_orders(customer_id);
create index if not exists laundry_orders_service_fk_idx
  on public.laundry_orders(service_id);
create index if not exists laundry_rider_invites_invited_by_fk_idx
  on public.laundry_rider_invites(invited_by);
create index if not exists laundry_rider_invites_rider_fk_idx
  on public.laundry_rider_invites(rider_id);
create index if not exists market_order_add_requests_customer_fk_idx
  on public.market_order_add_requests(customer_id);
create index if not exists market_order_pickups_order_fk_idx
  on public.market_order_pickups(order_id);
create index if not exists market_order_pickups_shop_fk_idx
  on public.market_order_pickups(shop_id);
create index if not exists market_orders_customer_fk_idx
  on public.market_orders(customer_id);
create index if not exists market_orders_market_fk_idx
  on public.market_orders(market_id);
create index if not exists market_products_product_shop_fk_idx
  on public.market_products(product_id,shop_id);
create index if not exists market_requests_created_market_fk_idx
  on public.market_requests(created_market_id);
create index if not exists market_requests_reviewed_by_fk_idx
  on public.market_requests(reviewed_by);
create index if not exists market_stock_movements_product_fk_idx
  on public.market_stock_movements(product_id);
create index if not exists merchant_cash_receipts_confirmed_by_fk_idx
  on public.merchant_cash_receipts(confirmed_by);
create index if not exists merchant_cash_receipts_shop_fk_idx
  on public.merchant_cash_receipts(shop_id);
create index if not exists order_items_product_fk_idx
  on public.order_items(product_id);
