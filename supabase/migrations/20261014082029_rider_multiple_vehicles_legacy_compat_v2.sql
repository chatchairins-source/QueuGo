alter table qg_private.rider_vehicles
  add column if not exists legacy_seed boolean not null default false;

update qg_private.rider_vehicles
set legacy_seed=true
where photo_path is null
  and approved_by is null;

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
        'legacy_seed',v.legacy_seed,
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

  if (select count(*) from qg_private.rider_vehicles where rider_id=v_rider and status<>'rejected') >= 5 then
    raise exception 'maximum five vehicles';
  end if;

  select * into v_existing
  from qg_private.rider_vehicles
  where rider_id=v_rider
    and lower(btrim(vehicle_plate))=lower(v_plate)
  limit 1
  for update;

  if v_existing.id is not null then
    if v_existing.status='approved' then
      raise exception 'vehicle already exists';
    end if;
    if v_existing.status='pending' and not v_existing.legacy_seed then
      raise exception 'vehicle already pending';
    end if;

    update qg_private.rider_vehicles
    set vehicle_type=v_type,
        vehicle_name=v_name,
        vehicle_plate=v_plate,
        vehicle_capacity_kg=p_capacity_kg,
        photo_path=p_photo_path,
        status='pending',
        legacy_seed=false,
        approved_at=null,
        approved_by=null,
        review_note=null,
        updated_at=now()
    where id=v_existing.id
    returning id into v_id;
  else
    insert into qg_private.rider_vehicles(
      rider_id,vehicle_type,vehicle_name,vehicle_plate,vehicle_capacity_kg,
      photo_path,status,is_selected,legacy_seed
    )
    values(v_rider,v_type,v_name,v_plate,p_capacity_kg,p_photo_path,'pending',false,false)
    returning id into v_id;
  end if;

  return jsonb_build_object('vehicle_id',v_id,'status','pending');
end $$;

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
        'legacy_seed',v.legacy_seed,
        'review_note',v.review_note,
        'created_at',v.created_at,
        'updated_at',v.updated_at
      )
      order by (v.status='pending' and not v.legacy_seed) desc, v.is_selected desc, v.updated_at desc, v.id
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
         and not v.legacy_seed
     ) then
    raise exception 'approved selected vehicle required' using errcode='42501';
  end if;
  return new;
end $$;
