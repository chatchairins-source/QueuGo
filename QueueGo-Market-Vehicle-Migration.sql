-- Existing rider_profiles and delivery pool remain the only Rider source of truth.
begin;
alter table public.market_products add constraint market_item_weight_positive check(item_weight_kg>0);
CREATE OR REPLACE FUNCTION public.market_save_product(p_product uuid, p_name text, p_category text, p_price numeric, p_image text, p_unit text, p_pack_size numeric, p_weight_kg numeric, p_cost numeric, p_initial_stock numeric, p_min_stock numeric, p_available boolean DEFAULT true)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_shop uuid;v_id uuid;v_category text;
begin
  v_shop:=public.pos_my_shop();
  select public_category into v_category from public.shop_profiles where id=v_shop;
  if v_shop is null or not public.pos_is_owner() or v_category not in ('market','meat','fish','vegetable','fruit','grocery') then raise exception 'market seller access denied'; end if;
  if length(trim(coalesce(p_name,''))) not between 2 and 100 or length(trim(coalesce(p_category,''))) not between 1 and 80
     or p_price is null or p_price<0 or p_unit not in ('ชิ้น','กิโลกรัม','ขีด','กรัม','ถุง','แพ็ก','มัด','ลูก','ขวด','กล่อง')
     or p_pack_size is null or p_pack_size<=0 or p_weight_kg is null or p_weight_kg<=0
     or p_cost is null or p_cost<0 or p_initial_stock is null or p_initial_stock<0 or p_min_stock is null or p_min_stock<0 then
    raise exception 'invalid market product';
  end if;
  if p_product is null then
    insert into public.products(shop_id,name,category,price,delivery_price,image,available,delivery_available,stock,metadata)
      values(v_shop,trim(p_name),trim(p_category),p_price,p_price,p_image,coalesce(p_available,true),true,0,'{}'::jsonb)
      returning id into v_id;
    insert into public.market_products(product_id,shop_id,unit,pack_size,item_weight_kg,stock_quantity,min_stock,cost_price)
      values(v_id,v_shop,p_unit,p_pack_size,p_weight_kg,p_initial_stock,p_min_stock,p_cost);
    if p_initial_stock>0 then
      insert into public.market_stock_movements(product_id,shop_id,type,quantity,before_quantity,after_quantity,cost_price,reference_type,actor_id)
        values(v_id,v_shop,'purchase',p_initial_stock,0,p_initial_stock,p_cost,'initial',auth.uid());
    end if;
  else
    select product_id into v_id from public.market_products where product_id=p_product and shop_id=v_shop for update;
    if not found then raise exception 'market product not found'; end if;
    if exists(select 1 from public.market_products where product_id=v_id and stock_quantity>0
      and (unit<>p_unit or pack_size<>p_pack_size)) then
      raise exception 'drain or adjust stock before changing sale unit';
    end if;
    update public.products set name=trim(p_name),category=trim(p_category),price=p_price,delivery_price=p_price,
      image=p_image,available=coalesce(p_available,true),updated_at=now() where id=v_id and shop_id=v_shop;
    update public.market_products set unit=p_unit,pack_size=p_pack_size,item_weight_kg=p_weight_kg,
      min_stock=p_min_stock,cost_price=p_cost,updated_at=now() where product_id=v_id;
    -- Stock is changed separately with a movement, never by saving product details.
  end if;
  return v_id;
end $function$;

alter table public.rider_profiles add column if not exists vehicle_name text;
alter table public.rider_profiles add column if not exists vehicle_capacity_kg numeric(12,3)
  check(vehicle_capacity_kg>0);
alter table public.rider_profiles add column if not exists vehicle_status text not null default 'pending'
  check(vehicle_status in ('pending','active','suspended'));
alter table public.rider_profiles add column if not exists vehicle_verified_at timestamptz;

create or replace function public.market_guard_vehicle_verification()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if not public.is_active_admin() then
    if tg_op='INSERT' then
      new.vehicle_capacity_kg:=null;new.vehicle_status:='pending';new.vehicle_verified_at:=null;
    else
      new.vehicle_capacity_kg:=old.vehicle_capacity_kg;
      new.vehicle_status:=old.vehicle_status;
      new.vehicle_verified_at:=old.vehicle_verified_at;
      if new.vehicle_type is distinct from old.vehicle_type or new.vehicle_plate is distinct from old.vehicle_plate then
        new.vehicle_capacity_kg:=null;new.vehicle_status:='pending';new.vehicle_verified_at:=null;
      end if;
    end if;
  end if;
  return new;
end $$;
create trigger market_vehicle_guard before insert or update of vehicle_type,vehicle_plate,vehicle_capacity_kg,vehicle_status,vehicle_verified_at
  on public.rider_profiles for each row execute function public.market_guard_vehicle_verification();
revoke all on function public.market_guard_vehicle_verification() from public,anon,authenticated;

create or replace function public.market_verify_rider_vehicle(p_rider uuid,p_capacity numeric,p_status text)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if not public.is_active_admin() or p_status not in ('active','suspended')
    or (p_status='active' and (p_capacity is null or p_capacity<=0 or p_capacity>1000)) then
    raise exception 'vehicle approval denied';
  end if;
  update public.rider_profiles set vehicle_capacity_kg=case when p_status='active' then p_capacity else vehicle_capacity_kg end,
    vehicle_status=p_status,vehicle_verified_at=case when p_status='active' then now() else null end
    where id=p_rider and vehicle_type in ('motorcycle','car','saleng') and status='active';
  if not found then raise exception 'active rider vehicle not found'; end if;
end $$;
revoke all on function public.market_verify_rider_vehicle(uuid,numeric,text) from public,anon;
grant execute on function public.market_verify_rider_vehicle(uuid,numeric,text) to authenticated;

create or replace function public.market_order_weight(p_order uuid)
returns numeric language sql stable security definer set search_path=public,pg_temp as $$
  select coalesce(sum(i.quantity*m.item_weight_kg),0)::numeric
  from public.order_items i join public.market_products m on m.product_id=i.product_id
  where i.order_id=p_order;
$$;
revoke all on function public.market_order_weight(uuid) from public,anon,authenticated;

CREATE OR REPLACE FUNCTION public.get_rider_delivery_pool()
 RETURNS TABLE(delivery_id uuid, order_id uuid, order_number text, delivery_status text, shop_id uuid, shop_name text, pickup_latitude numeric, pickup_longitude numeric, delivery_address text, delivery_latitude numeric, delivery_longitude numeric, distance_km numeric, delivery_fee numeric, created_at timestamp with time zone)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
                                                            SELECT
                                                                    d.id,
                                                                            d.order_id,
                                                                                    o.order_number,
                                                                                            d.status,
                                                                                                    o.shop_id,
                                                                                                            s.shop_name,
                                                                                                                    d.pickup_latitude,
                                                                                                                            d.pickup_longitude,
                                                                                                                                    d.delivery_address,
                                                                                                                                            d.delivery_latitude,
                                                                                                                                                    d.delivery_longitude,
                                                                                                                                                            d.distance_km,
                                                                                                                                                                    d.delivery_fee,
                                                                                                                                                                            d.created_at
                                                                                                                                                                                FROM public.deliveries AS d
                                                                                                                                                                                    INNER JOIN public.orders AS o
                                                                                                                                                                                            ON o.id = d.order_id
                                                                                                                                                                                                INNER JOIN public.shop_profiles AS s
                                                                                                                                                                                                        ON s.id = o.shop_id
                                                                                                                                                                                                            INNER JOIN public.rider_profiles AS r
                                                                                                                                                                                                                    ON r.user_id = public.get_my_user_id()
                                                                                                                                                                                                                        WHERE r.status = 'active'
                                                                                                                                                                                                                              AND COALESCE((r.metadata->>'online')::boolean, false) = true
                                                                                                                                                                                                                                    AND COALESCE((r.metadata->>'available')::boolean, false) = true
                                                                                                                                                                                                                                          AND d.status = 'pending'
                                                                                                                                                                                                                                                AND d.rider_id IS NULL
                                                                                                                                                                                                                                                      AND o.status = 'ready'
                                                                                                                                                                                                                                                            AND o.order_type = 'shopping'
  AND (o.fulfillment_vertical='food' OR (r.vehicle_type in ('motorcycle','car','saleng')
    AND r.vehicle_status='active' AND r.vehicle_verified_at is not null
    AND r.vehicle_capacity_kg>=public.market_order_weight(o.id) AND public.market_order_weight(o.id)>0))
                                                                                                                                                                                                                                                                ORDER BY d.created_at ASC;
                                                                                                                                                                                                                                                                $function$
;
CREATE OR REPLACE FUNCTION public.rider_claim_order(p_order_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid; v_order public.orders%rowtype;
begin
  select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
  if v_rider is null then raise exception 'active rider login required'; end if;
  if exists(select 1 from public.orders where rider_id=v_rider and status in ('assigned','picked_up','in_progress')) then
    raise exception 'finish current order before claiming another';
  end if;
  select * into v_order from public.orders where id=p_order_id for update;
  if not found or v_order.order_type<>'shopping' or v_order.status<>'ready' or v_order.rider_id is not null then
    raise exception 'order no longer available';
  end if;
  if v_order.fulfillment_vertical<>'food' and not exists(
    select 1 from public.rider_profiles r where r.id=v_rider and r.status='active'
      and r.vehicle_type in ('motorcycle','car','saleng') and r.vehicle_status='active'
      and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=public.market_order_weight(p_order_id)
      and public.market_order_weight(p_order_id)>0
      and coalesce((r.metadata->>'online')::boolean,false)
      and coalesce((r.metadata->>'available')::boolean,false)
  ) then raise exception 'vehicle capacity or availability insufficient for market order'; end if;
  perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set rider_id=v_rider,status='assigned',updated_at=now() where id=p_order_id;
  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values((select user_id from public.rider_profiles where id=v_rider),'rider_claim','order',p_order_id,'assigned',jsonb_build_object('from','ready'));
  return 'assigned';
end $function$
;
commit;
