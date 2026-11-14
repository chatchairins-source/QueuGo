-- Authoritative stop plan on EXISTING route_bundles. Live cap remains TWO.
alter table public.route_bundles add column if not exists stop_plan jsonb not null default '[]'::jsonb check(jsonb_typeof(stop_plan)='array' and jsonb_array_length(stop_plan)<=10);
alter table public.route_bundles add column if not exists route_revision bigint not null default 0 check(route_revision>=0);
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

  update public.route_bundles set updated_at=now(),route_revision=route_revision+1,
    stop_plan=(select jsonb_agg(jsonb_build_object(
      'order_id',case when k.key in ('AP','AD') then v_primary.id else v_candidate.id end,
      'kind',case when k.key in ('AP','BP') then 'pickup' else 'dropoff' end
    ) order by k.ordinality)
    from jsonb_array_elements_text(jsonb_build_array('AP') || (v_quote->'planned_stop_keys')) with ordinality k(key,ordinality))
  where id=v_bundle.id;

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

CREATE OR REPLACE FUNCTION public.queuego_route_bundle_active_orders()
 RETURNS TABLE(bundle_id uuid, order_id uuid, order_number text, bundle_sequence integer, drop_rank integer, status text, shop_id uuid, shop_name text, pickup_address text, pickup_latitude double precision, pickup_longitude double precision, delivery_address text, delivery_latitude double precision, delivery_longitude double precision, delivery_fee numeric, original_delivery_fee numeric, customer_savings numeric, rider_extra_fee numeric, detour_km numeric, added_minutes integer, route_sequence text)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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

  select rb.stop_plan,rb.route_revision into v_plan,v_revision from public.route_bundles rb where rb.id=v_bundle;

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
      jsonb_build_object('kind','queuego_stop_plan_v1','revision',v_revision,'customer_name',cu.name,
        'pickup_rank',(select k.ordinality from jsonb_array_elements(v_plan) with ordinality k(s,ordinality) where k.s->>'order_id'=o.id::text and k.s->>'kind'='pickup' limit 1),
        'dropoff_rank',(select k.ordinality from jsonb_array_elements(v_plan) with ordinality k(s,ordinality) where k.s->>'order_id'=o.id::text and k.s->>'kind'='dropoff' limit 1))::text
    else o.bundle_route_sequence end
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  left join public.users cu on cu.id=o.customer_id
  where o.route_bundle_id=v_bundle
    and o.rider_id=v_rider
    and o.status<>'cancelled'
  order by o.route_bundle_sequence,o.created_at,o.id;
end
$function$;
CREATE OR REPLACE FUNCTION public.qg_route_bundle_delivery_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_first uuid;
  v_flip boolean:=false;
  v_plan jsonb;
  v_next uuid;
  v_kind text;
begin
  if old.route_bundle_id is null then return new; end if;
  select rb.stop_plan into v_plan from public.route_bundles rb where rb.id=old.route_bundle_id;
  if jsonb_array_length(coalesce(v_plan,'[]'::jsonb))>0 then
    select o.id,k.s->>'kind' into v_next,v_kind
    from jsonb_array_elements(v_plan) with ordinality k(s,ordinality)
    join public.orders o on o.id=(k.s->>'order_id')::uuid and o.route_bundle_id=old.route_bundle_id
    where o.status not in ('completed','cancelled')
      and ((k.s->>'kind'='pickup' and o.status in ('rider_assigned','preparing','ready','assigned'))
        or k.s->>'kind'='dropoff')
    order by k.ordinality limit 1;
    if (old.status='ready' and new.status='picked_up') or (old.rider_arrived_shop_at is null and new.rider_arrived_shop_at is not null) then
      if v_next is distinct from old.id or v_kind<>'pickup' then raise exception 'follow authoritative pickup stop'; end if;
    elsif (old.status='picked_up' and new.status='in_progress') or (old.status='in_progress' and new.status='completed') then
      if v_next is distinct from old.id or v_kind<>'dropoff' then raise exception 'follow authoritative delivery stop'; end if;
    end if;
    return new;
  end if;
  if old.status is not distinct from new.status then return new; end if;

  -- Pickup remains per order. Delivery may start only after every live order
  -- in the bundle has been picked up, and only one customer is served at a time.
  if old.status='picked_up' and new.status='in_progress' then
    if exists(
      select 1 from public.orders o
      where o.route_bundle_id=old.route_bundle_id
        and o.status not in ('picked_up','completed','cancelled')
        and o.id<>old.id
    ) then
      raise exception 'all bundled pickups must be completed first';
    end if;

    if exists(
      select 1 from public.orders o
      where o.route_bundle_id=old.route_bundle_id
        and o.status='in_progress'
        and o.id<>old.id
    ) then
      raise exception 'finish current bundled delivery first';
    end if;

    select exists(
      select 1 from public.orders o
      where o.route_bundle_id=old.route_bundle_id
        and o.route_bundle_sequence=2
        and o.bundle_route_sequence='BUNDLE_DROP_THEN_PRIMARY_DROP'
        and o.status<>'cancelled'
    ) into v_flip;

    select o.id into v_first
    from public.orders o
    where o.route_bundle_id=old.route_bundle_id
      and o.status='picked_up'
    order by
      case
        when v_flip and o.route_bundle_sequence=2 then 1
        when v_flip and o.route_bundle_sequence=1 then 2
        else o.route_bundle_sequence
      end,
      o.created_at,o.id
    limit 1;

    if v_first is distinct from old.id then
      raise exception 'deliver bundled orders in route sequence';
    end if;
  end if;

  return new;
end
$function$;
revoke all on function public.queuego_route_bundle_active_orders() from public,anon;
grant execute on function public.queuego_route_bundle_active_orders() to authenticated;
-- Existing route_bundles RLS permits Rider/Admin SELECT only; there is no client plan-write policy.
-- Broaden the SAME existing trigger to validate pickup arrival as well as status changes.
drop trigger if exists qg_route_bundle_delivery_guard_trg on public.orders;
create trigger qg_route_bundle_delivery_guard_trg before update of status,rider_arrived_shop_at on public.orders
 for each row when (old.route_bundle_id is not null and (old.status is distinct from new.status or old.rider_arrived_shop_at is distinct from new.rider_arrived_shop_at))
 execute function public.qg_route_bundle_delivery_guard();

create or replace function qg_private.validate_bundle_stop_plan() returns trigger language plpgsql
set search_path=public,pg_temp as $$
declare k record;v_fixed integer:=0;v_done boolean;v_orders integer;
begin
 if TG_OP='UPDATE' and new.stop_plan is not distinct from old.stop_plan then
  if new.route_revision<old.route_revision then raise exception 'route revision cannot regress'; end if;
  return new;
 end if;
 if jsonb_array_length(new.stop_plan)=0 then
  if TG_OP='UPDATE' and jsonb_array_length(old.stop_plan)>0 then raise exception 'cannot clear committed route'; end if;
  return new;
 end if;
 if exists(select 1 from jsonb_array_elements(new.stop_plan) p where coalesce(p->>'order_id','')!~'^[0-9a-fA-F-]{36}$' or coalesce(p->>'kind','') not in ('pickup','dropoff')) then raise exception 'invalid route stop'; end if;
 select count(distinct p->>'order_id') into v_orders from jsonb_array_elements(new.stop_plan) p;
 if v_orders>5 or v_orders>public.queuego_rule_numeric('route_bundle.max_orders',2,now()) then raise exception 'route plan capacity exceeded'; end if;
 if exists(select 1 from jsonb_array_elements(new.stop_plan) p group by p->>'order_id',p->>'kind' having count(*)<>1)
  or exists(select 1 from jsonb_array_elements(new.stop_plan) p group by p->>'order_id' having count(*)<>2)
  or exists(select 1 from jsonb_array_elements(new.stop_plan) p where not exists(select 1 from public.orders o where o.id=(p->>'order_id')::uuid and o.route_bundle_id=new.id and o.rider_id=new.rider_id))
  or exists(select 1 from public.orders o where o.route_bundle_id=new.id and o.status<>'cancelled' and not exists(select 1 from jsonb_array_elements(new.stop_plan) p where p->>'order_id'=o.id::text))
 then raise exception 'route plan must preserve every separate order'; end if;
 if exists(select 1 from jsonb_array_elements(new.stop_plan) with ordinality p(s,n)
  join jsonb_array_elements(new.stop_plan) with ordinality d(s,n) on d.s->>'order_id'=p.s->>'order_id' and d.s->>'kind'='dropoff'
  where p.s->>'kind'='pickup' and p.n>=d.n) then raise exception 'pickup must precede dropoff'; end if;
 if TG_OP='UPDATE' then
  if new.route_revision<>old.route_revision+1 then raise exception 'route revision conflict'; end if;
  for k in select p.s,p.n from jsonb_array_elements(old.stop_plan) with ordinality p(s,n) order by p.n loop
   select case when k.s->>'kind'='pickup' then o.status in ('picked_up','in_progress','completed','cancelled') else o.status in ('completed','cancelled') end
    into v_done from public.orders o where o.id=(k.s->>'order_id')::uuid;
   v_fixed:=v_fixed+1;
   exit when not coalesce(v_done,false);
  end loop;
  if exists(select 1 from generate_series(0,v_fixed-1) i where new.stop_plan->i is distinct from old.stop_plan->i) then
   raise exception 'committed stops cannot be reordered';
  end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.validate_bundle_stop_plan() from public,anon,authenticated;
create trigger route_bundles_validate_stop_plan before insert or update of stop_plan,route_revision on public.route_bundles
 for each row execute function qg_private.validate_bundle_stop_plan();
