-- Make Admin rider credit selection unambiguous and show the balance that Rider actually uses.
-- Read-only target metadata only; credit grant semantics remain unchanged.
create or replace function public.queuego_admin_rider_credit_targets(p_query text default '')
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
begin
  perform qg_private.assert_live_session();

  if not exists(
    select 1
    from public.users
    where auth_user_id=auth.uid()
      and role='admin'
      and status='active'
  ) then
    raise exception 'active admin required' using errcode='42501';
  end if;

  if p_query is null or length(p_query)>100 then
    raise exception 'search must be at most 100 characters';
  end if;

  return coalesce((
    select jsonb_agg(to_jsonb(t))
    from (
      select
        u.id as user_id,
        u.name,
        coalesce(c.cash_minor,0) as cash_minor,
        coalesce(c.cash_minor+c.promo_minor-c.held_cash_minor-c.held_promo_minor,0) as available_minor,
        au.last_sign_in_at
      from public.users u
      left join qg_private.credit_accounts c
        on c.user_id=u.id and c.kind='rider'
      left join auth.users au
        on au.id=u.auth_user_id
      where u.role='rider'
        and u.status='active'
        and (
          btrim(p_query)=''
          or position(lower(btrim(p_query)) in lower(coalesce(u.name,'')))>0
        )
      order by
        (au.last_sign_in_at is not null) desc,
        au.last_sign_in_at desc nulls last,
        u.name,
        u.id
      limit 50
    ) t
  ),'[]'::jsonb);
end
$$;

revoke all on function public.queuego_admin_rider_credit_targets(text) from public,anon,service_role;
grant execute on function public.queuego_admin_rider_credit_targets(text) to authenticated;
