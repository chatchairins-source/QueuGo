-- QueueGo Market open-license image approval batch 13.
-- Raw meat cuts only; existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-pork-shoulder-1kg','https://upload.wikimedia.org/wikipedia/commons/a/a8/Boston_butt.jpg','https://commons.wikimedia.org/wiki/File:Boston_butt.jpg','Mark Miller · CC BY-SA 4.0 · Wikimedia Commons'),
  ('lotus-beef-rib','https://upload.wikimedia.org/wikipedia/commons/5/5b/Bone-in_chuck_short_ribs.jpg','https://commons.wikimedia.org/wiki/File:Bone-in_chuck_short_ribs.jpg','Tim Evanson · CC BY-SA 2.0 · Wikimedia Commons'),
  ('lotus-beef-hip','https://upload.wikimedia.org/wikipedia/commons/9/9e/Beef_round_top_round_steak_in_pan%2C_raw.jpg','https://commons.wikimedia.org/wiki/File:Beef_round_top_round_steak_in_pan,_raw.jpg','Dbenbenn (David Benbennick) · CC BY-SA 3.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
