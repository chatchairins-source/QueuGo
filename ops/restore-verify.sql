\set ON_ERROR_STOP on
do $$
declare
  missing text[];
begin
  select array_agg(x.name) into missing
  from (values
    ('public.users'),('public.shop_profiles'),('public.rider_profiles'),
    ('public.orders'),('public.order_items'),('public.deliveries'),
    ('public.notifications'),('public.products'),('public.markets'),
    ('public.qg_push_subscriptions'),('public.qg_native_push_tokens')
  ) as x(name)
  where to_regclass(x.name) is null;
  if missing is not null then
    raise exception 'Restore missing required tables: %',missing;
  end if;

  if to_regprocedure('public.queuego_place_cash_order(uuid,uuid,jsonb,double precision,double precision,text,numeric,numeric,text)') is null then
    raise exception 'Restore missing cash checkout RPC';
  end if;
  if to_regprocedure('public.qg_complete_with_proof(uuid,text)') is null then
    raise exception 'Restore missing completion proof RPC';
  end if;
  if to_regprocedure('public.qg_enqueue_push()') is null then
    raise exception 'Restore missing push enqueue trigger function';
  end if;

  if not exists(
    select 1 from pg_trigger
    where tgrelid='public.notifications'::regclass
      and tgname='qg_notifications_queuego_push'
      and not tgisinternal
  ) then
    raise exception 'Restore missing unified notification push trigger';
  end if;

  if exists(
    select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public'
      and c.relname in ('users','orders','notifications','qg_push_subscriptions','qg_native_push_tokens')
      and not c.relrowsecurity
  ) then
    raise exception 'Restore lost RLS on a required table';
  end if;

  if exists(
    select 1 from public.orders o
    left join public.users u on u.id=o.customer_id
    where o.customer_id is not null and u.id is null
  ) then
    raise exception 'Restore created orphan customer orders';
  end if;
end $$;

select 'queuego_restore_verification_ok' as result;
