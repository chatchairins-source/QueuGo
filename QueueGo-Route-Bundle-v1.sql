-- QueueGo Route Bundle v1
-- Normal food/cafe/grocery orders only. No Market Trip orders are mixed.
-- Additive tables/columns; existing order state machine remains authoritative.

create table if not exists public.route_bundles (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.rider_profiles(id) on delete restrict,
  primary_order_id uuid not null references public.orders(id) on delete restrict,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','COMPLETED','CANCELLED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(primary_order_id)
);

alter table public.orders
  add column if not exists route_bundle_id uuid references public.route_bundles(id) on delete set null,
  add column if not exists route_bundle_sequence integer,
  add column if not exists bundle_original_delivery_fee numeric,
  add column if not exists bundle_customer_savings numeric not null default 0,
  add column if not exists bundle_rider_extra_fee numeric not null default 0,
  add column if not exists bundle_detour_km numeric not null default 0,
  add column if not exists bundle_added_minutes integer not null default 0,
  add column if not exists bundle_route_sequence text;

create unique index if not exists orders_route_bundle_sequence_uidx
  on public.orders(route_bundle_id,route_bundle_sequence)
  where route_bundle_id is not null and route_bundle_sequence is not null;

create index if not exists orders_route_bundle_idx
  on public.orders(route_bundle_id,status)
  where route_bundle_id is not null;

alter table public.route_bundles enable row level security;

do $$
begin
  if not exists(
    select 1 from pg_policies
    where schemaname='public' and tablename='route_bundles'
      and policyname='route_bundles_rider_read'
  ) then
    create policy route_bundles_rider_read on public.route_bundles
      for select to authenticated
      using (
        rider_id in (
          select rp.id from public.rider_profiles rp
          where rp.user_id=public.get_my_user_id()
        )
        or public.is_active_admin()
      );
  end if;
end $$;

revoke all on public.route_bundles from public,anon,authenticated;
grant select on public.route_bundles to authenticated;
grant all on public.route_bundles to service_role;

create or replace function public.queuego_distance_km(
  p_lat1 double precision,p_lng1 double precision,
  p_lat2 double precision,p_lng2 double precision
) returns numeric
language sql immutable
set search_path to 'public','pg_temp'
as $$
  select case
    when p_lat1 is null or p_lng1 is null or p_lat2 is null or p_lng2 is null then null
    when abs(p_lat1)>90 or abs(p_lat2)>90 or abs(p_lng1)>180 or abs(p_lng2)>180 then null
    else round((
      6371*2*asin(sqrt(least(1,greatest(0,
        power(sin(radians(p_lat2-p_lat1)/2),2)
        + cos(radians(p_lat1))*cos(radians(p_lat2))
        * power(sin(radians(p_lng2-p_lng1)/2),2)
      ))))
    )::numeric,3)
  end;
$$;

revoke all on function public.queuego_distance_km(double precision,double precision,double precision,double precision) from public;
grant execute on function public.queuego_distance_km(double precision,double precision,double precision,double precision) to authenticated,service_role;

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
  extra_fee numeric;
  bundled_fee numeric;
  savings numeric;
  seq text;
begin
  select * into a from public.orders where id=p_primary_order_id;
  select * into b from public.orders where id=p_candidate_order_id;
  if not found or a.id is null or b.id is null or a.id=b.id then
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
  -- Travel-time estimate for eligibility only; Rider UI may show a live Longdo route as a richer estimate.
  delay_min:=greatest(0,ceil(detour_km/25*60)::integer);

  max_detour:=public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,p_at);
  max_delay:=public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,p_at);
  min_extra:=public.queuego_rule_numeric('route_bundle.min_rider_extra_fee',10,p_at);

  -- Rider receives the bundled order's delivery fee. Scale the standalone fee by incremental distance,
  -- but never below the Admin minimum. If that cannot still save the customer money, do not offer it.
  extra_fee:=greatest(
    min_extra,
    ceil(coalesce(b.delivery_fee,0) * least(1,detour_km/greatest(coalesce(b_direct_km,0),0.1)))
  );
  bundled_fee:=least(coalesce(b.delivery_fee,0),extra_fee);
  savings:=greatest(round(coalesce(b.delivery_fee,0)-bundled_fee,2),0);

  return jsonb_build_object(
    'eligible',
      public.queuego_feature_enabled('route_bundle',p_at)
      and detour_km<=max_detour
      and delay_min<=max_delay
      and savings>0
      and bundled_fee>=min_extra,
    'primary_order_id',a.id,
    'candidate_order_id',b.id,
    'detour_km',detour_km,
    'added_minutes',delay_min,
    'standalone_delivery_fee',b.delivery_fee,
    'bundled_delivery_fee',bundled_fee,
    'customer_savings',savings,
    'rider_extra_fee',bundled_fee,
    'route_sequence',seq,
    'max_detour_km',max_detour,
    'max_delay_minutes',max_delay,
    'min_rider_extra_fee',min_extra,
    'quote_method','geo_incremental_v1',
    'quoted_at',p_at
  );
end
$$;

revoke all on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) from public,anon,authenticated;
grant execute on function public.queuego_route_bundle_quote_orders(uuid,uuid,timestamptz) to service_role;

create or replace function public.queuego_route_bundle_candidates()
returns table(
  order_id uuid,
  order_number text,
  shop_id uuid,
  shop_name text,
  pickup_address text,
  delivery_address text,
  pickup_latitude double precision,
  pickup_longitude double precision,
  delivery_latitude double precision,
  delivery_longitude double precision,
  detour_km numeric,
  added_minutes integer,
  standalone_delivery_fee numeric,
  bundled_delivery_fee numeric,
  customer_savings numeric,
  rider_extra_fee numeric,
  route_sequence text
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_primary uuid;
  v_bundle uuid;
  v_count integer;
  v_max integer;
  v_used_detour numeric:=0;
  v_used_minutes integer:=0;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider' and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true;
  if v_rider is null then return; end if;
  if not public.queuego_feature_enabled('route_bundle',now()) then return; end if;

  select rb.id,rb.primary_order_id
    into v_bundle,v_primary
  from public.route_bundles rb
  join public.orders po on po.id=rb.primary_order_id
  where rb.rider_id=v_rider and rb.status='ACTIVE'
    and po.status in ('rider_assigned','preparing','ready')
  order by rb.created_at
  limit 1;

  if v_primary is null then
    select o.id into v_primary
    from public.orders o
    join public.shop_profiles s on s.id=o.shop_id
    where o.rider_id=v_rider
      and o.status in ('rider_assigned','preparing','ready')
      and o.market_order_id is null
      and lower(coalesce(s.public_category,'')) in ('food','cafe','grocery')
    order by o.rider_assigned_at nulls last,o.created_at,o.id
    limit 1;
  end if;
  if v_primary is null then return; end if;

  select count(*) into v_count
  from public.orders
  where rider_id=v_rider
    and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress');
  v_max:=greatest(1,least(5,public.queuego_rule_numeric('route_bundle.max_orders',2,now())::integer));
  if v_count>=v_max then return; end if;

  if v_bundle is not null then
    select coalesce(sum(bundle_detour_km),0),coalesce(sum(bundle_added_minutes),0)
      into v_used_detour,v_used_minutes
    from public.orders
    where route_bundle_id=v_bundle and route_bundle_sequence>1 and status<>'cancelled';
  end if;

  return query
  with candidates as (
    select o.id,o.order_number,o.shop_id,s.shop_name,o.pickup_address,o.delivery_address,
           o.pickup_latitude,o.pickup_longitude,o.delivery_latitude,o.delivery_longitude,
           public.queuego_route_bundle_quote_orders(v_primary,o.id,now()) q
    from public.orders o
    join public.shop_profiles s on s.id=o.shop_id
    where o.status='searching_rider'
      and o.rider_id is null
      and o.order_type='shopping'
      and o.market_order_id is null
      and lower(coalesce(s.public_category,'')) in ('food','cafe','grocery')
      and o.id<>v_primary
  )
  select c.id,c.order_number,c.shop_id,c.shop_name,c.pickup_address,c.delivery_address,
         c.pickup_latitude,c.pickup_longitude,c.delivery_latitude,c.delivery_longitude,
         (c.q->>'detour_km')::numeric,
         (c.q->>'added_minutes')::integer,
         (c.q->>'standalone_delivery_fee')::numeric,
         (c.q->>'bundled_delivery_fee')::numeric,
         (c.q->>'customer_savings')::numeric,
         (c.q->>'rider_extra_fee')::numeric,
         c.q->>'route_sequence'
  from candidates c
  where coalesce((c.q->>'eligible')::boolean,false)
    and v_used_detour+(c.q->>'detour_km')::numeric <= public.queuego_rule_numeric('route_bundle.max_detour_km',1.5,now())
    and v_used_minutes+(c.q->>'added_minutes')::integer <= public.queuego_rule_numeric('route_bundle.max_delay_minutes',10,now())
  order by (c.q->>'detour_km')::numeric,c.order_number
  limit greatest(v_max-v_count,0);
end
$$;

revoke all on function public.queuego_route_bundle_candidates() from public,anon;
grant execute on function public.queuego_route_bundle_candidates() to authenticated,service_role;

create or replace function public.queuego_guard_cash_order()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_role text;v_own_rider uuid;
begin
 if not exists(select 1 from public.queuego_cash_order_locks where order_id=old.id) then return new; end if;
 if current_setting('queuego.market_recalc',true)='on' and old.market_order_id is not null and new.market_order_id=old.market_order_id then return new; end if;
 v_role:=public.get_my_role();
 if v_role='admin' then return new; end if;

 if current_setting('queuego.route_bundle_reprice',true)='on' and v_role='rider' and old.market_order_id is null and new.market_order_id is null then
   select id into v_own_rider from public.rider_profiles where user_id=public.get_my_user_id();
   if v_own_rider is not null
      and new.order_number is not distinct from old.order_number
      and new.customer_id is not distinct from old.customer_id
      and new.shop_id is not distinct from old.shop_id
      and new.order_type is not distinct from old.order_type
      and new.subtotal is not distinct from old.subtotal
      and new.gp_rate is not distinct from old.gp_rate
      and new.gp_amount is not distinct from old.gp_amount
      and new.pickup_address is not distinct from old.pickup_address
      and new.pickup_latitude is not distinct from old.pickup_latitude
      and new.pickup_longitude is not distinct from old.pickup_longitude
      and new.delivery_address is not distinct from old.delivery_address
      and new.delivery_latitude is not distinct from old.delivery_latitude
      and new.delivery_longitude is not distinct from old.delivery_longitude
      and new.created_by is not distinct from old.created_by
      and (
        (old.status='searching_rider' and old.rider_id is null
          and new.status='rider_assigned' and new.rider_id=v_own_rider
          and new.delivery_fee<=old.delivery_fee
          and new.total_amount=old.total_amount-old.delivery_fee+new.delivery_fee
          and new.route_bundle_id is not null
          and new.route_bundle_sequence>1
          and new.bundle_original_delivery_fee=old.delivery_fee
          and new.bundle_customer_savings=old.delivery_fee-new.delivery_fee
          and new.bundle_rider_extra_fee=new.delivery_fee)
        or
        (old.rider_id=v_own_rider and new.rider_id=old.rider_id
          and new.status=old.status
          and new.delivery_fee=old.delivery_fee
          and new.total_amount=old.total_amount
          and old.route_bundle_id is null
          and new.route_bundle_id is not null
          and new.route_bundle_sequence=1
          and new.bundle_customer_savings=0
          and new.bundle_rider_extra_fee=0)
      )
   then return new;
   end if;
 end if;

 if new.order_number is distinct from old.order_number or new.customer_id is distinct from old.customer_id or new.shop_id is distinct from old.shop_id
 or new.order_type is distinct from old.order_type or new.subtotal is distinct from old.subtotal or new.delivery_fee is distinct from old.delivery_fee
 or new.total_amount is distinct from old.total_amount or new.gp_rate is distinct from old.gp_rate or new.gp_amount is distinct from old.gp_amount
 or new.pickup_address is distinct from old.pickup_address or new.pickup_latitude is distinct from old.pickup_latitude or new.pickup_longitude is distinct from old.pickup_longitude
 or new.delivery_address is distinct from old.delivery_address or new.delivery_latitude is distinct from old.delivery_latitude or new.delivery_longitude is distinct from old.delivery_longitude
 or new.created_by is distinct from old.created_by
 or new.route_bundle_id is distinct from old.route_bundle_id
 or new.route_bundle_sequence is distinct from old.route_bundle_sequence
 or new.bundle_original_delivery_fee is distinct from old.bundle_original_delivery_fee
 or new.bundle_customer_savings is distinct from old.bundle_customer_savings
 or new.bundle_rider_extra_fee is distinct from old.bundle_rider_extra_fee
 or new.bundle_detour_km is distinct from old.bundle_detour_km
 or new.bundle_added_minutes is distinct from old.bundle_added_minutes
 or new.bundle_route_sequence is distinct from old.bundle_route_sequence
 then raise exception 'order financial details are locked'; end if;
 if new.rider_id is distinct from old.rider_id and not(v_role='rider' and old.rider_id is null and ((old.status='searching_rider' and new.status='rider_assigned')or(old.status='ready' and new.status='assigned')) and new.rider_id in(select id from public.rider_profiles where user_id=public.get_my_user_id())) then raise exception 'rider assignment is locked'; end if;
 if new.status is distinct from old.status then
  if v_role='customer' then if not(old.status in('pending','searching_rider') and new.status='cancelled' and old.rider_id is null) then raise exception 'customer cannot change order status'; end if;
  elsif v_role='shop' then if not((old.status,new.status) in(('pending','searching_rider'),('rider_assigned','preparing'),('preparing','ready'),('pending','accepted'),('accepted','preparing')) or(new.status='cancelled' and old.status in('pending','accepted','searching_rider','rider_assigned','preparing','ready','assigned'))) then raise exception 'invalid shop order transition'; end if;
  elsif v_role='rider' then if not((old.status,new.status) in(('searching_rider','rider_assigned'),('ready','picked_up'),('picked_up','in_progress'),('in_progress','completed'),('ready','assigned'),('assigned','picked_up')) and(old.rider_id=new.rider_id or(old.rider_id is null and new.rider_id is not null))) then raise exception 'invalid rider order transition'; end if;
  else raise exception 'order status change forbidden'; end if;
 end if; return new;
end
$$;

create or replace function public.queuego_guard_cash_payment()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_order uuid;v_role text;
begin
  v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  if not exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then return case when tg_op='DELETE' then old else new end; end if;
  v_role:=public.get_my_role();
  if v_role='admin' then return case when tg_op='DELETE' then old else new end; end if;

  if current_setting('queuego.route_bundle_reprice',true)='on' and v_role='rider' and tg_op='UPDATE'
     and new.order_id is not distinct from old.order_id
     and new.payer_id is not distinct from old.payer_id
     and new.payment_method is not distinct from old.payment_method
     and new.status is not distinct from old.status
     and old.status='pending'
     and new.amount<=old.amount
     and exists(
       select 1 from public.orders o
       join public.rider_profiles r on r.id=o.rider_id
       where o.id=old.order_id and o.route_bundle_id is not null
         and r.user_id=public.get_my_user_id()
         and new.amount=o.total_amount
     )
  then return new; end if;

  if tg_op<>'UPDATE' then raise exception 'cash payment is locked'; end if;
  if new.order_id is distinct from old.order_id or new.payer_id is distinct from old.payer_id
    or new.amount is distinct from old.amount or new.payment_method is distinct from old.payment_method then
    raise exception 'cash payment amount is locked'; end if;
  if not ((v_role='rider' and old.status='pending' and new.status='paid'
    and exists(select 1 from public.orders o join public.rider_profiles r on r.id=o.rider_id
      where o.id=old.order_id and o.status='completed' and r.user_id=public.get_my_user_id()))
    or (old.status='pending' and new.status='cancelled' and exists(
      select 1 from public.orders o left join public.shop_profiles s on s.id=o.shop_id
      where o.id=old.order_id and o.status='cancelled' and
      ((v_role='customer' and o.customer_id=public.get_my_user_id())
       or (v_role='shop' and s.user_id=public.get_my_user_id()))))) then
    raise exception 'cash payment status change forbidden'; end if;
  return new;
end
$$;

create or replace function public.queuego_guard_cash_delivery()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_order uuid;v_src public.orders%rowtype;v_role text;
begin
 v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;
 v_role:=public.get_my_role();
 if v_role='admin' then return case when tg_op='DELETE' then old else new end; end if;
 if not exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then return case when tg_op='DELETE' then old else new end; end if;
 if tg_op='INSERT' then
   if current_setting('queuego.v22_transition',true) is distinct from 'rpc' then raise exception 'cash delivery is locked'; end if;
   select * into v_src from public.orders where id=new.order_id;
   if not found or new.rider_id is not null or new.status<>'pending'
      or new.delivery_fee is distinct from v_src.delivery_fee
      or new.pickup_address is distinct from v_src.pickup_address
      or new.pickup_latitude is distinct from v_src.pickup_latitude
      or new.pickup_longitude is distinct from v_src.pickup_longitude
      or new.delivery_address is distinct from v_src.delivery_address
      or new.delivery_latitude is distinct from v_src.delivery_latitude
      or new.delivery_longitude is distinct from v_src.delivery_longitude
   then raise exception 'invalid server delivery insert'; end if;
   return new;
 end if;
 if tg_op='DELETE' then raise exception 'cash delivery is locked'; end if;

 if current_setting('queuego.route_bundle_reprice',true)='on' and v_role='rider'
   and new.order_id is not distinct from old.order_id
   and new.distance_km is not distinct from old.distance_km
   and new.pickup_address is not distinct from old.pickup_address
   and new.pickup_latitude is not distinct from old.pickup_latitude
   and new.pickup_longitude is not distinct from old.pickup_longitude
   and new.delivery_address is not distinct from old.delivery_address
   and new.delivery_latitude is not distinct from old.delivery_latitude
   and new.delivery_longitude is not distinct from old.delivery_longitude
   and new.delivery_fee<=old.delivery_fee
   and exists(
     select 1 from public.orders o
     join public.rider_profiles r on r.id=o.rider_id
     where o.id=old.order_id and o.route_bundle_id is not null
       and r.user_id=public.get_my_user_id()
       and new.rider_id=o.rider_id
       and new.delivery_fee=o.delivery_fee
   )
 then return new; end if;

 if new.order_id is distinct from old.order_id or new.delivery_fee is distinct from old.delivery_fee
 or new.distance_km is distinct from old.distance_km
 or new.pickup_address is distinct from old.pickup_address
 or new.pickup_latitude is distinct from old.pickup_latitude or new.pickup_longitude is distinct from old.pickup_longitude
 or new.delivery_address is distinct from old.delivery_address
 or new.delivery_latitude is distinct from old.delivery_latitude or new.delivery_longitude is distinct from old.delivery_longitude
 then raise exception 'cash delivery amount and location are locked'; end if;
 return new;
end
$$;

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
  v_new_fee numeric;
  v_savings numeric;
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

  if v_bundle.id is null then
    insert into public.route_bundles(rider_id,primary_order_id,status)
    values(v_rider,v_primary.id,'ACTIVE')
    returning * into v_bundle;
    perform set_config('queuego.route_bundle_reprice','on',true);
    update public.orders
    set route_bundle_id=v_bundle.id,
        route_bundle_sequence=1,
        bundle_original_delivery_fee=delivery_fee,
        bundle_customer_savings=0,
        bundle_rider_extra_fee=0,
        bundle_detour_km=0,
        bundle_added_minutes=0,
        bundle_route_sequence='PRIMARY'
    where id=v_primary.id;
  end if;

  select coalesce(max(route_bundle_sequence),1)+1
    into v_seq
  from public.orders
  where route_bundle_id=v_bundle.id;

  v_new_fee:=(v_quote->>'bundled_delivery_fee')::numeric;
  v_savings:=(v_quote->>'customer_savings')::numeric;
  if v_new_fee<0 or v_new_fee>=v_candidate.delivery_fee or v_savings<=0 then
    raise exception 'bundle must reduce customer delivery fee';
  end if;

  perform set_config('queuego.route_bundle_reprice','on',true);
  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set rider_id=v_rider,
      status='rider_assigned',
      rider_assigned_at=coalesce(rider_assigned_at,now()),
      delivery_fee=v_new_fee,
      total_amount=total_amount-delivery_fee+v_new_fee,
      route_bundle_id=v_bundle.id,
      route_bundle_sequence=v_seq,
      bundle_original_delivery_fee=delivery_fee,
      bundle_customer_savings=v_savings,
      bundle_rider_extra_fee=v_new_fee,
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
    v_user,'route_bundle_claim','order',v_candidate.id,'rider_assigned_bundle',
    jsonb_build_object(
      'bundle_id',v_bundle.id,'primary_order_id',v_primary.id,
      'detour_km',v_quote->'detour_km','added_minutes',v_quote->'added_minutes',
      'rider_extra_fee',v_new_fee,'customer_savings',v_savings
    )
  );

  if v_candidate.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(
      v_candidate.customer_id,
      'ได้ค่าส่งงานพ่วงที่ถูกลง',
      'ออเดอร์ของคุณถูกพ่วงในเส้นทางเดียวกัน ประหยัดค่าส่ง ฿'||trim(to_char(v_savings,'FM999999990.00')),
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
end
$$;

revoke all on function public.queuego_claim_route_bundle(uuid) from public,anon;
grant execute on function public.queuego_claim_route_bundle(uuid) to authenticated,service_role;

create or replace function public.qg_route_bundle_sync_status()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_bundle uuid;v_remaining integer;
begin
  v_bundle:=coalesce(new.route_bundle_id,old.route_bundle_id);
  if v_bundle is null then return new; end if;
  select count(*) into v_remaining
  from public.orders
  where route_bundle_id=v_bundle and status not in ('completed','cancelled');
  if v_remaining=0 then
    update public.route_bundles
    set status=case when exists(select 1 from public.orders where route_bundle_id=v_bundle and status='completed') then 'COMPLETED' else 'CANCELLED' end,
        updated_at=now()
    where id=v_bundle;
  else
    update public.route_bundles set updated_at=now() where id=v_bundle;
  end if;
  return new;
end
$$;

drop trigger if exists qg_route_bundle_sync_status_trg on public.orders;
create trigger qg_route_bundle_sync_status_trg
after update of status on public.orders
for each row
when (old.status is distinct from new.status)
execute function public.qg_route_bundle_sync_status();

revoke all on function public.qg_route_bundle_sync_status() from public,anon,authenticated;
grant execute on function public.qg_route_bundle_sync_status() to service_role;
