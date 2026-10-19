-- QueueGo Market open-license image approval batch 7.
-- Exact/common cultivar matches only; existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('lotus-ripe-namdokmai-mango-kg','https://upload.wikimedia.org/wikipedia/commons/a/af/Mango_fruit_Nam_Dok_Mai.jpg','https://commons.wikimedia.org/wiki/File:Mango_fruit_Nam_Dok_Mai.jpg','Ivar Leidus · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-red-lady-papaya-1kg','https://upload.wikimedia.org/wikipedia/commons/7/7e/Red_lady_fruit.jpg','https://commons.wikimedia.org/wiki/File:Red_lady_fruit.jpg','Vijayanrajapuram · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-monthong-durian-2-3kg','https://upload.wikimedia.org/wikipedia/commons/e/eb/Durian_Monthong_Lokal_Malang.jpg','https://commons.wikimedia.org/wiki/File:Durian_Monthong_Lokal_Malang.jpg','Vitaamaliaf · CC0 1.0 · Wikimedia Commons'),
  ('makro-shine-muscat-500g','https://upload.wikimedia.org/wikipedia/commons/d/d5/Shine_Muscat_%2820241101%29.jpg','https://commons.wikimedia.org/wiki/File:Shine_Muscat_(20241101).jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
