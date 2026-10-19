-- QueueGo Market open-license image approval batch 5.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-old-galangal-1kg','https://upload.wikimedia.org/wikipedia/commons/b/b2/Galangal.jpg','https://commons.wikimedia.org/wiki/File:Galangal.jpg','Liza Daly (ld31337) · Public domain · Wikimedia Commons'),
  ('makro-broccoli-10kg','https://upload.wikimedia.org/wikipedia/commons/9/98/Broccoli_vegetable.jpg','https://commons.wikimedia.org/wiki/File:Broccoli_vegetable.jpg','Jon Sullivan · Public domain · Wikimedia Commons'),
  ('makro-old-ginger-1kg','https://upload.wikimedia.org/wikipedia/commons/f/f2/Fresh_Ginger.JPG','https://commons.wikimedia.org/wiki/File:Fresh_Ginger.JPG','Miansari66 · Public domain · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
