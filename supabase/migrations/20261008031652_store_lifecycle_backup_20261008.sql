create table if not exists queuego_private.store_lifecycle_backup_20261008 (kind text primary key, snapshot jsonb not null, captured_at timestamptz not null default now());
revoke all on queuego_private.store_lifecycle_backup_20261008 from public, anon, authenticated;
alter table queuego_private.store_lifecycle_backup_20261008 enable row level security;
insert into queuego_private.store_lifecycle_backup_20261008(kind,snapshot) values
('shop_profiles',(select coalesce(jsonb_agg(to_jsonb(s)),'[]') from public.shop_profiles s)),
('shop_open_states',(select coalesce(jsonb_agg(to_jsonb(s)),'[]') from public.shop_open_states s)),
('functions',(select jsonb_agg(jsonb_build_object('signature',p.oid::regprocedure::text,'definition',pg_get_functiondef(p.oid),'acl',p.proacl)) from pg_proc p where p.pronamespace='public'::regnamespace and p.prokind='f')),
('policies',(select jsonb_agg(to_jsonb(p)) from pg_policies p where schemaname='public')),
('counts',jsonb_build_object('orders',(select count(*) from public.orders),'products',(select count(*) from public.products)))
on conflict(kind) do nothing;
