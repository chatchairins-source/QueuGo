-- Rider-first ordinary QueueGo delivery.
-- Customer checkout enters searching_rider immediately and the merchant only
-- receives/sees the order after a Rider accepts it.

CREATE OR REPLACE FUNCTION public.queuego_place_cash_order_core(
  p_order_id uuid,
  p_shop_id uuid,
  p_items jsonb,
  p_delivery_lat double precision,
  p_delivery_lng double precision,
  p_delivery_address text,
  p_expected_subtotal numeric,
  p_expected_delivery_fee numeric,
  p_note text DEFAULT NULL::text
)
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

  select u.id into v_customer
  from public.users u
  where u.auth_user_id=auth.uid() and u.role='customer' and u.status='active';

  if v_customer is null then raise exception 'active customer required'; end if;
  if p_order_id is null then raise exception 'order id required'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_customer::text,121));

  select * into v_old from public.orders where id=p_order_id;
  if found then
    if v_old.customer_id is distinct from v_customer then raise exception 'order id already in use'; end if;
    return jsonb_build_object(
      'id',v_old.id,'order_number',v_old.order_number,'status',v_old.status,
      'subtotal',v_old.subtotal,'delivery_fee',v_old.delivery_fee,
      'total_amount',v_old.total_amount,'gp_amount',v_old.gp_amount,'replayed',true
    );
  end if;

  if exists(
    select 1 from public.orders
    where customer_id=v_customer and order_type='shopping'
      and status in ('pending','accepted','searching_rider','rider_assigned','preparing','ready','assigned','picked_up','in_progress')
  ) then
    raise exception 'customer has an active order';
  end if;

  if p_shop_id is null or p_items is null or jsonb_typeof(p_items)<>'array'
     or jsonb_array_length(p_items) not between 1 and 30 then
    raise exception 'invalid cart';
  end if;

  if p_delivery_lat is null or p_delivery_lng is null
     or abs(p_delivery_lat)>90 or abs(p_delivery_lng)>180
     or (p_delivery_lat=0 and p_delivery_lng=0) then
    raise exception 'delivery location required';
  end if;

  if length(trim(coalesce(p_delivery_address,'')))<3 or length(p_delivery_address)>500 then
    raise exception 'delivery address required';
  end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select s.* into v_shop
  from public.shop_profiles s
  join public.users u on u.id=s.user_id
  where s.id=p_shop_id and s.status='active' and u.role='shop' and u.status='active'
  for share of s;

  if not found then raise exception 'shop unavailable'; end if;
  if v_shop.latitude is null or v_shop.longitude is null or (v_shop.latitude=0 and v_shop.longitude=0) then
    raise exception 'shop pickup location required';
  end if;

  if exists(
    select 1 from public.shop_open_states x
    where x.shop_id=p_shop_id and x.is_open=false and (x.resume_at is null or x.resume_at>now())
  ) then
    raise exception 'shop closed';
  end if;

  v_distance:=public.queuego_assert_service_route(
    v_shop.latitude,v_shop.longitude,p_delivery_lat,p_delivery_lng,
    coalesce(v_shop.public_category,'delivery')
  )::double precision;

  v_fee:=case
    when v_distance<=5 then 30
    when v_distance<=6 then 40
    when v_distance<=7 then 50
    else 50+ceil(v_distance-7)*10
  end;

  for v_item in select value from jsonb_array_elements(p_items) loop
    v_count:=v_count+1;
    if jsonb_typeof(v_item)<>'object'
       or (v_item->>'product_id') is null
       or (v_item->>'qty') !~ '^[1-9][0-9]?$' then
      raise exception 'invalid cart item';
    end if;

    v_product_id:=(v_item->>'product_id')::uuid;
    v_qty:=(v_item->>'qty')::integer;

    if v_product_id=any(v_seen) then raise exception 'duplicate product in cart'; end if;
    v_seen:=array_append(v_seen,v_product_id);

    select * into v_product
    from public.products
    where id=v_product_id and shop_id=p_shop_id and available=true and delivery_available=true
    for share;

    if not found then raise exception 'product unavailable'; end if;
    if coalesce(v_product.delivery_price,v_product.price)<0 then raise exception 'invalid product price'; end if;

    v_subtotal:=v_subtotal+coalesce(v_product.delivery_price,v_product.price)*v_qty;
  end loop;

  v_subtotal:=round(v_subtotal,2);
  if v_subtotal<=0 then raise exception 'empty order total'; end if;
  if p_expected_subtotal is distinct from v_subtotal
     or p_expected_delivery_fee is distinct from v_fee then
    raise exception 'price or delivery fee changed; refresh checkout';
  end if;

  v_total:=v_subtotal+v_fee;
  v_number:=public.qg_next_order_number();

  insert into public.orders(
    id,order_number,customer_id,shop_id,order_type,sales_channel,status,
    subtotal,delivery_fee,total_amount,
    pickup_address,pickup_latitude,pickup_longitude,
    delivery_address,delivery_latitude,delivery_longitude,
    note,created_by,rider_search_started_at
  )
  values(
    p_order_id,v_number,v_customer,p_shop_id,'shopping','QUEUEGO_DELIVERY','searching_rider',
    v_subtotal,v_fee,v_total,
    v_shop.address,v_shop.latitude,v_shop.longitude,
    trim(p_delivery_address),p_delivery_lat,p_delivery_lng,
    nullif(trim(p_note),''),v_customer,now()
  );

  for v_item in select value from jsonb_array_elements(p_items) loop
    v_product_id:=(v_item->>'product_id')::uuid;
    v_qty:=(v_item->>'qty')::integer;
    select * into v_product from public.products where id=v_product_id;

    insert into public.order_items(
      order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price
    )
    values(
      p_order_id,v_product_id,'product',v_product.name,v_product.description,v_qty,
      coalesce(v_product.delivery_price,v_product.price),
      round(coalesce(v_product.delivery_price,v_product.price)*v_qty,2)
    );
  end loop;

  insert into public.payments(order_id,payer_id,amount,payment_method,status,note)
  values(
    p_order_id,v_customer,v_total,'cash','pending',
    'ไรเดอร์สำรองจ่ายค่าสินค้าให้ร้าน แล้วเก็บเงินสดจากลูกค้า'
  );

  insert into public.deliveries(
    order_id,status,pickup_address,pickup_latitude,pickup_longitude,
    delivery_address,delivery_latitude,delivery_longitude,distance_km,delivery_fee,note
  )
  values(
    p_order_id,'pending',v_shop.address,v_shop.latitude,v_shop.longitude,
    trim(p_delivery_address),p_delivery_lat,p_delivery_lng,
    round(v_distance::numeric,2),v_fee,'Rider-first search'
  );

  insert into public.queuego_cash_order_locks(order_id) values(p_order_id);

  -- Do not notify the merchant here. rider_claim_order() notifies the merchant
  -- only after a Rider has accepted the job.

  return (
    select jsonb_build_object(
      'id',o.id,'order_number',o.order_number,'status',o.status,
      'subtotal',o.subtotal,'delivery_fee',o.delivery_fee,
      'total_amount',o.total_amount,'gp_amount',o.gp_amount,'replayed',false
    )
    from public.orders o where o.id=p_order_id
  );
end
$function$;

ALTER POLICY orders_shop_select ON public.orders
USING (
  shop_id IN (
    SELECT shop_profiles.id
    FROM public.shop_profiles
    WHERE shop_profiles.user_id=public.get_my_user_id()
  )
  AND (
    sales_channel IS DISTINCT FROM 'QUEUEGO_DELIVERY'
    OR (
      rider_id IS NOT NULL
      AND status NOT IN ('pending','accepted','searching_rider')
    )
  )
);

ALTER POLICY pos_delivery_orders_read ON public.orders
USING (
  shop_id=public.pos_my_shop()
  AND (
    (
      sales_channel='QUEUEGO_DELIVERY'
      AND rider_id IS NOT NULL
      AND status NOT IN ('pending','accepted','searching_rider')
    )
    OR (
      sales_channel IS NULL
      AND order_type='shopping'
    )
  )
);

ALTER POLICY order_items_shop_select ON public.order_items
USING (
  order_id IN (
    SELECT o.id
    FROM public.orders o
    JOIN public.shop_profiles s ON s.id=o.shop_id
    WHERE s.user_id=public.get_my_user_id()
      AND (
        o.sales_channel IS DISTINCT FROM 'QUEUEGO_DELIVERY'
        OR (
          o.rider_id IS NOT NULL
          AND o.status NOT IN ('pending','accepted','searching_rider')
        )
      )
  )
);

ALTER POLICY deliveries_shop_select ON public.deliveries
USING (
  order_id IN (
    SELECT o.id
    FROM public.orders o
    JOIN public.shop_profiles s ON s.id=o.shop_id
    WHERE s.user_id=public.get_my_user_id()
      AND (
        o.sales_channel IS DISTINCT FROM 'QUEUEGO_DELIVERY'
        OR (
          o.rider_id IS NOT NULL
          AND o.status NOT IN ('pending','accepted','searching_rider')
        )
      )
  )
);

ALTER POLICY payments_shop_select ON public.payments
USING (
  order_id IN (
    SELECT o.id
    FROM public.orders o
    JOIN public.shop_profiles s ON s.id=o.shop_id
    WHERE s.user_id=public.get_my_user_id()
      AND (
        o.sales_channel IS DISTINCT FROM 'QUEUEGO_DELIVERY'
        OR (
          o.rider_id IS NOT NULL
          AND o.status NOT IN ('pending','accepted','searching_rider')
        )
      )
  )
);

ALTER POLICY pos_delivery_items_read ON public.order_items
USING (
  EXISTS (
    SELECT 1
    FROM public.orders o
    WHERE o.id=order_items.order_id
      AND o.shop_id=public.pos_my_shop()
      AND (
        (
          o.sales_channel='QUEUEGO_DELIVERY'
          AND o.rider_id IS NOT NULL
          AND o.status NOT IN ('pending','accepted','searching_rider')
        )
        OR (
          o.sales_channel IS NULL
          AND o.order_type='shopping'
        )
      )
  )
);
