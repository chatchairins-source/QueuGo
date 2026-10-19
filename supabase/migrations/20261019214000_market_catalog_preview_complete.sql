-- Complete Merchant Market catalog product imagery.
-- Product photos are rendered like Grocery catalog images. Source metadata stays backend-only.
-- Customer storefront artwork remains separate; only catalog_preview_* fields are filled.

alter table public.market_master_products
  drop constraint if exists market_master_products_preview_host_check;

alter table public.market_master_products
  add constraint market_master_products_preview_host_check
  check (
    catalog_preview_image is null
    or catalog_preview_image like 'https://images.mango-prod.siammakro.cloud/%'
    or catalog_preview_image like 'https://o2o-static.lotuss.com/%'
    or catalog_preview_image like 'https://static.wixstatic.com/%'
    or catalog_preview_image like 'https://www.soontree.co.th/wp-content/uploads/%'
  );

with refs(slug,image_url,source_name) as (
values
  ('talaadthai-shrimp-chaebow-medium','https://o2o-static.lotuss.com/products/107486/52195167.jpg','Lotus''s'),
  ('talaadthai-shrimp-chaebow-large','https://o2o-static.lotuss.com/products/107486/52195167.jpg','Lotus''s'),
  ('lotus-shrimp-chaebow-25-30kg','https://o2o-static.lotuss.com/products/107486/52195167.jpg','Lotus''s'),
  ('native-chicken','https://o2o-static.lotuss.com/products/107486/50862896.jpg','Lotus''s'),
  ('lotus-duck-whole-xl','https://images.mango-prod.siammakro.cloud/product-images/719934874995216-8a7012ca-ced7-4dba-9535-c4fbe5a6a5de.jpeg','Makro PRO'),
  ('lotus-duck-whole-m','https://images.mango-prod.siammakro.cloud/product-images/881262897942088-92e3db23-8d4c-4515-8454-1739f21a6372.jpeg','Makro PRO'),
  ('dried-squid-medium','https://images.mango-prod.siammakro.cloud/SOURCE/6e714716b8184077a19c2bad53dba945','Makro PRO'),
  ('textured-vegetable-protein','https://images.mango-prod.siammakro.cloud/SOURCE/52739abce29043a38468a13380521855?eo-img.format=webp&eo-img.resize=w%2F1080','Makro PRO'),
  ('mee-sua-400g','https://images.mango-prod.siammakro.cloud/product-images/607167254467143-052f6c2d-d3f2-4a89-906c-a484fd6366b0.jpeg','Makro PRO'),
  ('dried-shiitake-small','https://images.mango-prod.siammakro.cloud/SOURCE/5fb785ba4d754dfab57b609a7f8dc8e4','Makro PRO'),
  ('dried-shiitake-large','https://images.mango-prod.siammakro.cloud/SOURCE/f68da3c66ae84d628cfda6da1711ac63','Makro PRO'),
  ('fresh-tofu-skin','https://images.mango-prod.siammakro.cloud/product-images/7078764413123-1a9d34a3-1a4f-4850-bf44-51eda60a79c0.jpeg','Makro PRO'),
  ('lotus-beef-rib','https://o2o-static.lotuss.com/products/107486/75591312.jpg','Lotus''s'),
  ('lotus-beef-shank','https://o2o-static.lotuss.com/products/107486/75591305.jpg','Lotus''s'),
  ('lotus-beef-hip','https://images.mango-prod.siammakro.cloud/SOURCE/573c43f4ecd643258a3987972a919f2c','Makro PRO'),
  ('lotus-beef-sirloin','https://o2o-static.lotuss.com/products/107486/75591299.jpg','Lotus''s'),
  ('pork-lean','https://images.mango-prod.siammakro.cloud/product-images/7492546461891-343e06b8-b3cc-4358-83ee-bf8924b14576.jpeg','Makro PRO'),
  ('pork-belly','https://images.mango-prod.siammakro.cloud/product-images/7606384230595-904a3eae-a3fd-4315-8ae5-e7e6cb7cda54.jpeg','Makro PRO'),
  ('lotus-seabass-whole-4-6kg','https://o2o-static.lotuss.com/products/77354/52475366.jpg','Lotus''s'),
  ('lotus-seabass-sliced','https://o2o-static.lotuss.com/products/77354/52475412.jpg','Lotus''s'),
  ('lotus-snakehead-whole','https://o2o-static.lotuss.com/products/107486/71072624.jpg','Lotus''s'),
  ('lotus-mackerel-kg','https://o2o-static.lotuss.com/products/107486/52195081.jpg','Lotus''s'),
  ('makro-tilapia-900g-1kg','https://images.mango-prod.siammakro.cloud/product-images/757171830369003-2e70e685-6e4b-4136-b5cc-d52776074ead.jpeg','Makro PRO'),
  ('lotus-soft-fish-kg','https://o2o-static.lotuss.com/products/107486/52253272.jpg','Lotus''s'),
  ('talaadthai-climbing-perch-small','https://static.wixstatic.com/media/62c431_978f040c57c340709ae8359cef74e7ef~mv2.jpg/v1/fill/w_980%2Ch_1369%2Cal_c%2Cq_85%2Cusm_0.66_1.00_0.01%2Cenc_avif%2Cquality_auto/62c431_978f040c57c340709ae8359cef74e7ef~mv2.jpg','Thongmankong'),
  ('lotus-king-mackerel-3-5kg','https://o2o-static.lotuss.com/products/77354/52475660.jpg','Lotus''s'),
  ('lotus-peeled-squid-kg','https://o2o-static.lotuss.com/products/107486/75604476.jpg','Lotus''s'),
  ('makro-squid-tentacles-1kg','https://images.mango-prod.siammakro.cloud/product-images/6974600347843-751c2228-0a01-4ae1-ad79-051ae203b967.jpeg','Makro PRO'),
  ('lotus-peeled-cuttlefish-kg','https://images.mango-prod.siammakro.cloud/product-images/810736321465392-f1c4fc7b-49b0-4401-8dfa-88a76190d8e7.jpeg','Makro PRO'),
  ('talaadthai-mud-crab-medium','https://www.soontree.co.th/wp-content/uploads/2018/03/mud-crab-live.jpg','Soontree Sea Products'),
  ('talaadthai-mud-crab-small','https://www.soontree.co.th/wp-content/uploads/2018/03/mud-crab-live.jpg','Soontree Sea Products'),
  ('lotus-cut-blue-crab-500g','https://o2o-static.lotuss.com/products/107486/75603632.jpg','Lotus''s'),
  ('lotus-hom-banana-bunch','https://o2o-static.lotuss.com/products/107654/2754533.jpg','Lotus''s'),
  ('makro-red-dragon-fruit-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/110ddb9af43d439f83b4ba09ae92b3d8?eo-img.format=webp&eo-img.resize=w%2F1080','Makro PRO'),
  ('lotus-ripe-namdokmai-mango-kg','https://o2o-static.lotuss.com/products/107654/4723.jpg','Lotus''s'),
  ('makro-longan-1kg','https://images.mango-prod.siammakro.cloud/product-images/519588845902332-f1bb6a6c-6d3f-4154-848d-a166565560fc.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','Makro PRO'),
  ('lotus-emperor-lychee-kg','https://o2o-static.lotuss.com/products/107654/13201220.jpg','Lotus''s'),
  ('makro-honey-murcott-orange-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/a61c65942ac6445f8a46f4d66185e77a?eo-img.format=webp&eo-img.resize=w%2F1080','Makro PRO'),
  ('lotus-hom-suwan-pineapple','https://o2o-static.lotuss.com/products/107654/75595574.jpg','Lotus''s'),
  ('makro-shine-muscat-500g','https://images.mango-prod.siammakro.cloud/product-images/268767378367693-207e0be1-7462-49e0-8ce9-2bcb27f50fac.jpeg','Makro PRO'),
  ('makro-fuji-apple-10','https://images.mango-prod.siammakro.cloud/product-images/7416139808963-7b25cd62-b4d7-49b5-8e85-9faead73b68c.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','Makro PRO'),
  ('cabbage','https://images.mango-prod.siammakro.cloud/SOURCE/8e55ed93de624e46bf2dc24fd1b37239','Makro PRO'),
  ('kale-mixed','https://images.mango-prod.siammakro.cloud/product-images/7078708019395-16fcfa28-bd85-4bee-8b02-375f5322a593.jpeg','Makro PRO'),
  ('water-spinach','https://images.mango-prod.siammakro.cloud/product-images/7078709592259-f65f44a5-dda8-4804-8abf-41f30b4ad668.jpeg','Makro PRO'),
  ('lotus-cockle-kg','https://o2o-static.lotuss.com/products/107486/51294268.jpg','Lotus''s'),
  ('lotus-small-clam','https://o2o-static.lotuss.com/products/107486/75597747.jpg','Lotus''s'),
  ('lotus-sweet-snail-kg','https://o2o-static.lotuss.com/products/107486/52102814.jpg','Lotus''s'),
  ('makro-white-shimeji-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/5dec29c684944b4d833d37c32e7250eb','Makro PRO')
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=r.image_url,
    catalog_preview_source_name=r.source_name,
    updated_at=now()
from refs r
where m.slug=r.slug
  and m.active
  and m.catalog_preview_image is null;

do $$
declare
  v_missing integer;
begin
  select count(*) into v_missing
  from public.market_master_products
  where active and nullif(btrim(coalesce(catalog_preview_image,'')),'') is null;

  if v_missing <> 0 then
    raise exception 'Market catalog image completion failed: % active rows remain without preview images', v_missing;
  end if;
end
$$;
