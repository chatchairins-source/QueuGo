create schema if not exists queuego_private;
revoke all on schema queuego_private from public, anon, authenticated;

create table if not exists queuego_private.store_status_cascade_backup_20261008 (
  kind text primary key,
  snapshot jsonb not null,
  captured_at timestamptz not null default now()
);
revoke all on table queuego_private.store_status_cascade_backup_20261008 from public, anon, authenticated;
alter table queuego_private.store_status_cascade_backup_20261008 enable row level security;

insert into queuego_private.store_status_cascade_backup_20261008(kind,snapshot)
values
(
  'users_status_cascade',
  jsonb_build_object('definition',pg_get_functiondef('public.users_status_cascade()'::regprocedure))
),
(
  'queuego_shop_readiness_guard',
  jsonb_build_object('definition',pg_get_functiondef('public.queuego_shop_readiness_guard()'::regprocedure))
),
(
  'shop_profile_states',
  (select coalesce(jsonb_agg(jsonb_build_object(
    'id',id,
    'user_id',user_id,
    'status',status,
    'onboarding_status',onboarding_status,
    'archived_at',archived_at
  ) order by created_at),'[]'::jsonb) from public.shop_profiles)
)
on conflict(kind) do nothing;
