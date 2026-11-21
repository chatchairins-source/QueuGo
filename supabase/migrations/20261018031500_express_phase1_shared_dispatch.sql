-- QueueGo Express Phase 1: one pickup / one dropoff on the EXISTING Order + Rider dispatcher.
-- Source-only staging: no feature enable, no pricing defaults, no Production mutation.
begin;

alter table public.orders drop constraint if exists orders_fulfillment_vertical_check;
alter table public.orders add constraint orders_fulfillment_vertical_check
 check(fulfillment_vertical in ('food','market','grocery','ride','express'));

alter table public.orders add column if not exists express_distance_km numeric;
alter table public.orders add column if not exists express_duration_minutes numeric;
alter table public.orders add column if not exists express_platform_fee numeric;
alter table public.orders add column if not exists express_item_type text;
alter table public.orders add column if not exists express_weight_kg numeric;
alter table public.orders add column if not exists express_size text;
alter table public.orders add column if not exists express_vehicle_type text;
alter table public.orders add column if not exists express_sender_name text;
alter table public.orders add column if not exists express_sender_phone text;
alter table public.orders add column if not exists express_receiver_name text;
alter table public.orders add column if not exists express_receiver_phone text;
alter table public.orders add column if not exists express_instructions text;

alter table public.orders drop constraint if exists orders_express_shape_check;
alter table public.orders add constraint orders_express_shape_check check(
 fulfillment_vertical<>'express' or (
  order_type='service' and shop_id is null and market_order_id is null and route_bundle_id is null and next_after_order_id is null
  and subtotal=0 and delivery_fee>0 and total_amount=delivery_fee
  and express_distance_km>0 and express_duration_minutes>0
  and express_platform_fee>=0 and express_platform_fee<=delivery_fee
  and rider_delivery_payout>=0
  and length(trim(express_item_type)) between 1 and 80
  and express_weight_kg>0
  and length(trim(express_size)) between 1 and 50
  and express_vehicle_type in ('motorcycle','car','saleng')
  and length(trim(express_sender_name)) between 1 and 120
  and length(trim(express_sender_phone)) between 5 and 30
  and length(trim(express_receiver_name)) between 1 and 120
  and length(trim(express_receiver_phone)) between 5 and 30
 ));
create index if not exists orders_active_express_rider_idx on public.orders(rider_id)
 where fulfillment_vertical='express' and status not in ('completed','cancelled','no_rider_available');
create unique index if not exists orders_one_active_express_customer_idx on public.orders(customer_id)
 where fulfillment_vertical='express' and status not in ('completed','cancelled','no_rider_available');

create table if not exists qg_private.express_quotes(
 id uuid primary key default gen_random_uuid(),
 customer_id uuid not null references public.users(id),
 pickup_address text not null,delivery_address text not null,
 pickup_latitude double precision not null,pickup_longitude double precision not null,
 delivery_latitude double precision not null,delivery_longitude double precision not null,
 sender_name text not null,sender_phone text not null,receiver_name text not null,receiver_phone text not null,
 item_type text not null,weight_kg numeric not null check(weight_kg>0),item_size text not null,
 vehicle_type text not null check(vehicle_type in ('motorcycle','car','saleng')),
 instructions text,
 distance_km numeric not null check(distance_km>0),duration_minutes numeric not null check(duration_minutes>0),
 fare numeric not null check(fare>0),platform_fee numeric not null check(platform_fee>=0 and platform_fee<=fare),
 expires_at timestamptz not null,created_at timestamptz not null default now(),
 order_id uuid unique references public.orders(id)
);
create index if not exists express_quotes_customer_idx on qg_private.express_quotes(customer_id,expires_at);
alter table qg_private.express_quotes enable row level security;
revoke all on qg_private.express_quotes from public,anon,authenticated;

create or replace function public.queuego_express_config()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare b numeric;k numeric;r numeric;m numeric;g numeric;
begin
 b:=public.queuego_rule_numeric('express.base_fare',-1,now());
 k:=public.queuego_rule_numeric('express.included_km',-1,now());
 r:=public.queuego_rule_numeric('express.per_km',-1,now());
 m:=public.queuego_rule_numeric('express.minimum_fare',-1,now());
 g:=public.queuego_rule_numeric('express.platform_fee_percent',-1,now());
 return jsonb_build_object('enabled',public.queuego_feature_enabled('express',now()),
  'configured',b>=0 and k>=0 and r>=0 and m>0 and g>=0 and g<100,
  'base_fare',b,'included_km',k,'per_km',r,'minimum_fare',m,'platform_fee_percent',g);
end $$;
revoke all on function public.queuego_express_config() from public;
grant execute on function public.queuego_express_config() to authenticated,service_role;

create or replace function qg_private.express_credit_authorized(p_rider uuid,p_order uuid,p_fee numeric)
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
declare v_ok boolean;
begin
 if p_fee=0 then return true; end if;
 if p_fee is null or p_fee<0 or to_regprocedure('qg_private.rider_credit_order_authorized(uuid,uuid,numeric)') is null then return false; end if;
 execute 'select qg_private.rider_credit_order_authorized($1,$2,$3)' into v_ok using p_rider,p_order,p_fee;
 return coalesce(v_ok,false);
end $$;
revoke all on function qg_private.express_credit_authorized(uuid,uuid,numeric) from public,anon,authenticated;

create or replace function public.queuego_cache_express_quote(
 p_auth_user_id uuid,p_pickup_address text,p_delivery_address text,
 p_pickup_lat double precision,p_pickup_lng double precision,p_delivery_lat double precision,p_delivery_lng double precision,
 p_sender_name text,p_sender_phone text,p_receiver_name text,p_receiver_phone text,
 p_item_type text,p_weight_kg numeric,p_item_size text,p_vehicle_type text,p_instructions text,
 p_distance_km numeric,p_duration_minutes numeric)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare c jsonb;v_customer uuid;v_quote qg_private.express_quotes%rowtype;v_fare numeric;v_area jsonb;
begin
 select id into v_customer from public.users where auth_user_id=p_auth_user_id and role='customer' and status='active';
 if v_customer is null then raise exception 'active customer required'; end if;
 c:=public.queuego_express_config();
 if not coalesce((c->>'enabled')::boolean,false) or not coalesce((c->>'configured')::boolean,false)
 then raise exception 'express service is not configured or enabled'; end if;
 if length(trim(coalesce(p_pickup_address,''))) not between 3 and 500 or length(trim(coalesce(p_delivery_address,''))) not between 3 and 500
 then raise exception 'pickup and destination required'; end if;
 if length(trim(coalesce(p_sender_name,''))) not between 1 and 120 or length(trim(coalesce(p_sender_phone,''))) not between 5 and 30
  or length(trim(coalesce(p_receiver_name,''))) not between 1 and 120 or length(trim(coalesce(p_receiver_phone,''))) not between 5 and 30
 then raise exception 'sender and receiver required'; end if;
 if length(trim(coalesce(p_item_type,''))) not between 1 and 80 or p_weight_kg is null or p_weight_kg<=0
  or length(trim(coalesce(p_item_size,''))) not between 1 and 50 or p_vehicle_type not in ('motorcycle','car','saleng')
 then raise exception 'valid Express item and vehicle required'; end if;
 v_area:=public.queuego_service_area_route_status(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,'express');
 if not coalesce((v_area->>'available')::boolean,false) then raise exception 'express outside service area'; end if;
 if p_distance_km is null or p_duration_minutes is null or p_distance_km<=0 or p_duration_minutes<=0
  or p_distance_km<public.queuego_distance_km(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng)*0.9
 then raise exception 'verified road distance and duration required'; end if;
 v_fare:=round(greatest((c->>'minimum_fare')::numeric,
  (c->>'base_fare')::numeric+greatest(p_distance_km-(c->>'included_km')::numeric,0)*(c->>'per_km')::numeric),2);
 insert into qg_private.express_quotes(customer_id,pickup_address,delivery_address,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,
  sender_name,sender_phone,receiver_name,receiver_phone,item_type,weight_kg,item_size,vehicle_type,instructions,
  distance_km,duration_minutes,fare,platform_fee,expires_at)
 values(v_customer,trim(p_pickup_address),trim(p_delivery_address),p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,
  trim(p_sender_name),trim(p_sender_phone),trim(p_receiver_name),trim(p_receiver_phone),trim(p_item_type),p_weight_kg,trim(p_item_size),p_vehicle_type,
  nullif(trim(coalesce(p_instructions,'')),''),
  p_distance_km,p_duration_minutes,v_fare,round(v_fare*(c->>'platform_fee_percent')::numeric/100,2),now()+interval '2 minutes')
 returning * into v_quote;
 return jsonb_build_object('quote_id',v_quote.id,'fare',v_quote.fare,'distance_km',v_quote.distance_km,
  'duration_minutes',v_quote.duration_minutes,'vehicle_type',v_quote.vehicle_type,'expires_at',v_quote.expires_at,'payment_method','cash');
end $$;
revoke all on function public.queuego_cache_express_quote(uuid,text,text,double precision,double precision,double precision,double precision,text,text,text,text,text,numeric,text,text,text,numeric,numeric) from public,anon,authenticated;
grant execute on function public.queuego_cache_express_quote(uuid,text,text,double precision,double precision,double precision,double precision,text,text,text,text,text,numeric,text,text,text,numeric,numeric) to service_role;

create or replace function public.queuego_request_express(p_request_id uuid,p_quote_id uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_customer uuid;q qg_private.express_quotes%rowtype;o public.orders%rowtype;
begin
 perform qg_private.assert_live_session();
 select id into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
 if v_customer is null or p_request_id is null then raise exception 'active customer and request required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_customer::text,8361));
 select * into q from qg_private.express_quotes where id=p_quote_id and customer_id=v_customer for update;
 if not found then raise exception 'quote unavailable'; end if;
 if q.order_id is not null then
  if q.order_id<>p_request_id then raise exception 'quote already used by another request'; end if;
  select * into o from public.orders where id=q.order_id and customer_id=v_customer;
  return jsonb_build_object('id',o.id,'order_number',o.order_number,'status',o.status,'fare',o.total_amount);
 end if;
 if not public.queuego_feature_enabled('express',now()) or q.expires_at<=now() then raise exception 'express disabled or quote expired'; end if;
 if exists(select 1 from public.orders where customer_id=v_customer and fulfillment_vertical='express'
  and status not in ('completed','cancelled','no_rider_available')) then raise exception 'finish current Express first'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 insert into public.orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,gp_rate,gp_amount,
  pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,
  express_distance_km,express_duration_minutes,express_platform_fee,express_item_type,express_weight_kg,express_size,express_vehicle_type,
  express_sender_name,express_sender_phone,express_receiver_name,express_receiver_phone,express_instructions,rider_delivery_payout,rider_search_started_at)
 values(p_request_id,public.qg_next_order_number(),v_customer,'service','express','searching_rider',0,q.fare,q.fare,0,0,
  q.pickup_address,q.pickup_latitude,q.pickup_longitude,q.delivery_address,q.delivery_latitude,q.delivery_longitude,
  q.distance_km,q.duration_minutes,q.platform_fee,q.item_type,q.weight_kg,q.item_size,q.vehicle_type,
  q.sender_name,q.sender_phone,q.receiver_name,q.receiver_phone,q.instructions,q.fare-q.platform_fee,now()) returning * into o;
 insert into public.payments(order_id,amount,payment_method,status) values(o.id,o.total_amount,'cash','pending');
 insert into public.deliveries(order_id,status,delivery_fee,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude)
 values(o.id,'pending',o.delivery_fee,o.pickup_address,o.pickup_latitude,o.pickup_longitude,o.delivery_address,o.delivery_latitude,o.delivery_longitude);
 insert into public.queuego_cash_order_locks(order_id) values(o.id);
 update qg_private.express_quotes set order_id=o.id where id=q.id;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_customer,'express_requested','order',o.id,'Express delivery requested',
  jsonb_build_object('quote_id',q.id,'fare',q.fare,'vehicle_type',q.vehicle_type,'weight_kg',q.weight_kg,'payment_method','cash'));
 return jsonb_build_object('id',o.id,'order_number',o.order_number,'status',o.status,'fare',o.total_amount);
end $$;
revoke all on function public.queuego_request_express(uuid,uuid) from public,anon;
grant execute on function public.queuego_request_express(uuid,uuid) to authenticated;

create or replace function qg_private.express_order_action(p_order_id uuid,p_action text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_rider uuid;v_user uuid;v_user_status text;v_rider_status text;o public.orders%rowtype;
 v_action text:=lower(trim(coalesce(p_action,'')));v_next text;
begin
 select r.id,r.user_id,u.status,r.status into v_rider,v_user,v_user_status,v_rider_status
 from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider';
 if v_rider is null then raise exception 'rider required'; end if;
 select * into o from public.orders where id=p_order_id and rider_id=v_rider and fulfillment_vertical='express' for update;
 if not found then raise exception 'assigned Express order required'; end if;
 if o.status='completed' then return jsonb_build_object('status','completed','fare',o.total_amount); end if;
 if (v_user_status<>'active' or v_rider_status<>'active') and o.status not in ('picked_up','in_progress')
 then raise exception 'active rider required before parcel custody'; end if;
 if v_action='arrive_pickup' and o.status='rider_assigned' then
  update public.orders set rider_arrived_shop_at=coalesce(rider_arrived_shop_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='at_shop',updated_at=now() where order_id=o.id;
 elsif v_action='pickup' and o.status='rider_assigned' and o.rider_arrived_shop_at is not null then
  if not exists(select 1 from public.qg_pickup_proofs p where p.order_id=o.id and p.rider_id=v_rider and p.photo_path is not null)
  then raise exception 'pickup photo required'; end if;
  update public.orders set status='picked_up',picked_up_at=coalesce(picked_up_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='picked_up',updated_at=now() where order_id=o.id;
 elsif v_action='start_transit' and o.status='picked_up' then
  update public.orders set status='in_progress',delivering_at=coalesce(delivering_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='delivering',updated_at=now() where order_id=o.id;
 elsif v_action='arrive_dropoff' and o.status='in_progress' then
  update public.orders set rider_arrived_customer_at=coalesce(rider_arrived_customer_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='arrived',updated_at=now() where order_id=o.id;
 elsif v_action='complete' and o.status='in_progress' and o.rider_arrived_customer_at is not null then
  if not exists(select 1 from public.qg_delivery_proofs p where p.order_id=o.id and p.rider_id=v_rider and p.photo_path is not null)
  then raise exception 'delivery photo required'; end if;
  update public.payments set status='paid',paid_at=coalesce(paid_at,now()),updated_at=now()
   where order_id=o.id and payment_method='cash' and status='pending';
  if not found and not exists(select 1 from public.payments where order_id=o.id and payment_method='cash' and status='paid')
  then raise exception 'cash payment record required'; end if;
  update public.orders set status='completed',completed_at=coalesce(completed_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now() where order_id=o.id;
 else
  raise exception 'Express stage changed';
 end if;
 select status into v_next from public.orders where id=o.id;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_user,'express_order_action','order',o.id,v_action,jsonb_build_object('status',v_next));
 return jsonb_build_object('status',v_next,'fare',o.total_amount);
end $$;
revoke all on function qg_private.express_order_action(uuid,text) from public,anon,authenticated;

create or replace function public.qg_express_pickup_with_photo(
 p_request_id uuid,p_order_id uuid,p_photo_path text,p_lat double precision default null,p_lng double precision default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_rider uuid;v_result jsonb;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider';
 if v_rider is null then raise exception 'rider required'; end if;
 if p_photo_path is null or p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
  or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_photo_path)
 then raise exception 'valid pickup photo required'; end if;
 insert into public.qg_pickup_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path)
 values(p_order_id,v_rider,p_lat,p_lng,p_photo_path)
 on conflict(order_id) do update set gps_lat=excluded.gps_lat,gps_lng=excluded.gps_lng,photo_path=excluded.photo_path
 where public.qg_pickup_proofs.rider_id=excluded.rider_id;
 select public.qg_rider_action_once(p_request_id,'express',
  jsonb_build_object('p_order_id',p_order_id,'p_action','pickup','photo_path',p_photo_path)) into v_result;
 return v_result;
end $$;
revoke all on function public.qg_express_pickup_with_photo(uuid,uuid,text,double precision,double precision) from public,anon;
grant execute on function public.qg_express_pickup_with_photo(uuid,uuid,text,double precision,double precision) to authenticated;

create or replace function public.qg_express_complete_with_photo(
 p_request_id uuid,p_order_id uuid,p_photo_path text,p_lat double precision default null,p_lng double precision default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_rider uuid;v_result jsonb;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider';
 if v_rider is null then raise exception 'rider required'; end if;
 if p_photo_path is null or p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
  or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_photo_path)
 then raise exception 'valid delivery photo required'; end if;
 insert into public.qg_delivery_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path,extra_photo_path,otp_verified,verified_at)
 values(p_order_id,v_rider,p_lat,p_lng,p_photo_path,null,false,now())
 on conflict(order_id) do update set gps_lat=excluded.gps_lat,gps_lng=excluded.gps_lng,photo_path=excluded.photo_path,
  extra_photo_path=null,otp_verified=false,verified_at=now()
 where public.qg_delivery_proofs.rider_id=excluded.rider_id;
 select public.qg_rider_action_once(p_request_id,'express',
  jsonb_build_object('p_order_id',p_order_id,'p_action','complete','photo_path',p_photo_path)) into v_result;
 return v_result;
end $$;
revoke all on function public.qg_express_complete_with_photo(uuid,uuid,text,double precision,double precision) from public,anon;
grant execute on function public.qg_express_complete_with_photo(uuid,uuid,text,double precision,double precision) to authenticated;

create or replace function qg_private.guard_express_order_write()
returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if TG_OP='UPDATE' and (old.fulfillment_vertical='express' or new.fulfillment_vertical='express') then
  if new.fulfillment_vertical is distinct from old.fulfillment_vertical then raise exception 'Express snapshot is immutable'; end if;
  if (to_jsonb(new)-array['status','rider_id','rider_assigned_at','updated_at','note','rider_arrived_shop_at','rider_arrived_customer_at','picked_up_at','delivering_at','completed_at','cancelled_at'])
   is distinct from
   (to_jsonb(old)-array['status','rider_id','rider_assigned_at','updated_at','note','rider_arrived_shop_at','rider_arrived_customer_at','picked_up_at','delivering_at','completed_at','cancelled_at'])
  then raise exception 'Express snapshot is immutable'; end if;
  if new.status is distinct from old.status and not (
    (old.status='searching_rider' and old.rider_id is null and new.status in ('rider_assigned','cancelled','no_rider_available'))
    or (old.status='rider_assigned' and new.status='picked_up' and old.rider_arrived_shop_at is not null)
    or (old.status='picked_up' and new.status='in_progress')
    or (old.status='in_progress' and new.status='completed' and old.rider_arrived_customer_at is not null)
  ) then raise exception 'invalid Express transition'; end if;
  if old.rider_id is not null and new.rider_id is distinct from old.rider_id then raise exception 'Express rider is immutable'; end if;
 end if;
 if new.fulfillment_vertical='express' then
  if current_user not in ('postgres','service_role','supabase_admin') then raise exception 'Express requires authoritative RPC'; end if;
  if new.route_bundle_id is not null or new.next_after_order_id is not null then raise exception 'Express phase 1 cannot be bundled or queued'; end if;
 end if;
 if new.rider_id is not null and new.status not in ('completed','cancelled','no_rider_available') then
  perform 1 from public.rider_profiles where id=new.rider_id for update;
  if new.fulfillment_vertical='express' then
   if not public.queuego_feature_enabled('express',now()) then raise exception 'express service disabled'; end if;
   if not exists(select 1 from public.rider_profiles r where r.id=new.rider_id and r.status='active'
    and r.vehicle_type=new.express_vehicle_type and r.vehicle_status='active'
    and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=new.express_weight_kg)
   then raise exception 'verified Express vehicle capacity required'; end if;
   if not qg_private.express_credit_authorized(new.rider_id,new.id,new.express_platform_fee) then raise exception 'rider credit authorization required'; end if;
  end if;
  if exists(select 1 from public.orders o where o.rider_id=new.rider_id and o.id<>new.id
   and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
   and (new.fulfillment_vertical='express' or o.fulfillment_vertical='express'))
  then raise exception 'Express conflicts with active work'; end if;
  if new.fulfillment_vertical='express' and exists(select 1 from public.laundry_rider_jobs
   where rider_id=new.rider_id and status in ('assigned','accepted','arrived','collected'))
  then raise exception 'Express conflicts with laundry work'; end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.guard_express_order_write() from public,anon,authenticated;
drop trigger if exists qg_express_order_write_guard on public.orders;
create trigger qg_express_order_write_guard before insert or update on public.orders
 for each row execute function qg_private.guard_express_order_write();

create or replace function qg_private.guard_laundry_express_conflict()
returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if new.rider_id is not null and new.status in ('assigned','accepted','arrived','collected') then
  perform 1 from public.rider_profiles where id=new.rider_id for update;
  if exists(select 1 from public.orders where rider_id=new.rider_id and fulfillment_vertical='express'
    and status in ('rider_assigned','picked_up','in_progress'))
  then raise exception 'Express conflicts with laundry work'; end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.guard_laundry_express_conflict() from public,anon,authenticated;
drop trigger if exists qg_laundry_express_conflict on public.laundry_rider_jobs;
create trigger qg_laundry_express_conflict before insert or update on public.laundry_rider_jobs
 for each row execute function qg_private.guard_laundry_express_conflict();

alter policy rider_orders_offered_select on public.orders to authenticated using (
 public.get_my_role()='rider'
 and (order_type='shopping' or (order_type='service' and fulfillment_vertical in ('ride','express')))
 and status='searching_rider' and rider_id is null and (select public.qg_has_live_rider_offer(orders.id))
);


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
  if not found or not (v_order.order_type='shopping' or (v_order.order_type='service' and v_order.fulfillment_vertical in ('ride','express'))) then raise exception 'order no longer available'; end if;

  if v_order.rider_id=v_rider
     and v_order.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress','completed') then
    return v_order.status;
  end if;

  if v_order.fulfillment_vertical='ride' then
   if not public.queuego_feature_enabled('ride',now()) or not qg_private.ride_public_license_valid(v_rider,now()) then raise exception 'verified current public driving license required'; end if;
   if not qg_private.ride_credit_authorized(v_rider,v_order.id,v_order.ride_platform_fee) then raise exception 'rider credit authorization required'; end if;
   if exists(select 1 from public.laundry_rider_jobs where rider_id=v_rider and status in ('assigned','accepted','arrived','collected'))
    or exists(select 1 from public.qg_rider_delivery_returns where rider_id=v_rider and status not in ('resolved','returned','cancelled')) then raise exception 'passenger ride conflicts with active work'; end if;
  end if;

  if v_order.fulfillment_vertical='express' then
   if not public.queuego_feature_enabled('express',now()) then raise exception 'express service disabled'; end if;
   if not exists(select 1 from public.rider_profiles r where r.id=v_rider and r.status='active'
    and r.vehicle_type=v_order.express_vehicle_type and r.vehicle_status='active'
    and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=v_order.express_weight_kg)
   then raise exception 'verified Express vehicle capacity required'; end if;
   if not qg_private.express_credit_authorized(v_rider,v_order.id,v_order.express_platform_fee) then raise exception 'rider credit authorization required'; end if;
   if exists(select 1 from public.laundry_rider_jobs where rider_id=v_rider and status in ('assigned','accepted','arrived','collected'))
    or exists(select 1 from public.qg_rider_delivery_returns where rider_id=v_rider and status not in ('resolved','returned','cancelled'))
   then raise exception 'express conflicts with active work'; end if;
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

  if v_order.fulfillment_vertical='express' then
    if not exists(select 1 from public.rider_profiles r where r.id=v_rider and r.status='active'
      and r.vehicle_type=v_order.express_vehicle_type and r.vehicle_status='active'
      and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=v_order.express_weight_kg)
    then raise exception 'verified Express vehicle capacity required'; end if;
  elsif v_order.fulfillment_vertical not in ('food','ride') then
    if not exists(
       select 1 from public.rider_profiles r
       where r.id=v_rider
         and r.status='active'
         and r.vehicle_type in ('motorcycle','car','saleng')
         and r.vehicle_status='active'
         and r.vehicle_verified_at is not null
         and r.vehicle_capacity_kg>=public.market_order_weight(p_order_id)
         and public.market_order_weight(p_order_id)>0
    ) then raise exception 'vehicle capacity or availability insufficient for market order'; end if;
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
    values(v_order.customer_id,'พบไรเดอร์แล้ว',case when v_order.fulfillment_vertical='ride' then 'คนขับกำลังไปยังจุดรับผู้โดยสาร' when v_order.fulfillment_vertical='express' then 'ไรเดอร์กำลังไปยังจุดรับพัสดุ' else 'มีไรเดอร์รับออเดอร์แล้ว ร้านค้าจะเริ่มเตรียมสินค้า' end,'order',p_order_id);
  end if;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select sp.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานแล้ว สามารถเริ่มเตรียมสินค้าได้','order',p_order_id
  from public.shop_profiles sp where sp.id=v_order.shop_id;

  return 'rider_assigned';
end
$function$;
revoke all on function qg_private.claim_rider_order(uuid,uuid) from public,anon,authenticated;

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
    where (o.order_type='shopping' or (o.order_type='service' and ((o.fulfillment_vertical='ride' and public.queuego_feature_enabled('ride',now())) or (o.fulfillment_vertical='express' and public.queuego_feature_enabled('express',now())))))
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

    select r.id,active.last_order,coalesce(pref.auto_accept and not pref.stop_after_work and pref.ready_until>now() and j.market_order_id is null and (j.fulfillment_vertical in ('ride','express') or public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())=1),false) into rr,v_next,v_accept
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
      and (j.fulfillment_vertical<>'ride' or (qg_private.ride_public_license_valid(r.id,now()) and qg_private.ride_credit_authorized(r.id,j.id,j.ride_platform_fee)))
      and (j.fulfillment_vertical<>'express' or (
        r.vehicle_type=j.express_vehicle_type and r.vehicle_status='active' and r.vehicle_verified_at is not null
        and r.vehicle_capacity_kg>=j.express_weight_kg
        and qg_private.express_credit_authorized(r.id,j.id,j.express_platform_fee)
      ))
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
        (j.market_order_id is null and j.fulfillment_vertical in ('food','ride'))
        or (
          j.market_order_id is null and j.fulfillment_vertical='express'
          and r.vehicle_type=j.express_vehicle_type
          and r.vehicle_status='active' and r.vehicle_verified_at is not null
          and r.vehicle_capacity_kg>=j.express_weight_kg
        )
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
          if j.fulfillment_vertical='ride' then
            insert into public.notifications(user_id,title,message,type,reference_id)
            select user_id,'รับเที่ยวโดยสารอัตโนมัติแล้ว',j.order_number||' · ตรวจจุดรับผู้โดยสารได้ทันที','order',j.id from public.rider_profiles where id=rr;
          elsif j.fulfillment_vertical='express' then
            insert into public.notifications(user_id,title,message,type,reference_id)
            select user_id,'รับงาน Express อัตโนมัติแล้ว',j.order_number||' · ไปยังจุดรับพัสดุได้ทันที','order',j.id from public.rider_profiles where id=rr;
          end if;
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

CREATE OR REPLACE FUNCTION public.qg_rider_action_once(p_request_id uuid, p_kind text, p_payload jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user uuid;
  v_user_status text;
  v_existing public.qg_rider_action_receipts%rowtype;
  v_result jsonb;
  v_order uuid;
  v_market_order uuid;
begin
  select u.id,u.status
  into v_user,v_user_status
  from public.users u
  join public.rider_profiles r on r.user_id=u.id
  where u.auth_user_id=auth.uid()
    and u.role='rider';

  if v_user is null
     or p_request_id is null
     or p_payload is null
     or jsonb_typeof(p_payload)<>'object' then
    raise exception 'rider request required';
  end if;

  -- A suspended Rider may only continue the already-assigned Passenger Ride path.
  -- ride_order_action requeues pre-pickup work and permits safe completion in_progress.
  if v_user_status<>'active' and p_kind not in ('ride','express') then
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
      when 'express' then
        select qg_private.express_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action') into v_result;
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

revoke all on function public.qg_dispatch_rider_offers() from public,anon,authenticated;
grant execute on function public.qg_dispatch_rider_offers() to service_role;
revoke all on function public.qg_rider_action_once(uuid,text,jsonb) from public,anon;
grant execute on function public.qg_rider_action_once(uuid,text,jsonb) to authenticated;

commit;
