-- Shop-managed Laundry. Additive rollout; historical orders/events are retained.
begin;
alter table public.laundry_orders add column if not exists pickup_staff_name text;
alter table public.laundry_orders add column if not exists return_staff_name text;

create or replace function public.queuego_laundry_shop_manage(
 p_order_id uuid,p_action text,p_expected_status text,p_staff_name text,
 p_actual_quantity numeric,p_note text
) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
 v_actor uuid:=public.get_my_user_id();
 v_order public.laundry_orders%rowtype;
 v_settings public.laundry_shop_settings%rowtype;
 v_target text; v_service numeric; v_staff text:=nullif(btrim(p_staff_name),'');
 v_note text:=nullif(btrim(p_note),'');
begin
 if auth.uid() is null or v_actor is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
 if length(coalesce(v_note,''))>500 or length(coalesce(v_staff,''))>100 then raise exception 'laundry text too long'; end if;
 if p_actual_quantity is not null and (p_actual_quantity<=0 or p_actual_quantity>1000 or p_actual_quantity::text in ('NaN','Infinity','-Infinity')) then raise exception 'invalid actual quantity'; end if;
 select o.* into v_order from public.laundry_orders o
 join public.laundry_hubs h on h.id=o.hub_id join public.shop_profiles s on s.id=h.shop_id
 where o.id=p_order_id and s.user_id=v_actor and s.status='active' and s.archived_at is null for update of o;
 if not found then raise exception 'laundry order unavailable'; end if;
 v_target:=case p_action when 'accept' then 'accepted' when 'start_pickup' then 'pickup_assigned'
 when 'pickup_collected' then 'picked_up' when 'receive_at_shop' then 'at_hub'
 when 'start_washing' then 'washing' when 'ready_return' then 'ready_return'
 when 'start_return' then 'out_for_return' when 'complete_return' then 'completed' when 'cancel' then 'cancelled' end;
 if v_target is null then raise exception 'invalid laundry shop action'; end if;
 if p_action in ('start_pickup','start_return') and v_staff is null then raise exception 'staff name required'; end if;
 -- Repeated taps/retries do not append events or notifications, or alter staff/price.
 if v_order.status=v_target then
  if (p_action='start_pickup' and v_order.pickup_staff_name is distinct from v_staff)
    or (p_action='start_return' and v_order.return_staff_name is distinct from v_staff)
    or (p_action='ready_return' and v_order.pricing_type_snapshot<>'fixed' and v_order.actual_quantity is distinct from p_actual_quantity)
  then raise exception 'laundry action already completed with different details'; end if;
  return jsonb_build_object('id',v_order.id,'status',v_order.status,'replayed',true);
 end if;
 if p_expected_status is not null and v_order.status<>p_expected_status then raise exception 'laundry status changed; refresh'; end if;
 if not ((p_action='accept' and v_order.status='pending')
  or (p_action='start_pickup' and v_order.status='accepted')
  or (p_action='pickup_collected' and v_order.status='pickup_assigned')
  or (p_action='receive_at_shop' and v_order.status='picked_up')
  or (p_action='start_washing' and v_order.status='at_hub')
  or (p_action='ready_return' and v_order.status='washing')
  or (p_action='start_return' and v_order.status in ('ready_return','return_assigned'))
  or (p_action='complete_return' and v_order.status='out_for_return')
  or (p_action='cancel' and v_order.status in ('pending','accepted'))) then raise exception 'invalid laundry transition'; end if;
 if p_action='accept' and v_order.service_id is null then raise exception 'laundry service snapshot required before accept'; end if;
 if p_action='cancel' and v_note is null then raise exception 'cancellation reason required'; end if;
 if exists(select 1 from public.laundry_rider_jobs where laundry_order_id=v_order.id and status not in ('waiting','cancelled','completed')) then
  raise exception 'historical transport job requires resolution before shop-managed action';
 end if;
 if p_action='ready_return' then
  select * into v_settings from public.laundry_shop_settings where hub_id=v_order.hub_id;
  if v_order.service_id is null or v_order.unit_price_snapshot is null then raise exception 'laundry service snapshot required'; end if;
  if v_order.pricing_type_snapshot='fixed' then p_actual_quantity:=1;
  elsif p_actual_quantity is null then raise exception 'actual quantity required'; end if;
  v_service:=greatest(round(v_order.unit_price_snapshot*p_actual_quantity,2),coalesce(v_settings.minimum_order,0));
 end if;
 update public.laundry_orders set status=v_target,
 pickup_staff_name=case when p_action='start_pickup' then v_staff else pickup_staff_name end,
 return_staff_name=case when p_action='start_return' then v_staff else return_staff_name end,
 actual_quantity=case when p_action='ready_return' then p_actual_quantity else actual_quantity end,
 actual_kg=case when p_action='ready_return' and pricing_type_snapshot='per_kg' then p_actual_quantity else actual_kg end,
 final_amount=case when p_action='ready_return' then v_service else final_amount end,
 final_total_amount=case when p_action='ready_return' then v_service+coalesce(delivery_fee_total_snapshot,0) else final_total_amount end,
 updated_at=now() where id=v_order.id;
 insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
 values(v_order.id,v_order.status,v_target,auth.uid(),'shop',coalesce(v_note,case when v_staff is not null then 'ผู้รับผิดชอบ: '||v_staff end));
 insert into public.notifications(user_id,title,message,type,reference_id)
 values(v_order.customer_id,'อัปเดตงานฝากซัก',case v_target
 when 'accepted' then 'ร้านรับคำขอแล้ว ร้านจะจัดพนักงานไปรับผ้า'
 when 'pickup_assigned' then 'พนักงานของร้านกำลังไปรับผ้า'
 when 'picked_up' then 'พนักงานของร้านรับผ้าแล้ว'
 when 'at_hub' then 'ผ้าถึงร้านแล้ว' when 'washing' then 'ร้านกำลังซัก/ทำความสะอาด'
 when 'ready_return' then 'ผ้าพร้อมส่งคืนแล้ว' when 'out_for_return' then 'พนักงานของร้านกำลังส่งผ้าคืน'
 when 'completed' then 'ร้านส่งผ้าคืนสำเร็จ' else 'ร้านยกเลิกคำขอฝากซัก' end,'order',v_order.id);
 return jsonb_build_object('id',v_order.id,'status',v_target,'replayed',false);
end $$;
revoke all on function public.queuego_laundry_shop_manage(uuid,text,text,text,numeric,text) from public,anon;
grant execute on function public.queuego_laundry_shop_manage(uuid,text,text,text,numeric,text) to authenticated,service_role;

-- Existing API delegates to the same state machine; no duplicate implementation.
create or replace function public.queuego_laundry_shop_action_v2(p_order_id uuid,p_action text,p_actual_quantity numeric default null,p_note text default null)
returns jsonb language sql security invoker set search_path=public,pg_temp as $$
 select public.queuego_laundry_shop_manage(p_order_id,p_action,null,null,p_actual_quantity,p_note)
$$;
create or replace function public.queuego_laundry_shop_action(p_order_id uuid,p_action text,p_actual_kg numeric default null,p_note text default null)
returns jsonb language sql security invoker set search_path=public,pg_temp as $$
 select public.queuego_laundry_shop_manage(p_order_id,p_action,null,null,p_actual_kg,p_note)
$$;

-- Contact details only for the owner shop's active Laundry work.
create or replace function public.queuego_laundry_shop_contacts(p_hub_id uuid)
returns table(order_id uuid,customer_name text,customer_phone text)
language sql stable security definer set search_path=public,pg_temp as $$
 select o.id,u.name::text,u.phone::text from public.laundry_orders o
 join public.laundry_hubs h on h.id=o.hub_id join public.shop_profiles s on s.id=h.shop_id
 join public.users u on u.id=o.customer_id
 where o.hub_id=p_hub_id and auth.uid() is not null and public.get_my_role()='shop'
 and s.user_id=public.get_my_user_id() and s.status='active' and s.archived_at is null
 and o.status not in ('completed','cancelled')
$$;
revoke all on function public.queuego_laundry_shop_contacts(uuid) from public,anon;
grant execute on function public.queuego_laundry_shop_contacts(uuid) to authenticated,service_role;
CREATE OR REPLACE FUNCTION public.queuego_place_laundry_order_v2(p_request_id uuid, p_hub_id uuid, p_service_id uuid, p_pickup_address text, p_pickup_latitude double precision, p_pickup_longitude double precision, p_estimated_quantity numeric DEFAULT NULL::numeric, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_customer public.users%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_service public.laundry_services%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_existing public.laundry_orders%rowtype;
  v_order public.laundry_orders%rowtype;
  v_pickup numeric;v_return numeric;v_delivery numeric;v_est_service numeric;v_est_total numeric;v_distance numeric;
begin
  if auth.uid() is null then raise exception 'customer login required'; end if;
  if not public.queuego_feature_enabled('laundry',now()) then raise exception 'laundry is temporarily disabled'; end if;
  if p_request_id is null or p_hub_id is null or p_service_id is null then raise exception 'invalid laundry request'; end if;
  if length(trim(coalesce(p_pickup_address,'')))<3 or length(p_pickup_address)>500 then raise exception 'invalid pickup address'; end if;
  if p_pickup_latitude is null or p_pickup_longitude is null or abs(p_pickup_latitude)>90 or abs(p_pickup_longitude)>180 then
    raise exception 'invalid pickup coordinates';
  end if;
  if p_estimated_quantity is not null and (p_estimated_quantity<=0 or p_estimated_quantity>1000) then raise exception 'invalid estimated quantity'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select * into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
  if not found then raise exception 'active customer required'; end if;
  perform pg_advisory_xact_lock(hashtextextended('laundry-'||p_request_id::text,481));

  select * into v_existing from public.laundry_orders where request_key=p_request_id;
  if found then
    if v_existing.customer_id<>v_customer.id then raise exception 'request id already used'; end if;
    return jsonb_build_object(
      'id',v_existing.id,'order_number',v_existing.order_number,'status',v_existing.status,
      'estimated_total',v_existing.estimated_total_amount,'replayed',true
    );
  end if;

  select * into v_hub from public.laundry_hubs where id=p_hub_id and active=true for share;
  if not found then raise exception 'laundry hub unavailable'; end if;
  select * into v_shop from public.shop_profiles where id=v_hub.shop_id and status='active' for share;
  if not found then raise exception 'laundry shop unavailable'; end if;
  if v_shop.latitude is null or v_shop.longitude is null or (v_shop.latitude=0 and v_shop.longitude=0) then
    raise exception 'laundry shop pickup location required';
  end if;
  v_distance:=public.queuego_assert_service_route(v_shop.latitude,v_shop.longitude,p_pickup_latitude,p_pickup_longitude,'laundry');
  select * into v_settings from public.laundry_shop_settings
   where hub_id=v_hub.id and enabled=true and accepts_pickup=true and accepts_return=true for share;
  if not found then raise exception 'laundry pickup and return unavailable'; end if;
  select * into v_service from public.laundry_services
   where id=p_service_id and hub_id=v_hub.id and active=true for share;
  if not found then raise exception 'laundry service unavailable'; end if;

  if v_settings.delivery_fee_mode='round_trip' then
    v_delivery:=greatest(coalesce(v_settings.round_trip_fee,0),0);
    v_pickup:=round(v_delivery/2,2);
    v_return:=v_delivery-v_pickup;
  else
    v_pickup:=greatest(coalesce(v_settings.base_pickup_fee,0),0);
    v_return:=greatest(coalesce(v_settings.return_fee,0),0);
    v_delivery:=v_pickup+v_return;
  end if;

  if v_service.pricing_type='fixed' then
    v_est_service:=greatest(v_service.price,v_settings.minimum_order);
  elsif p_estimated_quantity is not null then
    v_est_service:=greatest(round(v_service.price*p_estimated_quantity,2),v_settings.minimum_order);
  else
    v_est_service:=null;
  end if;
  v_est_total:=case when v_est_service is null then null else v_est_service+v_delivery end;

  insert into public.laundry_orders(
    customer_id,hub_id,status,service_type,
    pickup_address,pickup_latitude,pickup_longitude,note,
    service_id,service_name_snapshot,pricing_type_snapshot,unit_price_snapshot,
    pickup_fee_snapshot,return_fee_snapshot,round_trip_fee_snapshot,
    estimated_amount,request_key,estimated_quantity,
    delivery_fee_mode_snapshot,delivery_fee_total_snapshot,estimated_total_amount
  ) values(
    v_customer.id,v_hub.id,'pending',v_service.code,
    trim(p_pickup_address),p_pickup_latitude,p_pickup_longitude,nullif(trim(coalesce(p_note,'')),''),
    v_service.id,v_service.name,v_service.pricing_type,v_service.price,
    v_pickup,v_return,v_delivery,
    v_est_service,p_request_id,p_estimated_quantity,
    v_settings.delivery_fee_mode,v_delivery,v_est_total
  ) returning * into v_order;

  insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
  values(v_order.id,null,'pending',auth.uid(),'customer','Laundry request created');

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_shop.user_id,'มีคำขอฝากซักใหม่','คำขอ '||v_order.order_number||' รอร้านรับ','order',v_order.id);

  return jsonb_build_object(
    'id',v_order.id,'order_number',v_order.order_number,'status',v_order.status,
    'service_name',v_order.service_name_snapshot,'pricing_type',v_order.pricing_type_snapshot,
    'unit_price',v_order.unit_price_snapshot,'estimated_quantity',v_order.estimated_quantity,
    'pickup_fee',v_order.pickup_fee_snapshot,'return_fee',v_order.return_fee_snapshot,
    'delivery_fee_total',v_order.delivery_fee_total_snapshot,'estimated_total',v_order.estimated_total_amount,
    'replayed',false
  );
end $function$
;


-- Archived platform-Rider endpoints remain in schema for history, but are no longer client APIs.
do $retire$
declare f record;
begin
 for f in select p.oid::regprocedure as signature from pg_proc p
 join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in
 ('queuego_set_laundry_rider_mode','queuego_laundry_rider_invite_action','queuego_claim_laundry_job',
  'queuego_laundry_rider_action','queuego_laundry_rider_pool','queuego_laundry_rider_state',
  'queuego_laundry_rider_active_job','queuego_laundry_rider_invites','queuego_laundry_invite_rider')
 loop execute format('revoke all on function %s from public,anon,authenticated',f.signature); end loop;
end $retire$;
commit;
