-- REVIEW REQUIRED: never apply to Production automatically.
-- Reject checkout replays when session, table, shop, or location is no longer authorized.
CREATE OR REPLACE FUNCTION public.qg_table_checkout(p_session uuid, p_device_key uuid, p_request uuid, p_items jsonb, p_lat double precision, p_lng double precision)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v public.qg_table_sessions%rowtype;
  t public.pos_tables%rowtype;
  s public.shop_profiles%rowtype;
  v_order uuid;
  v_order_number text;
  v_hash text;
  v_old public.qg_table_requests%rowtype;
  v_line jsonb;
  v_product public.products%rowtype;
  v_quantity integer;
  v_count integer:=0;
  v_total numeric:=0;
  v_note text;
  v_seen uuid[]:='{}';
begin
  if p_session is null or p_device_key is null or p_request is null
     or jsonb_typeof(p_items)<>'array'
     or jsonb_array_length(p_items) not between 1 and 50 then
    raise exception 'รายการอาหารไม่ถูกต้อง';
  end if;

  if p_lat is null or p_lng is null
     or p_lat not between -90 and 90
     or p_lng not between -180 and 180 then
    raise exception 'ต้องยืนยันตำแหน่งภายในร้านก่อนส่งออเดอร์';
  end if;

  select * into v
  from public.qg_table_sessions
  where id=p_session
    and device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex')
  for update;
  if not found then raise exception 'ไม่พบสิทธิ์การสั่งอาหารบนเครื่องนี้'; end if;

  -- Check revocation and expiry before replaying an existing checkout.
  if v.revoked_at is not null or now()>=v.expires_at then
    raise exception 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง';
  end if;

  v_hash:=encode(extensions.digest(p_items::text,'sha256'),'hex');

  select * into t
  from public.pos_tables
  where id=v.table_id and shop_id=v.shop_id and active;

  select * into s
  from public.shop_profiles
  where id=v.shop_id and status not in ('suspended','rejected');

  if not found or t.id is null or s.latitude is null or s.longitude is null then
    raise exception 'ร้านหรือโต๊ะไม่พร้อมรับออเดอร์';
  end if;

  if public.qg_table_distance_m(p_lat,p_lng,s.latitude,s.longitude)>100 then
    raise exception 'กรุณาสั่งอาหารภายในระยะ 100 เมตรจากร้าน';
  end if;

  -- Apply table/shop and proximity authorization to exact-request replays too.
  select * into v_old
  from public.qg_table_requests
  where session_id=p_session and request_id=p_request;
  if found then
    if v_old.payload_hash<>v_hash then
      raise exception 'รหัสคำขอถูกใช้กับรายการอาหารอื่น';
    end if;
    select o.order_number into v_order_number from public.orders o where o.id=v_old.order_id;
    return jsonb_build_object(
      'order_id',v_old.order_id,
      'order_number',v_order_number,
      'duplicate',true,
      'reason','same_request'
    );
  end if;

  select * into v_old
  from public.qg_table_requests
  where session_id=p_session
    and payload_hash=v_hash
    and created_at>now()-interval '8 seconds'
  order by created_at desc
  limit 1;

  if found then
    insert into public.qg_table_requests(session_id,request_id,payload_hash,order_id)
    values(v.id,p_request,v_hash,v_old.order_id);

    select o.order_number into v_order_number from public.orders o where o.id=v_old.order_id;
    return jsonb_build_object(
      'order_id',v_old.order_id,
      'order_number',v_order_number,
      'duplicate',true,
      'reason','recent_same_cart'
    );
  end if;

  perform set_config('queuego.pos_rpc','on',true);

  insert into public.orders(
    order_number,shop_id,order_type,sales_channel,table_id,table_session_id,
    status,kitchen_status,payment_status,bill_status,
    subtotal,total_amount,delivery_fee,gp_rate,gp_amount
  )
  values(
    public.qg_next_order_number(),
    v.shop_id,'DINE_IN','POS',v.table_id,v.id,
    'pending','SENT_TO_KITCHEN','UNPAID','OPEN',
    0,0,0,0,0
  )
  returning id,order_number into v_order,v_order_number;

  for v_line in select value from jsonb_array_elements(p_items)
  loop
    if jsonb_typeof(v_line)<>'object'
       or (v_line-'id'-'quantity'-'note')<>'{}'::jsonb then
      raise exception 'รูปแบบสินค้าไม่ถูกต้อง';
    end if;

    v_quantity:=(v_line->>'quantity')::integer;
    v_note:=coalesce(v_line->>'note','');

    if v_quantity not between 1 and 99 or length(v_note)>500 then
      raise exception 'จำนวนหรือหมายเหตุไม่ถูกต้อง';
    end if;

    if (v_line->>'id')::uuid=any(v_seen) then
      raise exception 'มีสินค้าเดียวกันซ้ำในคำขอ';
    end if;

    select * into v_product
    from public.products
    where id=(v_line->>'id')::uuid
      and shop_id=v.shop_id
      and available
      and pos_available;
    if not found then raise exception 'สินค้าไม่พร้อมขาย'; end if;

    v_seen:=array_append(v_seen,v_product.id);
    v_count:=v_count+1;

    insert into public.order_items(
      order_id,product_id,item_type,item_name,description,
      quantity,unit_price,total_price,pos_kitchen_status,pos_batch
    )
    values(
      v_order,v_product.id,'product',v_product.name,nullif(trim(v_note),''),
      v_quantity,coalesce(v_product.pos_price,v_product.price),
      v_quantity*coalesce(v_product.pos_price,v_product.price),
      'SENT_TO_KITCHEN',1
    );

    v_total:=v_total+v_quantity*coalesce(v_product.pos_price,v_product.price);
  end loop;

  if v_count=0 then raise exception 'ไม่มีสินค้า'; end if;

  update public.orders
  set subtotal=v_total,total_amount=v_total,updated_at=now()
  where id=v_order;

  insert into public.qg_table_requests(session_id,request_id,payload_hash,order_id)
  values(v.id,p_request,v_hash,v_order);

  return jsonb_build_object(
    'order_id',v_order,
    'order_number',v_order_number,
    'duplicate',false,
    'total',v_total,
    'table_name',t.label
  );
end
$function$
;
