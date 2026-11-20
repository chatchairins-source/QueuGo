-- Applied to Production using the server-issued migration version above.
-- Preserve existing publication membership, grants, RLS and dispatch logic.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'laundry_rider_preferences'
  ) then
    alter publication supabase_realtime add table public.laundry_rider_preferences;
  end if;
end $$;
