-- Consolidate QueueGo runtime controls onto queuego_platform_rules.
-- Removes only duplicate objects created by the immediately preceding foundation migration.
-- No customer/order/business data is removed.

create or replace function public.queuego_platform_rule_guard()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp') then
    if jsonb_typeof(new.value) <> 'boolean' then
      raise exception 'feature rule must be boolean';
    end if;
  elsif new.rule_key in (
    'pricing.gp_default_rate',
    'pricing.market_base_fee',
    'pricing.market_distance_step_fee',
    'pricing.market_second_shop_fee',
    'pricing.market_additional_shop_fee',
    'route_bundle.max_detour_km',
    'route_bundle.max_delay_minutes',
    'route_bundle.max_orders',
    'route_bundle.min_rider_extra_fee'
  ) then
    if jsonb_typeof(new.value) <> 'number' then
      raise exception 'pricing rule must be numeric';
    end if;
    n := (new.value #>> '{}')::numeric;
    if n < 0 then raise exception 'pricing rule cannot be negative'; end if;
    if new.rule_key='pricing.gp_default_rate' and n > 100 then
      raise exception 'GP rate must be 0-100';
    end if;
    if new.rule_key='route_bundle.max_orders' and (n < 1 or n > 5 or n <> trunc(n)) then
      raise exception 'route bundle max orders must be integer 1-5';
    end if;
    if new.rule_key='route_bundle.max_delay_minutes' and n > 240 then
      raise exception 'route bundle delay too large';
    end if;
    if new.rule_key='route_bundle.max_detour_km' and n > 50 then
      raise exception 'route bundle detour too large';
    end if;
  else
    raise exception 'unsupported QueueGo platform rule: %', new.rule_key;
  end if;
  new.created_at := coalesce(new.created_at,now());
  return new;
end
$$;

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'route_bundle.min_rider_extra_fee','10'::jsonb,'1970-01-01 00:00:00+00',
       'Minimum additional Rider compensation for a route bundle',null
where not exists(
  select 1 from public.queuego_platform_rules where rule_key='route_bundle.min_rider_extra_fee'
);

drop function if exists public.queuego_feature_enabled(text);
drop function if exists public.queuego_current_pricing(text,timestamptz);
drop function if exists public.queuego_market_delivery_fee(numeric);
drop table if exists public.platform_pricing_rules;

drop policy if exists system_settings_public_platform_features_read on public.system_settings;
delete from public.system_settings where key='platform_features';
