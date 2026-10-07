-- Reduce anonymous SECURITY DEFINER surface to intentional public APIs only.

revoke execute on function public.market_public_catalog() from public,anon;

revoke execute on function public.queuego_nearest_market(double precision,double precision,numeric) from public,anon;
grant execute on function public.queuego_nearest_market(double precision,double precision,numeric) to authenticated,service_role;
