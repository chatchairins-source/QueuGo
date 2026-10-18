-- Route Bundle Phase 2 read-only planner input.
-- Reads only fresh exact-revision stack evidence; no assignment, repricing or feature enablement.
create or replace function public.queuego_route_bundle_stack_plan_input(
 p_primary_order_id uuid,
 p_candidate_order_id uuid
) returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
 v_cache qg_private.route_bundle_stack_matrices%rowtype;
 v_order uuid;
 v_revision timestamptz;
 v_orders jsonb;
 v_count integer;
begin
 if auth.role()<>'service_role' then raise exception 'service role required'; end if;
 if p_primary_order_id is null or p_candidate_order_id is null or p_primary_order_id=p_candidate_order_id then return null; end if;

 select * into v_cache
 from qg_private.route_bundle_stack_matrices
 where primary_order_id=p_primary_order_id
   and candidate_order_id=p_candidate_order_id
   and expires_at>now()
 limit 1;
 if v_cache.primary_order_id is null or cardinality(v_cache.order_ids) not between 2 and 5 then return null; end if;

 select count(*) into v_count from public.orders where id=any(v_cache.order_ids);
 if v_count<>cardinality(v_cache.order_ids) then return null; end if;

 foreach v_order in array v_cache.order_ids loop
  if (v_cache.order_revisions->>v_order::text) is null then return null; end if;
  v_revision:=(v_cache.order_revisions->>v_order::text)::timestamptz;
  if v_order=v_cache.candidate_order_id then
   if not exists(
    select 1 from public.orders o
    where o.id=v_order and o.status='searching_rider' and o.rider_id is null
      and o.market_order_id is null and o.updated_at is not distinct from v_revision
   ) then return null; end if;
  else
   if not exists(
    select 1 from public.orders o
    where o.id=v_order and o.rider_id=v_cache.rider_id
      and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
      and o.updated_at is not distinct from v_revision
   ) then return null; end if;
  end if;
 end loop;

 select jsonb_object_agg(
   o.id::text,
   jsonb_build_object(
    'status',o.status,
    'routeBundleSequence',o.route_bundle_sequence,
    'createdAt',o.created_at
   )
  )
 into v_orders
 from public.orders o
 where o.id=any(v_cache.order_ids);

 return jsonb_build_object(
  'primaryOrderId',v_cache.primary_order_id,
  'candidateOrderId',v_cache.candidate_order_id,
  'orderIds',to_jsonb(v_cache.order_ids),
  'pointMap',v_cache.point_map,
  'edges',v_cache.edges,
  'orders',v_orders,
  'expiresAt',v_cache.expires_at
 );
end $$;

revoke all on function public.queuego_route_bundle_stack_plan_input(uuid,uuid) from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_stack_plan_input(uuid,uuid) to service_role;

-- Safety: this migration is read-only with respect to Orders, bundles, prices and dispatch.
-- It does not replace queuego_claim_route_bundle and does not change route_bundle.max_orders or feature flags.
