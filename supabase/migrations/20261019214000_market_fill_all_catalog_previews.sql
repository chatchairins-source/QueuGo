-- Fill every remaining active Market Master catalog preview with a retail/product image.
-- Visible Merchant UI shows only the product image; source metadata stays backend-only.
-- Makro PRO is preferred. Exact Lotus's / Big C / product-retailer imagery is used only
-- where the Makro catalog did not have a clean matching fresh-market product.

begin;

alter table public.market_master_products
  drop constraint if exists market_master_products_preview_host_check;

alter table public.market_master_products
  add constraint market_master_products_preview_host_check
  check (
    catalog_preview_image is null
    or catalog_preview_image like 'https://images.mango-prod.siammakro.cloud/%'
    or catalog_preview_image like 'https://o2o-static.lotuss.com/%'
    or catalog_preview_image like 'https://st.bigc-cs.com/%'
    or catalog_preview_image like 'https://riverfish.com.bd/%'
  );

with refs(slug,image_url,source_url,source_name) as (
values
  ('talaadthai-shrimp-chaebow-medium','https://images.mango-prod.siammakro.cloud/product-images/7606384754883-5091d3d5-3fa1-4095-ab69-90cf6173e8d9.jpeg','https://www.makro.pro/th/p/7606384754883','Makro PRO'),
  ('talaadthai-shrimp-chaebow-large','https://images.mango-prod.siammakro.cloud/SOURCE/ca47da5984174e5793a9814f666e56df','https://www.makro.pro/th/p/6974653300931','Makro PRO'),
  ('lotus-shrimp-chaebow-25-30kg','https://images.mango-prod.siammakro.cloud/SOURCE/ca47da5984174e5793a9814f666e56df','https://www.makro.pro/th/p/6974653300931','Makro PRO'),
  ('lotus-duck-whole-xl','https://images.mango-prod.siammakro.cloud/product-images/719934874995216-8a7012ca-ced7-4dba-9535-c4fbe5a6a5de.jpeg','https://www.makro.pro/th/p/719934874995216','Makro PRO'),
  ('lotus-duck-whole-m','https://images.mango-prod.siammakro.cloud/product-images/881262897942088-92e3db23-8d4c-4515-8454-1739f21a6372.jpeg','https://www.makro.pro/th/p/881262897942088','Makro PRO'),
  ('dried-squid-medium','https://images.mango-prod.siammakro.cloud/SOURCE/6e714716b8184077a19c2bad53dba945','https://www.makro.pro/th/p/7512874680515','Makro PRO'),
  ('mee-sua-400g','https://images.mango-prod.siammakro.cloud/product-images/607167254467143-052f6c2d-d3f2-4a89-906c-a484fd6366b0.jpeg','https://www.makro.pro/th/p/607167254467143','Makro PRO'),
  ('dried-shiitake-small','https://images.mango-prod.siammakro.cloud/SOURCE/5fb785ba4d754dfab57b609a7f8dc8e4','https://www.makro.pro/th/p/7078764150979','Makro PRO'),
  ('dried-shiitake-large','https://images.mango-prod.siammakro.cloud/SOURCE/f68da3c66ae84d628cfda6da1711ac63','https://www.makro.pro/th/p/7193012011203','Makro PRO'),
  ('fresh-tofu-skin','https://images.mango-prod.siammakro.cloud/product-images/7078764413123-1a9d34a3-1a4f-4850-bf44-51eda60a79c0.jpeg','https://www.makro.pro/th/p/7078764413123','Makro PRO'),
  ('lotus-beef-rib','https://images.mango-prod.siammakro.cloud/product-images/7499702763715-a28fba0c-f1e6-4368-b5be-ed95544ef6b8.jpeg','https://www.makro.pro/th/p/7499702763715','Makro PRO'),
  ('lotus-beef-shank','https://images.mango-prod.siammakro.cloud/product-images/886623404352245-472c50a4-a6eb-4bf3-bc6e-791412dc897e.jpeg','https://www.makro.pro/th/p/886623404352245','Makro PRO'),
  ('lotus-beef-hip','https://images.mango-prod.siammakro.cloud/SOURCE/573c43f4ecd643258a3987972a919f2c','https://www.makro.pro/th/p/229741736844198','Makro PRO'),
  ('lotus-beef-sirloin','https://images.mango-prod.siammakro.cloud/SOURCE/0654eea3b14d46309620b5b62e03b7a4','https://www.makro.pro/th/p/467804546795443','Makro PRO'),
  ('pork-lean','https://images.mango-prod.siammakro.cloud/product-images/7492546461891-343e06b8-b3cc-4358-83ee-bf8924b14576.jpeg','https://www.makro.pro/th/p/7492546461891','Makro PRO'),
  ('pork-belly','https://images.mango-prod.siammakro.cloud/product-images/7606384230595-904a3eae-a3fd-4315-8ae5-e7e6cb7cda54.jpeg','https://www.makro.pro/th/p/7606384230595','Makro PRO'),
  ('lotus-seabass-whole-4-6kg','https://images.mango-prod.siammakro.cloud/product-images/879051516531828-2b56dd07-10e2-4271-b476-a695516cb5ff.jpeg','https://www.makro.pro/th/p/879051516531828','Makro PRO'),
  ('lotus-seabass-sliced','https://images.mango-prod.siammakro.cloud/SOURCE/0c90bba376ed4d9a89ac4a92eb8dc1ea','https://www.makro.pro/th/p/7196421390531','Makro PRO'),
  ('lotus-snakehead-whole','https://images.mango-prod.siammakro.cloud/product-images/266031835612855-8e3a5af4-5b75-461c-82e6-6d42e4fc4605.jpeg','https://www.makro.pro/th/p/266031835612855','Makro PRO'),
  ('lotus-mackerel-kg','https://images.mango-prod.siammakro.cloud/SOURCE/8c5a8cd0b910411d82fdb1efe26ecb32','https://www.makro.pro/th/p/84925777539568','Makro PRO'),
  ('lotus-peeled-squid-kg','https://images.mango-prod.siammakro.cloud/SOURCE/038d7ed17a0745c09a6483d5ced4c7fa','https://www.makro.pro/th/p/7499629396163','Makro PRO'),
  ('makro-squid-tentacles-1kg','https://images.mango-prod.siammakro.cloud/product-images/7499690901699-d0d40537-1ef7-473b-bc63-1f43d2b2496e.jpeg','https://www.makro.pro/th/p/7499690901699','Makro PRO'),
  ('lotus-peeled-cuttlefish-kg','https://images.mango-prod.siammakro.cloud/product-images/810736321465392-f1c4fc7b-49b0-4401-8dfa-88a76190d8e7.jpeg','https://www.makro.pro/th/p/810736321465392','Makro PRO'),
  ('lotus-cut-blue-crab-500g','https://images.mango-prod.siammakro.cloud/product-images/7416127815875-d5f47276-5187-4e8e-a633-7442f6f6b11b.jpeg','https://www.makro.pro/th/p/7416127815875','Makro PRO'),
  ('lotus-hom-banana-bunch','https://images.mango-prod.siammakro.cloud/product-images/7416140234947-ef05144d-21b4-4cb3-bd77-92f1662b90c1.jpeg','https://www.makro.pro/th/p/7416140234947','Makro PRO'),
  ('lotus-hom-suwan-pineapple','https://images.mango-prod.siammakro.cloud/product-images/7497425092803-6c496442-eeac-4588-aa75-34446b509baf.jpeg','https://www.makro.pro/th/p/7497425092803','Makro PRO'),
  ('makro-shine-muscat-500g','https://images.mango-prod.siammakro.cloud/product-images/268767378367693-207e0be1-7462-49e0-8ce9-2bcb27f50fac.jpeg','https://www.makro.pro/th/p/268767378367693','Makro PRO'),
  ('cabbage','https://images.mango-prod.siammakro.cloud/SOURCE/8e55ed93de624e46bf2dc24fd1b37239','https://www.makro.pro/th/p/7325007478979','Makro PRO'),
  ('kale-mixed','https://images.mango-prod.siammakro.cloud/product-images/7078708019395-16fcfa28-bd85-4bee-8b02-375f5322a593.jpeg','https://www.makro.pro/th/p/7078708019395','Makro PRO'),
  ('water-spinach','https://images.mango-prod.siammakro.cloud/product-images/7078709592259-f65f44a5-dda8-4804-8abf-41f30b4ad668.jpeg','https://www.makro.pro/th/p/7078709592259','Makro PRO'),
  ('lotus-small-clam','https://images.mango-prod.siammakro.cloud/SOURCE/c4620d1f6c214b0aa0faf8ad54ae900e','https://www.makro.pro/th/p/312980014446564','Makro PRO'),
  ('makro-white-shimeji-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/5dec29c684944b4d833d37c32e7250eb','https://www.makro.pro/th/p/7243311710403','Makro PRO'),
  ('native-chicken','https://images.mango-prod.siammakro.cloud/SOURCE/6c888af3257146a3a126997a64e3ceeb?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/80977-465764173427841','Makro PRO'),
  ('textured-vegetable-protein','https://st.bigc-cs.com/cdn-cgi/image/format%3Dwebp%2Cquality%3D90/public/media/catalog/product/02/88/8851212052002/8851212052002_1-20260605133249-.jpg','https://www.bigc.co.th/en/product/khun-yuy-textured-soy-proten-no4-size-300-g.17990','Big C'),
  ('makro-tilapia-900g-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/3141236b28564c6ba89ad8a27e2a76b3?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/146263-187607668532060','Makro PRO'),
  ('lotus-soft-fish-kg','https://o2o-static.lotuss.com/products/107486/52253272.jpg','https://www.lotuss.com/th/product/52253272','Lotus''s'),
  ('talaadthai-climbing-perch-small','https://riverfish.com.bd/cdn/shop/files/651799682_1364275992413563_935225654335051432_n.jpg?v=1773644483&width=3840','https://riverfish.com.bd/products/%E0%A6%B9%E0%A6%BE%E0%A6%93%E0%A6%A1%E0%A6%BC%E0%A7%87%E0%A6%B0-%E0%A6%A6%E0%A7%87%E0%A6%B6%E0%A6%BF-%E0%A6%95%E0%A6%87-%E0%A7%A8%E0%A7%AB-%E0%A6%A5%E0%A7%87%E0%A6%95%E0%A7%87-%E0%A7%A9%E0%A7%A6-%E0%A6%AA%E0%A6%BF%E0%A6%B8%E0%A7%87-%E0%A6%95%E0%A7%87%E0%A6%9C%E0%A6%BF-%E0%A6%B9%E0%A6%BE%E0%A6%AB-%E0%A6%95%E0%A7%87%E0%A6%9C%E0%A6%BF','River Fish'),
  ('lotus-king-mackerel-3-5kg','https://o2o-static.lotuss.com/products/77354/52475660.jpg','https://www.lotuss.com/th/product/52475660','Lotus''s'),
  ('talaadthai-mud-crab-medium','https://images.mango-prod.siammakro.cloud/SOURCE/6179a059dbef45e4bd7b885d6b915def','https://www.makro.pro/th/p/754884-373775434540204','Makro PRO'),
  ('talaadthai-mud-crab-small','https://images.mango-prod.siammakro.cloud/SOURCE/6179a059dbef45e4bd7b885d6b915def','https://www.makro.pro/th/p/754884-373775434540204','Makro PRO'),
  ('makro-red-dragon-fruit-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/110ddb9af43d439f83b4ba09ae92b3d8?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/112887-103354738454522','Makro PRO'),
  ('lotus-ripe-namdokmai-mango-kg','https://o2o-static.lotuss.com/products/107654/4723_1.jpg','https://www.lotuss.com/th/product/ripe-namdokmai-mango-4723?openExternalBrowser=1','Lotus''s'),
  ('makro-longan-1kg','https://st.bigc-cs.com/cdn-cgi/image/format%3Dwebp%2Cquality%3D90/public/media/catalog/product/01/02/0255601/0255601_1-20260403171145-.jpg','https://www.bigc.co.th/product/premium-grade-longan-per-kg.64516','Big C'),
  ('lotus-emperor-lychee-kg','https://o2o-static.lotuss.com/products/107654/13201220.jpg','https://www.lotuss.com/th/product/13201220','Lotus''s'),
  ('makro-honey-murcott-orange-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/a61c65942ac6445f8a46f4d66185e77a?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/136699-79878301101545','Makro PRO'),
  ('makro-fuji-apple-10','https://images.mango-prod.siammakro.cloud/product-images/7416139808963-7b25cd62-b4d7-49b5-8e85-9faead73b68c.jpeg?eo-img.format=webp&eo-img.resize=w%2F1080','https://www.makro.pro/th/p/120208-7416139808963','Makro PRO'),
  ('lotus-cockle-kg','https://st.bigc-cs.com/cdn-cgi/image/format%3Dwebp%2Cquality%3D90%2Cwidth%3D3840/public/media/catalog/product/40/02/0210640/thumbnail/0210640_1-20260408182948-.jpg','https://www.bigc.co.th/product/cockle-0210640.34725','Big C'),
  ('lotus-sweet-snail-kg','https://o2o-static.lotuss.com/products/107486/52102814_1.jpg','https://www.lotuss.com/th/product/52102814?openExternalBrowser=1','Lotus''s')
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=r.source_url,
    catalog_preview_source_name=r.source_name,
    updated_at=now()
from refs r
where m.slug=r.slug
  and m.active
  and nullif(btrim(coalesce(m.catalog_preview_image,'')),'') is null;

do $$
declare
  v_total integer;
  v_filled integer;
begin
  select count(*)::integer,
         count(*) filter (where nullif(btrim(coalesce(catalog_preview_image,'')),'') is not null)::integer
  into v_total,v_filled
  from public.market_master_products
  where active;

  if v_total <> 128 or v_filled <> v_total then
    raise exception 'Market catalog preview coverage incomplete: %/%',v_filled,v_total;
  end if;
end
$$;

commit;
