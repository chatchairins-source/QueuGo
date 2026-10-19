-- QueueGo Market image-source gap fill for the remaining catalog families.
-- These are source/review references only. No external retailer image is published as an approved QueueGo image.

with refs(slug,image_source) as (
values
  ('pork-lean','https://www.lotuss.com/th/product/75592789'),
  ('egg-size-2-10','https://www.lotuss.com/th/product/betagro-hyegg-no2-chicken-egg-10pcs-51389936?openExternalBrowser=1'),
  ('fresh-tofu-skin','https://www.makro.pro/th/p/929752-703370101472939'),
  ('textured-vegetable-protein','https://www.bigc.co.th/product/khun-yuy-textured-soy-proten-no4-size-300-g.17990'),
  ('ginkgo-nut','https://www.makro.pro/p/225112-7115225333955'),
  ('dried-squid-medium','https://www.makro.pro/th/p/-03-00-01-523207652260723')
)
update public.market_master_products m
set image_source=r.image_source,
    image_status='pending',
    image=null,
    image_rights_evidence=null,
    updated_at=now()
from refs r
where m.slug=r.slug
  and m.image_status<>'approved';
