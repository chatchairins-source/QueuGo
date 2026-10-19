-- QueueGo Market open-license image approval batch 11.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('lotus-fresh-shiitake-100g','https://upload.wikimedia.org/wikipedia/commons/5/5a/Fresh_shiitake_mushrooms.jpg','https://commons.wikimedia.org/wiki/File:Fresh_shiitake_mushrooms.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('makro-white-shimeji-1kg','https://upload.wikimedia.org/wikipedia/commons/b/b7/Shimeji_mushroom.jpg','https://commons.wikimedia.org/wiki/File:Shimeji_mushroom.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('lotus-beef-shank','https://upload.wikimedia.org/wikipedia/commons/4/49/Beef_shank.jpg','https://commons.wikimedia.org/wiki/File:Beef_shank.jpg','Dragontail ys · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-pork-loin-1kg','https://upload.wikimedia.org/wikipedia/commons/5/59/Pork_loin.jpg','https://commons.wikimedia.org/wiki/File:Pork_loin.jpg','Pannet · CC BY 4.0 · Wikimedia Commons'),
  ('makro-lemongrass-1kg','https://upload.wikimedia.org/wikipedia/commons/f/fe/Lemongrass_Fresh.jpg','https://commons.wikimedia.org/wiki/File:Lemongrass_Fresh.jpg','SarKaLay · CC0 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
