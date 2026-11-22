// Keep both dump-derived expectations and restore verification on one table set.
export const criticalBackupTables = Object.freeze([
  'auth.users','public.users','public.shop_profiles','public.rider_profiles',
  'public.orders','public.order_items','public.deliveries','public.notifications',
  'public.products','public.markets','public.market_orders','public.laundry_orders',
  'public.qg_pickup_proofs','public.qg_delivery_proofs',
  'public.qg_ugc_terms_acceptances','public.qg_user_blocks','public.qg_ugc_reports',
  'storage.buckets','storage.objects',
 'public.payments','public.pos_register_cash_events','public.pos_register_shifts','public.merchant_cash_receipts','public.qg_rider_cash_return_refunds','public.queuego_cash_order_locks','public.rider_cash_advances','public.grocery_stock_reservations','public.market_stock_movements','public.qg_rider_offer_history','public.qg_rider_order_offers','qg_private.credit_accounts','qg_private.credit_entries','qg_private.credit_holds','qg_private.credit_topups'
]);
