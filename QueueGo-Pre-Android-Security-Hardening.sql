-- QueueGo pre-Android security hardening — 2026-10-04
-- Narrow profile reads without deleting tables or business data.

alter policy rider_profile_select
on public.rider_profiles
to authenticated
using (
  user_id = public.get_my_user_id()
  or public.get_my_role() = 'admin'
);

alter policy shop_profiles_authenticated_select
on public.shop_profiles
to authenticated
using (
  status = 'active'
  or user_id = public.get_my_user_id()
  or public.get_my_role() = 'admin'
);
