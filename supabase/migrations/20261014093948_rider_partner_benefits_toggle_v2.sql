create or replace function qg_private.admin_set_rider_benefit_enabled(
  p_benefit_id uuid,
  p_enabled boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  update qg_private.rider_benefits
  set enabled=coalesce(p_enabled,false), updated_at=now()
  where id=p_benefit_id;

  if not found then
    raise exception 'rider benefit unavailable';
  end if;

  return jsonb_build_object(
    'benefit_id',p_benefit_id,
    'enabled',coalesce(p_enabled,false)
  );
end $$;

revoke all on function qg_private.admin_set_rider_benefit_enabled(uuid,boolean) from public, anon;
grant execute on function qg_private.admin_set_rider_benefit_enabled(uuid,boolean) to authenticated;

create or replace function public.qg_admin_set_rider_benefit_enabled(
  p_benefit_id uuid,
  p_enabled boolean
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_set_rider_benefit_enabled(p_benefit_id,p_enabled); $$;

revoke all on function public.qg_admin_set_rider_benefit_enabled(uuid,boolean) from public, anon;
grant execute on function public.qg_admin_set_rider_benefit_enabled(uuid,boolean) to authenticated, service_role;