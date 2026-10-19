-- QueueGo Market open-license image approval batch 15.
-- Exact commodity/processing matches only; existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-king-oyster-500g','https://upload.wikimedia.org/wikipedia/commons/f/f1/Kr%C3%A4uter-Seitling_Pleurotus_eryngii.jpg','https://commons.wikimedia.org/wiki/File:Kr%C3%A4uter-Seitling_Pleurotus_eryngii.jpg','Holger Krisp · CC BY 3.0 · Wikimedia Commons'),
  ('lotus-king-mackerel-3-5kg','https://upload.wikimedia.org/wikipedia/commons/c/c1/King-mackerel.jpg','https://commons.wikimedia.org/wiki/File:King-mackerel.jpg','ScubaBear68 · cropped by Wemler · CC BY 2.0 · Wikimedia Commons'),
  ('makro-white-shrimp-l-1kg','https://upload.wikimedia.org/wikipedia/commons/6/68/White_shrimp.jpg','https://commons.wikimedia.org/wiki/File:White_shrimp.jpg','Gayandream · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-squid-rings-1kg','https://upload.wikimedia.org/wikipedia/commons/6/62/Frozen_squid_rings.jpg','https://commons.wikimedia.org/wiki/File:Frozen_squid_rings.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('makro-squid-tentacles-1kg','https://upload.wikimedia.org/wikipedia/commons/c/c0/Chilled_squid_tentacles.jpg','https://commons.wikimedia.org/wiki/File:Chilled_squid_tentacles.jpg','François Nguyen · CC BY 2.0 · Wikimedia Commons'),
  ('makro-blue-crab-large-1kg','https://upload.wikimedia.org/wikipedia/commons/9/93/Blue-Crab_Patrick_J._Lynch_CC-BY.jpg','https://commons.wikimedia.org/wiki/File:Blue-Crab_Patrick_J._Lynch_CC-BY.jpg','Patrick J. Lynch · CC BY 4.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
