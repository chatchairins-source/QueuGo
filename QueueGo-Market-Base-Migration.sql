-- Market is an extension of QueueGo's existing merchant/product/order/delivery model.
begin;

alter table public.products add constraint products_id_shop_unique unique (id,shop_id);
alter table public.orders add column if not exists fulfillment_vertical text not null default 'food'
  check (fulfillment_vertical in ('food','market','grocery'));

create table public.market_products (
  product_id uuid primary key,
  shop_id uuid not null,
  unit text not null check(unit in ('ชิ้น','กิโลกรัม','ขีด','กรัม','ถุง','แพ็ก','มัด','ลูก','ขวด','กล่อง')),
  pack_size numeric(12,3) not null check(pack_size>0),
  item_weight_kg numeric(12,3) not null check(item_weight_kg>=0),
  stock_quantity numeric(12,3) not null default 0 check(stock_quantity>=0),
  min_stock numeric(12,3) not null default 0 check(min_stock>=0),
  cost_price numeric(12,2) not null default 0 check(cost_price>=0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key(product_id,shop_id) references public.products(id,shop_id)
);
create index market_products_shop_idx on public.market_products(shop_id,product_id);

create table public.market_stock_movements (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null,
  shop_id uuid not null,
  type text not null check(type in ('purchase','sale','adjustment','cancel','return','waste')),
  quantity numeric(12,3) not null check(quantity>0),
  before_quantity numeric(12,3) not null,
  after_quantity numeric(12,3) not null,
  cost_price numeric(12,2),
  reference_type text not null,
  reference_id uuid,
  actor_id uuid,
  note text,
  created_at timestamptz not null default now(),
  foreign key(product_id) references public.market_products(product_id)
);
create index market_stock_movements_shop_date_idx on public.market_stock_movements(shop_id,created_at desc);
alter table public.market_products enable row level security;
alter table public.market_stock_movements enable row level security;
revoke all on public.market_products,public.market_stock_movements from public,anon,authenticated;
grant select on public.market_products,public.market_stock_movements to authenticated;
create policy market_stock_owner_read on public.market_products for select to authenticated
  using (shop_id=public.pos_my_shop() or public.is_active_admin());
create policy market_movement_owner_read on public.market_stock_movements for select to authenticated
  using (shop_id=public.pos_my_shop() or public.is_active_admin());

create or replace function public.market_save_product(
  p_product uuid,p_name text,p_category text,p_price numeric,p_image text,
  p_unit text,p_pack_size numeric,p_weight_kg numeric,p_cost numeric,p_initial_stock numeric,
  p_min_stock numeric,p_available boolean default true
) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop uuid;v_id uuid;v_category text;
begin
  v_shop:=public.pos_my_shop();
  select public_category into v_category from public.shop_profiles where id=v_shop;
  if v_shop is null or not public.pos_is_owner() or v_category not in ('market','meat','fish','vegetable','fruit','grocery') then raise exception 'market seller access denied'; end if;
  if length(trim(coalesce(p_name,''))) not between 2 and 100 or length(trim(coalesce(p_category,''))) not between 1 and 80
     or p_price is null or p_price<0 or p_unit not in ('ชิ้น','กิโลกรัม','ขีด','กรัม','ถุง','แพ็ก','มัด','ลูก','ขวด','กล่อง')
     or p_pack_size is null or p_pack_size<=0 or p_weight_kg is null or p_weight_kg<0
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
end $$;
revoke all on function public.market_save_product(uuid,text,text,numeric,text,text,numeric,numeric,numeric,numeric,numeric,boolean) from public,anon;
grant execute on function public.market_save_product(uuid,text,text,numeric,text,text,numeric,numeric,numeric,numeric,numeric,boolean) to authenticated;

create or replace function public.market_adjust_stock(p_product uuid,p_delta numeric,p_type text,p_note text default null)
returns numeric language plpgsql security definer set search_path=public,pg_temp as $$
declare v_row public.market_products%rowtype;v_after numeric;
begin
  if not public.pos_is_owner() or p_delta is null or p_delta=0 or p_type not in ('purchase','adjustment','return','waste')
    or length(coalesce(p_note,''))>500 then raise exception 'stock adjustment denied'; end if;
  select * into v_row from public.market_products where product_id=p_product and shop_id=public.pos_my_shop() for update;
  if not found then raise exception 'product not in this shop'; end if;
  v_after:=v_row.stock_quantity+p_delta;
  if v_after<0 then raise exception 'insufficient market stock'; end if;
  update public.market_products set stock_quantity=v_after,updated_at=now() where product_id=p_product;
  insert into public.market_stock_movements(product_id,shop_id,type,quantity,before_quantity,after_quantity,cost_price,reference_type,actor_id,note)
    values(p_product,v_row.shop_id,p_type,abs(p_delta),v_row.stock_quantity,v_after,v_row.cost_price,'manual',auth.uid(),p_note);
  return v_after;
end $$;
revoke all on function public.market_adjust_stock(uuid,numeric,text,text) from public,anon;
grant execute on function public.market_adjust_stock(uuid,numeric,text,text) to authenticated;

create or replace function public.market_public_catalog()
returns table(product_id uuid,shop_id uuid,shop_user_id uuid,shop_name text,shop_category text,shop_logo text,
  name text,category text,description text,image text,price numeric,unit text,pack_size numeric,
  available_packs numeric,weight_kg numeric)
language sql stable security definer set search_path=public,pg_temp as $$
  select p.id,p.shop_id,s.user_id,s.shop_name,s.public_category,s.public_logo,
    p.name,p.category,p.description,p.image,coalesce(p.delivery_price,p.price),m.unit,m.pack_size,
    floor(m.stock_quantity/m.pack_size),m.item_weight_kg
  from public.market_products m join public.products p on p.id=m.product_id and p.shop_id=m.shop_id
  join public.shop_profiles s on s.id=m.shop_id join public.users u on u.id=s.user_id
  where s.status='active' and u.status='active' and p.available and p.delivery_available
    and s.public_category in ('market','meat','fish','vegetable','fruit','grocery')
  order by s.shop_name,p.name limit 1000;
$$;
revoke all on function public.market_public_catalog() from public;
grant execute on function public.market_public_catalog() to anon,authenticated;

create or replace function public.market_item_stock_guard()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_product uuid;v_order public.orders%rowtype;v_row public.market_products%rowtype;
  v_delta numeric;v_after numeric;v_type text;v_reference uuid;
begin
  v_product:=case when tg_op='DELETE' then old.product_id else new.product_id end;
  if tg_op='UPDATE' and new.product_id is distinct from old.product_id and
     (exists(select 1 from public.market_products where product_id=old.product_id)
      or exists(select 1 from public.market_products where product_id=new.product_id)) then
    raise exception 'market item product cannot change';
  end if;
  select * into v_row from public.market_products where product_id=v_product for update;
  if not found then return case when tg_op='DELETE' then old else new end; end if;
  if current_setting('queuego.market_checkout',true) is distinct from 'on'
     and current_setting('queuego.pos_rpc',true) is distinct from 'on'
     and not public.is_active_admin() then raise exception 'market items require server order RPC'; end if;
  select * into v_order from public.orders where id=case when tg_op='DELETE' then old.order_id else new.order_id end;
  if not found or v_order.shop_id<>v_row.shop_id then raise exception 'market product/shop mismatch'; end if;
  if tg_op='INSERT' and v_order.status='cancelled' then raise exception 'cancelled order cannot reserve stock'; end if;
  if tg_op='DELETE' and v_order.status='cancelled' then return old; end if;
  v_delta:=(case when tg_op='DELETE' then -old.quantity when tg_op='INSERT' then new.quantity
    else new.quantity-old.quantity end)*v_row.pack_size;
  if v_delta<>0 then
    v_after:=v_row.stock_quantity-v_delta;
    if v_after<0 then raise exception 'insufficient market stock for %',v_product; end if;
    update public.market_products set stock_quantity=v_after,updated_at=now() where product_id=v_product;
    v_type:=case when v_delta>0 then 'sale' else 'return' end;
    v_reference:=case when tg_op='DELETE' then old.id else new.id end;
    insert into public.market_stock_movements(product_id,shop_id,type,quantity,before_quantity,after_quantity,cost_price,reference_type,reference_id,actor_id)
      values(v_product,v_row.shop_id,v_type,abs(v_delta),v_row.stock_quantity,v_after,v_row.cost_price,'order_item',v_reference,auth.uid());
  end if;
  if tg_op='INSERT' and v_order.fulfillment_vertical='food' then
    update public.orders set fulfillment_vertical=case when (select public_category from public.shop_profiles where id=v_row.shop_id)='grocery'
      then 'grocery' else 'market' end where id=v_order.id;
  end if;
  return case when tg_op='DELETE' then old else new end;
end $$;
create trigger market_item_stock after insert or update of quantity,product_id or delete on public.order_items
  for each row execute function public.market_item_stock_guard();
revoke all on function public.market_item_stock_guard() from public,anon,authenticated;

create or replace function public.market_restore_cancelled_order()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_item record;v_row public.market_products%rowtype;v_amount numeric;
begin
  if new.status<>'cancelled' or old.status='cancelled' then return new; end if;
  for v_item in select i.product_id,i.id,i.quantity from public.order_items i
    join public.market_products m on m.product_id=i.product_id where i.order_id=new.id loop
    select * into v_row from public.market_products where product_id=v_item.product_id for update;
    v_amount:=v_item.quantity*v_row.pack_size;
    update public.market_products set stock_quantity=stock_quantity+v_amount,updated_at=now() where product_id=v_item.product_id;
    insert into public.market_stock_movements(product_id,shop_id,type,quantity,before_quantity,after_quantity,cost_price,reference_type,reference_id,actor_id)
      values(v_item.product_id,v_row.shop_id,'cancel',v_amount,v_row.stock_quantity,v_row.stock_quantity+v_amount,v_row.cost_price,'order_item',v_item.id,auth.uid());
  end loop;
  return new;
end $$;
create trigger market_restore_on_cancel after update of status on public.orders
  for each row execute function public.market_restore_cancelled_order();
revoke all on function public.market_restore_cancelled_order() from public,anon,authenticated;
commit;
