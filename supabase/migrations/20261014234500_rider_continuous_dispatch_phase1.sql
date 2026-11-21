-- Extends the existing dispatcher/claim/bundle engine. Rollout stays OFF until gates pass.
-- No Production sample orders. Existing cash ledger and action receipts remain authoritative.
alter table public.orders add column if not exists next_after_order_id uuid references public.orders(id);
alter table public.orders add column if not exists rider_delivery_payout numeric check (rider_delivery_payout >= 0);
create unique index if not exists orders_one_next_job_per_rider on public.orders(rider_id)
 where next_after_order_id is not null and status not in ('completed','cancelled');
create index if not exists orders_next_after_order_idx on public.orders(next_after_order_id) where next_after_order_id is not null;
alter table public.qg_rider_order_offers add column if not exists next_after_order_id uuid references public.orders(id);
create schema if not exists qg_private;
create table if not exists qg_private.rider_dispatch_preferences (
 rider_id uuid primary key references public.rider_profiles(id),
 auto_accept boolean not null default false,
 stop_after_work boolean not null default false,
 ready_until timestamptz not null default '-infinity',
 updated_at timestamptz not null default now()
);
alter table qg_private.rider_dispatch_preferences enable row level security;
revoke all on qg_private.rider_dispatch_preferences from public,anon,authenticated;

create or replace function public.qg_rider_dispatch_preference(p_auto_accept boolean,p_stop_after_work boolean,p_ready boolean)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare rr uuid;
begin
 select r.id into rr from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 for update of r;
 if rr is null or p_auto_accept is null or p_stop_after_work is null or p_ready is null then
  raise exception 'active rider and explicit preference required';
 end if;
 insert into qg_private.rider_dispatch_preferences(rider_id,auto_accept,stop_after_work,ready_until)
 values(rr,p_auto_accept,p_stop_after_work,case when p_ready and not p_stop_after_work then now()+interval '60 seconds' else now() end)
 on conflict(rider_id) do update set auto_accept=excluded.auto_accept,stop_after_work=excluded.stop_after_work,
 ready_until=excluded.ready_until,updated_at=now();
 return jsonb_build_object('acknowledged',true,'lease_seconds',60);
end $$;
revoke all on function public.qg_rider_dispatch_preference(boolean,boolean,boolean) from public,anon;
grant execute on function public.qg_rider_dispatch_preference(boolean,boolean,boolean) to authenticated;

-- Only privileged existing RPC paths may write assignment or payout snapshots.
create or replace function qg_private.guard_dispatch_snapshots() returns trigger language plpgsql
set search_path=public,pg_temp as $$
begin
 if current_user not in ('postgres','service_role','supabase_admin') and (
  (TG_OP='INSERT' and (new.next_after_order_id is not null or new.rider_delivery_payout is not null)) or
  (TG_OP='UPDATE' and (new.next_after_order_id is distinct from old.next_after_order_id or new.rider_delivery_payout is distinct from old.rider_delivery_payout))
 ) then raise exception 'dispatch snapshots require authoritative RPC'; end if;
 if new.next_after_order_id is not null and new.next_after_order_id=new.id then raise exception 'next job cannot follow itself'; end if;
 return new;
end $$;
create trigger orders_dispatch_snapshots before insert or update on public.orders for each row execute function qg_private.guard_dispatch_snapshots();

-- Do not visit/pay/pick up NEXT until the predecessor has terminated. Merchant preparation remains allowed.
create or replace function qg_private.guard_next_job_stage() returns trigger language plpgsql
set search_path=public,pg_temp as $$
begin
 if old.next_after_order_id is not null and exists(select 1 from public.orders where id=old.next_after_order_id and status not in ('completed','cancelled'))
 and (new.status in ('picked_up','in_progress','completed') or new.rider_arrived_shop_at is distinct from old.rider_arrived_shop_at)
 then raise exception 'finish current delivery before next pickup'; end if;
 return new;
end $$;
create trigger orders_next_stage before update on public.orders for each row execute function qg_private.guard_next_job_stage();
-- Status transition completion promotes the queued order in the SAME transaction.
create or replace function qg_private.promote_next_job() returns trigger language plpgsql security definer
set search_path=public,pg_temp as $$
begin
 if new.status in ('completed','cancelled') and old.status not in ('completed','cancelled') then
  update public.orders set next_after_order_id=null,updated_at=now() where next_after_order_id=new.id;
 end if;
 return new;
end $$;
create trigger orders_promote_next after update of status on public.orders for each row execute function qg_private.promote_next_job();
revoke all on function qg_private.guard_dispatch_snapshots(),qg_private.guard_next_job_stage(),qg_private.promote_next_job() from public,anon,authenticated;

CREATE OR REPLACE FUNCTION public.queuego_platform_rule_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry') then
    if jsonb_typeof(new.value) <> 'boolean' then
      raise exception 'feature rule must be boolean';
    end if;
  elsif new.rule_key in (
    'pricing.gp_default_rate',
    'pricing.market_base_fee',
    'pricing.market_distance_step_fee',
    'pricing.market_second_shop_fee',
    'pricing.market_additional_shop_fee',
    'route_bundle.max_detour_km',
    'route_bundle.max_delay_minutes',
    'route_bundle.max_orders',
    'route_bundle.min_rider_extra_fee',
    'route_bundle.primary_savings_percent',
    'dispatch.continuous_enabled',
    'dispatch.max_next_link_km',
    'route_bundle.incremental_km_rate',
    'route_bundle.pickup_cost',
    'route_bundle.dropoff_cost',
    'route_bundle.minute_rate'
  ) then
    if jsonb_typeof(new.value) <> 'number' then raise exception 'pricing rule must be numeric'; end if;
    n := (new.value #>> '{}')::numeric;
    if n < 0 then raise exception 'pricing rule cannot be negative'; end if;
    if new.rule_key='dispatch.continuous_enabled' and n not in (0,1) then raise exception 'continuous enabled must be 0 or 1'; end if;
    if new.rule_key='dispatch.max_next_link_km' and n>1.5 then raise exception 'next link cap is 1.5 km'; end if;
    if new.rule_key='pricing.gp_default_rate' and n > 100 then raise exception 'GP rate must be 0-100'; end if;
    if new.rule_key='route_bundle.primary_savings_percent' and (n < 1 or n > 99) then
      raise exception 'route bundle primary savings percent must be 1-99';
    end if;
    if new.rule_key='route_bundle.max_orders' and (n < 1 or n > 5 or n <> trunc(n)) then
      raise exception 'route bundle max orders must be integer 1-5';
    end if;
    if new.rule_key='route_bundle.max_delay_minutes' and n > 240 then raise exception 'route bundle delay too large'; end if;
    if new.rule_key='route_bundle.max_detour_km' and n > 50 then raise exception 'route bundle detour too large'; end if;
  else
    raise exception 'unsupported QueueGo platform rule: %', new.rule_key;
  end if;
  new.created_at := coalesce(new.created_at,now());
  return new;
end $function$;

CREATE OR REPLACE FUNCTION qg_private.claim_rider_order(p_rider uuid, p_order_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_rider uuid;
  v_user uuid;
  v_order public.orders%rowtype;
  v_previous uuid;
  v_offer_previous uuid;
  v_active_count integer;
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where r.id=p_rider
    and u.role='rider'
    and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true
  for update of r;

  if v_rider is null then raise exception 'active online rider required'; end if;

  select * into v_order from public.orders where id=p_order_id for update;
  if not found or v_order.order_type<>'shopping' then raise exception 'order no longer available'; end if;

  if v_order.rider_id=v_rider
     and v_order.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress','completed') then
    return v_order.status;
  end if;

  -- Same Rider row lock as all existing claim paths; no second queue or dispatcher.
  perform pg_advisory_xact_lock(hashtextextended(v_rider::text,337));
  select f.next_after_order_id into v_offer_previous from public.qg_rider_order_offers f
   where f.order_id=p_order_id and f.rider_id=v_rider and f.expires_at>now() for update;
  if not found then raise exception 'live targeted offer required'; end if;
  -- Serialize predecessor completion with reservation so promotion cannot be missed.
  if v_offer_previous is not null then perform 1 from public.orders where id=v_offer_previous for update; end if;
  select count(*),min(id::text)::uuid into v_active_count,v_previous from public.orders
   where rider_id=v_rider and id<>p_order_id and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
  if v_active_count>0 then
   if v_active_count<>1 or v_offer_previous is distinct from v_previous
    or public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())<>1
    or not exists(select 1 from qg_private.rider_dispatch_preferences dp where dp.rider_id=v_rider and dp.auto_accept and not dp.stop_after_work and dp.ready_until>now())
    or not exists(select 1 from public.orders a where a.id=v_previous and a.status='in_progress' and a.order_type='shopping' and a.fulfillment_vertical='food' and a.market_order_id is null and a.route_bundle_id is null)
    or not exists(select 1 from qg_private.route_bundle_road_matrices rm join public.orders a on a.id=rm.primary_order_id join public.rider_profiles r on r.id=v_rider
      where rm.primary_order_id=v_previous and rm.candidate_order_id=v_order.id and rm.primary_updated_at=a.updated_at
        and rm.candidate_updated_at=v_order.updated_at and rm.expires_at>now()
        and qg_private.approx_distance_km(r.latitude,r.longitude,rm.rider_latitude,rm.rider_longitude)<0.1
        and (rm.edges->'AD|BP'->>'km')::numeric<=public.queuego_rule_numeric('dispatch.max_next_link_km',1.5,now())
        and (rm.edges->'RP|AD'->>'minutes')::numeric+(rm.edges->'AD|BP'->>'minutes')::numeric<=10)
    or v_order.market_order_id is not null or v_order.fulfillment_vertical<>'food' 
    or exists(select 1 from public.laundry_rider_jobs lj where lj.rider_id=v_rider and lj.status in ('assigned','accepted','arrived','collected'))
    or exists(select 1 from public.qg_rider_delivery_returns dr where dr.rider_id=v_rider and dr.status not in ('resolved','returned','cancelled'))
   then raise exception 'finish current order before claiming another'; end if;
  else
   v_previous:=null;
  end if;

  if v_order.status<>'searching_rider' or v_order.rider_id is not null then
    raise exception 'order no longer available';
  end if;

  if v_order.fulfillment_vertical<>'food'
     and not exists(
       select 1 from public.rider_profiles r
       where r.id=v_rider
         and r.status='active'
         and r.vehicle_type in ('motorcycle','car','saleng')
         and r.vehicle_status='active'
         and r.vehicle_verified_at is not null
         and r.vehicle_capacity_kg>=public.market_order_weight(p_order_id)
         and public.market_order_weight(p_order_id)>0
     ) then raise exception 'vehicle capacity or availability insufficient for market order';
  end if;

  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set rider_id=v_rider,status='rider_assigned',next_after_order_id=v_previous,
      rider_assigned_at=coalesce(rider_assigned_at,now()),updated_at=now(),
      note='__QT_ORDER_STATUS__=rider_assigned'||E'\n'||
           regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
  where id=p_order_id;

  update public.deliveries
  set rider_id=v_rider,status='assigned',updated_at=now()
  where order_id=p_order_id and rider_id is null and status='pending';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user,'rider_claim','order',p_order_id,'rider_assigned',
         jsonb_build_object('from','searching_rider','to','rider_assigned'));

  if v_order.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_order.customer_id,'พบไรเดอร์แล้ว','มีไรเดอร์รับออเดอร์แล้ว ร้านค้าจะเริ่มเตรียมสินค้า','order',p_order_id);
  end if;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select sp.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานแล้ว สามารถเริ่มเตรียมสินค้าได้','order',p_order_id
  from public.shop_profiles sp where sp.id=v_order.shop_id;

  return 'rider_assigned';
end
$function$;
revoke all on function qg_private.claim_rider_order(uuid,uuid) from public,anon,authenticated;
create or replace function public.rider_claim_order(p_order_id uuid) returns text language plpgsql security definer
set search_path=public,pg_temp as $$
declare rr uuid;
begin
 select r.id into rr from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if rr is null then raise exception 'active rider required'; end if;
 return qg_private.claim_rider_order(rr,p_order_id);
end $$;
revoke all on function public.rider_claim_order(uuid) from public,anon,authenticated;

CREATE OR REPLACE FUNCTION public.qg_dispatch_rider_offers()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  j record;
  rr uuid;
  a int;
  v_next uuid;
  v_accept boolean;
  n int:=0;
begin
  insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
  select f.order_id,f.rider_id,f.offered_at,now(),'expired',f.attempt
  from public.qg_rider_order_offers f
  join public.orders o on o.id=f.order_id
  where f.expires_at<=now()
    and o.status='searching_rider'
    and o.rider_id is null;

  delete from public.qg_rider_order_offers f
  using public.orders o
  where f.order_id=o.id
    and (f.expires_at<=now() or o.status<>'searching_rider' or o.rider_id is not null);

  for j in
    select o.*
    from public.orders o
    where o.order_type='shopping'
      and o.status='searching_rider'
      and o.rider_id is null
      and (
        o.market_order_id is null
        or o.id=(
          select o2.id from public.orders o2
          where o2.market_order_id=o.market_order_id
            and o2.status<>'cancelled'
          order by o2.created_at,o2.id
          limit 1
        )
      )
      and (
        o.market_order_id is null
        or not exists(
          select 1 from public.orders o3
          where o3.market_order_id=o.market_order_id
            and o3.status not in('searching_rider','cancelled')
        )
      )
      and not exists(
        select 1 from public.qg_rider_order_offers f
        where f.order_id=o.id
      )
    order by o.created_at
    for update skip locked
  loop
    rr:=null; v_next:=null; v_accept:=false;

    select r.id,active.last_order,coalesce(pref.auto_accept and not pref.stop_after_work and pref.ready_until>now() and j.market_order_id is null and public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())=1,false) into rr,v_next,v_accept
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    left join qg_private.rider_destination_preferences dp
      on dp.rider_id=r.id
     and dp.enabled=true
    left join qg_private.rider_dispatch_preferences pref on pref.rider_id=r.id
    left join lateral (
      select count(*) n,min(b.id::text)::uuid last_order from public.orders b where b.rider_id=r.id
      and b.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
    ) active on true
    where u.role='rider' 
      and u.status='active'
      and r.status='active'
      and coalesce((r.metadata->>'online')::boolean,false)
      and coalesce((r.metadata->>'available')::boolean,false)
      and r.latitude is not null
      and r.longitude is not null
      and (pref.rider_id is null or not pref.stop_after_work)
      and (active.n=0 or (
        active.n=1 and j.market_order_id is null and j.fulfillment_vertical='food'
        and public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())=1
        and pref.auto_accept and not pref.stop_after_work and pref.ready_until>now()
        and exists(select 1 from public.orders a where a.id=active.last_order and a.status='in_progress' and a.order_type='shopping' and a.fulfillment_vertical='food'
          and a.market_order_id is null and a.route_bundle_id is null and a.next_after_order_id is null
          and a.delivery_latitude is not null and a.delivery_longitude is not null
          and j.pickup_latitude is not null and j.pickup_longitude is not null
          and qg_private.approx_distance_km(a.delivery_latitude,a.delivery_longitude,j.pickup_latitude,j.pickup_longitude)
            <=public.queuego_rule_numeric('dispatch.max_next_link_km',1.5,now())
          and qg_private.approx_distance_km(r.latitude,r.longitude,a.delivery_latitude,a.delivery_longitude)<=1.5
          and exists(select 1 from qg_private.route_bundle_road_matrices rm where rm.primary_order_id=a.id and rm.candidate_order_id=j.id
            and rm.primary_updated_at=a.updated_at and rm.candidate_updated_at=j.updated_at and rm.expires_at>now()
            and qg_private.approx_distance_km(r.latitude,r.longitude,rm.rider_latitude,rm.rider_longitude)<0.1
            and (rm.edges->'AD|BP'->>'km')::numeric<=public.queuego_rule_numeric('dispatch.max_next_link_km',1.5,now())
            and (rm.edges->'RP|AD'->>'minutes')::numeric+(rm.edges->'AD|BP'->>'minutes')::numeric<=10))
      ))
      and not exists(select 1 from public.qg_rider_delivery_returns dr where dr.rider_id=r.id and dr.status not in ('resolved','returned','cancelled'))
      and not exists(
        select 1 from public.laundry_rider_jobs lj
        where lj.rider_id=r.id
          and lj.status in('assigned','accepted','arrived','collected')
      )
      and not exists(
        select 1 from public.qg_rider_order_offers h
        where h.rider_id=r.id
          and h.expires_at>now()
      )
      and not exists(
        select 1
        from public.qg_rider_offer_history h
        where h.order_id=j.id
          and h.rider_id=r.id
          and (
            (h.outcome='declined' and h.resolved_at>now()-interval '10 minutes')
            or
            (h.outcome='expired' and h.resolved_at>now()-interval '30 seconds')
            or
            (coalesce(h.outcome,'') not in ('declined','expired')
             and h.resolved_at>now()-interval '10 minutes')
          )
      )
      and (
        (j.market_order_id is null and j.fulfillment_vertical='food')
        or (
          j.market_order_id is not null
          and r.vehicle_type in('motorcycle','car','saleng')
          and r.vehicle_status='active'
          and r.vehicle_verified_at is not null
          and r.vehicle_capacity_kg >= (
            select coalesce(sum(public.market_order_weight(o4.id)),0)
            from public.orders o4
            where o4.market_order_id=j.market_order_id
              and o4.status<>'cancelled'
          )
        )
        or (
          j.market_order_id is null
          and j.fulfillment_vertical in('market','grocery')
          and r.vehicle_type in('motorcycle','car','saleng')
          and r.vehicle_status='active'
          and r.vehicle_verified_at is not null
          and r.vehicle_capacity_kg>=public.market_order_weight(j.id)
          and public.market_order_weight(j.id)>0
        )
      )
    order by
      floor(
        qg_private.approx_distance_km(
          r.latitude::double precision,r.longitude::double precision,
          j.pickup_latitude::double precision,j.pickup_longitude::double precision
        )
      ),
      case
        when dp.rider_id is not null
         and j.delivery_latitude is not null
         and j.delivery_longitude is not null
         and (
           qg_private.approx_distance_km(
             r.latitude::double precision,r.longitude::double precision,
             dp.latitude,dp.longitude
           )
           -
           qg_private.approx_distance_km(
             j.delivery_latitude::double precision,j.delivery_longitude::double precision,
             dp.latitude,dp.longitude
           )
         ) >= 1.0
        then 0 else 1
      end,
      case
        when dp.rider_id is not null
         and j.delivery_latitude is not null
         and j.delivery_longitude is not null
        then qg_private.approx_distance_km(
          j.delivery_latitude::double precision,j.delivery_longitude::double precision,
          dp.latitude,dp.longitude
        )
        else 1000000.0
      end,
      qg_private.approx_distance_km(
        r.latitude::double precision,r.longitude::double precision,
        j.pickup_latitude::double precision,j.pickup_longitude::double precision
      ),
      r.updated_at,
      r.id
    limit 1
    for update of r skip locked;

    if rr is not null then
      select coalesce(max(attempt),0)+1
      into a
      from public.qg_rider_offer_history
      where order_id=j.id;

      insert into public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt,next_after_order_id)
      values(j.id,rr,now(),now()+interval '30 seconds',a,v_next)
      on conflict(order_id) do nothing;

      if found then
        n:=n+1;
        if v_accept then
          perform qg_private.claim_rider_order(rr,j.id);
          insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
            values(j.id,rr,now(),now(),'accepted',a);
          delete from public.qg_rider_order_offers where order_id=j.id and rider_id=rr;
          continue;
        end if;

        insert into public.notifications(user_id,title,message,type,reference_id)
        select r.user_id,
               'มีงานใหม่',
               'มีออเดอร์ใหม่ใกล้คุณ กรุณาตอบรับภายใน 30 วินาที',
               'order',
               j.id
        from public.rider_profiles r
        where r.id=rr;
      end if;
    end if;
  end loop;

  return n;
end
$function$;

-- Short-lived routing evidence, not a second route-bundle or assignment system.
create table if not exists qg_private.route_bundle_road_matrices(
 primary_order_id uuid not null references public.orders(id),
 candidate_order_id uuid not null references public.orders(id),
 primary_updated_at timestamptz not null,candidate_updated_at timestamptz not null,
 rider_id uuid not null references public.rider_profiles(id),
 rider_latitude double precision not null,rider_longitude double precision not null,
 edges jsonb not null check(jsonb_typeof(edges)='object'),
 expires_at timestamptz not null,primary key(primary_order_id,candidate_order_id)
);
alter table qg_private.route_bundle_road_matrices enable row level security;
revoke all on qg_private.route_bundle_road_matrices from public,anon,authenticated;
create index if not exists route_bundle_road_matrices_candidate_idx on qg_private.route_bundle_road_matrices(candidate_order_id);
create index if not exists route_bundle_road_matrices_rider_idx on qg_private.route_bundle_road_matrices(rider_id);
create index if not exists route_bundle_road_matrices_expiry_idx on qg_private.route_bundle_road_matrices(expires_at);

create or replace function public.queuego_cache_route_bundle_matrix(
 p_primary_order_id uuid,p_candidate_order_id uuid,p_primary_updated_at timestamptz,p_candidate_updated_at timestamptz,
 p_rider_latitude double precision,p_rider_longitude double precision,p_edges jsonb
) returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
declare a public.orders%rowtype;b public.orders%rowtype;r public.rider_profiles%rowtype;
begin
 select * into a from public.orders where id=p_primary_order_id;
 select * into b from public.orders where id=p_candidate_order_id;
 select * into r from public.rider_profiles where id=a.rider_id;
 if a.id is null or b.id is null or r.id is null or a.id=b.id or a.updated_at is distinct from p_primary_updated_at
  or b.updated_at is distinct from p_candidate_updated_at or b.rider_id is not null or b.status<>'searching_rider'
  or a.status not in ('rider_assigned','preparing','ready','picked_up','in_progress')
  or p_rider_latitude is distinct from r.latitude or p_rider_longitude is distinct from r.longitude
 then return false; end if;
 if p_edges is null or jsonb_typeof(p_edges)<>'object' then raise exception 'road matrix required'; end if;
 if exists(select 1 from jsonb_each(p_edges) x where
  jsonb_typeof(x.value->'km') is distinct from 'number' or jsonb_typeof(x.value->'minutes') is distinct from 'number'
  or (x.value->>'km')::numeric<0 or (x.value->>'km')::numeric>=10000
  or (x.value->>'minutes')::numeric<0 or (x.value->>'minutes')::numeric>=10000)
 then raise exception 'invalid road edge'; end if;
 if not (p_edges ?& array['AP|AD','BP|BD','RP|AD','AD|BP']) then raise exception 'incomplete road matrix'; end if;
 delete from qg_private.route_bundle_road_matrices where expires_at<now()-interval '1 day';
 insert into qg_private.route_bundle_road_matrices values(a.id,b.id,a.updated_at,b.updated_at,r.id,r.latitude,r.longitude,p_edges,now()+interval '90 seconds')
 on conflict(primary_order_id,candidate_order_id) do update set primary_updated_at=excluded.primary_updated_at,
 candidate_updated_at=excluded.candidate_updated_at,rider_id=excluded.rider_id,rider_latitude=excluded.rider_latitude,
 rider_longitude=excluded.rider_longitude,edges=excluded.edges,expires_at=excluded.expires_at;
 return true;
end $$;
revoke all on function public.queuego_cache_route_bundle_matrix(uuid,uuid,timestamptz,timestamptz,double precision,double precision,jsonb) from public,anon,authenticated;
grant execute on function public.queuego_cache_route_bundle_matrix(uuid,uuid,timestamptz,timestamptz,double precision,double precision,jsonb) to service_role;

-- Shared route simulator for the EXISTING bundle quote. No dispatcher, pool or order model is added.
-- Directed road matrices can use this exact function; missing edges fail closed (no straight-line fallback here).
create or replace function qg_private.route_bundle_simulate(
 p_stops jsonb,p_edges jsonb,p_start text default 'START',p_locked_next text default null,
 p_pickups_first boolean default false
) returns jsonb language plpgsql immutable set search_path=public,pg_temp as $$
declare v_plan jsonb;v_count integer;v_orders integer;
begin
 if jsonb_typeof(p_stops)<>'array' or jsonb_typeof(p_edges)<>'object' then
  return jsonb_build_object('eligible',false,'reason','invalid route input');
 end if;
 select count(*),count(distinct s->>'order_id') into v_count,v_orders from jsonb_array_elements(p_stops) s;
 if v_count<1 or v_count>10 or v_orders>5 or v_orders<1
  or exists(select 1 from jsonb_array_elements(p_stops) s where coalesce(s->>'key','')='' or coalesce(s->>'order_id','')='' or coalesce(s->>'kind','') not in ('pickup','dropoff'))
  or (select count(distinct s->>'key') from jsonb_array_elements(p_stops) s)<>v_count
  or exists(select 1 from jsonb_array_elements(p_stops) s group by s->>'order_id',s->>'kind' having count(*)>1)
  or exists(select 1 from jsonb_array_elements(p_stops) s where s->>'kind'='pickup' and not exists(select 1 from jsonb_array_elements(p_stops) d where d->>'order_id'=s->>'order_id' and d->>'kind'='dropoff'))
 then return jsonb_build_object('eligible',false,'reason','invalid stop identity or hard cap'); end if;
 if exists(select 1 from jsonb_array_elements(p_stops) s where s->>'kind'='dropoff'
   and not coalesce((s->>'picked_up')::boolean,false)
   and not exists(select 1 from jsonb_array_elements(p_stops) p where p->>'order_id'=s->>'order_id' and p->>'kind'='pickup'))
 then return jsonb_build_object('eligible',false,'reason','missing pickup'); end if;
 with recursive stops as (
  select s, (ordinality)::integer n from jsonb_array_elements(p_stops) with ordinality a(s,ordinality)
 ), walk(path,last_key,km,minutes,pickup_times,arrivals) as (
  select array[]::integer[],p_start,0::numeric,0::numeric,'{}'::jsonb,'{}'::jsonb
  union all
  select w.path||x.n,x.s->>'key',w.km+(e.edge->>'km')::numeric,
   t.finish,
   case when x.s->>'kind'='pickup' then w.pickup_times||jsonb_build_object(x.s->>'order_id',t.finish) else w.pickup_times end,
   w.arrivals||jsonb_build_object(x.s->>'key',t.finish)
  from walk w join stops x on not x.n=any(w.path)
  cross join lateral (select p_edges->(w.last_key||'|'||(x.s->>'key')) edge) e
  cross join lateral (select greatest(w.minutes+(e.edge->>'minutes')::numeric,coalesce((x.s->>'ready_minutes')::numeric,0))
    +coalesce((x.s->>'service_minutes')::numeric,0) finish) t
  where e.edge is not null and e.edge->>'km' is not null and e.edge->>'minutes' is not null
   and (e.edge->>'km')::numeric>=0 and (e.edge->>'minutes')::numeric>=0
   and (e.edge->>'km')::numeric<10000 and (e.edge->>'minutes')::numeric<10000
   and coalesce((x.s->>'service_minutes')::numeric,0)>=0
   and (cardinality(w.path)>0 or p_locked_next is null or x.s->>'key'=p_locked_next)
   and (x.s->>'kind'='pickup' or w.pickup_times ? (x.s->>'order_id') or coalesce((x.s->>'picked_up')::boolean,false))
   and (not p_pickups_first or x.s->>'kind'='pickup' or not exists(select 1 from stops p where p.s->>'kind'='pickup' and not p.n=any(w.path)))
   and (not x.s ? 'deadline_minutes' or t.finish <= (x.s->>'deadline_minutes')::numeric)
   and (not x.s ? 'baseline_minutes' or t.finish <= (x.s->>'baseline_minutes')::numeric+coalesce((x.s->>'max_delay_minutes')::numeric,0))
   and (x.s->>'kind'<>'dropoff' or not x.s ? 'max_ride_minutes' or
    t.finish-coalesce((w.pickup_times->>(x.s->>'order_id'))::numeric,-coalesce((x.s->>'already_carried_minutes')::numeric,0)) <= (x.s->>'max_ride_minutes')::numeric)
 )
 select jsonb_build_object('eligible',true,'distance_km',w.km,'duration_minutes',w.minutes,
  'stop_keys',(select jsonb_agg(s.s->>'key' order by a.ordinality) from unnest(w.path) with ordinality a(n,ordinality) join stops s on s.n=a.n),
  'arrivals',w.arrivals,'orders',v_orders)
 into v_plan from walk w where cardinality(w.path)=v_count
 order by w.km,w.minutes,w.path limit 1;
 return coalesce(v_plan,jsonb_build_object('eligible',false,'reason','no route satisfies precedence, committed next stop and quality'));
end $$;
revoke all on function qg_private.route_bundle_simulate(jsonb,jsonb,text,text,boolean) from public,anon,authenticated;

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
  route_a_km numeric;
  route_b_km numeric;
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

  -- Existing two-order live behavior uses the same generalized simulator, with pickup-first
  -- compatibility until the dynamic-stop UI/backend gate passes. Do not raise the rollout cap.
  select jsonb_object_agg(x.key||'|'||y.key,jsonb_build_object('km',d.km,'minutes',d.km/25*60)) into edges
  from (values ('AP',a.pickup_latitude,a.pickup_longitude),('BP',b.pickup_latitude,b.pickup_longitude),
    ('AD',a.delivery_latitude,a.delivery_longitude),('BD',b.delivery_latitude,b.delivery_longitude)) x(key,lat,lng)
  cross join (values ('BP',b.pickup_latitude,b.pickup_longitude),('AD',a.delivery_latitude,a.delivery_longitude),
    ('BD',b.delivery_latitude,b.delivery_longitude)) y(key,lat,lng)
  cross join lateral (select public.queuego_distance_km(x.lat,x.lng,y.lat,y.lng) km) d
  where x.key<>y.key;
  select rm.edges into simulation from qg_private.route_bundle_road_matrices rm
   where rm.primary_order_id=a.id and rm.candidate_order_id=b.id and rm.primary_updated_at=a.updated_at
    and rm.candidate_updated_at=b.updated_at and rm.expires_at>p_at;
  if found then
    edges:=simulation; road:=true;
    base_km:=(edges->'AP|AD'->>'km')::numeric;
    b_direct_km:=(edges->'BP|BD'->>'km')::numeric;
  end if;
  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  if ca in ('food','cafe') or cb in ('food','cafe') then max_delay:=least(max_delay,5); end if;
  simulation:=qg_private.route_bundle_simulate(jsonb_build_array(
    jsonb_build_object('key','BP','order_id',b.id,'kind','pickup','service_minutes',2),
    jsonb_build_object('key','AD','order_id',a.id,'kind','dropoff','picked_up',true) ||
      case when road then jsonb_build_object('max_ride_minutes',(edges->'AP|AD'->>'minutes')::numeric+max_delay) else '{}'::jsonb end,
    jsonb_build_object('key','BD','order_id',b.id,'kind','dropoff','service_minutes',2) ||
      case when road then jsonb_build_object('max_ride_minutes',(edges->'BP|BD'->>'minutes')::numeric+max_delay) else '{}'::jsonb end),edges,'AP','BP',true);
  if not coalesce((simulation->>'eligible')::boolean,false) then return simulation; end if;
  bundled_km:=(simulation->>'distance_km')::numeric;
  seq:=case when simulation->'stop_keys'->>1='AD' then 'PRIMARY_DROP_THEN_BUNDLE_DROP' else 'BUNDLE_DROP_THEN_PRIMARY_DROP' end;

  detour_km:=greatest(round(bundled_km-base_km,3),0);
  if road then
    primary_delay:=greatest(0,(simulation->'arrivals'->>'AD')::numeric-(edges->'AP|AD'->>'minutes')::numeric);
    candidate_delay:=greatest(0,(simulation->'arrivals'->>'BD')::numeric-(simulation->'arrivals'->>'BP')::numeric-(edges->'BP|BD'->>'minutes')::numeric);
    delay_min:=ceil(greatest(primary_delay,candidate_delay))::integer;
  else
    delay_min:=greatest(0,ceil(detour_km/25*60)::integer)+4;
  end if;

  max_detour:=public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,p_at);
  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  min_extra:=0; -- legacy wire field only; no bonus or fixed extra floor
  km_cost:=detour_km*public.queuego_rule_numeric('route_bundle.incremental_km_rate',5,p_at);
  pickup_cost:=public.queuego_rule_numeric('route_bundle.pickup_cost',2,p_at);
  dropoff_cost:=public.queuego_rule_numeric('route_bundle.dropoff_cost',2,p_at);
  time_cost:=delay_min*public.queuego_rule_numeric('route_bundle.minute_rate',1,p_at);
  if ca in ('food','cafe') or cb in ('food','cafe') then max_delay:=least(max_delay,5); end if;
  split_percent:=public.queuego_rule_numeric('route_bundle.primary_savings_percent',50,p_at);

  -- Route distance, added pickup/dropoff handling and time are costs, never a Bundle Bonus.
  -- Never cap fair compensation to the candidate's solo fee: reject the bundle instead.
  rider_incremental:=ceil((km_cost+pickup_cost+dropoff_cost+time_cost)*100)/100;
  raw_savings:=greatest(round(coalesce(b.delivery_fee,0)-rider_incremental,2),0);

  -- Reallocate the saving, not the Rider income:
  -- primary fee falls by primary_savings, candidate fee rises by the same amount
  -- above the incremental Rider fee. Total delivery income remains unchanged.
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
    'quote_method','geo_burden_fair_share_v3',
    'road_eta_verified',road,
    'planned_stop_keys',simulation->'stop_keys',
    'quoted_at',p_at
  );
end $$;


revoke all on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) to service_role;

CREATE OR REPLACE FUNCTION public.queuego_claim_route_bundle(p_order_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_rider uuid;
  v_user uuid;
  v_primary public.orders%rowtype;
  v_candidate public.orders%rowtype;
  v_bundle public.route_bundles%rowtype;
  v_quote jsonb;
  v_count integer;
  v_max integer;
  v_used_detour numeric:=0;
  v_used_minutes integer:=0;
  v_seq integer;
  v_candidate_fee numeric;
  v_primary_fee numeric;
  v_primary_savings numeric;
  v_candidate_savings numeric;
  v_total_savings numeric;
  v_rider_extra numeric;
  v_result jsonb;
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider' and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true
  for update of r;
  if v_rider is null then raise exception 'active online rider required'; end if;
  if not public.queuego_feature_enabled('route_bundle',now()) then raise exception 'route bundle is disabled'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_rider::text,337));

  select rb.* into v_bundle
  from public.route_bundles rb
  join public.orders po on po.id=rb.primary_order_id
  where rb.rider_id=v_rider and rb.status='ACTIVE'
    and po.status in ('rider_assigned','preparing','ready')
  order by rb.created_at
  limit 1
  for update of rb;

  if v_bundle.id is null then
    select o.* into v_primary
    from public.orders o
    join public.shop_profiles s on s.id=o.shop_id
    where o.rider_id=v_rider
      and o.status in ('rider_assigned','preparing','ready')
      and o.market_order_id is null
      and lower(coalesce(s.public_category,'')) in ('food','cafe','grocery')
    order by o.rider_assigned_at nulls last,o.created_at,o.id
    limit 1
    for update of o;
  else
    select * into v_primary from public.orders where id=v_bundle.primary_order_id for update;
  end if;
  if v_primary.id is null then raise exception 'no bundle-eligible active order'; end if;
  if v_primary.next_after_order_id is not null or exists(select 1 from public.orders where rider_id=v_rider and next_after_order_id is not null and status not in ('completed','cancelled')) then raise exception 'next job conflicts with simultaneous bundle'; end if;

  select * into v_candidate from public.orders where id=p_order_id for update;
  if not found or v_candidate.status<>'searching_rider' or v_candidate.rider_id is not null or v_candidate.market_order_id is not null then
    raise exception 'bundle order no longer available';
  end if;

  select count(*) into v_count
  from public.orders
  where rider_id=v_rider
    and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
  v_max:=greatest(1,least(2,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
  if v_count>=v_max then raise exception 'route bundle order limit reached'; end if;

  if v_bundle.id is not null then
    select coalesce(sum(bundle_detour_km),0),coalesce(sum(bundle_added_minutes),0)
      into v_used_detour,v_used_minutes
    from public.orders
    where route_bundle_id=v_bundle.id and route_bundle_sequence>1 and status<>'cancelled';
  end if;

  v_quote:=public.queuego_route_bundle_quote_orders(v_primary.id,v_candidate.id,now());
  if not coalesce((v_quote->>'eligible')::boolean,false) then raise exception 'route bundle conditions not met'; end if;
  if public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())=1 and not coalesce((v_quote->>'road_eta_verified')::boolean,false) then raise exception 'fresh road route required'; end if;
  if v_used_detour+(v_quote->>'detour_km')::numeric > public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,now())
     or v_used_minutes+(v_quote->>'added_minutes')::integer > public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,now())
  then raise exception 'route bundle cumulative detour limit reached'; end if;

  v_primary_fee:=(v_quote->>'primary_bundled_delivery_fee')::numeric;
  v_candidate_fee:=(v_quote->>'bundled_delivery_fee')::numeric;
  v_primary_savings:=(v_quote->>'primary_customer_savings')::numeric;
  v_candidate_savings:=(v_quote->>'candidate_customer_savings')::numeric;
  v_total_savings:=(v_quote->>'customer_savings')::numeric;
  v_rider_extra:=(v_quote->>'rider_extra_fee')::numeric;

  if v_primary_savings<=0 or v_candidate_savings<=0 or v_total_savings<=0
     or v_primary_fee<0 or v_candidate_fee<0
     or v_candidate_fee>=v_candidate.delivery_fee then
    raise exception 'bundle must save both customers money';
  end if;

  if v_bundle.id is null then
    insert into public.route_bundles(rider_id,primary_order_id,status)
    values(v_rider,v_primary.id,'ACTIVE')
    returning * into v_bundle;
  end if;

  select coalesce(max(route_bundle_sequence),1)+1
    into v_seq
  from public.orders
  where route_bundle_id=v_bundle.id;

  perform set_config('queuego.route_bundle_reprice','on',true);
  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set rider_delivery_payout=coalesce(rider_delivery_payout,bundle_original_delivery_fee,delivery_fee),
      delivery_fee=v_primary_fee,
      total_amount=total_amount-delivery_fee+v_primary_fee,
      route_bundle_id=v_bundle.id,
      route_bundle_sequence=coalesce(route_bundle_sequence,1),
      bundle_original_delivery_fee=coalesce(bundle_original_delivery_fee,delivery_fee),
      bundle_customer_savings=coalesce(bundle_customer_savings,0)+v_primary_savings,
      bundle_rider_extra_fee=coalesce(bundle_rider_extra_fee,0),
      bundle_detour_km=coalesce(bundle_detour_km,0),
      bundle_added_minutes=coalesce(bundle_added_minutes,0),
      bundle_route_sequence=coalesce(bundle_route_sequence,'PRIMARY'),
      updated_at=now()
  where id=v_primary.id;

  update public.payments p
  set amount=o.total_amount,updated_at=now()
  from public.orders o
  where p.order_id=o.id and o.id=v_primary.id and p.status='pending';

  update public.deliveries d
  set delivery_fee=o.delivery_fee,updated_at=now()
  from public.orders o
  where d.order_id=o.id and o.id=v_primary.id
    and d.status not in ('completed','cancelled');

  update public.orders
  set rider_id=v_rider,
      rider_delivery_payout=v_rider_extra,
      status='rider_assigned',
      rider_assigned_at=coalesce(rider_assigned_at,now()),
      delivery_fee=v_candidate_fee,
      total_amount=total_amount-delivery_fee+v_candidate_fee,
      route_bundle_id=v_bundle.id,
      route_bundle_sequence=v_seq,
      bundle_original_delivery_fee=delivery_fee,
      bundle_customer_savings=v_candidate_savings,
      bundle_rider_extra_fee=v_rider_extra,
      bundle_detour_km=(v_quote->>'detour_km')::numeric,
      bundle_added_minutes=(v_quote->>'added_minutes')::integer,
      bundle_route_sequence=v_quote->>'route_sequence',
      note='__QT_ORDER_STATUS__=rider_assigned'||E'\n'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g'),
      updated_at=now()
  where id=v_candidate.id;

  update public.payments p
  set amount=o.total_amount,updated_at=now()
  from public.orders o
  where p.order_id=o.id and o.id=v_candidate.id and p.status='pending';

  update public.deliveries d
  set rider_id=v_rider,status='assigned',delivery_fee=o.delivery_fee,updated_at=now()
  from public.orders o
  where d.order_id=o.id and o.id=v_candidate.id
    and d.status='pending' and d.rider_id is null;

  update public.route_bundles set updated_at=now() where id=v_bundle.id;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    v_user,'route_bundle_claim','order',v_candidate.id,'rider_assigned_bundle_fair_savings',
    jsonb_build_object(
      'bundle_id',v_bundle.id,'primary_order_id',v_primary.id,
      'detour_km',v_quote->'detour_km','added_minutes',v_quote->'added_minutes',
      'rider_extra_fee',v_rider_extra,
      'primary_customer_savings',v_primary_savings,
      'candidate_customer_savings',v_candidate_savings,
      'total_customer_savings',v_total_savings
    )
  );

  if v_primary.customer_id is not null and v_primary_savings>0 then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(
      v_primary.customer_id,
      'ค่าส่งถูกลงจากงานพ่วง',
      'มีงานเส้นทางเดียวกัน ระบบลดค่าส่งให้คุณ ฿'||trim(to_char(v_primary_savings,'FM999999990.00')),
      'order',v_primary.id
    );
  end if;

  if v_candidate.customer_id is not null and v_candidate_savings>0 then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(
      v_candidate.customer_id,
      'ได้ค่าส่งงานพ่วงที่ถูกลง',
      'ออเดอร์ของคุณถูกพ่วงในเส้นทางเดียวกัน ประหยัดค่าส่ง ฿'||trim(to_char(v_candidate_savings,'FM999999990.00')),
      'order',v_candidate.id
    );
  end if;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select sp.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานพ่วงแล้ว สามารถเริ่มเตรียมสินค้าได้','order',v_candidate.id
  from public.shop_profiles sp where sp.id=v_candidate.shop_id;

  v_result:=v_quote || jsonb_build_object(
    'bundle_id',v_bundle.id,
    'bundle_sequence',v_seq,
    'status','rider_assigned'
  );
  return v_result;
end $function$;

CREATE OR REPLACE FUNCTION public.qg_rider_cash_ledger(p_from date, p_to date)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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

  if v_rider is null then
    raise exception 'active rider required';
  end if;

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
  where o.rider_id=v_rider
    and o.order_type='shopping'
    and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to;

  return v_rows;
end
$function$;

revoke all on function public.qg_dispatch_rider_offers() from public,anon,authenticated;
grant execute on function public.qg_dispatch_rider_offers() to service_role;
revoke all on function public.queuego_claim_route_bundle(uuid) from public,anon;
revoke all on function public.qg_rider_cash_ledger(date,date) from public,anon;
grant execute on function public.qg_rider_cash_ledger(date,date) to authenticated;
-- No insertion of dispatch.continuous_enabled=1 or increase of route_bundle.max_orders here.

CREATE OR REPLACE FUNCTION public.qg_rider_period_summary(p_days integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid;v_start timestamptz;v_jobs int;v_income numeric;v_seconds numeric;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_rider is null then raise exception 'active rider required'; end if;
 if p_days not in (1,7,30) then raise exception 'invalid range'; end if;
 v_start:=((now() at time zone 'Asia/Bangkok')::date-(p_days-1))::timestamp at time zone 'Asia/Bangkok';
 select count(*),coalesce(sum(coalesce(o.rider_delivery_payout,o.delivery_fee)),0) into v_jobs,v_income
 from public.orders o join public.deliveries d on d.order_id=o.id
 where o.rider_id=v_rider and o.status='completed' and d.delivered_at>=v_start;
 with events as (select online,occurred_at,lead(occurred_at,1,now()) over(order by occurred_at,id) next_at
  from public.qg_rider_presence_events where rider_id=v_rider)
 select coalesce(sum(extract(epoch from least(next_at,now())-greatest(occurred_at,v_start))),0) into v_seconds
 from events where online and next_at>v_start;
 return jsonb_build_object('days',p_days,'jobs',v_jobs,'income',v_income,'online_hours',round(v_seconds/3600,2),
   'income_per_hour',case when v_seconds>0 then round(v_income/(v_seconds/3600),2) else null end,
   'tracked_since',(select min(occurred_at) from public.qg_rider_presence_events where rider_id=v_rider));
end $function$
;
revoke all on function public.qg_rider_period_summary(integer) from public,anon;
grant execute on function public.qg_rider_period_summary(integer) to authenticated;
