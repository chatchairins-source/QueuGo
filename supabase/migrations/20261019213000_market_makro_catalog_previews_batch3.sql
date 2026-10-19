-- Second visually reviewed Makro PRO Market preview gap-fill.
-- Merchant-only reference imagery. Entries are the same commodity/cut/species; obvious mismatches
-- remain excluded. These previews never overwrite customer-facing products.image.

with refs(slug,image_url,source_url) as (
values
  ('kale-mixed','https://images.mango-prod.siammakro.cloud/product-images/7078708019395-16fcfa28-bd85-4bee-8b02-375f5322a593.jpeg','https://www.makro.pro/th/p/7078708019395'),
  ('cabbage','https://images.mango-prod.siammakro.cloud/SOURCE/8e55ed93de624e46bf2dc24fd1b37239','https://www.makro.pro/th/p/7325007478979'),
  ('water-spinach','https://images.mango-prod.siammakro.cloud/product-images/7078709592259-f65f44a5-dda8-4804-8abf-41f30b4ad668.jpeg','https://www.makro.pro/th/p/7078709592259'),
  ('dried-shiitake-large','https://images.mango-prod.siammakro.cloud/SOURCE/2bdd62d162ca4bd5b1551ff15e8b2e6d','https://www.makro.pro/th/p/696470680214214'),
  ('mee-sua-400g','https://images.mango-prod.siammakro.cloud/product-images/607167254467143-052f6c2d-d3f2-4a89-906c-a484fd6366b0.jpeg','https://www.makro.pro/th/p/607167254467143'),
  ('lotus-duck-whole-m','https://images.mango-prod.siammakro.cloud/product-images/881262897942088-92e3db23-8d4c-4515-8454-1739f21a6372.jpeg','https://www.makro.pro/th/p/881262897942088'),
  ('lotus-beef-sirloin','https://images.mango-prod.siammakro.cloud/product-images/7325024551107-9c571c9e-117b-4434-a52f-edcf92c52aec.jpeg','https://www.makro.pro/th/p/7325024551107'),
  ('lotus-beef-shank','https://images.mango-prod.siammakro.cloud/product-images/886623404352245-472c50a4-a6eb-4bf3-bc6e-791412dc897e.jpeg','https://www.makro.pro/th/p/886623404352245'),
  ('lotus-beef-hip','https://images.mango-prod.siammakro.cloud/SOURCE/573c43f4ecd643258a3987972a919f2c','https://www.makro.pro/th/p/229741736844198'),
  ('makro-tilapia-900g-1kg','https://images.mango-prod.siammakro.cloud/product-images/757171830369003-2e70e685-6e4b-4136-b5cc-d52776074ead.jpeg','https://www.makro.pro/th/p/757171830369003'),
  ('lotus-seabass-whole-4-6kg','https://images.mango-prod.siammakro.cloud/product-images/879051516531828-2b56dd07-10e2-4271-b476-a695516cb5ff.jpeg','https://www.makro.pro/th/p/879051516531828'),
  ('makro-river-prawn-small-1kg','https://images.mango-prod.siammakro.cloud/product-images/122240263068365-3fb430ce-ab3b-4043-8e55-0098ad6a758d.jpeg','https://www.makro.pro/th/p/122240263068365'),
  ('lotus-cut-blue-crab-500g','https://images.mango-prod.siammakro.cloud/product-images/7416127815875-d5f47276-5187-4e8e-a633-7442f6f6b11b.jpeg','https://www.makro.pro/th/p/7416127815875'),
  ('makro-squid-rings-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/3be99f0f53bc4289b61024ac7be552e8','https://www.makro.pro/th/p/6974604771523'),
  ('makro-squid-tentacles-1kg','https://images.mango-prod.siammakro.cloud/product-images/6974600347843-751c2228-0a01-4ae1-ad79-051ae203b967.jpeg','https://www.makro.pro/th/p/6974600347843'),
  ('lotus-hom-banana-bunch','https://images.mango-prod.siammakro.cloud/product-images/7416140234947-ef05144d-21b4-4cb3-bd77-92f1662b90c1.jpeg','https://www.makro.pro/th/p/7416140234947'),
  ('makro-cauliflower-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606380331203-96cb478c-1b14-442c-b3c5-f6ce805ad1ff.jpeg','https://www.makro.pro/th/p/7606380331203'),
  ('makro-long-bean-500g','https://images.mango-prod.siammakro.cloud/product-images/7416039112899-b4429a07-22fa-4398-bb82-a39913bb8aa8.jpeg','https://www.makro.pro/th/p/7416039112899'),
  ('makro-bird-chili-300g','https://images.mango-prod.siammakro.cloud/product-images/7193008636099-58c3adf4-a50a-4b8a-968f-dbd69023b666.jpeg','https://www.makro.pro/th/p/7193008636099'),
  ('makro-soft-shell-crab-1kg','https://images.mango-prod.siammakro.cloud/product-images/313173636621735-5871f908-8ec0-40c1-a576-5ad7abc7584f.jpeg','https://www.makro.pro/th/p/313173636621735'),
  ('lotus-peeled-cuttlefish-kg','https://images.mango-prod.siammakro.cloud/product-images/810736321465392-f1c4fc7b-49b0-4401-8dfa-88a76190d8e7.jpeg','https://www.makro.pro/th/p/810736321465392'),
  ('lotus-peeled-squid-kg','https://images.mango-prod.siammakro.cloud/SOURCE/28c42d46b273410c861a719f33c9d2c1','https://www.makro.pro/th/p/7606379872451')
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=r.source_url,
    catalog_preview_source_name='Makro PRO',
    updated_at=now()
from refs r
where m.slug=r.slug
  and m.active
  and r.image_url like 'https://images.mango-prod.siammakro.cloud/%';
