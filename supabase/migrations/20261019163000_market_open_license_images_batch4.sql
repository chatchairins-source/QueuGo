-- QueueGo Market open-license image approval batch 4.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('makro-bird-chili-300g','https://upload.wikimedia.org/wikipedia/commons/1/1b/Thai_chillies_%28birds_eye%29_-_prik_kii_nu.jpg','https://commons.wikimedia.org/wiki/File:Thai_chillies_(birds_eye)_-_prik_kii_nu.jpg','Susan Slater · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-thai-eggplant-300g','https://upload.wikimedia.org/wikipedia/commons/8/8e/Thai_eggplant.jpg','https://commons.wikimedia.org/wiki/File:Thai_eggplant.jpg','F_A · CC BY 2.0 · Wikimedia Commons (Flickr license reviewed)'),
  ('makro-kaffir-lime-leaf-150g','https://upload.wikimedia.org/wikipedia/commons/5/58/C_hystrix_kaffir_lime_leaves.jpg','https://commons.wikimedia.org/wiki/File:C_hystrix_kaffir_lime_leaves.jpg','Jpbrigand2 · CC BY-SA 4.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
