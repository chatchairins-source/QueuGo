-- Repair the Production ACL drift without changing the RPC body or any user data.
revoke execute on function public.queuego_ride_config() from anon, public;
grant execute on function public.queuego_ride_config() to authenticated, service_role;
