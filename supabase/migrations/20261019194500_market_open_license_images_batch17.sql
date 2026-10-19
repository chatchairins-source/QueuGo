-- QueueGo Market open-license image approval batch 17.
-- Dry-goods/tofu-skin exact commodity matches only; existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('dried-bamboo-shoot','https://upload.wikimedia.org/wikipedia/commons/a/ad/Dried_bamboo_shoots_%282%29.jpg','https://commons.wikimedia.org/wiki/File:Dried_bamboo_shoots_(2).jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('fresh-tofu-skin','https://upload.wikimedia.org/wikipedia/commons/9/9a/Tofu_skin.jpg','https://commons.wikimedia.org/wiki/File:Tofu_skin.jpg','Andrea Nguyen · CC BY 2.0 · Wikimedia Commons'),
  ('textured-vegetable-protein','https://upload.wikimedia.org/wikipedia/commons/5/5e/Textured_vegetable_protein.jpg','https://commons.wikimedia.org/wiki/File:Textured_vegetable_protein.jpg','NTK · Public domain · Wikimedia Commons'),
  ('ginkgo-nut','https://upload.wikimedia.org/wikipedia/commons/7/71/Peeled_ginkgo_nuts.jpg','https://commons.wikimedia.org/wiki/File:Peeled_ginkgo_nuts.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
