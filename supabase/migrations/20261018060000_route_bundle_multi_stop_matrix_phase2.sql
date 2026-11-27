-- Route Bundle Phase 2 groundwork: cache full multi-stop road evidence only.
-- Live claim/cap/feature flags are intentionally unchanged.
create table if not exists qg_private.route_bundle_stack_matrices(
 primary_order_id uuid not null references public.orders(id) on delete cascade,
 candidate_order_id uuid not null references public.orders(id) on delete cascade,
 rider_id uuid not null references public.rider_profiles(id) on delete cascade,
 bundle_id uuid,
 bundle_revision bigint,
 order_ids uuid[] not null,
 order_revisions jsonb not null check(jsonb_typeof(order_revisions)='object'),
 point_map jsonb not null check(jsonb_typeof(point_map)='object'),
 rider_latitude double precision not null check(rider_latitude between -90 and 90),
 rider_longitude double precision not null check(rider_longitude between -180 and 180),
 edges jsonb not null check(jsonb_typeof(edges)='object'),
 expires_at timestamptz not null,
 created_at timestamptz not null default now(),
 primary key(primary_order_id,candidate_order_id)
);
alter table qg_private.route_bundle_stack_matrices enable row level security;
revoke all on qg_private.route_bundle_stack_matrices from public,anon,authenticated;
create index if not exists route_bundle_stack_matrices_expiry_idx on qg_private.route_bundle_stack_matrices(expires_at);

create or replace function public.queuego_cache_route_bundle_stack_matrix(
 p_primary_order_id uuid,
 p_candidate_order_id uuid,
 p_order_ids uuid[],
 p_order_revisions jsonb,
 p_point_map jsonb,
 p_rider_latitude double precision,
 p_rider_longitude double precision,
 p_edges jsonb
) returns boolean
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
 v_rider uuid;
 v_bundle_id uuid;
 v_bundle_revision bigint;
 v_count integer;
 v_expected_points integer;
 v_edge_count integer;
 v_order uuid;
begin
 if auth.role()<>'service_role' then raise exception 'service role required'; end if;
 if p_primary_order_id is null or p_candidate_order_id is null or p_primary_order_id=p_candidate_order_id then return false; end if;
 if p_order_ids is null or cardinality(p_order_ids) not between 2 and 5 then return false; end if;
 if (select count(distinct x) from unnest(p_order_ids) x)<>cardinality(p_order_ids) then return false; end if;
 if not p_primary_order_id=any(p_order_ids) or not p_candidate_order_id=any(p_order_ids) then return false; end if;
 if p_rider_latitude is null or p_rider_longitude is null or p_rider_latitude not between -90 and 90 or p_rider_longitude not between -180 and 180 then return false; end if;
 if jsonb_typeof(p_order_revisions)<>'object' or jsonb_typeof(p_point_map)<>'object' or jsonb_typeof(p_edges)<>'object' then return false; end if;

 select o.rider_id,o.route_bundle_id into v_rider,v_bundle_id from public.orders o where o.id=p_primary_order_id;
 if v_bundle_id is not null then
  select rb.route_revision into v_bundle_revision from public.route_bundles rb
  where rb.id=v_bundle_id and rb.rider_id=v_rider and rb.status='ACTIVE';
  if not found then return false; end if;
 end if;
 if v_rider is null then return false; end if;
 if not exists(select 1 from public.orders o where o.id=p_candidate_order_id and o.status='searching_rider' and o.rider_id is null and o.market_order_id is null) then return false; end if;

 foreach v_order in array p_order_ids loop
  if v_order=p_candidate_order_id then continue; end if;
  if not exists(
   select 1 from public.orders o
   where o.id=v_order and o.rider_id=v_rider
    and o.route_bundle_id is not distinct from v_bundle_id
    and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
    and o.updated_at is not null
  ) then return false; end if;
  if (p_order_revisions->>v_order::text)::timestamptz is distinct from (select o.updated_at from public.orders o where o.id=v_order) then return false; end if;
 end loop;
 if (p_order_revisions->>p_candidate_order_id::text)::timestamptz is distinct from (select o.updated_at from public.orders o where o.id=p_candidate_order_id) then return false; end if;

 v_expected_points:=1+2*cardinality(p_order_ids);
 if (select count(*) from jsonb_object_keys(p_point_map))<>v_expected_points then return false; end if;
 if p_point_map->'R' is distinct from jsonb_build_object('kind','rider') then return false; end if;
 if exists(
  select 1 from jsonb_each(p_point_map) e
  where e.key<>'R' and (
   coalesce(e.value->>'kind','') not in ('pickup','dropoff')
   or coalesce(e.value->>'orderId','')!~'^[0-9a-fA-F-]{36}$'
   or not ((e.value->>'orderId')::uuid=any(p_order_ids))
  )
 ) then return false; end if;
 if exists(
  select 1 from unnest(p_order_ids) oid
  where (select count(*) from jsonb_each(p_point_map) e where e.value->>'orderId'=oid::text and e.value->>'kind'='pickup')<>1
     or (select count(*) from jsonb_each(p_point_map) e where e.value->>'orderId'=oid::text and e.value->>'kind'='dropoff')<>1
 ) then return false; end if;

 select count(*) into v_edge_count from jsonb_object_keys(p_edges);
 if v_edge_count<>v_expected_points*(v_expected_points-1) then return false; end if;
 if exists(
  select 1 from jsonb_each(p_edges) e
  where jsonb_typeof(e.value)<>'object'
    or (e.value->>'km') is null or (e.value->>'minutes') is null
    or (e.value->>'km')::numeric<0 or (e.value->>'minutes')::numeric<0
 ) then return false; end if;

 delete from qg_private.route_bundle_stack_matrices where expires_at<now()-interval '1 day';
 insert into qg_private.route_bundle_stack_matrices(
  primary_order_id,candidate_order_id,rider_id,bundle_id,bundle_revision,order_ids,order_revisions,point_map,
  rider_latitude,rider_longitude,edges,expires_at
 ) values(
  p_primary_order_id,p_candidate_order_id,v_rider,v_bundle_id,v_bundle_revision,p_order_ids,p_order_revisions,p_point_map,
  p_rider_latitude,p_rider_longitude,p_edges,now()+interval '90 seconds'
 )
 on conflict(primary_order_id,candidate_order_id) do update set
  rider_id=excluded.rider_id,bundle_id=excluded.bundle_id,bundle_revision=excluded.bundle_revision,
  order_ids=excluded.order_ids,order_revisions=excluded.order_revisions,
  point_map=excluded.point_map,rider_latitude=excluded.rider_latitude,rider_longitude=excluded.rider_longitude,
  edges=excluded.edges,expires_at=excluded.expires_at,created_at=now();
 return true;
end $$;
revoke all on function public.queuego_cache_route_bundle_stack_matrix(uuid,uuid,uuid[],jsonb,jsonb,double precision,double precision,jsonb) from public,anon,authenticated;
grant execute on function public.queuego_cache_route_bundle_stack_matrix(uuid,uuid,uuid[],jsonb,jsonb,double precision,double precision,jsonb) to service_role;

-- Explicit safety: this migration does NOT replace queuego_claim_route_bundle,
-- does NOT change feature.route_bundle, dispatch.continuous_enabled or route_bundle.max_orders.
