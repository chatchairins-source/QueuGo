-- QueueGo Native release certification evidence bucket.
-- Production was created additively on 2026-10-10. Keep this bucket private and
-- server-side only; do not add anon/authenticated storage.objects policies for it.
insert into storage.buckets (id, name, public, allowed_mime_types)
select
  'queuego-native-release-evidence',
  'queuego-native-release-evidence',
  false,
  array['application/zip','application/x-zip-compressed']::text[]
where not exists (
  select 1
  from storage.buckets
  where id = 'queuego-native-release-evidence'
);

do $$
declare
  v_public boolean;
begin
  select public into v_public
  from storage.buckets
  where id = 'queuego-native-release-evidence';

  if v_public is distinct from false then
    raise exception 'queuego-native-release-evidence must remain private';
  end if;
end
$$;
