-- Searchable Makro source catalog for Grocery Merchant Web.
-- User-locked scope: branded, shelf-stable grocery-store products only.
-- Primary food root is dry-grocery; personal-care is used only to reach the requested 10k.
-- No fresh market, frozen/chilled food, electrical appliances, alcohol or tobacco.
-- Remote Makro images are Merchant catalog previews only; products.image remains null
-- until a store supplies/uses a separately approved production image path.

create extension if not exists pg_trgm with schema extensions;

create table if not exists public.grocery_source_products (
  source text not null,
  source_product_id text not null,
  source_rank integer not null check (source_rank >= 0),
  source_category text not null,
  sku text,
  barcode text,
  brand text not null,
  title_th text not null,
  title_en text,
  deepest_category text not null,
  image_url text,
  unit_size text,
  unit_type text,
  unit_factor numeric,
  source_url text not null,
  retrieved_at timestamptz not null,
  active boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (source,source_product_id),
  check (source='makro-pro'),
  check (source_category in ('dry-grocery','health-beauty/personal-care')),
  check (deepest_category=source_category or deepest_category like source_category||'/%'),
  check (length(btrim(brand)) between 1 and 250),
  check (length(btrim(title_th)) between 1 and 500),
  check (
    source_url=case source_category
      when 'dry-grocery' then 'https://www.makro.pro/th/c/dry-grocery'
      when 'health-beauty/personal-care' then 'https://www.makro.pro/th/c/health-beauty/personal-care'
    end
  ),
  check (image_url is null or image_url like 'https://images.mango-prod.siammakro.cloud/%')
);

create index if not exists grocery_source_active_rank
  on public.grocery_source_products(active,source_rank,source_product_id);

create index if not exists grocery_source_brand_trgm
  on public.grocery_source_products using gin (brand extensions.gin_trgm_ops)
  where active;

create index if not exists grocery_source_title_trgm
  on public.grocery_source_products using gin (title_th extensions.gin_trgm_ops)
  where active;

create index if not exists grocery_source_search
  on public.grocery_source_products using gin (
    to_tsvector('simple',
      coalesce(brand,'')||' '||
      coalesce(title_th,'')||' '||
      coalesce(title_en,'')||' '||
      coalesce(barcode,'')
    )
  )
  where active;

alter table public.grocery_source_products enable row level security;
revoke all on public.grocery_source_products from public,anon,authenticated;

-- A shop can add the same Makro source product only once.
create unique index if not exists products_shop_makro_source_unique
  on public.products(shop_id,(metadata->>'makro_source_product_id'))
  where coalesce(metadata->>'makro_source_product_id','')<>'';

create or replace function public.qg_grocery_source_category_label(p_deepest_category text)
returns text
language sql
immutable
set search_path=public,pg_temp
as $$
  select case
    when coalesce(p_deepest_category,'') like 'health-beauty/personal-care/%hair%' then 'ยาสระผม'
    when coalesce(p_deepest_category,'') like 'health-beauty/personal-care/%women%' then 'ของใช้ส่วนตัว'
    when coalesce(p_deepest_category,'') like 'health-beauty/personal-care/%' then 'ของใช้ส่วนตัว'
    when coalesce(p_deepest_category,'') like '%instant-noodles%' then 'บะหมี่กึ่งสำเร็จรูป'
    when coalesce(p_deepest_category,'') like '%rice%' then 'ข้าวสาร'
    when coalesce(p_deepest_category,'') ~ '(canned|can-food|fish-can)' then 'อาหารกระป๋อง'
    when coalesce(p_deepest_category,'') ~ '(bread|bakery)' then 'ขนมอบ'
    when coalesce(p_deepest_category,'') ~ '(seasoning|sauce|condiment|spice|sugar|salt|oil)' then 'เครื่องปรุง'
    else 'ของแห้ง'
  end
$$;

create or replace function public.qg_grocery_source_search(
  p_shop_id uuid,
  p_query text default '',
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  q text:=btrim(coalesce(p_query,''));
  result jsonb;
begin
  if auth.uid() is null
     or coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1 from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
         and archived_at is null
     )
  then
    raise exception 'own grocery shop required' using errcode='42501';
  end if;

  if length(q)>100 or p_offset<0 or p_offset>20000 then
    raise exception 'invalid source search';
  end if;

  select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb)
  into result
  from (
    select
      s.source,
      s.source_product_id,
      s.source_rank,
      s.source_category,
      s.sku,
      s.barcode,
      s.brand,
      s.title_th,
      s.deepest_category,
      s.image_url,
      s.unit_size,
      s.unit_type,
      s.unit_factor,
      public.qg_grocery_source_category_label(s.deepest_category) as queuego_category,
      exists(
        select 1 from public.products p
        where p.shop_id=p_shop_id
          and p.metadata->>'makro_source_product_id'=s.source_product_id
      ) as in_shop
    from public.grocery_source_products s
    where s.active
      and (
        q=''
        or s.barcode=q
        or s.brand ilike '%'||q||'%'
        or s.title_th ilike '%'||q||'%'
        or coalesce(s.title_en,'') ilike '%'||q||'%'
        or to_tsvector('simple',
             coalesce(s.brand,'')||' '||
             coalesce(s.title_th,'')||' '||
             coalesce(s.title_en,'')||' '||
             coalesce(s.barcode,'')
           ) @@ plainto_tsquery('simple',q)
      )
    order by
      (s.barcode=q and q<>'') desc,
      (s.brand ilike q||'%' and q<>'') desc,
      (s.title_th ilike q||'%' and q<>'') desc,
      s.source_rank,
      s.brand,
      s.title_th,
      s.source_product_id
    limit 51 offset p_offset
  ) x;

  return result;
end;
$;

create or replace function public.qg_grocery_source_add_to_shop(
  p_shop_id uuid,
  p_source_product_id text,
  p_store_price numeric,
  p_stock integer default 0,
  p_pos_available boolean default true,
  p_delivery_available boolean default true
)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  s public.grocery_source_products;
  existing_id uuid;
  v_id uuid;
  v_name text;
  v_category text;
  v_gp numeric;
  v_delivery_price numeric;
  v_metadata jsonb;
begin
  if auth.uid() is null
     or coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1 from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
         and archived_at is null
     )
  then
    raise exception 'own grocery shop required' using errcode='42501';
  end if;

  if p_store_price is null or p_store_price<=0 or p_store_price>999999
     or p_stock is null or p_stock<0
     or coalesce(p_source_product_id,'')=''
     or (coalesce(p_pos_available,false)=false and coalesce(p_delivery_available,false)=false)
  then
    raise exception 'invalid product values';
  end if;

  perform pg_advisory_xact_lock(hashtext(p_shop_id::text||':makro:'||p_source_product_id));

  select * into strict s
  from public.grocery_source_products
  where source='makro-pro'
    and source_product_id=p_source_product_id
    and active
  for share;

  v_name:=case
    when public.qg_grocery_fold(s.title_th) like public.qg_grocery_fold(s.brand)||'%'
      then s.title_th
    else s.brand||' '||s.title_th
  end;
  v_category:=public.qg_grocery_source_category_label(s.deepest_category);
  v_gp:=public.effective_gp_rate(p_shop_id);
  v_delivery_price:=round(p_store_price*(1+v_gp/100),2);
  v_metadata:=jsonb_build_object(
    'category',v_category,
    'stock',p_stock,
    'catalog_source','makro-pro',
    'makro_source_product_id',s.source_product_id,
    'makro_sku',s.sku,
    'barcode',s.barcode,
    'catalog_preview_image',s.image_url,
    'catalog_preview_only',true,
    'catalog_source_category',s.source_category,
    'catalog_deepest_category',s.deepest_category,
    'catalog_retrieved_at',s.retrieved_at
  );

  select id into existing_id
  from public.products
  where shop_id=p_shop_id
    and metadata->>'makro_source_product_id'=s.source_product_id
  for update;

  if existing_id is null then
    insert into public.products(
      shop_id,name,description,price,image,category,stock,variants,available,
      pos_available,delivery_available,delivery_restriction,pos_price,delivery_price,metadata
    )
    values(
      p_shop_id,v_name,'',p_store_price,null,v_category,p_stock,'[]'::jsonb,true,
      p_pos_available,p_delivery_available,'none',p_store_price,v_delivery_price,v_metadata
    )
    returning id into v_id;
  else
    update public.products
    set
      name=v_name,
      price=p_store_price,
      category=v_category,
      stock=p_stock,
      available=true,
      pos_available=p_pos_available,
      delivery_available=p_delivery_available,
      delivery_restriction='none',
      pos_price=p_store_price,
      delivery_price=v_delivery_price,
      metadata=coalesce(metadata,'{}'::jsonb)||v_metadata,
      updated_at=now()
    where id=existing_id and shop_id=p_shop_id
    returning id into v_id;
  end if;

  return jsonb_build_object(
    'product_id',v_id,
    'source_product_id',s.source_product_id,
    'name',v_name,
    'category',v_category,
    'store_price',p_store_price,
    'delivery_price',v_delivery_price,
    'gp_rate',v_gp,
    'catalog_preview_image',s.image_url
  );
end;
$;

-- Admin-only batch import from the generated, reviewed public Makro source snapshot.
-- Existing active rows stay active during an incomplete upload; new rows remain inactive
-- until qg_admin_grocery_source_finalize verifies the whole 10k snapshot.
create or replace function public.qg_admin_grocery_source_import(p_rows jsonb)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  r jsonb;
  source_id text;
  brand_value text;
  title_value text;
  deepest text;
  source_category_value text;
  source_url_value text;
  image_value text;
  retrieved timestamptz;
  rank_value integer;
  n integer:=0;
begin
  if public.is_active_admin() is distinct from true then
    raise exception 'active admin required' using errcode='42501';
  end if;
  if p_rows is null
     or jsonb_typeof(p_rows)<>'array'
     or jsonb_array_length(p_rows) not between 1 and 200
  then
    raise exception 'batch must contain 1..200 rows';
  end if;

  perform pg_advisory_xact_lock(hashtext('queuego.grocery.source.makro.import'));

  for r in select value from jsonb_array_elements(p_rows)
  loop
    source_id:=btrim(coalesce(r->>'source_product_id',''));
    brand_value:=btrim(coalesce(r->>'brand',''));
    title_value:=btrim(coalesce(r->>'title_th',''));
    deepest:=lower(btrim(coalesce(r->>'deepest_category','')));
    source_category_value:=lower(btrim(coalesce(r->>'source_category','')));
    source_url_value:=btrim(coalesce(r->>'source_url',''));
    image_value:=nullif(btrim(coalesce(r->>'image_url','')),'');
    retrieved:=nullif(r->>'retrieved_at','')::timestamptz;
    rank_value:=coalesce((r->>'source_rank')::integer,-1);

    if source_id=''
       or brand_value=''
       or brand_value ~* '^(?:-|n/?a|na|none|no\s*brand|unbranded|ไม่มีแบรนด์|ไม่มียี่ห้อ|ไม่ระบุ)$'
       or title_value=''
       or source_category_value not in ('dry-grocery','health-beauty/personal-care')
       or not (deepest=source_category_value or deepest like source_category_value||'/%')
       or source_url_value<>case source_category_value
            when 'dry-grocery' then 'https://www.makro.pro/th/c/dry-grocery'
            when 'health-beauty/personal-care' then 'https://www.makro.pro/th/c/health-beauty/personal-care'
          end
       or deepest ~ '(^|/)(eggs?|frozen|tofu|fresh([^/]*|$)|chilled|fresh-market|market-fresh|electronics?|electrical|appliances?|alcohol|beer|wine|liquor|spirits?|tobacco|cigarettes?)(/|$)'
       or retrieved is null
       or rank_value<0
    then
      raise exception 'invalid Makro dry source row';
    end if;
    if image_value is not null
       and image_value not like 'https://images.mango-prod.siammakro.cloud/%'
    then
      raise exception 'unexpected Makro image host';
    end if;

    insert into public.grocery_source_products(
      source,source_product_id,source_rank,source_category,sku,barcode,brand,title_th,title_en,
      deepest_category,image_url,unit_size,unit_type,unit_factor,source_url,
      retrieved_at,active,updated_at
    )
    values(
      'makro-pro',source_id,rank_value,source_category_value,
      nullif(r->>'sku',''),nullif(r->>'barcode',''),brand_value,title_value,
      nullif(r->>'title_en',''),deepest,image_value,
      nullif(r->>'unit_size',''),nullif(r->>'unit_type',''),
      nullif(r->>'unit_factor','')::numeric,
      source_url_value,
      retrieved,false,now()
    )
    on conflict(source,source_product_id) do update set
      source_rank=excluded.source_rank,
      source_category=excluded.source_category,
      sku=excluded.sku,
      barcode=excluded.barcode,
      brand=excluded.brand,
      title_th=excluded.title_th,
      title_en=excluded.title_en,
      deepest_category=excluded.deepest_category,
      image_url=excluded.image_url,
      unit_size=excluded.unit_size,
      unit_type=excluded.unit_type,
      unit_factor=excluded.unit_factor,
      source_url=excluded.source_url,
      retrieved_at=excluded.retrieved_at,
      updated_at=now();

    n:=n+1;
  end loop;

  return jsonb_build_object('processed',n);
end;
$;

create or replace function public.qg_admin_grocery_source_finalize(
  p_retrieved_at timestamptz,
  p_target integer default 10000
)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  n_snapshot integer;
  n_active integer;
begin
  if public.is_active_admin() is distinct from true then
    raise exception 'active admin required' using errcode='42501';
  end if;
  if p_retrieved_at is null or p_target<>10000 then
    raise exception 'exact 10000-row snapshot required';
  end if;

  perform pg_advisory_xact_lock(hashtext('queuego.grocery.source.makro.import'));

  select count(*) into n_snapshot
  from public.grocery_source_products
  where source='makro-pro'
    and retrieved_at=p_retrieved_at;

  if n_snapshot<>p_target then
    raise exception 'Makro source snapshot incomplete: %/%',n_snapshot,p_target;
  end if;

  update public.grocery_source_products
  set active=(source='makro-pro' and retrieved_at=p_retrieved_at),
      updated_at=now()
  where source='makro-pro';

  select count(*) into n_active
  from public.grocery_source_products
  where source='makro-pro' and active;

  if n_active<>p_target then
    raise exception 'Makro source activation mismatch: %/%',n_active,p_target;
  end if;

  return jsonb_build_object(
    'retrieved_at',p_retrieved_at,
    'target',p_target,
    'active',n_active
  );
end;
$;

revoke all on function public.qg_grocery_source_category_label(text) from public,anon;
grant execute on function public.qg_grocery_source_category_label(text) to authenticated;

revoke all on function public.qg_grocery_source_search(uuid,text,integer) from public,anon;
grant execute on function public.qg_grocery_source_search(uuid,text,integer) to authenticated;

revoke all on function public.qg_grocery_source_add_to_shop(uuid,text,numeric,integer,boolean,boolean) from public,anon;
grant execute on function public.qg_grocery_source_add_to_shop(uuid,text,numeric,integer,boolean,boolean) to authenticated;

revoke all on function public.qg_admin_grocery_source_import(jsonb) from public,anon;
grant execute on function public.qg_admin_grocery_source_import(jsonb) to authenticated;

revoke all on function public.qg_admin_grocery_source_finalize(timestamptz,integer) from public,anon;
grant execute on function public.qg_admin_grocery_source_finalize(timestamptz,integer) to authenticated;
