create schema if not exists queuego_private;

create table if not exists queuego_private.home_banner_backup_20261008_1146 as
select key, value, updated_at
from public.system_settings
where key = 'home_service_banner';

alter table queuego_private.home_banner_backup_20261008_1146 enable row level security;
revoke all on queuego_private.home_banner_backup_20261008_1146 from public, anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'queuego-banners',
  'queuego-banners',
  true,
  5242880,
  array['image/jpeg','image/png','image/webp']::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "queuego_banners_public_read" on storage.objects;
create policy "queuego_banners_public_read"
on storage.objects
for select
to public
using (bucket_id = 'queuego-banners');

drop policy if exists "queuego_banners_admin_insert" on storage.objects;
create policy "queuego_banners_admin_insert"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'queuego-banners'
  and exists (
    select 1
    from public.users u
    where u.auth_user_id = auth.uid()
      and u.role = 'admin'
      and u.status = 'active'
  )
);

drop policy if exists "queuego_banners_admin_update" on storage.objects;
create policy "queuego_banners_admin_update"
on storage.objects
for update
to authenticated
using (
  bucket_id = 'queuego-banners'
  and exists (
    select 1
    from public.users u
    where u.auth_user_id = auth.uid()
      and u.role = 'admin'
      and u.status = 'active'
  )
)
with check (
  bucket_id = 'queuego-banners'
  and exists (
    select 1
    from public.users u
    where u.auth_user_id = auth.uid()
      and u.role = 'admin'
      and u.status = 'active'
  )
);

drop policy if exists "queuego_banners_admin_delete" on storage.objects;
create policy "queuego_banners_admin_delete"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'queuego-banners'
  and exists (
    select 1
    from public.users u
    where u.auth_user_id = auth.uid()
      and u.role = 'admin'
      and u.status = 'active'
  )
);

do $$
declare
  v jsonb;
begin
  select value into v
  from public.system_settings
  where key = 'home_service_banner';

  if v is null then
    insert into public.system_settings(key, value, updated_at)
    values (
      'home_service_banner',
      jsonb_build_object(
        'display','home',
        'version','2',
        'rotation_ms',5000,
        'slides',jsonb_build_array(
          jsonb_build_object('slot',1,'active',false),
          jsonb_build_object('slot',2,'active',false),
          jsonb_build_object('slot',3,'active',false)
        )
      ),
      now()
    )
    on conflict (key) do nothing;
  elsif jsonb_typeof(v) = 'object' and not (v ? 'slides') then
    update public.system_settings
    set value = v || jsonb_build_object(
      'version','2',
      'rotation_ms',5000,
      'default_image_data',v->>'image_data',
      'slides',jsonb_build_array(
        jsonb_strip_nulls(jsonb_build_object(
          'slot',1,
          'image_data',v->>'image_data',
          'alt',coalesce(v->>'alt','QueueGo'),
          'active',true
        )),
        jsonb_build_object('slot',2,'active',false),
        jsonb_build_object('slot',3,'active',false)
      )
    ),
    updated_at = now()
    where key = 'home_service_banner';
  end if;
end
$$;
