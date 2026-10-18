-- Route Bundle Phase 2 authoritative financial/readiness snapshot.
-- Read-only evidence only: no assignment, repricing, cap or Production activation.
create or replace function public.queuego_route_bundle_stack_financial_input(
 p_primary_order_id uuid,
 p_candidate_order_id uuid
) returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
 v_plan jsonb;
 v_ids uuid[];
 v_financial_orders jsonb;
 v_at timestamptz:=now();
 v_max_delay numeric;
begin
 if auth.role()<>'service_role' then raise exception 'service role required'; end if;

 -- Reuse the exact-revision, full-active-set, committed-next and bundle-revision
 -- fence. Calling it inside this statement keeps the financial read on the same
 -- PostgreSQL snapshot instead of trusting caller-supplied money.
 v_plan:=public.queuego_route_bundle_stack_plan_input(
   p_primary_order_id,
   p_candidate_order_id
 );
 if v_plan is null then return null; end if;

 select array_agg(x.value::uuid order by x.ordinality)
 into v_ids
 from jsonb_array_elements_text(v_plan->'orderIds') with ordinality x(value,ordinality);
 if v_ids is null or cardinality(v_ids) not between 2 and 5 then return null; end if;

 select jsonb_object_agg(
   o.id::text,
   jsonb_build_object(
     'status',o.status,
     'isCandidate',o.id=p_candidate_order_id,
     'currentFeeSatang',round(greatest(coalesce(o.delivery_fee,0),0)*100)::bigint,
     'lockedRiderPayoutSatang',
       case when o.id=p_candidate_order_id then null
            else round(greatest(coalesce(o.rider_delivery_payout,o.delivery_fee,0),0)*100)::bigint end,
     'merchantReadyEstimateAt',o.merchant_ready_estimate_at,
     'shopCategory',lower(coalesce(s.public_category,''))
   )
 )
 into v_financial_orders
 from public.orders o
 left join public.shop_profiles s on s.id=o.shop_id
 where o.id=any(v_ids);

 if v_financial_orders is null or jsonb_object_length(v_financial_orders)<>cardinality(v_ids) then
   return null;
 end if;

 v_max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,v_at);
 if exists(
   select 1 from public.orders o
   join public.shop_profiles s on s.id=o.shop_id
   where o.id=any(v_ids) and lower(coalesce(s.public_category,'')) in ('food','cafe')
 ) then
   v_max_delay:=least(v_max_delay,5);
 end if;

 return v_plan || jsonb_build_object(
   'financialPolicyVersion','stack-savings-group-weighted-v1',
   'financialEvaluatedAt',v_at,
   'financialOrders',v_financial_orders,
   'financialRules',jsonb_build_object(
     'existingGroupSavingsPercent',public.queuego_rule_numeric('route_bundle.primary_savings_percent',50,v_at),
     'incrementalKmRateSatangPerKm',round(public.queuego_rule_numeric('route_bundle.incremental_km_rate',5,v_at)*100)::bigint,
     'pickupCostSatang',round(public.queuego_rule_numeric('route_bundle.pickup_cost',2,v_at)*100)::bigint,
     'dropoffCostSatang',round(public.queuego_rule_numeric('route_bundle.dropoff_cost',2,v_at)*100)::bigint,
     'minuteRateSatangPerMinute',round(public.queuego_rule_numeric('route_bundle.minute_rate',1,v_at)*100)::bigint,
     'maxDetourMeters',round(public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,v_at)*1000)::bigint,
     'maxDelaySeconds',round(v_max_delay*60)::bigint
   )
 );
end $$;

revoke all on function public.queuego_route_bundle_stack_financial_input(uuid,uuid)
from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_stack_financial_input(uuid,uuid)
to service_role;

-- Safety: this function is STABLE/read-only and service-role-only. It does not
-- replace queuego_claim_route_bundle, mutate prices/payouts, or raise max_orders.
