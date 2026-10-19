-- QueueGo Market fresh catalog expansion from current public Makro PRO and Lotus's product facts.
-- Facts only: no retailer price/cost/stock is imported. Storefront price remains merchant-owned.
-- Product images remain null until QueueGo has approved source + reuse-rights evidence.

alter table public.market_master_products
  drop constraint if exists market_master_products_category_check;

alter table public.market_master_products
  add constraint market_master_products_category_check
  check (category in (
    'ผักสด','เห็ด','ผลไม้','เนื้อหมู','เนื้อวัว','ไก่ / เป็ด','ปลา','อาหารทะเล','ไข่',
    'เต้าหู้ / เส้นสด / ลูกชิ้น','ของสดพร้อมปรุง','อาหารแช่เย็น / แช่แข็ง',
    'พริกแกง / เครื่องแกง','เครื่องปรุง / ซอส','ข้าวสาร / ธัญพืช','ของแห้ง',
    'อาหารปรุงสำเร็จ','ขนม / ของหวาน','เครื่องดื่ม','ของใช้ในครัวเรือน',
    'ดอกไม้ / ของไหว้','อื่น ๆ'
  ));

insert into public.market_master_products(
  slug,name_th,category,suggested_unit,suggested_pack_size,source_unit,
  source_url,source_name,source_note,retrieved_on,image,image_status,image_source,image_rights_evidence,active
)
values
  ('makro-chicken-breast-skin-on-1kg','อกไก่เนื้อล้วนติดหนัง','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-chicken-leg-quarter-1kg','น่องติดสะโพกไก่','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-chicken-wing-stick-1kg','ปีกบนไก่','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-chicken-tenderloin-1kg','สันในไก่','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-chicken-drumstick-1kg','น่องไก่','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-chicken-middle-wing-1kg','ปีกกลางไก่','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-pork-hip-1kg','สะโพกหมู','เนื้อหมู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-pork-loin-1kg','สันนอกหมู','เนื้อหมู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-pork-minced-fat10-1kg','เนื้อหมูบด ผสมมัน 10%','เนื้อหมู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-pork-rib-slice-1kg','ซี่โครงหมูตัดเส้น','เนื้อหมู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-pork-shoulder-1kg','หัวไหล่หมู','เนื้อหมู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Fresh%20for%20Business%20%281%20Jul%20-%2031%20Jul%2026%29/39903','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-duck-minced-gizzard-1kg','เนื้อเป็ดบดผสมกึ๋น','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/p/928220-357229728318241','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-duck-whole-s-1kg','ซีพี เป็ดสดอนามัย ขนาด S','ไก่ / เป็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/p/552500-921664243522588','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-duck-whole-m','ซีพี เป็ดสดอนามัย ไซส์ M','ไก่ / เป็ด','กิโลกรัม',1,'กก.ละ; น้ำหนักตัวประมาณ 2.4-2.6 กก.','https://www.lotuss.com/th/product/51262018?openExternalBrowser=1','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-duck-whole-xl','ซีพี เป็ดสดอนามัย XL','ไก่ / เป็ด','กิโลกรัม',1,'กก.ละ; น้ำหนักตัวประมาณ 2.8-3.0 กก.','https://www.lotuss.com/th/product/51467654','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-beef-sirloin','เนื้อสันนอกโคขุน','เนื้อวัว','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/product/75591299?openExternalBrowser=1','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-beef-shank','เนื้อน่องโคขุน','เนื้อวัว','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/product/75591305','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-beef-rib','เนื้อติดซี่โครงโคขุน','เนื้อวัว','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/product/75591312','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-beef-hip','เนื้อสะโพกวัว','เนื้อวัว','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/smartx/category/meat/beef','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-enoki-1kg','เห็ดเข็มทอง','เห็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-white-shimeji-1kg','เห็ดชิเมจิขาว','เห็ด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/en/c/collections/Gold%20Container%20-%20Fresh%20Value%20Deal%20%289%20Jul%202026%29/40637','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-king-oyster-500g','เห็ดนางรมหลวง','เห็ด','กรัม',500,'500 กรัม','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-fresh-shiitake-100g','เห็ดหอมสด','เห็ด','กรัม',100,'100 กรัม','https://www.lotuss.com/th/product/75713235','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('lotus-enoki-500g','โลตัส เห็ดเข็มทอง','เห็ด','กรัม',500,'500 กรัม','https://www.lotuss.com/th/shop/fresh-food?sort=relevance%3ADESC','Lotus''s','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-import-carrot-1kg','แครอทนอก จีน/เวียดนาม','ผักสด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-chinese-cabbage-400-600g','ผักกาดขาวปลี','ผักสด','กรัม',500,'400-600 กรัม/ชิ้น','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-cabbage-500-700g','กะหล่ำปลีแพ็ค','ผักสด','กรัม',600,'500-700 กรัม','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Pure%20Veg%20Pure%20Protein%20%288%20-%2014%20Jun%2026%29/38448','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-cucumber-500g','แตงกวา','ผักสด','กรัม',500,'500 กรัม','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-kale-500g','ผักคะน้า','ผักสด','กรัม',500,'500 กรัม','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-choy-sum-500g','ผักกวางตุ้งไต้หวัน','ผักสด','กรัม',500,'500 กรัม','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-peeled-garlic-1kg','กระเทียมสดปอกเปลือก','ผักสด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-tomato-1kg','มะเขือเทศท้อ','ผักสด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true),
  ('makro-onion-1kg','หอมใหญ่นอก','ผักสด','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/collections/Fresh%20and%20Frozen%20%7C%20Perfect%20Pair%20Thai%20Beef%20%26%20Veggies%20%2825%20-%2030%20Sep%202026%29/45215','Makro PRO','Public retail fresh-product fact; QueueGo does not import the retailer price.','2026-10-19'::date,null,'pending',null,null,true)
on conflict(slug) do update set
  name_th=excluded.name_th,
  category=excluded.category,
  suggested_unit=excluded.suggested_unit,
  suggested_pack_size=excluded.suggested_pack_size,
  source_unit=excluded.source_unit,
  source_url=excluded.source_url,
  source_name=excluded.source_name,
  source_note=excluded.source_note,
  retrieved_on=excluded.retrieved_on,
  active=true,
  updated_at=now();
