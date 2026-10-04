-- QueueGo platform controls + Market/Laundry foundation
-- Production-applied additive migration. No DROP statements.
-- Feature defaults preserve existing GP behavior while keeping new beta features off.

insert into public.system_settings(key,value,updated_at)
values (
  'platform_features',
  jsonb_build_object(
    'market_multi_shop_enabled', false,
    'route_bundle_enabled', false,
    'gp_enabled', true
  ),
  now()
)
on conflict (key) do nothing;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname='public'
      and tablename='system_settings'
      and policyname='system_settings_public_platform_features_read'
  ) then
    create policy system_settings_public_platform_features_read
      on public.system_settings
      for select
      to anon
      using (key='platform_features');
  end if;
end $$;

create table if not exists public.platform_pricing_rules (
  id uuid primary key default gen_random_uuid(),
  rule_type text not null check (rule_type in ('market','route_bundle','gp')),
  effective_from timestamptz not null,
  config jsonb not null default '{}'::jsonb,
  active boolean not null default true,
  created_by uuid default public.get_my_user_id(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(rule_type,effective_from)
);

alter table public.platform_pricing_rules enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='platform_pricing_rules'
      and policyname='platform_pricing_rules_public_current_read'
  ) then
    create policy platform_pricing_rules_public_current_read
      on public.platform_pricing_rules
      for select
      to anon, authenticated
      using (active=true and effective_from<=now());
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='platform_pricing_rules'
      and policyname='platform_pricing_rules_admin_future_read'
  ) then
    create policy platform_pricing_rules_admin_future_read
      on public.platform_pricing_rules
      for select
      to authenticated
      using (public.get_my_role()='admin');
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='platform_pricing_rules'
      and policyname='platform_pricing_rules_admin_insert'
  ) then
    create policy platform_pricing_rules_admin_insert
      on public.platform_pricing_rules
      for insert
      to authenticated
      with check (public.get_my_role()='admin');
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='platform_pricing_rules'
      and policyname='platform_pricing_rules_admin_update'
  ) then
    create policy platform_pricing_rules_admin_update
      on public.platform_pricing_rules
      for update
      to authenticated
      using (public.get_my_role()='admin')
      with check (public.get_my_role()='admin');
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='platform_pricing_rules'
      and policyname='platform_pricing_rules_admin_delete'
  ) then
    create policy platform_pricing_rules_admin_delete
      on public.platform_pricing_rules
      for delete
      to authenticated
      using (public.get_my_role()='admin');
  end if;
end $$;

revoke all on table public.platform_pricing_rules from anon, authenticated;
grant select on table public.platform_pricing_rules to anon;
grant select,insert,update,delete on table public.platform_pricing_rules to authenticated;
grant select,insert,update,delete on table public.platform_pricing_rules to service_role;

insert into public.platform_pricing_rules(rule_type,effective_from,config,active,created_by)
select 'market','2000-01-01 00:00:00+07'::timestamptz,
       jsonb_build_object(
         'base_fee',30,
         'base_distance_km',5,
         'extra_distance_per_km',10,
         'second_shop_fee',10,
         'additional_shop_fee',5
       ),
       true,null
where not exists(select 1 from public.platform_pricing_rules where rule_type='market');

insert into public.platform_pricing_rules(rule_type,effective_from,config,active,created_by)
select 'gp','2000-01-01 00:00:00+07'::timestamptz,
       jsonb_build_object(
         'default_rate',
         coalesce((select (value->>'default_rate')::numeric from public.system_settings where key='gp'),10)
       ),
       true,null
where not exists(select 1 from public.platform_pricing_rules where rule_type='gp');

create or replace function public.queuego_feature_enabled(p_feature text)
returns boolean
language sql
stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select coalesce(
    (select (value->>p_feature)::boolean
       from public.system_settings
      where key='platform_features'),
    false
  );
$$;

revoke all on function public.queuego_feature_enabled(text) from public;
grant execute on function public.queuego_feature_enabled(text) to anon,authenticated,service_role;

create or replace function public.queuego_current_pricing(p_rule_type text, p_at timestamptz default now())
returns jsonb
language sql
stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select r.config
    from public.platform_pricing_rules r
   where r.rule_type=p_rule_type
     and r.active=true
     and r.effective_from<=p_at
   order by r.effective_from desc,r.created_at desc
   limit 1;
$$;

revoke all on function public.queuego_current_pricing(text,timestamptz) from public;
grant execute on function public.queuego_current_pricing(text,timestamptz) to anon,authenticated,service_role;

create or replace function public.queuego_market_multi_shop_fee(p_shop_count integer)
returns numeric
language plpgsql
stable
security invoker
set search_path to 'public','pg_temp'
as $$
declare
  v_cfg jsonb;
  v_second numeric;
  v_more numeric;
begin
  if coalesce(p_shop_count,0)<=1 then return 0; end if;
  v_cfg:=coalesce(public.queuego_current_pricing('market',now()),'{}'::jsonb);
  v_second:=coalesce((v_cfg->>'second_shop_fee')::numeric,10);
  v_more:=coalesce((v_cfg->>'additional_shop_fee')::numeric,5);
  return greatest(v_second,0) + greatest(p_shop_count-2,0)*greatest(v_more,0);
end
$$;

revoke all on function public.queuego_market_multi_shop_fee(integer) from public;
grant execute on function public.queuego_market_multi_shop_fee(integer) to anon,authenticated,service_role;

create or replace function public.queuego_market_delivery_fee(p_distance_km numeric)
returns numeric
language plpgsql
stable
security invoker
set search_path to 'public','pg_temp'
as $$
declare
  v_cfg jsonb;
  v_base numeric;
  v_base_km numeric;
  v_extra numeric;
begin
  if p_distance_km is null or p_distance_km<0 then
    raise exception 'invalid market delivery distance';
  end if;
  v_cfg:=coalesce(public.queuego_current_pricing('market',now()),'{}'::jsonb);
  v_base:=greatest(coalesce((v_cfg->>'base_fee')::numeric,30),0);
  v_base_km:=greatest(coalesce((v_cfg->>'base_distance_km')::numeric,5),0);
  v_extra:=greatest(coalesce((v_cfg->>'extra_distance_per_km')::numeric,10),0);
  return v_base + greatest(ceil(p_distance_km-v_base_km),0)*v_extra;
end
$$;

revoke all on function public.queuego_market_delivery_fee(numeric) from public;
grant execute on function public.queuego_market_delivery_fee(numeric) to anon,authenticated,service_role;

create or replace function public.effective_gp_rate_at(
  p_shop_profile_id uuid,
  p_at timestamptz default now()
)
returns numeric
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_enabled boolean;
  v_day date;
  v_rate numeric;
begin
  select coalesce((value->>'gp_enabled')::boolean,true)
    into v_enabled
    from public.system_settings
   where key='platform_features';
  if coalesce(v_enabled,true)=false then return 0; end if;

  v_day:=(p_at at time zone 'Asia/Bangkok')::date;

  select r.rate_percent
    into v_rate
    from public.shop_gp_rates r
    join public.shop_profiles sp on sp.user_id=r.shop_user_id
   where sp.id=p_shop_profile_id
     and (r.promo_until is null or r.promo_until>=v_day)
   limit 1;
  if v_rate is not null then return greatest(least(v_rate,100),0); end if;

  select (config->>'default_rate')::numeric
    into v_rate
    from public.platform_pricing_rules
   where rule_type='gp' and active=true and effective_from<=p_at
   order by effective_from desc,created_at desc
   limit 1;
  if v_rate is not null then return greatest(least(v_rate,100),0); end if;

  select (value->>'default_rate')::numeric
    into v_rate
    from public.system_settings
   where key='gp';
  return greatest(least(coalesce(v_rate,10),100),0);
end
$$;

revoke all on function public.effective_gp_rate_at(uuid,timestamptz) from public,anon,authenticated;
grant execute on function public.effective_gp_rate_at(uuid,timestamptz) to service_role;

create or replace function public.orders_set_gp()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
begin
  if new.order_type in ('DINE_IN','TAKEAWAY') then
    new.gp_rate:=0;
    new.gp_amount:=0;
  elsif tg_op='INSERT' then
    new.gp_rate:=public.effective_gp_rate_at(new.shop_id,now());
    new.gp_amount:=round(coalesce(new.subtotal,0)*new.gp_rate/100,2);
  else
    new.gp_rate:=old.gp_rate;
    if new.subtotal is distinct from old.subtotal then
      new.gp_amount:=round(coalesce(new.subtotal,0)*coalesce(old.gp_rate,10)/100,2);
    else
      new.gp_amount:=old.gp_amount;
    end if;
  end if;
  return new;
end
$$;

revoke all on function public.orders_set_gp() from public,anon,authenticated;
grant execute on function public.orders_set_gp() to service_role;

alter table public.shop_profiles
  add column if not exists market_stall_no text,
  add column if not exists market_zone text,
  add column if not exists market_membership_confirmed boolean not null default false,
  add column if not exists market_membership_confirmed_at timestamptz;

alter table public.laundry_services
  add column if not exists description text,
  add column if not exists estimated_minutes integer;

alter table public.laundry_shop_settings
  add column if not exists return_fee numeric not null default 0,
  add column if not exists round_trip_fee numeric not null default 0,
  add column if not exists delivery_fee_mode text not null default 'separate';

alter table public.laundry_orders
  add column if not exists service_id uuid references public.laundry_services(id) on delete set null,
  add column if not exists service_name_snapshot text,
  add column if not exists pricing_type_snapshot text,
  add column if not exists unit_price_snapshot numeric,
  add column if not exists pickup_fee_snapshot numeric,
  add column if not exists return_fee_snapshot numeric,
  add column if not exists round_trip_fee_snapshot numeric,
  add column if not exists request_key uuid;

create unique index if not exists laundry_orders_request_key_uidx
  on public.laundry_orders(request_key)
  where request_key is not null;

insert into public.markets(
  name,address,latitude,longitude,active,province,district,subdistrict,
  source,verified,assignment_radius_km,created_at,updated_at
)
select
  'ตลาดสดสวายจีก',
  'ต.สวายจีก อ.เมืองบุรีรัมย์ จ.บุรีรัมย์ 31000',
  14.9058,
  103.14682,
  true,
  'บุรีรัมย์',
  'เมืองบุรีรัมย์',
  'สวายจีก',
  'QueueGo launch seed; Sawai Chik locality coordinate pending exact market-pin verification',
  false,
  2,
  now(),
  now()
where not exists(
  select 1 from public.markets
   where name='ตลาดสดสวายจีก'
     and province='บุรีรัมย์'
     and subdistrict='สวายจีก'
);
