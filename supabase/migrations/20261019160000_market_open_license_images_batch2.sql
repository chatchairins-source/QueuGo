-- QueueGo Market open-license image approval batch 2.
-- The preceding open-license migration establishes the strict Wikimedia/Commons rights gate.
-- This migration only approves exact/common commodity matches with explicit attribution.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('chinese-cabbage','https://upload.wikimedia.org/wikipedia/commons/2/24/Napa_Cabbage.jpg','https://commons.wikimedia.org/wiki/File:Napa_Cabbage.jpg','SarKaLay စာကလေး · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-chinese-cabbage-400-600g','https://upload.wikimedia.org/wikipedia/commons/2/24/Napa_Cabbage.jpg','https://commons.wikimedia.org/wiki/File:Napa_Cabbage.jpg','SarKaLay စာကလေး · CC BY-SA 4.0 · Wikimedia Commons'),
  ('holy-basil-leaves','https://upload.wikimedia.org/wikipedia/commons/0/02/Holy_Basil_plant_leaves.jpg','https://commons.wikimedia.org/wiki/File:Holy_Basil_plant_leaves.jpg','Satheesh Sankaran · CC BY 2.0 · Wikimedia Commons'),
  ('large-cavendish-banana','https://upload.wikimedia.org/wikipedia/commons/5/56/Two_ripe_Cavendish_bananas.jpg','https://commons.wikimedia.org/wiki/File:Two_ripe_Cavendish_bananas.jpg','Assumulator · CC BY-SA 3.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
