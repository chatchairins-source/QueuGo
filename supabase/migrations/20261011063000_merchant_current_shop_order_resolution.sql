-- Keep Merchant order/Laundry RPCs aligned with the current non-archived shop profile.
-- The store lifecycle allows an owner to archive a shop and later create one fresh profile.
-- Preserve existing RPC response bodies; patch only current-profile/hub resolution.

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
where n.nspname='public'
  and p.proname in (
    'get_my_shop_orders',
    'queuego_laundry_merchant_state',
    'queuego_laundry_save_service',
    'queuego_laundry_save_settings'
  )
on conflict(signature) do nothing;

do $orders$
declare
  v_before text;
  v_after text;
begin
  select pg_get_functiondef('public.get_my_shop_orders()'::regprocedure) into v_before;
  if v_before is null then raise exception 'GET_MY_SHOP_ORDERS_NOT_FOUND'; end if;

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
  if v_after = v_before then raise exception 'GET_MY_SHOP_ORDERS_PATCH_NOT_APPLIED'; end if;
  execute v_after;
end
$orders$;

do $laundry_state$
declare
  v_before text;
  v_after text;
  v_old text := 'select id into v_shop from public.shop_profiles where user_id=v_user limit 1;';
  v_new text := 'select id into v_shop from public.shop_profiles where user_id=v_user and archived_at is null order by created_at desc limit 1;';
begin
  select pg_get_functiondef('public.queuego_laundry_merchant_state()'::regprocedure) into v_before;
  if v_before is null then raise exception 'LAUNDRY_MERCHANT_STATE_NOT_FOUND'; end if;

  if position(v_new in v_before) > 0 then return; end if;
  if position(v_old in v_before) = 0 then
    raise exception 'LAUNDRY_MERCHANT_STATE_UNEXPECTED_DEFINITION';
  end if;

  v_after := replace(v_before,v_old,v_new);
  if v_after = v_before then raise exception 'LAUNDRY_MERCHANT_STATE_PATCH_NOT_APPLIED'; end if;
  execute v_after;
end
$laundry_state$;

do $laundry_writes$
declare
  v_signature text;
  v_before text;
  v_after text;
begin
  foreach v_signature in array array[
    'public.queuego_laundry_save_service(uuid,text,text,text,numeric,integer,boolean)',
    'public.queuego_laundry_save_settings(boolean,numeric,numeric,numeric,numeric,text)'
  ]
  loop
    select pg_get_functiondef(v_signature::regprocedure) into v_before;
    if v_before is null then raise exception 'LAUNDRY_WRITE_RPC_NOT_FOUND: %',v_signature; end if;

    if position('where s.user_id=v_user and s.archived_at is null' in v_before) > 0
       and position('order by h.created_at desc' in v_before) > 0 then
      continue;
    end if;

    if position('where s.user_id=v_user' in v_before) = 0
       or position('order by h.created_at' in v_before) = 0
       or position('order by h.created_at desc' in v_before) > 0 then
      raise exception 'LAUNDRY_WRITE_RPC_UNEXPECTED_DEFINITION: %',v_signature;
    end if;

    v_after := replace(
      v_before,
      'where s.user_id=v_user',
      'where s.user_id=v_user and s.archived_at is null'
    );
    v_after := replace(
      v_after,
      'order by h.created_at',
      'order by h.created_at desc'
    );
    if v_after = v_before then raise exception 'LAUNDRY_WRITE_RPC_PATCH_NOT_APPLIED: %',v_signature; end if;
    execute v_after;
  end loop;
end
$laundry_writes$;

do $verify$
declare
  v_orders text;
  v_state text;
  v_service text;
  v_settings text;
begin
  select pg_get_functiondef('public.get_my_shop_orders()'::regprocedure) into v_orders;
  select pg_get_functiondef('public.queuego_laundry_merchant_state()'::regprocedure) into v_state;
  select pg_get_functiondef('public.queuego_laundry_save_service(uuid,text,text,text,numeric,integer,boolean)'::regprocedure) into v_service;
  select pg_get_functiondef('public.queuego_laundry_save_settings(boolean,numeric,numeric,numeric,numeric,text)'::regprocedure) into v_settings;

  if position('and sp.archived_at is null' in v_orders) = 0
     or position('order by sp.created_at desc' in v_orders) = 0 then
    raise exception 'GET_MY_SHOP_ORDERS_CURRENT_PROFILE_GUARD_MISSING';
  end if;
  if position('where user_id=v_user and archived_at is null order by created_at desc limit 1;' in v_state) = 0 then
    raise exception 'LAUNDRY_MERCHANT_STATE_CURRENT_PROFILE_GUARD_MISSING';
  end if;
  if position('where s.user_id=v_user and s.archived_at is null' in v_service) = 0
     or position('order by h.created_at desc' in v_service) = 0 then
    raise exception 'LAUNDRY_SAVE_SERVICE_CURRENT_PROFILE_GUARD_MISSING';
  end if;
  if position('where s.user_id=v_user and s.archived_at is null' in v_settings) = 0
     or position('order by h.created_at desc' in v_settings) = 0 then
    raise exception 'LAUNDRY_SAVE_SETTINGS_CURRENT_PROFILE_GUARD_MISSING';
  end if;
end
$verify$;

commit;
