-- Passenger rides use the existing Orders/Rider/offer/receipt/cash infrastructure.
-- No feature is enabled and no fare or credit amount is invented by this migration.
alter table public.orders drop constraint if exists orders_fulfillment_vertical_check;
alter table public.orders add constraint orders_fulfillment_vertical_check check(fulfillment_vertical in ('food','market','grocery','ride'));
alter table public.orders add column if not exists ride_distance_km numeric;
alter table public.orders add column if not exists ride_duration_minutes numeric;
alter table public.orders add column if not exists ride_platform_fee numeric;
alter table public.orders add constraint orders_ride_shape_check check(
 fulfillment_vertical<>'ride' or (order_type='service' and shop_id is null and market_order_id is null
 and route_bundle_id is null and subtotal=0 and delivery_fee>0 and total_amount=delivery_fee
 and ride_distance_km is not null and ride_distance_km>0 and ride_duration_minutes is not null and ride_duration_minutes>0
 and ride_platform_fee is not null and ride_platform_fee>=0 and ride_platform_fee<delivery_fee));
create index if not exists orders_active_ride_rider_idx on public.orders(rider_id) where fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available');
create unique index if not exists orders_one_active_ride_customer_idx on public.orders(customer_id) where fulfillment_vertical='ride' and status not in ('completed','cancelled','no_rider_available');

-- Extend the existing admin-recorded document system. Ordinary Rider readiness is unchanged.
alter table qg_private.rider_document_expiries drop constraint rider_document_expiries_key_check;
alter table qg_private.rider_document_expiries add constraint rider_document_expiries_key_check check(document_key in ('national_id','driver_license','public_driver_license','vehicle_prb'));
alter table qg_private.rider_document_expiries drop constraint rider_document_expiries_subject_check;
alter table qg_private.rider_document_expiries add constraint rider_document_expiries_subject_check check(
 (document_key in ('national_id','driver_license','public_driver_license') and vehicle_id is null)
 or (document_key='vehicle_prb' and vehicle_id is not null));

create or replace function public.queuego_admin_review_public_license(p_rider_id uuid,p_expires_on date,p_note text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_admin uuid;
begin
 perform qg_private.assert_live_session();
 select id into v_admin from public.users where auth_user_id=auth.uid() and role='admin' and status='active';
 if v_admin is null then raise exception 'active admin required' using errcode='42501'; end if;
 if length(trim(coalesce(p_note,''))) not between 3 and 500 then raise exception 'public license review note required'; end if;
 perform 1 from public.rider_profiles r join public.users u on u.id=r.user_id where r.id=p_rider_id and u.role='rider' and u.status<>'deleted' for update of r;
 if not found then raise exception 'rider unavailable'; end if;
 if p_expires_on is not null and (p_expires_on<current_date or p_expires_on>current_date+interval '20 years') then raise exception 'valid public driving license expiry required'; end if;
 delete from qg_private.rider_document_expiries where rider_id=p_rider_id and document_key='public_driver_license';
 if p_expires_on is not null then
  insert into qg_private.rider_document_expiries(rider_id,document_key,expires_on,recorded_by) values(p_rider_id,'public_driver_license',p_expires_on,v_admin);
 end if;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_admin,'rider_public_license_review','rider',p_rider_id,trim(p_note),jsonb_build_object('expires_on',p_expires_on,'approved',p_expires_on is not null));
 return jsonb_build_object('rider_id',p_rider_id,'public_license_expires_on',p_expires_on,'approved',p_expires_on is not null);
end $$;
revoke all on function public.queuego_admin_review_public_license(uuid,date,text) from public,anon;
grant execute on function public.queuego_admin_review_public_license(uuid,date,text) to authenticated;

create or replace function qg_private.ride_public_license_valid(p_rider uuid,p_at timestamptz default now())
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.rider_profiles r join public.users u on u.id=r.user_id
 join qg_private.rider_document_expiries d on d.rider_id=r.id and d.document_key='public_driver_license'
 where r.id=p_rider and r.status='active' and u.role='rider' and u.status='active'
 and d.vehicle_id is null and d.recorded_by is not null and d.expires_on>=(p_at at time zone 'Asia/Bangkok')::date)
$$;
revoke all on function qg_private.ride_public_license_valid(uuid,timestamptz) from public,anon,authenticated;

create table qg_private.ride_quotes(
 id uuid primary key default gen_random_uuid(),customer_id uuid not null references public.users(id),
 pickup_address text not null,delivery_address text not null,
 pickup_latitude double precision not null,pickup_longitude double precision not null,
 delivery_latitude double precision not null,delivery_longitude double precision not null,
 distance_km numeric not null check(distance_km>0),duration_minutes numeric not null check(duration_minutes>0),
 fare numeric not null check(fare>0),platform_fee numeric not null check(platform_fee>=0 and platform_fee<=fare),
 expires_at timestamptz not null,created_at timestamptz not null default now(),order_id uuid unique references public.orders(id)
);
create index ride_quotes_customer_idx on qg_private.ride_quotes(customer_id,expires_at);
create index ride_quotes_expiry_idx on qg_private.ride_quotes(expires_at);
alter table qg_private.ride_quotes enable row level security;
revoke all on qg_private.ride_quotes from public,anon,authenticated;

create or replace function public.queuego_ride_config()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare b numeric;k numeric;r numeric;m numeric;g numeric;
begin
 b:=public.queuego_rule_numeric('ride.base_fare',-1,now());k:=public.queuego_rule_numeric('ride.included_km',-1,now());
 r:=public.queuego_rule_numeric('ride.per_km',-1,now());m:=public.queuego_rule_numeric('ride.minimum_fare',-1,now());
 g:=public.queuego_rule_numeric('ride.platform_fee_percent',-1,now());
 return jsonb_build_object('enabled',public.queuego_feature_enabled('ride',now()),
 'configured',b>=0 and k>=0 and r>=0 and m>0 and g>=0 and g<100,
 'base_fare',b,'included_km',k,'per_km',r,'minimum_fare',m,'platform_fee_percent',g);
end $$;
revoke all on function public.queuego_ride_config() from public;
grant execute on function public.queuego_ride_config() to authenticated,service_role;

-- Only the trusted road-quote producer supplies distance/duration. App clients cannot price a trip.
create or replace function public.queuego_cache_ride_quote(p_auth_user_id uuid,p_pickup_address text,p_delivery_address text,
 p_pickup_lat double precision,p_pickup_lng double precision,p_delivery_lat double precision,p_delivery_lng double precision,
 p_distance_km numeric,p_duration_minutes numeric)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
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
 or p_distance_km<public.queuego_distance_km(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng)*0.9 then raise exception 'verified road distance and duration required'; end if;
 v_fare:=round(greatest((c->>'minimum_fare')::numeric,(c->>'base_fare')::numeric+greatest(p_distance_km-(c->>'included_km')::numeric,0)*(c->>'per_km')::numeric),2);
 insert into qg_private.ride_quotes(customer_id,pickup_address,delivery_address,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,distance_km,duration_minutes,fare,platform_fee,expires_at)
 values(v_customer,trim(p_pickup_address),trim(p_delivery_address),p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,p_distance_km,p_duration_minutes,v_fare,round(v_fare*(c->>'platform_fee_percent')::numeric/100,2),now()+interval '2 minutes') returning * into v_quote;
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
 insert into public.orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,gp_rate,gp_amount,
 pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,
 ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,rider_search_started_at)
 values(p_request_id,public.qg_next_order_number(),v_customer,'service','ride','searching_rider',0,q.fare,q.fare,0,0,
 q.pickup_address,q.pickup_latitude,q.pickup_longitude,q.delivery_address,q.delivery_latitude,q.delivery_longitude,q.distance_km,q.duration_minutes,q.platform_fee,q.fare-q.platform_fee,now()) returning * into o;
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

-- A read-only bridge to the owner's EXISTING credit server. No second wallet/credit ledger.
-- Nonzero platform fees fail closed until its verified order authorization adapter is installed.
create or replace function qg_private.ride_credit_authorized(p_rider uuid,p_order uuid,p_fee numeric)
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
declare v_ok boolean;
begin
 if p_fee=0 then return true; end if;
 if p_fee is null or p_fee<0 or to_regprocedure('qg_private.rider_credit_order_authorized(uuid,uuid,numeric)') is null then return false; end if;
 execute 'select qg_private.rider_credit_order_authorized($1,$2,$3)' into v_ok using p_rider,p_order,p_fee;
 return coalesce(v_ok,false);
end $$;
revoke all on function qg_private.ride_credit_authorized(uuid,uuid,numeric) from public,anon,authenticated;

create or replace function qg_private.requeue_passenger_ride(
 p_order_id uuid,p_rider_id uuid,p_reason text,p_actor_user_id uuid
)
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
declare o public.orders%rowtype;
begin
 if p_order_id is null or p_rider_id is null or length(trim(coalesce(p_reason,'')))<3 then
  raise exception 'ride compliance requeue context required';
 end if;

 select * into o
 from public.orders
 where id=p_order_id and rider_id=p_rider_id and fulfillment_vertical='ride' and status='rider_assigned'
 for update;
 if not found then return false; end if;

 perform set_config('queuego.v22_transition','rpc',true);
 perform set_config('queuego.ride_compliance_requeue','1',true);

 delete from public.qg_rider_order_offers where order_id=o.id;

 update public.orders
 set status='searching_rider',
     rider_id=null,
     rider_assigned_at=null,
     rider_arrived_shop_at=null,
     rider_arrived_customer_at=null,
     picked_up_at=null,
     delivering_at=null,
     completed_at=null,
     rider_search_started_at=now(),
     updated_at=now(),
     note='__QT_ORDER_STATUS__=searching_rider'||E'\n'||
          regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
 where id=o.id;

 update public.deliveries
 set rider_id=null,status='pending',updated_at=now()
 where order_id=o.id and status='assigned';

 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(p_actor_user_id,'ride_compliance_requeue','order',o.id,trim(p_reason),
   jsonb_build_object('from','rider_assigned','to','searching_rider','previous_rider_id',p_rider_id));

 if o.customer_id is not null then
  insert into public.notifications(user_id,title,message,type,reference_id)
  values(o.customer_id,'กำลังหาคนขับใหม่','สิทธิ์รับส่งผู้โดยสารของคนขับเดิมไม่พร้อม ระบบกำลังหาคนขับใหม่','order',o.id);
 end if;

 insert into public.notifications(user_id,title,message,type,reference_id)
 select r.user_id,'เที่ยวโดยสารถูกส่งคืนระบบ',o.order_number||' · สิทธิ์รับส่งผู้โดยสารไม่พร้อมก่อนเริ่มเที่ยว','order',o.id
 from public.rider_profiles r where r.id=p_rider_id;

 return true;
end $$;
revoke all on function qg_private.requeue_passenger_ride(uuid,uuid,text,uuid) from public,anon,authenticated;

create or replace function qg_private.guard_ride_order_write()
returns trigger language plpgsql set search_path=public,pg_temp as $$
declare v_compliance_requeue boolean:=false;
begin
 if TG_OP='UPDATE' then
  v_compliance_requeue :=
   coalesce(current_setting('queuego.ride_compliance_requeue',true),'')='1'
   and old.fulfillment_vertical='ride' and new.fulfillment_vertical='ride'
   and old.status='rider_assigned' and new.status='searching_rider'
   and old.rider_id is not null and new.rider_id is null;
 end if;

 if TG_OP='UPDATE' and (old.fulfillment_vertical='ride' or new.fulfillment_vertical='ride') then
  if new.fulfillment_vertical is distinct from old.fulfillment_vertical then
   raise exception 'passenger ride snapshot is immutable';
  end if;

  if v_compliance_requeue then
   if (to_jsonb(new)-array['status','rider_id','rider_assigned_at','rider_search_started_at','updated_at','note','rider_arrived_shop_at','rider_arrived_customer_at','picked_up_at','delivering_at','completed_at','cancelled_at'])
      is distinct from
      (to_jsonb(old)-array['status','rider_id','rider_assigned_at','rider_search_started_at','updated_at','note','rider_arrived_shop_at','rider_arrived_customer_at','picked_up_at','delivering_at','completed_at','cancelled_at'])
   then raise exception 'passenger ride snapshot is immutable'; end if;
  elsif (to_jsonb(new)-array['status','rider_id','rider_assigned_at','updated_at','note','rider_arrived_shop_at','rider_arrived_customer_at','picked_up_at','delivering_at','completed_at','cancelled_at'])
      is distinct from
      (to_jsonb(old)-array['status','rider_id','rider_assigned_at','updated_at','note','rider_arrived_shop_at','rider_arrived_customer_at','picked_up_at','delivering_at','completed_at','cancelled_at'])
  then raise exception 'passenger ride snapshot is immutable'; end if;

  if new.status is distinct from old.status and not (
   (old.status='searching_rider' and old.rider_id is null and new.status in ('rider_assigned','cancelled','no_rider_available'))
   or (old.status='rider_assigned' and new.status='in_progress' and old.rider_arrived_shop_at is not null)
   or (old.status='in_progress' and new.status='completed' and old.rider_arrived_customer_at is not null)
   or v_compliance_requeue)
  then raise exception 'invalid passenger ride transition'; end if;

  if old.rider_id is not null and new.rider_id is distinct from old.rider_id and not v_compliance_requeue
  then raise exception 'passenger rider is immutable'; end if;
 end if;

 if new.fulfillment_vertical='ride' or (TG_OP='UPDATE' and old.fulfillment_vertical='ride') then
  if current_user not in ('postgres','service_role','supabase_admin') then raise exception 'passenger rides require authoritative RPC'; end if;
  if new.route_bundle_id is not null or new.next_after_order_id is not null then raise exception 'passengers cannot be bundled or queued'; end if;
 end if;

 if new.rider_id is not null and new.status not in ('completed','cancelled','no_rider_available') then
  perform 1 from public.rider_profiles where id=new.rider_id for update;
  if new.fulfillment_vertical='ride' and (TG_OP='INSERT' or old.rider_id is distinct from new.rider_id or new.status='in_progress' and old.status is distinct from new.status) then
   if not qg_private.ride_public_license_valid(new.rider_id,now()) then raise exception 'verified current public driving license required'; end if;
   if not qg_private.ride_credit_authorized(new.rider_id,new.id,new.ride_platform_fee) then raise exception 'rider credit authorization required'; end if;
  end if;
  if exists(select 1 from public.orders o where o.rider_id=new.rider_id and o.id<>new.id
   and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
   and (new.fulfillment_vertical='ride' or o.fulfillment_vertical='ride')) then raise exception 'passenger ride conflicts with active work'; end if;
  if new.fulfillment_vertical='ride' and exists(select 1 from public.laundry_rider_jobs where rider_id=new.rider_id and status in ('assigned','accepted','arrived','collected')) then raise exception 'passenger ride conflicts with laundry work'; end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.guard_ride_order_write() from public,anon,authenticated;
create trigger qg_ride_order_write_guard before insert or update on public.orders for each row execute function qg_private.guard_ride_order_write();
create or replace function qg_private.guard_laundry_passenger_conflict()
returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if new.rider_id is not null and new.status in ('assigned','accepted','arrived','collected') then
  perform 1 from public.rider_profiles where id=new.rider_id for update;
  if exists(select 1 from public.orders where rider_id=new.rider_id and fulfillment_vertical='ride' and status in ('rider_assigned','in_progress')) then raise exception 'passenger ride conflicts with laundry work'; end if;
 end if;
 return new;
end $$;
revoke all on function qg_private.guard_laundry_passenger_conflict() from public,anon,authenticated;
create trigger qg_laundry_passenger_conflict before insert or update on public.laundry_rider_jobs for each row execute function qg_private.guard_laundry_passenger_conflict();

create or replace function qg_private.ride_order_action(p_order_id uuid,p_action text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_rider uuid;v_user uuid;o public.orders%rowtype;v_action text:=lower(trim(coalesce(p_action,'')));v_next text;
begin
 perform qg_private.assert_live_session();
 select r.id,r.user_id into v_rider,v_user from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active' for update of r;
 if v_rider is null then raise exception 'active rider required'; end if;
 select * into o from public.orders where id=p_order_id and rider_id=v_rider and fulfillment_vertical='ride' for update;
 if not found then raise exception 'assigned passenger ride required'; end if;
 if o.status='completed' then return jsonb_build_object('status',o.status,'fare',o.total_amount); end if;
 perform set_config('queuego.v22_transition','rpc',true);
 if v_action='arrive_pickup' and o.status='rider_assigned' then
  if o.rider_arrived_shop_at is not null then return jsonb_build_object('status',o.status,'fare',o.total_amount); end if;
  update public.orders set rider_arrived_shop_at=coalesce(rider_arrived_shop_at,now()),updated_at=now() where id=o.id;
 elsif v_action='start' and o.status='rider_assigned' and o.rider_arrived_shop_at is not null then
  if not qg_private.ride_public_license_valid(v_rider,now()) then
   if qg_private.requeue_passenger_ride(o.id,v_rider,'public driving license unavailable before passenger pickup',v_user)
   then return jsonb_build_object('status','searching_rider','fare',o.total_amount,'requeued',true); end if;
   raise exception 'verified current public driving license required';
  end if;
  if not qg_private.ride_credit_authorized(v_rider,o.id,o.ride_platform_fee) then
   if qg_private.requeue_passenger_ride(o.id,v_rider,'rider credit unavailable before passenger pickup',v_user)
   then return jsonb_build_object('status','searching_rider','fare',o.total_amount,'requeued',true); end if;
   raise exception 'rider credit authorization required';
  end if;
  update public.orders set status='in_progress',picked_up_at=coalesce(picked_up_at,now()),delivering_at=coalesce(delivering_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='picked_up',updated_at=now() where order_id=o.id;
 elsif v_action='arrive_dropoff' and o.status='in_progress' then
  if o.rider_arrived_customer_at is not null then return jsonb_build_object('status',o.status,'fare',o.total_amount); end if;
  update public.orders set rider_arrived_customer_at=coalesce(rider_arrived_customer_at,now()),updated_at=now() where id=o.id;
 elsif v_action='complete' and o.status='in_progress' and o.rider_arrived_customer_at is not null then
  update public.payments set status='paid',paid_at=coalesce(paid_at,now()),updated_at=now() where order_id=o.id and payment_method='cash' and status='pending';
  if not found and not exists(select 1 from public.payments where order_id=o.id and payment_method='cash' and status='paid') then raise exception 'cash payment record required'; end if;
  update public.orders set status='completed',completed_at=coalesce(completed_at,now()),updated_at=now() where id=o.id;
  update public.deliveries set status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now() where order_id=o.id;
 elsif v_action='start' and o.status='in_progress' then
  return jsonb_build_object('status',o.status,'fare',o.total_amount);
 else raise exception 'ride stage changed; refresh'; end if;
 select status into v_next from public.orders where id=o.id;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_user,'ride_order_action','order',o.id,v_action,jsonb_build_object('from',o.status,'to',v_next,'fare',o.total_amount));
 insert into public.notifications(user_id,title,message,type,reference_id)
 values(o.customer_id,'อัปเดตการเดินทาง',case v_action when 'arrive_pickup' then 'คนขับถึงจุดรับแล้ว' when 'start' then 'เริ่มการเดินทางแล้ว' when 'arrive_dropoff' then 'ถึงจุดหมายแล้ว' else 'จบการเดินทางแล้ว · ชำระเงินสด ฿'||o.total_amount end,'order',o.id);
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
  select o.created_at,jsonb_build_object('id',o.id,'order_number',o.order_number,'status',o.status,'rider_id',o.rider_id,
   'pickup_address',o.pickup_address,'pickup_latitude',o.pickup_latitude,'pickup_longitude',o.pickup_longitude,
   'delivery_address',o.delivery_address,'delivery_latitude',o.delivery_latitude,'delivery_longitude',o.delivery_longitude,
   'fare',o.total_amount,'rider_payout',o.rider_delivery_payout,'distance_km',o.ride_distance_km,'duration_minutes',o.ride_duration_minutes,
   'arrived_pickup_at',o.rider_arrived_shop_at,'arrived_dropoff_at',o.rider_arrived_customer_at,
   'customer_name',cu.name,'rider_name',ru.name,'rider_latitude',case when o.status in ('rider_assigned','in_progress') then r.latitude end,
   'rider_longitude',case when o.status in ('rider_assigned','in_progress') then r.longitude end) data
  from public.orders o left join public.users cu on cu.id=o.customer_id left join public.rider_profiles r on r.id=o.rider_id left join public.users ru on ru.id=r.user_id
  where o.fulfillment_vertical='ride' and (p_order_id is null or o.id=p_order_id)
   and (v_role='admin' or (v_role='customer' and o.customer_id=v_user) or (v_role='rider' and o.rider_id=v_rider))
  order by o.created_at desc limit 30
 ) x;
 return jsonb_build_object('rides',v_rows,'public_license_valid',case when v_role='rider' then qg_private.ride_public_license_valid(v_rider,now()) else null end);
end $$;
revoke all on function public.queuego_ride_state(uuid) from public,anon;
grant execute on function public.queuego_ride_state(uuid) to authenticated;

-- Extend the SAME platform rules, claim, dispatcher and request receipts.
CREATE OR REPLACE FUNCTION public.queuego_platform_rule_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry','feature.ride') then
    if jsonb_typeof(new.value) <> 'boolean' then
      raise exception 'feature rule must be boolean';
    end if;
  elsif new.rule_key in (
    'ride.base_fare','ride.included_km','ride.per_km','ride.minimum_fare','ride.platform_fee_percent',
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
    if new.rule_key='ride.platform_fee_percent' and n>=100 then raise exception 'ride platform fee must be less than fare'; end if;
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
  if not found or not (v_order.order_type='shopping' or (v_order.order_type='service' and v_order.fulfillment_vertical='ride')) then raise exception 'order no longer available'; end if;

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
    values(v_order.customer_id,'พบไรเดอร์แล้ว',case when v_order.fulfillment_vertical='ride' then 'คนขับกำลังไปยังจุดรับผู้โดยสาร' else 'มีไรเดอร์รับออเดอร์แล้ว ร้านค้าจะเริ่มเตรียมสินค้า' end,'order',p_order_id);
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
    rr:=null; v_next:=null; v_accept:=false;

    select r.id,active.last_order,coalesce(pref.auto_accept and not pref.stop_after_work and pref.ready_until>now() and j.market_order_id is null and (j.fulfillment_vertical='ride' or public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())=1),false) into rr,v_next,v_accept
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
CREATE OR REPLACE FUNCTION public.queuego_guard_cash_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_role text;v_own_rider uuid;
begin
  -- qg_ride_order_write_guard enforces the immutable snapshot, ownership and Ride transitions.
  if old.fulfillment_vertical='ride' then return new; end if;
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
