-- Read-only contact adapter over existing QueueGo records; no table policy changes.
create schema if not exists qg_private;
revoke all on schema qg_private from public, anon;
grant usage on schema qg_private to authenticated;

create function qg_private.rider_order_contact(p_order_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare o public.orders%rowtype; result jsonb; v_session_id text;
begin
  v_session_id := auth.jwt()->>'session_id';
  if auth.uid() is null or v_session_id is null or v_session_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception 'active assigned rider required' using errcode='42501';
  end if;
  if not exists (select 1 from auth.sessions s where s.id=v_session_id::uuid and s.user_id=auth.uid() and (s.not_after is null or s.not_after > now())) then
    raise exception 'active assigned rider required' using errcode='42501';
  end if;
  select ord.* into o from public.orders ord
  where ord.id=p_order_id and ord.status in ('rider_assigned','preparing','ready','picked_up','in_progress','assigned','completed')
    and exists (select 1 from public.rider_profiles r join public.users u on u.id=r.user_id
      where r.id=ord.rider_id and r.status='active' and u.auth_user_id=auth.uid() and u.role='rider' and u.status='active');
  if not found then raise exception 'active assigned rider required' using errcode='42501'; end if;
  if o.status='completed' and not exists (
    select 1 from public.deliveries d where d.order_id=o.id and d.delivered_at is not null
      and d.delivered_at <= now() and now() < d.delivered_at + interval '30 minutes'
  ) then raise exception 'order contact expired' using errcode='42501'; end if;
  select jsonb_build_object('name',c.name,'phone',c.phone) into result
    from public.users c where c.id=o.customer_id and c.status='active';
  return coalesce(result,'{}'::jsonb);
end $$;
revoke all on function qg_private.rider_order_contact(uuid) from public, anon;
grant execute on function qg_private.rider_order_contact(uuid) to authenticated;

create function public.qg_rider_order_contact(p_order_id uuid)
returns jsonb language sql stable security invoker set search_path = ''
as $$ select qg_private.rider_order_contact(p_order_id); $$;
revoke all on function public.qg_rider_order_contact(uuid) from public, anon;
grant execute on function public.qg_rider_order_contact(uuid) to authenticated;
