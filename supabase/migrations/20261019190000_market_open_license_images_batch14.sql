-- QueueGo Market open-license image approval batch 14.
-- Produce/fruit identities only; existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('kale-mixed','https://upload.wikimedia.org/wikipedia/commons/5/53/Kale_%28Green_Vegetable%29.jpg','https://commons.wikimedia.org/wiki/File:Kale_(Green_Vegetable).jpg','Catherine Amia · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-kale-500g','https://upload.wikimedia.org/wikipedia/commons/5/53/Kale_%28Green_Vegetable%29.jpg','https://commons.wikimedia.org/wiki/File:Kale_(Green_Vegetable).jpg','Catherine Amia · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-celery-300g','https://upload.wikimedia.org/wikipedia/commons/6/63/Mini_celery.jpg','https://commons.wikimedia.org/wiki/File:Mini_celery.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('makro-large-pumpkin-half-kg','https://upload.wikimedia.org/wikipedia/commons/a/a1/Pumpkinhalf-03_hg.jpg','https://commons.wikimedia.org/wiki/File:Pumpkinhalf-03_hg.jpg','Hannes Grobe · CC BY 4.0 · Wikimedia Commons'),
  ('makro-key-lime-10','https://upload.wikimedia.org/wikipedia/commons/4/43/Key_lime_%28Citrus_%C3%97_aurantiifolia%29_-_DSC09279.jpg','https://commons.wikimedia.org/wiki/File:Key_lime_(Citrus_%C3%97_aurantiifolia)_-_DSC09279.jpg','Daderot · CC0 1.0 · Wikimedia Commons'),
  ('lotus-emperor-lychee-kg','https://upload.wikimedia.org/wikipedia/commons/3/32/Lychee_Fruit.jpg','https://commons.wikimedia.org/wiki/File:Lychee_Fruit.jpg','Sanjay Acharya · CC BY-SA 4.0 · Wikimedia Commons'),
  ('makro-golden-rambutan-2-5kg','https://upload.wikimedia.org/wikipedia/commons/5/50/Rambutan_Fruit_%2831433%29.jpg','https://commons.wikimedia.org/wiki/File:Rambutan_Fruit_(31433).jpg','SarKaLay · CC0 1.0 · Wikimedia Commons'),
  ('makro-sunlady-cantaloupe-1kg','https://upload.wikimedia.org/wikipedia/commons/9/9a/Cantaloupe.jpg','https://commons.wikimedia.org/wiki/File:Cantaloupe.jpg','Renee Comet / National Cancer Institute · Public domain · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
