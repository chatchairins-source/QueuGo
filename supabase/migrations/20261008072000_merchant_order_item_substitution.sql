-- QueueGo Merchant order substitution before preparation.
-- Ordinary food delivery only. The merchant may remove, change quantities,
-- or add substitute products from the same shop while status=rider_assigned.
-- The customer-authorized merchandise subtotal can never be exceeded.

CREATE TABLE IF NOT EXISTS public.qg_order_item_adjustments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
  shop_id uuid NOT NULL REFERENCES public.shop_profiles(id) ON DELETE CASCADE,
  actor_user_id uuid NOT NULL REFERENCES public.users(id),
  before_subtotal numeric NOT NULL,
  after_subtotal numeric NOT NULL,
  before_items jsonb NOT NULL DEFAULT '[]'::jsonb,
  after_items jsonb NOT NULL DEFAULT '[]'::jsonb,
  reason text NOT NULL DEFAULT 'substitution',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS qg_order_item_adjustments_order_idx
  ON public.qg_order_item_adjustments(order_id,created_at);

ALTER TABLE public.qg_order_item_adjustments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS qg_order_item_adjustments_admin_select ON public.qg_order_item_adjustments;
CREATE POLICY qg_order_item_adjustments_admin_select
ON public.qg_order_item_adjustments FOR SELECT
USING (public.get_my_role()='admin');

DROP POLICY IF EXISTS qg_order_item_adjustments_shop_select ON public.qg_order_item_adjustments;
CREATE POLICY qg_order_item_adjustments_shop_select
ON public.qg_order_item_adjustments FOR SELECT
USING (
  shop_id IN (
    SELECT s.id FROM public.shop_profiles s
    WHERE s.user_id=public.get_my_user_id()
  )
);

DROP POLICY IF EXISTS qg_order_item_adjustments_customer_select ON public.qg_order_item_adjustments;
CREATE POLICY qg_order_item_adjustments_customer_select
ON public.qg_order_item_adjustments FOR SELECT
USING (
  order_id IN (
    SELECT o.id FROM public.orders o
    WHERE o.customer_id=public.get_my_user_id()
  )
);

DROP POLICY IF EXISTS qg_order_item_adjustments_rider_select ON public.qg_order_item_adjustments;
CREATE POLICY qg_order_item_adjustments_rider_select
ON public.qg_order_item_adjustments FOR SELECT
USING (
  order_id IN (
    SELECT o.id
    FROM public.orders o
    JOIN public.rider_profiles r ON r.id=o.rider_id
    WHERE r.user_id=public.get_my_user_id()
  )
);

-- Trusted merchant edit RPC uses a transaction-local switch. Direct table
-- writes remain locked exactly as before.
CREATE OR REPLACE FUNCTION public.queuego_guard_cash_items()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
declare v_order uuid;
begin
  v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;

  if current_setting('queuego.merchant_order_edit',true)='on'
     and public.get_my_role()='shop' then
    return case when tg_op='DELETE' then old else new end;
  end if;

  if public.get_my_role()<>'admin'
     and exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then
    raise exception 'order items are locked';
  end if;

  if tg_op='UPDATE'
     and new.order_id is distinct from old.order_id
     and public.get_my_role()<>'admin'
     and exists(select 1 from public.queuego_cash_order_locks where order_id=new.order_id) then
    raise exception 'order items are locked';
  end if;

  return case when tg_op='DELETE' then old else new end;
end
$function$;

CREATE OR REPLACE FUNCTION public.queuego_guard_cash_payment()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
declare v_order uuid;v_role text;
begin
  v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  if not exists(select 1 from public.queuego_cash_order_locks where order_id=v_order) then
    return case when tg_op='DELETE' then old else new end;
  end if;

  v_role:=public.get_my_role();

  if current_setting('queuego.merchant_order_edit',true)='on'
     and v_role='shop'
     and tg_op='UPDATE'
     and new.order_id is not distinct from old.order_id
     and new.payer_id is not distinct from old.payer_id
     and new.payment_method is not distinct from old.payment_method
     and new.status is not distinct from old.status
     and old.status='pending' then
    return new;
  end if;

  if v_role='admin' then
    return case when tg_op='DELETE' then old else new end;
  end if;

  if current_setting('queuego.route_bundle_reprice',true)='on'
     and v_role='rider' and tg_op='UPDATE'
     and new.order_id is not distinct from old.order_id
     and new.payer_id is not distinct from old.payer_id
     and new.payment_method is not distinct from old.payment_method
     and new.status is not distinct from old.status
     and old.status='pending'
     and new.amount<=old.amount
     and exists(
       select 1 from public.orders o
       join public.rider_profiles r on r.id=o.rider_id
       where o.id=old.order_id and o.route_bundle_id is not null
         and r.user_id=public.get_my_user_id()
         and new.amount=o.total_amount
     ) then
    return new;
  end if;

  if tg_op<>'UPDATE' then raise exception 'cash payment is locked'; end if;

  if new.order_id is distinct from old.order_id
     or new.payer_id is distinct from old.payer_id
     or new.amount is distinct from old.amount
     or new.payment_method is distinct from old.payment_method then
    raise exception 'cash payment amount is locked';
  end if;

  if not (
    (v_role='rider' and old.status='pending' and new.status='paid'
      and exists(
        select 1 from public.orders o
        join public.rider_profiles r on r.id=o.rider_id
        where o.id=old.order_id and o.status='completed'
          and r.user_id=public.get_my_user_id()
      ))
    or
    (old.status='pending' and new.status='cancelled' and exists(
      select 1 from public.orders o
      left join public.shop_profiles s on s.id=o.shop_id
      where o.id=old.order_id and o.status='cancelled'
        and (
          (v_role='customer' and o.customer_id=public.get_my_user_id())
          or
          (v_role='shop' and s.user_id=public.get_my_user_id())
        )
    ))
  ) then
    raise exception 'cash payment status change forbidden';
  end if;

  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.queuego_guard_cash_order()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
declare v_role text;v_own_rider uuid;
begin
  if not exists(select 1 from public.queuego_cash_order_locks where order_id=old.id) then
    return new;
  end if;

  if current_setting('queuego.market_recalc',true)='on'
     and old.market_order_id is not null
     and new.market_order_id=old.market_order_id then
    return new;
  end if;

  v_role:=public.get_my_role();

  if current_setting('queuego.merchant_order_edit',true)='on'
     and v_role='shop'
     and new.id=old.id
     and new.status=old.status
     and new.rider_id is not distinct from old.rider_id
     and new.customer_id is not distinct from old.customer_id
     and new.shop_id is not distinct from old.shop_id
     and new.delivery_fee is not distinct from old.delivery_fee
     and new.market_order_id is null
     and old.market_order_id is null then
    return new;
  end if;

  if v_role='admin' then return new; end if;

  if current_setting('queuego.route_bundle_reprice',true)='on'
     and v_role='rider'
     and old.market_order_id is null
     and new.market_order_id is null then
    select id into v_own_rider
    from public.rider_profiles
    where user_id=public.get_my_user_id();

    if v_own_rider is not null
       and new.order_number is not distinct from old.order_number
       and new.customer_id is not distinct from old.customer_id
       and new.shop_id is not distinct from old.shop_id
       and new.order_type is not distinct from old.order_type
       and new.subtotal is not distinct from old.subtotal
       and new.gp_rate is not distinct from old.gp_rate
       and new.gp_amount is not distinct from old.gp_amount
       and new.pickup_address is not distinct from old.pickup_address
       and new.pickup_latitude is not distinct from old.pickup_latitude
       and new.pickup_longitude is not distinct from old.pickup_longitude
       and new.delivery_address is not distinct from old.delivery_address
       and new.delivery_latitude is not distinct from old.delivery_latitude
       and new.delivery_longitude is not distinct from old.delivery_longitude
       and new.created_by is not distinct from old.created_by
       and (
         (
           old.status='searching_rider' and old.rider_id is null
           and new.status='rider_assigned' and new.rider_id=v_own_rider
           and new.delivery_fee<=old.delivery_fee
           and new.total_amount=old.total_amount-old.delivery_fee+new.delivery_fee
           and new.route_bundle_id is not null
           and new.route_bundle_sequence>1
           and new.bundle_original_delivery_fee=old.delivery_fee
           and new.bundle_customer_savings=old.delivery_fee-new.delivery_fee
           and new.bundle_rider_extra_fee=new.delivery_fee
         )
         or
         (
           old.rider_id=v_own_rider and new.rider_id=old.rider_id
           and new.status=old.status
           and new.delivery_fee=old.delivery_fee
           and new.total_amount=old.total_amount
           and old.route_bundle_id is null
           and new.route_bundle_id is not null
           and new.route_bundle_sequence=1
           and new.bundle_customer_savings=0
           and new.bundle_rider_extra_fee=0
         )
       ) then
      return new;
    end if;
  end if;

  if new.order_number is distinct from old.order_number
     or new.customer_id is distinct from old.customer_id
     or new.shop_id is distinct from old.shop_id
     or new.order_type is distinct from old.order_type
     or new.subtotal is distinct from old.subtotal
     or new.delivery_fee is distinct from old.delivery_fee
     or new.total_amount is distinct from old.total_amount
     or new.gp_rate is distinct from old.gp_rate
     or new.gp_amount is distinct from old.gp_amount
     or new.pickup_address is distinct from old.pickup_address
     or new.pickup_latitude is distinct from old.pickup_latitude
     or new.pickup_longitude is distinct from old.pickup_longitude
     or new.delivery_address is distinct from old.delivery_address
     or new.delivery_latitude is distinct from old.delivery_latitude
     or new.delivery_longitude is distinct from old.delivery_longitude
     or new.created_by is distinct from old.created_by
     or new.route_bundle_id is distinct from old.route_bundle_id
     or new.route_bundle_sequence is distinct from old.route_bundle_sequence
     or new.bundle_original_delivery_fee is distinct from old.bundle_original_delivery_fee
     or new.bundle_customer_savings is distinct from old.bundle_customer_savings
     or new.bundle_rider_extra_fee is distinct from old.bundle_rider_extra_fee
     or new.bundle_detour_km is distinct from old.bundle_detour_km
     or new.bundle_added_minutes is distinct from old.bundle_added_minutes
     or new.bundle_route_sequence is distinct from old.bundle_route_sequence then
    raise exception 'order financial details are locked';
  end if;

  if new.rider_id is distinct from old.rider_id
     and not (
       v_role='rider'
       and old.rider_id is null
       and (
         (old.status='searching_rider' and new.status='rider_assigned')
         or (old.status='ready' and new.status='assigned')
       )
       and new.rider_id in(
         select id from public.rider_profiles
         where user_id=public.get_my_user_id()
       )
     ) then
    raise exception 'rider assignment is locked';
  end if;

  if new.status is distinct from old.status then
    if v_role='customer' then
      if not (
        old.status in('pending','searching_rider')
        and new.status='cancelled'
        and old.rider_id is null
      ) then
        raise exception 'customer cannot change order status';
      end if;
    elsif v_role='shop' then
      if not (
        (old.status,new.status) in(
          ('pending','searching_rider'),
          ('rider_assigned','preparing'),
          ('preparing','ready'),
          ('pending','accepted'),
          ('accepted','preparing')
        )
        or (
          new.status='cancelled'
          and old.status in(
            'pending','accepted','searching_rider',
            'rider_assigned','preparing','ready','assigned'
          )
        )
      ) then
        raise exception 'invalid shop order transition';
      end if;
    elsif v_role='rider' then
      if not (
        (old.status,new.status) in(
          ('searching_rider','rider_assigned'),
          ('ready','picked_up'),
          ('picked_up','in_progress'),
          ('in_progress','completed'),
          ('ready','assigned'),
          ('assigned','picked_up')
        )
        and (
          old.rider_id=new.rider_id
          or (old.rider_id is null and new.rider_id is not null)
        )
      ) then
        raise exception 'invalid rider order transition';
      end if;
    else
      raise exception 'order status change forbidden';
    end if;
  end if;

  return new;
end
$function$;

CREATE OR REPLACE FUNCTION public.qg_merchant_edit_order_items(
  p_order_id uuid,
  p_items jsonb,
  p_reason text DEFAULT 'สินค้าหมด / สินค้าทดแทน'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
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

      v_key:=coalesce(v_existing.product_id::text,'item:'||v_existing.id::text);
      if v_key=any(v_seen) then raise exception 'duplicate order item'; end if;
      v_seen:=array_append(v_seen,v_key);

      v_unit:=round(v_existing.unit_price,2);
      v_row:=jsonb_build_object(
        'product_id',v_existing.product_id,
        'item_name',v_existing.item_name,
        'description',v_existing.description,
        'quantity',v_qty,
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

      v_key:=v_product.id::text;
      if v_key=any(v_seen) then raise exception 'duplicate product'; end if;
      v_seen:=array_append(v_seen,v_key);

      v_unit:=round(coalesce(v_product.delivery_price,v_product.price),2);
      if v_unit<0 then raise exception 'invalid substitute price'; end if;

      v_row:=jsonb_build_object(
        'product_id',v_product.id,
        'item_name',v_product.name,
        'description',v_product.description,
        'quantity',v_qty,
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
      quantity,unit_price,total_price,item_image
    )
    values(
      v_order.id,
      nullif(v_row->>'product_id','')::uuid,
      'product',
      coalesce(v_row->>'item_name','สินค้า'),
      nullif(v_row->>'description',''),
      (v_row->>'quantity')::numeric,
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

REVOKE ALL ON FUNCTION public.qg_merchant_edit_order_items(uuid,jsonb,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.qg_merchant_edit_order_items(uuid,jsonb,text) FROM anon;
GRANT EXECUTE ON FUNCTION public.qg_merchant_edit_order_items(uuid,jsonb,text) TO authenticated;
