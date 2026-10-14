create table if not exists qg_private.rider_vehicle_inspection_requests (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  status text not null check (status in ('required','pending','approved','rejected')),
  reason text,
  vehicle_photo_path text,
  requested_at timestamptz not null default now(),
  submitted_at timestamptz,
  reviewed_at timestamptz,
  reviewed_by uuid references public.users(id) on delete set null,
  review_note text,
  updated_at timestamptz not null default now()
);

create unique index if not exists rider_vehicle_inspection_one_open_idx
  on qg_private.rider_vehicle_inspection_requests(rider_id)
  where status in ('required','pending');

create index if not exists rider_vehicle_inspection_rider_created_idx
  on qg_private.rider_vehicle_inspection_requests(rider_id, requested_at desc);

revoke all on table qg_private.rider_vehicle_inspection_requests from public, anon, authenticated;
grant all on table qg_private.rider_vehicle_inspection_requests to service_role;

create or replace function qg_private.rider_vehicle_inspection_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_row qg_private.rider_vehicle_inspection_requests%rowtype;
begin
  perform qg_private.assert_live_session();
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;
  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;
  select * into v_row
  from qg_private.rider_vehicle_inspection_requests
  where rider_id=v_rider
  order by requested_at desc
  limit 1;
  if v_row.id is null then
    return jsonb_build_object('required',false);
  end if;
  return jsonb_build_object(
    'required',v_row.status in ('required','pending','rejected'),
    'request_id',v_row.id,
    'status',v_row.status,
    'reason',v_row.reason,
    'requested_at',v_row.requested_at,
    'submitted_at',v_row.submitted_at,
    'reviewed_at',v_row.reviewed_at,
    'review_note',v_row.review_note
  );
end $$;

revoke all on function qg_private.rider_vehicle_inspection_status() from public, anon;
grant execute on function qg_private.rider_vehicle_inspection_status() to authenticated;

create or replace function public.qg_rider_vehicle_inspection_status()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_vehicle_inspection_status(); $$;

revoke all on function public.qg_rider_vehicle_inspection_status() from public, anon;
grant execute on function public.qg_rider_vehicle_inspection_status() to authenticated, service_role;

create or replace function qg_private.rider_submit_vehicle_inspection(p_photo_path text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_request uuid;
begin
  perform qg_private.assert_live_session();
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;
  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;
  if p_photo_path is null
     or p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     or not exists (
       select 1 from storage.objects
       where bucket_id='qg-evidence' and name=p_photo_path
     ) then
    raise exception 'valid vehicle photo required';
  end if;
  select id into v_request
  from qg_private.rider_vehicle_inspection_requests
  where rider_id=v_rider
    and status in ('required','pending','rejected')
  order by requested_at desc
  limit 1
  for update;
  if v_request is null then
    raise exception 'vehicle inspection is not required';
  end if;
  update qg_private.rider_vehicle_inspection_requests
  set status='pending',
      vehicle_photo_path=p_photo_path,
      submitted_at=now(),
      reviewed_at=null,
      reviewed_by=null,
      review_note=null,
      updated_at=now()
  where id=v_request;
  return qg_private.rider_vehicle_inspection_status();
end $$;

revoke all on function qg_private.rider_submit_vehicle_inspection(text) from public, anon;
grant execute on function qg_private.rider_submit_vehicle_inspection(text) to authenticated;

create or replace function public.qg_rider_submit_vehicle_inspection(p_photo_path text)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.rider_submit_vehicle_inspection(p_photo_path); $$;

revoke all on function public.qg_rider_submit_vehicle_inspection(text) from public, anon;
grant execute on function public.qg_rider_submit_vehicle_inspection(text) to authenticated, service_role;

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
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;
  if not exists (
    select 1
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    where r.id=p_rider_id
      and r.status='active'
      and u.role='rider'
      and u.status='active'
  ) then
    raise exception 'active rider required';
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

create or replace function public.qg_admin_request_rider_vehicle_inspection(
  p_rider_id uuid,
  p_reason text default null
)
returns uuid
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_request_rider_vehicle_inspection(p_rider_id,p_reason); $$;

revoke all on function public.qg_admin_request_rider_vehicle_inspection(uuid,text) from public, anon;
grant execute on function public.qg_admin_request_rider_vehicle_inspection(uuid,text) to authenticated, service_role;

create or replace function qg_private.admin_review_rider_vehicle_inspection(
  p_request_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin uuid;
  v_status text;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;
  if lower(coalesce(p_decision,'')) not in ('approved','rejected') then
    raise exception 'invalid decision';
  end if;
  if lower(p_decision)='rejected' and nullif(btrim(p_note),'') is null then
    raise exception 'rejection reason required';
  end if;
  select u.id into v_admin
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;
  update qg_private.rider_vehicle_inspection_requests
  set status=lower(p_decision),
      reviewed_at=now(),
      reviewed_by=v_admin,
      review_note=nullif(btrim(p_note),''),
      updated_at=now()
  where id=p_request_id
    and status='pending'
    and vehicle_photo_path is not null
  returning status into v_status;
  if v_status is null then
    raise exception 'pending vehicle photo required';
  end if;
  return jsonb_build_object('request_id',p_request_id,'status',v_status);
end $$;

revoke all on function qg_private.admin_review_rider_vehicle_inspection(uuid,text,text) from public, anon;
grant execute on function qg_private.admin_review_rider_vehicle_inspection(uuid,text,text) to authenticated;

create or replace function public.qg_admin_review_rider_vehicle_inspection(
  p_request_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_review_rider_vehicle_inspection(p_request_id,p_decision,p_note); $$;

revoke all on function public.qg_admin_review_rider_vehicle_inspection(uuid,text,text) from public, anon;
grant execute on function public.qg_admin_review_rider_vehicle_inspection(uuid,text,text) to authenticated, service_role;

create or replace function qg_private.admin_rider_vehicle_inspection_queue()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_result jsonb;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'rider_id',r.id,
        'request_id',ri.id,
        'status',ri.status,
        'reason',ri.reason,
        'vehicle_photo_path',ri.vehicle_photo_path,
        'requested_at',ri.requested_at,
        'submitted_at',ri.submitted_at,
        'reviewed_at',ri.reviewed_at,
        'review_note',ri.review_note
      )
      order by coalesce(ri.updated_at,r.updated_at) desc,r.id
    ),
    '[]'::jsonb
  )
  into v_result
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  left join lateral (
    select x.*
    from qg_private.rider_vehicle_inspection_requests x
    where x.rider_id=r.id
    order by x.requested_at desc
    limit 1
  ) ri on true
  where u.role='rider'
    and u.status<>'deleted';
  return v_result;
end $$;

revoke all on function qg_private.admin_rider_vehicle_inspection_queue() from public, anon;
grant execute on function qg_private.admin_rider_vehicle_inspection_queue() to authenticated;

create or replace function public.qg_admin_rider_vehicle_inspection_queue()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.admin_rider_vehicle_inspection_queue(); $$;

revoke all on function public.qg_admin_rider_vehicle_inspection_queue() from public, anon;
grant execute on function public.qg_admin_rider_vehicle_inspection_queue() to authenticated, service_role;

create or replace function qg_private.guard_rider_vehicle_inspection_online()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_old_online boolean := coalesce((old.metadata->>'online')::boolean,false);
  v_new_online boolean := coalesce((new.metadata->>'online')::boolean,false);
begin
  if not v_old_online
     and v_new_online
     and auth.uid() is not null
     and coalesce(auth.role(),'') <> 'service_role'
     and not public.is_active_admin()
     and exists (
       select 1
       from qg_private.rider_vehicle_inspection_requests ri
       where ri.rider_id=new.id
         and ri.status in ('required','pending','rejected')
     ) then
    raise exception 'rider vehicle inspection required' using errcode='42501';
  end if;
  return new;
end $$;

revoke all on function qg_private.guard_rider_vehicle_inspection_online() from public, anon, authenticated;

drop trigger if exists qg_guard_rider_vehicle_inspection_online on public.rider_profiles;
create trigger qg_guard_rider_vehicle_inspection_online
before update of metadata on public.rider_profiles
for each row
when (
  coalesce((old.metadata->>'online')::boolean,false) is distinct from
  coalesce((new.metadata->>'online')::boolean,false)
)
execute function qg_private.guard_rider_vehicle_inspection_online();
