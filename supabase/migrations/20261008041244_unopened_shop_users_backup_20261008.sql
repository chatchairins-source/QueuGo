create schema if not exists queuego_private;
revoke all on schema queuego_private from public, anon, authenticated;

create table if not exists queuego_private.unopened_shop_users_backup_20261008 (
  user_id uuid primary key,
  status text,
  auth_user_id uuid,
  metadata jsonb,
  captured_at timestamptz not null default now()
);
revoke all on table queuego_private.unopened_shop_users_backup_20261008 from public, anon, authenticated;
alter table queuego_private.unopened_shop_users_backup_20261008 enable row level security;

insert into queuego_private.unopened_shop_users_backup_20261008(user_id,status,auth_user_id,metadata)
select u.id,u.status,u.auth_user_id,u.metadata
from public.users u
where u.role='shop'
  and not exists(select 1 from public.shop_profiles sp where sp.user_id=u.id)
on conflict(user_id) do nothing;
