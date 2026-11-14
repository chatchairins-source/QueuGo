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
 v_bundle public.route_bundles%rowtype;
 v_live_plan jsonb;
 v_prefix jsonb:='[]'::jsonb;
 v_next jsonb;
 v_next_key text;
 v_done boolean;
 k record;
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
      and o.route_bundle_id is not distinct from v_cache.bundle_id
      and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
      and o.updated_at is not distinct from v_revision
   ) then return null; end if;
  end if;
 end loop;

 -- Per-order revisions do not detect another job newly assigned to this Rider.
 -- Together with the loop above, this makes the cached noncandidate set equal
 -- to the complete authoritative active set in this read-only statement snapshot.
 if exists(
  select 1 from public.orders o
  where o.rider_id=v_cache.rider_id
    and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
    and not (o.id=any(v_cache.order_ids))
 ) then return null; end if;

 if v_cache.bundle_id is not null then
  select * into v_bundle from public.route_bundles rb
  where rb.id=v_cache.bundle_id and rb.rider_id=v_cache.rider_id and rb.status='ACTIVE'
    and rb.route_revision=v_cache.bundle_revision;
  if not found then return null; end if;

  -- Same cancellation filtering and completed-prefix/next-stop semantics as
  -- qg_private.validate_bundle_stop_plan. Keep raw stop objects for exact identity.
  select coalesce(jsonb_agg(p.s order by p.n),'[]'::jsonb) into v_live_plan
  from jsonb_array_elements(v_bundle.stop_plan) with ordinality p(s,n)
  join public.orders o on o.id=(p.s->>'order_id')::uuid
  where o.status<>'cancelled';
  for k in select p.s from jsonb_array_elements(v_live_plan) with ordinality p(s,n) order by p.n loop
   select case when k.s->>'kind'='pickup' then o.status in ('picked_up','in_progress','completed')
               else o.status='completed' end into v_done
   from public.orders o where o.id=(k.s->>'order_id')::uuid;
   if not coalesce(v_done,false) then v_next:=k.s; exit; end if;
   v_prefix:=v_prefix||jsonb_build_array(k.s);
  end loop;
  if v_next is null then return null; end if;
  select e.key into v_next_key from jsonb_each(v_cache.point_map) e
  where e.value->>'orderId'=v_next->>'order_id' and e.value->>'kind'=v_next->>'kind';
  if v_next_key is null then return null; end if;
 end if;

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
  'bundleId',v_cache.bundle_id,
  'routeRevision',v_cache.bundle_revision,
  'committedPrefix',v_prefix,
  'lockedNext',v_next_key,
  'lockedNextStop',v_next,
  'primaryOrderId',v_cache.primary_order_id,
  'candidateOrderId',v_cache.candidate_order_id,
  'riderId',v_cache.rider_id,
  'orderRevisions',v_cache.order_revisions,
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
