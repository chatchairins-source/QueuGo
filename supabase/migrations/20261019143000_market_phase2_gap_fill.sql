-- QueueGo Market phase 2 supplement: fill verified fish, shellfish, crab and longan gaps.
-- Public product facts only. No retailer price/cost/stock/barcode is imported.
-- image_source is a review reference; image remains NULL and publication stays pending.

insert into public.market_master_products(
  slug,name_th,category,suggested_unit,suggested_pack_size,source_unit,
  source_url,source_name,source_note,retrieved_on,
  image,image_status,image_source,image_rights_evidence,active
)
values
  ('lotus-soft-fish-kg','ปลาเนื้ออ่อน','ปลา','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/product/52253272','Lotus''s','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.lotuss.com/th/product/52253272',null,true),
  ('talaadthai-climbing-perch-small','ปลาหมอเลี้ยง เบอร์เล็ก','ปลา',null,null,'หน่วยขายไม่ระบุในหน้าสาธารณะ','https://talaadthai.com/products/freshwater-fish-9449-3139','ตลาดไท','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://talaadthai.com/products/freshwater-fish-9449-3139',null,true),
  ('makro-oyster-meat-100g','เนื้อหอยนางรม','หอย','กรัม',100,'100 ก.','https://www.makro.pro/th/p/835213-7275699273923','Makro PRO','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.makro.pro/th/p/835213-7275699273923',null,true),
  ('lotus-sweet-snail-kg','หอยหวานกลาง','หอย','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/product/52102814?openExternalBrowser=1','Lotus''s','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.lotuss.com/th/product/52102814?openExternalBrowser=1',null,true),
  ('makro-clam-1kg','เอโร่ หอยตลับแช่แข็ง 45-60 ตัว/กก.','หอย','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/p/843376-7167173689539','Makro PRO','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.makro.pro/th/p/843376-7167173689539',null,true),
  ('makro-scallop-half-shell-1kg','เอโร่ หอยเชลล์ติดไข่ครึ่งฝา 8-9 ซม.','หอย','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/p/143992-7078809567427','Makro PRO','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.makro.pro/th/p/143992-7078809567427',null,true),
  ('makro-soft-shell-crab-1kg','เอโร่ ปูนิ่มแช่แข็ง 7-9 ตัว/กก.','ปู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/p/174959-6974747115715','Makro PRO','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.makro.pro/th/p/174959-6974747115715',null,true),
  ('makro-blue-crab-large-1kg','ปูม้าขนาดใหญ่','ปู','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/p/168350-831006247598074','Makro PRO','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.makro.pro/th/p/168350-831006247598074',null,true),
  ('makro-longan-1kg','ลำไย','ผลไม้','กิโลกรัม',1,'1 กก.','https://www.makro.pro/th/c/fruit-vegetables/fruit/imported-fruit','Makro PRO','Public retailer/market product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.makro.pro/th/c/fruit-vegetables/fruit/imported-fruit',null,true)
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
  image_source=case
    when public.market_master_products.image_status='approved'
      then public.market_master_products.image_source
    else excluded.image_source
  end,
  active=true,
  updated_at=now();
