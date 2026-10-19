-- QueueGo Market open-license image approval batch 16.
-- Exact raw/commodity matches only; existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-chicken-leg-quarter-1kg','https://upload.wikimedia.org/wikipedia/commons/d/dc/Raw_leg_chicken_quarters.jpg','https://commons.wikimedia.org/wiki/File:Raw_leg_chicken_quarters.jpg','Baoothersks · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-catfish-cut-1kg','https://upload.wikimedia.org/wikipedia/commons/1/1b/Catfish_Sliced.jpg','https://commons.wikimedia.org/wiki/File:Catfish_Sliced.jpg','Royalesignature · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-soft-shell-crab-1kg','https://upload.wikimedia.org/wikipedia/commons/8/8d/Soft-shell_crab_on_ice.jpg','https://commons.wikimedia.org/wiki/File:Soft-shell_crab_on_ice.jpg','Vrlobo888 · Public domain · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
