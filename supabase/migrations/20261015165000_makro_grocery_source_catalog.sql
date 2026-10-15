-- Searchable Makro source catalog for Grocery Merchant Web.
-- Source rows remain separate from canonical grocery_master_products.
-- Remote Makro image URLs are Merchant catalog previews, not customer image-rights approval.

create extension if not exists http with schema extensions;
create extension if not exists pg_trgm with schema extensions;

create table if not exists public.grocery_source_products (
  source text not null,
  source_product_id text not null,
  source_category text not null,
  source_rank integer not null default 0 check (source_rank >= 0),
  sku text,
  barcode text,
  brand text not null,
  brand_en text,
  title_th text not null,
  title_en text,
  categories text[] not null default '{}'::text[],
  category_ids text[] not null default '{}'::text[],
  deepest_category text,
  image_url text,
  image_urls text[] not null default '{}'::text[],
  unit_size text,
  unit_type text,
  unit_factor numeric,
  packaging_weight numeric,
  source_url text not null,
  raw_facts jsonb not null default '{}'::jsonb,
  retrieved_at timestamptz not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (source, source_product_id),
  check (source = 'makro-pro'),
  check (source_category in ('dry-grocery','beverages')),
  check (length(btrim(brand)) between 1 and 250),
  check (length(btrim(title_th)) between 1 and 500),
  check (source_url like 'https://www.makro.pro/%'),
  check (image_url is null or image_url like 'https://images.mango-prod.siammakro.cloud/%')
);

create index if not exists grocery_source_active_rank
  on public.grocery_source_products(active,source_category,source_rank,source_product_id);

create index if not exists grocery_source_brand_trgm
  on public.grocery_source_products using gin (brand extensions.gin_trgm_ops)
  where active;

create index if not exists grocery_source_title_trgm
  on public.grocery_source_products using gin (title_th extensions.gin_trgm_ops)
  where active;

create index if not exists grocery_source_search
  on public.grocery_source_products using gin (
    to_tsvector('simple',coalesce(brand,'')||' '||coalesce(title_th,'')||' '||coalesce(title_en,'')||' '||coalesce(barcode,''))
  )
  where active;

alter table public.grocery_source_products enable row level security;
revoke all on public.grocery_source_products from public,anon,authenticated;

-- A shop can add the same Makro source product only once.
create unique index if not exists products_shop_makro_source_unique
  on public.products(shop_id,(metadata->>'makro_source_product_id'))
  where coalesce(metadata->>'makro_source_product_id','')<>'';

create or replace function public.qg_grocery_source_category_label(p_source_category text,p_deepest_category text)
returns text
language sql
immutable
set search_path=public,pg_temp
as $$
  select case
    when p_source_category='beverages' then 'เครื่องดื่ม'
    when coalesce(p_deepest_category,'') like '%instant-noodles%' then 'บะหมี่กึ่งสำเร็จรูป'
    when coalesce(p_deepest_category,'') like '%rice%' then 'ข้าวสาร'
    when coalesce(p_deepest_category,'') ~ '(canned|can-food|fish-can)' then 'อาหารกระป๋อง'
    when coalesce(p_deepest_category,'') ~ '(bread|bakery)' then 'ขนมอบ'
    when coalesce(p_deepest_category,'') ~ '(seasoning|sauce|condiment|spice|sugar|salt|oil)' then 'เครื่องปรุง'
    else 'อื่นๆ'
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
      s.source_category,
      s.source_rank,
      s.sku,
      s.barcode,
      s.brand,
      s.title_th,
      s.deepest_category,
      s.image_url,
      s.unit_size,
      s.unit_type,
      s.unit_factor,
      public.qg_grocery_source_category_label(s.source_category,s.deepest_category) as queuego_category,
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
        or to_tsvector('simple',coalesce(s.brand,'')||' '||coalesce(s.title_th,'')||' '||coalesce(s.title_en,'')||' '||coalesce(s.barcode,''))
           @@ plainto_tsquery('simple',q)
      )
    order by
      (s.barcode=q and q<>'') desc,
      (s.brand ilike q||'%' and q<>'') desc,
      (s.title_th ilike q||'%' and q<>'') desc,
      case s.source_category when 'dry-grocery' then 0 else 1 end,
      s.source_rank,
      s.brand,
      s.title_th,
      s.source_product_id
    limit 51 offset p_offset
  ) x;

  return result;
end
$$;

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
  v_category:=public.qg_grocery_source_category_label(s.source_category,s.deepest_category);
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
end
$$;

-- Internal page importer. It is not executable through anon/authenticated Data API.
create or replace function public.qg_internal_makro_source_sync_page(
  p_category text,
  p_page integer,
  p_size integer default 120
)
returns jsonb
language plpgsql
security definer
set search_path=public,extensions,pg_temp
as $$
declare
  response extensions.http_response;
  body jsonb;
  doc jsonb;
  hit jsonb;
  n integer:=0;
  excluded integer:=0;
  found_count integer:=0;
  ord bigint;
  deepest text;
  brand_value text;
  title_value text;
  image_value text;
  attr_text text;
begin
  if p_category not in ('dry-grocery','beverages')
     or p_page<1
     or p_size<1
     or p_size>200
  then
    raise exception 'invalid Makro sync page';
  end if;

  select * into response
  from extensions.http_post(
    'https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search',
    jsonb_build_object(
      'q','*',
      'size',p_size,
      'filters',jsonb_build_object(
        'category',jsonb_build_array(p_category),
        'isSalesCustomer',false,
        'countryCode','TH',
        'lang','th',
        'allowRestrictedProduct',true,
        'allowAlcohol',false
      ),
      'sortBy','RELEVANCE',
      'autocorrect',false,
      'page',p_page,
      'autocategorise',false,
      'includeCurations',false,
      'source','web'
    )::text,
    'application/json'
  );

  if response.status<>200 then
    raise exception 'Makro search HTTP %: %',response.status,left(response.content,300);
  end if;

  body:=response.content::jsonb;
  found_count:=coalesce((body->>'found')::integer,0);

  for hit,ord in
    select value,ordinality
    from jsonb_array_elements(coalesce(body->'hits','[]'::jsonb)) with ordinality
  loop
    doc:=hit->'document';
    brand_value:=btrim(coalesce(doc->>'brand',''));
    title_value:=btrim(coalesce(doc->>'title',doc->>'searchTitle',''));
    deepest:=lower(coalesce(doc->>'deepestCategory',''));
    attr_text:=lower(coalesce(doc->'productAttributes'->'categoricalAttributes','[]'::jsonb)::text);

    if brand_value=''
       or brand_value ~* '^(?:-|n/?a|na|none|no\s*brand|unbranded|ไม่มีแบรนด์|ไม่มียี่ห้อ|ไม่ระบุ)$'
       or title_value=''
       or deepest !~ ('^'||replace(p_category,'-','\-')||'(/|$)')
       or deepest ~ '(^|/)(eggs?|frozen|tofu|fresh([^/]*|$)|chilled|produce|meat|seafood|vegetable|fruit)(/|$)'
       or lower(coalesce(doc->>'isWeightedFresh','false'))='true'
       or attr_text ~ 'storage_type::(chilled|frozen|refrigerated)'
    then
      excluded:=excluded+1;
      continue;
    end if;

    image_value:=case
      when coalesce(doc#>>'{images,0}','') like 'https://images.mango-prod.siammakro.cloud/%'
      then doc#>>'{images,0}'
      else null
    end;

    insert into public.grocery_source_products(
      source,source_product_id,source_category,source_rank,sku,barcode,brand,brand_en,
      title_th,title_en,categories,category_ids,deepest_category,image_url,image_urls,
      unit_size,unit_type,unit_factor,packaging_weight,source_url,raw_facts,retrieved_at,active,updated_at
    )
    values(
      'makro-pro',
      coalesce(doc->>'productId',doc->>'id'),
      p_category,
      (p_page-1)*p_size+ord::integer,
      nullif(doc->>'sku',''),
      nullif(coalesce(doc->>'itemBarCode',doc#>>'{itemBarCodes,0}'),''),
      brand_value,
      nullif(doc->>'brandEn',''),
      title_value,
      nullif(doc->>'titleEn',''),
      array(select jsonb_array_elements_text(coalesce(doc->'categories','[]'::jsonb))),
      array(select jsonb_array_elements_text(coalesce(doc->'categoryIds','[]'::jsonb))),
      nullif(doc->>'deepestCategory',''),
      image_value,
      array(
        select x
        from jsonb_array_elements_text(coalesce(doc->'images','[]'::jsonb)) x
        where x like 'https://images.mango-prod.siammakro.cloud/%'
        limit 5
      ),
      nullif(doc->>'unitSize',''),
      nullif(doc->>'unitType',''),
      nullif(doc->>'unitFactor','')::numeric,
      nullif(doc->>'packagingWeight','')::numeric,
      'https://www.makro.pro/th/c/'||p_category,
      jsonb_build_object(
        'productAttributes',coalesce(doc->'productAttributes','{}'::jsonb),
        'itemBarCodes',coalesce(doc->'itemBarCodes','[]'::jsonb)
      ),
      now(),
      true,
      now()
    )
    on conflict(source,source_product_id) do update set
      source_category=excluded.source_category,
      source_rank=excluded.source_rank,
      sku=excluded.sku,
      barcode=excluded.barcode,
      brand=excluded.brand,
      brand_en=excluded.brand_en,
      title_th=excluded.title_th,
      title_en=excluded.title_en,
      categories=excluded.categories,
      category_ids=excluded.category_ids,
      deepest_category=excluded.deepest_category,
      image_url=excluded.image_url,
      image_urls=excluded.image_urls,
      unit_size=excluded.unit_size,
      unit_type=excluded.unit_type,
      unit_factor=excluded.unit_factor,
      packaging_weight=excluded.packaging_weight,
      source_url=excluded.source_url,
      raw_facts=excluded.raw_facts,
      retrieved_at=excluded.retrieved_at,
      active=true,
      updated_at=now();
    n:=n+1;
  end loop;

  return jsonb_build_object(
    'category',p_category,
    'page',p_page,
    'size',p_size,
    'source_found',found_count,
    'upserted',n,
    'excluded',excluded
  );
end
$$;

create or replace function public.qg_internal_makro_source_finalize(p_target integer default 10000)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  n_active integer;
begin
  if p_target<1 or p_target>50000 then raise exception 'invalid target';end if;

  with ranked as (
    select source,source_product_id,
           row_number() over(
             order by case source_category when 'dry-grocery' then 0 else 1 end,
                      source_rank,source_product_id
           ) as rn
    from public.grocery_source_products
    where source='makro-pro'
  )
  update public.grocery_source_products s
  set active=(r.rn<=p_target),updated_at=now()
  from ranked r
  where s.source=r.source and s.source_product_id=r.source_product_id;

  select count(*) into n_active
  from public.grocery_source_products
  where source='makro-pro' and active;

  return jsonb_build_object('target',p_target,'active',n_active);
end
$$;

revoke all on function public.qg_grocery_source_category_label(text,text) from public,anon;
grant execute on function public.qg_grocery_source_category_label(text,text) to authenticated;

revoke all on function public.qg_grocery_source_search(uuid,text,integer) from public,anon;
grant execute on function public.qg_grocery_source_search(uuid,text,integer) to authenticated;

revoke all on function public.qg_grocery_source_add_to_shop(uuid,text,numeric,integer,boolean,boolean) from public,anon;
grant execute on function public.qg_grocery_source_add_to_shop(uuid,text,numeric,integer,boolean,boolean) to authenticated;

revoke all on function public.qg_internal_makro_source_sync_page(text,integer,integer) from public,anon,authenticated,service_role;
revoke all on function public.qg_internal_makro_source_finalize(integer) from public,anon,authenticated,service_role;
