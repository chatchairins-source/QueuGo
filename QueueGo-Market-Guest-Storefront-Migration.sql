begin;
grant select(id,shop_name,status,public_category,public_logo,public_cover,public_open_time,public_close_time,public_description,address,latitude,longitude,delivery_enabled) on public.shop_profiles to anon;
grant select(id,shop_id,name,description,price,delivery_price,image,available,delivery_available) on public.products to anon;
commit;
