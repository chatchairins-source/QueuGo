-- Exact Makro PRO product-ID preview packshots for Market Master.
-- Merchant-only catalog references; customer storefront artwork remains separate.
with refs(slug,image_url,source_url) as (
values
  ('makro-catfish-cut-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/f2b8198ff5674b40883fc6135fd70d80','https://www.makro.pro/th/p/7325028778179'),
  ('makro-catfish-400-600g','https://images.mango-prod.siammakro.cloud/SOURCE/270f6ca5a6d8456aa3580dea4efe8399','https://www.makro.pro/th/p/565706619975270'),
  ('makro-white-sand-fish-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/946bb804d0d14ed48f1eab9b7a022c69','https://www.makro.pro/th/p/31434973638933'),
  ('makro-gourami-sun-dried-500g','https://images.mango-prod.siammakro.cloud/product-images/7499747590339-f626718d-260b-4d28-89ee-45a3a0dde774.jpeg','https://www.makro.pro/th/p/7499747590339'),
  ('makro-white-shrimp-meat-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/513834978cab41eaa798b1873fb2ff48','https://www.makro.pro/th/p/7196423880899'),
  ('makro-white-shrimp-tail-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/dc55fcd347f34bcda801b1713a2e3fde','https://www.makro.pro/th/p/7078809043139'),
  ('makro-white-shrimp-l-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606384754883-5091d3d5-3fa1-4095-ab69-90cf6173e8d9.jpeg','https://www.makro.pro/th/p/7606384754883'),
  ('makro-river-prawn-small-1kg','https://images.mango-prod.siammakro.cloud/product-images/739215489512557-f3a5bf65-e437-43e2-9647-0778aa7061d8.jpeg','https://www.makro.pro/th/p/739215489512557'),
  ('makro-aro-shrimp-28-35-1kg','https://images.mango-prod.siammakro.cloud/product-images/7204398268611-6d70b4e0-5439-4cd5-bf4e-f3c2d2eec81c.jpeg','https://www.makro.pro/th/p/7204398268611'),
  ('makro-mussel-meat-1kg','https://images.mango-prod.siammakro.cloud/product-images/486238897813711-e8eff273-f712-470f-81b5-7f511ef2a628.jpeg','https://www.makro.pro/th/p/486238897813711'),
  ('makro-half-shell-mussel-1kg','https://images.mango-prod.siammakro.cloud/product-images/724338579634982-823c79c0-4331-4ee1-b1b7-1a7f3834ad2c.jpeg','https://www.makro.pro/th/p/724338579634982'),
  ('makro-chilean-mussel-meat-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/4a4385e740f34f19930246db8a4b00ba','https://www.makro.pro/th/p/6974641930435'),
  ('makro-squid-rings-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/3be99f0f53bc4289b61024ac7be552e8','https://www.makro.pro/th/p/6974604771523'),
  ('makro-guava-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/0f48b74c1584416fa9c74adf9621dc1a','https://www.makro.pro/th/p/481477667716528'),
  ('makro-golden-rambutan-2-5kg','https://images.mango-prod.siammakro.cloud/product-images/471432494654513-3b4f2188-019f-42e7-be00-5ade5fb46fd4.jpeg','https://www.makro.pro/th/p/471432494654513'),
  ('makro-monthong-durian-2-3kg','https://images.mango-prod.siammakro.cloud/product-images/645412775209079-16459776-c818-4d26-b0f8-4709c19ead0c.jpeg','https://www.makro.pro/th/p/645412775209079'),
  ('makro-mangosteen-basket-3kg','https://images.mango-prod.siammakro.cloud/product-images/876358407272104-70b72f5c-4e76-4546-a0d7-640a432575ef.jpeg','https://www.makro.pro/th/p/876358407272104'),
  ('makro-coriander-300g','https://images.mango-prod.siammakro.cloud/SOURCE/8531f32e5532489fbd0901752f7fe6df','https://www.makro.pro/th/p/7078705889475'),
  ('makro-celery-300g','https://images.mango-prod.siammakro.cloud/SOURCE/4413107ae1154b5091278736954fff4f','https://www.makro.pro/th/p/7078707953859'),
  ('makro-cauliflower-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606380331203-96cb478c-1b14-442c-b3c5-f6ce805ad1ff.jpeg','https://www.makro.pro/th/p/7606380331203'),
  ('makro-broccoli-10kg','https://images.mango-prod.siammakro.cloud/product-images/702158168803212-c0e3f6aa-43d3-42b8-94ba-d9eaa5231645.jpeg','https://www.makro.pro/th/p/702158168803212'),
  ('makro-long-bean-500g','https://images.mango-prod.siammakro.cloud/product-images/7416039112899-b4429a07-22fa-4398-bb82-a39913bb8aa8.jpeg','https://www.makro.pro/th/p/7416039112899'),
  ('makro-large-pumpkin-half-kg','https://images.mango-prod.siammakro.cloud/product-images/141252868777614-d6edfb6a-c67f-4a39-b0da-bb172b77ad32.jpeg','https://www.makro.pro/th/p/141252868777614'),
  ('makro-old-galangal-1kg','https://images.mango-prod.siammakro.cloud/product-images/335646183364767-ff8203af-afec-4909-bb5d-5053d14e053f.jpeg','https://www.makro.pro/th/p/335646183364767'),
  ('makro-lemongrass-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/5843242d741c43358287f80bad8c0fee','https://www.makro.pro/th/p/7543242555587'),
  ('makro-kaffir-lime-leaf-150g','https://images.mango-prod.siammakro.cloud/SOURCE/fea9bb3010c247108dd989d1a00df11d','https://www.makro.pro/th/p/7422334861507'),
  ('makro-bird-chili-300g','https://images.mango-prod.siammakro.cloud/product-images/7193008636099-58c3adf4-a50a-4b8a-968f-dbd69023b666.jpeg','https://www.makro.pro/th/p/7193008636099'),
  ('makro-thai-eggplant-300g','https://images.mango-prod.siammakro.cloud/SOURCE/0cbbef6a3aa9409f8934ebf2ba111452','https://www.makro.pro/th/p/7422332567747'),
  ('makro-key-lime-10','https://images.mango-prod.siammakro.cloud/SOURCE/f66af3c518c149da8370b88eb4b54e18','https://www.makro.pro/th/p/7275698356419'),
  ('makro-oyster-meat-100g','https://images.mango-prod.siammakro.cloud/product-images/7275699273923-0f2985b4-3834-4faa-acc2-7a59ba91e732.jpeg','https://www.makro.pro/th/p/7275699273923'),
  ('makro-clam-1kg','https://images.mango-prod.siammakro.cloud/product-images/7167173689539-6061688f-dba6-463f-99ff-7156b0b4fc73.jpeg','https://www.makro.pro/th/p/7167173689539'),
  ('makro-scallop-half-shell-1kg','https://images.mango-prod.siammakro.cloud/product-images/7078809567427-95beb142-eeb9-467b-bd51-e1f6b81213b0.jpeg','https://www.makro.pro/th/p/7078809567427'),
  ('makro-soft-shell-crab-1kg','https://images.mango-prod.siammakro.cloud/product-images/6974747115715-7f3da9d9-7add-43eb-bbdb-f5c88c8bc9a5.jpeg','https://www.makro.pro/th/p/6974747115715'),
  ('makro-blue-crab-large-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/bd28d8d1a65348cd9aa4f2a44395f6a5','https://www.makro.pro/th/p/831006247598074')
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
