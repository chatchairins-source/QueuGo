-- QueueGo Market open-license image approval batch 8.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-import-carrot-1kg','https://upload.wikimedia.org/wikipedia/commons/4/4a/CARROT.jpg','https://commons.wikimedia.org/wiki/File:CARROT.jpg','Ranjithkumar Murugesan · CC0 1.0 · Wikimedia Commons'),
  ('thai-basil','https://upload.wikimedia.org/wikipedia/commons/0/05/Sweet_basil_or_ocimum_basilicum.jpg','https://commons.wikimedia.org/wiki/File:Sweet_basil_or_ocimum_basilicum.jpg','Lfatty22 · CC0 1.0 · Wikimedia Commons'),
  ('makro-coriander-300g','https://upload.wikimedia.org/wikipedia/commons/3/3d/Coriander_leaves.jpg','https://commons.wikimedia.org/wiki/File:Coriander_leaves.jpg','Azaadsameer3 · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-cauliflower-1kg','https://upload.wikimedia.org/wikipedia/commons/1/16/Cauliflower_in_Thailand.jpg','https://commons.wikimedia.org/wiki/File:Cauliflower_in_Thailand.jpg','Ronggy · CC0 1.0 · Wikimedia Commons'),
  ('makro-onion-1kg','https://upload.wikimedia.org/wikipedia/commons/2/2a/Yellow_onion_%F0%9F%A7%85.jpg','https://commons.wikimedia.org/wiki/File:Yellow_onion_%F0%9F%A7%85.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
