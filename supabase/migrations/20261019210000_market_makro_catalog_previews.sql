-- Merchant-only Makro packshot/reference previews for the Market Master Catalog.
-- Mirrors Grocery catalog behavior: official Makro images help merchants identify products,
-- while products.image continues to use separately approved/store-owned art for customer storefronts.

alter table public.market_master_products
  add column if not exists catalog_preview_image text,
  add column if not exists catalog_preview_source_url text,
  add column if not exists catalog_preview_source_name text;

alter table public.market_master_products
  drop constraint if exists market_master_products_preview_host_check;

alter table public.market_master_products
  add constraint market_master_products_preview_host_check
  check (
    catalog_preview_image is null
    or catalog_preview_image like 'https://images.mango-prod.siammakro.cloud/%'
  );

with refs(slug,image_url,source_url,source_name) as (
values
  ('chinese-cabbage','https://images.mango-prod.siammakro.cloud/product-images/7352901501123-bdd63837-ca36-43af-84ab-4cbffcb0cae0.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('dried-bamboo-shoot','https://images.mango-prod.siammakro.cloud/product-images/835184523168742-0b8b64c8-7631-47f1-a0ad-d55c9b745148.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-aro-shrimp-28-35-1kg','https://images.mango-prod.siammakro.cloud/product-images/7204398268611-6d70b4e0-5439-4cd5-bf4e-f3c2d2eec81c.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/6jba629-7204398268611','Makro PRO'),
  ('makro-cabbage-500-700g','https://images.mango-prod.siammakro.cloud/SOURCE/8e55ed93de624e46bf2dc24fd1b37239?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-catfish-400-600g','https://images.mango-prod.siammakro.cloud/SOURCE/270f6ca5a6d8456aa3580dea4efe8399?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/166452-565706619975270','Makro PRO'),
  ('makro-chicken-breast-skin-on-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606382067907-9e6eedad-bf00-4d35-ab4a-ea5a64f09b19.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-chicken-middle-wing-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/a0105c6292eb423ba883f1d48d29758e?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-chicken-tenderloin-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606381379779-eaaadb6f-99eb-4ecc-ad53-c1b9e4c0a446.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-chicken-wing-stick-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/e7716c315fd24e7f8423e80a7d19f266?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-chilean-mussel-meat-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/4a4385e740f34f19930246db8a4b00ba?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/g9kh8s0-6974641930435','Makro PRO'),
  ('makro-chinese-cabbage-400-600g','https://images.mango-prod.siammakro.cloud/product-images/7352901501123-bdd63837-ca36-43af-84ab-4cbffcb0cae0.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-choy-sum-500g','https://images.mango-prod.siammakro.cloud/product-images/7078708707523-65e7893d-6c84-4efb-a18a-04bd46b6764e.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-clam-1kg','https://images.mango-prod.siammakro.cloud/product-images/7167173689539-6061688f-dba6-463f-99ff-7156b0b4fc73.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/843376-7167173689539','Makro PRO'),
  ('makro-crab-breast-claw-500g','https://images.mango-prod.siammakro.cloud/product-images/496850194004493-682d2def-0edc-43bf-ba15-ba7a3b79be7d.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/p/13ild86K-496850194004493','Makro PRO'),
  ('makro-cucumber-500g','https://images.mango-prod.siammakro.cloud/product-images/6976617611459-a3469303-2a6a-49a9-b636-7cc455c9c4ed.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-cut-blue-crab-500g','https://images.mango-prod.siammakro.cloud/product-images/7416127815875-d5f47276-5187-4e8e-a633-7442f6f6b11b.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/HoReCa%20%7C%20Asian%20Cuisine%20Essentials%20%281%20Jul%20-%2031%20Jul%2026%29/40079','Makro PRO'),
  ('makro-duck-minced-gizzard-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/6560cffdd3a141318b23d07ab2535d3c?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/928220-357229728318241','Makro PRO'),
  ('makro-duck-whole-s-1kg','https://images.mango-prod.siammakro.cloud/product-images/921664243522588-74c26511-0f2e-4ff4-9db6-18e58db822a2.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/552500-921664243522588','Makro PRO'),
  ('makro-enoki-1kg','https://images.mango-prod.siammakro.cloud/product-images/6976617349315-8ae78f12-af0d-426a-b250-9dbc53ef2e68.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-gourami-sun-dried-500g','https://images.mango-prod.siammakro.cloud/product-images/7499747590339-f626718d-260b-4d28-89ee-45a3a0dde774.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/900973-7499747590339','Makro PRO'),
  ('makro-half-shell-mussel-1kg','https://images.mango-prod.siammakro.cloud/product-images/724338579634982-823c79c0-4331-4ee1-b1b7-1a7f3834ad2c.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/931585-724338579634982','Makro PRO'),
  ('makro-import-carrot-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606383542467-e91c2264-6e5d-410e-bb5f-264f5227a275.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-king-oyster-500g','https://images.mango-prod.siammakro.cloud/SOURCE/e8123af7dda24c188c59c8f8a292b8da?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-mussel-meat-1kg','https://images.mango-prod.siammakro.cloud/product-images/486238897813711-e8eff273-f712-470f-81b5-7f511ef2a628.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/RT_jXAUr-486238897813711','Makro PRO'),
  ('makro-onion-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/cfd923aa4c714c4382ef12c0a66c9a9e?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-oyster-meat-100g','https://images.mango-prod.siammakro.cloud/product-images/7275699273923-0f2985b4-3834-4faa-acc2-7a59ba91e732.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/835213-7275699273923','Makro PRO'),
  ('makro-peeled-garlic-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/40dec5834b184e629780def6e7531efb?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-pork-hip-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/8b34b1c4ba894450be3e735188bd698c?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-pork-loin-1kg','https://images.mango-prod.siammakro.cloud/product-images/291270989495295-6411e46c-4815-4b27-8416-ce1c2328eb3e.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-pork-minced-fat10-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606382592195-94bb55b9-1e96-4bba-a4a2-abc8ccd2108b.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-pork-rib-slice-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/03731fdc33df46db9188b5324614ce7c?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO'),
  ('makro-pork-shoulder-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606384197827-6083d8aa-6fcb-4c2f-b498-cd866cbdd16a.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('makro-red-tilapia-900g-1-2kg','https://images.mango-prod.siammakro.cloud/SOURCE/3cd5c537c71d4d1daec7714ace831eab?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/916297-39582199052231','Makro PRO'),
  ('makro-scallop-half-shell-1kg','https://images.mango-prod.siammakro.cloud/product-images/7078809567427-95beb142-eeb9-467b-bd51-e1f6b81213b0.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/143992-7078809567427','Makro PRO'),
  ('makro-white-shrimp-meat-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/513834978cab41eaa798b1873fb2ff48?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/zw3rk47-7196423880899','Makro PRO'),
  ('makro-white-shrimp-tail-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/dc55fcd347f34bcda801b1713a2e3fde?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/155650-7078809043139','Makro PRO'),
  ('pork-tenderloin-dressed','https://images.mango-prod.siammakro.cloud/SOURCE/bf4d7341c09e4756ad1dc7d3f73a44ec?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO'),
  ('whole-duck','https://images.mango-prod.siammakro.cloud/product-images/921664243522588-74c26511-0f2e-4ff4-9db6-18e58db822a2.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080',null,'Makro PRO')
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=coalesce(r.source_url,m.source_url),
    catalog_preview_source_name=r.source_name,
    updated_at=now()
from refs r
where m.slug=r.slug;

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
    jsonb_agg(to_jsonb(x) order by x.has_catalog_image desc,x.name_th,x.id),
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
      m.catalog_preview_image,
      m.catalog_preview_source_url,
      m.catalog_preview_source_name,
      (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null) as has_approved_image,
      (nullif(btrim(coalesce(m.catalog_preview_image,'')),'') is not null) as has_preview_image,
      (
        (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null)
        or nullif(btrim(coalesce(m.catalog_preview_image,'')),'') is not null
      ) as has_catalog_image,
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
      (
        (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null)
        or nullif(btrim(coalesce(m.catalog_preview_image,'')),'') is not null
      ) desc,
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
