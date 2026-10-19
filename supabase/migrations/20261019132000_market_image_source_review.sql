-- QueueGo Market image-source review checkpoint.
-- External retailer images/pages are references only. They remain image_status='pending' and are never
-- copied to QueueGo approved storage or rendered as approved product art without reuse-rights evidence.

with candidates(slug,image_source) as (
values
  ('kale-mixed','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215'),
  ('chinese-cabbage','https://images.mango-prod.siammakro.cloud/product-images/7352901501123-bdd63837-ca36-43af-84ab-4cbffcb0cae0.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('choy-sum','https://www.makro.pro/th/p/826831-7078708216003'),
  ('cabbage','https://www.makro.pro/th/c/collections/Fresh%20Fruit%20and%20Veggie%20Stock-Up%20Blitz%21/41158'),
  ('thai-basil','https://o2o-static.lotuss.com/products/107654/75673037.jpg'),
  ('spring-onion','https://o2o-static.lotuss.com/products/107654/21701784.jpg'),
  ('holy-basil-leaves','https://o2o-static.lotuss.com/products/107654/20454775.jpg'),
  ('water-spinach','https://www.lotuss.com/th/product/75583825'),
  ('large-cavendish-banana','https://o2o-static.lotuss.com/products/107654/2754533.jpg'),
  ('pork-tenderloin-dressed','https://images.mango-prod.siammakro.cloud/SOURCE/bf4d7341c09e4756ad1dc7d3f73a44ec?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('pork-belly','https://www.makro.pro/p/3ql5zp7-6974749180099'),
  ('native-chicken','https://www.lotuss.com/th/product/50862896'),
  ('whole-duck','https://images.mango-prod.siammakro.cloud/product-images/921664243522588-74c26511-0f2e-4ff4-9db6-18e58db822a2.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('egg-size-3-10','https://www.lotuss.com/th/product/cp-selection-no-3-fresh-egg-10pcs-51177663'),
  ('dried-shiitake-small','https://www.lotuss.com/th/product/51976604'),
  ('dried-shiitake-large','https://www.lotuss.com/th/product/tesco-large-dried-shitake-mushroom-80g-10628258'),
  ('dried-bamboo-shoot','https://images.mango-prod.siammakro.cloud/product-images/835184523168742-0b8b64c8-7631-47f1-a0ad-d55c9b745148.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('dried-lily-flower','https://www.makro.pro/th/c/world-of-cuisines/chinese-cuisine'),
  ('mee-sua-400g','https://www.lotuss.com/th/product/75610239'),
  ('makro-chicken-breast-skin-on-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606382067907-9e6eedad-bf00-4d35-ab4a-ea5a64f09b19.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-chicken-leg-quarter-1kg','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448'),
  ('makro-chicken-wing-stick-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/e7716c315fd24e7f8423e80a7d19f266?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-chicken-tenderloin-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606381379779-eaaadb6f-99eb-4ecc-ad53-c1b9e4c0a446.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-chicken-drumstick-1kg','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448'),
  ('makro-chicken-middle-wing-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/a0105c6292eb423ba883f1d48d29758e?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-pork-hip-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/8b34b1c4ba894450be3e735188bd698c?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-pork-loin-1kg','https://images.mango-prod.siammakro.cloud/product-images/291270989495295-6411e46c-4815-4b27-8416-ce1c2328eb3e.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-pork-minced-fat10-1kg','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448'),
  ('makro-pork-rib-slice-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/03731fdc33df46db9188b5324614ce7c?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-pork-shoulder-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606384197827-6083d8aa-6fcb-4c2f-b498-cd866cbdd16a.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-duck-minced-gizzard-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/6560cffdd3a141318b23d07ab2535d3c?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-duck-whole-s-1kg','https://images.mango-prod.siammakro.cloud/product-images/921664243522588-74c26511-0f2e-4ff4-9db6-18e58db822a2.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('lotus-duck-whole-m','https://o2o-static.lotuss.com/products/107486/51262018.jpg'),
  ('lotus-duck-whole-xl','https://www.lotuss.com/th/product/51467654'),
  ('lotus-beef-sirloin','https://o2o-static.lotuss.com/products/107486/75591299.jpg'),
  ('lotus-beef-shank','https://o2o-static.lotuss.com/products/107486/75591305.jpg'),
  ('lotus-beef-rib','https://o2o-static.lotuss.com/products/107486/75591312.jpg'),
  ('lotus-beef-hip','https://www.lotuss.com/th/smartx/category/meat/beef'),
  ('makro-enoki-1kg','https://images.mango-prod.siammakro.cloud/product-images/6976617349315-8ae78f12-af0d-426a-b250-9dbc53ef2e68.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-white-shimeji-1kg','https://www.makro.pro/en/c/collections/Gold%20Container%20-%20Fresh%20Value%20Deal%20%289%20Jul%202026%29/40637'),
  ('makro-king-oyster-500g','https://images.mango-prod.siammakro.cloud/SOURCE/e8123af7dda24c188c59c8f8a292b8da?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('lotus-fresh-shiitake-100g','https://o2o-static.lotuss.com/products/107654/75713235.jpg'),
  ('lotus-enoki-500g','https://www.lotuss.com/th/shop/fresh-food?sort=relevance%3ADESC'),
  ('makro-import-carrot-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606383542467-e91c2264-6e5d-410e-bb5f-264f5227a275.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-chinese-cabbage-400-600g','https://images.mango-prod.siammakro.cloud/product-images/7352901501123-bdd63837-ca36-43af-84ab-4cbffcb0cae0.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-cabbage-500-700g','https://images.mango-prod.siammakro.cloud/SOURCE/8e55ed93de624e46bf2dc24fd1b37239?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-cucumber-500g','https://images.mango-prod.siammakro.cloud/product-images/6976617611459-a3469303-2a6a-49a9-b636-7cc455c9c4ed.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-kale-500g','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215'),
  ('makro-choy-sum-500g','https://images.mango-prod.siammakro.cloud/product-images/7078708707523-65e7893d-6c84-4efb-a18a-04bd46b6764e.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-peeled-garlic-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/40dec5834b184e629780def6e7531efb?eo-img.format=webp&eo-img.resize=w%2F1080'),
  ('makro-tomato-1kg','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215'),
  ('makro-onion-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/cfd923aa4c714c4382ef12c0a66c9a9e?eo-img.format=webp&eo-img.resize=w%2F1080')
)
update public.market_master_products m
set image_source=c.image_source,
    image_status='pending',
    image=null,
    image_rights_evidence=null,
    updated_at=now()
from candidates c
where m.slug=c.slug
  and m.image_status<>'approved';

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
  v_shop:=public.pos_my_shop();
  select public_category into v_shop_category
  from public.shop_profiles
  where id=v_shop and archived_at is null;

  if v_shop is null
     or not public.pos_is_owner()
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
