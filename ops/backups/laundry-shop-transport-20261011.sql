-- queuego_laundry_merchant_state()
CREATE OR REPLACE FUNCTION public.queuego_laundry_merchant_state()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user uuid:=public.get_my_user_id();
  v_shop uuid;
  v_hub uuid;
begin
  if v_user is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  select id into v_shop from public.shop_profiles where user_id=v_user and archived_at is null order by created_at desc limit 1;
  if v_shop is null then raise exception 'shop profile unavailable'; end if;
  select id into v_hub from public.laundry_hubs where shop_id=v_shop order by created_at limit 1;

  return jsonb_build_object(
    'hub',(select to_jsonb(h) from public.laundry_hubs h where h.id=v_hub),
    'settings',(select to_jsonb(s) from public.laundry_shop_settings s where s.hub_id=v_hub),
    'services',coalesce((select jsonb_agg(to_jsonb(s) order by s.sort_order,s.created_at) from public.laundry_services s where s.hub_id=v_hub),'[]'::jsonb),
    'riders',coalesce((
      select jsonb_agg(jsonb_build_object(
        'rider_id',r.id,'name',u.name,'phone',u.phone,'active',hr.active
      ) order by u.name)
      from public.laundry_hub_riders hr
      join public.rider_profiles r on r.id=hr.rider_id
      join public.users u on u.id=r.user_id
      where hr.hub_id=v_hub
    ),'[]'::jsonb),
    'invites',coalesce((
      select jsonb_agg(jsonb_build_object(
        'id',i.id,'rider_id',i.rider_id,'status',i.status,
        'name',u.name,'phone',u.phone,'created_at',i.created_at
      ) order by i.created_at desc)
      from public.laundry_rider_invites i
      join public.rider_profiles r on r.id=i.rider_id
      join public.users u on u.id=r.user_id
      where i.hub_id=v_hub
    ),'[]'::jsonb)
  );
end $function$
;

-- queuego_laundry_shop_action(p_order_id uuid, p_action text, p_actual_kg numeric, p_note text)
CREATE OR REPLACE FUNCTION public.queuego_laundry_shop_action(p_order_id uuid, p_action text, p_actual_kg numeric DEFAULT NULL::numeric, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=public.get_my_user_id();
  v_order public.laundry_orders%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_from text;
  v_to text;
  v_amount numeric;
begin
  if v_actor is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  if p_order_id is null then raise exception 'invalid laundry order'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select o.* into v_order
  from public.laundry_orders o
  join public.laundry_hubs h on h.id=o.hub_id
  join public.shop_profiles s on s.id=h.shop_id
  where o.id=p_order_id and s.user_id=v_actor
  for update of o;
  if not found then raise exception 'laundry order unavailable'; end if;

  select * into v_settings from public.laundry_shop_settings where hub_id=v_order.hub_id;
  select s.* into v_shop
  from public.laundry_hubs h join public.shop_profiles s on s.id=h.shop_id
  where h.id=v_order.hub_id;

  v_from:=v_order.status;

  if p_action='accept' then
    if v_order.status<>'pending' then raise exception 'laundry status changed; refresh'; end if;
    v_to:='accepted';
    insert into public.laundry_rider_jobs(laundry_order_id,leg,status)
    values(v_order.id,'pickup','waiting')
    on conflict(laundry_order_id,leg) do nothing;

  elsif p_action='start_washing' then
    if v_order.status<>'at_hub' then raise exception 'laundry must be at hub first'; end if;
    v_to:='washing';

  elsif p_action='ready_return' then
    if v_order.status<>'washing' then raise exception 'laundry is not washing'; end if;
    if v_order.pricing_type_snapshot='per_kg' then
      if p_actual_kg is null or p_actual_kg<=0 or p_actual_kg>200 then raise exception 'actual kg required'; end if;
      v_amount:=greatest(
        round(coalesce(v_order.unit_price_snapshot,0)*p_actual_kg,2),
        coalesce(v_settings.minimum_order,0)
      );
    else
      v_amount:=greatest(coalesce(v_order.unit_price_snapshot,0),coalesce(v_settings.minimum_order,0));
    end if;
    v_to:='ready_return';
    if coalesce(v_settings.accepts_return,false) then
      insert into public.laundry_rider_jobs(laundry_order_id,leg,status)
      values(v_order.id,'return','waiting')
      on conflict(laundry_order_id,leg) do nothing;
    end if;

  elsif p_action='complete_at_hub' then
    if v_order.status<>'ready_return' or coalesce(v_settings.accepts_return,false) then
      raise exception 'return delivery is enabled for this laundry';
    end if;
    v_to:='completed';

  elsif p_action='cancel' then
    if v_order.status not in ('pending','accepted') then raise exception 'laundry can no longer be cancelled by shop'; end if;
    if exists(
      select 1 from public.laundry_rider_jobs
      where laundry_order_id=v_order.id and status not in ('waiting','cancelled')
    ) then raise exception 'rider already started laundry pickup'; end if;
    v_to:='cancelled';
    update public.laundry_rider_jobs
    set status='cancelled',updated_at=now()
    where laundry_order_id=v_order.id and status='waiting';

  else
    raise exception 'invalid laundry shop action';
  end if;

  update public.laundry_orders
  set status=v_to,
      actual_kg=case when p_action='ready_return' and pricing_type_snapshot='per_kg' then p_actual_kg else actual_kg end,
      final_amount=case when p_action='ready_return' then v_amount else final_amount end,
      note=case when nullif(trim(coalesce(p_note,'')),'') is not null then trim(p_note) else note end,
      updated_at=now()
  where id=v_order.id
  returning * into v_order;

  insert into public.laundry_order_events(
    laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note
  ) values(v_order.id,v_from,v_to,auth.uid(),'shop',nullif(trim(coalesce(p_note,'')),''));

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(
    v_order.customer_id,
    'อัปเดตงานฝากซัก',
    case v_to
      when 'accepted' then 'ร้านรับคำขอแล้ว กำลังหา Rider ไปรับผ้า'
      when 'washing' then 'ร้านกำลังซักผ้าของคุณ'
      when 'ready_return' then 'ผ้าพร้อมส่งคืนแล้ว'
      when 'completed' then 'งานฝากซักเสร็จสมบูรณ์'
      when 'cancelled' then 'ร้านยกเลิกคำขอฝากซัก'
      else 'สถานะงานฝากซักมีการเปลี่ยนแปลง'
    end,
    'order',v_order.id
  );

  return jsonb_build_object(
    'id',v_order.id,'order_number',v_order.order_number,'status',v_order.status,
    'actual_kg',v_order.actual_kg,'final_amount',v_order.final_amount
  );
end
$function$
;

-- queuego_laundry_shop_action_v2(p_order_id uuid, p_action text, p_actual_quantity numeric, p_note text)
CREATE OR REPLACE FUNCTION public.queuego_laundry_shop_action_v2(p_order_id uuid, p_action text, p_actual_quantity numeric DEFAULT NULL::numeric, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=public.get_my_user_id();
  v_order public.laundry_orders%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_from text;v_to text;v_service_amount numeric;v_final_total numeric;
begin
  if v_actor is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select o.* into v_order
  from public.laundry_orders o
  join public.laundry_hubs h on h.id=o.hub_id
  join public.shop_profiles s on s.id=h.shop_id
  where o.id=p_order_id and s.user_id=v_actor
  for update of o;
  if not found then raise exception 'laundry order unavailable'; end if;
  select * into v_settings from public.laundry_shop_settings where hub_id=v_order.hub_id;
  v_from:=v_order.status;

  if p_action='accept' then
    if v_order.status<>'pending' then raise exception 'laundry status changed; refresh'; end if;
    v_to:='accepted';
    insert into public.laundry_rider_jobs(laundry_order_id,leg,status)
    values(v_order.id,'pickup','waiting') on conflict(laundry_order_id,leg) do nothing;

  elsif p_action='start_washing' then
    if v_order.status<>'at_hub' then raise exception 'laundry must be at hub first'; end if;
    v_to:='washing';

  elsif p_action='ready_return' then
    if v_order.status<>'washing' then raise exception 'laundry is not washing'; end if;
    if v_order.pricing_type_snapshot='fixed' then
      v_service_amount:=greatest(coalesce(v_order.unit_price_snapshot,0),coalesce(v_settings.minimum_order,0));
      p_actual_quantity:=1;
    else
      if p_actual_quantity is null or p_actual_quantity<=0 or p_actual_quantity>1000 then
        raise exception 'actual quantity required';
      end if;
      v_service_amount:=greatest(
        round(coalesce(v_order.unit_price_snapshot,0)*p_actual_quantity,2),
        coalesce(v_settings.minimum_order,0)
      );
    end if;
    v_final_total:=v_service_amount+coalesce(v_order.delivery_fee_total_snapshot,0);
    v_to:='ready_return';
    insert into public.laundry_rider_jobs(laundry_order_id,leg,status)
    values(v_order.id,'return','waiting') on conflict(laundry_order_id,leg) do nothing;

  elsif p_action='cancel' then
    if v_order.status not in ('pending','accepted') then raise exception 'laundry can no longer be cancelled by shop'; end if;
    if exists(select 1 from public.laundry_rider_jobs where laundry_order_id=v_order.id and status not in ('waiting','cancelled')) then
      raise exception 'Rider already started laundry pickup';
    end if;
    v_to:='cancelled';
    update public.laundry_rider_jobs set status='cancelled',updated_at=now()
    where laundry_order_id=v_order.id and status='waiting';

  else
    raise exception 'invalid laundry shop action';
  end if;

  update public.laundry_orders
  set status=v_to,
      actual_quantity=case when p_action='ready_return' then p_actual_quantity else actual_quantity end,
      actual_kg=case when p_action='ready_return' and pricing_type_snapshot='per_kg' then p_actual_quantity else actual_kg end,
      final_amount=case when p_action='ready_return' then v_service_amount else final_amount end,
      final_total_amount=case when p_action='ready_return' then v_final_total else final_total_amount end,
      note=case when nullif(trim(coalesce(p_note,'')),'') is not null then trim(p_note) else note end,
      updated_at=now()
  where id=v_order.id returning * into v_order;

  insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
  values(v_order.id,v_from,v_to,auth.uid(),'shop',nullif(trim(coalesce(p_note,'')),''));

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_order.customer_id,'อัปเดตงานฝากซัก',
    case v_to
      when 'accepted' then 'ร้านรับคำขอแล้ว กำลังหา Rider ไปรับผ้า'
      when 'washing' then 'ร้านกำลังดำเนินการซัก/ทำความสะอาด'
      when 'ready_return' then 'งานเสร็จแล้ว กำลังหา Rider ส่งคืน'
      when 'cancelled' then 'ร้านยกเลิกคำขอฝากซัก'
      else 'สถานะงานฝากซักมีการเปลี่ยนแปลง'
    end,'order',v_order.id);

  return jsonb_build_object(
    'id',v_order.id,'order_number',v_order.order_number,'status',v_order.status,
    'actual_quantity',v_order.actual_quantity,'service_amount',v_order.final_amount,
    'delivery_fee_total',v_order.delivery_fee_total_snapshot,'final_total',v_order.final_total_amount
  );
end $function$
;

-- queuego_place_laundry_order_v2(p_request_id uuid, p_hub_id uuid, p_service_id uuid, p_pickup_address text, p_pickup_latitude double precision, p_pickup_longitude double precision, p_estimated_quantity numeric, p_note text)
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
end $function$
;

-- Production settings function before retiring the accepted-platform-Rider prerequisite.
CREATE OR REPLACE FUNCTION public.queuego_laundry_save_settings(p_enabled boolean, p_minimum_order numeric, p_pickup_fee numeric, p_return_fee numeric, p_round_trip_fee numeric, p_delivery_fee_mode text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user uuid:=public.get_my_user_id();
  v_hub uuid;
  v_settings public.laundry_shop_settings%rowtype;
begin
  if v_user is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  select h.id into v_hub
  from public.laundry_hubs h
  join public.shop_profiles s on s.id=h.shop_id
  where s.user_id=v_user and s.archived_at is null
  order by h.created_at
  limit 1;
  if v_hub is null then raise exception 'setup laundry service first'; end if;
  if p_delivery_fee_mode not in ('separate','round_trip') then raise exception 'invalid delivery fee mode'; end if;
  if greatest(coalesce(p_minimum_order,0),coalesce(p_pickup_fee,0),coalesce(p_return_fee,0),coalesce(p_round_trip_fee,0))>100000
     or least(coalesce(p_minimum_order,0),coalesce(p_pickup_fee,0),coalesce(p_return_fee,0),coalesce(p_round_trip_fee,0))<0
  then raise exception 'invalid laundry pricing'; end if;

  if coalesce(p_enabled,false) then
    if not exists(select 1 from public.laundry_services where hub_id=v_hub and active=true) then
      raise exception 'add at least one active laundry service';
    end if;
    if not exists(select 1 from public.laundry_hub_riders where hub_id=v_hub and active=true) then
      raise exception 'at least one accepted laundry Rider is required';
    end if;
  end if;

  insert into public.laundry_shop_settings(
    hub_id,enabled,accepts_pickup,accepts_return,minimum_order,
    base_pickup_fee,return_fee,round_trip_fee,delivery_fee_mode,updated_at
  ) values(
    v_hub,coalesce(p_enabled,false),true,true,greatest(coalesce(p_minimum_order,0),0),
    greatest(coalesce(p_pickup_fee,0),0),greatest(coalesce(p_return_fee,0),0),
    greatest(coalesce(p_round_trip_fee,0),0),p_delivery_fee_mode,now()
  )
  on conflict(hub_id) do update set
    enabled=excluded.enabled,
    accepts_pickup=true,
    accepts_return=true,
    minimum_order=excluded.minimum_order,
    base_pickup_fee=excluded.base_pickup_fee,
    return_fee=excluded.return_fee,
    round_trip_fee=excluded.round_trip_fee,
    delivery_fee_mode=excluded.delivery_fee_mode,
    updated_at=now()
  returning * into v_settings;

  return to_jsonb(v_settings);
end $function$

;
