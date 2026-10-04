-- QueueGo pre-Android security hardening — 2026-10-04
-- Restrict profile rows that contain operational/private fields while preserving
-- customer storefront reads, rider self-service and full active-admin access.

drop policy if exists rider_profile_select on public.rider_profiles;
drop policy if exists rider_profile_update on public.rider_profiles;

drop policy if exists shop_profiles_authenticated_select on public.shop_profiles;
create policy shop_profiles_authenticated_select
on public.shop_profiles
for select
to authenticated
using (
  status = 'active'
  or user_id = public.get_my_user_id()
);

-- Existing policies intentionally retained:
-- rider_profiles_select_own / rider_profiles_update_own
-- rider_profiles_admin_select / rider_profiles_admin_update
-- shop_profiles_admin_select
-- shop_profiles_guest_active_read
