-- Retire the legacy Laundry customer-order entry point from client access.
-- Production customer UI uses queuego_place_laundry_order_v2, which enforces
-- QueueGo service-area authority through queuego_assert_service_route().
-- Keep the legacy function body for historical audit/rollback; only the
-- service role may execute it after this migration.
revoke all on function public.queuego_place_laundry_order(
  uuid,uuid,uuid,text,double precision,double precision,text
) from public,anon,authenticated;
grant execute on function public.queuego_place_laundry_order(
  uuid,uuid,uuid,text,double precision,double precision,text
) to service_role;

comment on function public.queuego_place_laundry_order(
  uuid,uuid,uuid,text,double precision,double precision,text
) is 'Legacy Laundry v1 order writer. Client execution retired 2026-10-25; use queuego_place_laundry_order_v2 with nationwide service-area enforcement.';
