-- QueueGo Laundry merchant controls + route direction hardening
-- Reuses existing laundry tables and workflow. Additive only.

alter table public.laundry_orders
  add column if not exists delivery_fee_mode_snapshot text;

create or replace function public.queuego_laundry_merchant_snapshot()
returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_user uuid:=public.get_my_user_id();
  v_shop public.shop_profiles%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
begin
  if v_user is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  select * into v_shop from public.shop_profiles where user_id=v_user limit 1;
  if not found then raise exception 'shop profile unavailable'; end if;
  select * into v_hub from public.laundry_hubs where shop_id=v_shop.id order by created_at limit 1;
  if not found then
    return jsonb_build_object(
      'shop_id',v_shop.id,'shop_name',v_shop.shop_name,'hub',null,'settings',null,
      'services','[]'::jsonb,'orders','[]'::jsonb
    );
  end if;
  select * into v_settings from public.laundry_shop_settings where hub_id=v_hub.id;
  return jsonb_build_object(
    'shop_id',v_shop.id,
    'shop_name',v_shop.shop_name,
    'hub',to_jsonb(v_hub),
    'settings',case when v_settings.id is null then null else to_jsonb(v_settings) end,
    'services',coalesce((
      select jsonb_agg(to_jsonb(s) order by s.sort_order,s.created_at)
      from public.laundry_services s where s.hub_id=v_hub.id
    ),'[]'::jsonb),
    'orders',coalesce((
      select jsonb_agg(to_jsonb(o) order by o.created_at desc)
      from (
        select id,order_number,status,service_name_snapshot,pricing_type_snapshot,unit_price_snapshot,
               pickup_fee_snapshot,return_fee_snapshot,round_trip_fee_snapshot,delivery_fee_mode_snapshot,
               estimated_kg,actual_kg,estimated_amount,final_amount,pickup_address,note,created_at,updated_at
        from public.laundry_orders
        where hub_id=v_hub.id
        order by created_at desc
        limit 100
      ) o
    ),'[]'::jsonb)
  );
end $$;

create or replace function public.queuego_laundry_save_settings(
  p_enabled boolean,
  p_base_pickup_fee numeric,
  p_return_fee numeric,
  p_round_trip_fee numeric,
  p_delivery_fee_mode text,
  p_minimum_order numeric default 0
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_user uuid:=public.get_my_user_id();
  v_shop public.shop_profiles%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
begin
  if v_user is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  if p_delivery_fee_mode not in ('separate','round_trip') then raise exception 'invalid delivery fee mode'; end if;
  if least(coalesce(p_base_pickup_fee,0),coalesce(p_return_fee,0),coalesce(p_round_trip_fee,0),coalesce(p_minimum_order,0))<0 then
    raise exception 'laundry fees cannot be negative';
  end if;
  if greatest(coalesce(p_base_pickup_fee,0),coalesce(p_return_fee,0),coalesce(p_round_trip_fee,0),coalesce(p_minimum_order,0))>100000 then
    raise exception 'laundry fee too large';
  end if;

  select * into v_shop from public.shop_profiles where user_id=v_user and status='active' limit 1;
  if not found then raise exception 'active shop required'; end if;
  perform pg_advisory_xact_lock(hashtextextended('laundry-shop-'||v_shop.id::text,623));

  select * into v_hub from public.laundry_hubs where shop_id=v_shop.id order by created_at limit 1 for update;
  if not found then
    insert into public.laundry_hubs(shop_id,name,active)
    values(v_shop.id,coalesce(nullif(trim(v_shop.shop_name),''),'บริการฝากซัก'),true)
    returning * into v_hub;
  else
    update public.laundry_hubs set name=coalesce(nullif(trim(v_shop.shop_name),''),name),active=true
    where id=v_hub.id returning * into v_hub;
  end if;

  insert into public.laundry_shop_settings(
    hub_id,enabled,accepts_pickup,accepts_return,minimum_order,base_pickup_fee,
    return_fee,round_trip_fee,delivery_fee_mode,currency,created_at,updated_at
  ) values(
    v_hub.id,coalesce(p_enabled,false),true,true,greatest(coalesce(p_minimum_order,0),0),
    greatest(coalesce(p_base_pickup_fee,0),0),greatest(coalesce(p_return_fee,0),0),
    greatest(coalesce(p_round_trip_fee,0),0),p_delivery_fee_mode,'THB',now(),now()
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
    currency='THB',
    updated_at=now()
  returning * into v_settings;

  return jsonb_build_object('hub',to_jsonb(v_hub),'settings',to_jsonb(v_settings));
end $$;

create or replace function public.queuego_laundry_save_service(
  p_service_id uuid,
  p_name text,
  p_description text,
  p_pricing_type text,
  p_price numeric,
  p_estimated_minutes integer,
  p_active boolean
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_user uuid:=public.get_my_user_id();
  v_shop public.shop_profiles%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_service public.laundry_services%rowtype;
  v_code text;
begin
  if v_user is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  if length(trim(coalesce(p_name,'')))<2 or length(p_name)>120 then raise exception 'service name required'; end if;
  if p_pricing_type not in ('per_kg','per_item','per_set','fixed') then raise exception 'invalid pricing type'; end if;
  if coalesce(p_price,-1)<0 or p_price>100000 then raise exception 'invalid service price'; end if;
  if p_estimated_minutes is not null and (p_estimated_minutes<1 or p_estimated_minutes>10080) then raise exception 'invalid estimated time'; end if;
  if length(coalesce(p_description,''))>500 then raise exception 'description too long'; end if;

  select * into v_shop from public.shop_profiles where user_id=v_user and status='active' limit 1;
  if not found then raise exception 'active shop required'; end if;
  select * into v_hub from public.laundry_hubs where shop_id=v_shop.id order by created_at limit 1;
  if not found then raise exception 'save laundry settings before adding services'; end if;

  if p_service_id is null then
    v_code:='custom_'||replace(substr(gen_random_uuid()::text,1,8),'-','');
    insert into public.laundry_services(
      hub_id,code,name,description,pricing_type,price,estimated_minutes,active,sort_order
    ) values(
      v_hub.id,v_code,trim(p_name),nullif(trim(coalesce(p_description,'')),''),
      p_pricing_type,p_price,p_estimated_minutes,coalesce(p_active,true),
      coalesce((select max(sort_order)+1 from public.laundry_services where hub_id=v_hub.id),0)
    ) returning * into v_service;
  else
    update public.laundry_services
    set name=trim(p_name),
        description=nullif(trim(coalesce(p_description,'')),''),
        pricing_type=p_pricing_type,
        price=p_price,
        estimated_minutes=p_estimated_minutes,
        active=coalesce(p_active,true)
    where id=p_service_id and hub_id=v_hub.id
    returning * into v_service;
    if not found then raise exception 'laundry service unavailable'; end if;
  end if;

  return to_jsonb(v_service);
end $$;

-- Preserve fee mode and allocate a combined round-trip fee across both rider legs.
create or replace function public.queuego_place_laundry_order(
  p_request_id uuid,
  p_hub_id uuid,
  p_service_id uuid,
  p_pickup_address text,
  p_pickup_latitude double precision,
  p_pickup_longitude double precision,
  p_note text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_customer public.users%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_service public.laundry_services%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_existing public.laundry_orders%rowtype;
  v_order public.laundry_orders%rowtype;
  v_pickup numeric;v_return numeric;v_round numeric;
begin
  if auth.uid() is null then raise exception 'customer login required'; end if;
  if not public.queuego_feature_enabled('laundry',now()) then raise exception 'laundry is temporarily disabled'; end if;
  if p_request_id is null or p_hub_id is null or p_service_id is null then raise exception 'invalid laundry request'; end if;
  if length(trim(coalesce(p_pickup_address,'')))<3 or length(p_pickup_address)>500 then raise exception 'invalid pickup address'; end if;
  if p_pickup_latitude is null or p_pickup_longitude is null or abs(p_pickup_latitude)>90 or abs(p_pickup_longitude)>180 then raise exception 'invalid pickup coordinates'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select * into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
  if not found then raise exception 'active customer required'; end if;
  perform pg_advisory_xact_lock(hashtextextended('laundry-'||p_request_id::text,481));

  select * into v_existing from public.laundry_orders where request_key=p_request_id;
  if found then
    if v_existing.customer_id<>v_customer.id then raise exception 'request id already used'; end if;
    return jsonb_build_object('id',v_existing.id,'order_number',v_existing.order_number,'status',v_existing.status,'replayed',true);
  end if;

  select * into v_hub from public.laundry_hubs where id=p_hub_id and active=true for share;
  if not found then raise exception 'laundry hub unavailable'; end if;
  select * into v_shop from public.shop_profiles where id=v_hub.shop_id and status='active' for share;
  if not found then raise exception 'laundry shop unavailable'; end if;
  select * into v_settings from public.laundry_shop_settings
  where hub_id=v_hub.id and enabled=true and accepts_pickup=true and accepts_return=true for share;
  if not found then raise exception 'laundry pickup/return unavailable'; end if;
  select * into v_service from public.laundry_services
  where id=p_service_id and hub_id=v_hub.id and active=true for share;
  if not found then raise exception 'laundry service unavailable'; end if;

  if v_settings.delivery_fee_mode='round_trip' then
    v_round:=greatest(coalesce(v_settings.round_trip_fee,0),0);
    v_pickup:=floor(v_round/2*100)/100;
    v_return:=round(v_round-v_pickup,2);
  else
    v_pickup:=greatest(coalesce(v_settings.base_pickup_fee,0),0);
    v_return:=greatest(coalesce(v_settings.return_fee,0),0);
    v_round:=round(v_pickup+v_return,2);
  end if;

  insert into public.laundry_orders(
    customer_id,hub_id,status,service_type,pickup_address,pickup_latitude,pickup_longitude,note,
    service_id,service_name_snapshot,pricing_type_snapshot,unit_price_snapshot,
    pickup_fee_snapshot,return_fee_snapshot,round_trip_fee_snapshot,delivery_fee_mode_snapshot,
    estimated_amount,request_key
  ) values(
    v_customer.id,v_hub.id,'pending',v_service.code,trim(p_pickup_address),p_pickup_latitude,p_pickup_longitude,
    nullif(trim(coalesce(p_note,'')),''),
    v_service.id,v_service.name,v_service.pricing_type,v_service.price,
    v_pickup,v_return,v_round,v_settings.delivery_fee_mode,
    case when v_service.pricing_type='fixed' then greatest(v_service.price,v_settings.minimum_order) else null end,
    p_request_id
  ) returning * into v_order;

  insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
  values(v_order.id,null,'pending',auth.uid(),'customer','Laundry request created');
  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_shop.user_id,'มีคำขอฝากซักใหม่','คำขอ '||v_order.order_number||' รอร้านรับ','order',v_order.id);

  return jsonb_build_object(
    'id',v_order.id,'order_number',v_order.order_number,'status',v_order.status,
    'service_name',v_order.service_name_snapshot,'unit_price',v_order.unit_price_snapshot,
    'pickup_fee',v_order.pickup_fee_snapshot,'return_fee',v_order.return_fee_snapshot,
    'round_trip_fee',v_order.round_trip_fee_snapshot,'delivery_fee_mode',v_order.delivery_fee_mode_snapshot,
    'replayed',false
  );
end $$;

-- Correct pickup leg direction: customer -> laundry hub. Return leg: hub -> customer.
create or replace function public.queuego_laundry_rider_pool()
returns table(
  job_id uuid,laundry_order_id uuid,order_number text,leg text,hub_id uuid,hub_name text,shop_name text,
  from_address text,from_latitude double precision,from_longitude double precision,
  to_address text,to_latitude double precision,to_longitude double precision,
  job_fee numeric,service_name text,actual_kg numeric,created_at timestamptz
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare v_rider uuid;
begin
  if not public.queuego_feature_enabled('laundry',now()) then return; end if;
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  join public.laundry_rider_preferences p on p.rider_id=r.id and p.laundry_mode_enabled=true
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true;
  if v_rider is null then return; end if;

  if exists(select 1 from public.orders where rider_id=v_rider and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress'))
     or exists(select 1 from public.laundry_rider_jobs where rider_id=v_rider and status in ('assigned','accepted','arrived','collected'))
  then return; end if;

  return query
  select j.id,o.id,o.order_number,j.leg,h.id,h.name,s.shop_name,
         case when j.leg='pickup' then o.pickup_address else s.address end,
         case when j.leg='pickup' then o.pickup_latitude else s.latitude end,
         case when j.leg='pickup' then o.pickup_longitude else s.longitude end,
         case when j.leg='pickup' then s.address else o.pickup_address end,
         case when j.leg='pickup' then s.latitude else o.pickup_latitude end,
         case when j.leg='pickup' then s.longitude else o.pickup_longitude end,
         case when j.leg='pickup' then coalesce(o.pickup_fee_snapshot,0) else coalesce(o.return_fee_snapshot,0) end,
         o.service_name_snapshot,o.actual_kg,j.created_at
  from public.laundry_rider_jobs j
  join public.laundry_orders o on o.id=j.laundry_order_id
  join public.laundry_hubs h on h.id=o.hub_id
  join public.shop_profiles s on s.id=h.shop_id
  join public.laundry_hub_riders hr on hr.hub_id=h.id and hr.rider_id=v_rider and hr.active=true
  where j.status='waiting' and j.rider_id is null
    and ((j.leg='pickup' and o.status='accepted') or (j.leg='return' and o.status='ready_return'))
  order by j.created_at
  limit 20;
end $$;

revoke all on function public.queuego_laundry_merchant_snapshot() from public,anon;
revoke all on function public.queuego_laundry_save_settings(boolean,numeric,numeric,numeric,text,numeric) from public,anon;
revoke all on function public.queuego_laundry_save_service(uuid,text,text,text,numeric,integer,boolean) from public,anon;
grant execute on function public.queuego_laundry_merchant_snapshot() to authenticated,service_role;
grant execute on function public.queuego_laundry_save_settings(boolean,numeric,numeric,numeric,text,numeric) to authenticated,service_role;
grant execute on function public.queuego_laundry_save_service(uuid,text,text,text,numeric,integer,boolean) to authenticated,service_role;
