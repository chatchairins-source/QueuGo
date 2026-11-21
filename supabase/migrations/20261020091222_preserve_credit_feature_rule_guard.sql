-- Forward reconciliation: Ride tariff guard replaced earlier credit and continuous-dispatch keys.
-- Validation only: no feature activation, balances, pricing values, grants or business rows change.
-- Retain the current Ride bounds and restore the earlier validated keys.
create or replace function public.queuego_platform_rule_guard()
returns trigger language plpgsql set search_path=public,pg_temp as $$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry','feature.ride','feature.credit_rider','feature.credit_merchant','feature.credit_merchant_promo') then
    if jsonb_typeof(new.value)<>'boolean' then raise exception 'feature rule must be boolean'; end if;
  elsif new.rule_key in (
    'dispatch.continuous_enabled','dispatch.max_next_link_km',
    'ride.base_fare','ride.included_km','ride.per_km','ride.minimum_fare',
    'ride.long_distance_threshold_km','ride.long_distance_per_km','ride.platform_fee_percent',
    'pricing.gp_default_rate','pricing.market_base_fee','pricing.market_distance_step_fee',
    'pricing.market_second_shop_fee','pricing.market_additional_shop_fee',
    'route_bundle.max_detour_km','route_bundle.max_delay_minutes','route_bundle.max_orders',
    'route_bundle.min_rider_extra_fee','route_bundle.primary_savings_percent',
    'route_bundle.incremental_km_rate','route_bundle.pickup_cost','route_bundle.dropoff_cost','route_bundle.minute_rate'
  ) then
    if jsonb_typeof(new.value)<>'number' then raise exception 'pricing rule must be numeric'; end if;
    n:=(new.value#>>'{}')::numeric;
    if n<0 then raise exception 'pricing rule cannot be negative'; end if;
    if new.rule_key='dispatch.continuous_enabled' and n not in (0,1) then raise exception 'continuous enabled must be 0 or 1'; end if;
    if new.rule_key='dispatch.max_next_link_km' and n>1.5 then raise exception 'next link cap is 1.5 km'; end if;
    if new.rule_key='ride.platform_fee_percent' and n>=100 then raise exception 'ride platform fee must be less than fare'; end if;
    if new.rule_key='pricing.gp_default_rate' and n>100 then raise exception 'GP rate must be 0-100'; end if;
    if new.rule_key='route_bundle.primary_savings_percent' and (n<1 or n>99) then raise exception 'route bundle primary savings percent must be 1-99'; end if;
    if new.rule_key='route_bundle.max_orders' and (n<1 or n>5 or n<>trunc(n)) then raise exception 'route bundle max orders must be integer 1-5'; end if;
    if new.rule_key='route_bundle.max_delay_minutes' and n>240 then raise exception 'route bundle delay too large'; end if;
    if new.rule_key='route_bundle.max_detour_km' and n>50 then raise exception 'route bundle detour too large'; end if;
  else
    raise exception 'unsupported QueueGo platform rule: %',new.rule_key;
  end if;
  new.created_at:=coalesce(new.created_at,now());
  return new;
end $$;
