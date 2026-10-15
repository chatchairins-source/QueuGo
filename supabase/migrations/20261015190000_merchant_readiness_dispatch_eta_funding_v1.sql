-- Merchant readiness -> Dynamic Dispatch -> Rider Stack -> Customer ETA -> Cancellation/Funding.
-- Extends the existing QueueGo dispatcher/route_bundles/order state machine. No competing engine.
-- Rollout flags remain unchanged; this migration does NOT enable Saver or Continuous Dispatch.

alter table public.orders
  add column if not exists merchant_ready_estimate_at timestamptz,
  add column if not exists merchant_ready_estimate_updated_at timestamptz,
  add column if not exists merchant_ready_estimate_source text,
  add column if not exists merchant_readiness_revision bigint not null default 0 check (merchant_readiness_revision >= 0);

create index if not exists orders_active_ready_estimate_idx
  on public.orders(merchant_ready_estimate_at)
  where status in ('rider_assigned','preparing','ready');

create or replace function qg_private.default_prep_minutes(p_order_id uuid)
returns integer
language sql stable
set search_path=public,pg_temp
as $$
  select case
    when lower(coalesce(s.public_category,''))='grocery' then 10
    else 20
  end
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  where o.id=p_order_id
$$;
revoke all on function qg_private.default_prep_minutes(uuid) from public,anon,authenticated;

create or replace function qg_private.order_ready_wait_minutes(p_order_id uuid,p_at timestamptz)
returns numeric
language sql stable
set search_path=public,pg_temp
as $$
  select greatest(
    case
      when o.status in ('ready','picked_up','in_progress','completed') then 0::numeric
      when o.merchant_ready_estimate_at is not null then
        extract(epoch from (o.merchant_ready_estimate_at-p_at))/60.0
      -- Unknown is not fabricated into a promise. The order becomes authoritative
      -- once the Merchant starts preparing or publishes an explicit estimate.
      else 0::numeric
    end,
    0::numeric
  )
  from public.orders o
  where o.id=p_order_id
$$;
revoke all on function qg_private.order_ready_wait_minutes(uuid,timestamptz) from public,anon,authenticated;

create or replace function qg_private.sync_order_ready_estimate()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_minutes integer;
  v_target timestamptz;
  v_source text;
begin
  if new.status='preparing' and (old.status is distinct from new.status or new.merchant_ready_estimate_at is null) then
    v_minutes:=coalesce(qg_private.default_prep_minutes(new.id),20);
    v_target:=coalesce(new.merchant_ready_estimate_at,coalesce(new.preparing_at,now())+make_interval(mins=>v_minutes));
    v_source:=case when new.merchant_ready_estimate_at is null then 'system_default' else coalesce(new.merchant_ready_estimate_source,'merchant') end;
    update public.orders
       set merchant_ready_estimate_at=v_target,
           merchant_ready_estimate_updated_at=now(),
           merchant_ready_estimate_source=v_source,
           merchant_readiness_revision=merchant_readiness_revision+1
     where id=new.id
       and (
         merchant_ready_estimate_at is distinct from v_target
         or merchant_ready_estimate_source is distinct from v_source
       );
  elsif new.status='ready' and old.status is distinct from new.status then
    v_target:=coalesce(new.ready_at,now());
    update public.orders
       set merchant_ready_estimate_at=v_target,
           merchant_ready_estimate_updated_at=now(),
           merchant_ready_estimate_source='ready',
           merchant_readiness_revision=merchant_readiness_revision+1
     where id=new.id
       and (
         merchant_ready_estimate_at is distinct from v_target
         or merchant_ready_estimate_source is distinct from 'ready'
       );
  end if;
  return new;
end $$;
revoke all on function qg_private.sync_order_ready_estimate() from public,anon,authenticated;

drop trigger if exists orders_sync_ready_estimate on public.orders;
create trigger orders_sync_ready_estimate
after update of status,preparing_at,ready_at on public.orders
for each row execute function qg_private.sync_order_ready_estimate();

create or replace function public.qg_merchant_ready_estimate_once(
  p_request_id uuid,
  p_order_id uuid,
  p_ready_minutes integer
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_user uuid;
  v_existing public.qg_merchant_action_receipts%rowtype;
  v_order public.orders%rowtype;
  v_ready_at timestamptz;
  v_payload jsonb;
  v_result jsonb;
begin
  select u.id into v_user
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='shop'
    and u.status='active';
  if v_user is null then raise exception 'active shop request required'; end if;
  if p_request_id is null or p_order_id is null or p_ready_minutes not between 1 and 90 then
    raise exception 'valid ready estimate required';
  end if;

  v_payload:=jsonb_build_object(
    'p_order_id',p_order_id,
    'p_ready_minutes',p_ready_minutes
  );

  perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));
  select * into v_existing
  from public.qg_merchant_action_receipts
  where request_id=p_request_id;
  if found then
    if v_existing.user_id<>v_user or v_existing.kind<>'ready_estimate' or v_existing.payload<>v_payload then
      raise exception 'request id already in use';
    end if;
    return v_existing.result || jsonb_build_object('replayed',true);
  end if;

  select o.* into v_order
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  where o.id=p_order_id
    and s.user_id=v_user
  for update of o;
  if not found then raise exception 'order unavailable'; end if;
  if v_order.status<>'preparing' or v_order.rider_id is null then
    raise exception 'ready estimate requires an assigned preparing order';
  end if;

  v_ready_at:=now()+make_interval(mins=>p_ready_minutes);
  update public.orders
     set merchant_ready_estimate_at=v_ready_at,
         merchant_ready_estimate_updated_at=now(),
         merchant_ready_estimate_source='merchant',
         merchant_readiness_revision=merchant_readiness_revision+1
   where id=p_order_id
   returning * into v_order;

  v_result:=jsonb_build_object(
    'order_id',v_order.id,
    'ready_at',v_order.merchant_ready_estimate_at,
    'ready_minutes',p_ready_minutes,
    'readiness_revision',v_order.merchant_readiness_revision,
    'replayed',false
  );

  insert into public.qg_merchant_action_receipts(request_id,user_id,kind,payload,result)
  values(p_request_id,v_user,'ready_estimate',v_payload,v_result);

  return v_result;
end $$;
revoke all on function public.qg_merchant_ready_estimate_once(uuid,uuid,integer) from public,anon;
grant execute on function public.qg_merchant_ready_estimate_once(uuid,uuid,integer) to authenticated;

-- Replace the existing two-order quote with the same pricing model plus merchant readiness.
-- The generalized simulator remains authoritative. Live claim cap remains TWO.
create or replace function public.queuego_route_bundle_quote_orders(
  p_primary_order_id uuid,
  p_candidate_order_id uuid,
  p_at timestamptz default now()
) returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  a public.orders%rowtype;
  b public.orders%rowtype;
  ca text; cb text;
  base_km numeric;
  b_direct_km numeric;
  bundled_km numeric;
  detour_km numeric;
  delay_min integer;
  max_detour numeric;
  max_delay numeric;
  min_extra numeric;
  km_cost numeric;
  pickup_cost numeric;
  dropoff_cost numeric;
  time_cost numeric;
  rider_incremental numeric;
  raw_savings numeric;
  split_percent numeric;
  primary_savings numeric;
  candidate_savings numeric;
  candidate_fee numeric;
  primary_fee_after numeric;
  seq text;
  simulation jsonb;
  edges jsonb;
  road boolean:=false;
  primary_delay numeric;
  candidate_delay numeric;
  primary_ready_wait numeric:=0;
  candidate_ready_wait numeric:=0;
  readiness_wait numeric:=0;
  time_burden numeric:=0;
  bp_position integer;
  previous_key text;
  previous_finish numeric;
  travel_arrival numeric;
  k record;
  ad_position integer;
  bd_position integer;
begin
  if not public.queuego_feature_enabled('route_bundle',p_at) then
    return jsonb_build_object('eligible',false,'reason','route bundle is disabled');
  end if;
  select * into a from public.orders where id=p_primary_order_id;
  select * into b from public.orders where id=p_candidate_order_id;
  if a.id is null or b.id is null or a.id=b.id then
    return jsonb_build_object('eligible',false,'reason','order unavailable');
  end if;
  if a.market_order_id is not null or b.market_order_id is not null then
    return jsonb_build_object('eligible',false,'reason','market orders cannot be route bundled');
  end if;
  select lower(coalesce(public_category,'')) into ca from public.shop_profiles where id=a.shop_id;
  select lower(coalesce(public_category,'')) into cb from public.shop_profiles where id=b.shop_id;
  if ca not in ('food','cafe','grocery') or cb not in ('food','cafe','grocery') then
    return jsonb_build_object('eligible',false,'reason','unsupported service category');
  end if;
  if a.status not in ('rider_assigned','preparing','ready') then
    return jsonb_build_object('eligible',false,'reason','primary order already past pickup');
  end if;
  if b.status<>'searching_rider' or b.rider_id is not null then
    return jsonb_build_object('eligible',false,'reason','candidate no longer available');
  end if;
  if a.pickup_latitude is null or a.pickup_longitude is null or a.delivery_latitude is null or a.delivery_longitude is null
     or b.pickup_latitude is null or b.pickup_longitude is null or b.delivery_latitude is null or b.delivery_longitude is null then
    return jsonb_build_object('eligible',false,'reason','missing route coordinates');
  end if;

  base_km:=public.queuego_distance_km(a.pickup_latitude,a.pickup_longitude,a.delivery_latitude,a.delivery_longitude);
  b_direct_km:=public.queuego_distance_km(b.pickup_latitude,b.pickup_longitude,b.delivery_latitude,b.delivery_longitude);

  select jsonb_object_agg(x.key||'|'||y.key,jsonb_build_object('km',d.km,'minutes',d.km/25*60)) into edges
  from (values ('AP',a.pickup_latitude,a.pickup_longitude),('BP',b.pickup_latitude,b.pickup_longitude),
    ('AD',a.delivery_latitude,a.delivery_longitude),('BD',b.delivery_latitude,b.delivery_longitude)) x(key,lat,lng)
  cross join (values ('BP',b.pickup_latitude,b.pickup_longitude),('AD',a.delivery_latitude,a.delivery_longitude),
    ('BD',b.delivery_latitude,b.delivery_longitude)) y(key,lat,lng)
  cross join lateral (select public.queuego_distance_km(x.lat,x.lng,y.lat,y.lng) km) d
  where x.key<>y.key;

  select rm.edges into simulation
  from qg_private.route_bundle_road_matrices rm
  where rm.primary_order_id=a.id and rm.candidate_order_id=b.id
    and rm.primary_updated_at=a.updated_at
    and rm.candidate_updated_at=b.updated_at
    and rm.expires_at>p_at;
  if found then
    edges:=simulation;
    road:=true;
    base_km:=(edges->'AP|AD'->>'km')::numeric;
    b_direct_km:=(edges->'BP|BD'->>'km')::numeric;
  end if;

  primary_ready_wait:=coalesce(qg_private.order_ready_wait_minutes(a.id,p_at),0);
  candidate_ready_wait:=coalesce(qg_private.order_ready_wait_minutes(b.id,p_at),0);

  -- The Rider is anchored at the primary pickup. Waiting for that shop is part of
  -- the clock before any outgoing leg, but not extra customer delay versus solo.
  if primary_ready_wait>0 then
    for k in select key,value from jsonb_each(edges) where key like 'AP|%' loop
      edges:=jsonb_set(
        edges,
        array[k.key,'minutes'],
        to_jsonb((k.value->>'minutes')::numeric+primary_ready_wait),
        false
      );
    end loop;
  end if;

  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  if ca in ('food','cafe') or cb in ('food','cafe') then max_delay:=least(max_delay,5); end if;

  simulation:=qg_private.route_bundle_simulate(
    jsonb_build_array(
      jsonb_build_object(
        'key','BP','order_id',b.id,'kind','pickup','service_minutes',2,
        'ready_minutes',candidate_ready_wait
      ),
      jsonb_build_object('key','AD','order_id',a.id,'kind','dropoff','picked_up',true) ||
        case when road then jsonb_build_object(
          'max_ride_minutes',(edges->'AP|AD'->>'minutes')::numeric+max_delay
        ) else '{}'::jsonb end,
      jsonb_build_object('key','BD','order_id',b.id,'kind','dropoff','service_minutes',2) ||
        case when road then jsonb_build_object(
          'max_ride_minutes',(edges->'BP|BD'->>'minutes')::numeric+max_delay
        ) else '{}'::jsonb end
    ),
    edges,
    'AP',
    null,
    false
  );
  if not coalesce((simulation->>'eligible')::boolean,false) then return simulation; end if;

  bundled_km:=(simulation->>'distance_km')::numeric;

  select min(ordinality)::integer into ad_position
  from jsonb_array_elements_text(simulation->'stop_keys') with ordinality x(key,ordinality)
  where key='AD';
  select min(ordinality)::integer into bd_position
  from jsonb_array_elements_text(simulation->'stop_keys') with ordinality x(key,ordinality)
  where key='BD';
  seq:=case when ad_position<bd_position then 'PRIMARY_DROP_THEN_BUNDLE_DROP' else 'BUNDLE_DROP_THEN_PRIMARY_DROP' end;

  detour_km:=greatest(round(bundled_km-base_km,3),0);
  if road then
    primary_delay:=greatest(
      0,
      (simulation->'arrivals'->>'AD')::numeric-(edges->'AP|AD'->>'minutes')::numeric
    );
    candidate_delay:=greatest(
      0,
      (simulation->'arrivals'->>'BD')::numeric
        -(simulation->'arrivals'->>'BP')::numeric
        -(edges->'BP|BD'->>'minutes')::numeric
    );
    delay_min:=ceil(greatest(primary_delay,candidate_delay))::integer;
  else
    delay_min:=greatest(0,ceil(detour_km/25*60)::integer)+4;
  end if;

  select min(ordinality)::integer into bp_position
  from jsonb_array_elements_text(simulation->'stop_keys') with ordinality x(key,ordinality)
  where key='BP';
  if bp_position=1 then
    previous_key:='AP';
    previous_finish:=0;
  else
    previous_key:=simulation->'stop_keys'->>(bp_position-2);
    previous_finish:=coalesce((simulation->'arrivals'->>previous_key)::numeric,0);
  end if;
  travel_arrival:=previous_finish+coalesce((edges->(previous_key||'|BP')->>'minutes')::numeric,0);
  readiness_wait:=greatest(candidate_ready_wait-travel_arrival,0);

  max_detour:=public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,p_at);
  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  min_extra:=0;
  km_cost:=detour_km*public.queuego_rule_numeric('route_bundle.incremental_km_rate',5,p_at);
  pickup_cost:=public.queuego_rule_numeric('route_bundle.pickup_cost',2,p_at);
  dropoff_cost:=public.queuego_rule_numeric('route_bundle.dropoff_cost',2,p_at);
  time_burden:=greatest(delay_min::numeric,readiness_wait);
  time_cost:=time_burden*public.queuego_rule_numeric('route_bundle.minute_rate',1,p_at);
  if ca in ('food','cafe') or cb in ('food','cafe') then max_delay:=least(max_delay,5); end if;
  split_percent:=public.queuego_rule_numeric('route_bundle.primary_savings_percent',50,p_at);

  rider_incremental:=ceil((km_cost+pickup_cost+dropoff_cost+time_cost)*100)/100;
  raw_savings:=greatest(round(coalesce(b.delivery_fee,0)-rider_incremental,2),0);
  primary_savings:=least(
    round(raw_savings*split_percent/100,2),
    greatest(coalesce(a.delivery_fee,0),0)
  );
  candidate_savings:=greatest(round(raw_savings-primary_savings,2),0);
  candidate_fee:=round(coalesce(b.delivery_fee,0)-candidate_savings,2);
  primary_fee_after:=round(greatest(coalesce(a.delivery_fee,0)-primary_savings,0),2);

  return jsonb_build_object(
    'eligible',
      public.queuego_feature_enabled('route_bundle',p_at)
      and detour_km<=max_detour
      and delay_min<=max_delay
      and raw_savings>0
      and primary_savings>0
      and candidate_savings>0
      and rider_incremental>=min_extra,
    'primary_order_id',a.id,
    'candidate_order_id',b.id,
    'detour_km',detour_km,
    'added_minutes',delay_min,
    'primary_original_delivery_fee',a.delivery_fee,
    'primary_bundled_delivery_fee',primary_fee_after,
    'primary_customer_savings',primary_savings,
    'standalone_delivery_fee',b.delivery_fee,
    'bundled_delivery_fee',candidate_fee,
    'candidate_customer_savings',candidate_savings,
    'customer_savings',raw_savings,
    'rider_extra_fee',rider_incremental,
    'route_sequence',seq,
    'max_detour_km',max_detour,
    'max_delay_minutes',max_delay,
    'min_rider_extra_fee',min_extra,
    'primary_savings_percent',split_percent,
    'rider_route_payout',coalesce(a.rider_delivery_payout,a.delivery_fee)+rider_incremental,
    'incremental_distance_cost',km_cost,
    'incremental_pickup_cost',pickup_cost,
    'incremental_dropoff_cost',dropoff_cost,
    'incremental_time_cost',time_cost,
    'primary_ready_wait_minutes',round(primary_ready_wait,2),
    'candidate_ready_wait_minutes',round(candidate_ready_wait,2),
    'readiness_wait_minutes',round(readiness_wait,2),
    'readiness_verified',
      a.merchant_ready_estimate_at is not null and b.merchant_ready_estimate_at is not null,
    'quote_method','readiness_road_burden_fair_share_v4',
    'road_eta_verified',road,
    'planned_stop_keys',simulation->'stop_keys',
    'quoted_at',p_at
  );
end $$;
revoke all on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) to service_role;

-- Re-plan only the unpicked two-order bundle suffix when Merchant readiness changes.
-- Uses the SAME cached road matrix and generalized simulator; no second optimizer.
create or replace function qg_private.refresh_two_order_bundle_plan(
  p_bundle_id uuid,
  p_at timestamptz default now()
) returns boolean
language plpgsql
security definer
set search_path=public,pg_temp
as $
declare
  rb public.route_bundles%rowtype;
  a public.orders%rowtype;
  b public.orders%rowtype;
  edges jsonb;
  simulation jsonb;
  primary_wait numeric:=0;
  candidate_wait numeric:=0;
  max_delay numeric;
  new_plan jsonb;
  k record;
begin
  select * into rb
  from public.route_bundles
  where id=p_bundle_id and status='ACTIVE'
  for update;
  if not found then return false; end if;

  select * into a
  from public.orders
  where id=rb.primary_order_id
    and route_bundle_id=rb.id
    and status<>'cancelled';
  if not found then return false; end if;

  select * into b
  from public.orders
  where route_bundle_id=rb.id
    and id<>a.id
    and status<>'cancelled'
  order by route_bundle_sequence,created_at,id
  limit 1;
  if not found then return false; end if;

  if (select count(*) from public.orders where route_bundle_id=rb.id and status<>'cancelled')<>2 then
    return false;
  end if;
  if exists(
    select 1 from public.orders
    where route_bundle_id=rb.id
      and status in ('picked_up','in_progress','completed')
  ) then
    return false;
  end if;

  select rm.edges into edges
  from qg_private.route_bundle_road_matrices rm
  where rm.primary_order_id=a.id
    and rm.candidate_order_id=b.id
    and rm.primary_updated_at=a.updated_at
    and rm.candidate_updated_at=b.updated_at
    and rm.expires_at>p_at;
  if not found then return false; end if;

  primary_wait:=coalesce(qg_private.order_ready_wait_minutes(a.id,p_at),0);
  candidate_wait:=coalesce(qg_private.order_ready_wait_minutes(b.id,p_at),0);

  if primary_wait>0 then
    for k in select key,value from jsonb_each(edges) where key like 'AP|%' loop
      edges:=jsonb_set(
        edges,
        array[k.key,'minutes'],
        to_jsonb((k.value->>'minutes')::numeric+primary_wait),
        false
      );
    end loop;
  end if;

  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  if exists(
    select 1
    from public.orders o
    join public.shop_profiles s on s.id=o.shop_id
    where o.id in (a.id,b.id)
      and lower(coalesce(s.public_category,'')) in ('food','cafe')
  ) then
    max_delay:=least(max_delay,5);
  end if;

  simulation:=qg_private.route_bundle_simulate(
    jsonb_build_array(
      jsonb_build_object(
        'key','BP','order_id',b.id,'kind','pickup','service_minutes',2,
        'ready_minutes',candidate_wait
      ),
      jsonb_build_object(
        'key','AD','order_id',a.id,'kind','dropoff','picked_up',true,
        'max_ride_minutes',(edges->'AP|AD'->>'minutes')::numeric+max_delay
      ),
      jsonb_build_object(
        'key','BD','order_id',b.id,'kind','dropoff','service_minutes',2,
        'max_ride_minutes',(edges->'BP|BD'->>'minutes')::numeric+max_delay
      )
    ),
    edges,
    'AP',
    null,
    false
  );
  if not coalesce((simulation->>'eligible')::boolean,false) then return false; end if;

  select jsonb_build_array(
           jsonb_build_object('order_id',a.id,'kind','pickup')
         ) ||
         coalesce(
           jsonb_agg(
             jsonb_build_object(
               'order_id',case when x.key in ('AD') then a.id else b.id end,
               'kind',case when x.key='BP' then 'pickup' else 'dropoff' end
             )
             order by x.ordinality
           ),
           '[]'::jsonb
         )
    into new_plan
  from jsonb_array_elements_text(simulation->'stop_keys') with ordinality x(key,ordinality);

  if new_plan is distinct from rb.stop_plan then
    update public.route_bundles
       set stop_plan=new_plan,
           route_revision=route_revision+1,
           updated_at=now()
     where id=rb.id;
    return true;
  end if;
  return false;
end $;
revoke all on function qg_private.refresh_two_order_bundle_plan(uuid,timestamptz) from public,anon,authenticated;

create or replace function qg_private.on_order_readiness_replan()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $
begin
  if new.route_bundle_id is not null
     and new.merchant_ready_estimate_at is distinct from old.merchant_ready_estimate_at then
    perform qg_private.refresh_two_order_bundle_plan(new.route_bundle_id,now());
  end if;
  return new;
end $;
revoke all on function qg_private.on_order_readiness_replan() from public,anon,authenticated;

drop trigger if exists orders_bundle_readiness_replan on public.orders;
create trigger orders_bundle_readiness_replan
after update of merchant_ready_estimate_at on public.orders
for each row execute function qg_private.on_order_readiness_replan();

create or replace function qg_private.on_route_matrix_replan()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $
declare
  v_bundle uuid;
begin
  select route_bundle_id into v_bundle
  from public.orders
  where id=new.primary_order_id;
  if v_bundle is not null then
    perform qg_private.refresh_two_order_bundle_plan(v_bundle,now());
  end if;
  return new;
end $;
revoke all on function qg_private.on_route_matrix_replan() from public,anon,authenticated;

drop trigger if exists route_matrix_bundle_readiness_replan on qg_private.route_bundle_road_matrices;
create trigger route_matrix_bundle_readiness_replan
after insert or update of edges,expires_at on qg_private.route_bundle_road_matrices
for each row execute function qg_private.on_route_matrix_replan();

-- Cancellation may remove cancelled stops, but must preserve the completed/current live prefix.
create or replace function qg_private.validate_bundle_stop_plan()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
declare
  k record;
  v_fixed integer:=0;
  v_done boolean;
  v_orders integer;
  v_old_live jsonb:='[]'::jsonb;
  v_live_orders integer:=0;
begin
  if TG_OP='UPDATE' and new.stop_plan is not distinct from old.stop_plan then
    if new.route_revision<old.route_revision then raise exception 'route revision cannot regress'; end if;
    return new;
  end if;

  select count(*) into v_live_orders
  from public.orders o
  where o.route_bundle_id=new.id and o.status<>'cancelled';

  if jsonb_array_length(new.stop_plan)=0 then
    if v_live_orders>0 then raise exception 'cannot clear route while live orders remain'; end if;
    if TG_OP='UPDATE' and new.route_revision<>old.route_revision+1 then
      raise exception 'route revision conflict';
    end if;
    return new;
  end if;

  if exists(
    select 1 from jsonb_array_elements(new.stop_plan) p
    where coalesce(p->>'order_id','')!~'^[0-9a-fA-F-]{36}$'
       or coalesce(p->>'kind','') not in ('pickup','dropoff')
  ) then raise exception 'invalid route stop'; end if;

  select count(distinct p->>'order_id') into v_orders
  from jsonb_array_elements(new.stop_plan) p;
  if v_orders>5 or v_orders>public.queuego_rule_numeric('route_bundle.max_orders',2,now()) then
    raise exception 'route plan capacity exceeded';
  end if;

  if exists(
      select 1 from jsonb_array_elements(new.stop_plan) p
      group by p->>'order_id',p->>'kind' having count(*)<>1
    )
    or exists(
      select 1 from jsonb_array_elements(new.stop_plan) p
      group by p->>'order_id' having count(*)<>2
    )
    or exists(
      select 1 from jsonb_array_elements(new.stop_plan) p
      where not exists(
        select 1 from public.orders o
        where o.id=(p->>'order_id')::uuid
          and o.route_bundle_id=new.id
          and o.rider_id=new.rider_id
          and o.status<>'cancelled'
      )
    )
    or exists(
      select 1 from public.orders o
      where o.route_bundle_id=new.id
        and o.status<>'cancelled'
        and not exists(
          select 1 from jsonb_array_elements(new.stop_plan) p
          where p->>'order_id'=o.id::text
        )
    )
  then raise exception 'route plan must preserve every separate live order'; end if;

  if exists(
    select 1
    from jsonb_array_elements(new.stop_plan) with ordinality p(s,n)
    join jsonb_array_elements(new.stop_plan) with ordinality d(s,n)
      on d.s->>'order_id'=p.s->>'order_id' and d.s->>'kind'='dropoff'
    where p.s->>'kind'='pickup' and p.n>=d.n
  ) then raise exception 'pickup must precede dropoff'; end if;

  if TG_OP='UPDATE' then
    if new.route_revision<>old.route_revision+1 then raise exception 'route revision conflict'; end if;

    select coalesce(jsonb_agg(p.s order by p.n),'[]'::jsonb)
      into v_old_live
    from jsonb_array_elements(old.stop_plan) with ordinality p(s,n)
    join public.orders o on o.id=(p.s->>'order_id')::uuid
    where o.status<>'cancelled';

    for k in
      select p.s,p.n
      from jsonb_array_elements(v_old_live) with ordinality p(s,n)
      order by p.n
    loop
      select case
        when k.s->>'kind'='pickup' then o.status in ('picked_up','in_progress','completed')
        else o.status='completed'
      end
      into v_done
      from public.orders o
      where o.id=(k.s->>'order_id')::uuid;
      v_fixed:=v_fixed+1;
      exit when not coalesce(v_done,false);
    end loop;

    if v_fixed>0 and exists(
      select 1
      from generate_series(0,v_fixed-1) i
      where new.stop_plan->i is distinct from v_old_live->i
    ) then
      raise exception 'committed live stops cannot be reordered';
    end if;
  end if;
  return new;
end $$;
revoke all on function qg_private.validate_bundle_stop_plan() from public,anon,authenticated;

create table if not exists qg_private.rider_cancel_protection (
  order_id uuid primary key references public.orders(id) on delete restrict,
  bundle_id uuid references public.route_bundles(id) on delete restrict,
  rider_id uuid not null references public.rider_profiles(id) on delete restrict,
  shop_id uuid not null references public.shop_profiles(id) on delete restrict,
  payout_snapshot numeric not null check (payout_snapshot>=0),
  funding_source text not null default 'platform_advance_recover_from_shop'
    check (funding_source in ('platform_advance_recover_from_shop','platform_protection')),
  status text not null default 'payable'
    check (status in ('payable','settled','void')),
  created_at timestamptz not null default now(),
  settled_at timestamptz,
  metadata jsonb not null default '{}'::jsonb
);
alter table qg_private.rider_cancel_protection enable row level security;
revoke all on qg_private.rider_cancel_protection from public,anon,authenticated;
create index if not exists rider_cancel_protection_rider_created_idx
  on qg_private.rider_cancel_protection(rider_id,created_at desc);

create or replace function qg_private.on_bundled_order_cancelled()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_plan jsonb;
  v_new_plan jsonb;
  v_revision bigint;
  v_live integer;
  v_rider_user uuid;
  v_amount numeric;
begin
  if new.status<>'cancelled' or old.status='cancelled' or old.route_bundle_id is null then
    return new;
  end if;

  select rb.stop_plan,rb.route_revision
    into v_plan,v_revision
  from public.route_bundles rb
  where rb.id=old.route_bundle_id
  for update;

  if found then
    select coalesce(jsonb_agg(p.s order by p.n),'[]'::jsonb)
      into v_new_plan
    from jsonb_array_elements(coalesce(v_plan,'[]'::jsonb)) with ordinality p(s,n)
    where p.s->>'order_id'<>old.id::text;

    select count(*) into v_live
    from public.orders o
    where o.route_bundle_id=old.route_bundle_id
      and o.status<>'cancelled';

    if v_new_plan is distinct from v_plan then
      update public.route_bundles
         set stop_plan=v_new_plan,
             route_revision=v_revision+1,
             status=case when v_live=0 then 'CANCELLED' else status end,
             updated_at=now()
       where id=old.route_bundle_id;
    end if;
  end if;

  v_amount:=greatest(coalesce(old.rider_delivery_payout,0),0);
  if old.rider_id is not null and v_amount>0 then
    insert into qg_private.rider_cancel_protection(
      order_id,bundle_id,rider_id,shop_id,payout_snapshot,metadata
    ) values(
      old.id,old.route_bundle_id,old.rider_id,old.shop_id,v_amount,
      jsonb_build_object(
        'order_number',old.order_number,
        'cancelled_at',coalesce(new.cancelled_at,now()),
        'customer_delivery_fee',old.delivery_fee,
        'bundle_customer_savings',coalesce(old.bundle_customer_savings,0)
      )
    )
    on conflict(order_id) do nothing;

    select user_id into v_rider_user
    from public.rider_profiles
    where id=old.rider_id;
    if v_rider_user is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(
        v_rider_user,
        'ร้านยกเลิกงานพ่วง',
        'ตัดจุดที่ยกเลิกออกจากเส้นทางแล้ว รายได้ที่รับไว้ถูกบันทึกเป็นค่าชดเชย ไม่ใช่โบนัส',
        'order',
        old.id
      );
    end if;
  end if;

  return new;
end $$;
revoke all on function qg_private.on_bundled_order_cancelled() from public,anon,authenticated;

drop trigger if exists orders_bundle_cancel_continuity on public.orders;
create trigger orders_bundle_cancel_continuity
after update of status on public.orders
for each row
when (old.route_bundle_id is not null and old.status is distinct from new.status and new.status='cancelled')
execute function qg_private.on_bundled_order_cancelled();

create or replace function public.qg_rider_cancellation_compensations(p_from date,p_to date)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider uuid;
  v_rows jsonb;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';
  if v_rider is null then raise exception 'active rider required'; end if;
  if p_from is null or p_to is null or p_to<p_from or p_to>p_from+31 then
    raise exception 'invalid date range';
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'order_id',c.order_id,
    'bundle_id',c.bundle_id,
    'amount',c.payout_snapshot,
    'status',c.status,
    'funding_source',c.funding_source,
    'created_at',c.created_at,
    'settled_at',c.settled_at
  ) order by c.created_at desc),'[]'::jsonb)
  into v_rows
  from qg_private.rider_cancel_protection c
  where c.rider_id=v_rider
    and (c.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to;

  return v_rows;
end $$;
revoke all on function public.qg_rider_cancellation_compensations(date,date) from public,anon;
grant execute on function public.qg_rider_cancellation_compensations(date,date) to authenticated;

create table if not exists qg_private.rider_customer_eta_snapshots (
  order_id uuid primary key references public.orders(id) on delete cascade,
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  route_revision bigint not null default 0 check(route_revision>=0),
  eta_seconds integer not null check(eta_seconds between 0 and 14400),
  eta_at timestamptz not null,
  observed_at timestamptz not null default now(),
  expires_at timestamptz not null,
  source text not null default 'rider_navigation'
    check(source='rider_navigation')
);
alter table qg_private.rider_customer_eta_snapshots enable row level security;
revoke all on qg_private.rider_customer_eta_snapshots from public,anon,authenticated;
create index if not exists rider_customer_eta_expiry_idx
  on qg_private.rider_customer_eta_snapshots(expires_at);

create or replace function public.qg_rider_publish_customer_eta(
  p_order_id uuid,
  p_eta_seconds integer,
  p_route_revision bigint default null
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider uuid;
  v_order public.orders%rowtype;
  v_revision bigint:=0;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active';
  if v_rider is null then raise exception 'active rider required'; end if;
  if p_order_id is null or p_eta_seconds is null or p_eta_seconds not between 0 and 14400 then
    raise exception 'valid eta required';
  end if;

  select * into v_order
  from public.orders
  where id=p_order_id and rider_id=v_rider
  for update;
  if not found or v_order.status not in ('picked_up','in_progress') then
    raise exception 'customer eta requires current delivery ownership';
  end if;

  if v_order.route_bundle_id is not null then
    select route_revision into v_revision
    from public.route_bundles
    where id=v_order.route_bundle_id;
    if p_route_revision is not null and p_route_revision<>v_revision then
      raise exception 'route revision changed';
    end if;
  end if;

  insert into qg_private.rider_customer_eta_snapshots(
    order_id,rider_id,route_revision,eta_seconds,eta_at,observed_at,expires_at
  ) values(
    v_order.id,v_rider,coalesce(v_revision,0),p_eta_seconds,
    now()+make_interval(secs=>p_eta_seconds),now(),now()+interval '45 seconds'
  )
  on conflict(order_id) do update
    set rider_id=excluded.rider_id,
        route_revision=excluded.route_revision,
        eta_seconds=excluded.eta_seconds,
        eta_at=excluded.eta_at,
        observed_at=excluded.observed_at,
        expires_at=excluded.expires_at;

  return jsonb_build_object(
    'order_id',v_order.id,
    'eta_seconds',p_eta_seconds,
    'eta_at',now()+make_interval(secs=>p_eta_seconds),
    'route_revision',coalesce(v_revision,0),
    'expires_in_seconds',45
  );
end $$;
revoke all on function public.qg_rider_publish_customer_eta(uuid,integer,bigint) from public,anon;
grant execute on function public.qg_rider_publish_customer_eta(uuid,integer,bigint) to authenticated;

create or replace function public.qg_customer_order_eta(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_user uuid;
  v_order public.orders%rowtype;
  v_eta qg_private.rider_customer_eta_snapshots%rowtype;
begin
  select u.id into v_user
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='customer'
    and u.status='active';
  if v_user is null then raise exception 'active customer required'; end if;

  select * into v_order
  from public.orders
  where id=p_order_id and customer_id=v_user;
  if not found then raise exception 'order unavailable'; end if;

  if v_order.status in ('completed','cancelled','no_rider_available') then
    return jsonb_build_object('kind','closed','status',v_order.status);
  end if;

  if v_order.status in ('picked_up','in_progress') then
    select * into v_eta
    from qg_private.rider_customer_eta_snapshots e
    where e.order_id=v_order.id
      and e.rider_id=v_order.rider_id
      and e.expires_at>now();
    if found then
      return jsonb_build_object(
        'kind','delivery_eta',
        'eta_at',v_eta.eta_at,
        'eta_seconds',v_eta.eta_seconds,
        'updated_at',v_eta.observed_at,
        'confidence','road_live',
        'source','rider_navigation',
        'route_revision',v_eta.route_revision
      );
    end if;
    return jsonb_build_object(
      'kind','calculating',
      'status',v_order.status,
      'confidence','waiting_for_live_route'
    );
  end if;

  if v_order.status='ready' then
    return jsonb_build_object(
      'kind','ready',
      'ready_at',coalesce(v_order.ready_at,v_order.merchant_ready_estimate_at),
      'confidence','merchant_confirmed'
    );
  end if;

  if v_order.status='preparing' and v_order.merchant_ready_estimate_at is not null then
    return jsonb_build_object(
      'kind','merchant_ready',
      'ready_at',v_order.merchant_ready_estimate_at,
      'updated_at',v_order.merchant_ready_estimate_updated_at,
      'confidence',case when v_order.merchant_ready_estimate_source='merchant' then 'merchant_live' else 'system_default' end,
      'readiness_revision',v_order.merchant_readiness_revision
    );
  end if;

  return jsonb_build_object(
    'kind','calculating',
    'status',v_order.status,
    'confidence','waiting_for_merchant_readiness'
  );
end $$;
revoke all on function public.qg_customer_order_eta(uuid) from public,anon;
grant execute on function public.qg_customer_order_eta(uuid) to authenticated;

create or replace function qg_private.clear_customer_eta_on_terminal()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
begin
  if new.status in ('completed','cancelled','no_rider_available')
     or (old.rider_id is distinct from new.rider_id) then
    delete from qg_private.rider_customer_eta_snapshots where order_id=new.id;
  end if;
  return new;
end $$;
revoke all on function qg_private.clear_customer_eta_on_terminal() from public,anon,authenticated;

drop trigger if exists orders_clear_customer_eta on public.orders;
create trigger orders_clear_customer_eta
after update of status,rider_id on public.orders
for each row execute function qg_private.clear_customer_eta_on_terminal();
