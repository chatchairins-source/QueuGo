create schema if not exists queuego_private;
revoke all on schema queuego_private from public,anon,authenticated;

create table if not exists queuego_private.empty_shop_account_backup_20261008 (
  user_id uuid primary key,
  snapshot jsonb not null,
  captured_at timestamptz not null default now()
);
revoke all on table queuego_private.empty_shop_account_backup_20261008 from public,anon,authenticated;
alter table queuego_private.empty_shop_account_backup_20261008 enable row level security;

insert into queuego_private.empty_shop_account_backup_20261008(user_id,snapshot)
select u.id,to_jsonb(u)
from public.users u
where u.role='shop'
  and not exists(select 1 from public.shop_profiles sp where sp.user_id=u.id)
on conflict(user_id) do nothing;
