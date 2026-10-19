-- QueueGo Market phase 2 supplement: chaebow shrimp and mud crab.
-- Public product facts only. No retailer/market price, merchant cost, stock or barcode is imported.
-- image_source remains a review reference; image is NULL and publication remains pending.

insert into public.market_master_products(
  slug,name_th,category,suggested_unit,suggested_pack_size,source_unit,
  source_url,source_name,source_note,retrieved_on,
  image,image_status,image_source,image_rights_evidence,active
)
values
  ('lotus-shrimp-chaebow-25-30kg','กุ้งแชบ๊วย 25-30 ตัว/กก.','กุ้ง','กิโลกรัม',1,'กก.ละ','https://www.lotuss.com/th/product/52195167?openExternalBrowser=1','Lotus''s','Public retailer product fact; QueueGo does not import retailer price.','2026-10-19'::date,null,'pending','https://www.lotuss.com/th/product/52195167?openExternalBrowser=1',null,true),
  ('talaadthai-shrimp-chaebow-medium','กุ้งแชบ๊วย (เลี้ยง) เบอร์กลาง','กุ้ง','กิโลกรัม',1,'กก.','https://talaadthai.com/products/shrimp-9436-2887','ตลาดไท','Public wholesale-market product identity; QueueGo does not import market price.','2026-10-19'::date,null,'pending','https://talaadthai.com/products/shrimp-9436-2887',null,true),
  ('talaadthai-shrimp-chaebow-large','กุ้งแชบ๊วย (เลี้ยง) เบอร์ใหญ่','กุ้ง','กิโลกรัม',1,'กก.','https://talaadthai.com/products/shrimp-9437-3057','ตลาดไท','Public wholesale-market product identity; QueueGo does not import market price.','2026-10-19'::date,null,'pending','https://talaadthai.com/products/shrimp-9437-3057',null,true),
  ('talaadthai-mud-crab-small','ปูทะเล ขนาดเล็ก','ปู','กิโลกรัม',1,'กก.','https://talaadthai.com/products/4573','ตลาดไท','Public wholesale-market product identity; QueueGo does not import market price.','2026-10-19'::date,null,'pending','https://talaadthai.com/products/4573',null,true),
  ('talaadthai-mud-crab-medium','ปูทะเล ขนาดกลาง','ปู','กิโลกรัม',1,'กก.','https://talaadthai.com/products/4632','ตลาดไท','Public wholesale-market product identity; QueueGo does not import market price.','2026-10-19'::date,null,'pending','https://talaadthai.com/products/4632',null,true)
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
