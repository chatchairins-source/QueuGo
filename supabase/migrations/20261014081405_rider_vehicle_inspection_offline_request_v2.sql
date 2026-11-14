create or replace function qg_private.admin_request_rider_vehicle_inspection(
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
  v_online boolean := false;
begin
  perform qg_private.assert_live_session();

  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  select coalesce((r.metadata->>'online')::boolean,false)
  into v_online
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where r.id=p_rider_id
    and r.status='active'
    and u.role='rider'
    and u.status='active'
  limit 1;

  if not found then
    raise exception 'active rider required';
  end if;

  if v_online then
    raise exception 'rider must be offline before vehicle inspection request';
  end if;

  if exists (
    select 1
    from public.orders o
    where o.rider_id=p_rider_id
      and o.status not in ('completed','cancelled','no_rider_available')
  ) then
    raise exception 'rider has active delivery';
  end if;

  select id into v_id
  from qg_private.rider_vehicle_inspection_requests
  where rider_id=p_rider_id
    and status in ('required','pending')
  order by requested_at desc
  limit 1
  for update;

  if v_id is not null then
    update qg_private.rider_vehicle_inspection_requests
    set status='required',
        reason=nullif(btrim(p_reason),''),
        vehicle_photo_path=null,
        submitted_at=null,
        reviewed_at=null,
        reviewed_by=null,
        review_note=null,
        requested_at=now(),
        updated_at=now()
    where id=v_id;
    return v_id;
  end if;

  insert into qg_private.rider_vehicle_inspection_requests(rider_id,status,reason)
  values(p_rider_id,'required',nullif(btrim(p_reason),''))
  returning id into v_id;

  return v_id;
end $$;

revoke all on function qg_private.admin_request_rider_vehicle_inspection(uuid,text) from public, anon;
grant execute on function qg_private.admin_request_rider_vehicle_inspection(uuid,text) to authenticated;
