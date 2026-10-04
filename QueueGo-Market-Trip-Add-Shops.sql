-- QueueGo Market Trip add-shops flow.
-- Additive and idempotent. Customers may add NEW shops to their own Market Trip
-- only while the trip is still inside the market lifecycle (before IN_PROGRESS).

create table if not exists public.market_order_add_requests (
  request_id uuid primary key,
  market_order_id uuid not null references public.market_orders(id) on delete cascade,
  customer_id uuid not null references public.users(id) on delete cascade,
  response jsonb,
  created_at timestamptz not null default now()
);

alter table public.market_order_add_requests enable row level security;
revoke all on public.market_order_add_requests from public,anon,authenticated;
grant all on public.market_order_add_requests to service_role;

create index if not exists market_order_add_requests_trip_idx
  on public.market_order_add_requests(market_order_id,created_at desc);

create or replace function public.queuego_add_market_order_shops(
  p_request_id uuid,
  p_market_order_id uuid,
  p_items jsonb,
  p_note text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_customer uuid;
  v_parent public.market_orders%rowtype;
  v_existing jsonb;
  v_item jsonb;
  v_prod public.products%rowtype;
  v_shoprow public.shop_profiles%rowtype;
  v_pid uuid;
  v_qty integer;
  v_shop uuid;
  v_shop_sub numeric;
  v_oid uuid;
  v_num text;
  v_today text;
  v_next integer;
  v_seq integer;
  v_rider uuid;
  v_rider_count integer;
  v_dist numeric;
  v_new_shops uuid[]:=array[]::uuid[];
  v_seen_products uuid[]:=array[]::uuid[];
  v_result jsonb;
  v_group jsonb;
begin
  if auth.uid() is null then raise exception 'customer login required'; end if;
  select id into v_customer
  from public.users
  where auth_user_id=auth.uid() and role='customer' and status='active';
  if v_customer is null then raise exception 'active customer required'; end if;
  if p_request_id is null or p_market_order_id is null then raise exception 'request id and market order id required'; end if;
  if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 40 then
    raise exception 'invalid add-shop cart';
  end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  perform pg_advisory_xact_lock(hashtextextended(p_market_order_id::text,219));

  select response into v_existing
  from public.market_order_add_requests
  where request_id=p_request_id and market_order_id=p_market_order_id and customer_id=v_customer;
  if found and v_existing is not null then
    return v_existing || jsonb_build_object('replayed',true);
  end if;
  if exists(select 1 from public.market_order_add_requests where request_id=p_request_id) then
    raise exception 'request id already used';
  end if;

  select * into v_parent
  from public.market_orders
  where id=p_market_order_id and customer_id=v_customer
  for update;
  if not found then raise exception 'market trip unavailable'; end if;

  if upper(v_parent.status) in ('IN_PROGRESS','COMPLETED','CANCELLED') then
    raise exception 'market trip is locked after rider leaves market';
  end if;
  if not public.queuego_feature_enabled('market_multi_shop',now()) then
    raise exception 'market multi-shop is temporarily disabled';
  end if;

  select (array_agg(distinct rider_id) filter(where rider_id is not null))[1],
         count(distinct rider_id) filter(where rider_id is not null)
  into v_rider,v_rider_count
  from public.orders
  where market_order_id=p_market_order_id and status<>'cancelled';
  if coalesce(v_rider_count,0)>1 then raise exception 'market trip rider mismatch'; end if;

  for v_item in select value from jsonb_array_elements(p_items) loop
    if (v_item->>'product_id') is null or (v_item->>'qty') !~ '^[1-9][0-9]?$' then
      raise exception 'invalid cart item';
    end if;
    v_pid:=(v_item->>'product_id')::uuid;
    v_qty:=(v_item->>'qty')::integer;
    if v_pid=any(v_seen_products) then raise exception 'duplicate product'; end if;
    v_seen_products:=array_append(v_seen_products,v_pid);

    select p.* into v_prod
    from public.products p
    join public.market_products mp on mp.product_id=p.id and mp.shop_id=p.shop_id
    join public.shop_profiles s on s.id=p.shop_id
    where p.id=v_pid
      and p.available and p.delivery_available
      and s.status='active' and s.delivery_enabled
      and s.market_id=v_parent.market_id
      and s.market_membership_status='approved'
      and mp.stock_quantity>=mp.pack_size*v_qty
    for share of p;
    if not found then raise exception 'market product unavailable'; end if;

    if exists(
      select 1 from public.orders o
      where o.market_order_id=p_market_order_id
        and o.shop_id=v_prod.shop_id
        and o.status<>'cancelled'
    ) then
      raise exception 'shop already exists in this market trip';
    end if;

    if not v_prod.shop_id=any(v_new_shops) then
      v_new_shops:=array_append(v_new_shops,v_prod.shop_id);
    end if;
  end loop;

  if coalesce(array_length(v_new_shops,1),0)<1 then raise exception 'no new shop'; end if;

  insert into public.market_order_add_requests(request_id,market_order_id,customer_id,response)
  values(p_request_id,p_market_order_id,v_customer,null);

  select coalesce(max(pickup_sequence),0)
  into v_seq
  from public.market_order_pickups
  where market_order_id=p_market_order_id;

  foreach v_shop in array v_new_shops loop
    v_seq:=v_seq+1;
    select * into v_shoprow from public.shop_profiles where id=v_shop for share;
    if not found then raise exception 'shop unavailable'; end if;

    select round(sum(coalesce(p.delivery_price,p.price)*(j.value->>'qty')::integer),2)
    into v_shop_sub
    from jsonb_array_elements(p_items) j
    join public.products p on p.id=(j.value->>'product_id')::uuid
    where p.shop_id=v_shop;
    if coalesce(v_shop_sub,0)<=0 then raise exception 'invalid shop subtotal'; end if;

    v_oid:=gen_random_uuid();
    v_today:=to_char(now() at time zone 'Asia/Bangkok','YYYYMMDD');
    perform pg_advisory_xact_lock(hashtextextended('queuego-number-'||v_today,122));
    select coalesce(max(right(order_number,4)::integer),0)+1
      into v_next
    from public.orders
    where order_number ~ ('^QT-'||v_today||'-[0-9]{4}$');
    v_num:='QT-'||v_today||'-'||lpad(v_next::text,4,'0');

    v_dist:=public.queuego_market_distance_km(
      v_shoprow.latitude,v_shoprow.longitude,
      v_parent.delivery_latitude,v_parent.delivery_longitude
    );

    perform set_config('queuego.market_checkout','on',true);
    insert into public.orders(
      id,order_number,customer_id,shop_id,order_type,sales_channel,status,rider_id,
      subtotal,delivery_fee,total_amount,
      pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude,
      note,created_by,market_order_id,multi_shop_service_fee,rider_multi_shop_bonus,market_shop_count
    ) values(
      v_oid,v_num,v_customer,v_shop,'shopping','QUEUEGO_DELIVERY','pending',v_rider,
      v_shop_sub,0,v_shop_sub,
      v_shoprow.address,v_shoprow.latitude,v_shoprow.longitude,
      v_parent.delivery_address,v_parent.delivery_latitude,v_parent.delivery_longitude,
      nullif(trim(p_note),''),v_customer,p_market_order_id,0,0,v_parent.shop_count+array_length(v_new_shops,1)
    );

    for v_item in
      select j.value
      from jsonb_array_elements(p_items) j
      join public.products p on p.id=(j.value->>'product_id')::uuid
      where p.shop_id=v_shop
    loop
      v_pid:=(v_item->>'product_id')::uuid;
      v_qty:=(v_item->>'qty')::integer;
      select * into v_prod from public.products where id=v_pid;
      insert into public.order_items(order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price)
      values(
        v_oid,v_pid,'product',v_prod.name,v_prod.description,v_qty,
        coalesce(v_prod.delivery_price,v_prod.price),
        round(coalesce(v_prod.delivery_price,v_prod.price)*v_qty,2)
      );
    end loop;

    insert into public.market_order_pickups(
      market_order_id,order_id,shop_id,pickup_sequence,status,shop_amount
    ) values(p_market_order_id,v_oid,v_shop,v_seq,'PENDING',v_shop_sub);

    insert into public.payments(order_id,payer_id,amount,payment_method,status,note)
    values(v_oid,v_customer,v_shop_sub,'cash','pending','Market trip added shop');

    insert into public.deliveries(
      order_id,rider_id,status,
      pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude,
      distance_km,delivery_fee,note
    ) values(
      v_oid,v_rider,case when v_rider is null then 'pending' else 'assigned' end,
      v_shoprow.address,v_shoprow.latitude,v_shoprow.longitude,
      v_parent.delivery_address,v_parent.delivery_latitude,v_parent.delivery_longitude,
      round(v_dist,2),0,'Added to existing Market Trip'
    );

    insert into public.queuego_cash_order_locks(order_id) values(v_oid);

    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_shoprow.user_id,'มีคำสั่งซื้อเพิ่มใน Market Trip','กรุณาตรวจสอบคำสั่งซื้อ '||v_num,'order',v_oid);
  end loop;

  v_group:=public.qg_market_recalculate_group(p_market_order_id);

  v_result:=jsonb_build_object(
    'market_order_id',p_market_order_id,
    'added_shop_count',array_length(v_new_shops,1),
    'shop_count',(v_group->>'shop_count')::integer,
    'subtotal',(v_group->>'subtotal')::numeric,
    'delivery_fee',(v_group->>'delivery_fee')::numeric,
    'multi_shop_service_fee',(v_group->>'multi_shop_service_fee')::numeric,
    'total_amount',(v_group->>'total_amount')::numeric,
    'rider_id',v_rider,
    'locked',false,
    'replayed',false
  );

  update public.market_order_add_requests
  set response=v_result
  where request_id=p_request_id;

  return v_result;
end
$$;

revoke all on function public.queuego_add_market_order_shops(uuid,uuid,jsonb,text) from public,anon;
grant execute on function public.queuego_add_market_order_shops(uuid,uuid,jsonb,text) to authenticated,service_role;

create or replace function public.queuego_shop_delivery_transition(
  p_order_id uuid,
  p_action text,
  p_reason text,
  p_actor_user uuid
) returns text
language plpgsql
set search_path to 'public','pg_temp'
as $$
declare
  v_order public.orders%rowtype;
  v_next text;
  v_note text;
  v_rider_user uuid;
  v_message text;
  v_parent_status text;
begin
  select * into v_order from public.orders where id=p_order_id for update;
  if not found then raise exception 'order unavailable'; end if;

  if p_action='cancel' then
    if v_order.status not in ('pending','accepted','searching_rider','rider_assigned','preparing','ready') then
      raise exception 'rider has already picked up or order finished';
    end if;
    if trim(coalesce(p_reason,''))='' or length(p_reason)>500 then
      raise exception 'cancellation reason required (max 500)';
    end if;
    if exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then
      raise exception 'cannot cancel after rider cash payment';
    end if;
    v_next:='cancelled';
    v_message:='ร้านค้ายกเลิกออเดอร์: '||trim(p_reason);

  elsif p_action='accepted' and v_order.status='pending' then
    if v_order.market_order_id is not null and v_order.rider_id is not null then
      select status into v_parent_status from public.market_orders where id=v_order.market_order_id;
      if upper(coalesce(v_parent_status,'')) in ('IN_PROGRESS','COMPLETED','CANCELLED') then
        raise exception 'market trip already left market';
      end if;
      v_next:='rider_assigned';
      v_message:='ร้านรับออเดอร์แล้ว ใช้ไรเดอร์คนเดิมของ Market Trip';
    else
      v_next:='searching_rider';
      v_message:='ร้านรับออเดอร์แล้ว กำลังค้นหาไรเดอร์';
    end if;

  elsif p_action='preparing' and v_order.status='rider_assigned' and v_order.rider_id is not null then
    v_next:='preparing';
    v_message:='ร้านกำลังเตรียมสินค้า';

  elsif p_action='ready' and v_order.status='preparing' and v_order.rider_id is not null then
    v_next:='ready';
    v_message:='สินค้าเตรียมเสร็จแล้ว ไรเดอร์สามารถรับสินค้าได้';

  else
    raise exception 'order status changed; refresh';
  end if;

  v_note:=regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\\n]*\\n?','','g');
  v_note:=regexp_replace(v_note,'^__QT_CANCEL_REASON__=[^\\n]*\\n?','','g');
  v_note:='__QT_ORDER_STATUS__='||v_next||E'\n'||
    (case when v_next='cancelled'
      then '__QT_CANCEL_REASON__='||replace(trim(p_reason),E'\n',' ')||E'\n'
      else '' end)||v_note;

  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set status=v_next,
      note=v_note,
      updated_at=now(),
      merchant_accepted_at=case when v_next in ('searching_rider','rider_assigned') then coalesce(merchant_accepted_at,now()) else merchant_accepted_at end,
      rider_search_started_at=case when v_next='searching_rider' then coalesce(rider_search_started_at,now()) else rider_search_started_at end,
      preparing_at=case when v_next='preparing' then coalesce(preparing_at,now()) else preparing_at end,
      ready_at=case when v_next='ready' then coalesce(ready_at,now()) else ready_at end,
      cancelled_at=case when v_next='cancelled' then coalesce(cancelled_at,now()) else cancelled_at end
  where id=p_order_id;

  if v_next='searching_rider' then
    insert into public.deliveries(
      order_id,rider_id,status,
      pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude,
      delivery_fee,note
    ) values(
      p_order_id,null,'pending',
      v_order.pickup_address,v_order.pickup_latitude,v_order.pickup_longitude,
      v_order.delivery_address,v_order.delivery_latitude,v_order.delivery_longitude,
      v_order.delivery_fee,'Rider-first search'
    )
    on conflict(order_id) do nothing;

  elsif v_next='rider_assigned' then
    insert into public.deliveries(
      order_id,rider_id,status,
      pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude,
      delivery_fee,note
    ) values(
      p_order_id,v_order.rider_id,'assigned',
      v_order.pickup_address,v_order.pickup_latitude,v_order.pickup_longitude,
      v_order.delivery_address,v_order.delivery_latitude,v_order.delivery_longitude,
      v_order.delivery_fee,'Existing Market Trip rider'
    )
    on conflict(order_id) do update
      set rider_id=excluded.rider_id,status='assigned',updated_at=now(),note='Existing Market Trip rider';

  elsif v_next='cancelled' then
    update public.deliveries
    set status='cancelled',updated_at=now()
    where order_id=p_order_id and status in ('pending','assigned');
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    p_actor_user,'merchant_order_action','order',p_order_id,p_action,
    jsonb_build_object('from',v_order.status,'to',v_next,'reason',p_reason)
  );

  if v_order.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_order.customer_id,'อัปเดตออเดอร์',v_message,'order',p_order_id);
  end if;

  if v_next='cancelled' and v_order.rider_id is not null then
    select user_id into v_rider_user from public.rider_profiles where id=v_order.rider_id;
    if v_rider_user is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_rider_user,'ออเดอร์ถูกยกเลิกโดยร้านค้า',v_message,'order',p_order_id);
    end if;
  end if;

  return v_next;
end
$$;
