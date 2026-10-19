-- Reviewed species/commodity illustrations only; preserve source-backed size/pack facts.
with approved(slug,image_url,source_page,rights_evidence) as (
values
('lotus-shrimp-chaebow-25-30kg','https://upload.wikimedia.org/wikipedia/commons/c/ca/Jerbung_Pj_DSC_1970.jpg','https://commons.wikimedia.org/wiki/File:Jerbung_Pj_DSC_1970.jpg','Kembangraps · CC BY-SA 4.0 · Wikimedia Commons'),
('makro-white-sand-fish-1kg','https://upload.wikimedia.org/wikipedia/commons/f/fd/Sillago_sihama_Thailand.jpg','https://commons.wikimedia.org/wiki/File:Sillago_sihama_Thailand.jpg','Sahat Ratmuangkhwang · CC BY 3.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,image_status='approved',image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,updated_at=now()
from approved a where m.slug=a.slug;
