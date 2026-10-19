-- QueueGo Market open-license image approval batch 9.
-- Existing Wikimedia/Commons rights gate remains authoritative.

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('cabbage','https://upload.wikimedia.org/wikipedia/commons/6/62/A_cabbage_vegetables.jpg','https://commons.wikimedia.org/wiki/File:A_cabbage_vegetables.jpg','Salma kyari · CC0 1.0 · Wikimedia Commons'),
  ('makro-cabbage-500-700g','https://upload.wikimedia.org/wikipedia/commons/6/62/A_cabbage_vegetables.jpg','https://commons.wikimedia.org/wiki/File:A_cabbage_vegetables.jpg','Salma kyari · CC0 1.0 · Wikimedia Commons'),
  ('pork-belly','https://upload.wikimedia.org/wikipedia/commons/8/8c/Freshly_prepared_raw_pork_belly_with_red_onions_and_chili_peppers.jpg','https://commons.wikimedia.org/wiki/File:Freshly_prepared_raw_pork_belly_with_red_onions_and_chili_peppers.jpg','Shixart1985 · CC BY 2.0 · Wikimedia Commons'),
  ('makro-chicken-drumstick-1kg','https://upload.wikimedia.org/wikipedia/commons/2/29/Raw_chicken_drumsticks_%283312851753%29.jpg','https://commons.wikimedia.org/wiki/File:Raw_chicken_drumsticks_(3312851753).jpg','Geoff Peters · CC BY 2.0 · Wikimedia Commons'),
  ('egg-size-2-10','https://upload.wikimedia.org/wikipedia/commons/f/fb/Eggs_Chicken.jpg','https://commons.wikimedia.org/wiki/File:Eggs_Chicken.jpg','safaritravelplus · CC0 1.0 · Wikimedia Commons'),
  ('egg-size-3-10','https://upload.wikimedia.org/wikipedia/commons/f/fb/Eggs_Chicken.jpg','https://commons.wikimedia.org/wiki/File:Eggs_Chicken.jpg','safaritravelplus · CC0 1.0 · Wikimedia Commons'),
  ('makro-enoki-1kg','https://upload.wikimedia.org/wikipedia/commons/9/90/Enoki_mushroom.jpg','https://commons.wikimedia.org/wiki/File:Enoki_mushroom.jpg','Mitrofresh · CC BY-SA 4.0 · Wikimedia Commons'),
  ('lotus-enoki-500g','https://upload.wikimedia.org/wikipedia/commons/9/90/Enoki_mushroom.jpg','https://commons.wikimedia.org/wiki/File:Enoki_mushroom.jpg','Mitrofresh · CC BY-SA 4.0 · Wikimedia Commons'),
  ('dried-shiitake-small','https://upload.wikimedia.org/wikipedia/commons/e/ef/Dried_shiitake_mushrooms.jpg','https://commons.wikimedia.org/wiki/File:Dried_shiitake_mushrooms.jpg','François Nguyen · CC BY 2.0 · Wikimedia Commons'),
  ('dried-shiitake-large','https://upload.wikimedia.org/wikipedia/commons/e/ef/Dried_shiitake_mushrooms.jpg','https://commons.wikimedia.org/wiki/File:Dried_shiitake_mushrooms.jpg','François Nguyen · CC BY 2.0 · Wikimedia Commons'),
  ('makro-tomato-1kg','https://upload.wikimedia.org/wikipedia/commons/4/4d/Tomato_Fruit.jpg','https://commons.wikimedia.org/wiki/File:Tomato_Fruit.jpg','Judethedeus · CC0 1.0 · Wikimedia Commons'),
  ('makro-long-bean-500g','https://upload.wikimedia.org/wikipedia/commons/d/d6/Long_beans.jpg','https://commons.wikimedia.org/wiki/File:Long_beans.jpg','Paul Goyette · CC BY-SA 2.0 · Wikimedia Commons'),
  ('lotus-mackerel-kg','https://upload.wikimedia.org/wikipedia/commons/4/4c/Mackerel_fish.jpg','https://commons.wikimedia.org/wiki/File:Mackerel_fish.jpg','Hariprasad Shetty10 · CC0 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;
