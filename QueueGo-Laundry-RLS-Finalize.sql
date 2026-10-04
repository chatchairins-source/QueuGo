-- QueueGo Laundry final RLS cleanup.
-- Reads stay role-scoped. All mutations remain RPC-only for authenticated clients.

drop policy if exists laundry_events_hub_read on public.laundry_order_events;
create policy laundry_events_hub_read
on public.laundry_order_events
for select to authenticated
using (
  exists (
    select 1
    from public.laundry_orders o
    join public.laundry_hubs h on h.id=o.hub_id
    join public.shop_profiles s on s.id=h.shop_id
    where o.id=laundry_order_events.laundry_order_id
      and s.user_id=public.get_my_user_id()
  )
  or public.is_active_admin()
);

drop policy if exists laundry_orders_hub_select on public.laundry_orders;
create policy laundry_orders_hub_select
on public.laundry_orders
for select to authenticated
using (
  exists (
    select 1
    from public.laundry_hubs h
    join public.shop_profiles s on s.id=h.shop_id
    where h.id=laundry_orders.hub_id
      and s.user_id=public.get_my_user_id()
  )
  or public.is_active_admin()
);

drop policy if exists laundry_jobs_rider_select on public.laundry_rider_jobs;
create policy laundry_jobs_rider_select
on public.laundry_rider_jobs
for select to authenticated
using (
  exists (
    select 1
    from public.rider_profiles r
    where r.id=laundry_rider_jobs.rider_id
      and r.user_id=public.get_my_user_id()
  )
  or public.is_active_admin()
);

-- Defense in depth: these tables are changed by SECURITY DEFINER RPCs only.
drop policy if exists laundry_orders_customer_insert on public.laundry_orders;
drop policy if exists laundry_orders_hub_update on public.laundry_orders;
drop policy if exists laundry_jobs_rider_update on public.laundry_rider_jobs;
drop policy if exists laundry_rider_pref_insert on public.laundry_rider_preferences;
drop policy if exists laundry_rider_pref_update on public.laundry_rider_preferences;

revoke insert,update,delete on public.laundry_orders from anon,authenticated;
revoke insert,update,delete on public.laundry_rider_jobs from anon,authenticated;
revoke insert,update,delete on public.laundry_rider_preferences from anon,authenticated;
revoke insert,update,delete on public.laundry_order_events from anon,authenticated;
revoke insert,update,delete on public.laundry_services from anon,authenticated;
revoke insert,update,delete on public.laundry_shop_settings from anon,authenticated;
revoke insert,update,delete on public.laundry_hubs from anon,authenticated;
revoke insert,update,delete on public.laundry_hub_riders from anon,authenticated;
revoke insert,update,delete on public.laundry_rider_invites from anon,authenticated;

grant select on public.laundry_orders to authenticated;
grant select on public.laundry_rider_jobs to authenticated;
grant select on public.laundry_rider_preferences to authenticated;
grant select on public.laundry_order_events to authenticated;
grant select on public.laundry_services to authenticated;
grant select on public.laundry_shop_settings to authenticated;
grant select on public.laundry_hubs to authenticated;
grant select on public.laundry_hub_riders to authenticated;
grant select on public.laundry_rider_invites to authenticated;
