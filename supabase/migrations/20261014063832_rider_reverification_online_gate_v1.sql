create or replace function qg_private.guard_rider_reverification_online()
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
       from qg_private.rider_reverification_requests rr
       where rr.rider_id=new.id
         and rr.status in ('required','pending','rejected')
     ) then
    raise exception 'rider identity reverification required' using errcode='42501';
  end if;

  return new;
end $$;

revoke all on function qg_private.guard_rider_reverification_online() from public, anon, authenticated;

drop trigger if exists qg_guard_rider_reverification_online on public.rider_profiles;
create trigger qg_guard_rider_reverification_online
before update of metadata on public.rider_profiles
for each row
when (
  coalesce((old.metadata->>'online')::boolean,false) is distinct from
  coalesce((new.metadata->>'online')::boolean,false)
)
execute function qg_private.guard_rider_reverification_online();
