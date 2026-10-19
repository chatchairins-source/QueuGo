-- QueueGo Market open-license image approval batch 6.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-fuji-apple-10','https://upload.wikimedia.org/wikipedia/commons/c/c1/Fuji_apple.jpg','https://commons.wikimedia.org/wiki/File:Fuji_apple.jpg','Scott Bauer, USDA ARS · Public domain (USDA ARS) · Wikimedia Commons'),
  ('makro-longan-1kg','https://upload.wikimedia.org/wikipedia/commons/4/41/Thailand_longan.jpg','https://commons.wikimedia.org/wiki/File:Thailand_longan.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('makro-guava-1kg','https://upload.wikimedia.org/wikipedia/commons/3/3f/Fresh_Guava_fruit.jpg','https://commons.wikimedia.org/wiki/File:Fresh_Guava_fruit.jpg','Mohana hari balu · CC0 1.0 · Wikimedia Commons'),
  ('makro-mangosteen-basket-3kg','https://upload.wikimedia.org/wikipedia/commons/e/e8/Garcinia_mangostana_fruit1.jpg','https://commons.wikimedia.org/wiki/File:Garcinia_mangostana_fruit1.jpg','Shin-改 · Public domain · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
