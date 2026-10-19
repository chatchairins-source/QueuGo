-- Curated high-confidence Makro PRO preview gap-fill for Market Master.
-- Merchant-only reference images; customer storefront product art remains separately approved/store-owned.
-- Deliberately excludes visually misleading variant matches (marinated pork belly, BBQ wings,
-- mismatched egg packs, cooked cockle meat, peeled durian, mixed celery bundle, etc.).

with refs(slug,image_url,source_url) as (
values
  ('chinese-cabbage','https://images.mango-prod.siammakro.cloud/SOURCE/7ca1c447cfec4eb39636b8a98a80ba6a','https://www.makro.pro/th/p/72228168183436'),
  ('choy-sum','https://images.mango-prod.siammakro.cloud/product-images/7078708707523-65e7893d-6c84-4efb-a18a-04bd46b6764e.jpeg','https://www.makro.pro/th/p/7078708707523'),
  ('thai-basil','https://images.mango-prod.siammakro.cloud/product-images/7078706708675-9078f5c1-1844-46ce-918c-9b5a80b7adad.jpeg','https://www.makro.pro/th/p/7078706708675'),
  ('spring-onion','https://images.mango-prod.siammakro.cloud/SOURCE/8d492c7e0c2942469d060371fe0bf87b','https://www.makro.pro/th/p/7275689443523'),
  ('holy-basil-leaves','https://images.mango-prod.siammakro.cloud/product-images/7275698520259-049950e2-039c-4469-bdeb-4993ccb2ee51.jpeg','https://www.makro.pro/th/p/7275698520259'),
  ('large-cavendish-banana','https://images.mango-prod.siammakro.cloud/product-images/7416140234947-ef05144d-21b4-4cb3-bd77-92f1662b90c1.jpeg','https://www.makro.pro/th/p/7416140234947'),
  ('pork-tenderloin-dressed','https://images.mango-prod.siammakro.cloud/SOURCE/bf4d7341c09e4756ad1dc7d3f73a44ec','https://www.makro.pro/th/p/7606385672387'),
  ('dried-lily-flower','https://images.mango-prod.siammakro.cloud/SOURCE/82532524c2e546299781a7346d1c4edf','https://www.makro.pro/th/p/7196400779459'),
  ('ginkgo-nut','https://images.mango-prod.siammakro.cloud/SOURCE/c1303708979f42e2a03f2bfd47c394f5','https://www.makro.pro/th/p/7115225333955'),
  ('makro-chicken-breast-skin-on-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606382067907-9e6eedad-bf00-4d35-ab4a-ea5a64f09b19.jpeg','https://www.makro.pro/th/p/7606382067907'),
  ('makro-chicken-leg-quarter-1kg','https://images.mango-prod.siammakro.cloud/product-images/775090870054429-5690dc6f-df9c-445c-99a8-48a2333a1499.jpeg','https://www.makro.pro/th/p/775090870054429'),
  ('makro-chicken-wing-stick-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/e7716c315fd24e7f8423e80a7d19f266','https://www.makro.pro/th/p/7606381445315'),
  ('makro-chicken-tenderloin-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606381379779-eaaadb6f-99eb-4ecc-ad53-c1b9e4c0a446.jpeg','https://www.makro.pro/th/p/7606381379779'),
  ('makro-chicken-drumstick-1kg','https://images.mango-prod.siammakro.cloud/product-images/7196423782595-7e338cc5-d047-41b2-b9ca-aefd9b67ee1a.jpeg','https://www.makro.pro/th/p/7196423782595'),
  ('makro-pork-hip-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/46fec37afd7640a689c91b011736debb','https://www.makro.pro/th/p/635914030471687'),
  ('makro-pork-loin-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/14133ca4268b489790c436c5ea85a2d1','https://www.makro.pro/th/p/930553716174385'),
  ('makro-pork-minced-fat10-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606382592195-94bb55b9-1e96-4bba-a4a2-abc8ccd2108b.jpeg','https://www.makro.pro/th/p/7606382592195'),
  ('makro-pork-shoulder-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606384197827-6083d8aa-6fcb-4c2f-b498-cd866cbdd16a.jpeg','https://www.makro.pro/th/p/7606384197827'),
  ('makro-enoki-1kg','https://images.mango-prod.siammakro.cloud/product-images/6976618397891-8bf43848-3bcf-4300-965f-110fe4268f5d.jpeg','https://www.makro.pro/th/p/6976618397891'),
  ('makro-king-oyster-500g','https://images.mango-prod.siammakro.cloud/SOURCE/e8123af7dda24c188c59c8f8a292b8da','https://www.makro.pro/th/p/7243311743171'),
  ('lotus-fresh-shiitake-100g','https://images.mango-prod.siammakro.cloud/SOURCE/ca933f5d7d3f49afb6f0c5f7933c627f','https://www.makro.pro/th/p/6976620036291'),
  ('lotus-enoki-500g','https://images.mango-prod.siammakro.cloud/product-images/6976618397891-8bf43848-3bcf-4300-965f-110fe4268f5d.jpeg','https://www.makro.pro/th/p/6976618397891'),
  ('makro-import-carrot-1kg','https://images.mango-prod.siammakro.cloud/product-images/7606383542467-e91c2264-6e5d-410e-bb5f-264f5227a275.jpeg','https://www.makro.pro/th/p/7606383542467'),
  ('makro-chinese-cabbage-400-600g','https://images.mango-prod.siammakro.cloud/SOURCE/7ca1c447cfec4eb39636b8a98a80ba6a','https://www.makro.pro/th/p/72228168183436'),
  ('makro-cucumber-500g','https://images.mango-prod.siammakro.cloud/product-images/6976617611459-a3469303-2a6a-49a9-b636-7cc455c9c4ed.jpeg','https://www.makro.pro/th/p/6976617611459'),
  ('makro-kale-500g','https://images.mango-prod.siammakro.cloud/product-images/7078708019395-16fcfa28-bd85-4bee-8b02-375f5322a593.jpeg','https://www.makro.pro/th/p/7078708019395'),
  ('makro-choy-sum-500g','https://images.mango-prod.siammakro.cloud/product-images/7078708707523-65e7893d-6c84-4efb-a18a-04bd46b6764e.jpeg','https://www.makro.pro/th/p/7078708707523'),
  ('makro-peeled-garlic-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/fbc4beca31b94148b01b52fedb2d7724','https://www.makro.pro/th/p/7078709002435'),
  ('makro-tomato-1kg','https://images.mango-prod.siammakro.cloud/product-images/7275696226499-513e3a7a-a01d-4cda-a163-cd8cba8bde1c.jpeg','https://www.makro.pro/th/p/7275696226499'),
  ('makro-onion-1kg','https://images.mango-prod.siammakro.cloud/product-images/6976618070211-bfdb8854-ec82-4432-8d29-912cc0751f40.jpeg','https://www.makro.pro/th/p/6976618070211'),
  ('makro-catfish-cut-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/f2b8198ff5674b40883fc6135fd70d80','https://www.makro.pro/th/p/7325028778179'),
  ('makro-white-sand-fish-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/946bb804d0d14ed48f1eab9b7a022c69','https://www.makro.pro/th/p/31434973638933'),
  ('makro-gourami-sun-dried-500g','https://images.mango-prod.siammakro.cloud/SOURCE/036d874dab1f4523ad0b726280bd1c11','https://www.makro.pro/th/p/564168289439610'),
  ('makro-mussel-meat-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/4a4385e740f34f19930246db8a4b00ba','https://www.makro.pro/th/p/6974641930435'),
  ('makro-half-shell-mussel-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/709947bb7ef54ffca4d3001319266247','https://www.makro.pro/th/p/7243311808707'),
  ('makro-chilean-mussel-meat-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/4a4385e740f34f19930246db8a4b00ba','https://www.makro.pro/th/p/6974641930435'),
  ('makro-kinnaree-watermelon-4kg','https://images.mango-prod.siammakro.cloud/SOURCE/95d43d48c97443fea8c1e52bc44d855e','https://www.makro.pro/th/p/551312627531523'),
  ('makro-guava-1kg','https://images.mango-prod.siammakro.cloud/product-images/188963332000408-a505a628-31ac-4489-ab68-230d841de53c.jpeg','https://www.makro.pro/th/p/188963332000408'),
  ('makro-red-lady-papaya-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/56f00735cd75463888c80fc81dd537bd','https://www.makro.pro/th/p/967705863335182'),
  ('makro-sunlady-cantaloupe-1kg','https://images.mango-prod.siammakro.cloud/product-images/744823429316792-0c62e666-fde5-4ec3-9085-5fe9e56e1d14.jpeg','https://www.makro.pro/th/p/744823429316792'),
  ('makro-coriander-300g','https://images.mango-prod.siammakro.cloud/SOURCE/7542884ae69d46d1bef93b243e84cee1','https://www.makro.pro/th/p/7275698290883'),
  ('makro-old-ginger-1kg','https://images.mango-prod.siammakro.cloud/product-images/174073521300528-8b246a50-ae5a-44b4-a6b9-cba63b60f668.jpeg','https://www.makro.pro/th/p/174073521300528'),
  ('makro-lemongrass-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/a3da5cad856842428fca4b796004ae18','https://www.makro.pro/th/p/279685401912765'),
  ('makro-kaffir-lime-leaf-150g','https://images.mango-prod.siammakro.cloud/SOURCE/0052e7af41d7486abc88c2991af730f0','https://www.makro.pro/th/p/7078709362883'),
  ('makro-thai-eggplant-300g','https://images.mango-prod.siammakro.cloud/SOURCE/673389d77106463eaaad57fd199326e6','https://www.makro.pro/th/p/7422339219651'),
  ('makro-key-lime-10','https://images.mango-prod.siammakro.cloud/SOURCE/f66af3c518c149da8370b88eb4b54e18','https://www.makro.pro/th/p/7275698356419'),
  ('makro-oyster-meat-100g','https://images.mango-prod.siammakro.cloud/product-images/7275699273923-0f2985b4-3834-4faa-acc2-7a59ba91e732.jpeg','https://www.makro.pro/th/p/7275699273923'),
  ('makro-clam-1kg','https://images.mango-prod.siammakro.cloud/product-images/7167173689539-6061688f-dba6-463f-99ff-7156b0b4fc73.jpeg','https://www.makro.pro/th/p/7167173689539'),
  ('makro-blue-crab-large-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/bd28d8d1a65348cd9aa4f2a44395f6a5','https://www.makro.pro/th/p/831006247598074')
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=r.source_url,
    catalog_preview_source_name='Makro PRO',
    updated_at=now()
from refs r
where m.slug=r.slug
  and m.active;

