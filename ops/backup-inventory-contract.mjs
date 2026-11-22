// Keep both dump-derived expectations and restore verification on one table set.
export const criticalBackupTables = Object.freeze([
  'auth.users','public.users','public.shop_profiles','public.rider_profiles',
  'public.orders','public.order_items','public.deliveries','public.notifications',
  'public.products','public.markets','public.market_orders','public.laundry_orders',
  'public.qg_pickup_proofs','public.qg_delivery_proofs',
  'public.qg_ugc_terms_acceptances','public.qg_user_blocks','public.qg_ugc_reports',
  'storage.buckets','storage.objects'
]);
