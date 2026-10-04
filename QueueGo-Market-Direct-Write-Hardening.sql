-- QueueGo Market direct-write hardening.
-- Market parent/pickup lifecycle is server-owned through role-checking RPCs.

revoke all on public.market_orders, public.market_order_pickups from anon, authenticated;
grant select on public.market_orders, public.market_order_pickups to authenticated;
grant all on public.market_orders, public.market_order_pickups to service_role;

-- market_requests is also RPC-owned for create/review. Keep participants read-only.
revoke insert,update,delete,truncate,references,trigger on public.market_requests from authenticated;
grant select on public.market_requests to authenticated;
grant all on public.market_requests to service_role;
