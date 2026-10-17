-- QueueGo Passenger Ride production-compatible activation.
-- Reuses existing Orders, targeted 30-second offers, Rider sessions, cash payments and service-area rules.
-- Initial fare policy uses the Thai public-motorcycle ceiling requested by the owner:
-- <=2 km: 25 THB; >2 to 5 km: +5 THB/km; >5 km: 10 THB/km from km 1.
-- QueueGo platform fee starts at 0%.

alter table public.orders drop constraint if exists orders_fulfillment_vertical_check;
alter table public.orders add constraint orders_fulfillment_vertical_check
  check(fulfillment_vertical in ('food','market','grocery','ride'));

alter table public.orders add column if not exists ride_distance_km numeric;
alter table public.orders add column if not exists ride_duration_minutes numeric;
alter table public.orders add column if not exists ride_platform_fee numeric;
alter table public.orders add column if not exists rider_delivery_payout numeric;

alter table public.orders drop constraint if exists orders_ride_shape_check;
alter table public.orders add constraint orders_ride_shape_check check(
 fulfillment_vertical<>'ride' or (
   order_type='service' and shop_id is null and market_order_id is null and route_bundle_id is null
   and subtotal=0 and delivery_fee>0 and total_amount=delivery_fee
   and ride_distance_km is not null and ride_distance_km>0
   and ride_duration_minutes is not null and ride_duration_minutes>0
   and ride_platform_fee is not null and ride_platform_fee>=0 and ride_platform_fee<=delivery_fee
   and rider_delivery_payout is not null and rider_delivery_payout>=0
 )
);
create index if not exists orders_active_ride_rider_idx
  on public.orders(rider_id)
  where fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available');
create unique index if not exists orders_one_active_ride_customer_idx
  on public.orders(customer_id)
  where fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available');

create or replace function qg_private.ride_owner_authorized(p_rider uuid)
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(
   select 1 from public.rider_profiles r
   join public.users u on u.id=r.user_id
   where r.id=p_rider
     and r.status='active'
     and u.role='rider'
     and u.status='active'
     and coalesce((r.metadata->>'rideAccessAuthorized')::boolean,false)
 )
$$;
revoke all on function qg_private.ride_owner_authorized(uuid) from public,anon,authenticated;

create table if not exists qg_private.ride_quotes(
 id uuid primary key default gen_random_uuid(),
 customer_id uuid not null references public.users(id),
 pickup_address text not null,
 delivery_address text not null,
 pickup_latitude double precision not null,
 pickup_longitude double precision not null,
 delivery_latitude double precision not null,
 delivery_longitude double precision not null,
 distance_km numeric not null check(distance_km>0),
 duration_minutes numeric not null check(duration_minutes>0),
 fare numeric not null check(fare>0),
 platform_fee numeric not null check(platform_fee>=0 and platform_fee<=fare),
 expires_at timestamptz not null,
 created_at timestamptz not null default now(),
 order_id uuid unique references public.orders(id)
);
create index if not exists ride_quotes_customer_idx on qg_private.ride_quotes(customer_id,expires_at);
create index if not exists ride_quotes_expiry_idx on qg_private.ride_quotes(expires_at);
alter table qg_private.ride_quotes enable row level security;
revoke all on qg_private.ride_quotes from public,anon,authenticated;
grant all on qg_private.ride_quotes to service_role;

create or replace function public.queuego_platform_rule_guard()
returns trigger language plpgsql set search_path=public,pg_temp as $$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry','feature.ride') then
    if jsonb_typeof(new.value)<>'boolean' then raise exception 'feature rule must be boolean'; end if;
  elsif new.rule_key in (
    'ride.base_fare','ride.included_km','ride.per_km','ride.minimum_fare',
    'ride.long_distance_threshold_km','ride.long_distance_per_km','ride.platform_fee_percent',
    'pricing.gp_default_rate','pricing.market_base_fee','pricing.market_distance_step_fee',
    'pricing.market_second_shop_fee','pricing.market_additional_shop_fee',
    'route_bundle.max_detour_km','route_bundle.max_delay_minutes','route_bundle.max_orders',
    'route_bundle.min_rider_extra_fee','route_bundle.primary_savings_percent',
    'route_bundle.incremental_km_rate','route_bundle.pickup_cost','route_bundle.dropoff_cost','route_bundle.minute_rate'
  ) then
    if jsonb_typeof(new.value)<>'number' then raise exception 'pricing rule must be numeric'; end if;
    n:=(new.value#>>'{}')::numeric;
    if n<0 then raise exception 'pricing rule cannot be negative'; end if;
    if new.rule_key='ride.platform_fee_percent' and n>=100 then raise exception 'ride platform fee must be less than fare'; end if;
    if new.rule_key='pricing.gp_default_rate' and n>100 then raise exception 'GP rate must be 0-100'; end if;
    if new.rule_key='route_bundle.primary_savings_percent' and (n<1 or n>99) then raise exception 'route bundle primary savings percent must be 1-99'; end if;
    if new.rule_key='route_bundle.max_orders' and (n<1 or n>5 or n<>trunc(n)) then raise exception 'route bundle max orders must be integer 1-5'; end if;
    if new.rule_key='route_bundle.max_delay_minutes' and n>240 then raise exception 'route bundle delay too large'; end if;
    if new.rule_key='route_bundle.max_detour_km' and n>50 then raise exception 'route bundle detour too large'; end if;
  else
    raise exception 'unsupported QueueGo platform rule: %',new.rule_key;
  end if;
  new.created_at:=coalesce(new.created_at,now());
  return new;
end $$;

create or replace function public.queuego_ride_config()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare b numeric;k numeric;r numeric;m numeric;t numeric;l numeric;g numeric;
begin
 b:=public.queuego_rule_numeric('ride.base_fare',-1,now());
 k:=public.queuego_rule_numeric('ride.included_km',-1,now());
 r:=public.queuego_rule_numeric('ride.per_km',-1,now());
 m:=public.queuego_rule_numeric('ride.minimum_fare',-1,now());
 t:=public.queuego_rule_numeric('ride.long_distance_threshold_km',-1,now());
 l:=public.queuego_rule_numeric('ride.long_distance_per_km',-1,now());
 g:=public.queuego_rule_numeric('ride.platform_fee_percent',-1,now());
 return jsonb_build_object(
   'enabled',public.queuego_feature_enabled('ride',now()),
   'configured',b>=0 and k>=0 and r>=0 and m>0 and t>k and l>=0 and g>=0 and g<100,
   'base_fare',b,'included_km',k,'per_km',r,'minimum_fare',m,
   'long_distance_threshold_km',t,'long_distance_per_km',l,'platform_fee_percent',g,
   'tariff','TH_PUBLIC_MOTORCYCLE_CEILING'
 );
end $$;
revoke all on function public.queuego_ride_config() from public;
grant execute on function public.queuego_ride_config() to authenticated,service_role;

create or replace function qg_private.ride_fare(p_distance_km numeric)
returns numeric language plpgsql stable security definer set search_path=public,pg_temp as $$
declare c jsonb;d numeric;fare numeric;
begin
 c:=public.queuego_ride_config();
 if not coalesce((c->>'enabled')::boolean,false) or not coalesce((c->>'configured')::boolean,false) then
   raise exception 'ride service is not configured or enabled';
 end if;
 d:=greatest(coalesce(p_distance_km,0),0);
 if d<=0 then raise exception 'valid ride distance required'; end if;
 if d<=(c->>'included_km')::numeric then
   fare:=(c->>'base_fare')::numeric;
 elsif d<=(c->>'long_distance_threshold_km')::numeric then
   fare:=(c->>'base_fare')::numeric+(d-(c->>'included_km')::numeric)*(c->>'per_km')::numeric;
 else
   fare:=d*(c->>'long_distance_per_km')::numeric;
 end if;
 return round(greatest((c->>'minimum_fare')::numeric,fare),2);
end $$;
revoke all on function qg_private.ride_fare(numeric) from public,anon,authenticated;

create or replace function public.queuego_ride_quote_context(
 p_auth_user_id uuid,p_pickup_lat double precision,p_pickup_lng double precision,
 p_delivery_lat double precision,p_delivery_lng double precision
) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare c jsonb;u uuid;a jsonb;
begin
 select id into u from public.users where auth_user_id=p_auth_user_id and role='customer' and status='active';
 if u is null then raise exception 'active customer required'; end if;
 c:=public.queuego_ride_config();
 if not coalesce((c->>'enabled')::boolean,false) or not coalesce((c->>'configured')::boolean,false) then raise exception 'ride unavailable'; end if;
 if exists(select 1 from public.orders where customer_id=u and fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available')) then raise exception 'finish current ride first'; end if;
 if (select count(*) from qg_private.ride_quotes where customer_id=u and created_at>now()-interval '1 minute')>=5 then raise exception 'wait before requesting another quote'; end if;
 a:=public.queuego_service_area_route_status(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,'ride');
 if not coalesce((a->>'available')::boolean,false) then raise exception 'outside ride service area'; end if;
 return jsonb_build_object('available',true);
end $$;
revoke all on function public.queuego_ride_quote_context(uuid,double precision,double precision,double precision,double precision) from public,anon,authenticated;
grant execute on function public.queuego_ride_quote_context(uuid,double precision,double precision,double precision,double precision) to service_role;

create or replace function public.queuego_cache_ride_quote(
 p_auth_user_id uuid,p_pickup_address text,p_delivery_address text,
 p_pickup_lat double precision,p_pickup_lng double precision,p_delivery_lat double precision,p_delivery_lng double precision,
 p_distance_km numeric,p_duration_minutes numeric
) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare c jsonb;v_customer uuid;v_quote qg_private.ride_quotes%rowtype;v_fare numeric;v_area jsonb;
begin
 select id into v_customer from public.users where auth_user_id=p_auth_user_id and role='customer' and status='active';
 if v_customer is null then raise exception 'active customer required'; end if;
 c:=public.queuego_ride_config();
 if not coalesce((c->>'enabled')::boolean,false) or not coalesce((c->>'configured')::boolean,false) then raise exception 'ride service is not configured or enabled'; end if;
 if p_pickup_address is null or p_delivery_address is null or length(trim(p_pickup_address)) not between 3 and 500 or length(trim(p_delivery_address)) not between 3 and 500 then raise exception 'pickup and destination required'; end if;
 v_area:=public.queuego_service_area_route_status(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,'ride');
 if not coalesce((v_area->>'available')::boolean,false) then raise exception 'ride outside service area'; end if;
 if p_distance_km is null or p_duration_minutes is null or p_distance_km<=0 or p_distance_km>300 or p_duration_minutes<=0 or p_duration_minutes>600
    or p_distance_km<public.queuego_distance_km(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng)*0.9 then
   raise exception 'verified road distance and duration required';
 end if;
 v_fare:=qg_private.ride_fare(p_distance_km);
 insert into qg_private.ride_quotes(
   customer_id,pickup_address,delivery_address,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,
   distance_km,duration_minutes,fare,platform_fee,expires_at
 ) values(
   v_customer,trim(p_pickup_address),trim(p_delivery_address),p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,
   p_distance_km,p_duration_minutes,v_fare,round(v_fare*(c->>'platform_fee_percent')::numeric/100,2),now()+interval '2 minutes'
 ) returning * into v_quote;
 return jsonb_build_object('quote_id',v_quote.id,'fare',v_quote.fare,'distance_km',v_quote.distance_km,'duration_minutes',v_quote.duration_minutes,'expires_at',v_quote.expires_at,'payment_method','cash');
end $$;
revoke all on function public.queuego_cache_ride_quote(uuid,text,text,double precision,double precision,double precision,double precision,numeric,numeric) from public,anon,authenticated;
grant execute on function public.queuego_cache_ride_quote(uuid,text,text,double precision,double precision,double precision,double precision,numeric,numeric) to service_role;

create or replace function public.queuego_request_ride(p_request_id uuid,p_quote_id uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_customer uuid;q qg_private.ride_quotes%rowtype;o public.orders%rowtype;
begin
 perform qg_private.assert_live_session();
 select id into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
 if v_customer is null or p_request_id is null then raise exception 'active customer and request required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_customer::text,7361));
 select * into q from qg_private.ride_quotes where id=p_quote_id and customer_id=v_customer for update;
 if not found then raise exception 'quote unavailable'; end if;
 if q.order_id is not null then
   if q.order_id<>p_request_id then raise exception 'quote already used by another request'; end if;
   select * into o from public.orders where id=q.order_id and customer_id=v_customer;
   return jsonb_build_object('id',o.id,'order_number',o.order_number,'status',o.status,'fare',o.total_amount);
 end if;
 if not public.queuego_feature_enabled('ride',now()) or q.expires_at<=now() then raise exception 'ride disabled or quote expired'; end if;
 if exists(select 1 from public.orders where customer_id=v_customer and fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available')) then raise exception 'finish current ride first'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 perform set_config('queuego.ride_action','rpc',true);
 insert into public.orders(
   id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,gp_rate,gp_amount,
   pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,
   ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,rider_search_started_at
 ) values(
   p_request_id,public.qg_next_order_number(),v_customer,'service','ride','searching_rider',0,q.fare,q.fare,0,0,
   q.pickup_address,q.pickup_latitude,q.pickup_longitude,q.delivery_address,q.delivery_latitude,q.delivery_longitude,
   q.distance_km,q.duration_minutes,q.platform_fee,q.fare-q.platform_fee,now()
 ) returning * into o;
 insert into public.payments(order_id,amount,payment_method,status) values(o.id,o.total_amount,'cash','pending');
 insert into public.deliveries(order_id,status,delivery_fee,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude)
 values(o.id,'pending',o.delivery_fee,o.pickup_address,o.pickup_latitude,o.pickup_longitude,o.delivery_address,o.delivery_latitude,o.delivery_longitude);
 insert into public.queuego_cash_order_locks(order_id) values(o.id);
 update qg_private.ride_quotes set order_id=o.id where id=q.id;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_customer,'ride_requested','order',o.id,'Passenger ride requested',jsonb_build_object('quote_id',q.id,'fare',q.fare,'payment_method','cash'));
 return jsonb_build_object('id',o.id,'order_number',o.order_number,'status',o.status,'fare',o.total_amount);
end $$;
revoke all on function public.queuego_request_ride(uuid,uuid) from public,anon;
grant execute on function public.queuego_request_ride(uuid,uuid) to authenticated;

create or replace function qg_private.guard_ride_order_write()
returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if tg_op='UPDATE' and old.fulfillment_vertical='ride' then
   if current_setting('queuego.ride_action',true) is distinct from 'rpc' and not public.is_active_admin() then
     raise exception 'passenger ride transition requires authoritative RPC';
   end if;
   if new.fulfillment_vertical is distinct from old.fulfillment_vertical
      or new.customer_id is distinct from old.customer_id
      or new.shop_id is distinct from old.shop_id
      or new.subtotal is distinct from old.subtotal
      or new.delivery_fee is distinct from old.delivery_fee
      or new.total_amount is distinct from old.total_amount
      or new.ride_distance_km is distinct from old.ride_distance_km
      or new.ride_duration_minutes is distinct from old.ride_duration_minutes
      or new.ride_platform_fee is distinct from old.ride_platform_fee
      or new.rider_delivery_payout is distinct from old.rider_delivery_payout
   then raise exception 'passenger ride snapshot is immutable'; end if;
   if new.status is distinct from old.status and not (
      (old.status='searching_rider' and new.status in ('rider_assigned','cancelled','no_rider_available'))
      or (old.status='rider_assigned' and new.status='in_progress' and old.rider_arrived_shop_at is not null)
      or (old.status='in_progress' and new.status='completed' and old.rider_arrived_customer_at is not null)
   ) then raise exception 'invalid passenger ride transition'; end if;
 end if;
 return new;
end $$;
drop trigger if exists qg_guard_ride_order_write on public.orders;
create trigger qg_guard_ride_order_write
 before update on public.orders for each row execute function qg_private.guard_ride_order_write();

create or replace function qg_private.ride_order_action(p_order_id uuid,p_action text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_rider uuid;v_user uuid;o public.orders%rowtype;v_action text:=lower(trim(coalesce(p_action,'')));v_next text;
begin
 perform qg_private.assert_live_session();
 select r.id,r.user_id into v_rider,v_user
 from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 for update of r;
 if v_rider is null or not qg_private.ride_owner_authorized(v_rider) then raise exception 'passenger ride access required'; end if;
 select * into o from public.orders where id=p_order_id and rider_id=v_rider and fulfillment_vertical='ride' for update;
 if not found then raise exception 'assigned passenger ride required'; end if;
 if o.status='completed' then return jsonb_build_object('status',o.status,'fare',o.total_amount); end if;
 perform set_config('queuego.v22_transition','rpc',true);
 perform set_config('queuego.ride_action','rpc',true);
 if v_action='arrive_pickup' and o.status='rider_assigned' then
   if o.rider_arrived_shop_at is null then update public.orders set rider_arrived_shop_at=now(),updated_at=now() where id=o.id; end if;
 elsif v_action='start' and o.status='rider_assigned' and o.rider_arrived_shop_at is not null then
   update public.orders set status='in_progress',picked_up_at=coalesce(picked_up_at,now()),delivering_at=coalesce(delivering_at,now()),updated_at=now() where id=o.id;
   update public.deliveries set status='picked_up',updated_at=now() where order_id=o.id;
 elsif v_action='arrive_dropoff' and o.status='in_progress' then
   if o.rider_arrived_customer_at is null then update public.orders set rider_arrived_customer_at=now(),updated_at=now() where id=o.id; end if;
 elsif v_action='complete' and o.status='in_progress' and o.rider_arrived_customer_at is not null then
   update public.payments set status='paid',paid_at=coalesce(paid_at,now()),updated_at=now() where order_id=o.id and payment_method='cash' and status='pending';
   if not found and not exists(select 1 from public.payments where order_id=o.id and payment_method='cash' and status='paid') then raise exception 'cash payment record required'; end if;
   update public.orders set status='completed',completed_at=coalesce(completed_at,now()),updated_at=now() where id=o.id;
   update public.deliveries set status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now() where order_id=o.id;
 elsif v_action='start' and o.status='in_progress' then
   return jsonb_build_object('status',o.status,'fare',o.total_amount);
 else
   raise exception 'ride stage changed; refresh';
 end if;
 select status into v_next from public.orders where id=o.id;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_user,'ride_order_action','order',o.id,v_action,jsonb_build_object('from',o.status,'to',v_next,'fare',o.total_amount));
 insert into public.notifications(user_id,title,message,type,reference_id)
 values(o.customer_id,'อัปเดตการเดินทาง',
   case v_action when 'arrive_pickup' then 'คนขับถึงจุดรับแล้ว'
                 when 'start' then 'เริ่มการเดินทางแล้ว'
                 when 'arrive_dropoff' then 'ถึงจุดหมายแล้ว'
                 else 'จบการเดินทางแล้ว · ชำระเงินสด ฿'||o.total_amount end,
   'order',o.id);
 return jsonb_build_object('status',v_next,'fare',o.total_amount);
end $$;
revoke all on function qg_private.ride_order_action(uuid,text) from public,anon,authenticated;

create or replace function public.queuego_ride_state(p_order_id uuid default null)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_user uuid;v_role text;v_rider uuid;v_rows jsonb;
begin
 perform qg_private.assert_live_session();
 select id,role into v_user,v_role from public.users where auth_user_id=auth.uid() and status='active';
 if v_role not in ('customer','rider','admin') or v_user is null then raise exception 'active account required'; end if;
 if v_role='rider' then select id into v_rider from public.rider_profiles where user_id=v_user; end if;
 select coalesce(jsonb_agg(x.data order by x.created_at desc),'[]'::jsonb) into v_rows from (
   select o.created_at,jsonb_build_object(
     'id',o.id,'order_number',o.order_number,'status',o.status,'rider_id',o.rider_id,
     'pickup_address',o.pickup_address,'pickup_latitude',o.pickup_latitude,'pickup_longitude',o.pickup_longitude,
     'delivery_address',o.delivery_address,'delivery_latitude',o.delivery_latitude,'delivery_longitude',o.delivery_longitude,
     'fare',o.total_amount,'rider_payout',o.rider_delivery_payout,'distance_km',o.ride_distance_km,'duration_minutes',o.ride_duration_minutes,
     'arrived_pickup_at',o.rider_arrived_shop_at,'arrived_dropoff_at',o.rider_arrived_customer_at,
     'customer_name',cu.name,'rider_name',ru.name,
     'rider_latitude',case when o.status in ('rider_assigned','in_progress') then r.latitude end,
     'rider_longitude',case when o.status in ('rider_assigned','in_progress') then r.longitude end
   ) data
   from public.orders o
   left join public.users cu on cu.id=o.customer_id
   left join public.rider_profiles r on r.id=o.rider_id
   left join public.users ru on ru.id=r.user_id
   where o.fulfillment_vertical='ride' and (p_order_id is null or o.id=p_order_id)
     and (v_role='admin' or (v_role='customer' and o.customer_id=v_user) or (v_role='rider' and o.rider_id=v_rider))
   order by o.created_at desc limit 30
 ) x;
 return jsonb_build_object('rides',v_rows,'public_license_valid',case when v_role='rider' then qg_private.ride_owner_authorized(v_rider) else null end);
end $$;
revoke all on function public.queuego_ride_state(uuid) from public,anon;
grant execute on function public.queuego_ride_state(uuid) to authenticated;

create or replace function public.qg_rider_public_license_status()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_rider uuid;v_ok boolean;
begin
 perform qg_private.assert_live_session();
 select r.id into v_rider from public.rider_profiles r
 join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 order by r.updated_at desc nulls last,r.id limit 1;
 if v_rider is null then raise exception 'active rider required'; end if;
 v_ok:=qg_private.ride_owner_authorized(v_rider);
 return jsonb_build_object(
   'valid',v_ok,
   'status',case when v_ok then 'approved' else 'missing' end,
   'expires_on',null,
   'submitted_at',null,
   'reviewed_at',null,
   'review_note',case when v_ok then 'Owner-authorized pilot passenger access' else null end,
   'has_evidence',false
 );
end $$;
revoke all on function public.qg_rider_public_license_status() from public,anon;
grant execute on function public.qg_rider_public_license_status() to authenticated,service_role;

alter policy rider_orders_offered_select on public.orders to authenticated using (
 public.get_my_role()='rider'
 and (order_type='shopping' or (order_type='service' and fulfillment_vertical='ride'))
 and status='searching_rider' and rider_id is null
 and (select public.qg_has_live_rider_offer(orders.id))
);


CREATE OR REPLACE FUNCTION public.queuego_reject_closed_shop_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_open boolean;v_resume timestamptz;v_now timestamp;v_special public.shop_special_hours%rowtype;v_hours public.shop_business_hours%rowtype;
begin
 if new.fulfillment_vertical='ride' then return new; end if;
 if new.order_type in ('DINE_IN','TAKEAWAY') then return new; end if;
 if not exists(select 1 from public.shop_profiles where id=new.shop_id and delivery_enabled) then raise exception 'shop has not enabled Delivery'; end if;
 select s.is_open,s.resume_at into v_open,v_resume from public.shop_open_states s where s.shop_id=new.shop_id;
 if found and not v_open and (v_resume is null or v_resume>now()) then raise exception 'ร้านปิดรับออเดอร์ชั่วคราว'; end if;
 v_now:=now() at time zone 'Asia/Bangkok';
 select * into v_special from public.shop_special_hours where shop_id=new.shop_id and day=v_now::date;
 if found then
  if v_special.is_closed or v_special.opens_at is null or v_special.closes_at is null or v_now::time<v_special.opens_at or v_now::time>=v_special.closes_at then raise exception 'ร้านปิดตามเวลาพิเศษ'; end if;
 else
  select * into v_hours from public.shop_business_hours where shop_id=new.shop_id and weekday=extract(dow from v_now);
  if found and (v_hours.is_closed or v_now::time<v_hours.opens_at or v_now::time>=v_hours.closes_at) then raise exception 'ร้านปิดตามเวลาทำการ'; end if;
 end if;
 return new;
end $function$
;


CREATE OR REPLACE FUNCTION public.orders_set_gp()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_rate numeric;
begin
  if new.fulfillment_vertical='ride' then
    new.gp_rate:=0;
    new.gp_amount:=0;
    return new;
  end if;
  if new.order_type in ('DINE_IN','TAKEAWAY') then
    new.gp_rate:=0;
    new.gp_amount:=0;
  elsif tg_op='INSERT' then
    v_rate:=coalesce(public.effective_gp_rate_at(new.shop_id,now()),0);
    if v_rate<0 or v_rate>100 then
      raise exception 'invalid effective GP rate';
    end if;
    new.gp_rate:=v_rate;
    new.gp_amount:=case
      when v_rate=0 then 0
      else round(coalesce(new.subtotal,0)*v_rate/(100+v_rate),2)
    end;
  else
    new.gp_rate:=old.gp_rate;
    if new.subtotal is distinct from old.subtotal then
      if coalesce(old.subtotal,0)>0 and old.gp_amount is not null then
        -- Preserve the financial model of an already-created order.
        new.gp_amount:=round(coalesce(new.subtotal,0)*old.gp_amount/old.subtotal,2);
      else
        v_rate:=coalesce(old.gp_rate,0);
        new.gp_amount:=case
          when v_rate=0 then 0
          else round(coalesce(new.subtotal,0)*v_rate/(100+v_rate),2)
        end;
      end if;
    else
      new.gp_amount:=old.gp_amount;
    end if;
  end if;
  return new;
end
$function$
;


CREATE OR REPLACE FUNCTION public.queuego_guard_cash_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_role text;v_own_rider uuid;
begin
  if old.fulfillment_vertical='ride' and current_setting('queuego.ride_action',true)='rpc' then return new; end if;
  if not exists(select 1 from public.queuego_cash_order_locks where order_id=old.id) then
    return new;
  end if;

  if current_setting('queuego.market_recalc',true)='on'
     and old.market_order_id is not null
     and new.market_order_id=old.market_order_id then
    return new;
  end if;

  v_role:=public.get_my_role();

  if current_setting('queuego.merchant_order_edit',true)='on'
     and v_role='shop'
     and new.id=old.id
     and new.status=old.status
     and new.rider_id is not distinct from old.rider_id
     and new.customer_id is not distinct from old.customer_id
     and new.shop_id is not distinct from old.shop_id
     and new.delivery_fee is not distinct from old.delivery_fee
     and new.market_order_id is null
     and old.market_order_id is null then
    return new;
  end if;

  if v_role='admin' then return new; end if;

  if current_setting('queuego.route_bundle_reprice',true)='on'
     and v_role='rider'
     and old.market_order_id is null
     and new.market_order_id is null then
    select id into v_own_rider
    from public.rider_profiles
    where user_id=public.get_my_user_id();

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
         (
           old.status='searching_rider' and old.rider_id is null
           and new.status='rider_assigned' and new.rider_id=v_own_rider
           and new.delivery_fee<=old.delivery_fee
           and new.total_amount=old.total_amount-old.delivery_fee+new.delivery_fee
           and new.route_bundle_id is not null
           and new.route_bundle_sequence>1
           and new.bundle_original_delivery_fee=old.delivery_fee
           and new.bundle_customer_savings=old.delivery_fee-new.delivery_fee
           and new.bundle_rider_extra_fee=new.delivery_fee
         )
         or
         (
           old.rider_id=v_own_rider and new.rider_id=old.rider_id
           and new.status=old.status
           and new.delivery_fee=old.delivery_fee
           and new.total_amount=old.total_amount
           and old.route_bundle_id is null
           and new.route_bundle_id is not null
           and new.route_bundle_sequence=1
           and new.bundle_customer_savings=0
           and new.bundle_rider_extra_fee=0
         )
       ) then
      return new;
    end if;
  end if;

  if new.order_number is distinct from old.order_number
     or new.customer_id is distinct from old.customer_id
     or new.shop_id is distinct from old.shop_id
     or new.order_type is distinct from old.order_type
     or new.subtotal is distinct from old.subtotal
     or new.delivery_fee is distinct from old.delivery_fee
     or new.total_amount is distinct from old.total_amount
     or new.gp_rate is distinct from old.gp_rate
     or new.gp_amount is distinct from old.gp_amount
     or new.pickup_address is distinct from old.pickup_address
     or new.pickup_latitude is distinct from old.pickup_latitude
     or new.pickup_longitude is distinct from old.pickup_longitude
     or new.delivery_address is distinct from old.delivery_address
     or new.delivery_latitude is distinct from old.delivery_latitude
     or new.delivery_longitude is distinct from old.delivery_longitude
     or new.created_by is distinct from old.created_by
     or new.route_bundle_id is distinct from old.route_bundle_id
     or new.route_bundle_sequence is distinct from old.route_bundle_sequence
     or new.bundle_original_delivery_fee is distinct from old.bundle_original_delivery_fee
     or new.bundle_customer_savings is distinct from old.bundle_customer_savings
     or new.bundle_rider_extra_fee is distinct from old.bundle_rider_extra_fee
     or new.bundle_detour_km is distinct from old.bundle_detour_km
     or new.bundle_added_minutes is distinct from old.bundle_added_minutes
     or new.bundle_route_sequence is distinct from old.bundle_route_sequence then
    raise exception 'order financial details are locked';
  end if;

  if new.rider_id is distinct from old.rider_id
     and not (
       v_role='rider'
       and old.rider_id is null
       and (
         (old.status='searching_rider' and new.status='rider_assigned')
         or (old.status='ready' and new.status='assigned')
       )
       and new.rider_id in(
         select id from public.rider_profiles
         where user_id=public.get_my_user_id()
       )
     ) then
    raise exception 'rider assignment is locked';
  end if;

  if new.status is distinct from old.status then
    if v_role='customer' then
      if not (
        old.status in('pending','searching_rider')
        and new.status='cancelled'
        and old.rider_id is null
      ) then
        raise exception 'customer cannot change order status';
      end if;
    elsif v_role='shop' then
      if not (
        (old.status,new.status) in(
          ('pending','searching_rider'),
          ('rider_assigned','preparing'),
          ('preparing','ready'),
          ('pending','accepted'),
          ('accepted','preparing')
        )
        or (
          new.status='cancelled'
          and old.status in(
            'pending','accepted','searching_rider',
            'rider_assigned','preparing','ready','assigned'
          )
        )
      ) then
        raise exception 'invalid shop order transition';
      end if;
    elsif v_role='rider' then
      if not (
        (old.status,new.status) in(
          ('searching_rider','rider_assigned'),
          ('ready','picked_up'),
          ('picked_up','in_progress'),
          ('in_progress','completed'),
          ('ready','assigned'),
          ('assigned','picked_up')
        )
        and (
          old.rider_id=new.rider_id
          or (old.rider_id is null and new.rider_id is not null)
        )
      ) then
        raise exception 'invalid rider order transition';
      end if;
    else
      raise exception 'order status change forbidden';
    end if;
  end if;

  return new;
end
$function$
;


CREATE OR REPLACE FUNCTION public.qg_customer_cancel_order(p_order_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_customer uuid; v_order public.orders%rowtype; v_shop_user uuid;
begin
 select id into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
 if v_customer is null then raise exception 'active customer required'; end if;
 select * into v_order from public.orders where id=p_order_id and customer_id=v_customer for update;
 if not found or v_order.status not in ('pending','searching_rider') or v_order.rider_id is not null then raise exception 'order is no longer cancellable'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 if v_order.fulfillment_vertical='ride' then perform set_config('queuego.ride_action','rpc',true); end if;
 update public.orders set status='cancelled',cancelled_at=coalesce(cancelled_at,now()),updated_at=now(),note='__QT_ORDER_STATUS__=cancelled'||E'\n'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g') where id=p_order_id;
 update public.deliveries set status='cancelled',updated_at=now() where order_id=p_order_id and status='pending';
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(v_customer,'customer_cancel','order',p_order_id,'cancelled',jsonb_build_object('from',v_order.status,'to','cancelled'));
 select user_id into v_shop_user from public.shop_profiles where id=v_order.shop_id;
 if v_shop_user is not null then insert into public.notifications(user_id,title,message,type,reference_id) values(v_shop_user,'ลูกค้ายกเลิกออเดอร์','ออเดอร์ถูกยกเลิกโดยลูกค้า','order',p_order_id); end if;
 return 'cancelled';
end $function$
;


CREATE OR REPLACE FUNCTION public.rider_claim_order(p_order_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_rider uuid;
  v_user uuid;
  v_order public.orders%rowtype;
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true
  for update of r;

  if v_rider is null then raise exception 'active online rider required'; end if;

  select * into v_order from public.orders where id=p_order_id for update;
  if not found or not (v_order.order_type='shopping' or (v_order.order_type='service' and v_order.fulfillment_vertical='ride')) then raise exception 'order no longer available'; end if;

  if v_order.fulfillment_vertical='ride' and not qg_private.ride_owner_authorized(v_rider) then raise exception 'passenger ride access required'; end if;

  if v_order.rider_id=v_rider
     and v_order.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress','completed') then
    return v_order.status;
  end if;

  if exists(
    select 1 from public.orders
    where rider_id=v_rider and id<>p_order_id
      and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
  ) then raise exception 'finish current order before claiming another'; end if;

  if v_order.status<>'searching_rider' or v_order.rider_id is not null then
    raise exception 'order no longer available';
  end if;

  if v_order.fulfillment_vertical not in ('food','ride')
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
  if v_order.fulfillment_vertical='ride' then perform set_config('queuego.ride_action','rpc',true); end if;

  update public.orders
  set rider_id=v_rider,status='rider_assigned',
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
    values(v_order.customer_id,'พบไรเดอร์แล้ว',case when v_order.fulfillment_vertical='ride' then 'คนขับกำลังไปยังจุดรับผู้โดยสาร' else 'มีไรเดอร์รับออเดอร์แล้ว ร้านค้าจะเริ่มเตรียมสินค้า' end,'order',p_order_id);
  end if;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select sp.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานแล้ว สามารถเริ่มเตรียมสินค้าได้','order',p_order_id
  from public.shop_profiles sp where sp.id=v_order.shop_id;

  return 'rider_assigned';
end
$function$
;


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
    where (o.order_type='shopping' or (o.order_type='service' and o.fulfillment_vertical='ride' and public.queuego_feature_enabled('ride',now())))
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
    rr:=null;

    select r.id into rr
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    left join qg_private.rider_destination_preferences dp
      on dp.rider_id=r.id
     and dp.enabled=true
    where u.role='rider'
      and u.status='active'
      and r.status='active'
      and coalesce((r.metadata->>'online')::boolean,false)
      and coalesce((r.metadata->>'available')::boolean,false)
      and r.latitude is not null
      and r.longitude is not null
      and (j.fulfillment_vertical<>'ride' or qg_private.ride_owner_authorized(r.id))
      and not exists(
        select 1 from public.orders b
        where b.rider_id=r.id
          and b.status in('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
      )
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
        (j.market_order_id is null and j.fulfillment_vertical in ('food','ride'))
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

      insert into public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt)
      values(j.id,rr,now(),now()+interval '30 seconds',a)
      on conflict(order_id) do nothing;

      if found then
        n:=n+1;

        insert into public.notifications(user_id,title,message,type,reference_id)
        select r.user_id,
               case when j.fulfillment_vertical='ride' then 'มีเที่ยวผู้โดยสารใหม่' else 'มีงานใหม่' end,
               case when j.fulfillment_vertical='ride' then 'มีคำขอรับส่งผู้โดยสารใกล้คุณ กรุณาตอบรับภายใน 30 วินาที' else 'มีออเดอร์ใหม่ใกล้คุณ กรุณาตอบรับภายใน 30 วินาที' end,
               'order',
               j.id
        from public.rider_profiles r
        where r.id=rr;
      end if;
    end if;
  end loop;

  return n;
end
$function$
;


CREATE OR REPLACE FUNCTION public.qg_dispatch_on_searching_rider()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
 if (new.order_type='shopping' or (new.order_type='service' and new.fulfillment_vertical='ride')) and new.status='searching_rider' and (tg_op='INSERT' or old.status is distinct from new.status) then perform public.qg_dispatch_rider_offers(); end if;
 return new;
end $function$
;


CREATE OR REPLACE FUNCTION public.qg_rider_action_once(p_request_id uuid, p_kind text, p_payload jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user uuid;
  v_existing public.qg_rider_action_receipts%rowtype;
  v_result jsonb;
  v_order uuid;
  v_market_order uuid;
begin
  select u.id
  into v_user
  from public.users u
  join public.rider_profiles r on r.user_id=u.id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';

  if v_user is null
     or p_request_id is null
     or p_payload is null
     or jsonb_typeof(p_payload)<>'object' then
    raise exception 'active rider request required';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));

  select *
  into v_existing
  from public.qg_rider_action_receipts
  where request_id=p_request_id;

  if found then
    if v_existing.user_id<>v_user
       or v_existing.kind<>p_kind
       or v_existing.payload<>p_payload then
      raise exception 'request id already in use';
    end if;
    return v_existing.result;
  end if;

  if p_kind in ('claim','market_claim') then
    v_order:=(p_payload->>'p_order_id')::uuid;

    -- Preserve the selected-Rider lease/concurrency guard before classifying the order.
    perform public.qg_require_live_rider_offer(v_order);

    -- Client claim kind is only a hint. Production orders are the source of truth.
    select o.market_order_id
    into v_market_order
    from public.orders o
    where o.id=v_order;

    if not found then
      raise exception 'order no longer available';
    end if;

    if v_market_order is null then
      select to_jsonb(public.rider_claim_order(v_order))
      into v_result;
    else
      select to_jsonb(public.market_rider_claim_group(v_order))
      into v_result;
    end if;
  else
    case p_kind
      when 'ride' then
        select qg_private.ride_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action') into v_result;
      when 'bundle_claim' then
        select to_jsonb(public.queuego_claim_route_bundle((p_payload->>'p_order_id')::uuid)) into v_result;
      when 'order' then
        select to_jsonb(public.rider_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action')) into v_result;
      when 'market' then
        select to_jsonb(public.market_rider_group_action((p_payload->>'p_market_order_id')::uuid,p_payload->>'p_action')) into v_result;
      when 'market_pickup' then
        select to_jsonb(public.market_rider_confirm_pickup_cash((p_payload->>'p_pickup_id')::uuid,(p_payload->>'p_amount')::numeric)) into v_result;
      when 'laundry_mode' then
        select to_jsonb(public.queuego_set_laundry_rider_mode((p_payload->>'p_enabled')::boolean)) into v_result;
      when 'laundry_invite' then
        select to_jsonb(public.queuego_laundry_rider_invite_action((p_payload->>'p_invite_id')::uuid,(p_payload->>'p_accept')::boolean)) into v_result;
      when 'laundry_claim' then
        select to_jsonb(public.queuego_claim_laundry_job((p_payload->>'p_job_id')::uuid)) into v_result;
      when 'laundry_action' then
        select to_jsonb(public.queuego_laundry_rider_action((p_payload->>'p_job_id')::uuid,p_payload->>'p_action')) into v_result;
      else
        raise exception 'unsupported rider action';
    end case;
  end if;

  if p_kind in ('claim','market_claim') then
    perform public.qg_accept_offer_receipt(v_order);
  end if;

  insert into public.qg_rider_action_receipts(request_id,user_id,kind,payload,result)
  values(p_request_id,v_user,p_kind,p_payload,coalesce(v_result,'null'::jsonb));

  return v_result;
end
$function$
;



-- Owner-requested initial legal-ceiling tariff and zero platform fee.
insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by) values
 ('ride.base_fare','25'::jsonb,now(),'Passenger Ride pilot: legal motorcycle ceiling',null),
 ('ride.included_km','2'::jsonb,now(),'Passenger Ride pilot: legal motorcycle ceiling',null),
 ('ride.per_km','5'::jsonb,now(),'Passenger Ride pilot: legal motorcycle ceiling',null),
 ('ride.minimum_fare','25'::jsonb,now(),'Passenger Ride pilot: legal motorcycle ceiling',null),
 ('ride.long_distance_threshold_km','5'::jsonb,now(),'Passenger Ride pilot: legal motorcycle ceiling',null),
 ('ride.long_distance_per_km','10'::jsonb,now(),'Passenger Ride pilot: legal motorcycle ceiling',null),
 ('ride.platform_fee_percent','0'::jsonb,now(),'Passenger Ride pilot: cash, no QueueGo fee',null),
 ('feature.ride','true'::jsonb,now(),'Passenger Ride enabled by owner for authorized Rider pilot',null);
