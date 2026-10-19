begin;

alter table public.market_master_products
  drop constraint if exists market_master_products_market_category_only;

alter table public.market_master_products
  add constraint market_master_products_market_category_only
  check (
    category in (
      'ผักสด',
      'เห็ด',
      'ผลไม้',
      'เนื้อหมู',
      'เนื้อวัว',
      'ไก่ / เป็ด',
      'ปลา',
      'กุ้ง',
      'ปู',
      'หอย',
      'ปลาหมึก',
      'อาหารทะเล',
      'ไข่',
      'เต้าหู้ / เส้นสด / ลูกชิ้น',
      'ของสดพร้อมปรุง',
      'ของแห้ง',
      'พริกแกง / เครื่องแกง',
      'ดอกไม้ / ของไหว้'
    )
  );

commit;
