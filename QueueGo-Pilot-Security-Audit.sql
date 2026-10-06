-- QueueGo Pilot Gate 3: Security Surface Hardening
-- Mirrors the production revocations applied before Closed Beta.

-- Trigger-only functions must never be callable over the Data API.
revoke all on function public.qg_guard_pickup_photo() from public,anon,authenticated;
grant execute on function public.qg_guard_pickup_photo() to service_role;
revoke all on function public.qg_snapshot_order_item_image() from public,anon,authenticated;
grant execute on function public.qg_snapshot_order_item_image() to service_role;
revoke all on function public.pos_guard_delivery_enabled() from public,anon,authenticated;
grant execute on function public.pos_guard_delivery_enabled() to service_role;
revoke all on function public.pos_guard_write() from public,anon,authenticated;
grant execute on function public.pos_guard_write() to service_role;
revoke all on function public.pos_record_event() from public,anon,authenticated;
grant execute on function public.pos_record_event() to service_role;
revoke all on function public.pos_set_new_item_state() from public,anon,authenticated;
grant execute on function public.pos_set_new_item_state() to service_role;

-- Internal helpers are called only by authorized wrappers.
revoke execute on function public.effective_gp_rate_at(uuid,timestamptz) from authenticated;
grant execute on function public.effective_gp_rate_at(uuid,timestamptz) to service_role;
revoke execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) from authenticated;
grant execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) to service_role;

-- Legacy/manual cash-confirmation endpoints are retired from client access.
revoke execute on function public.merchant_confirm_cash(uuid) from authenticated;
grant execute on function public.merchant_confirm_cash(uuid) to service_role;
revoke execute on function public.market_rider_confirm_pickup_cash(uuid,numeric) from authenticated;
grant execute on function public.market_rider_confirm_pickup_cash(uuid,numeric) to service_role;

-- Legacy competitive Rider claim is retired; sequential server offer dispatch is authoritative.
revoke execute on function public.claim_delivery(uuid) from authenticated;
grant execute on function public.claim_delivery(uuid) to service_role;

-- Server-only tables: explicit deny is defense-in-depth even if grants change later.
do $$
declare t text;
begin
  foreach t in array array[
    'admin_order_delete_archive','market_order_add_requests','pos_request_keys','qg_delivery_pins',
    'qg_merchant_action_receipts','qg_pickup_proofs','qg_rider_offer_history','qg_rider_order_offers',
    'qg_rider_push_config','qg_rider_push_outbox','qg_rider_push_subscriptions',
    'qg_table_requests','qg_table_sessions','queuego_cash_order_locks'
  ] loop
    execute format('drop policy if exists qg_server_only_no_client_access on public.%I',t);
    execute format('create policy qg_server_only_no_client_access on public.%I for all to anon,authenticated using (false) with check (false)',t);
  end loop;
end $$;

-- Intentional anonymous SECURITY DEFINER allowlist (reviewed):
-- market_public_catalog(), market_public_catalog_v2(), market_public_markets_v1(),
-- market_public_shops_v2(), qg_public_promotions(), qg_public_shop_reviews(uuid),
-- qg_table_catalog(uuid,uuid), qg_table_checkout(uuid,uuid,uuid,jsonb,float8,float8),
-- qg_table_scan(uuid,uuid,float8,float8), qg_table_session_status(uuid,uuid),
-- queuego_nearest_market(float8,float8,numeric).
-- These are deliberately public capabilities; do not broaden this allowlist.
