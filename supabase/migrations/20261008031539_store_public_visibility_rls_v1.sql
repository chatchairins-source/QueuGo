update public.shop_profiles
set onboarding_status='approved',approved_at=coalesce(approved_at,updated_at,created_at)
where status='active' and archived_at is null and onboarding_status='draft';

alter policy shop_profiles_guest_active_read on public.shop_profiles
using(status='active' and archived_at is null and onboarding_status='approved');

alter policy shop_profiles_authenticated_select on public.shop_profiles
using((status='active' and archived_at is null and onboarding_status='approved') or user_id=get_my_user_id() or get_my_role()='admin');

alter policy products_guest_available_read on public.products
using(available is true and exists(select 1 from public.shop_profiles sp where sp.id=products.shop_id and sp.status='active' and sp.archived_at is null and sp.onboarding_status='approved'));
