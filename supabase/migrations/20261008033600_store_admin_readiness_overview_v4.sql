create or replace function public.queuego_admin_shop_overview()
returns table(
  shop_id uuid,
  shop_user_id uuid,
  readiness jsonb,
  order_count bigint
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare r record;
begin
  if not public.is_active_admin() then
    raise exception 'admin only' using errcode='42501';
  end if;

  for r in
    select distinct on (sp.user_id)
      sp.id,sp.user_id
    from public.shop_profiles sp
    order by sp.user_id,(sp.archived_at is null) desc,sp.created_at desc
  loop
    shop_id:=r.id;
    shop_user_id:=r.user_id;
    readiness:=public.queuego_shop_readiness(r.id);
    select count(*) into order_count from public.orders o where o.shop_id=r.id;
    return next;
  end loop;
end
$$;

revoke all on function public.queuego_admin_shop_overview() from public,anon;
grant execute on function public.queuego_admin_shop_overview() to authenticated;
