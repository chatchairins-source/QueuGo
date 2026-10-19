-- QueueGo Market open-license image approval batch 3.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('spring-onion','https://upload.wikimedia.org/wikipedia/commons/c/ce/Bunches_of_spring_onions_1.jpg','https://commons.wikimedia.org/wiki/File:Bunches_of_spring_onions_1.jpg','Halima Waziri · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-cucumber-500g','https://upload.wikimedia.org/wikipedia/commons/0/0d/Small_cucumber_-_vegetable.jpg','https://commons.wikimedia.org/wiki/File:Small_cucumber_-_vegetable.jpg','Tshrinivasan · CC BY-SA 3.0 · Wikimedia Commons'),
  ('water-spinach','https://upload.wikimedia.org/wikipedia/commons/4/49/Ipomoea_aquatica.jpg','https://commons.wikimedia.org/wiki/File:Ipomoea_aquatica.jpg','Eric in SF · CC BY-SA 4.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
