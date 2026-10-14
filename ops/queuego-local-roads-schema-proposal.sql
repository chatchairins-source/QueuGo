-- QueueGo Local Roads schema proposal
-- PROPOSAL ONLY. Safe to inspect/run because this file ends with ROLLBACK.
-- When approved for Production, generate the real migration with the Supabase CLI
-- and remove the surrounding test transaction.
--
-- Deliberately no PostGIS dependency: Production currently has no PostGIS extension.
-- points is an ordered JSONB array of [longitude, latitude] pairs. Full coordinate
-- validation remains part of the reviewed publication pipeline before Production apply.

begin;

create table if not exists public.qg_local_road_candidates (
  id uuid primary key default gen_random_uuid(),
  road_key text not null unique,
  service_area_id uuid not null references public.queuego_service_areas(id) on delete restrict,
  status text not null default 'candidate',
  source_type text not null,
  source_reference text not null,
  points jsonb not null,
  access_type text not null default 'motorcycle_and_car',
  one_way boolean not null default false,
  surface text not null default 'unknown',
  name text,
  reviewed_at timestamptz,
  reviewed_by uuid references public.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint qg_local_road_candidates_key_format
    check (road_key ~ '^[a-z0-9][a-z0-9:_-]{2,119}$'),
  constraint qg_local_road_candidates_status
    check (status in ('candidate','approved','rejected')),
  constraint qg_local_road_candidates_source
    check (source_type in (
      'openstreetmap_verified',
      'public_data',
      'rider_gps_trace_verified',
      'merchant_confirmed',
      'field_survey'
    )),
  constraint qg_local_road_candidates_source_reference
    check (
      (source_type = 'openstreetmap_verified' and source_reference like 'osm:%') or
      (source_type = 'public_data' and source_reference like 'public:%') or
      (source_type = 'rider_gps_trace_verified' and source_reference like 'queuego-trace:%') or
      (source_type = 'merchant_confirmed' and source_reference like 'merchant:%') or
      (source_type = 'field_survey' and source_reference like 'field-survey:%')
    ),
  constraint qg_local_road_candidates_points
    check (
      jsonb_typeof(points) = 'array'
      and jsonb_array_length(points) between 2 and 10000
    ),
  constraint qg_local_road_candidates_access
    check (access_type in (
      'motorcycle_and_car',
      'motorcycle_only',
      'service_access',
      'private_restricted'
    )),
  constraint qg_local_road_candidates_surface
    check (surface in ('paved','unpaved','unknown')),
  constraint qg_local_road_candidates_name
    check (name is null or char_length(name) <= 160),
  constraint qg_local_road_candidates_review_state
    check (
      (status = 'candidate' and reviewed_at is null and reviewed_by is null) or
      (status in ('approved','rejected') and reviewed_at is not null and reviewed_by is not null)
    )
);

create table if not exists public.qg_local_roads (
  id uuid primary key default gen_random_uuid(),
  road_key text not null unique,
  service_area_id uuid not null references public.queuego_service_areas(id) on delete restrict,
  feed_revision bigint not null,
  source_type text not null,
  source_reference text not null,
  points jsonb not null,
  access_type text not null,
  one_way boolean not null default false,
  surface text not null default 'unknown',
  name text,
  active boolean not null default true,
  verified_at timestamptz not null,
  verified_by uuid references public.users(id) on delete set null,
  published_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint qg_local_roads_key_format
    check (road_key ~ '^[a-z0-9][a-z0-9:_-]{2,119}$'),
  constraint qg_local_roads_revision
    check (feed_revision > 0),
  constraint qg_local_roads_source
    check (source_type in (
      'openstreetmap_verified',
      'public_data',
      'rider_gps_trace_verified',
      'merchant_confirmed',
      'field_survey'
    )),
  constraint qg_local_roads_source_reference
    check (
      (source_type = 'openstreetmap_verified' and source_reference like 'osm:%') or
      (source_type = 'public_data' and source_reference like 'public:%') or
      (source_type = 'rider_gps_trace_verified' and source_reference like 'queuego-trace:%') or
      (source_type = 'merchant_confirmed' and source_reference like 'merchant:%') or
      (source_type = 'field_survey' and source_reference like 'field-survey:%')
    ),
  constraint qg_local_roads_points
    check (
      jsonb_typeof(points) = 'array'
      and jsonb_array_length(points) between 2 and 10000
    ),
  constraint qg_local_roads_access
    check (access_type in (
      'motorcycle_and_car',
      'motorcycle_only',
      'service_access',
      'private_restricted'
    )),
  constraint qg_local_roads_surface
    check (surface in ('paved','unpaved','unknown')),
  constraint qg_local_roads_name
    check (name is null or char_length(name) <= 160)
);

create table if not exists public.qg_local_road_feed_state (
  singleton boolean primary key default true,
  revision bigint not null default 1,
  published_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint qg_local_road_feed_state_singleton check (singleton),
  constraint qg_local_road_feed_state_revision check (revision > 0)
);

insert into public.qg_local_road_feed_state(singleton, revision)
values (true, 1)
on conflict (singleton) do nothing;

create index if not exists qg_local_road_candidates_area_status_idx
  on public.qg_local_road_candidates(service_area_id, status, updated_at desc);

create index if not exists qg_local_roads_area_active_idx
  on public.qg_local_roads(service_area_id, active, feed_revision desc);

create index if not exists qg_local_roads_feed_revision_idx
  on public.qg_local_roads(feed_revision);

alter table public.qg_local_road_candidates enable row level security;
alter table public.qg_local_roads enable row level security;
alter table public.qg_local_road_feed_state enable row level security;

revoke all on table public.qg_local_road_candidates from anon, authenticated;
revoke all on table public.qg_local_roads from anon, authenticated;
revoke all on table public.qg_local_road_feed_state from anon, authenticated;

grant select, insert, update, delete
  on table public.qg_local_road_candidates to authenticated;
grant select on table public.qg_local_roads to authenticated;
grant select on table public.qg_local_road_feed_state to authenticated;

grant all on table public.qg_local_road_candidates to service_role;
grant all on table public.qg_local_roads to service_role;
grant all on table public.qg_local_road_feed_state to service_role;

drop policy if exists qg_local_road_candidates_admin_select
  on public.qg_local_road_candidates;
create policy qg_local_road_candidates_admin_select
  on public.qg_local_road_candidates
  for select
  to authenticated
  using ((select public.is_active_admin()));

drop policy if exists qg_local_road_candidates_admin_insert
  on public.qg_local_road_candidates;
create policy qg_local_road_candidates_admin_insert
  on public.qg_local_road_candidates
  for insert
  to authenticated
  with check ((select public.is_active_admin()));

drop policy if exists qg_local_road_candidates_admin_update
  on public.qg_local_road_candidates;
create policy qg_local_road_candidates_admin_update
  on public.qg_local_road_candidates
  for update
  to authenticated
  using ((select public.is_active_admin()))
  with check ((select public.is_active_admin()));

drop policy if exists qg_local_road_candidates_admin_delete
  on public.qg_local_road_candidates;
create policy qg_local_road_candidates_admin_delete
  on public.qg_local_road_candidates
  for delete
  to authenticated
  using ((select public.is_active_admin()));

drop policy if exists qg_local_roads_verified_read
  on public.qg_local_roads;
create policy qg_local_roads_verified_read
  on public.qg_local_roads
  for select
  to authenticated
  using (active);

drop policy if exists qg_local_roads_admin_read
  on public.qg_local_roads;
create policy qg_local_roads_admin_read
  on public.qg_local_roads
  for select
  to authenticated
  using ((select public.is_active_admin()));

drop policy if exists qg_local_road_feed_state_read
  on public.qg_local_road_feed_state;
create policy qg_local_road_feed_state_read
  on public.qg_local_road_feed_state
  for select
  to authenticated
  using (true);

create or replace view public.qg_local_road_feed
with (security_invoker = true)
as
select
  state.revision as feed_revision,
  state.published_at as feed_published_at,
  roads.road_key,
  roads.service_area_id,
  roads.source_type,
  roads.source_reference,
  roads.points,
  roads.access_type,
  roads.one_way,
  roads.surface,
  roads.name,
  roads.verified_at
from public.qg_local_roads roads
cross join public.qg_local_road_feed_state state
where roads.active;

revoke all on table public.qg_local_road_feed from anon, authenticated;
grant select on table public.qg_local_road_feed to authenticated;

-- No authenticated INSERT/UPDATE/DELETE grant exists on published roads or feed state.
-- A later server-only publication transaction will:
-- 1) validate approved candidate geometry and provenance,
-- 2) increment feed_state.revision,
-- 3) upsert/retire published roads with that same revision,
-- 4) commit all changes atomically.
--
-- This proposal creates no SECURITY DEFINER function and stores no raw Rider GPS trails.

rollback;
