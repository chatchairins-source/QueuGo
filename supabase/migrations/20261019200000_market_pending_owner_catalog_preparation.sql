-- Preparation access only: keep POS, checkout, approval and public shop visibility unchanged.
-- Reuse the same owner identity helpers as the existing Grocery preparation flow.
create or replace function public.qg_market_owner_shop()
returns uuid language sql stable security invoker set search_path=public,pg_temp as $$
  select sp.id from public.shop_profiles sp
  where auth.uid() is not null
    and public.get_my_role()='shop'
    and sp.user_id=public.get_my_user_id()
    and sp.status in ('pending','active')
    and sp.archived_at is null
    and sp.public_category in ('market','meat','fish','vegetable','fruit','grocery')
  order by sp.id limit 1
$$;
revoke all on function public.qg_market_owner_shop() from public,anon;
grant execute on function public.qg_market_owner_shop() to authenticated;

create or replace function public.market_save_product(
  p_product uuid,p_name text,p_category text,p_price numeric,p_image text,
  p_unit text,p_pack_size numeric,p_weight_kg numeric,p_cost numeric,p_initial_stock numeric,
  p_min_stock numeric,p_available boolean default true
) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop uuid;v_id uuid;v_category text;
begin
  v_shop:=public.qg_market_owner_shop();
  select public_category into v_category from public.shop_profiles where id=v_shop;
  if v_shop is null or v_category not in ('market','meat','fish','vegetable','fruit','grocery') then raise exception 'market seller access denied'; end if;
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
end $$;
revoke all on function public.market_save_product(uuid,text,text,numeric,text,text,numeric,numeric,numeric,numeric,numeric,boolean) from public,anon;
grant execute on function public.market_save_product(uuid,text,text,numeric,text,text,numeric,numeric,numeric,numeric,numeric,boolean) to authenticated;


create or replace function public.qg_market_catalog_page(
  p_query text default '',
  p_category text default '',
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security invoker
set search_path=public,pg_temp
as $$
declare
  v_shop uuid;
  v_shop_category text;
  result jsonb;
begin
  v_shop:=public.qg_market_owner_shop();
  select public_category into v_shop_category
  from public.shop_profiles
  where id=v_shop and archived_at is null;

  if v_shop is null
     or v_shop_category not in ('market','meat','fish','vegetable','fruit')
  then
    raise exception 'market seller access denied' using errcode='42501';
  end if;

  if p_offset<0
     or length(coalesce(p_query,''))>100
     or length(coalesce(p_category,''))>80
  then
    raise exception 'invalid query';
  end if;

  select coalesce(
    jsonb_agg(to_jsonb(x) order by x.has_approved_image desc,x.name_th,x.id),
    '[]'::jsonb
  )
  into result
  from (
    select
      m.id,
      m.slug,
      m.name_th,
      m.category,
      m.suggested_unit,
      m.suggested_pack_size,
      m.source_unit,
      case when m.image_status='approved' then m.image else null end as image,
      case when m.image_status='approved' then m.image_source else null end as image_source,
      case when m.image_status='approved' then m.image_rights_evidence else null end as image_rights_evidence,
      m.image_status,
      (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null) as has_approved_image,
      (nullif(btrim(coalesce(m.image_source,'')),'') is not null) as has_image_reference,
      mp.product_id,
      p.updated_at as product_updated_at,
      p.price,
      p.delivery_price,
      p.available,
      mp.unit,
      mp.pack_size,
      mp.item_weight_kg,
      mp.stock_quantity,
      mp.min_stock,
      mp.cost_price
    from public.market_master_products m
    left join public.market_products mp
      on mp.master_product_id=m.id
     and mp.shop_id=v_shop
    left join public.products p
      on p.id=mp.product_id
     and p.shop_id=v_shop
    where m.active
      and (coalesce(p_category,'')='' or m.category=p_category)
      and (
        coalesce(p_query,'')=''
        or m.name_th ilike '%'||p_query||'%'
        or m.slug ilike '%'||lower(p_query)||'%'
      )
    order by
      (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null) desc,
      m.name_th,
      m.id
    limit 51
    offset p_offset
  ) x;

  return result;
end
$$;


revoke all on function public.qg_market_catalog_page(text,text,integer) from public,anon;
grant execute on function public.qg_market_catalog_page(text,text,integer) to authenticated;

create or replace function public.qg_market_catalog_add(
  p_master_product_id uuid,
  p_price numeric,
  p_unit text,
  p_pack_size numeric,
  p_weight_kg numeric,
  p_cost numeric,
  p_initial_stock numeric,
  p_min_stock numeric,
  p_available boolean,
  p_request_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path=public,market_private,pg_temp
as $$
declare
  v_shop uuid;
  v_shop_category text;
  v_master public.market_master_products%rowtype;
  v_product uuid;
  v_body jsonb;
  v_receipt market_private.catalog_add_receipts%rowtype;
  v_result jsonb;
begin
  if auth.uid() is null or p_request_id is null then
    raise exception 'authentication and request ID required' using errcode='42501';
  end if;

  v_shop:=public.qg_market_owner_shop();
  select public_category into v_shop_category
  from public.shop_profiles
  where id=v_shop and archived_at is null;

  if v_shop is null
     or v_shop_category not in ('market','meat','fish','vegetable','fruit')
  then
    raise exception 'market seller access denied' using errcode='42501';
  end if;

  select * into strict v_master
  from public.market_master_products
  where id=p_master_product_id and active;

  v_body:=jsonb_build_object(
    'master_product_id',p_master_product_id,
    'price',p_price,
    'unit',p_unit,
    'pack_size',p_pack_size,
    'weight_kg',p_weight_kg,
    'cost',p_cost,
    'initial_stock',p_initial_stock,
    'min_stock',p_min_stock,
    'available',coalesce(p_available,true)
  );

  perform pg_advisory_xact_lock(hashtext(v_shop::text||':market-master:'||p_master_product_id::text));
  perform pg_advisory_xact_lock(hashtext(v_shop::text||':market-request:'||p_request_id::text));

  select * into v_receipt
  from market_private.catalog_add_receipts
  where shop_id=v_shop and request_id=p_request_id;

  if found then
    if v_receipt.request_body is distinct from v_body then
      raise exception 'request ID body mismatch';
    end if;
    return v_receipt.result;
  end if;

  select product_id into v_product
  from public.market_products
  where shop_id=v_shop and master_product_id=p_master_product_id
  for update;

  if found then
    v_result:=jsonb_build_object('product_id',v_product,'created',false);
    insert into market_private.catalog_add_receipts(shop_id,request_id,request_body,result)
    values(v_shop,p_request_id,v_body,v_result);
    return v_result;
  end if;

  v_product:=public.market_save_product(
    null,
    v_master.name_th,
    v_master.category,
    p_price,
    case when v_master.image_status='approved' then v_master.image else null end,
    p_unit,
    p_pack_size,
    p_weight_kg,
    p_cost,
    p_initial_stock,
    p_min_stock,
    coalesce(p_available,true)
  );

  update public.market_products
  set master_product_id=p_master_product_id,
      updated_at=now()
  where product_id=v_product and shop_id=v_shop;

  if not found then
    raise exception 'market product link failed';
  end if;

  v_result:=jsonb_build_object('product_id',v_product,'created',true);
  insert into market_private.catalog_add_receipts(shop_id,request_id,request_body,result)
  values(v_shop,p_request_id,v_body,v_result);
  return v_result;
end
$$;
revoke all on function public.qg_market_catalog_add(uuid,numeric,text,numeric,numeric,numeric,numeric,numeric,boolean,uuid) from public,anon;
grant execute on function public.qg_market_catalog_add(uuid,numeric,text,numeric,numeric,numeric,numeric,numeric,boolean,uuid) to authenticated;


create policy market_preparation_owner_read on public.market_products
for select to authenticated using(shop_id=public.qg_market_owner_shop());
create policy market_preparation_movement_read on public.market_stock_movements
for select to authenticated using(shop_id=public.qg_market_owner_shop());
