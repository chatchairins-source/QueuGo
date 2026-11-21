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
  v_online boolean := false;
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

    if v_existing.legacy_seed and v_existing.is_selected then
      select coalesce((r.metadata->>'online')::boolean,false)
      into v_online
      from public.rider_profiles r
      where r.id=v_rider
      for update;

      if v_online then
        raise exception 'rider must be offline before verifying selected vehicle';
      end if;

      if exists (
        select 1 from public.orders o
        where o.rider_id=v_rider
          and o.status not in ('completed','cancelled','no_rider_available')
      ) then
        raise exception 'rider has active delivery';
      end if;
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
