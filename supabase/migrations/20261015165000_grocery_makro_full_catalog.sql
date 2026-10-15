-- Full branded dry-grocery catalog from Makro PRO.
-- Additive to the existing QueueGo Grocery Master Catalog. Makro imagery is Merchant preview only;
-- Customer/POS product image remains gated by image_status='approved'.

alter table public.grocery_master_products
  add column if not exists catalog_display_name text,
  add column if not exists catalog_source text,
  add column if not exists source_category_path text;

create index if not exists grocery_master_catalog_display_search
on public.grocery_master_products
using gin(to_tsvector('simple',
  coalesce(catalog_display_name,'')||' '||
  brand||' '||
  product_name_th||' '||
  coalesce(product_name_en,'')||' '||
  variant||' '||
  coalesce(barcode,'')
));

create or replace function public.qg_grocery_makro_category(p_path text)
returns text
language sql
immutable
set search_path=public,pg_temp
as $$
  select case
    when coalesce(p_path,'') like 'dry-grocery/grains-rice-cereal%' then 'ข้าวสาร'
    when coalesce(p_path,'') like 'dry-grocery/instant-meals%' then 'บะหมี่กึ่งสำเร็จรูป'
    when coalesce(p_path,'') like 'dry-grocery/pasta-noodles%' then 'บะหมี่กึ่งสำเร็จรูป'
    when coalesce(p_path,'') like 'dry-grocery/cooking-oil-vinegar%' then 'น้ำมันพืช'
    when coalesce(p_path,'') like 'dry-grocery/sauce-seasoning%' then 'ซอส'
    when coalesce(p_path,'') like 'dry-grocery/seasoning-and-spices%' then 'เครื่องปรุง'
    when coalesce(p_path,'') like 'dry-grocery/sweet-condiment%' then 'เครื่องปรุง'
    when coalesce(p_path,'') like 'dry-grocery/canned-packaged-food%' then 'อาหารกระป๋อง'
    when coalesce(p_path,'') like 'dry-grocery/ready-to-eat%' then 'ขนมปัง'
    else 'ของแห้ง'
  end;
$$;

revoke all on function public.qg_grocery_makro_category(text) from public,anon;
grant execute on function public.qg_grocery_makro_category(text) to authenticated;

create or replace function public.qg_grocery_import_makro(p_rows jsonb)
returns jsonb
language plpgsql
security invoker
set search_path=public,pg_temp
as $$
declare
  r jsonb;
  m uuid;
  by_source uuid;
  by_barcode uuid;
  by_key uuid;
  b text;
  key text;
  title_th text;
  brand_th text;
  source_id text;
  deepest text;
  image_url text;
  v_n integer:=0;
  v_created integer:=0;
  v_linked integer:=0;
begin
  if public.is_active_admin() is distinct from true then
    raise exception 'active admin required' using errcode='42501';
  end if;
  if p_rows is null
     or jsonb_typeof(p_rows) is distinct from 'array'
     or jsonb_array_length(p_rows) not between 1 and 200
  then
    raise exception 'batch must contain 1..200 rows';
  end if;

  perform pg_advisory_xact_lock(hashtext('queuego.grocery.catalog.makro'));

  for r in select value from jsonb_array_elements(p_rows)
  loop
    source_id:=btrim(coalesce(r->>'source_product_id',''));
    brand_th:=btrim(coalesce(r->>'brand',''));
    title_th:=btrim(coalesce(r->>'title_th',''));
    deepest:=btrim(coalesce(r->>'deepest_category','dry-grocery'));
    image_url:=nullif(btrim(coalesce(r->>'image_url','')),'');

    if source_id=''
       or brand_th=''
       or title_th=''
       or deepest not like 'dry-grocery%'
       or lower(brand_th) in ('no brand','unbranded','none','n/a','na')
       or brand_th in ('-','ไม่มีแบรนด์','ไม่มียี่ห้อ','ไม่ระบุ')
    then
      raise exception 'invalid Makro branded dry row';
    end if;
    if deepest ~* '(^|/)(eggs?|frozen|tofu|dairy-products|fresh[^/]*|chilled|produce|meat|seafood|vegetable|fruit)(/|$)' then
      raise exception 'fresh/non-dry Makro row rejected: %',deepest;
    end if;
    if image_url is not null
       and image_url not like 'https://images.mango-prod.siammakro.cloud/%'
    then
      raise exception 'unexpected Makro image host';
    end if;

    b:=null;
    if nullif(btrim(coalesce(r->>'barcode','')),'') is not null then
      begin
        b:=public.qg_grocery_gtin(r->>'barcode');
      exception when others then
        b:=null;
      end;
    end if;

    by_source:=null;by_barcode:=null;by_key:=null;m:=null;
    select min(s.master_product_id)
      into by_source
    from public.grocery_master_sources s
    where s.source_type='retailer'
      and s.source_url='https://www.makro.pro/th/c/dry-grocery'
      and s.source_product_id=source_id;

    if b is not null then
      select id into by_barcode
      from public.grocery_master_products
      where barcode=b
        and merged_into is null
      limit 1;
    end if;

    key:=public.qg_grocery_identity_key(brand_th,title_th,'',1,'piece',1,'ชิ้น');
    select id into by_key
    from public.grocery_master_products
    where normalized_key=key
      and merged_into is null
    limit 1;

    if by_source is not null then
      m:=by_source;
    elsif by_barcode is not null then
      m:=by_barcode;
    elsif by_key is not null then
      m:=by_key;
    end if;

    if m is null then
      insert into public.grocery_master_products(
        barcode,brand,product_name_th,product_name_en,variant,
        size_value,size_unit,pack_size,sales_unit,category,subcategory,
        normalized_key,image_status,image_source,active,
        catalog_display_name,catalog_source,source_category_path
      )
      values(
        b,brand_th,title_th,nullif(r->>'title_en',''),'',
        1,'piece',1,'ชิ้น',public.qg_grocery_makro_category(deepest),
        'Makro · '||deepest,
        key,'pending',image_url,true,
        title_th,'makro-pro',deepest
      )
      returning id into m;
      v_created:=v_created+1;
    else
      update public.grocery_master_products
      set
        active=true,
        image_source=coalesce(nullif(image_source,''),image_url),
        catalog_display_name=coalesce(nullif(catalog_display_name,''),title_th),
        catalog_source=coalesce(nullif(catalog_source,''),'makro-pro'),
        source_category_path=coalesce(nullif(source_category_path,''),deepest)
      where id=m
        and merged_into is null;
      v_linked:=v_linked+1;
    end if;

    insert into public.grocery_master_sources(
      master_product_id,source_url,source_type,source_product_id,retrieved_at
    )
    values(
      m,
      'https://www.makro.pro/th/c/dry-grocery',
      'retailer',
      source_id,
      coalesce(nullif(r->>'retrieved_at','')::timestamptz,now())
    )
    on conflict(master_product_id,source_url,source_product_id)
    do update set retrieved_at=greatest(grocery_master_sources.retrieved_at,excluded.retrieved_at);

    v_n:=v_n+1;
  end loop;

  return jsonb_build_object('processed',v_n,'created',v_created,'linked',v_linked);
end
$$;

revoke all on function public.qg_grocery_import_makro(jsonb) from public,anon;
grant execute on function public.qg_grocery_import_makro(jsonb) to authenticated;

create or replace function public.qg_grocery_product_guard()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  m public.grocery_master_products;
begin
  if tg_op='UPDATE'
     and old.grocery_master_id is not null
     and new.grocery_master_id is distinct from old.grocery_master_id
     and not public.is_active_admin()
  then
    raise exception 'master identity is locked';
  end if;
  if new.grocery_master_id is null then return new; end if;
  if not exists(
    select 1 from public.shop_profiles
    where id=new.shop_id and public_category='grocery' and archived_at is null
  ) then raise exception 'grocery shop required'; end if;

  select * into strict m from public.grocery_master_products where id=new.grocery_master_id;
  if m.merged_into is not null then raise exception 'master is merged'; end if;
  if new.stock<0 then raise exception 'negative stock'; end if;
  new.grocery_promo_price:=null;

  new.name:=coalesce(
    nullif(btrim(m.catalog_display_name),''),
    case when left(public.qg_grocery_fold(m.product_name_th),length(public.qg_grocery_fold(m.brand)))=public.qg_grocery_fold(m.brand)
      then '' else m.brand||' ' end
    ||m.product_name_th
    ||case when m.variant='' then '' else ' '||m.variant end
    ||' '||m.size_value::text||' '||m.size_unit
    ||case when m.pack_size=1 then '' else ' x '||m.pack_size end
  );
  new.category:=m.category;
  new.image:=case when m.image_status='approved' then m.image else null end;
  new.price:=coalesce(new.grocery_selling_price,0);
  new.pos_price:=new.price;
  new.available:=m.active and new.grocery_enabled and new.grocery_selling_price>0
    and (not new.grocery_stock_tracking or new.stock>0);
  new.available:=coalesce(new.available,false);
  new.updated_at:=now();
  return new;
end
$$;

revoke all on function public.qg_grocery_product_guard() from public,anon,authenticated;

create or replace function public.qg_grocery_merchant_page(
  p_shop_id uuid,
  p_query text default '',
  p_category text default '',
  p_mode text default 'all',
  p_offset integer default 0
) returns jsonb
language plpgsql
stable
security invoker
set search_path=public,pg_temp
as $$
declare result jsonb;
begin
  if coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1 from public.shop_profiles
       where id=p_shop_id and user_id=public.get_my_user_id() and public_category='grocery'
     )
  then raise exception 'own grocery shop required'; end if;

  if p_offset<0 or length(p_query)>100 or p_mode not in ('all','on','off','unpriced')
  then raise exception 'invalid query'; end if;

  select coalesce(jsonb_agg(to_jsonb(x) order by
    (x.subcategory='สินค้าหลักร้านชำ') desc,
    (x.image_status='approved' and nullif(btrim(x.image),'') is not null) desc,
    x.display_name,x.id),'[]')
  into result
  from (
    select
      m.id,m.brand,m.product_name_th,
      coalesce(nullif(btrim(m.catalog_display_name),''),m.product_name_th) display_name,
      m.catalog_source,m.source_category_path,m.subcategory,
      m.variant,m.size_value,m.size_unit,m.pack_size,m.sales_unit,m.category,m.barcode,
      coalesce(nullif(btrim(m.image),''),nullif(btrim(m.image_source),'')) image,
      m.image_status,
      p.id product_id,p.updated_at product_updated_at,
      coalesce(p.grocery_enabled,false) enabled,
      p.grocery_selling_price selling_price,
      coalesce(p.stock,0) stock_qty,
      coalesce(p.grocery_stock_tracking,false) stock_tracking,
      coalesce(p.pos_available,true) pos_available,
      coalesce(p.delivery_available,true) delivery_available,
      p.delivery_price
    from public.grocery_master_products m
    left join public.products p on p.grocery_master_id=m.id and p.shop_id=p_shop_id
    where m.active and m.merged_into is null
      and (p_category='' or m.category=p_category)
      and (
        p_query=''
        or to_tsvector('simple',
          coalesce(m.catalog_display_name,'')||' '||m.brand||' '||m.product_name_th||' '||
          coalesce(m.product_name_en,'')||' '||m.variant||' '||coalesce(m.barcode,'')
        ) @@ plainto_tsquery('simple',p_query)
        or coalesce(m.catalog_display_name,m.product_name_th) ilike '%'||p_query||'%'
        or m.product_name_th ilike '%'||p_query||'%'
        or m.brand ilike '%'||p_query||'%'
        or m.barcode=p_query
      )
      and (
        p_mode='all'
        or p_mode='on' and p.grocery_enabled
        or p_mode='off' and not coalesce(p.grocery_enabled,false)
        or p_mode='unpriced' and p.grocery_selling_price is null
      )
    order by
      (m.subcategory='สินค้าหลักร้านชำ') desc,
      (m.image_status='approved' and nullif(btrim(m.image),'') is not null) desc,
      coalesce(nullif(btrim(m.catalog_display_name),''),m.product_name_th),
      m.id
    limit 51 offset p_offset
  ) x;
  return result;
end
$$;

revoke all on function public.qg_grocery_merchant_page(uuid,text,text,text,integer) from public,anon;
grant execute on function public.qg_grocery_merchant_page(uuid,text,text,text,integer) to authenticated;
