insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values(
  'rider-profile',
  'rider-profile',
  true,
  5242880,
  array['image/jpeg','image/png','image/webp']::text[]
)
on conflict(id) do nothing;

drop policy if exists rider_profile_owner_select on storage.objects;
create policy rider_profile_owner_select
on storage.objects for select
to authenticated
using (
  bucket_id='rider-profile'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.users u
    where u.auth_user_id=(select auth.uid())
      and u.role='rider'
      and u.status='active'
  )
);

drop policy if exists rider_profile_owner_insert on storage.objects;
create policy rider_profile_owner_insert
on storage.objects for insert
to authenticated
with check (
  bucket_id='rider-profile'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.users u
    where u.auth_user_id=(select auth.uid())
      and u.role='rider'
      and u.status='active'
  )
);

drop policy if exists rider_profile_owner_update on storage.objects;
create policy rider_profile_owner_update
on storage.objects for update
to authenticated
using (
  bucket_id='rider-profile'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.users u
    where u.auth_user_id=(select auth.uid())
      and u.role='rider'
      and u.status='active'
  )
)
with check (
  bucket_id='rider-profile'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.users u
    where u.auth_user_id=(select auth.uid())
      and u.role='rider'
      and u.status='active'
  )
);

drop policy if exists rider_profile_owner_delete on storage.objects;
create policy rider_profile_owner_delete
on storage.objects for delete
to authenticated
using (
  bucket_id='rider-profile'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.users u
    where u.auth_user_id=(select auth.uid())
      and u.role='rider'
      and u.status='active'
  )
);

create or replace function qg_private.rider_set_profile_photo(p_path text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid;
  v_expected_path text;
  v_url text;
  v_profile uuid;
begin
  perform qg_private.assert_live_session();
  v_uid := auth.uid();
  v_expected_path := v_uid::text || '/profile';

  if p_path is null or p_path <> v_expected_path then
    raise exception 'invalid rider profile photo path' using errcode='42501';
  end if;

  if not exists (
    select 1
    from storage.objects o
    where o.bucket_id='rider-profile'
      and o.name=v_expected_path
      and o.owner_id=v_uid::text
  ) then
    raise exception 'rider profile photo not uploaded' using errcode='22023';
  end if;

  v_url := 'https://pkypiqhlrmzocysgeqew.supabase.co/storage/v1/object/public/rider-profile/'
    || v_expected_path
    || '?v='
    || floor(extract(epoch from clock_timestamp())*1000)::bigint::text;

  update public.rider_profiles r
  set metadata=jsonb_set(
        coalesce(r.metadata,'{}'::jsonb),
        '{profileImage}',
        to_jsonb(v_url),
        true
      ),
      updated_at=now()
  from public.users u
  where r.user_id=u.id
    and u.auth_user_id=v_uid
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  returning r.id into v_profile;

  if v_profile is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  return jsonb_build_object('url',v_url);
end $$;

revoke all on function qg_private.rider_set_profile_photo(text) from public, anon;
grant execute on function qg_private.rider_set_profile_photo(text) to authenticated;

create or replace function public.qg_rider_set_profile_photo(p_path text)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.rider_set_profile_photo(p_path); $$;

revoke all on function public.qg_rider_set_profile_photo(text) from public, anon;
grant execute on function public.qg_rider_set_profile_photo(text) to authenticated, service_role;
