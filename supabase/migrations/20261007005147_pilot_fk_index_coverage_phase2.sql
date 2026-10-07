-- QueueGo Pilot FK coverage phase 2: Push, proof, Rider offer, support and POS hot paths.

create index if not exists qg_account_deletion_requests_user_fk_idx
  on public.qg_account_deletion_requests(user_id);
create index if not exists qg_customer_favorites_shop_fk_idx
  on public.qg_customer_favorites(shop_id);
create index if not exists qg_customer_favorites_product_fk_idx
  on public.qg_customer_favorites(product_id);
create index if not exists qg_delivery_proofs_rider_fk_idx
  on public.qg_delivery_proofs(rider_id);
create index if not exists qg_pickup_proofs_rider_fk_idx
  on public.qg_pickup_proofs(rider_id);
create index if not exists qg_push_outbox_subscription_fk_idx
  on public.qg_push_outbox(subscription_id);
create index if not exists qg_push_outbox_native_token_fk_idx
  on public.qg_push_outbox(native_token_id);
create index if not exists qg_rider_offer_history_rider_fk_idx
  on public.qg_rider_offer_history(rider_id);
create index if not exists qg_support_tickets_order_fk_idx
  on public.qg_support_tickets(order_id);
create index if not exists pos_request_keys_order_fk_idx
  on public.pos_request_keys(order_id);
create index if not exists qg_table_requests_order_fk_idx
  on public.qg_table_requests(order_id);
create index if not exists qg_table_sessions_shop_fk_idx
  on public.qg_table_sessions(shop_id);
create index if not exists qg_table_sessions_table_shop_fk_idx
  on public.qg_table_sessions(table_id,shop_id);
create index if not exists reviews_customer_fk_idx
  on public.reviews(customer_id);
create index if not exists rider_cash_advances_rider_fk_idx
  on public.rider_cash_advances(rider_id);
create index if not exists rider_cash_advances_shop_fk_idx
  on public.rider_cash_advances(shop_id);
create index if not exists shop_support_messages_sender_fk_idx
  on public.shop_support_messages(sender_user_id);
create index if not exists orders_staff_fk_idx
  on public.orders(staff_id);
create index if not exists orders_table_shop_fk_idx
  on public.orders(table_id,shop_id);
