-- Restore the existing Admin control path for the already-deployed Passenger Ride policy.
-- This migration defines no default price and does not change any existing rule or feature flag.
create or replace function public.queuego_admin_configure_ride(
 p_enabled boolean,
 p_pricing jsonb
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
 v_admin uuid;
 v_key text;
 v_value numeric;
 v_at timestamptz:=now();
begin
 perform qg_private.assert_live_session();
 select id into v_admin
 from public.users
 where auth_user_id=auth.uid() and role='admin' and status='active'
 for share;
 if v_admin is null or p_enabled is null then
  raise exception 'active admin and explicit Ride availability required' using errcode='42501';
 end if;
 if p_pricing is null or jsonb_typeof(p_pricing)<>'object'
    or (select count(*) from jsonb_object_keys(p_pricing))<>7 then
  raise exception 'complete explicit Ride pricing required';
 end if;

 foreach v_key in array array[
  'base_fare','included_km','per_km','minimum_fare',
  'long_distance_threshold_km','long_distance_per_km','platform_fee_percent'
 ] loop
  if jsonb_typeof(p_pricing->v_key) is distinct from 'number' then
   raise exception 'numeric Ride pricing required: %',v_key;
  end if;
  v_value:=(p_pricing->>v_key)::numeric;
  if v_value<0 or v_value>100000
     or (v_key in ('base_fare','minimum_fare') and v_value<=0)
     or (v_key='platform_fee_percent' and v_value>=100) then
   raise exception 'invalid Ride pricing: %',v_key;
  end if;
 end loop;
 if (p_pricing->>'long_distance_threshold_km')::numeric<=(p_pricing->>'included_km')::numeric then
  raise exception 'long-distance threshold must exceed included distance';
 end if;
 if p_enabled and (p_pricing->>'platform_fee_percent')::numeric>0
    and to_regprocedure('qg_private.rider_credit_order_authorized(uuid,uuid,numeric)') is null then
  raise exception 'existing Rider credit integration is required before enabling charged rides';
 end if;

 lock table public.queuego_platform_rules in share row exclusive mode;
 foreach v_key in array array[
  'base_fare','included_km','per_km','minimum_fare',
  'long_distance_threshold_km','long_distance_per_km','platform_fee_percent'
 ] loop
  insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
  values('ride.'||v_key,p_pricing->v_key,v_at,'Explicit Admin passenger policy',v_admin);
 end loop;
 insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
 values('feature.ride',to_jsonb(p_enabled),v_at,'Admin passenger availability',v_admin);

 insert into public.audit_logs(user_id,action,entity_type,description,metadata)
 values(
  v_admin,'ride_policy_update','system_settings','Passenger Ride pricing and availability',
  jsonb_build_object('enabled',p_enabled,'pricing',p_pricing,'effective_from',v_at)
 );
 return public.queuego_ride_config();
end
$$;

revoke all on function public.queuego_admin_configure_ride(boolean,jsonb) from public,anon,service_role;
grant execute on function public.queuego_admin_configure_ride(boolean,jsonb) to authenticated;
