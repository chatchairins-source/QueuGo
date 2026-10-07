-- QueueGo Pilot performance gate: cache auth.uid() once per statement in RLS policies.

drop policy if exists "users_select_own" on public.users;
create policy "users_select_own" on public.users
for select to authenticated using (auth_user_id=(select auth.uid()));

drop policy if exists "users_insert_own" on public.users;
create policy "users_insert_own" on public.users
for insert to authenticated with check (auth_user_id=(select auth.uid()));

drop policy if exists "users_update_own" on public.users;
create policy "users_update_own" on public.users
for update to authenticated
using (auth_user_id=(select auth.uid()))
with check (auth_user_id=(select auth.uid()));

drop policy if exists "QueueTech audit logs insert authenticated" on public.audit_logs;
create policy "QueueTech audit logs insert authenticated" on public.audit_logs
for insert to authenticated
with check (
  user_id is null or exists(
    select 1 from public.users u
    where u.id=audit_logs.user_id and u.auth_user_id=(select auth.uid())
  )
);

drop policy if exists "shop_profiles_shop_insert" on public.shop_profiles;
create policy "shop_profiles_shop_insert" on public.shop_profiles
for insert to authenticated
with check (user_id in (
  select u.id from public.users u
  where u.auth_user_id=(select auth.uid()) and u.role='shop'
));

drop policy if exists "shop_profiles_shop_delete" on public.shop_profiles;
create policy "shop_profiles_shop_delete" on public.shop_profiles
for delete to authenticated
using (user_id in (
  select u.id from public.users u
  where u.auth_user_id=(select auth.uid()) and u.role='shop'
));

drop policy if exists "shop_profiles_shop_update" on public.shop_profiles;
create policy "shop_profiles_shop_update" on public.shop_profiles
for update to authenticated
using (user_id in (
  select u.id from public.users u
  where u.auth_user_id=(select auth.uid()) and u.role='shop'
))
with check (user_id in (
  select u.id from public.users u
  where u.auth_user_id=(select auth.uid()) and u.role='shop'
));

drop policy if exists "rider_profile_update" on public.rider_profiles;
create policy "rider_profile_update" on public.rider_profiles
for update to authenticated
using (user_id in (
  select u.id from public.users u
  where u.auth_user_id=(select auth.uid()) and u.role='rider'
))
with check (user_id in (
  select u.id from public.users u
  where u.auth_user_id=(select auth.uid()) and u.role='rider'
));

drop policy if exists "user_active_sessions_select_own" on public.user_active_sessions;
create policy "user_active_sessions_select_own" on public.user_active_sessions
for select to authenticated using ((select auth.uid())=user_id);

drop policy if exists "laundry_orders_customer_select" on public.laundry_orders;
create policy "laundry_orders_customer_select" on public.laundry_orders
for select to authenticated
using (exists(
  select 1 from public.users u
  where u.id=laundry_orders.customer_id and u.auth_user_id=(select auth.uid())
));

drop policy if exists "laundry_hub_riders_read" on public.laundry_hub_riders;
create policy "laundry_hub_riders_read" on public.laundry_hub_riders
for select to authenticated
using (
  exists(
    select 1 from public.rider_profiles r
    where r.id=laundry_hub_riders.rider_id
      and r.user_id=(select users.id from public.users where users.auth_user_id=(select auth.uid()) limit 1)
  )
  or exists(
    select 1 from public.laundry_hubs h
    join public.shop_profiles s on s.id=h.shop_id
    where h.id=laundry_hub_riders.hub_id
      and s.user_id=(select users.id from public.users where users.auth_user_id=(select auth.uid()) limit 1)
  )
);

drop policy if exists "laundry_events_customer_read" on public.laundry_order_events;
create policy "laundry_events_customer_read" on public.laundry_order_events
for select to authenticated
using (exists(
  select 1 from public.laundry_orders o
  join public.users u on u.id=o.customer_id
  where o.id=laundry_order_events.laundry_order_id
    and u.auth_user_id=(select auth.uid())
));

drop policy if exists "laundry_events_rider_read" on public.laundry_order_events;
create policy "laundry_events_rider_read" on public.laundry_order_events
for select to authenticated
using (exists(
  select 1 from public.laundry_rider_jobs j
  join public.rider_profiles r on r.id=j.rider_id
  join public.users u on u.id=r.user_id
  where j.laundry_order_id=laundry_order_events.laundry_order_id
    and u.auth_user_id=(select auth.uid())
    and u.role='rider'
    and u.status='active'
));
