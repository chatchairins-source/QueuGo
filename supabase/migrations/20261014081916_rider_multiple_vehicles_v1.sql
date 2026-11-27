create table if not exists qg_private.rider_vehicles (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  vehicle_type text not null check (vehicle_type in ('motorcycle','car','bicycle','saleng')),
  vehicle_name text,
  vehicle_plate text not null,
  vehicle_capacity_kg numeric check (vehicle_capacity_kg is null or (vehicle_capacity_kg >= 0 and vehicle_capacity_kg <= 1000)),
  photo_path text,
  status text not null check (status in ('pending','approved','rejected')),
  is_selected boolean not null default false,
  approved_at timestamptz,
  approved_by uuid references public.users(id) on delete set null,
  review_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists rider_vehicles_one_selected_idx
  on qg_private.rider_vehicles(rider_id)
  where is_selected;

create unique index if not exists rider_vehicles_plate_per_rider_idx
  on qg_private.rider_vehicles(rider_id, lower(btrim(vehicle_plate)));

create index if not exists rider_vehicles_status_idx
  on qg_private.rider_vehicles(status, updated_at desc);

revoke all on table qg_private.rider_vehicles from public, anon, authenticated;
grant all on table qg_private.rider_vehicles to service_role;

insert into qg_private.rider_vehicles(
  rider_id, vehicle_type, vehicle_name, vehicle_plate, vehicle_capacity_kg,
  status, is_selected, approved_at, created_at, updated_at
)
select
  r.id,
  case
    when r.vehicle_type in ('motorcycle','car','bicycle','saleng') then r.vehicle_type
    else 'motorcycle'
  end,
  nullif(btrim(r.vehicle_name),''),
  btrim(r.vehicle_plate),
  r.vehicle_capacity_kg,
  case when r.vehicle_status='active' then 'approved' else 'pending' end,
  true,
  case when r.vehicle_status='active' then coalesce(r.vehicle_verified_at,r.updated_at) else null end,
  r.created_at,
  r.updated_at
from public.rider_profiles r
where nullif(btrim(coalesce(r.vehicle_plate,'')),'') is not null
on conflict do nothing;

create or replace function qg_private.current_rider_profile_id()
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
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
  return v_rider;
end $$;

revoke all on function qg_private.current_rider_profile_id() from public, anon;
grant execute on function qg_private.current_rider_profile_id() to authenticated;

create or replace function qg_private.rider_vehicle_list()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_result jsonb;
begin
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'vehicle_id',v.id,
        'vehicle_type',v.vehicle_type,
        'vehicle_name',v.vehicle_name,
        'vehicle_plate',v.vehicle_plate,
        'vehicle_capacity_kg',v.vehicle_capacity_kg,
        'status',v.status,
        'is_selected',v.is_selected,
        'approved_at',v.approved_at,
        'review_note',v.review_note
      )
      order by v.is_selected desc, v.status='approved' desc, v.updated_at desc, v.id
    ),
    '[]'::jsonb
  )
  into v_result
  from qg_private.rider_vehicles v
  where v.rider_id=v_rider;

  return v_result;
end $$;

revoke all on function qg_private.rider_vehicle_list() from public, anon;
grant execute on function qg_private.rider_vehicle_list() to authenticated;

create or replace function public.qg_rider_vehicle_list()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_vehicle_list(); $$;

revoke all on function public.qg_rider_vehicle_list() from public, anon;
grant execute on function public.qg_rider_vehicle_list() to authenticated, service_role;

create or replace function qg_private.rider_add_vehicle(
  p_vehicle_type text,
  p_vehicle_name text,
  p_vehicle_plate text,
  p_capacity_kg numeric,
  p_photo_path text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_type text := lower(btrim(coalesce(p_vehicle_type,'')));
  v_name text := nullif(btrim(coalesce(p_vehicle_name,'')),'');
  v_plate text := btrim(coalesce(p_vehicle_plate,''));
  v_existing qg_private.rider_vehicles%rowtype;
  v_id uuid;
begin
  if v_type not in ('motorcycle','car','bicycle','saleng') then
    raise exception 'invalid vehicle type';
  end if;
  if length(v_plate) < 2 or length(v_plate) > 32 then
    raise exception 'invalid vehicle plate';
  end if;
  if v_name is not null and length(v_name) > 100 then
    raise exception 'vehicle name too long';
  end if;
  if p_capacity_kg is not null and (p_capacity_kg < 0 or p_capacity_kg > 1000) then
    raise exception 'invalid vehicle capacity';
  end if;
  if p_photo_path is null
     or p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     or not exists (
       select 1 from storage.objects
       where bucket_id='qg-evidence' and name=p_photo_path
     ) then
    raise exception 'valid vehicle photo required';
  end if;

  if (select count(*) from qg_private.rider_vehicles where rider_id=v_rider) >= 5 then
    raise exception 'maximum five vehicles';
  end if;

  select * into v_existing
  from qg_private.rider_vehicles
  where rider_id=v_rider
    and lower(btrim(vehicle_plate))=lower(v_plate)
  limit 1
  for update;

  if v_existing.id is not null then
    if v_existing.status in ('pending','approved') then
      raise exception 'vehicle already exists';
    end if;
    update qg_private.rider_vehicles
    set vehicle_type=v_type,
        vehicle_name=v_name,
        vehicle_plate=v_plate,
        vehicle_capacity_kg=p_capacity_kg,
        photo_path=p_photo_path,
        status='pending',
        approved_at=null,
        approved_by=null,
        review_note=null,
        updated_at=now()
    where id=v_existing.id
    returning id into v_id;
  else
    insert into qg_private.rider_vehicles(
      rider_id,vehicle_type,vehicle_name,vehicle_plate,vehicle_capacity_kg,
      photo_path,status,is_selected
    )
    values(v_rider,v_type,v_name,v_plate,p_capacity_kg,p_photo_path,'pending',false)
    returning id into v_id;
  end if;

  return jsonb_build_object('vehicle_id',v_id,'status','pending');
end $$;

revoke all on function qg_private.rider_add_vehicle(text,text,text,numeric,text) from public, anon;
grant execute on function qg_private.rider_add_vehicle(text,text,text,numeric,text) to authenticated;

create or replace function public.qg_rider_add_vehicle(
  p_vehicle_type text,
  p_vehicle_name text,
  p_vehicle_plate text,
  p_capacity_kg numeric,
  p_photo_path text
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.rider_add_vehicle(p_vehicle_type,p_vehicle_name,p_vehicle_plate,p_capacity_kg,p_photo_path); $$;

revoke all on function public.qg_rider_add_vehicle(text,text,text,numeric,text) from public, anon;
grant execute on function public.qg_rider_add_vehicle(text,text,text,numeric,text) to authenticated, service_role;

create or replace function qg_private.rider_select_vehicle(p_vehicle_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid := qg_private.current_rider_profile_id();
  v_vehicle qg_private.rider_vehicles%rowtype;
  v_online boolean := false;
begin
  select coalesce((r.metadata->>'online')::boolean,false)
  into v_online
  from public.rider_profiles r
  where r.id=v_rider
  for update;

  if v_online then
    raise exception 'rider must be offline before switching vehicle';
  end if;

  if exists (
    select 1 from public.orders o
    where o.rider_id=v_rider
      and o.status not in ('completed','cancelled','no_rider_available')
  ) then
    raise exception 'rider has active delivery';
  end if;

  select * into v_vehicle
  from qg_private.rider_vehicles v
  where v.id=p_vehicle_id
    and v.rider_id=v_rider
  for update;

  if v_vehicle.id is null then
    raise exception 'vehicle unavailable';
  end if;
  if v_vehicle.status <> 'approved' then
    raise exception 'vehicle approval required';
  end if;

  update qg_private.rider_vehicles
  set is_selected=(id=v_vehicle.id),
      updated_at=case when id=v_vehicle.id then now() else updated_at end
  where rider_id=v_rider
    and (is_selected or id=v_vehicle.id);

  update public.rider_profiles
  set vehicle_type=v_vehicle.vehicle_type,
      vehicle_name=v_vehicle.vehicle_name,
      vehicle_plate=v_vehicle.vehicle_plate,
      vehicle_capacity_kg=v_vehicle.vehicle_capacity_kg,
      vehicle_status='active',
      vehicle_verified_at=coalesce(v_vehicle.approved_at,now()),
      updated_at=now()
  where id=v_rider;

  return jsonb_build_object(
    'vehicle_id',v_vehicle.id,
    'vehicle_type',v_vehicle.vehicle_type,
    'vehicle_name',v_vehicle.vehicle_name,
    'vehicle_plate',v_vehicle.vehicle_plate,
    'vehicle_capacity_kg',v_vehicle.vehicle_capacity_kg,
    'status','approved',
    'is_selected',true
  );
end $$;

revoke all on function qg_private.rider_select_vehicle(uuid) from public, anon;
grant execute on function qg_private.rider_select_vehicle(uuid) to authenticated;

create or replace function public.qg_rider_select_vehicle(p_vehicle_id uuid)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.rider_select_vehicle(p_vehicle_id); $$;

revoke all on function public.qg_rider_select_vehicle(uuid) from public, anon;
grant execute on function public.qg_rider_select_vehicle(uuid) to authenticated, service_role;

create or replace function qg_private.admin_rider_vehicle_queue()
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
        'rider_id',v.rider_id,
        'vehicle_id',v.id,
        'rider_name',coalesce(nullif(r.rider_name,''),u.name,'Rider'),
        'vehicle_type',v.vehicle_type,
        'vehicle_name',v.vehicle_name,
        'vehicle_plate',v.vehicle_plate,
        'vehicle_capacity_kg',v.vehicle_capacity_kg,
        'photo_path',v.photo_path,
        'status',v.status,
        'is_selected',v.is_selected,
        'review_note',v.review_note,
        'created_at',v.created_at,
        'updated_at',v.updated_at
      )
      order by (v.status='pending') desc, v.updated_at desc, v.id
    ),
    '[]'::jsonb
  )
  into v_result
  from qg_private.rider_vehicles v
  join public.rider_profiles r on r.id=v.rider_id
  join public.users u on u.id=r.user_id
  where u.role='rider'
    and u.status<>'deleted';

  return v_result;
end $$;

revoke all on function qg_private.admin_rider_vehicle_queue() from public, anon;
grant execute on function qg_private.admin_rider_vehicle_queue() to authenticated;

create or replace function public.qg_admin_rider_vehicle_queue()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.admin_rider_vehicle_queue(); $$;

revoke all on function public.qg_admin_rider_vehicle_queue() from public, anon;
grant execute on function public.qg_admin_rider_vehicle_queue() to authenticated, service_role;

create or replace function qg_private.admin_review_rider_vehicle(
  p_vehicle_id uuid,
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
  v_vehicle qg_private.rider_vehicles%rowtype;
  v_decision text := lower(btrim(coalesce(p_decision,'')));
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;
  if v_decision not in ('approved','rejected') then
    raise exception 'invalid decision';
  end if;
  if v_decision='rejected' and nullif(btrim(coalesce(p_note,'')),'') is null then
    raise exception 'rejection reason required';
  end if;

  select u.id into v_admin
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;

  select * into v_vehicle
  from qg_private.rider_vehicles
  where id=p_vehicle_id
  for update;

  if v_vehicle.id is null then
    raise exception 'vehicle unavailable';
  end if;
  if v_vehicle.status <> 'pending' then
    raise exception 'pending vehicle required';
  end if;
  if v_vehicle.photo_path is null and v_vehicle.approved_at is null then
    raise exception 'vehicle photo required';
  end if;

  update qg_private.rider_vehicles
  set status=v_decision,
      approved_at=case when v_decision='approved' then now() else null end,
      approved_by=case when v_decision='approved' then v_admin else null end,
      review_note=nullif(btrim(coalesce(p_note,'')),''),
      updated_at=now()
  where id=v_vehicle.id;

  if v_vehicle.is_selected then
    update public.rider_profiles
    set vehicle_status=case when v_decision='approved' then 'active' else 'pending' end,
        vehicle_verified_at=case when v_decision='approved' then now() else null end,
        updated_at=now()
    where id=v_vehicle.rider_id;
  end if;

  return jsonb_build_object('vehicle_id',v_vehicle.id,'status',v_decision);
end $$;

revoke all on function qg_private.admin_review_rider_vehicle(uuid,text,text) from public, anon;
grant execute on function qg_private.admin_review_rider_vehicle(uuid,text,text) to authenticated;

create or replace function public.qg_admin_review_rider_vehicle(
  p_vehicle_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_review_rider_vehicle(p_vehicle_id,p_decision,p_note); $$;

revoke all on function public.qg_admin_review_rider_vehicle(uuid,text,text) from public, anon;
grant execute on function public.qg_admin_review_rider_vehicle(uuid,text,text) to authenticated, service_role;

create or replace function qg_private.guard_rider_selected_vehicle_online()
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
       from qg_private.rider_vehicles v
       where v.rider_id=new.id
         and v.is_selected
         and v.status <> 'approved'
     ) then
    raise exception 'approved selected vehicle required' using errcode='42501';
  end if;
  return new;
end $$;

revoke all on function qg_private.guard_rider_selected_vehicle_online() from public, anon, authenticated;

drop trigger if exists qg_guard_rider_selected_vehicle_online on public.rider_profiles;
create trigger qg_guard_rider_selected_vehicle_online
before update of metadata on public.rider_profiles
for each row
when (
  coalesce((old.metadata->>'online')::boolean,false) is distinct from
  coalesce((new.metadata->>'online')::boolean,false)
)
execute function qg_private.guard_rider_selected_vehicle_online();
