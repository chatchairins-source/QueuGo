-- Route Bundle Phase 2 bounded discovery for active 2-4 Order bundles.
-- Reuses the existing route-matrix lease and Edge worker. No assignment or cap enablement.

create or replace function public.queuego_route_bundle_stack_refresh_claim(p_limit integer default 2)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
 v_lease uuid;
 v_pairs jsonb;
 v_max integer;
begin
 if auth.role()<>'service_role' then raise exception 'service role required'; end if;
 if p_limit is null or p_limit not between 1 and 2 then raise exception 'refresh limit must be 1-2'; end if;
 if public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())<>1
    or not public.queuego_feature_enabled('route_bundle',now())
 then return '{}'::jsonb; end if;

 v_max:=greatest(1,least(5,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
 if v_max<3 then return '{}'::jsonb; end if;

 perform 1 from qg_private.route_matrix_refresh_lease
 where singleton and expires_at<=now()
 for update skip locked;
 if not found then return '{}'::jsonb; end if;

 select coalesce(jsonb_agg(jsonb_build_object(
   'action','stack',
   'primaryOrderId',x.primary_id,
   'candidateOrderId',x.candidate_id
 ) order by x.created_at,x.candidate_id),'[]'::jsonb)
 into v_pairs
 from (
  select rb.primary_order_id primary_id,b.id candidate_id,b.created_at
  from public.route_bundles rb
  join public.rider_profiles r on r.id=rb.rider_id
  join public.users u on u.id=r.user_id
  join qg_private.rider_dispatch_preferences pref on pref.rider_id=r.id
  join public.orders p on p.id=rb.primary_order_id
  cross join public.orders b
  where rb.status='ACTIVE'
    and u.role='rider' and u.status='active' and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)
    and coalesce((r.metadata->>'available')::boolean,false)
    and not pref.stop_after_work and pref.ready_until>now()
    and p.rider_id=r.id and p.route_bundle_id=rb.id
    and p.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
    and (
      select count(*) from public.orders o
      where o.rider_id=r.id and o.route_bundle_id=rb.id
        and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
    ) between 2 and least(4,v_max-1)
    and not exists(
      select 1 from public.orders o
      where o.rider_id=r.id
        and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
        and o.route_bundle_id is distinct from rb.id
    )
    and b.id<>rb.primary_order_id
    and b.order_type='shopping'
    and b.fulfillment_vertical in ('food','grocery')
    and b.market_order_id is null
    and b.route_bundle_id is null
    and b.next_after_order_id is null
    and b.rider_id is null and b.status='searching_rider'
    and b.pickup_latitude is not null and b.pickup_longitude is not null
    and b.delivery_latitude is not null and b.delivery_longitude is not null
    and not exists(select 1 from public.qg_rider_order_offers f where f.order_id=b.id and f.expires_at>now())
    and not exists(select 1 from public.qg_rider_order_offers f where f.rider_id=r.id and f.expires_at>now())
    and not exists(select 1 from public.laundry_rider_jobs l where l.rider_id=r.id and l.status in ('assigned','accepted','arrived','collected'))
    and not exists(select 1 from public.qg_rider_delivery_returns d where d.rider_id=r.id and d.status not in ('resolved','returned','cancelled'))
    and not exists(
      select 1 from qg_private.route_bundle_stack_matrices m
      where m.primary_order_id=rb.primary_order_id and m.candidate_order_id=b.id
        and m.rider_id=r.id and m.bundle_id=rb.id and m.bundle_revision=rb.route_revision
        and m.expires_at>now()+interval '30 seconds'
    )
    and qg_private.approx_distance_km(
      r.latitude::double precision,r.longitude::double precision,
      b.pickup_latitude::double precision,b.pickup_longitude::double precision
    )<=5
  order by b.created_at,b.id,rb.updated_at,rb.id
  limit p_limit
 ) x;

 if jsonb_array_length(v_pairs)=0 then return '{}'::jsonb; end if;
 v_lease:=gen_random_uuid();
 update qg_private.route_matrix_refresh_lease
 set lease_id=v_lease,expires_at=now()+interval '90 seconds'
 where singleton;
 return jsonb_build_object('leaseId',v_lease,'pairs',v_pairs);
end $$;

revoke all on function public.queuego_route_bundle_stack_refresh_claim(integer) from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_stack_refresh_claim(integer) to service_role;

-- Release continues to use the existing exact-lease queuego_route_matrix_refresh_release(uuid).
-- Current max_orders fallback=2 makes this function return no work by default.
