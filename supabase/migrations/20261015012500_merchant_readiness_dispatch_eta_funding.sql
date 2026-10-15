-- Merchant readiness -> dispatch/stack -> customer ETA -> cancellation funding.
-- Extends the existing QueueGo order/dispatcher/route-bundle/cash systems only.
-- Saver remains controlled by feature.route_bundle; this migration does not enable it
-- and does not raise the live route-bundle cap above the existing rollout rule.

alter table public.orders
  add column if not exists merchant_ready_estimate_at timestamptz,
  add column if not exists merchant_ready_revision bigint not null default 0 check (merchant_ready_revision >= 0),
  add column if not exists merchant_ready_updated_at timestamptz;

create or replace function qg_private.apply_merchant_readiness_defaults()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
declare
  v_now timestamptz:=clock_timestamp();
  v_target timestamptz;
begin
  if tg_op='INSERT' then
    if new.status in ('searching_rider','rider_assigned','assigned','preparing') and new.merchant_ready_estimate_at is null then
      v_target:=case
        when new.status='preparing' and new.preparing_at is not null then new.preparing_at+interval '20 minutes'
        else v_now+interval '20 minutes'
      end;
      new.merchant_ready_estimate_at:=v_target;
      new.merchant_ready_revision:=greatest(coalesce(new.merchant_ready_revision,0),1);
      new.merchant_ready_updated_at:=v_now;
    elsif new.status='ready' then
      new.merchant_ready_estimate_at:=coalesce(new.ready_at,v_now);
      new.merchant_ready_revision:=greatest(coalesce(new.merchant_ready_revision,0),1);
      new.merchant_ready_updated_at:=v_now;
    end if;
    return new;
  end if;

  v_target:=new.merchant_ready_estimate_at;
  if new.status='ready' and old.status is distinct from new.status then
    v_target:=coalesce(new.ready_at,v_now);
  elsif new.status='preparing' and old.status is distinct from new.status
    and (v_target is null or v_target<v_now) then
    v_target:=coalesce(new.preparing_at,v_now)+interval '20 minutes';
  elsif new.status in ('searching_rider','rider_assigned','assigned','preparing')
    and v_target is null then
    v_target:=v_now+interval '20 minutes';
  end if;

  if v_target is distinct from old.merchant_ready_estimate_at then
    new.merchant_ready_estimate_at:=v_target;
    new.merchant_ready_revision:=old.merchant_ready_revision+1;
    new.merchant_ready_updated_at:=v_now;
  end if;
  return new;
end $$;

revoke all on function qg_private.apply_merchant_readiness_defaults() from public,anon,authenticated;

drop trigger if exists orders_merchant_readiness_defaults on public.orders;
create trigger orders_merchant_readiness_defaults
before insert or update of status,preparing_at,ready_at on public.orders
for each row execute function qg_private.apply_merchant_readiness_defaults();

create or replace function qg_private.guard_merchant_readiness_write()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
begin
  if current_user not in ('postgres','service_role','supabase_admin') then
    raise exception 'merchant readiness requires authoritative RPC';
  end if;
  return new;
end $$;

create or replace function qg_private.version_merchant_readiness_write()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
begin
  if new.merchant_ready_estimate_at is distinct from old.merchant_ready_estimate_at then
    new.merchant_ready_revision:=old.merchant_ready_revision+1;
    new.merchant_ready_updated_at:=clock_timestamp();
    new.updated_at:=clock_timestamp();
  end if;
  return new;
end $$;

revoke all on function qg_private.guard_merchant_readiness_write(),qg_private.version_merchant_readiness_write()
from public,anon,authenticated;

drop trigger if exists a_orders_merchant_readiness_guard on public.orders;
create trigger a_orders_merchant_readiness_guard
before update of merchant_ready_estimate_at,merchant_ready_revision,merchant_ready_updated_at on public.orders
for each row execute function qg_private.guard_merchant_readiness_write();

drop trigger if exists b_orders_merchant_readiness_version on public.orders;
create trigger b_orders_merchant_readiness_version
before update of merchant_ready_estimate_at on public.orders
for each row execute function qg_private.version_merchant_readiness_write();

-- Existing active orders get a conservative readiness signal. No completed/cancelled
-- financials or route snapshots are changed by this backfill.
update public.orders
set merchant_ready_estimate_at=case
      when status='ready' then coalesce(ready_at,updated_at,now())
      when status='preparing' then greatest(coalesce(preparing_at,updated_at,now())+interval '20 minutes',now())
      else greatest(coalesce(updated_at,now())+interval '20 minutes',now())
    end,
    merchant_ready_revision=greatest(merchant_ready_revision,1),
    merchant_ready_updated_at=now()
where merchant_ready_estimate_at is null
  and status in ('searching_rider','rider_assigned','assigned','preparing','ready');

create or replace function public.qg_merchant_set_ready_estimate(
  p_request_id uuid,
  p_order_id uuid,
  p_ready_at timestamptz,
  p_expected_revision bigint
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_user uuid;
  v_order public.orders%rowtype;
  v_existing public.qg_merchant_action_receipts%rowtype;
  v_payload jsonb;
  v_result jsonb;
  v_rider_user uuid;
begin
  select u.id into v_user
  from public.users u
  join public.shop_profiles sp on sp.user_id=u.id
  where u.auth_user_id=auth.uid()
    and u.role='shop'
    and u.status='active'
  order by sp.created_at asc
  limit 1;

  if v_user is null then raise exception 'active shop request required'; end if;
  if p_request_id is null or p_order_id is null or p_ready_at is null or p_expected_revision is null then
    raise exception 'ready estimate request is incomplete';
  end if;
  if p_ready_at<clock_timestamp()-interval '1 minute'
     or p_ready_at>clock_timestamp()+interval '2 hours' then
    raise exception 'ready estimate must be within the next 2 hours';
  end if;

  v_payload:=jsonb_build_object(
    'p_order_id',p_order_id,
    'p_ready_at',p_ready_at,
    'p_expected_revision',p_expected_revision
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
  join public.shop_profiles sp on sp.id=o.shop_id
  where o.id=p_order_id and sp.user_id=v_user
  for update of o;

  if not found then raise exception 'order unavailable'; end if;
  if v_order.status not in ('searching_rider','rider_assigned','assigned','preparing') then
    raise exception 'order is not waiting for merchant readiness';
  end if;
  if v_order.merchant_ready_revision<>p_expected_revision then
    raise exception 'readiness revision changed; refresh';
  end if;

  update public.orders
  set merchant_ready_estimate_at=p_ready_at
  where id=p_order_id
  returning * into v_order;

  v_result:=jsonb_build_object(
    'order_id',v_order.id,
    'ready_estimate_at',v_order.merchant_ready_estimate_at,
    'ready_revision',v_order.merchant_ready_revision,
    'replayed',false
  );

  insert into public.qg_merchant_action_receipts(request_id,user_id,kind,payload,result)
  values(p_request_id,v_user,'ready_estimate',v_payload,v_result);

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    v_user,'merchant_ready_estimate','order',p_order_id,'merchant readiness updated',
    jsonb_build_object('ready_estimate_at',v_order.merchant_ready_estimate_at,'ready_revision',v_order.merchant_ready_revision)
  );

  if v_order.rider_id is not null then
    select rp.user_id into v_rider_user from public.rider_profiles rp where rp.id=v_order.rider_id;
    if v_rider_user is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_rider_user,'ร้านอัปเดตเวลาพร้อม','เวลาที่ร้านคาดว่าจะพร้อมถูกอัปเดต ระบบจะปรับลำดับงานตามข้อมูลล่าสุด','order',p_order_id);
    end if;
  end if;

  return v_result;
end $$;

revoke all on function public.qg_merchant_set_ready_estimate(uuid,uuid,timestamptz,bigint)
from public,anon;
grant execute on function public.qg_merchant_set_ready_estimate(uuid,uuid,timestamptz,bigint)
to authenticated;

-- Make readiness a hard input to the SAME NEXT offer path. A late shop simply
-- receives no NEXT offer; the existing dispatcher continues searching normally.
create or replace function qg_private.guard_next_offer_merchant_readiness()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
declare
  v_candidate public.orders%rowtype;
  v_primary public.orders%rowtype;
  v_matrix record;
  v_pickup_minutes numeric;
  v_ready_minutes numeric;
begin
  if new.next_after_order_id is null then return new; end if;

  select * into v_candidate from public.orders where id=new.order_id;
  select * into v_primary from public.orders where id=new.next_after_order_id;
  if v_candidate.id is null or v_primary.id is null then return null; end if;
  if v_candidate.merchant_ready_estimate_at is null then return null; end if;

  select rm.edges,rm.expires_at into v_matrix
  from qg_private.route_bundle_road_matrices rm
  where rm.primary_order_id=v_primary.id
    and rm.candidate_order_id=v_candidate.id
    and rm.primary_updated_at=v_primary.updated_at
    and rm.candidate_updated_at=v_candidate.updated_at
    and rm.rider_id=new.rider_id
    and rm.expires_at>clock_timestamp()
  limit 1;

  if not found then return null; end if;
  if v_matrix.edges->'RP|AD'->>'minutes' is null or v_matrix.edges->'AD|BP'->>'minutes' is null then
    return null;
  end if;

  v_pickup_minutes:=(v_matrix.edges->'RP|AD'->>'minutes')::numeric+
                    (v_matrix.edges->'AD|BP'->>'minutes')::numeric;
  v_ready_minutes:=greatest(
    extract(epoch from (v_candidate.merchant_ready_estimate_at-clock_timestamp()))/60.0,
    0
  );

  if v_ready_minutes>v_pickup_minutes+5 then return null; end if;
  return new;
end $$;

revoke all on function qg_private.guard_next_offer_merchant_readiness()
from public,anon,authenticated;

drop trigger if exists qg_next_offer_merchant_readiness on public.qg_rider_order_offers;
create trigger qg_next_offer_merchant_readiness
before insert on public.qg_rider_order_offers
for each row
when (new.next_after_order_id is not null)
execute function qg_private.guard_next_offer_merchant_readiness();

-- Preserve the proven quote implementation as an internal helper, then put the
-- Merchant Readiness gate in front of the same public service-role RPC.
alter function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz)
rename to qg_route_bundle_quote_orders_without_readiness_v3;

revoke all on function public.qg_route_bundle_quote_orders_without_readiness_v3(uuid,uuid,timestamptz)
from public,anon,authenticated,service_role;

create or replace function public.queuego_route_bundle_quote_orders(
  p_primary_order_id uuid,
  p_candidate_order_id uuid,
  p_at timestamptz default now()
) returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_quote jsonb;
  a public.orders%rowtype;
  b public.orders%rowtype;
  v_edges jsonb;
  v_primary_ready numeric;
  v_candidate_ready numeric;
  v_primary_arrival numeric;
  v_candidate_arrival numeric;
  v_primary_wait numeric;
  v_candidate_wait numeric;
begin
  v_quote:=public.qg_route_bundle_quote_orders_without_readiness_v3(
    p_primary_order_id,p_candidate_order_id,p_at
  );
  if not coalesce((v_quote->>'eligible')::boolean,false) then return v_quote; end if;

  select * into a from public.orders where id=p_primary_order_id;
  select * into b from public.orders where id=p_candidate_order_id;
  if a.id is null or b.id is null
     or a.merchant_ready_estimate_at is null
     or b.merchant_ready_estimate_at is null then
    return v_quote || jsonb_build_object(
      'eligible',false,
      'reason','merchant readiness unavailable',
      'readiness_verified',false
    );
  end if;

  select rm.edges into v_edges
  from qg_private.route_bundle_road_matrices rm
  where rm.primary_order_id=a.id
    and rm.candidate_order_id=b.id
    and rm.primary_updated_at=a.updated_at
    and rm.candidate_updated_at=b.updated_at
    and rm.expires_at>p_at
  limit 1;

  if v_edges is null
     or v_edges->'RP|AP'->>'minutes' is null
     or v_edges->'AP|BP'->>'minutes' is null then
    return v_quote || jsonb_build_object(
      'eligible',false,
      'reason','fresh road route required for readiness',
      'readiness_verified',false
    );
  end if;

  v_primary_ready:=greatest(extract(epoch from (a.merchant_ready_estimate_at-p_at))/60.0,0);
  v_candidate_ready:=greatest(extract(epoch from (b.merchant_ready_estimate_at-p_at))/60.0,0);
  v_primary_arrival:=(v_edges->'RP|AP'->>'minutes')::numeric;
  v_primary_wait:=greatest(v_primary_ready-v_primary_arrival,0);
  v_candidate_arrival:=greatest(v_primary_arrival,v_primary_ready)+2+
                       (v_edges->'AP|BP'->>'minutes')::numeric;
  v_candidate_wait:=greatest(v_candidate_ready-v_candidate_arrival,0);

  if v_primary_wait>5 or v_candidate_wait>5 then
    return v_quote || jsonb_build_object(
      'eligible',false,
      'reason','merchant readiness would make Rider wait too long',
      'readiness_verified',true,
      'primary_pickup_wait_minutes',ceil(v_primary_wait),
      'candidate_pickup_wait_minutes',ceil(v_candidate_wait)
    );
  end if;

  return v_quote || jsonb_build_object(
    'readiness_verified',true,
    'primary_ready_estimate_at',a.merchant_ready_estimate_at,
    'candidate_ready_estimate_at',b.merchant_ready_estimate_at,
    'primary_pickup_wait_minutes',ceil(v_primary_wait),
    'candidate_pickup_wait_minutes',ceil(v_candidate_wait)
  );
end $$;

revoke all on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz)
from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz)
to service_role;

-- Expose readiness in the existing Rider stack wire object without adding a
-- competing stack endpoint or changing the RPC return signature.
create or replace function public.queuego_route_bundle_active_orders()
 returns table(bundle_id uuid, order_id uuid, order_number text, bundle_sequence integer, drop_rank integer, status text, shop_id uuid, shop_name text, pickup_address text, pickup_latitude double precision, pickup_longitude double precision, delivery_address text, delivery_latitude double precision, delivery_longitude double precision, delivery_fee numeric, original_delivery_fee numeric, customer_savings numeric, rider_extra_fee numeric, detour_km numeric, added_minutes integer, route_sequence text)
 language plpgsql
 stable security definer
 set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_bundle uuid;
  v_flip boolean:=false;
  v_plan jsonb;
  v_revision bigint;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider' and u.status='active'
    and r.status='active';
  if v_rider is null then return; end if;

  select rb.id into v_bundle
  from public.route_bundles rb
  where rb.rider_id=v_rider and rb.status='ACTIVE'
  order by rb.created_at
  limit 1;
  if v_bundle is null then return; end if;

  select exists(
    select 1 from public.orders o
    where o.route_bundle_id=v_bundle
      and o.route_bundle_sequence=2
      and o.bundle_route_sequence='BUNDLE_DROP_THEN_PRIMARY_DROP'
      and o.status<>'cancelled'
  ) into v_flip;

  select rb.stop_plan,rb.route_revision into v_plan,v_revision
  from public.route_bundles rb where rb.id=v_bundle;

  return query
  select
    v_bundle,
    o.id,
    o.order_number,
    o.route_bundle_sequence,
    case
      when v_flip and o.route_bundle_sequence=2 then 1
      when v_flip and o.route_bundle_sequence=1 then 2
      else o.route_bundle_sequence
    end as drop_rank,
    o.status,
    o.shop_id,
    s.shop_name,
    o.pickup_address,
    o.pickup_latitude,
    o.pickup_longitude,
    o.delivery_address,
    o.delivery_latitude,
    o.delivery_longitude,
    o.delivery_fee,
    o.bundle_original_delivery_fee,
    o.bundle_customer_savings,
    o.bundle_rider_extra_fee,
    o.bundle_detour_km,
    o.bundle_added_minutes,
    case when jsonb_array_length(coalesce(v_plan,'[]'::jsonb))>0 then
      jsonb_build_object(
        'kind','queuego_stop_plan_v1',
        'revision',v_revision,
        'customer_name',cu.name,
        'pickup_rank',(select k.ordinality from jsonb_array_elements(v_plan) with ordinality k(s,ordinality) where k.s->>'order_id'=o.id::text and k.s->>'kind'='pickup' limit 1),
        'dropoff_rank',(select k.ordinality from jsonb_array_elements(v_plan) with ordinality k(s,ordinality) where k.s->>'order_id'=o.id::text and k.s->>'kind'='dropoff' limit 1),
        'ready_estimate_at',o.merchant_ready_estimate_at,
        'ready_wait_minutes',ceil(greatest(extract(epoch from (coalesce(o.merchant_ready_estimate_at,now())-now()))/60.0,0))
      )::text
    else o.bundle_route_sequence end
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  left join public.users cu on cu.id=o.customer_id
  where o.route_bundle_id=v_bundle
    and o.rider_id=v_rider
    and o.status<>'cancelled'
  order by o.route_bundle_sequence,o.created_at,o.id;
end $$;

revoke all on function public.queuego_route_bundle_active_orders() from public,anon;
grant execute on function public.queuego_route_bundle_active_orders() to authenticated;

-- Merchant readiness updates may re-plan the unpicked suffix of the SAME
-- two-order Rider stack. It reuses the fresh matrix that was already validated
-- at claim time; no competing route engine is introduced.
create or replace function qg_private.refresh_two_order_bundle_plan(
  p_bundle_id uuid,
  p_at timestamptz default now()
) returns boolean
language plpgsql
security definer
set search_path=public,pg_temp
as $$
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

  -- Assignment changes order.updated_at, but not pickup/dropoff coordinates.
  -- For an already-claimed bundle the still-fresh matrix is safe to reuse for
  -- readiness-only re-ordering. Offer/claim revision checks remain strict.
  select rm.edges into edges
  from qg_private.route_bundle_road_matrices rm
  where rm.primary_order_id=a.id
    and rm.candidate_order_id=b.id
    and rm.expires_at>p_at;
  if not found then return false; end if;

  primary_wait:=greatest(
    extract(epoch from (coalesce(a.merchant_ready_estimate_at,p_at)-p_at))/60.0,
    0
  );
  candidate_wait:=greatest(
    extract(epoch from (coalesce(b.merchant_ready_estimate_at,p_at)-p_at))/60.0,
    0
  );

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
               'order_id',case when x.key='AD' then a.id else b.id end,
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
end $$;

revoke all on function qg_private.refresh_two_order_bundle_plan(uuid,timestamptz)
from public,anon,authenticated;

create or replace function qg_private.replan_bundle_on_merchant_readiness()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
begin
  if new.route_bundle_id is not null
     and new.merchant_ready_estimate_at is distinct from old.merchant_ready_estimate_at then
    perform qg_private.refresh_two_order_bundle_plan(new.route_bundle_id,clock_timestamp());
  end if;
  return new;
end $$;

revoke all on function qg_private.replan_bundle_on_merchant_readiness()
from public,anon,authenticated;

drop trigger if exists z_orders_bundle_readiness_replan on public.orders;
create trigger z_orders_bundle_readiness_replan
after update of merchant_ready_estimate_at on public.orders
for each row execute function qg_private.replan_bundle_on_merchant_readiness();

-- Cancellation may remove only cancelled stops. Completed/current committed live
-- stops still cannot be reordered.
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
begin
  if tg_op='UPDATE' and new.stop_plan is not distinct from old.stop_plan then
    if new.route_revision<old.route_revision then raise exception 'route revision cannot regress'; end if;
    return new;
  end if;

  if jsonb_array_length(new.stop_plan)=0 then
    if tg_op='UPDATE' and exists(
      select 1
      from jsonb_array_elements(old.stop_plan) p
      join public.orders o on o.id=(p->>'order_id')::uuid
      where o.status<>'cancelled'
    ) then
      raise exception 'cannot clear committed route';
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
      on d.s->>'order_id'=p.s->>'order_id'
     and d.s->>'kind'='dropoff'
    where p.s->>'kind'='pickup' and p.n>=d.n
  ) then raise exception 'pickup must precede dropoff'; end if;

  if tg_op='UPDATE' then
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

revoke all on function qg_private.validate_bundle_stop_plan()
from public,anon,authenticated;

-- Private accounting: this is a QueueGo funding obligation, NOT a claim that a
-- Rider has been paid. A future settlement rail must move reserved -> settled.
create table if not exists qg_private.rider_cancellation_funding (
  id uuid primary key default gen_random_uuid(),
  bundle_id uuid not null references public.route_bundles(id) on delete restrict,
  order_id uuid not null references public.orders(id) on delete restrict,
  rider_id uuid not null references public.rider_profiles(id) on delete restrict,
  amount numeric not null check(amount>=0),
  status text not null default 'reserved' check(status in ('reserved','settled','void')),
  reason text not null default 'bundle_counterparty_cancelled',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(bundle_id,order_id)
);
alter table qg_private.rider_cancellation_funding enable row level security;
revoke all on qg_private.rider_cancellation_funding from public,anon,authenticated;

create index if not exists rider_cancellation_funding_rider_idx
on qg_private.rider_cancellation_funding(rider_id,status,created_at desc);

create or replace function qg_private.reflow_bundle_after_cancel()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_bundle public.route_bundles%rowtype;
  v_remaining integer;
  v_order public.orders%rowtype;
  v_plan jsonb;
  v_payout numeric;
  v_gap numeric;
  v_rider_user uuid;
begin
  if new.status<>'cancelled' or old.status='cancelled' or old.route_bundle_id is null then
    return new;
  end if;

  if exists(select 1 from public.rider_cash_advances where order_id=new.id) then
    raise exception 'picked-up/cash-paid bundle order requires return flow, not cancellation';
  end if;

  select * into v_bundle
  from public.route_bundles
  where id=old.route_bundle_id
  for update;
  if not found then return new; end if;

  select count(*) into v_remaining
  from public.orders
  where route_bundle_id=v_bundle.id
    and status not in ('completed','cancelled');

  select coalesce(jsonb_agg(p.s order by p.n),'[]'::jsonb)
  into v_plan
  from jsonb_array_elements(v_bundle.stop_plan) with ordinality p(s,n)
  where p.s->>'order_id'<>new.id::text;

  if v_remaining>0 then
    update public.route_bundles
    set stop_plan=v_plan,
        route_revision=route_revision+1,
        updated_at=now()
    where id=v_bundle.id;
  end if;

  if v_remaining=1 then
    select * into v_order
    from public.orders
    where route_bundle_id=v_bundle.id
      and status not in ('completed','cancelled')
    for update;

    v_payout:=greatest(
      coalesce(v_order.rider_delivery_payout,0),
      coalesce(v_order.bundle_original_delivery_fee,0),
      coalesce(v_order.delivery_fee,0)
    );
    v_gap:=greatest(round(v_payout-coalesce(v_order.delivery_fee,0),2),0);

    update public.orders
    set rider_delivery_payout=v_payout,
        route_bundle_id=null,
        route_bundle_sequence=null,
        bundle_detour_km=0,
        bundle_added_minutes=0,
        updated_at=now()
    where id=v_order.id;

    if v_gap>0 and v_order.rider_id is not null then
      insert into qg_private.rider_cancellation_funding(
        bundle_id,order_id,rider_id,amount,status,reason
      ) values(
        v_bundle.id,v_order.id,v_order.rider_id,v_gap,'reserved','bundle_counterparty_cancelled'
      )
      on conflict(bundle_id,order_id) do update
      set amount=excluded.amount,
          status=case
            when qg_private.rider_cancellation_funding.status='settled' then 'settled'
            else 'reserved'
          end,
          updated_at=now();
    end if;

    update public.route_bundles
    set status='CANCELLED',updated_at=now()
    where id=v_bundle.id;

    if v_order.rider_id is not null then
      select user_id into v_rider_user from public.rider_profiles where id=v_order.rider_id;
      if v_rider_user is not null then
        insert into public.notifications(user_id,title,message,type,reference_id)
        values(
          v_rider_user,
          'ปรับเส้นทางหลังร้านยกเลิก',
          'ระบบถอดจุดที่ยกเลิกออกแล้ว งานที่เหลือยังคงรายได้มาตรฐาน และจะหางานต่อใหม่เมื่อเส้นทางปลอดภัย',
          'order',v_order.id
        );
      end if;
    end if;
  elsif v_remaining=0 then
    update public.route_bundles
    set status=case
      when exists(select 1 from public.orders where route_bundle_id=v_bundle.id and status='completed')
        then 'COMPLETED'
      else 'CANCELLED'
    end,
    updated_at=now()
    where id=v_bundle.id;
  end if;

  return new;
end $$;

revoke all on function qg_private.reflow_bundle_after_cancel()
from public,anon,authenticated;

drop trigger if exists a_qg_bundle_cancel_reflow on public.orders;
create trigger a_qg_bundle_cancel_reflow
after update of status on public.orders
for each row
when (new.status='cancelled' and old.status is distinct from new.status and old.route_bundle_id is not null)
execute function qg_private.reflow_bundle_after_cancel();

-- Existing Rider cash ledger remains the single read model. Add the private
-- funding obligation alongside each QT without changing product cash.
create or replace function public.qg_rider_cash_ledger(p_from date, p_to date)
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

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'order_id',o.id,
        'order_number',o.order_number,
        'status',o.status,
        'customer_id',o.customer_id,
        'shop_id',o.shop_id,
        'rider_id',o.rider_id,
        'subtotal',o.subtotal,
        'delivery_fee',coalesce(o.rider_delivery_payout,o.delivery_fee),
        'customer_delivery_fee',o.delivery_fee,
        'gp_amount',o.gp_amount,
        'total',o.total_amount,
        'cash_paid_merchant',a.amount,
        'cash_paid_at',a.paid_at,
        'merchant_confirmed',mr.amount,
        'merchant_confirmed_at',mr.received_at,
        'cash_refunded_merchant',coalesce(rr.amount,0),
        'cash_refunded_at',rr.refunded_at,
        'cash_collected_customer',case when p.status='paid' then p.amount else 0 end,
        'customer_paid_at',p.paid_at,
        'payment_id',p.id,
        'payment_status',p.status,
        'proof_verified_at',d.verified_at,
        'platform_cancellation_funding',coalesce(cf.amount,0),
        'platform_cancellation_funding_status',cf.status,
        'created_at',o.created_at
      )
      order by o.created_at desc
    ),
    '[]'::jsonb
  )
  into v_rows
  from public.orders o
  left join public.rider_cash_advances a on a.order_id=o.id
  left join public.merchant_cash_receipts mr on mr.order_id=o.id
  left join public.qg_rider_cash_return_refunds rr on rr.order_id=o.id
  left join public.payments p on p.order_id=o.id and p.payment_method='cash'
  left join public.qg_delivery_proofs d on d.order_id=o.id
  left join qg_private.rider_cancellation_funding cf
    on cf.order_id=o.id and cf.rider_id=o.rider_id and cf.status<>'void'
  where o.rider_id=v_rider
    and o.order_type='shopping'
    and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to;

  return v_rows;
end $$;

revoke all on function public.qg_rider_cash_ledger(date,date) from public,anon;
grant execute on function public.qg_rider_cash_ledger(date,date) to authenticated;

-- Customer ETA is derived from the same readiness/order/Rider state. It is
-- intentionally labelled approximate and does not pretend straight-line math is
-- road evidence.
create or replace function public.qg_customer_order_eta(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_customer uuid;
  o public.orders%rowtype;
  r public.rider_profiles%rowtype;
  prev public.orders%rowtype;
  v_now timestamptz:=clock_timestamp();
  v_pickup_at timestamptz;
  v_eta timestamptz;
  v_minutes numeric:=0;
  v_source text;
  v_shared boolean:=false;
begin
  select u.id into v_customer
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='customer'
    and u.status='active';
  if v_customer is null then raise exception 'active customer required'; end if;

  select * into o
  from public.orders
  where id=p_order_id and customer_id=v_customer;
  if not found then raise exception 'order unavailable'; end if;

  if o.status in ('completed','cancelled','no_rider_available') then
    return jsonb_build_object(
      'order_id',o.id,
      'eta_at',null,
      'merchant_ready_estimate_at',o.merchant_ready_estimate_at,
      'source','closed',
      'shared_route',o.route_bundle_id is not null,
      'road_eta_verified',false,
      'calculated_at',v_now
    );
  end if;

  if o.rider_id is null then
    return jsonb_build_object(
      'order_id',o.id,
      'eta_at',null,
      'merchant_ready_estimate_at',o.merchant_ready_estimate_at,
      'source','awaiting_rider',
      'shared_route',false,
      'road_eta_verified',false,
      'calculated_at',v_now
    );
  end if;

  select * into r from public.rider_profiles where id=o.rider_id;
  v_shared:=o.route_bundle_id is not null;

  if o.status in ('picked_up','in_progress') then
    if r.latitude is not null and r.longitude is not null
       and o.delivery_latitude is not null and o.delivery_longitude is not null then
      v_minutes:=ceil(public.queuego_distance_km(
        r.latitude,r.longitude,o.delivery_latitude,o.delivery_longitude
      )/25*60);
      v_eta:=v_now+make_interval(mins=>greatest(v_minutes::integer,1));
    end if;
    v_source:='rider_to_customer';
  elsif o.next_after_order_id is not null then
    select * into prev from public.orders where id=o.next_after_order_id;
    if r.latitude is not null and r.longitude is not null
       and prev.delivery_latitude is not null and prev.delivery_longitude is not null
       and o.pickup_latitude is not null and o.pickup_longitude is not null
       and o.delivery_latitude is not null and o.delivery_longitude is not null then
      v_minutes:=
        public.queuego_distance_km(r.latitude,r.longitude,prev.delivery_latitude,prev.delivery_longitude)/25*60+
        public.queuego_distance_km(prev.delivery_latitude,prev.delivery_longitude,o.pickup_latitude,o.pickup_longitude)/25*60;
      v_pickup_at:=greatest(
        v_now+make_interval(mins=>ceil(v_minutes)::integer),
        coalesce(o.merchant_ready_estimate_at,v_now)
      );
      v_minutes:=public.queuego_distance_km(
        o.pickup_latitude,o.pickup_longitude,o.delivery_latitude,o.delivery_longitude
      )/25*60;
      v_eta:=v_pickup_at+make_interval(mins=>greatest(ceil(v_minutes)::integer,1));
    end if;
    v_source:='continuous_stack';
    v_shared:=true;
  else
    if r.latitude is not null and r.longitude is not null
       and o.pickup_latitude is not null and o.pickup_longitude is not null
       and o.delivery_latitude is not null and o.delivery_longitude is not null then
      v_minutes:=public.queuego_distance_km(
        r.latitude,r.longitude,o.pickup_latitude,o.pickup_longitude
      )/25*60;
      v_pickup_at:=greatest(
        v_now+make_interval(mins=>ceil(v_minutes)::integer),
        coalesce(o.merchant_ready_estimate_at,v_now)
      );
      v_minutes:=public.queuego_distance_km(
        o.pickup_latitude,o.pickup_longitude,o.delivery_latitude,o.delivery_longitude
      )/25*60;
      v_eta:=v_pickup_at+make_interval(
        mins=>greatest(ceil(v_minutes)::integer+coalesce(o.bundle_added_minutes,0),1)
      );
    end if;
    v_source:=case when v_shared then 'route_stack' else 'merchant_readiness' end;
  end if;

  return jsonb_build_object(
    'order_id',o.id,
    'eta_at',v_eta,
    'eta_minutes',case when v_eta is null then null else greatest(ceil(extract(epoch from (v_eta-v_now))/60.0),0) end,
    'merchant_ready_estimate_at',o.merchant_ready_estimate_at,
    'merchant_ready_revision',o.merchant_ready_revision,
    'source',v_source,
    'shared_route',v_shared,
    'road_eta_verified',false,
    'calculated_at',v_now
  );
end $$;

revoke all on function public.qg_customer_order_eta(uuid) from public,anon;
grant execute on function public.qg_customer_order_eta(uuid) to authenticated;
