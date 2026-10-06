-- QueueGo Pilot Gate 2: Service Area
-- Production mirror. Pilot area is admin/database configurable without changing checkout clients.

create table if not exists public.queuego_service_areas (
 id uuid primary key default gen_random_uuid(),
 name text not null,
 province text not null,
 center_latitude double precision not null check (center_latitude between -90 and 90),
 center_longitude double precision not null check (center_longitude between -180 and 180),
 radius_km numeric not null check (radius_km>0 and radius_km<=200),
 max_delivery_km numeric not null check (max_delivery_km>0 and max_delivery_km<=100),
 verticals text[] not null default array['delivery','food','cafe','grocery','market','laundry']::text[],
 active boolean not null default true,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create unique index if not exists queuego_service_areas_name_uidx on public.queuego_service_areas(lower(name));
alter table public.queuego_service_areas enable row level security;
drop policy if exists queuego_service_areas_public_active_read on public.queuego_service_areas;
create policy queuego_service_areas_public_active_read on public.queuego_service_areas for select to anon,authenticated using(active);
revoke all on public.queuego_service_areas from anon,authenticated;
grant select on public.queuego_service_areas to anon,authenticated;

insert into public.queuego_service_areas(name,province,center_latitude,center_longitude,radius_km,max_delivery_km,verticals,active)
select 'Buriram Pilot','บุรีรัมย์',14.99292,103.10804,20,15,array['delivery','food','cafe','grocery','market','laundry']::text[],true
where not exists(select 1 from public.queuego_service_areas where lower(name)=lower('Buriram Pilot'));

CREATE OR REPLACE FUNCTION public.queuego_service_area_route_status(p_pickup_lat double precision, p_pickup_lng double precision, p_delivery_lat double precision, p_delivery_lng double precision, p_vertical text DEFAULT 'delivery'::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_area public.queuego_service_areas%rowtype;v_direct numeric;
begin
 if p_pickup_lat is null or p_pickup_lng is null or p_delivery_lat is null or p_delivery_lng is null
 or abs(p_pickup_lat)>90 or abs(p_delivery_lat)>90 or abs(p_pickup_lng)>180 or abs(p_delivery_lng)>180
 or (p_pickup_lat=0 and p_pickup_lng=0) or (p_delivery_lat=0 and p_delivery_lng=0)
 then return jsonb_build_object('available',false,'code','INVALID_LOCATION'); end if;
 select a.* into v_area from public.queuego_service_areas a
 where a.active
 and (coalesce(nullif(lower(trim(p_vertical)),''),'delivery')=any(a.verticals) or 'delivery'=any(a.verticals))
 and public.queuego_distance_km(a.center_latitude,a.center_longitude,p_pickup_lat,p_pickup_lng)<=a.radius_km
 and public.queuego_distance_km(a.center_latitude,a.center_longitude,p_delivery_lat,p_delivery_lng)<=a.radius_km
 order by a.radius_km,a.created_at limit 1;
 v_direct:=public.queuego_distance_km(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng);
 if v_area.id is null then
   return jsonb_build_object('available',false,'code','OUTSIDE_SERVICE_AREA','distance_km',v_direct);
 end if;
 if v_direct is null or v_direct>v_area.max_delivery_km then
   return jsonb_build_object('available',false,'code','DELIVERY_DISTANCE_EXCEEDED','area_id',v_area.id,'area_name',v_area.name,'distance_km',v_direct,'max_delivery_km',v_area.max_delivery_km);
 end if;
 return jsonb_build_object('available',true,'code','OK','area_id',v_area.id,'area_name',v_area.name,'distance_km',v_direct,'max_delivery_km',v_area.max_delivery_km);
end $function$;
CREATE OR REPLACE FUNCTION public.queuego_assert_service_route(p_pickup_lat double precision, p_pickup_lng double precision, p_delivery_lat double precision, p_delivery_lng double precision, p_vertical text DEFAULT 'delivery'::text)
 RETURNS numeric
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v jsonb;
begin
 v:=public.queuego_service_area_route_status(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,p_vertical);
 if coalesce((v->>'available')::boolean,false) is not true then
   raise exception '%',coalesce(v->>'code','OUTSIDE_SERVICE_AREA') using errcode='P0001',detail=v::text;
 end if;
 return (v->>'distance_km')::numeric;
end $function$;
CREATE OR REPLACE FUNCTION public.queuego_place_cash_order_core(p_order_id uuid, p_shop_id uuid, p_items jsonb, p_delivery_lat double precision, p_delivery_lng double precision, p_delivery_address text, p_expected_subtotal numeric, p_expected_delivery_fee numeric, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_customer uuid; v_shop public.shop_profiles%rowtype; v_product public.products%rowtype; v_item jsonb;
  v_product_id uuid; v_qty integer; v_count integer:=0; v_subtotal numeric:=0; v_fee numeric; v_total numeric;
  v_distance double precision; v_a double precision; v_number text;
  v_old public.orders%rowtype; v_seen uuid[]:=array[]::uuid[];
begin
  if auth.uid() is null then raise exception 'customer login required'; end if;
  perform set_config('queuego.market_checkout','on',true);
  select u.id into v_customer from public.users u where u.auth_user_id=auth.uid() and u.role='customer' and u.status='active';
  if v_customer is null then raise exception 'active customer required'; end if;
  if p_order_id is null then raise exception 'order id required'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_customer::text,121));
  select * into v_old from public.orders where id=p_order_id;
  if found then
    if v_old.customer_id is distinct from v_customer then raise exception 'order id already in use'; end if;
    return jsonb_build_object('id',v_old.id,'order_number',v_old.order_number,'status',v_old.status,'subtotal',v_old.subtotal,'delivery_fee',v_old.delivery_fee,'total_amount',v_old.total_amount,'gp_amount',v_old.gp_amount,'replayed',true);
  end if;
  if exists(select 1 from public.orders where customer_id=v_customer and order_type='shopping'
    and status in ('pending','accepted','searching_rider','rider_assigned','preparing','ready','assigned','picked_up','in_progress')) then
    raise exception 'customer has an active order';
  end if;
  if p_shop_id is null or p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 30 then raise exception 'invalid cart'; end if;
  if p_delivery_lat is null or p_delivery_lng is null or abs(p_delivery_lat)>90 or abs(p_delivery_lng)>180 or (p_delivery_lat=0 and p_delivery_lng=0) then raise exception 'delivery location required'; end if;
  if length(trim(coalesce(p_delivery_address,'')))<3 or length(p_delivery_address)>500 then raise exception 'delivery address required'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;
  select s.* into v_shop from public.shop_profiles s join public.users u on u.id=s.user_id
  where s.id=p_shop_id and s.status='active' and u.role='shop' and u.status='active' for share of s;
  if not found then raise exception 'shop unavailable'; end if;
  if v_shop.latitude is null or v_shop.longitude is null or (v_shop.latitude=0 and v_shop.longitude=0) then raise exception 'shop pickup location required'; end if;
  if exists(select 1 from public.shop_open_states x where x.shop_id=p_shop_id and x.is_open=false and (x.resume_at is null or x.resume_at>now())) then raise exception 'shop closed'; end if;
  v_distance:=public.queuego_assert_service_route(v_shop.latitude,v_shop.longitude,p_delivery_lat,p_delivery_lng,coalesce(v_shop.public_category,'delivery'))::double precision;
  v_fee:=case when v_distance<=5 then 30 when v_distance<=6 then 40 when v_distance<=7 then 50 else 50+ceil(v_distance-7)*10 end;
  for v_item in select value from jsonb_array_elements(p_items) loop
    v_count:=v_count+1;
    if jsonb_typeof(v_item)<>'object' or (v_item->>'product_id') is null or (v_item->>'qty') !~ '^[1-9][0-9]?$' then raise exception 'invalid cart item'; end if;
    v_product_id:=(v_item->>'product_id')::uuid; v_qty:=(v_item->>'qty')::integer;
    if v_product_id=any(v_seen) then raise exception 'duplicate product in cart'; end if;
    v_seen:=array_append(v_seen,v_product_id);
    select * into v_product from public.products where id=v_product_id and shop_id=p_shop_id and available=true and delivery_available=true for share;
    if not found then raise exception 'product unavailable'; end if;
    if coalesce(v_product.delivery_price,v_product.price)<0 then raise exception 'invalid product price'; end if;
    v_subtotal:=v_subtotal+coalesce(v_product.delivery_price,v_product.price)*v_qty;
  end loop;
  v_subtotal:=round(v_subtotal,2);
  if v_subtotal<=0 then raise exception 'empty order total'; end if;
  if p_expected_subtotal is distinct from v_subtotal or p_expected_delivery_fee is distinct from v_fee then raise exception 'price or delivery fee changed; refresh checkout'; end if;
  v_total:=v_subtotal+v_fee;
  v_number:=public.qg_next_order_number();
  insert into public.orders(id,order_number,customer_id,shop_id,order_type,sales_channel,status,subtotal,delivery_fee,total_amount,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,note,created_by)
  values(p_order_id,v_number,v_customer,p_shop_id,'shopping','QUEUEGO_DELIVERY','pending',v_subtotal,v_fee,v_total,v_shop.address,v_shop.latitude,v_shop.longitude,trim(p_delivery_address),p_delivery_lat,p_delivery_lng,nullif(trim(p_note),''),v_customer);
  for v_item in select value from jsonb_array_elements(p_items) loop
    v_product_id:=(v_item->>'product_id')::uuid; v_qty:=(v_item->>'qty')::integer;
    select * into v_product from public.products where id=v_product_id;
    insert into public.order_items(order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price)
    values(p_order_id,v_product_id,'product',v_product.name,v_product.description,v_qty,coalesce(v_product.delivery_price,v_product.price),round(coalesce(v_product.delivery_price,v_product.price)*v_qty,2));
  end loop;
  insert into public.payments(order_id,payer_id,amount,payment_method,status,note) values(p_order_id,v_customer,v_total,'cash','pending','ไรเดอร์สำรองจ่ายค่าสินค้าให้ร้าน แล้วเก็บเงินสดจากลูกค้า');
  insert into public.deliveries(order_id,status,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,distance_km,delivery_fee)
  values(p_order_id,'pending',v_shop.address,v_shop.latitude,v_shop.longitude,trim(p_delivery_address),p_delivery_lat,p_delivery_lng,round(v_distance::numeric,2),v_fee);
  insert into public.queuego_cash_order_locks(order_id) values(p_order_id);
  insert into public.notifications(user_id,title,message,type,reference_id) values(v_shop.user_id,'มีคำสั่งซื้อใหม่','กรุณาตรวจสอบคำสั่งซื้อ '||v_number,'order',p_order_id);
  return (select jsonb_build_object('id',o.id,'order_number',o.order_number,'status',o.status,'subtotal',o.subtotal,'delivery_fee',o.delivery_fee,'total_amount',o.total_amount,'gp_amount',o.gp_amount,'replayed',false) from public.orders o where o.id=p_order_id);
end $function$;
CREATE OR REPLACE FUNCTION public.queuego_place_market_order(p_market_order_id uuid, p_items jsonb, p_delivery_lat double precision, p_delivery_lng double precision, p_delivery_address text, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
 v_customer uuid;v_market uuid;v_shop uuid;v_shoprow public.shop_profiles%rowtype;v_item jsonb;v_prod public.products%rowtype;
 v_pid uuid;v_qty int;v_sub numeric:=0;v_shop_sub numeric;v_shop_count int;v_fee numeric;v_extra numeric;v_total numeric;
 v_dist double precision;v_a double precision;v_oid uuid;v_num text;v_seq int:=0;v_seen uuid[]:=array[]::uuid[];
 v_now timestamptz:=now();v_snapshot jsonb;
begin
 if auth.uid() is null then raise exception 'customer login required'; end if;
 select id into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
 if v_customer is null then raise exception 'active customer required'; end if;
 if p_market_order_id is null then raise exception 'market order id required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_customer::text,141));
 if exists(select 1 from public.market_orders where id=p_market_order_id) then
  return (select jsonb_build_object('market_order_id',id,'subtotal',subtotal,'delivery_fee',delivery_fee,'multi_shop_service_fee',multi_shop_service_fee,'total_amount',total_amount,'replayed',true) from public.market_orders where id=p_market_order_id and customer_id=v_customer);
 end if;
 if exists(select 1 from public.orders where customer_id=v_customer and status in('pending','accepted','searching_rider','rider_assigned','preparing','ready','assigned','picked_up','in_progress')) then raise exception 'customer has an active order'; end if;
 if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 60 then raise exception 'invalid cart'; end if;
 if p_delivery_lat is null or p_delivery_lng is null or abs(p_delivery_lat)>90 or abs(p_delivery_lng)>180 then raise exception 'delivery location required'; end if;
 if length(trim(coalesce(p_delivery_address,'')))<3 or length(p_delivery_address)>500 then raise exception 'delivery address required'; end if;
 if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;
 perform set_config('queuego.market_checkout','on',true);

 for v_item in select value from jsonb_array_elements(p_items) loop
  if (v_item->>'product_id') is null or (v_item->>'qty') !~ '^[1-9][0-9]?$' then raise exception 'invalid cart item'; end if;
  v_pid:=(v_item->>'product_id')::uuid;v_qty=(v_item->>'qty')::int;
  if v_pid=any(v_seen) then raise exception 'duplicate product'; end if;v_seen:=array_append(v_seen,v_pid);
  select p.* into v_prod from public.products p join public.market_products mp on mp.product_id=p.id
   join public.shop_profiles s on s.id=p.shop_id
   where p.id=v_pid and p.available and p.delivery_available and s.status='active' and s.delivery_enabled and s.market_id is not null
   and mp.stock_quantity>=mp.pack_size*v_qty for share of p;
  if not found then raise exception 'market product unavailable'; end if;
  if v_market is null then select market_id into v_market from public.shop_profiles where id=v_prod.shop_id;
  elsif v_market is distinct from (select market_id from public.shop_profiles where id=v_prod.shop_id) then raise exception 'all shops must be in same market'; end if;
  v_sub:=v_sub+round(coalesce(v_prod.delivery_price,v_prod.price)*v_qty,2);
 end loop;

 select count(distinct p.shop_id) into v_shop_count
 from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid;
 if v_shop_count<1 then raise exception 'empty market cart'; end if;
 if v_shop_count>1 and not public.queuego_feature_enabled('market_multi_shop',v_now) then
   raise exception 'market multi-shop is temporarily disabled';
 end if;

 select s.* into v_shoprow from public.shop_profiles s
 where s.id=(select p.shop_id from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid limit 1);
 v_dist:=public.queuego_assert_service_route(v_shoprow.latitude,v_shoprow.longitude,p_delivery_lat,p_delivery_lng,'market')::double precision;
 v_fee:=public.queuego_market_delivery_fee(v_dist::numeric,v_now);
 v_extra:=public.queuego_market_multi_shop_fee(v_shop_count,v_now);
 v_total:=round(v_sub,2)+v_fee+v_extra;
 v_snapshot:=jsonb_build_object(
   'captured_at',v_now,
   'market_multi_shop_enabled',public.queuego_feature_enabled('market_multi_shop',v_now),
   'market_base_fee',public.queuego_rule_numeric('pricing.market_base_fee',30,v_now),
   'market_distance_step_fee',public.queuego_rule_numeric('pricing.market_distance_step_fee',10,v_now),
   'market_second_shop_fee',public.queuego_rule_numeric('pricing.market_second_shop_fee',10,v_now),
   'market_additional_shop_fee',public.queuego_rule_numeric('pricing.market_additional_shop_fee',5,v_now)
 );

 insert into public.market_orders(
   id,customer_id,market_id,status,shop_count,subtotal,delivery_fee,multi_shop_service_fee,rider_bonus,total_amount,
   delivery_address,delivery_latitude,delivery_longitude,created_at,updated_at,pricing_snapshot
 ) values(
   p_market_order_id,v_customer,v_market,'PENDING',v_shop_count,round(v_sub,2),v_fee,v_extra,v_extra,v_total,
   trim(p_delivery_address),p_delivery_lat,p_delivery_lng,v_now,v_now,v_snapshot
 );

 for v_shop in
  select distinct p.shop_id
  from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid
  order by p.shop_id
 loop
  v_seq:=v_seq+1;v_oid:=gen_random_uuid();
  select * into v_shoprow from public.shop_profiles where id=v_shop;
  select round(sum(coalesce(p.delivery_price,p.price)*(j.value->>'qty')::int),2)
   into v_shop_sub
   from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid
   where p.shop_id=v_shop;
  v_num:=public.qg_next_order_number();
  insert into public.orders(id,order_number,customer_id,shop_id,order_type,sales_channel,status,subtotal,delivery_fee,total_amount,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,note,created_by,market_order_id,multi_shop_service_fee,rider_multi_shop_bonus,market_shop_count)
  values(v_oid,v_num,v_customer,v_shop,'shopping','QUEUEGO_DELIVERY','pending',v_shop_sub,case when v_seq=1 then v_fee else 0 end,v_shop_sub+case when v_seq=1 then v_fee+v_extra else 0 end,v_shoprow.address,v_shoprow.latitude,v_shoprow.longitude,trim(p_delivery_address),p_delivery_lat,p_delivery_lng,nullif(trim(p_note),''),v_customer,p_market_order_id,case when v_seq=1 then v_extra else 0 end,case when v_seq=1 then v_extra else 0 end,v_shop_count);
  for v_item in
   select j.value from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid where p.shop_id=v_shop
  loop
   v_pid=(v_item->>'product_id')::uuid;v_qty=(v_item->>'qty')::int;select * into v_prod from public.products where id=v_pid;
   insert into public.order_items(order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price)
   values(v_oid,v_pid,'product',v_prod.name,v_prod.description,v_qty,coalesce(v_prod.delivery_price,v_prod.price),round(coalesce(v_prod.delivery_price,v_prod.price)*v_qty,2));
  end loop;
  insert into public.market_order_pickups(market_order_id,order_id,shop_id,pickup_sequence,status,shop_amount)
   values(p_market_order_id,v_oid,v_shop,v_seq,'PENDING',v_shop_sub);
  insert into public.payments(order_id,payer_id,amount,payment_method,status,note)
   values(v_oid,v_customer,v_shop_sub+case when v_seq=1 then v_fee+v_extra else 0 end,'cash','pending','Market multi-shop cash order');
  insert into public.deliveries(order_id,status,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,distance_km,delivery_fee)
   values(v_oid,'pending',v_shoprow.address,v_shoprow.latitude,v_shoprow.longitude,trim(p_delivery_address),p_delivery_lat,p_delivery_lng,round(v_dist::numeric,2),case when v_seq=1 then v_fee else 0 end);
  insert into public.queuego_cash_order_locks(order_id) values(v_oid);
  insert into public.notifications(user_id,title,message,type,reference_id)
   values(v_shoprow.user_id,'มีคำสั่งซื้อจากตลาด','กรุณาตรวจสอบคำสั่งซื้อ '||v_num,'order',v_oid);
 end loop;
 return jsonb_build_object('market_order_id',p_market_order_id,'shop_count',v_shop_count,'subtotal',round(v_sub,2),'delivery_fee',v_fee,'multi_shop_service_fee',v_extra,'total_amount',v_total,'replayed',false);
end $function$;
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

revoke all on function public.queuego_assert_service_route(double precision,double precision,double precision,double precision,text) from public,anon,authenticated;
grant execute on function public.queuego_assert_service_route(double precision,double precision,double precision,double precision,text) to service_role;
grant execute on function public.queuego_service_area_route_status(double precision,double precision,double precision,double precision,text) to anon,authenticated,service_role;
