-- QueueGo Market open-license image approval batch 10.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('choy-sum','https://upload.wikimedia.org/wikipedia/commons/a/a7/Choy-sum.jpg','https://commons.wikimedia.org/wiki/File:Choy-sum.jpg','Pokman817 · CC BY-SA 3.0 · Wikimedia Commons'),
  ('makro-choy-sum-500g','https://upload.wikimedia.org/wikipedia/commons/a/a7/Choy-sum.jpg','https://commons.wikimedia.org/wiki/File:Choy-sum.jpg','Pokman817 · CC BY-SA 3.0 · Wikimedia Commons'),
  ('makro-peeled-garlic-1kg','https://upload.wikimedia.org/wikipedia/commons/a/a7/Peeled_garlic_cloves.jpg','https://commons.wikimedia.org/wiki/File:Peeled_garlic_cloves.jpg','Fumikas Sagisavas · CC0 1.0 · Wikimedia Commons'),
  ('lotus-hom-banana-bunch','https://upload.wikimedia.org/wikipedia/commons/c/cf/Bunch_of_bananna.jpg','https://commons.wikimedia.org/wiki/File:Bunch_of_bananna.jpg','Fatimah Bello · CC0 1.0 · Wikimedia Commons'),
  ('makro-kinnaree-watermelon-4kg','https://upload.wikimedia.org/wikipedia/commons/1/10/A_water_melon_fruit.jpg','https://commons.wikimedia.org/wiki/File:A_water_melon_fruit.jpg','Salma kyari · CC0 1.0 · Wikimedia Commons'),
  ('makro-honey-murcott-orange-1kg','https://upload.wikimedia.org/wikipedia/commons/3/37/Orange_fruit_%28Citrus_%C3%97_sinensis%29.jpg','https://commons.wikimedia.org/wiki/File:Orange_fruit_(Citrus_%C3%97_sinensis).jpg','Kuttykrishnan Photos · CC0 1.0 · Wikimedia Commons'),
  ('lotus-hom-suwan-pineapple','https://upload.wikimedia.org/wikipedia/commons/e/ed/Pineapple_fruit.jpg','https://commons.wikimedia.org/wiki/File:Pineapple_fruit.jpg','Mibrahim74 · CC BY 3.0 · Wikimedia Commons'),
  ('makro-red-dragon-fruit-1kg','https://upload.wikimedia.org/wikipedia/commons/9/96/%22dragon_fruit.jpg','https://commons.wikimedia.org/wiki/File:%22dragon_fruit.jpg','Arivumathi · CC0 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
