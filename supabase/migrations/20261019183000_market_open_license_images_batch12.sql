-- QueueGo Market open-license image approval batch 12.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-tilapia-900g-1kg','https://upload.wikimedia.org/wikipedia/commons/5/57/Tilapia_oreochromis_niloticus_fish.jpg','https://commons.wikimedia.org/wiki/File:Tilapia_oreochromis_niloticus_fish.jpg','Raver Duane, U.S. Fish and Wildlife Service · Public domain · Wikimedia Commons'),
  ('lotus-snakehead-whole','https://upload.wikimedia.org/wikipedia/commons/f/f5/Channa_striata.jpg','https://commons.wikimedia.org/wiki/File:Channa_striata.jpg','BEDO (Thailand) · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-river-prawn-small-1kg','https://upload.wikimedia.org/wikipedia/commons/a/a4/Macrobrachium_rosenbergii.jpg','https://commons.wikimedia.org/wiki/File:Macrobrachium_rosenbergii.jpg','Citron · CC BY-SA 3.0 · Wikimedia Commons'),
  ('lotus-seabass-whole-4-6kg','https://upload.wikimedia.org/wikipedia/commons/3/3a/Lates_calcarifer_%28Barramundi%29_selling.jpg','https://commons.wikimedia.org/wiki/File:Lates_calcarifer_(Barramundi)_selling.jpg','Salil Kumar Mukherjee · CC BY-SA 4.0 · Wikimedia Commons'),
  ('lotus-cockle-kg','https://upload.wikimedia.org/wikipedia/commons/9/9c/Anadara_granosa.jpg','https://commons.wikimedia.org/wiki/File:Anadara_granosa.jpg','Stefan · CC BY 2.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
