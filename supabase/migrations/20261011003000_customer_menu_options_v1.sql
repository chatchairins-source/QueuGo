-- QueueGo customer menu options v1
-- Backward-compatible extension of ordinary food delivery checkout.
-- Existing {product_id, qty} carts keep the same price and behavior.

alter table public.order_items
  add column if not exists selected_options jsonb not null default '[]'::jsonb,
  add column if not exists base_unit_price numeric,
  add column if not exists option_price_delta numeric not null default 0;

update public.order_items
set base_unit_price = unit_price
where base_unit_price is null;

alter table public.order_items
  drop constraint if exists qg_order_items_selected_options_array,
  add constraint qg_order_items_selected_options_array
    check (jsonb_typeof(selected_options) = 'array'),
  drop constraint if exists qg_order_items_option_price_delta_nonnegative,
  add constraint qg_order_items_option_price_delta_nonnegative
    check (option_price_delta >= 0);

create or replace function public.qg_resolve_product_options(
  p_variants jsonb,
  p_selected jsonb default '[]'::jsonb
)
returns jsonb
language plpgsql
immutable
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_variants jsonb := coalesce(p_variants, '[]'::jsonb);
  v_selected jsonb := coalesce(p_selected, '[]'::jsonb);
  v_group jsonb;
  v_option jsonb;
  v_pick jsonb;
  v_group_key text;
  v_group_name text;
  v_group_type text;
  v_option_key text;
  v_option_name text;
  v_pair text;
  v_seen text[] := array[]::text[];
  v_explicit_count integer;
  v_explicit_matches integer := 0;
  v_default_normal boolean;
  v_default_found boolean;
  v_selected_here boolean;
  v_delta numeric := 0;
  v_price numeric;
  v_snapshot jsonb := '[]'::jsonb;
begin
  if jsonb_typeof(v_selected) <> 'array' or jsonb_array_length(v_selected) > 30 then
    raise exception 'invalid product options';
  end if;

  for v_pick in select value from jsonb_array_elements(v_selected)
  loop
    if jsonb_typeof(v_pick) <> 'object' then
      raise exception 'invalid product option';
    end if;
    v_group_key := trim(coalesce(v_pick->>'group_key', ''));
    v_option_key := trim(coalesce(v_pick->>'option_key', ''));
    if v_group_key = '' or v_option_key = ''
       or length(v_group_key) > 80 or length(v_option_key) > 80 then
      raise exception 'invalid product option';
    end if;
    v_pair := v_group_key || chr(31) || v_option_key;
    if v_pair = any(v_seen) then
      raise exception 'duplicate product option';
    end if;
    v_seen := array_append(v_seen, v_pair);
  end loop;

  if jsonb_typeof(v_variants) <> 'array' then
    if jsonb_array_length(v_selected) > 0 then
      raise exception 'product option configuration invalid';
    end if;
    return jsonb_build_object(
      'selected_options', '[]'::jsonb,
      'price_delta', 0
    );
  end if;

  for v_group in select value from jsonb_array_elements(v_variants)
  loop
    if jsonb_typeof(v_group) <> 'object'
       or jsonb_typeof(v_group->'options') <> 'array' then
      continue;
    end if;

    v_group_key := trim(coalesce(v_group->>'key', ''));
    if v_group_key = '' then
      continue;
    end if;
    v_group_name := trim(coalesce(nullif(v_group->>'name', ''), v_group_key));
    v_group_type := lower(trim(coalesce(nullif(v_group->>'type', ''), 'multi')));
    if v_group_type not in ('single','multi') then
      raise exception 'product option configuration invalid';
    end if;

    select count(*)
    into v_explicit_count
    from jsonb_array_elements(v_selected) s(value)
    where trim(coalesce(s.value->>'group_key','')) = v_group_key;

    if v_group_type = 'single' and v_explicit_count > 1 then
      raise exception 'select one product option';
    end if;

    v_default_normal :=
      v_explicit_count = 0
      and coalesce(v_group->>'required','false') = 'true'
      and v_group_key = 'portion';
    v_default_found := false;

    if coalesce(v_group->>'required','false') = 'true'
       and v_explicit_count = 0
       and not v_default_normal then
      raise exception 'required product option missing';
    end if;

    for v_option in select value from jsonb_array_elements(v_group->'options')
    loop
      if jsonb_typeof(v_option) <> 'object' then
        raise exception 'product option configuration invalid';
      end if;

      v_option_key := trim(coalesce(v_option->>'key', ''));
      v_option_name := trim(coalesce(nullif(v_option->>'name', ''), v_option_key));
      if v_option_key = '' then
        raise exception 'product option configuration invalid';
      end if;

      v_selected_here := exists(
        select 1
        from jsonb_array_elements(v_selected) s(value)
        where trim(coalesce(s.value->>'group_key','')) = v_group_key
          and trim(coalesce(s.value->>'option_key','')) = v_option_key
      );

      if v_default_normal and v_option_key = 'normal' then
        v_selected_here := true;
        v_default_found := true;
      end if;

      if not v_selected_here then
        continue;
      end if;

      if not coalesce(v_option->>'price_delta','0') ~ '^[0-9]+([.][0-9]+)?$' then
        raise exception 'product option price invalid';
      end if;
      v_price := round((v_option->>'price_delta')::numeric, 2);
      if v_price < 0 or v_price > 999999 then
        raise exception 'product option price invalid';
      end if;

      if exists(
        select 1
        from jsonb_array_elements(v_selected) s(value)
        where trim(coalesce(s.value->>'group_key','')) = v_group_key
          and trim(coalesce(s.value->>'option_key','')) = v_option_key
      ) then
        v_explicit_matches := v_explicit_matches + 1;
      end if;

      v_snapshot := v_snapshot || jsonb_build_array(
        jsonb_build_object(
          'schema', 'queuego.menu-options.v1',
          'group_key', v_group_key,
          'group_name', v_group_name,
          'option_key', v_option_key,
          'option_name', v_option_name,
          'price_delta', v_price
        )
      );
      v_delta := v_delta + v_price;
    end loop;

    if v_default_normal and not v_default_found then
      raise exception 'required normal product option unavailable';
    end if;
  end loop;

  if v_explicit_matches <> jsonb_array_length(v_selected) then
    raise exception 'unknown product option';
  end if;

  return jsonb_build_object(
    'selected_options', v_snapshot,
    'price_delta', round(v_delta, 2)
  );
end
$function$;

revoke all on function public.qg_resolve_product_options(jsonb,jsonb) from public;
revoke all on function public.qg_resolve_product_options(jsonb,jsonb) from anon;
revoke all on function public.qg_resolve_product_options(jsonb,jsonb) from authenticated;

create or replace function public.queuego_place_cash_order_core(
  p_order_id uuid,
  p_shop_id uuid,
  p_items jsonb,
  p_delivery_lat double precision,
  p_delivery_lng double precision,
  p_delivery_address text,
  p_expected_subtotal numeric,
  p_expected_delivery_fee numeric,
  p_note text default null::text
)
returns jsonb
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_customer uuid; v_shop public.shop_profiles%rowtype; v_product public.products%rowtype; v_item jsonb;
  v_product_id uuid; v_qty integer; v_count integer:=0; v_subtotal numeric:=0; v_fee numeric; v_total numeric;
  v_distance double precision; v_number text;
  v_old public.orders%rowtype; v_seen text[]:=array[]::text[];
  v_option_result jsonb; v_selected_options jsonb; v_option_delta numeric; v_base_unit numeric; v_unit numeric; v_key text;
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

    select * into v_product
    from public.products
    where id=v_product_id and shop_id=p_shop_id and available=true and delivery_available=true
    for share;

    if not found then raise exception 'product unavailable'; end if;
    v_base_unit:=round(coalesce(v_product.delivery_price,v_product.price),2);
    if v_base_unit<0 then raise exception 'invalid product price'; end if;

    v_option_result:=public.qg_resolve_product_options(v_product.variants, coalesce(v_item->'options','[]'::jsonb));
    v_selected_options:=coalesce(v_option_result->'selected_options','[]'::jsonb);
    v_option_delta:=coalesce((v_option_result->>'price_delta')::numeric,0);
    v_unit:=round(v_base_unit+v_option_delta,2);

    v_key:=v_product_id::text||'|'||v_selected_options::text;
    if v_key=any(v_seen) then raise exception 'duplicate product option combination in cart'; end if;
    v_seen:=array_append(v_seen,v_key);

    v_subtotal:=v_subtotal+v_unit*v_qty;
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

    v_base_unit:=round(coalesce(v_product.delivery_price,v_product.price),2);
    v_option_result:=public.qg_resolve_product_options(v_product.variants, coalesce(v_item->'options','[]'::jsonb));
    v_selected_options:=coalesce(v_option_result->'selected_options','[]'::jsonb);
    v_option_delta:=coalesce((v_option_result->>'price_delta')::numeric,0);
    v_unit:=round(v_base_unit+v_option_delta,2);

    insert into public.order_items(
      order_id,product_id,item_type,item_name,description,quantity,
      base_unit_price,option_price_delta,selected_options,unit_price,total_price,item_image
    )
    values(
      p_order_id,v_product_id,'product',v_product.name,v_product.description,v_qty,
      v_base_unit,v_option_delta,v_selected_options,v_unit,round(v_unit*v_qty,2),v_product.image
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

create or replace function public.qg_merchant_edit_order_items(
  p_order_id uuid,
  p_items jsonb,
  p_reason text default 'สินค้าหมด / สินค้าทดแทน'::text
)
returns jsonb
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_actor uuid;
  v_order public.orders%rowtype;
  v_item jsonb;
  v_existing public.order_items%rowtype;
  v_product public.products%rowtype;
  v_qty integer;
  v_item_id uuid;
  v_product_id uuid;
  v_unit numeric;
  v_base_unit numeric;
  v_option_delta numeric;
  v_selected_options jsonb;
  v_option_result jsonb;
  v_new_subtotal numeric:=0;
  v_authorized_subtotal numeric;
  v_before_items jsonb;
  v_after_items jsonb:='[]'::jsonb;
  v_seen text[]:=array[]::text[];
  v_key text;
  v_total numeric;
  v_row jsonb;
begin
  if auth.uid() is null then raise exception 'shop login required'; end if;

  select u.id into v_actor
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='shop'
    and u.status='active';

  if v_actor is null then raise exception 'active shop required'; end if;
  if p_order_id is null then raise exception 'order id required'; end if;
  if p_items is null or jsonb_typeof(p_items)<>'array'
     or jsonb_array_length(p_items) not between 1 and 30 then
    raise exception 'invalid order items';
  end if;

  select o.* into v_order
  from public.orders o
  join public.shop_profiles s on s.id=o.shop_id
  where o.id=p_order_id
    and s.user_id=v_actor
  for update of o;

  if not found then raise exception 'order unavailable'; end if;

  if v_order.sales_channel is distinct from 'QUEUEGO_DELIVERY'
     or v_order.order_type<>'shopping'
     or v_order.market_order_id is not null
     or coalesce(v_order.fulfillment_vertical,'food')<>'food' then
    raise exception 'order substitution is only available for ordinary food delivery';
  end if;

  if v_order.status<>'rider_assigned' or v_order.rider_id is null then
    raise exception 'order items can only be edited after Rider assignment and before preparation';
  end if;

  select coalesce(
    (
      select a.before_subtotal
      from public.qg_order_item_adjustments a
      where a.order_id=v_order.id
      order by a.created_at,a.id
      limit 1
    ),
    v_order.subtotal
  ) into v_authorized_subtotal;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id',i.id,
        'product_id',i.product_id,
        'item_name',i.item_name,
        'description',i.description,
        'quantity',i.quantity,
        'base_unit_price',i.base_unit_price,
        'option_price_delta',i.option_price_delta,
        'selected_options',i.selected_options,
        'unit_price',i.unit_price,
        'total_price',i.total_price,
        'item_image',i.item_image
      )
      order by i.created_at,i.id
    ),
    '[]'::jsonb
  )
  into v_before_items
  from public.order_items i
  where i.order_id=v_order.id;

  for v_item in select value from jsonb_array_elements(p_items) loop
    if jsonb_typeof(v_item)<>'object'
       or coalesce(v_item->>'qty','') !~ '^[1-9][0-9]?$' then
      raise exception 'invalid order item';
    end if;

    v_qty:=(v_item->>'qty')::integer;
    v_item_id:=nullif(v_item->>'item_id','')::uuid;
    v_product_id:=nullif(v_item->>'product_id','')::uuid;

    if v_item_id is not null then
      select * into v_existing
      from public.order_items i
      where i.id=v_item_id and i.order_id=v_order.id;

      if not found then raise exception 'order item unavailable'; end if;

      v_selected_options:=coalesce(v_existing.selected_options,'[]'::jsonb);
      v_key:=coalesce(v_existing.product_id::text,'item:'||v_existing.id::text)||'|'||v_selected_options::text;
      if v_key=any(v_seen) then raise exception 'duplicate order item'; end if;
      v_seen:=array_append(v_seen,v_key);

      v_unit:=round(v_existing.unit_price,2);
      v_row:=jsonb_build_object(
        'product_id',v_existing.product_id,
        'item_name',v_existing.item_name,
        'description',v_existing.description,
        'quantity',v_qty,
        'base_unit_price',coalesce(v_existing.base_unit_price,v_existing.unit_price),
        'option_price_delta',coalesce(v_existing.option_price_delta,0),
        'selected_options',v_selected_options,
        'unit_price',v_unit,
        'total_price',round(v_unit*v_qty,2),
        'item_image',v_existing.item_image
      );
    elsif v_product_id is not null then
      select p.* into v_product
      from public.products p
      where p.id=v_product_id
        and p.shop_id=v_order.shop_id
        and p.available=true
        and p.delivery_available=true;

      if not found then raise exception 'substitute product unavailable'; end if;

      if exists(
        select 1 from public.market_products mp
        where mp.product_id=v_product.id
      ) then
        raise exception 'market product substitution must use market flow';
      end if;

      v_option_result:=public.qg_resolve_product_options(v_product.variants,'[]'::jsonb);
      v_selected_options:=coalesce(v_option_result->'selected_options','[]'::jsonb);
      v_option_delta:=coalesce((v_option_result->>'price_delta')::numeric,0);
      v_base_unit:=round(coalesce(v_product.delivery_price,v_product.price),2);
      v_unit:=round(v_base_unit+v_option_delta,2);
      if v_unit<0 then raise exception 'invalid substitute price'; end if;

      v_key:=v_product.id::text||'|'||v_selected_options::text;
      if v_key=any(v_seen) then raise exception 'duplicate product'; end if;
      v_seen:=array_append(v_seen,v_key);

      v_row:=jsonb_build_object(
        'product_id',v_product.id,
        'item_name',v_product.name,
        'description',v_product.description,
        'quantity',v_qty,
        'base_unit_price',v_base_unit,
        'option_price_delta',v_option_delta,
        'selected_options',v_selected_options,
        'unit_price',v_unit,
        'total_price',round(v_unit*v_qty,2),
        'item_image',v_product.image
      );
    else
      raise exception 'item_id or product_id required';
    end if;

    v_new_subtotal:=v_new_subtotal+coalesce((v_row->>'total_price')::numeric,0);
    v_after_items:=v_after_items||jsonb_build_array(v_row);
  end loop;

  v_new_subtotal:=round(v_new_subtotal,2);

  if v_new_subtotal<=0 then raise exception 'order must keep at least one paid item'; end if;
  if v_new_subtotal>round(v_authorized_subtotal,2) then
    raise exception 'replacement total cannot exceed customer-authorized subtotal';
  end if;

  perform set_config('queuego.merchant_order_edit','on',true);

  delete from public.order_items
  where order_id=v_order.id;

  for v_row in select value from jsonb_array_elements(v_after_items) loop
    insert into public.order_items(
      order_id,product_id,item_type,item_name,description,
      quantity,base_unit_price,option_price_delta,selected_options,
      unit_price,total_price,item_image
    )
    values(
      v_order.id,
      nullif(v_row->>'product_id','')::uuid,
      'product',
      coalesce(v_row->>'item_name','สินค้า'),
      nullif(v_row->>'description',''),
      (v_row->>'quantity')::numeric,
      (v_row->>'base_unit_price')::numeric,
      (v_row->>'option_price_delta')::numeric,
      coalesce(v_row->'selected_options','[]'::jsonb),
      (v_row->>'unit_price')::numeric,
      (v_row->>'total_price')::numeric,
      nullif(v_row->>'item_image','')
    );
  end loop;

  v_total:=round(v_new_subtotal+v_order.delivery_fee,2);

  update public.orders
  set subtotal=v_new_subtotal,
      total_amount=v_total,
      updated_at=now()
  where id=v_order.id;

  update public.payments
  set amount=v_total
  where order_id=v_order.id
    and payment_method='cash'
    and status='pending';

  insert into public.qg_order_item_adjustments(
    order_id,shop_id,actor_user_id,
    before_subtotal,after_subtotal,
    before_items,after_items,reason
  )
  values(
    v_order.id,v_order.shop_id,v_actor,
    v_order.subtotal,v_new_subtotal,
    v_before_items,v_after_items,
    left(coalesce(nullif(trim(p_reason),''),'สินค้าหมด / สินค้าทดแทน'),200)
  );

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(
    v_order.customer_id,
    'ร้านปรับรายการสินค้า',
    'ร้านได้ปรับรายการสินค้าในออเดอร์ของคุณ ยอดใหม่ '||trim(to_char(v_total,'FM999999990.00'))||' บาท',
    'order',
    v_order.id
  );

  insert into public.notifications(user_id,title,message,type,reference_id)
  select r.user_id,
         'ร้านปรับรายการสินค้า',
         'ร้านได้ปรับรายการสินค้า ยอดค่าสินค้าที่ต้องชำระร้าน '
           ||trim(to_char(v_new_subtotal,'FM999999990.00'))||' บาท',
         'order',
         v_order.id
  from public.rider_profiles r
  where r.id=v_order.rider_id;

  return jsonb_build_object(
    'order_id',v_order.id,
    'subtotal',v_new_subtotal,
    'delivery_fee',v_order.delivery_fee,
    'total_amount',v_total,
    'authorized_subtotal',v_authorized_subtotal,
    'items',v_after_items
  );
end
$function$;

comment on column public.order_items.selected_options is
  'Server-snapshotted QueueGo menu option selections for this order line.';
comment on column public.order_items.base_unit_price is
  'Product delivery/base unit price before menu option deltas.';
comment on column public.order_items.option_price_delta is
  'Per-unit price delta from validated selected menu options.';
