-- Keep existing UPDATE/vehicle guards; close direct INSERT approval bypass.
-- Approved legacy riders may recreate a missing profile using authoritative users.status.
create or replace function public.qg_guard_rider_profile_insert()
returns trigger language plpgsql
set search_path = public, pg_temp
as $function$
declare account_status text;
begin
  if current_user in ('postgres','service_role') or public.is_active_admin() then
    return new;
  end if;
  select u.status into account_status from public.users u
    where u.id = new.user_id and u.auth_user_id = auth.uid() and u.role = 'rider';
  if account_status is null then
    raise exception 'Only an owned rider account may create a rider profile' using errcode = '42501';
  end if;
  if new.status is distinct from
       (case when account_status = 'active' then 'active' else 'pending' end) then
    raise exception 'Rider approval must match the authoritative account approval' using errcode = '42501';
  end if;
  return new;
end;
$function$;
revoke all on function public.qg_guard_rider_profile_insert() from public, anon, authenticated;
create trigger qg_guard_rider_profile_insert before insert on public.rider_profiles
for each row execute function public.qg_guard_rider_profile_insert();

