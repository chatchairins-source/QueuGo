create table if not exists qg_private.rider_emergency_contacts (
  rider_id uuid primary key references public.rider_profiles(id) on delete cascade,
  contact_name text not null,
  contact_phone text not null,
  updated_at timestamptz not null default now(),
  constraint rider_emergency_contact_name_length
    check (char_length(contact_name) between 1 and 100),
  constraint rider_emergency_contact_phone_format
    check (contact_phone ~ '^\\+?[0-9]{8,15}$')
);

revoke all on table qg_private.rider_emergency_contacts from public, anon, authenticated;
grant all on table qg_private.rider_emergency_contacts to service_role;

create or replace function qg_private.rider_emergency_contact()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_row qg_private.rider_emergency_contacts%rowtype;
begin
  perform qg_private.assert_live_session();

  select r.id
  into v_rider
  from public.rider_profiles r
  join public.users u on u.id = r.user_id
  where u.auth_user_id = auth.uid()
    and u.role = 'rider'
    and u.status = 'active'
    and r.status = 'active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  select *
  into v_row
  from qg_private.rider_emergency_contacts
  where rider_id = v_rider;

  if not found then
    return jsonb_build_object('configured', false);
  end if;

  return jsonb_build_object(
    'configured', true,
    'name', v_row.contact_name,
    'phone', v_row.contact_phone,
    'updated_at', v_row.updated_at
  );
end $$;

revoke all on function qg_private.rider_emergency_contact() from public, anon;
grant execute on function qg_private.rider_emergency_contact() to authenticated;

create or replace function public.qg_rider_emergency_contact()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_emergency_contact(); $$;

revoke all on function public.qg_rider_emergency_contact() from public, anon;
grant execute on function public.qg_rider_emergency_contact() to authenticated, service_role;

create or replace function qg_private.rider_set_emergency_contact(
  p_name text,
  p_phone text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_name text := nullif(btrim(coalesce(p_name,'')), '');
  v_phone_raw text := nullif(btrim(coalesce(p_phone,'')), '');
  v_phone text;
begin
  perform qg_private.assert_live_session();

  select r.id
  into v_rider
  from public.rider_profiles r
  join public.users u on u.id = r.user_id
  where u.auth_user_id = auth.uid()
    and u.role = 'rider'
    and u.status = 'active'
    and r.status = 'active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  if v_name is null and v_phone_raw is null then
    delete from qg_private.rider_emergency_contacts
    where rider_id = v_rider;
    return jsonb_build_object('configured', false);
  end if;

  if v_name is null or v_phone_raw is null then
    raise exception 'emergency contact name and phone required';
  end if;

  if char_length(v_name) > 100 then
    raise exception 'emergency contact name too long';
  end if;

  v_phone := case
    when left(v_phone_raw,1)='+' then
      '+' || regexp_replace(substr(v_phone_raw,2), '[^0-9]', '', 'g')
    else
      regexp_replace(v_phone_raw, '[^0-9]', '', 'g')
  end;

  if v_phone !~ '^\\+?[0-9]{8,15}$' then
    raise exception 'invalid emergency contact phone';
  end if;

  insert into qg_private.rider_emergency_contacts(
    rider_id,contact_name,contact_phone,updated_at
  )
  values(v_rider,v_name,v_phone,now())
  on conflict(rider_id) do update
    set contact_name=excluded.contact_name,
        contact_phone=excluded.contact_phone,
        updated_at=now();

  return jsonb_build_object(
    'configured', true,
    'name', v_name,
    'phone', v_phone
  );
end $$;

revoke all on function qg_private.rider_set_emergency_contact(text,text) from public, anon;
grant execute on function qg_private.rider_set_emergency_contact(text,text) to authenticated;

create or replace function public.qg_rider_set_emergency_contact(
  p_name text,
  p_phone text
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.rider_set_emergency_contact(p_name,p_phone); $$;

revoke all on function public.qg_rider_set_emergency_contact(text,text) from public, anon;
grant execute on function public.qg_rider_set_emergency_contact(text,text) to authenticated, service_role;
