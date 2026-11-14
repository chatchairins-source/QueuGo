create table if not exists qg_private.rider_document_expiries (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  vehicle_id uuid references qg_private.rider_vehicles(id) on delete cascade,
  document_key text not null,
  expires_on date not null,
  recorded_by uuid references public.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint rider_document_expiries_key_check
    check (document_key in ('national_id','driver_license','vehicle_prb')),
  constraint rider_document_expiries_subject_check
    check (
      (document_key in ('national_id','driver_license') and vehicle_id is null)
      or
      (document_key='vehicle_prb' and vehicle_id is not null)
    )
);

create unique index if not exists rider_document_expiries_rider_doc_uidx
  on qg_private.rider_document_expiries(rider_id,document_key)
  where vehicle_id is null;

create unique index if not exists rider_document_expiries_vehicle_doc_uidx
  on qg_private.rider_document_expiries(vehicle_id,document_key)
  where vehicle_id is not null;

create index if not exists rider_document_expiries_expiry_idx
  on qg_private.rider_document_expiries(expires_on);

revoke all on table qg_private.rider_document_expiries from public, anon, authenticated;
grant all on table qg_private.rider_document_expiries to service_role;

create or replace function qg_private.rider_document_readiness()
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
  with selected_vehicle as (
    select v.id, v.vehicle_plate
    from qg_private.rider_vehicles v
    where v.rider_id=v_rider
      and v.is_selected
    order by v.updated_at desc, v.id
    limit 1
  ),
  expected as (
    select
      'national_id'::text as document_key,
      'บัตรประชาชน'::text as label,
      null::uuid as vehicle_id,
      null::text as vehicle_plate
    union all
    select
      'driver_license',
      'ใบขับขี่',
      null::uuid,
      null::text
    union all
    select
      'vehicle_prb',
      'พ.ร.บ. รถคันปัจจุบัน',
      sv.id,
      sv.vehicle_plate
    from selected_vehicle sv
    union all
    select
      'vehicle_prb',
      'พ.ร.บ. รถคันปัจจุบัน',
      null::uuid,
      null::text
    where not exists (select 1 from selected_vehicle)
  ),
  states as (
    select
      e.document_key,
      e.label,
      e.vehicle_id,
      e.vehicle_plate,
      d.expires_on,
      case
        when d.expires_on is null then 'unknown'
        when d.expires_on < current_date then 'expired'
        when d.expires_on <= current_date + 30 then 'expiring'
        else 'valid'
      end as status,
      case when d.expires_on is null then null
           else (d.expires_on-current_date)::integer
      end as days_remaining
    from expected e
    left join qg_private.rider_document_expiries d
      on d.rider_id=v_rider
     and d.document_key=e.document_key
     and (
       (e.vehicle_id is null and d.vehicle_id is null)
       or d.vehicle_id=e.vehicle_id
     )
  )
  select jsonb_build_object(
    'blocking',
    coalesce(bool_or(status='expired'),false),
    'items',
    coalesce(
      jsonb_agg(
        jsonb_build_object(
          'key',document_key,
          'label',label,
          'vehicle_id',vehicle_id,
          'vehicle_plate',vehicle_plate,
          'expires_on',expires_on,
          'status',status,
          'days_remaining',days_remaining
        )
        order by case document_key
          when 'national_id' then 1
          when 'driver_license' then 2
          else 3
        end
      ),
      '[]'::jsonb
    )
  )
  into v_result
  from states;

  return v_result;
end $$;

revoke all on function qg_private.rider_document_readiness() from public, anon;
grant execute on function qg_private.rider_document_readiness() to authenticated;

create or replace function public.qg_rider_document_readiness()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_document_readiness(); $$;

revoke all on function public.qg_rider_document_readiness() from public, anon;
grant execute on function public.qg_rider_document_readiness() to authenticated, service_role;

create or replace function qg_private.admin_set_rider_document_expiry(
  p_rider_id uuid,
  p_document_key text,
  p_expires_on date,
  p_vehicle_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_key text := lower(btrim(coalesce(p_document_key,'')));
  v_admin_user uuid;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  select u.id into v_admin_user
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;

  if not exists (
    select 1
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    where r.id=p_rider_id
      and u.role='rider'
      and u.status<>'deleted'
  ) then
    raise exception 'rider unavailable';
  end if;

  if v_key not in ('national_id','driver_license','vehicle_prb') then
    raise exception 'invalid rider document';
  end if;

  if v_key='vehicle_prb' then
    if p_vehicle_id is null or not exists (
      select 1
      from qg_private.rider_vehicles v
      where v.id=p_vehicle_id
        and v.rider_id=p_rider_id
    ) then
      raise exception 'vehicle unavailable';
    end if;
  elsif p_vehicle_id is not null then
    raise exception 'vehicle not allowed for rider document';
  end if;

  if p_expires_on is not null
     and (
       p_expires_on < current_date - interval '5 years'
       or p_expires_on > current_date + interval '20 years'
     ) then
    raise exception 'document expiry date out of range';
  end if;

  if v_key='vehicle_prb' then
    delete from qg_private.rider_document_expiries
    where rider_id=p_rider_id
      and document_key=v_key
      and vehicle_id=p_vehicle_id;
  else
    delete from qg_private.rider_document_expiries
    where rider_id=p_rider_id
      and document_key=v_key
      and vehicle_id is null;
  end if;

  if p_expires_on is not null then
    insert into qg_private.rider_document_expiries(
      rider_id,vehicle_id,document_key,expires_on,recorded_by,updated_at
    )
    values(
      p_rider_id,
      case when v_key='vehicle_prb' then p_vehicle_id else null end,
      v_key,
      p_expires_on,
      v_admin_user,
      now()
    );
  end if;

  return jsonb_build_object(
    'rider_id',p_rider_id,
    'document_key',v_key,
    'vehicle_id',case when v_key='vehicle_prb' then p_vehicle_id else null end,
    'expires_on',p_expires_on
  );
end $$;

revoke all on function qg_private.admin_set_rider_document_expiry(uuid,text,date,uuid) from public, anon;
grant execute on function qg_private.admin_set_rider_document_expiry(uuid,text,date,uuid) to authenticated;

create or replace function public.qg_admin_set_rider_document_expiry(
  p_rider_id uuid,
  p_document_key text,
  p_expires_on date,
  p_vehicle_id uuid
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_set_rider_document_expiry(
  p_rider_id,p_document_key,p_expires_on,p_vehicle_id
); $$;

revoke all on function public.qg_admin_set_rider_document_expiry(uuid,text,date,uuid) from public, anon;
grant execute on function public.qg_admin_set_rider_document_expiry(uuid,text,date,uuid) to authenticated, service_role;

create or replace function qg_private.admin_rider_document_queue()
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

  with riders as (
    select
      r.id as rider_id,
      coalesce(nullif(r.rider_name,''),u.name,'Rider') as rider_name,
      sv.id as selected_vehicle_id,
      sv.vehicle_plate as selected_vehicle_plate
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    left join lateral (
      select v.id,v.vehicle_plate
      from qg_private.rider_vehicles v
      where v.rider_id=r.id
        and v.is_selected
      order by v.updated_at desc,v.id
      limit 1
    ) sv on true
    where u.role='rider'
      and u.status<>'deleted'
  )
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'rider_id',r.rider_id,
        'rider_name',r.rider_name,
        'selected_vehicle_id',r.selected_vehicle_id,
        'selected_vehicle_plate',r.selected_vehicle_plate,
        'national_id_expires_on',nid.expires_on,
        'driver_license_expires_on',lic.expires_on,
        'vehicle_prb_expires_on',prb.expires_on
      )
      order by r.rider_name,r.rider_id
    ),
    '[]'::jsonb
  )
  into v_result
  from riders r
  left join qg_private.rider_document_expiries nid
    on nid.rider_id=r.rider_id
   and nid.document_key='national_id'
   and nid.vehicle_id is null
  left join qg_private.rider_document_expiries lic
    on lic.rider_id=r.rider_id
   and lic.document_key='driver_license'
   and lic.vehicle_id is null
  left join qg_private.rider_document_expiries prb
    on prb.rider_id=r.rider_id
   and prb.document_key='vehicle_prb'
   and prb.vehicle_id=r.selected_vehicle_id;

  return v_result;
end $$;

revoke all on function qg_private.admin_rider_document_queue() from public, anon;
grant execute on function qg_private.admin_rider_document_queue() to authenticated;

create or replace function public.qg_admin_rider_document_queue()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.admin_rider_document_queue(); $$;

revoke all on function public.qg_admin_rider_document_queue() from public, anon;
grant execute on function public.qg_admin_rider_document_queue() to authenticated, service_role;

create or replace function qg_private.guard_rider_document_expiry_online()
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
     and (
       exists (
         select 1
         from qg_private.rider_document_expiries d
         where d.rider_id=new.id
           and d.vehicle_id is null
           and d.document_key in ('national_id','driver_license')
           and d.expires_on < current_date
       )
       or exists (
         select 1
         from qg_private.rider_vehicles v
         join qg_private.rider_document_expiries d
           on d.vehicle_id=v.id
          and d.document_key='vehicle_prb'
         where v.rider_id=new.id
           and v.is_selected
           and d.expires_on < current_date
       )
     ) then
    raise exception 'rider document expired' using errcode='42501';
  end if;
  return new;
end $$;

revoke all on function qg_private.guard_rider_document_expiry_online() from public, anon, authenticated;

drop trigger if exists qg_guard_rider_document_expiry_online on public.rider_profiles;
create trigger qg_guard_rider_document_expiry_online
before update of metadata on public.rider_profiles
for each row
when (
  coalesce((old.metadata->>'online')::boolean,false) is distinct from
  coalesce((new.metadata->>'online')::boolean,false)
)
execute function qg_private.guard_rider_document_expiry_online();
