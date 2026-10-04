-- QueueGo Route Bundle fair savings
-- Share the route-efficiency saving between the primary customer and the bundled customer
-- without reducing total Rider delivery income. Admin controls the split percentage.

create or replace function public.queuego_platform_rule_guard()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $$
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
    'route_bundle.primary_savings_percent'
  ) then
    if jsonb_typeof(new.value) <> 'number' then raise exception 'pricing rule must be numeric'; end if;
    n := (new.value #>> '{}')::numeric;
    if n < 0 then raise exception 'pricing rule cannot be negative'; end if;
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
end $$;

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'route_bundle.primary_savings_percent','50'::jsonb,'1970-01-01 00:00:00+00',
       'Share route-bundle savings 50/50 between primary and bundled customers',null
where not exists(
  select 1 from public.queuego_platform_rules
  where rule_key='route_bundle.primary_savings_percent'
);

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
  rider_incremental numeric;
  raw_savings numeric;
  split_percent numeric;
  primary_savings numeric;
  candidate_savings numeric;
  candidate_fee numeric;
  primary_fee_after numeric;
  seq text;
begin
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
  if least(a.pickup_latitude,a.pickup_longitude,a.delivery_latitude,a.delivery_longitude,
           b.pickup_latitude,b.pickup_longitude,b.delivery_latitude,b.delivery_longitude) is null then
    return jsonb_build_object('eligible',false,'reason','missing route coordinates');
  end if;

  base_km:=public.queuego_distance_km(a.pickup_latitude,a.pickup_longitude,a.delivery_latitude,a.delivery_longitude);
  b_direct_km:=public.queuego_distance_km(b.pickup_latitude,b.pickup_longitude,b.delivery_latitude,b.delivery_longitude);

  route_a_km:=
    public.queuego_distance_km(a.pickup_latitude,a.pickup_longitude,b.pickup_latitude,b.pickup_longitude)
    + public.queuego_distance_km(b.pickup_latitude,b.pickup_longitude,a.delivery_latitude,a.delivery_longitude)
    + public.queuego_distance_km(a.delivery_latitude,a.delivery_longitude,b.delivery_latitude,b.delivery_longitude);

  route_b_km:=
    public.queuego_distance_km(a.pickup_latitude,a.pickup_longitude,b.pickup_latitude,b.pickup_longitude)
    + public.queuego_distance_km(b.pickup_latitude,b.pickup_longitude,b.delivery_latitude,b.delivery_longitude)
    + public.queuego_distance_km(b.delivery_latitude,b.delivery_longitude,a.delivery_latitude,a.delivery_longitude);

  if route_a_km<=route_b_km then
    bundled_km:=route_a_km;seq:='PRIMARY_DROP_THEN_BUNDLE_DROP';
  else
    bundled_km:=route_b_km;seq:='BUNDLE_DROP_THEN_PRIMARY_DROP';
  end if;

  detour_km:=greatest(round(bundled_km-base_km,3),0);
  delay_min:=greatest(0,ceil(detour_km/25*60)::integer);

  max_detour:=public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,p_at);
  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  min_extra:=public.queuego_rule_numeric('route_bundle.min_rider_extra_fee',10,p_at);
  split_percent:=public.queuego_rule_numeric('route_bundle.primary_savings_percent',50,p_at);

  -- True incremental Rider compensation before sharing customer savings.
  rider_incremental:=greatest(
    min_extra,
    ceil(coalesce(b.delivery_fee,0) * least(1,detour_km/greatest(coalesce(b_direct_km,0),0.1)))
  );
  rider_incremental:=least(coalesce(b.delivery_fee,0),rider_incremental);
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
    'quote_method','geo_incremental_fair_share_v2',
    'quoted_at',p_at
  );
end $$;

create or replace function public.queuego_claim_route_bundle(p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
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

  select * into v_candidate from public.orders where id=p_order_id for update;
  if not found or v_candidate.status<>'searching_rider' or v_candidate.rider_id is not null or v_candidate.market_order_id is not null then
    raise exception 'bundle order no longer available';
  end if;

  select count(*) into v_count
  from public.orders
  where rider_id=v_rider
    and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
  v_max:=greatest(1,least(5,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
  if v_count>=v_max then raise exception 'route bundle order limit reached'; end if;

  if v_bundle.id is not null then
    select coalesce(sum(bundle_detour_km),0),coalesce(sum(bundle_added_minutes),0)
      into v_used_detour,v_used_minutes
    from public.orders
    where route_bundle_id=v_bundle.id and route_bundle_sequence>1 and status<>'cancelled';
  end if;

  v_quote:=public.queuego_route_bundle_quote_orders(v_primary.id,v_candidate.id,now());
  if not coalesce((v_quote->>'eligible')::boolean,false) then raise exception 'route bundle conditions not met'; end if;
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
  set delivery_fee=v_primary_fee,
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
end $$;

revoke all on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) from public,anon;
grant execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) to authenticated,service_role;
revoke all on function public.queuego_claim_route_bundle(uuid) from public,anon;
grant execute on function public.queuego_claim_route_bundle(uuid) to authenticated,service_role;
