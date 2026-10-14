create or replace function qg_private.admin_request_rider_reverification(
  p_rider_id uuid,
  p_reason text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid;
  v_admin uuid;
  v_reason text;
begin
  perform qg_private.assert_live_session();

  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  v_reason := nullif(btrim(p_reason),'');
  if v_reason is null then
    raise exception 'reverification reason required';
  end if;

  select u.id into v_admin
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;

  if not exists (
    select 1 from public.rider_profiles r
    join public.users u on u.id=r.user_id
    where r.id=p_rider_id and r.status='active' and u.status='active'
  ) then
    raise exception 'active rider required';
  end if;

  select id into v_id
  from qg_private.rider_reverification_requests
  where rider_id=p_rider_id and status in ('required','pending')
  order by requested_at desc
  limit 1
  for update;

  if v_id is not null then
    update qg_private.rider_reverification_requests
    set status='required',
        reason=v_reason,
        selfie_path=null,
        submitted_at=null,
        reviewed_at=null,
        reviewed_by=null,
        review_note=null,
        requested_at=now(),
        updated_at=now()
    where id=v_id;
    return v_id;
  end if;

  insert into qg_private.rider_reverification_requests(rider_id,status,reason)
  values(p_rider_id,'required',v_reason)
  returning id into v_id;
  return v_id;
end $$;

revoke all on function qg_private.admin_request_rider_reverification(uuid,text) from public, anon;
grant execute on function qg_private.admin_request_rider_reverification(uuid,text) to authenticated;
