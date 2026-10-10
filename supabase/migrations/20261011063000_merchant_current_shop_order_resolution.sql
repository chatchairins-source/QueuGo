-- Keep legacy Merchant order reads aligned with the current non-archived shop profile.
-- The store lifecycle allows an owner to archive a shop and later create one fresh profile.
-- Preserve the existing get_my_shop_orders() response body; patch only profile resolution.

begin;

create table if not exists queuego_private.merchant_current_shop_rpc_backup_20261011(
  signature text primary key,
  definition text not null,
  acl text,
  captured_at timestamptz not null default now()
);
revoke all on queuego_private.merchant_current_shop_rpc_backup_20261011 from public, anon, authenticated;
alter table queuego_private.merchant_current_shop_rpc_backup_20261011 enable row level security;

insert into queuego_private.merchant_current_shop_rpc_backup_20261011(signature,definition,acl)
select p.oid::regprocedure::text, pg_get_functiondef(p.oid), p.proacl::text
from pg_proc p
join pg_namespace n on n.oid=p.pronamespace
where n.nspname='public' and p.proname='get_my_shop_orders' and p.pronargs=0
on conflict(signature) do nothing;

do $migration$
declare
  v_before text;
  v_after text;
begin
  select pg_get_functiondef('public.get_my_shop_orders()'::regprocedure) into v_before;

  if v_before is null then
    raise exception 'GET_MY_SHOP_ORDERS_NOT_FOUND';
  end if;

  if position('and sp.archived_at is null' in v_before) > 0
     and position('order by sp.created_at desc' in v_before) > 0 then
    return;
  end if;

  if position('and u.role = ''shop''' in v_before) = 0
     or position('order by sp.created_at asc' in v_before) = 0 then
    raise exception 'GET_MY_SHOP_ORDERS_UNEXPECTED_DEFINITION';
  end if;

  v_after := replace(
    v_before,
    'and u.role = ''shop''',
    'and u.role = ''shop'' and sp.archived_at is null'
  );
  v_after := replace(
    v_after,
    'order by sp.created_at asc',
    'order by sp.created_at desc'
  );

  if v_after = v_before then
    raise exception 'GET_MY_SHOP_ORDERS_PATCH_NOT_APPLIED';
  end if;

  execute v_after;
end
$migration$;

do $verify$
declare
  v_definition text;
begin
  select pg_get_functiondef('public.get_my_shop_orders()'::regprocedure) into v_definition;
  if position('and sp.archived_at is null' in v_definition) = 0
     or position('order by sp.created_at desc' in v_definition) = 0 then
    raise exception 'GET_MY_SHOP_ORDERS_CURRENT_PROFILE_GUARD_MISSING';
  end if;
end
$verify$;

commit;
