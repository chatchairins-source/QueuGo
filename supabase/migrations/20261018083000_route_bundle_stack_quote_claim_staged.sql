-- Route Bundle Phase 2 staged quote + atomic generalized claim.
-- Live behavior remains capped by route_bundle.max_orders (default 2).
-- No feature/rule enablement is performed here.

create table if not exists qg_private.route_bundle_stack_quotes(
 id uuid primary key default gen_random_uuid(),
 primary_order_id uuid not null references public.orders(id) on delete cascade,
 candidate_order_id uuid not null references public.orders(id) on delete cascade,
 rider_id uuid not null references public.rider_profiles(id) on delete cascade,
 bundle_id uuid not null references public.route_bundles(id) on delete cascade,
 bundle_revision bigint not null check(bundle_revision>=0),
 order_ids uuid[] not null check(cardinality(order_ids) between 3 and 5),
 order_revisions jsonb not null check(jsonb_typeof(order_revisions)='object'),
 financial_orders jsonb not null check(jsonb_typeof(financial_orders)='object'),
 financial_rules jsonb not null check(jsonb_typeof(financial_rules)='object'),
 stop_plan jsonb not null check(jsonb_typeof(stop_plan)='array'),
 financial jsonb not null check(jsonb_typeof(financial)='object'),
 expires_at timestamptz not null,
 claimed_at timestamptz,
 created_at timestamptz not null default now(),
 unique(primary_order_id,candidate_order_id)
);
alter table qg_private.route_bundle_stack_quotes enable row level security;
revoke all on qg_private.route_bundle_stack_quotes from public,anon,authenticated;
create index if not exists route_bundle_stack_quotes_rider_idx on qg_private.route_bundle_stack_quotes(rider_id,expires_at);
create index if not exists route_bundle_stack_quotes_candidate_idx on qg_private.route_bundle_stack_quotes(candidate_order_id,expires_at);

create or replace function qg_private.route_bundle_stack_financial_payload_valid(
 p_snapshot jsonb,
 p_financial jsonb
) returns boolean
language plpgsql
immutable
set search_path=public,pg_temp
as $$
declare
 v_ids uuid[];
 v_id uuid;
 v_candidate uuid;
 v_row jsonb;
 v_alloc jsonb;
 v_current bigint;
 v_locked bigint;
 v_proposed bigint;
 v_saving bigint;
 v_payout bigint;
 v_collections bigint:=0;
 v_payouts bigint:=0;
 v_savings bigint:=0;
 v_increment bigint;
 v_expected_increment bigint;
 v_wait record;
begin
 if p_snapshot is null or p_financial is null
    or p_snapshot->>'financialPolicyVersion'<>'stack-savings-group-weighted-v1'
    or p_financial->>'policyVersion'<>'stack-savings-group-weighted-v1'
    or not coalesce((p_financial->>'eligible')::boolean,false)
    or coalesce((p_financial->>'fundingGapSatang')::bigint,-1)<>0
    or jsonb_typeof(p_financial->'allocations')<>'array'
    or jsonb_typeof(p_financial->'route')<>'object'
 then return false; end if;

 select array_agg(x.value::uuid order by x.ordinality)
 into v_ids
 from jsonb_array_elements_text(p_snapshot->'orderIds') with ordinality x(value,ordinality);
 v_candidate:=(p_snapshot->>'candidateOrderId')::uuid;
 if v_ids is null or cardinality(v_ids) not between 3 and 5
    or jsonb_array_length(p_financial->'allocations')<>cardinality(v_ids)
 then return false; end if;

 if coalesce((p_financial->'route'->>'detourMeters')::bigint,-1)<0
    or (p_financial->'route'->>'detourMeters')::bigint>(p_snapshot->'financialRules'->>'maxDetourMeters')::bigint
    or coalesce((p_financial->'route'->>'observedDelaySeconds')::bigint,-1)<0
    or (p_financial->'route'->>'observedDelaySeconds')::bigint>(p_snapshot->'financialRules'->>'maxDelaySeconds')::bigint
 then return false; end if;

 if jsonb_typeof(p_financial->'route'->'pickupWaitSeconds')<>'object' then return false; end if;
 for v_wait in select key,value from jsonb_each_text(p_financial->'route'->'pickupWaitSeconds') loop
  if v_wait.value::bigint<0
     or v_wait.value::bigint>(p_snapshot->'financialRules'->>'maxPickupWaitSeconds')::bigint
  then return false; end if;
 end loop;

 v_increment:=(p_financial->'route'->>'incrementalCompensationSatang')::bigint;
 if v_increment<0
    or coalesce((p_financial->'route'->>'addedMinutes')::bigint,-1)<0
 then return false; end if;

 v_expected_increment:=
   ceil(
     (p_financial->'route'->>'detourMeters')::numeric
     * (p_snapshot->'financialRules'->>'incrementalKmRateSatangPerKm')::numeric
     / 1000
   )::bigint
   + (p_snapshot->'financialRules'->>'pickupCostSatang')::bigint
   + (p_snapshot->'financialRules'->>'dropoffCostSatang')::bigint
   + (p_financial->'route'->>'addedMinutes')::bigint
     * (p_snapshot->'financialRules'->>'minuteRateSatangPerMinute')::bigint;
 if v_increment<>v_expected_increment then return false; end if;

 foreach v_id in array v_ids loop
  v_row:=p_snapshot->'financialOrders'->v_id::text;
  select a into v_alloc
  from jsonb_array_elements(p_financial->'allocations') a
  where a->>'orderId'=v_id::text;
  if v_row is null or v_alloc is null
     or (select count(*) from jsonb_array_elements(p_financial->'allocations') a where a->>'orderId'=v_id::text)<>1
  then return false; end if;

  v_current:=(v_alloc->>'currentFeeSatang')::bigint;
  v_proposed:=(v_alloc->>'proposedFeeSatang')::bigint;
  v_saving:=(v_alloc->>'customerSavingSatang')::bigint;
  v_payout:=(v_alloc->>'riderPayoutSatang')::bigint;
  if v_current<>(v_row->>'currentFeeSatang')::bigint
     or v_proposed<0 or v_proposed>v_current
     or v_saving<=0 or v_saving<>v_current-v_proposed
  then return false; end if;

  if v_id=v_candidate then
   if v_payout<>v_increment then return false; end if;
  else
   v_locked:=(v_row->>'lockedRiderPayoutSatang')::bigint;
   if v_payout<>v_locked then return false; end if;
  end if;

  v_collections:=v_collections+v_proposed;
  v_payouts:=v_payouts+v_payout;
  v_savings:=v_savings+v_saving;
 end loop;

 return v_collections>=v_payouts
    and v_collections=coalesce((p_financial->>'proposedCollectionsSatang')::bigint,-1)
    and v_payouts=coalesce((p_financial->>'riderPayoutTotalSatang')::bigint,-1)
    and v_savings=coalesce((p_financial->>'totalCustomerSavingSatang')::bigint,-1);
exception when others then
 return false;
end $$;
revoke all on function qg_private.route_bundle_stack_financial_payload_valid(jsonb,jsonb) from public,anon,authenticated;

create or replace function public.queuego_cache_route_bundle_stack_quote(
 p_primary_order_id uuid,
 p_candidate_order_id uuid,
 p_stop_plan jsonb,
 p_financial jsonb
) returns uuid
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
 v_snapshot jsonb;
 v_quote uuid;
 v_ids uuid[];
 v_prefix jsonb;
 v_item record;
 v_id uuid;
 v_exp timestamptz;
 v_expected_plan jsonb;
 v_key text;
 v_point jsonb;
begin
 if auth.role()<>'service_role' then raise exception 'service role required'; end if;
 if p_primary_order_id is null or p_candidate_order_id is null or p_primary_order_id=p_candidate_order_id
    or jsonb_typeof(p_stop_plan)<>'array' then return null; end if;

 v_snapshot:=public.queuego_route_bundle_stack_financial_input(p_primary_order_id,p_candidate_order_id);
 if v_snapshot is null or v_snapshot->>'bundleId' is null or v_snapshot->>'riderId' is null
    or not qg_private.route_bundle_stack_financial_payload_valid(v_snapshot,p_financial)
 then return null; end if;

 select array_agg(x.value::uuid order by x.ordinality)
 into v_ids
 from jsonb_array_elements_text(v_snapshot->'orderIds') with ordinality x(value,ordinality);
 if v_ids is null or cardinality(v_ids) not between 3 and 5 then return null; end if;

 v_prefix:=coalesce(v_snapshot->'committedPrefix','[]'::jsonb);
 for v_item in select s,n from jsonb_array_elements(v_prefix) with ordinality p(s,n) loop
  if p_stop_plan->(v_item.n-1) is distinct from v_item.s then return null; end if;
 end loop;

 -- The mutation route must be exactly the route priced by the financial evaluator.
 if jsonb_typeof(p_financial->'route'->'fullKeys')<>'array' then return null; end if;
 v_expected_plan:=v_prefix;
 for v_key in select jsonb_array_elements_text(p_financial->'route'->'fullKeys') loop
  v_point:=v_snapshot->'pointMap'->v_key;
  if v_point is null
     or coalesce(v_point->>'kind','') not in ('pickup','dropoff')
     or coalesce(v_point->>'orderId','')!~'^[0-9a-fA-F-]{36}$'
  then return null; end if;
  v_expected_plan:=v_expected_plan||jsonb_build_array(
    jsonb_build_object('order_id',v_point->>'orderId','kind',v_point->>'kind')
  );
 end loop;
 if p_stop_plan is distinct from v_expected_plan then return null; end if;

 foreach v_id in array v_ids loop
  if (select count(*) from jsonb_array_elements(p_stop_plan) s
      where s->>'order_id'=v_id::text and s->>'kind'='pickup')<>1
     or (select count(*) from jsonb_array_elements(p_stop_plan) s
      where s->>'order_id'=v_id::text and s->>'kind'='dropoff')<>1
  then return null; end if;
 end loop;

 if exists(
  select 1 from jsonb_array_elements(p_stop_plan) s
  where coalesce(s->>'kind','') not in ('pickup','dropoff')
     or coalesce(s->>'order_id','')!~'^[0-9a-fA-F-]{36}$'
 ) or exists(
  select 1 from jsonb_array_elements(p_stop_plan) s
  group by s->>'order_id',s->>'kind' having count(*)>1
 ) then return null; end if;

 v_exp:=least((v_snapshot->>'expiresAt')::timestamptz,now()+interval '30 seconds');
 insert into qg_private.route_bundle_stack_quotes(
  primary_order_id,candidate_order_id,rider_id,bundle_id,bundle_revision,
  order_ids,order_revisions,financial_orders,financial_rules,stop_plan,financial,expires_at,claimed_at
 ) values(
  p_primary_order_id,p_candidate_order_id,(v_snapshot->>'riderId')::uuid,(v_snapshot->>'bundleId')::uuid,
  (v_snapshot->>'routeRevision')::bigint,v_ids,v_snapshot->'orderRevisions',
  v_snapshot->'financialOrders',v_snapshot->'financialRules',p_stop_plan,p_financial,v_exp,null
 )
 on conflict(primary_order_id,candidate_order_id) do update set
  rider_id=excluded.rider_id,bundle_id=excluded.bundle_id,bundle_revision=excluded.bundle_revision,
  order_ids=excluded.order_ids,order_revisions=excluded.order_revisions,
  financial_orders=excluded.financial_orders,financial_rules=excluded.financial_rules,
  stop_plan=excluded.stop_plan,financial=excluded.financial,expires_at=excluded.expires_at,
  claimed_at=null,created_at=now()
 returning id into v_quote;
 return v_quote;
end $$;
revoke all on function public.queuego_cache_route_bundle_stack_quote(uuid,uuid,jsonb,jsonb) from public,anon,authenticated;
grant execute on function public.queuego_cache_route_bundle_stack_quote(uuid,uuid,jsonb,jsonb) to service_role;

create or replace function public.queuego_offer_route_bundle_stack_quote(p_quote_id uuid)
returns boolean
language plpgsql
security definer
set search_path=public,pg_temp
as $
declare
 q qg_private.route_bundle_stack_quotes%rowtype;
 rb public.route_bundles%rowtype;
 r public.rider_profiles%rowtype;
 v_candidate public.orders%rowtype;
 v_existing uuid[];
 v_active uuid[];
 v_count integer;
 v_max integer;
 v_attempt integer;
begin
 if auth.role()<>'service_role' then raise exception 'service role required'; end if;
 select * into q from qg_private.route_bundle_stack_quotes
 where id=p_quote_id and claimed_at is null and expires_at>now()
 for update;
 if not found then return false; end if;

 v_count:=cardinality(q.order_ids);
 v_max:=greatest(1,least(5,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
 if v_count not between 3 and 5 or v_count>v_max then return false; end if;
 if not public.queuego_feature_enabled('route_bundle',now()) then return false; end if;

 select * into r from public.rider_profiles
 where id=q.rider_id and status='active'
   and coalesce((metadata->>'online')::boolean,false)
   and coalesce((metadata->>'available')::boolean,false)
 for update;
 if not found or not exists(
   select 1 from public.users u where u.id=r.user_id and u.role='rider' and u.status='active'
 ) then return false; end if;

 select * into rb from public.route_bundles
 where id=q.bundle_id and rider_id=q.rider_id and status='ACTIVE' and route_revision=q.bundle_revision
 for update;
 if not found then return false; end if;

 select * into v_candidate from public.orders
 where id=q.candidate_order_id and status='searching_rider' and rider_id is null and route_bundle_id is null
 for update;
 if not found
    or v_candidate.updated_at is distinct from (q.order_revisions->>q.candidate_order_id::text)::timestamptz
 then return false; end if;

 v_existing:=array_remove(q.order_ids,q.candidate_order_id);
 select array_agg(o.id order by o.id) into v_active
 from public.orders o
 where o.rider_id=q.rider_id and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
 if coalesce(v_active,'{}'::uuid[]) is distinct from
    (select array_agg(x order by x) from unnest(v_existing) x)
 then return false; end if;

 if exists(select 1 from public.laundry_rider_jobs l where l.rider_id=q.rider_id and l.status in ('assigned','accepted','arrived','collected'))
    or exists(select 1 from public.qg_rider_delivery_returns d where d.rider_id=q.rider_id and d.status not in ('resolved','returned','cancelled'))
 then return false; end if;

 if exists(
   select 1 from public.qg_rider_order_offers f
   where f.order_id=q.candidate_order_id and f.rider_id=q.rider_id and f.expires_at>now()
 ) then return true; end if;

 if exists(select 1 from public.qg_rider_order_offers f where f.order_id=q.candidate_order_id and f.expires_at>now())
    or exists(select 1 from public.qg_rider_order_offers f where f.rider_id=q.rider_id and f.expires_at>now())
 then return false; end if;

 select coalesce(max(h.attempt),0)+1 into v_attempt
 from public.qg_rider_offer_history h where h.order_id=q.candidate_order_id;

 insert into public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt)
 values(q.candidate_order_id,q.rider_id,now(),least(q.expires_at,now()+interval '30 seconds'),v_attempt)
 on conflict(order_id) do nothing;
 if not found then return false; end if;

 insert into public.notifications(user_id,title,message,type,reference_id)
 values(r.user_id,'มีงานพ่วงใหม่','มีงานพ่วงเส้นทางเดียวกัน กรุณาตอบรับภายใน 30 วินาที','order',q.candidate_order_id);
 return true;
end $;
revoke all on function public.queuego_offer_route_bundle_stack_quote(uuid) from public,anon,authenticated;
grant execute on function public.queuego_offer_route_bundle_stack_quote(uuid) to service_role;

create or replace function public.queuego_claim_route_bundle_stack(p_quote_id uuid)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
 v_rider uuid;
 v_user uuid;
 q qg_private.route_bundle_stack_quotes%rowtype;
 rb public.route_bundles%rowtype;
 m qg_private.route_bundle_stack_matrices%rowtype;
 v_order public.orders%rowtype;
 v_id uuid;
 v_rev timestamptz;
 v_alloc jsonb;
 v_existing uuid[];
 v_active uuid[];
 v_count integer;
 v_max integer;
 v_seq integer;
 v_fee numeric;
 v_saving numeric;
 v_payout numeric;
 v_max_delay numeric;
 v_rule numeric;
 v_offer public.qg_rider_order_offers%rowtype;
begin
 select r.id,r.user_id into v_rider,v_user
 from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
   and r.status='active'
   and coalesce((r.metadata->>'online')::boolean,false)
   and coalesce((r.metadata->>'available')::boolean,false)
 for update of r;
 if v_rider is null then raise exception 'active online rider required'; end if;
 if not public.queuego_feature_enabled('route_bundle',now()) then raise exception 'route bundle is disabled'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_rider::text,337));

 select * into q from qg_private.route_bundle_stack_quotes
 where id=p_quote_id and claimed_at is null and expires_at>now()
 for update;
 if not found or q.rider_id<>v_rider then raise exception 'stack quote unavailable'; end if;

 v_count:=cardinality(q.order_ids);
 v_max:=greatest(1,least(5,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
 if v_count not between 3 and 5 or v_count>v_max then raise exception 'route bundle order limit reached'; end if;

 select * into v_offer from public.qg_rider_order_offers
 where order_id=q.candidate_order_id and rider_id=v_rider and expires_at>now()
 for update;
 if not found then raise exception 'live targeted offer required'; end if;

 select * into rb from public.route_bundles
 where id=q.bundle_id and rider_id=v_rider and status='ACTIVE' and route_revision=q.bundle_revision
 for update;
 if not found then raise exception 'bundle revision changed'; end if;

 perform 1 from public.orders where id=any(q.order_ids) order by id for update;
 if (select count(*) from public.orders where id=any(q.order_ids))<>v_count then raise exception 'stack membership changed'; end if;

 select * into m from qg_private.route_bundle_stack_matrices
 where primary_order_id=q.primary_order_id and candidate_order_id=q.candidate_order_id
   and rider_id=v_rider and bundle_id is not distinct from q.bundle_id
   and bundle_revision is not distinct from q.bundle_revision
   and order_ids=q.order_ids and order_revisions=q.order_revisions and expires_at>now()
 limit 1;
 if not found then raise exception 'stack road evidence expired'; end if;

 v_rule:=public.queuego_rule_numeric('route_bundle.primary_savings_percent',50,now());
 if (q.financial_rules->>'existingGroupSavingsPercent')::numeric is distinct from v_rule then raise exception 'stack financial rules changed'; end if;
 v_rule:=round(public.queuego_rule_numeric('route_bundle.incremental_km_rate',5,now())*100);
 if (q.financial_rules->>'incrementalKmRateSatangPerKm')::numeric is distinct from v_rule then raise exception 'stack financial rules changed'; end if;
 v_rule:=round(public.queuego_rule_numeric('route_bundle.pickup_cost',2,now())*100);
 if (q.financial_rules->>'pickupCostSatang')::numeric is distinct from v_rule then raise exception 'stack financial rules changed'; end if;
 v_rule:=round(public.queuego_rule_numeric('route_bundle.dropoff_cost',2,now())*100);
 if (q.financial_rules->>'dropoffCostSatang')::numeric is distinct from v_rule then raise exception 'stack financial rules changed'; end if;
 v_rule:=round(public.queuego_rule_numeric('route_bundle.minute_rate',1,now())*100);
 if (q.financial_rules->>'minuteRateSatangPerMinute')::numeric is distinct from v_rule then raise exception 'stack financial rules changed'; end if;
 v_rule:=round(public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,now())*1000);
 if (q.financial_rules->>'maxDetourMeters')::numeric is distinct from v_rule then raise exception 'stack financial rules changed'; end if;
 v_max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,now());
 if exists(select 1 from public.orders o join public.shop_profiles s on s.id=o.shop_id
           where o.id=any(q.order_ids) and lower(coalesce(s.public_category,'')) in ('food','cafe'))
 then v_max_delay:=least(v_max_delay,5); end if;
 if (q.financial_rules->>'maxDelaySeconds')::numeric is distinct from round(v_max_delay*60)
    or coalesce((q.financial_rules->>'maxPickupWaitSeconds')::integer,-1)<>300
 then raise exception 'stack financial rules changed'; end if;

 v_existing:=array_remove(q.order_ids,q.candidate_order_id);
 select array_agg(o.id order by o.id) into v_active
 from public.orders o
 where o.rider_id=v_rider and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
 if coalesce(v_active,'{}'::uuid[]) is distinct from
    (select array_agg(x order by x) from unnest(v_existing) x)
 then raise exception 'stack active membership changed'; end if;

 if exists(select 1 from public.laundry_rider_jobs l where l.rider_id=v_rider and l.status in ('assigned','accepted','arrived','collected'))
 then raise exception 'active laundry conflicts with route bundle'; end if;
 if exists(select 1 from public.orders o where o.id=any(v_existing) and o.next_after_order_id is not null)
 then raise exception 'next job conflicts with simultaneous bundle'; end if;

 foreach v_id in array q.order_ids loop
  v_rev:=(q.order_revisions->>v_id::text)::timestamptz;
  select * into v_order from public.orders where id=v_id;
  if v_order.updated_at is distinct from v_rev then raise exception 'stack order revision changed'; end if;
  if v_id=q.candidate_order_id then
   if v_order.status<>'searching_rider' or v_order.rider_id is not null or v_order.route_bundle_id is not null
      or v_order.market_order_id is not null then raise exception 'bundle order no longer available'; end if;
  else
   if v_order.rider_id<>v_rider or v_order.route_bundle_id<>q.bundle_id
      or v_order.status not in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
   then raise exception 'existing stack changed'; end if;
  end if;
  if v_order.market_order_id is not null or not exists(
    select 1 from public.shop_profiles s where s.id=v_order.shop_id and lower(coalesce(s.public_category,'')) in ('food','cafe','grocery')
  ) then raise exception 'unsupported stack order'; end if;

  select a into v_alloc from jsonb_array_elements(q.financial->'allocations') a where a->>'orderId'=v_id::text;
  if v_alloc is null or round(greatest(coalesce(v_order.delivery_fee,0),0)*100)::bigint<>(v_alloc->>'currentFeeSatang')::bigint
  then raise exception 'stack financial snapshot changed'; end if;
  if v_id<>q.candidate_order_id
     and round(greatest(coalesce(v_order.rider_delivery_payout,v_order.delivery_fee,0),0)*100)::bigint<>(v_alloc->>'riderPayoutSatang')::bigint
  then raise exception 'stack Rider payout changed'; end if;
 end loop;

 if not qg_private.route_bundle_stack_financial_payload_valid(
   jsonb_build_object(
    'financialPolicyVersion','stack-savings-group-weighted-v1','candidateOrderId',q.candidate_order_id,
    'orderIds',to_jsonb(q.order_ids),'financialOrders',q.financial_orders,'financialRules',q.financial_rules
   ),q.financial
 ) then raise exception 'stack financial quote invalid'; end if;

 select coalesce(max(route_bundle_sequence),0)+1 into v_seq
 from public.orders where route_bundle_id=q.bundle_id;

 perform set_config('queuego.route_bundle_reprice','on',true);
 perform set_config('queuego.v22_transition','rpc',true);

 for v_alloc in select a from jsonb_array_elements(q.financial->'allocations') a loop
  v_id:=(v_alloc->>'orderId')::uuid;
  v_fee:=(v_alloc->>'proposedFeeSatang')::numeric/100;
  v_saving:=(v_alloc->>'customerSavingSatang')::numeric/100;
  v_payout:=(v_alloc->>'riderPayoutSatang')::numeric/100;
  if v_id=q.candidate_order_id then
   update public.orders
   set rider_id=v_rider,rider_delivery_payout=v_payout,status='rider_assigned',
       rider_assigned_at=coalesce(rider_assigned_at,now()),
       delivery_fee=v_fee,total_amount=total_amount-delivery_fee+v_fee,
       route_bundle_id=q.bundle_id,route_bundle_sequence=v_seq,
       bundle_original_delivery_fee=delivery_fee,bundle_customer_savings=v_saving,
       bundle_rider_extra_fee=v_payout,
       bundle_detour_km=(q.financial->'route'->>'detourMeters')::numeric/1000,
       bundle_added_minutes=(q.financial->'route'->>'addedMinutes')::integer,
       bundle_route_sequence='STACK_GROUP_WEIGHTED_V1',
       note='__QT_ORDER_STATUS__=rider_assigned'||E'\n'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g'),
       updated_at=now()
   where id=v_id;
  else
   update public.orders
   set rider_delivery_payout=coalesce(rider_delivery_payout,delivery_fee),
       delivery_fee=v_fee,total_amount=total_amount-delivery_fee+v_fee,
       bundle_original_delivery_fee=coalesce(bundle_original_delivery_fee,delivery_fee),
       bundle_customer_savings=coalesce(bundle_customer_savings,0)+v_saving,
       updated_at=now()
   where id=v_id;
  end if;
 end loop;

 update public.payments p set amount=o.total_amount,updated_at=now()
 from public.orders o where p.order_id=o.id and o.id=any(q.order_ids) and p.status='pending';
 update public.deliveries d set rider_id=o.rider_id,delivery_fee=o.delivery_fee,
  status=case when d.status='pending' and o.id=q.candidate_order_id then 'assigned' else d.status end,
  updated_at=now()
 from public.orders o where d.order_id=o.id and o.id=any(q.order_ids)
   and d.status not in ('completed','cancelled');

 update public.route_bundles
 set stop_plan=q.stop_plan,route_revision=route_revision+1,updated_at=now()
 where id=q.bundle_id and route_revision=q.bundle_revision;
 if not found then raise exception 'bundle revision changed'; end if;

 insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
 values(v_offer.order_id,v_offer.rider_id,v_offer.offered_at,now(),'accepted',v_offer.attempt);
 delete from public.qg_rider_order_offers where order_id=q.candidate_order_id and rider_id=v_rider;
 update qg_private.route_bundle_stack_quotes set claimed_at=now() where id=q.id;

 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_user,'route_bundle_stack_claim','order',q.candidate_order_id,'rider_assigned_generalized_route_bundle',
  jsonb_build_object('bundle_id',q.bundle_id,'orders',v_count,'policy','stack-savings-group-weighted-v1',
   'route',q.financial->'route','allocations',q.financial->'allocations'));

 insert into public.notifications(user_id,title,message,type,reference_id)
 select o.customer_id,'ค่าส่งถูกลงจากงานพ่วง',
        'ระบบจัดเส้นทางร่วมและลดค่าส่งให้คุณ ฿'||trim(to_char((a->>'customerSavingSatang')::numeric/100,'FM999999990.00')),
        'order',o.id
 from jsonb_array_elements(q.financial->'allocations') a
 join public.orders o on o.id=(a->>'orderId')::uuid
 where o.customer_id is not null and (a->>'customerSavingSatang')::bigint>0;

 insert into public.notifications(user_id,title,message,type,reference_id)
 select s.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานพ่วงแล้ว สามารถเตรียมสินค้าได้','order',q.candidate_order_id
 from public.orders o join public.shop_profiles s on s.id=o.shop_id where o.id=q.candidate_order_id;

 return q.financial||jsonb_build_object(
  'quote_id',q.id,'bundle_id',q.bundle_id,'bundle_revision',q.bundle_revision+1,
  'status','rider_assigned','orders',v_count
 );
end $$;
revoke all on function public.queuego_claim_route_bundle_stack(uuid) from public,anon;
grant execute on function public.queuego_claim_route_bundle_stack(uuid) to authenticated;

-- Safety: this migration stages 3-5 quote/claim machinery only. The claim is
-- unreachable under the existing default route_bundle.max_orders=2.
