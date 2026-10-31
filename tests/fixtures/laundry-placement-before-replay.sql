-- Inspected pre-change Production definition, retained only for isolated regression/rollback review.
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
  if not exists(select 1 from public.laundry_hub_riders where hub_id=v_hub.id and active=true) then
    raise exception 'laundry Rider unavailable';
  end if;
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
end $function$;
