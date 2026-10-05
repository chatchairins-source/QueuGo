-- Align Supabase Realtime publication with the tables the QueueGo clients actually subscribe to.
-- Additive only: no data or RLS policy changes.

do $$
declare
  v_table text;
begin
  foreach v_table in array array[
    'users',
    'shop_profiles',
    'technician_profiles',
    'products',
    'payments',
    'quotations',
    'promotions',
    'settlements',
    'audit_logs',
    'order_audit',
    'market_orders',
    'market_order_pickups',
    'market_requests',
    'laundry_orders',
    'laundry_rider_jobs',
    'laundry_rider_invites',
    'queuego_platform_rules',
    'route_bundles'
  ]
  loop
    if to_regclass('public.'||v_table) is not null
       and not exists(
         select 1
         from pg_publication_tables
         where pubname='supabase_realtime'
           and schemaname='public'
           and tablename=v_table
       ) then
      execute format('alter publication supabase_realtime add table public.%I',v_table);
    end if;
  end loop;
end
$$;
