-- QR availability uses the same POS catalog boundary. Chat helper checks the current order participant.
BEGIN;
CREATE OR REPLACE FUNCTION public.qg_table_checkout(p_session uuid, p_device_key uuid, p_request uuid, p_items jsonb, p_lat double precision, p_lng double precision)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v public.qg_table_sessions%rowtype;
  t public.pos_tables%rowtype;
  s public.shop_profiles%rowtype;
  v_order uuid;
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

  if not found then
    raise exception 'ไม่พบสิทธิ์การสั่งอาหารบนเครื่องนี้';
  end if;

  v_hash:=encode(extensions.digest(p_items::text,'sha256'),'hex');

  select * into v_old
  from public.qg_table_requests
  where session_id=p_session and request_id=p_request;

  if found then
    if v_old.payload_hash<>v_hash then
      raise exception 'รหัสคำขอถูกใช้กับรายการอาหารอื่น';
    end if;
    return jsonb_build_object('order_id',v_old.order_id,'duplicate',true,'reason','same_request');
  end if;

  if v.revoked_at is not null or now()>=v.expires_at then
    raise exception 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง';
  end if;

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
    return jsonb_build_object('order_id',v_old.order_id,'duplicate',true,'reason','recent_same_cart');
  end if;

  perform set_config('queuego.pos_rpc','on',true);

  insert into public.orders(
    order_number,shop_id,order_type,sales_channel,table_id,table_session_id,
    status,kitchen_status,payment_status,bill_status,
    subtotal,total_amount,delivery_fee,gp_rate,gp_amount
  )
  values(
    'QR-'||upper(substr(replace(gen_random_uuid()::text,'-',''),1,12)),
    v.shop_id,'DINE_IN','POS',v.table_id,v.id,
    'pending','SENT_TO_KITCHEN','UNPAID','OPEN',
    0,0,0,0,0
  )
  returning id into v_order;

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

    if not found then
      raise exception 'สินค้าไม่พร้อมขาย';
    end if;

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

  if v_count=0 then
    raise exception 'ไม่มีสินค้า';
  end if;

  update public.orders
  set subtotal=v_total,total_amount=v_total,updated_at=now()
  where id=v_order;

  insert into public.qg_table_requests(session_id,request_id,payload_hash,order_id)
  values(v.id,p_request,v_hash,v_order);

  return jsonb_build_object(
    'order_id',v_order,
    'duplicate',false,
    'total',v_total,
    'table_name',t.label
  );
end
$function$;

CREATE OR REPLACE FUNCTION public.qg_chat_postjob_allowed(p_order_id uuid, p_sender_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$ declare v_actor uuid := public.get_my_user_id(); v_role text := public.get_my_role(); v_order record; v_delivered timestamptz; begin if v_actor is null or p_sender_id is distinct from v_actor then return false; end if; select o.id,o.customer_id,o.shop_id,o.technician_id,o.rider_id,o.status into v_order from public.orders o where o.id=p_order_id; if not found then return false; end if; if v_role='admin' then return true; end if; if v_role='rider' then if not exists(select 1 from public.rider_profiles r where r.id=v_order.rider_id and r.user_id=v_actor) then return false; end if; if v_order.status='cancelled' then return false; end if; if v_order.status<>'completed' then return true; end if; select d.delivered_at into v_delivered from public.deliveries d where d.order_id=p_order_id order by d.delivered_at desc nulls last limit 1; if v_delivered is null then return false; end if; return exists(select 1 from public.order_chat_messages m where m.order_id=p_order_id and m.sender_id=v_order.customer_id and m.created_at>v_delivered); end if; if v_role='customer' and v_order.customer_id=v_actor then return v_order.status<>'cancelled'; end if; if v_role='shop' and exists(select 1 from public.shop_profiles s where s.id=v_order.shop_id and s.user_id=v_actor) then return v_order.status<>'cancelled'; end if; if v_role='technician' and exists(select 1 from public.technician_profiles t where t.id=v_order.technician_id and t.user_id=v_actor) then return v_order.status<>'cancelled'; end if; return false; end $function$;
COMMIT;

