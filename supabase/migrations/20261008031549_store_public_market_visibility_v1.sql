create or replace function public.market_public_catalog_v2()
returns table(product_id uuid,shop_id uuid,market_id uuid,market_name text,shop_user_id uuid,shop_name text,shop_category text,shop_logo text,name text,category text,description text,image text,price numeric,unit text,pack_size numeric,available_packs numeric,weight_kg numeric)
language sql stable security definer set search_path=public,pg_temp as $$
 select p.id,p.shop_id,s.market_id,mkt.name,s.user_id,s.shop_name,s.public_category,s.public_logo,
 p.name,p.category,p.description,p.image,coalesce(p.delivery_price,p.price),mp.unit,mp.pack_size,
 floor(mp.stock_quantity/mp.pack_size),mp.item_weight_kg
 from public.market_products mp
 join public.products p on p.id=mp.product_id and p.shop_id=mp.shop_id
 join public.shop_profiles s on s.id=mp.shop_id
 join public.users u on u.id=s.user_id
 join public.markets mkt on mkt.id=s.market_id and mkt.active=true
 where s.status='active' and s.archived_at is null and s.onboarding_status='approved'
 and s.delivery_enabled=true and u.status='active' and s.market_membership_status='approved'
 and p.available and p.delivery_available and mp.stock_quantity>=mp.pack_size
 order by mkt.name,s.shop_name,p.name limit 2000
$$;
create or replace function public.market_public_shops_v2()
returns table(shop_id uuid,shop_user_id uuid,market_id uuid,market_name text,shop_name text,shop_category text,shop_logo text,shop_cover text,latitude double precision,longitude double precision,open_time text,close_time text,description text,delivery_enabled boolean)
language sql stable security definer set search_path=public,pg_temp as $$
 select s.id,s.user_id,s.market_id,m.name,s.shop_name,coalesce(s.public_category,'market'),
 coalesce(s.public_logo,''),coalesce(s.public_cover,''),s.latitude,s.longitude,
 s.public_open_time,s.public_close_time,coalesce(s.public_description,''),coalesce(s.delivery_enabled,false)
 from public.shop_profiles s join public.users u on u.id=s.user_id join public.markets m on m.id=s.market_id and m.active=true
 where s.status='active' and s.archived_at is null and s.onboarding_status='approved'
 and u.role='shop' and u.status='active' and s.market_membership_status='approved'
 order by m.name,s.shop_name
$$;
