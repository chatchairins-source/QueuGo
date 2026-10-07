select jsonb_build_object(
  'auth.users',(select count(*) from auth.users),
  'public.users',(select count(*) from public.users),
  'public.shop_profiles',(select count(*) from public.shop_profiles),
  'public.rider_profiles',(select count(*) from public.rider_profiles),
  'public.orders',(select count(*) from public.orders),
  'public.order_items',(select count(*) from public.order_items),
  'public.deliveries',(select count(*) from public.deliveries),
  'public.notifications',(select count(*) from public.notifications),
  'public.products',(select count(*) from public.products),
  'public.markets',(select count(*) from public.markets),
  'public.market_orders',(select count(*) from public.market_orders),
  'public.laundry_orders',(select count(*) from public.laundry_orders),
  'public.qg_pickup_proofs',(select count(*) from public.qg_pickup_proofs),
  'public.qg_delivery_proofs',(select count(*) from public.qg_delivery_proofs),
  'storage.buckets',(select count(*) from storage.buckets),
  'storage.objects',(select count(*) from storage.objects)
);
