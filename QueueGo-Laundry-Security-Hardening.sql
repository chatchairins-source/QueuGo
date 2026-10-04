-- QueueGo Laundry/Market security hardening.
-- All Laundry writes go through role-checking RPCs. Direct clients keep SELECT only under RLS.

revoke all on
  public.laundry_hubs,
  public.laundry_services,
  public.laundry_shop_settings,
  public.laundry_orders,
  public.laundry_rider_jobs,
  public.laundry_rider_preferences,
  public.laundry_hub_riders,
  public.laundry_order_events,
  public.laundry_rider_invites
from anon;

revoke all on
  public.laundry_hubs,
  public.laundry_services,
  public.laundry_shop_settings,
  public.laundry_orders,
  public.laundry_rider_jobs,
  public.laundry_rider_preferences,
  public.laundry_hub_riders,
  public.laundry_order_events,
  public.laundry_rider_invites
from authenticated;

grant select on
  public.laundry_hubs,
  public.laundry_services,
  public.laundry_shop_settings,
  public.laundry_orders,
  public.laundry_rider_jobs,
  public.laundry_rider_preferences,
  public.laundry_hub_riders,
  public.laundry_order_events,
  public.laundry_rider_invites
to authenticated;

-- Trigger functions are internal only and must not be callable through PostgREST RPC.
revoke all on function public.qg_market_membership_order_guard() from public,anon,authenticated;
revoke all on function public.queuego_market_membership_write_guard() from public,anon,authenticated;
revoke all on function public.qg_route_bundle_delivery_guard() from public,anon,authenticated;

grant execute on function public.qg_market_membership_order_guard() to service_role;
grant execute on function public.queuego_market_membership_write_guard() to service_role;
grant execute on function public.qg_route_bundle_delivery_guard() to service_role;
