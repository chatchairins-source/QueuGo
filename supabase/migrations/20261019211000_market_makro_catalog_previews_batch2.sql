-- Additional visually reviewed Makro PRO Merchant-only preview packshots.
-- These are reference images in the Merchant catalog, matching the Grocery catalog UX.
-- They do not overwrite products.image or approved customer storefront artwork.
with refs(slug,image_url,source_url) as (
values
  ('choy-sum','https://images.mango-prod.siammakro.cloud/product-images/7078708707523-65e7893d-6c84-4efb-a18a-04bd46b6764e.jpeg','https://www.makro.pro/th/p/7078708707523'),
  ('thai-basil','https://images.mango-prod.siammakro.cloud/product-images/7078706708675-9078f5c1-1844-46ce-918c-9b5a80b7adad.jpeg','https://www.makro.pro/th/p/7078706708675'),
  ('holy-basil-leaves','https://images.mango-prod.siammakro.cloud/product-images/7275698520259-049950e2-039c-4469-bdeb-4993ccb2ee51.jpeg','https://www.makro.pro/th/p/7275698520259'),
  ('large-cavendish-banana','https://images.mango-prod.siammakro.cloud/product-images/7416140234947-ef05144d-21b4-4cb3-bd77-92f1662b90c1.jpeg','https://www.makro.pro/th/p/7416140234947'),
  ('egg-size-2-10','https://images.mango-prod.siammakro.cloud/SOURCE/e2b5a0c206fe4684a701a557e7b445ea','https://www.makro.pro/th/p/7552057704643'),
  ('egg-size-3-10','https://images.mango-prod.siammakro.cloud/SOURCE/95785eee352c4f228e30dc2fc66f850f','https://www.makro.pro/th/p/7552057770179'),
  ('dried-lily-flower','https://images.mango-prod.siammakro.cloud/SOURCE/82532524c2e546299781a7346d1c4edf','https://www.makro.pro/th/p/7196400779459'),
  ('ginkgo-nut','https://images.mango-prod.siammakro.cloud/SOURCE/c1303708979f42e2a03f2bfd47c394f5','https://www.makro.pro/th/p/7115225333955'),
  ('makro-chicken-leg-quarter-1kg','https://images.mango-prod.siammakro.cloud/product-images/775090870054429-5690dc6f-df9c-445c-99a8-48a2333a1499.jpeg','https://www.makro.pro/th/p/775090870054429'),
  ('makro-chicken-drumstick-1kg','https://images.mango-prod.siammakro.cloud/product-images/7196423782595-7e338cc5-d047-41b2-b9ca-aefd9b67ee1a.jpeg','https://www.makro.pro/th/p/7196423782595'),
  ('lotus-fresh-shiitake-100g','https://images.mango-prod.siammakro.cloud/SOURCE/ca933f5d7d3f49afb6f0c5f7933c627f','https://www.makro.pro/th/p/6976620036291'),
  ('lotus-enoki-500g','https://images.mango-prod.siammakro.cloud/product-images/6976618397891-8bf43848-3bcf-4300-965f-110fe4268f5d.jpeg','https://www.makro.pro/th/p/6976618397891'),
  ('makro-kale-500g','https://images.mango-prod.siammakro.cloud/product-images/7078708019395-16fcfa28-bd85-4bee-8b02-375f5322a593.jpeg','https://www.makro.pro/th/p/7078708019395'),
  ('makro-tomato-1kg','https://images.mango-prod.siammakro.cloud/product-images/7275696226499-513e3a7a-a01d-4cda-a163-cd8cba8bde1c.jpeg','https://www.makro.pro/th/p/7275696226499'),
  ('makro-catfish-cut-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/f2b8198ff5674b40883fc6135fd70d80','https://www.makro.pro/th/p/7325028778179'),
  ('makro-white-sand-fish-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/946bb804d0d14ed48f1eab9b7a022c69','https://www.makro.pro/th/p/31434973638933'),
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
  ('makro-blue-crab-large-1kg','https://images.mango-prod.siammakro.cloud/SOURCE/bd28d8d1a65348cd9aa4f2a44395f6a5','https://www.makro.pro/th/p/831006247598074')
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=r.source_url,
    catalog_preview_source_name='Makro PRO',
    updated_at=now()
from refs r
where m.slug=r.slug
  and r.image_url like 'https://images.mango-prod.siammakro.cloud/%';
