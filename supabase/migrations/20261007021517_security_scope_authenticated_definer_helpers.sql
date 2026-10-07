-- QueueGo authenticated SECURITY DEFINER scope hardening.
-- Production migration: 20261007021517_security_scope_authenticated_definer_helpers

create or replace function public.effective_gp_rate(
  p_shop_profile_id uuid,
  p_day date default current_date
) returns numeric
language plpgsql stable security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_user uuid:=public.get_my_user_id();
  v_role text:=public.get_my_role();
  v_rate numeric;
begin
  if v_user is null then
    raise exception 'active account required' using errcode='42501';
  end if;

  if v_role<>'admin' and not exists(
    select 1
    from public.shop_profiles sp
    join public.users u on u.id=sp.user_id
    where sp.id=p_shop_profile_id
      and sp.user_id=v_user
      and u.role='shop'
      and u.status='active'
  ) then
    raise exception 'shop access denied' using errcode='42501';
  end if;

  select public.effective_gp_rate_at(
    p_shop_profile_id,
    (p_day::timestamp at time zone 'Asia/Bangkok')
  ) into v_rate;

  return v_rate;
end $$;

revoke all on function public.effective_gp_rate(uuid,date) from public,anon;
grant execute on function public.effective_gp_rate(uuid,date) to authenticated,service_role;

-- Retired legacy catalog and nearest-market helper have no current client caller.
-- Keep service_role access only; active customer flows use v2 market directory RPCs.
revoke execute on function public.market_public_catalog() from authenticated;
grant execute on function public.market_public_catalog() to service_role;

revoke execute on function public.queuego_nearest_market(double precision,double precision,numeric) from authenticated;
grant execute on function public.queuego_nearest_market(double precision,double precision,numeric) to service_role;
