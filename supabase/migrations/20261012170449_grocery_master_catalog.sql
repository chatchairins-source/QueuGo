-- Grocery master data, linked to existing products: no second checkout/GP/POS system.
create table public.grocery_catalog_categories(category text primary key);
insert into public.grocery_catalog_categories(category) values ('น้ำดื่ม'),('น้ำอัดลม'),('เครื่องดื่มชูกำลัง'),('กาแฟพร้อมดื่ม'),('ชา'),('นม'),('นมเปรี้ยว'),('น้ำผลไม้'),('บะหมี่กึ่งสำเร็จรูป'),('ข้าวสาร'),('น้ำมันพืช'),('น้ำปลา'),('ซอส'),('ซีอิ๊ว'),('เครื่องปรุง'),('น้ำตาล'),('เกลือ'),('ไข่'),('ขนมขบเคี้ยว'),('ลูกอม'),('หมากฝรั่ง'),('คุกกี้'),('ขนมปัง'),('อาหารกระป๋อง'),('ปลากระป๋อง'),('ของแห้ง'),('ทิชชู่'),('กระดาษชำระ'),('สบู่'),('ยาสระผม'),('ยาสีฟัน'),('แปรงสีฟัน'),('ผงซักฟอก'),('น้ำยาซักผ้า'),('น้ำยาปรับผ้านุ่ม'),('น้ำยาล้างจาน'),('น้ำยาทำความสะอาด'),('ถุงขยะ'),('ของใช้เด็ก'),('ผ้าอนามัย'),('ของใช้ส่วนตัว'),('ของใช้ในบ้าน'),('สินค้าจิปาถะ'),('กาแฟและผงชงดื่ม'),('เครื่องดื่มอื่น ๆ');
alter table public.grocery_catalog_categories enable row level security;
revoke all on public.grocery_catalog_categories from public,anon,authenticated;
grant select,insert,update on public.grocery_catalog_categories to authenticated;
create policy grocery_category_read on public.grocery_catalog_categories for select to authenticated using (true);
create policy grocery_category_admin_insert on public.grocery_catalog_categories for insert to authenticated with check(public.is_active_admin());
create policy grocery_category_admin_update on public.grocery_catalog_categories for update to authenticated using(public.is_active_admin()) with check(public.is_active_admin());

create table public.grocery_master_products (
 id uuid primary key default gen_random_uuid(), barcode text unique,
 brand text not null, product_name_th text not null, product_name_en text,
 variant text not null default '', size_value numeric not null check(size_value>0),
 size_unit text not null check(size_unit in ('ml','g','piece','egg','sheet')),
 pack_size integer not null default 1 check(pack_size between 1 and 10000),sales_unit text not null,
 category text not null references public.grocery_catalog_categories(category), subcategory text not null default '', normalized_key text not null unique,
 image text, image_status text not null default 'pending' check(image_status in ('pending','approved')),
 image_source text, image_rights_evidence text,
 active boolean not null default false, merged_into uuid references public.grocery_master_products(id),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 check(length(brand) between 1 and 250 and length(product_name_th) between 1 and 250 and length(category) between 1 and 250),
 check(barcode is null or barcode ~ '^\d{14}$'),
 check(image_status='pending' and image is null or image_status='approved' and image like 'https://pkypiqhlrmzocysgeqew.supabase.co/storage/v1/object/public/%' and length(image_rights_evidence)>10),
 check(merged_into is null or merged_into<>id)
);
create table public.grocery_master_sources (
 id uuid primary key default gen_random_uuid(),master_product_id uuid not null references public.grocery_master_products(id),
 source_url text not null check(source_url like 'https://%'),source_type text not null check(source_type in ('manufacturer','distributor','retailer','government','merchant')),
 source_product_id text,retrieved_at timestamptz not null, first_seen_at timestamptz not null default now(),
 unique(master_product_id,source_url)
);
alter table public.products add column grocery_master_id uuid references public.grocery_master_products(id),
 add column grocery_enabled boolean not null default false,
 add column grocery_selling_price numeric check(grocery_selling_price>0 and grocery_selling_price<=999999),
 add column grocery_promo_price numeric check(grocery_promo_price>0),
 add column grocery_stock_tracking boolean not null default false;
create unique index products_grocery_shop_master on public.products(shop_id,grocery_master_id) where grocery_master_id is not null;
create index grocery_master_category_page on public.grocery_master_products(category,product_name_th,id) where active;
create index grocery_master_search on public.grocery_master_products using gin(to_tsvector('simple',brand||' '||product_name_th||' '||coalesce(product_name_en,'')||' '||variant));
create index grocery_sources_product on public.grocery_master_sources(master_product_id);
alter table public.grocery_master_products enable row level security;
alter table public.grocery_master_sources enable row level security;
revoke all on public.grocery_master_products,public.grocery_master_sources from public,anon,authenticated;
grant select,insert,update on public.grocery_master_products,public.grocery_master_sources to authenticated;
create policy grocery_master_read on public.grocery_master_products for select to authenticated using (public.is_active_admin() or (active and merged_into is null and public.get_my_role()='shop'));
create policy grocery_master_admin_insert on public.grocery_master_products for insert to authenticated with check(public.is_active_admin());
create policy grocery_master_admin_update on public.grocery_master_products for update to authenticated using(public.is_active_admin()) with check(public.is_active_admin());
create policy grocery_source_admin_read on public.grocery_master_sources for select to authenticated using(public.is_active_admin());
create policy grocery_source_admin_insert on public.grocery_master_sources for insert to authenticated with check(public.is_active_admin());
create policy grocery_source_admin_update on public.grocery_master_sources for update to authenticated using(public.is_active_admin()) with check(public.is_active_admin());

create function public.qg_grocery_gtin(p text) returns text language plpgsql immutable set search_path=public,pg_temp as $$
declare s text:=regexp_replace(coalesce(p,''),'[ -]','','g'); n integer:=0;w integer:=3;i integer;
begin
 if s='' then return null;end if;
 if s !~ '^(\d{8}|\d{12}|\d{13}|\d{14})$' then raise exception 'invalid GTIN length';end if;
 for i in reverse length(s)-1..1 loop n:=n+substring(s,i,1)::integer*w;w:=4-w;end loop;
 if (10-n%10)%10<>right(s,1)::integer then raise exception 'invalid GTIN check digit';end if;
 return lpad(s,14,'0');
end $$;
create function public.qg_grocery_fold(p text) returns text language sql immutable set search_path=public,pg_temp as $$
 select regexp_replace(lower(translate(replace(normalize(coalesce(p,''),NFKC),'ํา','ำ'),'๐๑๒๓๔๕๖๗๘๙','0123456789')),'[\s.\-_]','','g');
$$;
create function public.qg_grocery_identity_key(b text,n text,v text,s numeric,u text,p integer,sale text) returns text language plpgsql immutable set search_path=public,pg_temp as $$
declare brand text:=public.qg_grocery_fold(b);name text:=public.qg_grocery_fold(n);alias text;
begin
 if brand in ('coke','โค้ก','cocacola') then
  brand:='coca-cola';
  foreach alias in array array['cocacola','coke','โค้ก'] loop if name like alias||'%' then name:=substring(name,length(alias)+1);exit;end if;end loop;
  if name in ('','น้ำอัดลม','cola') then name:='cola';end if;
 elsif brand in ('มาม่า','mama') then brand:='mama';foreach alias in array array['มาม่า','mama'] loop if name like alias||'%' then name:=substring(name,length(alias)+1);exit;end if;end loop;
 end if;
 return brand||'|'||name||'|'||public.qg_grocery_fold(v)||'|'||trim(trailing '.' from trim(trailing '0' from to_char(s,'FM999999999990.999')))||'|'||u||'|'||p::text||'|'||public.qg_grocery_fold(sale);
end $$;
create function public.qg_grocery_master_guard() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 new.barcode:=public.qg_grocery_gtin(new.barcode);
 new.brand:=trim(regexp_replace(replace(normalize(new.brand,NFKC),'ํา','ำ'),'\s+',' ','g'));
 new.product_name_th:=trim(regexp_replace(replace(normalize(new.product_name_th,NFKC),'ํา','ำ'),'\s+',' ','g'));
 new.normalized_key:=public.qg_grocery_identity_key(new.brand,new.product_name_th,new.variant,new.size_value,new.size_unit,new.pack_size,new.sales_unit);
 new.updated_at:=now();
 if new.image_status='pending' then new.image:=null;end if;
 return new;
end $$;
create trigger grocery_master_guard before insert or update on public.grocery_master_products for each row execute function public.qg_grocery_master_guard();

-- Trigger needs privileged master lookup for POS/customer stock mutations and disabled masters.
-- Not a callable public RPC; no table writes other than NEW, no caller-supplied SQL.
create function public.qg_grocery_product_guard() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare m public.grocery_master_products;v_changed boolean;
begin
 if tg_op='UPDATE' and old.grocery_master_id is not null and new.grocery_master_id is distinct from old.grocery_master_id and not public.is_active_admin() then raise exception 'master identity is locked';end if;
 if new.grocery_master_id is null then return new;end if;
 if not exists(select 1 from public.shop_profiles where id=new.shop_id and public_category='grocery' and archived_at is null) then raise exception 'grocery shop required';end if;
 select * into strict m from public.grocery_master_products where id=new.grocery_master_id;
 if m.merged_into is not null then raise exception 'master is merged';end if;
 if new.grocery_promo_price is not null and (new.grocery_selling_price is null or new.grocery_promo_price>new.grocery_selling_price) then raise exception 'promo exceeds selling price';end if;
 if new.stock<0 then raise exception 'negative stock';end if;
 -- Available is derived on every write, including stock decrements by the existing POS.
 new.name:=m.product_name_th||case when m.variant='' then '' else ' '||m.variant end||' '||m.size_value::text||' '||m.size_unit||case when m.pack_size=1 then '' else ' x '||m.pack_size end;
 new.category:=m.category;new.image:=case when m.image_status='approved' then m.image else null end;
 new.price:=coalesce(new.grocery_promo_price,new.grocery_selling_price,0);new.pos_price:=new.price;
 new.available:=m.active and new.grocery_enabled and new.grocery_selling_price>0 and (not new.grocery_stock_tracking or new.stock>0);
 new.available:=coalesce(new.available,false);new.updated_at:=now();
 return new;
end $$;
-- Alphabetical ordering: runs before existing delivery restriction and GP price triggers.
create trigger a_grocery_product_guard before insert or update on public.products for each row execute function public.qg_grocery_product_guard();
create function public.qg_grocery_master_fanout() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if new.merged_into is null then update public.products set grocery_master_id=new.id where grocery_master_id=new.id;end if;
 return null;
end $$;
create trigger grocery_master_fanout after update on public.grocery_master_products for each row execute function public.qg_grocery_master_fanout();

create function public.qg_grocery_import(p_rows jsonb) returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare r jsonb;m uuid;b text;by_key uuid;by_barcode uuid;v_n integer:=0;
begin
 if not public.is_active_admin() then raise exception 'active admin required' using errcode='42501';end if;
 if jsonb_typeof(p_rows)<>'array' or jsonb_array_length(p_rows) not between 1 and 200 then raise exception 'batch must contain 1..200 rows';end if;
 -- Serialize imports + merge; unique keys still enforce concurrency at the table boundary.
 perform pg_advisory_xact_lock(hashtext('queuego.grocery.catalog'));
 for r in select value from jsonb_array_elements(p_rows) loop
  if coalesce(r->>'normalized_key','')='' or coalesce(r->>'source_url','') not like 'https://%' or coalesce(r->>'retrieved_at','')='' then raise exception 'malformed provenance/key';end if;
  b:=public.qg_grocery_gtin(r->>'barcode');
  r:=jsonb_set(r,'{normalized_key}',to_jsonb(public.qg_grocery_identity_key(r->>'brand',r->>'product_name_th',coalesce(r->>'variant',''),(r->>'size_value')::numeric,r->>'size_unit',(r->>'pack_size')::integer,r->>'sales_unit')));
  select id into by_key from public.grocery_master_products where normalized_key=r->>'normalized_key';
  select id into by_barcode from public.grocery_master_products where barcode=b;
  if by_key is not null and by_barcode is not null and by_key<>by_barcode then raise exception 'identity conflict: admin merge required';end if;
  m:=coalesce(by_barcode,by_key);
  if m is null then
   insert into public.grocery_master_products(barcode,brand,product_name_th,product_name_en,variant,size_value,size_unit,pack_size,sales_unit,category,subcategory,normalized_key,active)
   values(b,r->>'brand',r->>'product_name_th',r->>'product_name_en',coalesce(r->>'variant',''),(r->>'size_value')::numeric,r->>'size_unit',(r->>'pack_size')::integer,r->>'sales_unit',r->>'category',coalesce(r->>'subcategory',''),r->>'normalized_key',false) returning id into m;
  elsif exists(select 1 from public.grocery_master_products where id=m and merged_into is not null) then raise exception 'merged identity requires canonical target';
  elsif b is not null then
   if exists(select 1 from public.grocery_master_products where id=m and barcode is not null and barcode<>b) then raise exception 'different barcode for same normalized key';end if;
   update public.grocery_master_products set barcode=b where id=m and barcode is null;
  end if;
  insert into public.grocery_master_sources(master_product_id,source_url,source_type,source_product_id,retrieved_at)
  values(m,r->>'source_url',r->>'source_type',r->>'source_product_id',(r->>'retrieved_at')::timestamptz)
  on conflict(master_product_id,source_url) do update set retrieved_at=greatest(grocery_master_sources.retrieved_at,excluded.retrieved_at);
  v_n:=v_n+1;
 end loop;
 return jsonb_build_object('processed',v_n,'activation','Admin must verify and activate new products');
end $$;

-- Private durable receipts: stores cannot forge successful receipts through the Data API.
create schema grocery_private;
revoke all on schema grocery_private from public,anon,authenticated;
grant usage on schema grocery_private to authenticated;
create table grocery_private.store_save_receipts(shop_id uuid not null references public.shop_profiles(id),request_id uuid not null,request_body jsonb not null,result jsonb not null,created_at timestamptz not null default now(),primary key(shop_id,request_id));
alter table grocery_private.store_save_receipts enable row level security;
revoke all on grocery_private.store_save_receipts from public,anon,authenticated;
create function grocery_private.store_save(p_shop_id uuid,p_rows jsonb,p_request_id uuid) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare r jsonb;v_n integer:=0;receipt grocery_private.store_save_receipts;current_stamp timestamptz;result jsonb;
begin
 if auth.uid() is null or public.get_my_role()<>'shop' or not exists(select 1 from public.shop_profiles where id=p_shop_id and user_id=public.get_my_user_id() and public_category='grocery' and archived_at is null) then raise exception 'own grocery shop required' using errcode='42501';end if;
 if jsonb_typeof(p_rows)<>'array' or jsonb_array_length(p_rows) not between 1 and 100 then raise exception 'batch must contain 1..100 rows';end if;
 if p_request_id is null then raise exception 'request ID required';end if;
 perform pg_advisory_xact_lock(hashtext(p_shop_id::text||p_request_id::text));
 select * into receipt from grocery_private.store_save_receipts where shop_id=p_shop_id and request_id=p_request_id;
 if found then if receipt.request_body is distinct from p_rows then raise exception 'request ID body mismatch';end if;return receipt.result;end if;
 for r in select value from jsonb_array_elements(p_rows) order by value->>'master_product_id' loop
  if not exists(select 1 from public.grocery_master_products where id=(r->>'master_product_id')::uuid and active and merged_into is null) then raise exception 'master unavailable';end if;
  select updated_at into current_stamp from public.products where shop_id=p_shop_id and grocery_master_id=(r->>'master_product_id')::uuid for update;
  if current_stamp is distinct from nullif(r->>'expected_updated_at','')::timestamptz then raise exception 'grocery listing changed; refresh before saving';end if;
  insert into public.products(shop_id,grocery_master_id,grocery_enabled,grocery_selling_price,grocery_promo_price,stock,grocery_stock_tracking)
  values(p_shop_id,(r->>'master_product_id')::uuid,coalesce((r->>'enabled')::boolean,false),nullif(r->>'selling_price','')::numeric,nullif(r->>'promo_price','')::numeric,coalesce((r->>'stock_qty')::integer,0),coalesce((r->>'stock_tracking')::boolean,false))
  on conflict(shop_id,grocery_master_id) where grocery_master_id is not null do update set
  price=excluded.price,pos_price=excluded.pos_price,grocery_enabled=excluded.grocery_enabled,grocery_selling_price=excluded.grocery_selling_price,grocery_promo_price=excluded.grocery_promo_price,stock=excluded.stock,grocery_stock_tracking=excluded.grocery_stock_tracking;
  v_n:=v_n+1;
 end loop;
 result:=jsonb_build_object('saved',v_n);
 insert into grocery_private.store_save_receipts(shop_id,request_id,request_body,result) values(p_shop_id,p_request_id,p_rows,result);
 return result;
end $$;

revoke all on function grocery_private.store_save(uuid,jsonb,uuid) from public,anon;
grant execute on function grocery_private.store_save(uuid,jsonb,uuid) to authenticated;
create function public.qg_grocery_store_save(p_shop_id uuid,p_rows jsonb,p_request_id uuid) returns jsonb language sql security invoker set search_path=public,pg_temp as $$
 select grocery_private.store_save(p_shop_id,p_rows,p_request_id);
$$;

create function public.qg_grocery_merchant_page(p_shop_id uuid,p_query text default '',p_category text default '',p_mode text default 'all',p_offset integer default 0) returns jsonb language plpgsql stable security invoker set search_path=public,pg_temp as $$
declare result jsonb;
begin
 if public.get_my_role()<>'shop' or not exists(select 1 from public.shop_profiles where id=p_shop_id and user_id=public.get_my_user_id() and public_category='grocery') then raise exception 'own grocery shop required';end if;
 if p_offset<0 or length(p_query)>100 or p_mode not in ('all','on','off','unpriced') then raise exception 'invalid query';end if;
 select coalesce(jsonb_agg(to_jsonb(x)),'[]') into result from (
 select m.id,m.brand,m.product_name_th,m.variant,m.size_value,m.size_unit,m.pack_size,m.sales_unit,m.category,m.image,m.image_status,
 p.id product_id,p.updated_at product_updated_at,coalesce(p.grocery_enabled,false) enabled,p.grocery_selling_price selling_price,p.grocery_promo_price promo_price,coalesce(p.stock,0) stock_qty,coalesce(p.grocery_stock_tracking,false) stock_tracking
 from public.grocery_master_products m left join public.products p on p.grocery_master_id=m.id and p.shop_id=p_shop_id
 where m.active and m.merged_into is null and (p_category='' or m.category=p_category)
 and (p_query='' or to_tsvector('simple',m.brand||' '||m.product_name_th||' '||coalesce(m.product_name_en,'')||' '||m.variant) @@ plainto_tsquery('simple',p_query) or m.product_name_th ilike '%'||p_query||'%' or m.brand ilike '%'||p_query||'%')
 and (p_mode='all' or p_mode='on' and p.grocery_enabled or p_mode='off' and not coalesce(p.grocery_enabled,false) or p_mode='unpriced' and p.grocery_selling_price is null)
 order by m.product_name_th,m.id limit 51 offset p_offset) x;
 return result;
end $$;
create function public.qg_grocery_categories() returns table(category text,n bigint) language sql stable security invoker set search_path=public,pg_temp as $$
 select c.category,count(m.id) from public.grocery_catalog_categories c left join public.grocery_master_products m on m.category=c.category and m.active and m.merged_into is null group by c.category order by c.category;
$$;

create function public.qg_grocery_merge(p_from uuid,p_into uuid,p_reason text) returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare m public.grocery_master_products;n integer;
begin
 if not public.is_active_admin() then raise exception 'active admin required';end if;
 if p_from=p_into or length(trim(p_reason))<5 then raise exception 'target and reason required';end if;
 perform pg_advisory_xact_lock(hashtext('queuego.grocery.catalog'));
 perform 1 from public.grocery_master_products where id in (p_from,p_into) order by id for update;
 select * into strict m from public.grocery_master_products where id=p_into and merged_into is null;
 if not exists(select 1 from public.grocery_master_products where id=p_from and merged_into is null) then
  if exists(select 1 from public.grocery_master_products where id=p_from and merged_into=p_into) then return jsonb_build_object('merged',true,'retry',true);end if;
  raise exception 'source not canonical';end if;
 -- Colliding store listings need owner review; never silently choose a price/stock or delete product/order history.
 if exists(select 1 from public.products a join public.products b using(shop_id) where a.grocery_master_id=p_from and b.grocery_master_id=p_into) then raise exception 'store collision: resolve listings before merge';end if;
 update public.products set grocery_master_id=p_into where grocery_master_id=p_from;get diagnostics n=row_count;
 insert into public.grocery_master_sources(master_product_id,source_url,source_type,source_product_id,retrieved_at)
 select p_into,source_url,source_type,source_product_id,retrieved_at from public.grocery_master_sources where master_product_id=p_from on conflict(master_product_id,source_url) do nothing;
 update public.grocery_master_products set active=false,merged_into=p_into where id=p_from;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(public.get_my_user_id(),'grocery_merge','grocery_master_products',p_from,p_reason,jsonb_build_object('target',p_into,'stores',n));
 return jsonb_build_object('merged',true,'stores',n);
end $$;
create trigger grocery_master_audit after insert or update on public.grocery_master_products for each row execute function public.qg_admin_change_audit();
revoke all on function public.qg_grocery_product_guard(),public.qg_grocery_master_fanout(),public.qg_grocery_master_guard() from public,anon,authenticated;
revoke all on function public.qg_grocery_import(jsonb),public.qg_grocery_store_save(uuid,jsonb,uuid),public.qg_grocery_merchant_page(uuid,text,text,text,integer),public.qg_grocery_categories(),public.qg_grocery_merge(uuid,uuid,text),public.qg_grocery_gtin(text) from public,anon;
grant execute on function public.qg_grocery_import(jsonb),public.qg_grocery_store_save(uuid,jsonb,uuid),public.qg_grocery_merchant_page(uuid,text,text,text,integer),public.qg_grocery_categories(),public.qg_grocery_merge(uuid,uuid,text),public.qg_grocery_gtin(text) to authenticated;

create function public.qg_grocery_category_toggle(p_shop_id uuid,p_category text,p_enabled boolean) returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare n integer;
begin
 if public.get_my_role()<>'shop' or not exists(select 1 from public.shop_profiles where id=p_shop_id and user_id=public.get_my_user_id() and public_category='grocery' and archived_at is null) then raise exception 'own grocery shop required';end if;
 if coalesce(p_category,'')='' or p_enabled is null then raise exception 'category and state required';end if;
 insert into public.products(shop_id,grocery_master_id,grocery_enabled)
 select p_shop_id,id,p_enabled from public.grocery_master_products where active and merged_into is null and category=p_category
 on conflict(shop_id,grocery_master_id) where grocery_master_id is not null do update set grocery_enabled=excluded.grocery_enabled,pos_price=products.pos_price;
 get diagnostics n=row_count;
 return jsonb_build_object('updated',n);
end $$;
create function public.qg_grocery_admin_page(p_query text default '',p_offset integer default 0) returns jsonb language plpgsql stable security invoker set search_path=public,pg_temp as $$
declare result jsonb;
begin
 if not public.is_active_admin() then raise exception 'active admin required';end if;
 if p_offset<0 or length(p_query)>100 then raise exception 'invalid query';end if;
 select coalesce(jsonb_agg(to_jsonb(x)),'[]') into result from (
 select m.*,(select count(*) from public.products p where p.grocery_master_id=m.id) stores,
 (select coalesce(jsonb_agg(to_jsonb(s)),'[]') from public.grocery_master_sources s where master_product_id=m.id) sources
 from public.grocery_master_products m where merged_into is null
 and (p_query='' or product_name_th ilike '%'||p_query||'%' or brand ilike '%'||p_query||'%' or barcode=p_query)
 order by product_name_th,id limit 51 offset p_offset) x;
 return result;
end $$;
revoke all on function public.qg_grocery_category_toggle(uuid,text,boolean),public.qg_grocery_admin_page(text,integer) from public,anon;
grant execute on function public.qg_grocery_category_toggle(uuid,text,boolean),public.qg_grocery_admin_page(text,integer) to authenticated;

revoke all on function public.qg_grocery_fold(text),public.qg_grocery_identity_key(text,text,text,numeric,text,integer,text) from public,anon;
grant execute on function public.qg_grocery_fold(text),public.qg_grocery_identity_key(text,text,text,numeric,text,integer,text) to authenticated;

-- Reserve tracked Grocery stock atomically in the same existing order transaction.
-- This table is private to non-callable triggers, not another order/payment system.
create table public.grocery_stock_reservations (
 item_id uuid primary key,order_id uuid not null references public.orders(id),product_id uuid not null references public.products(id),
 quantity integer not null check(quantity>=0),released boolean not null default false,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now()
);
create index grocery_stock_order on public.grocery_stock_reservations(order_id) where not released;
alter table public.grocery_stock_reservations enable row level security;
revoke all on public.grocery_stock_reservations from public,anon,authenticated;
create function public.qg_grocery_item_stock() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare p public.products;o public.orders;r public.grocery_stock_reservations;item uuid;order_key uuid;product uuid;qty integer;delta integer;
begin
 item:=case when tg_op='DELETE' then old.id else new.id end;
 order_key:=case when tg_op='DELETE' then old.order_id else new.order_id end;
 product:=case when tg_op='DELETE' then old.product_id else new.product_id end;
 if tg_op='UPDATE' and (new.product_id is distinct from old.product_id or new.order_id is distinct from old.order_id) and (exists(select 1 from public.grocery_stock_reservations where item_id=item) or exists(select 1 from public.products where id in (new.product_id,old.product_id) and grocery_master_id is not null)) then raise exception 'grocery order item identity is locked';end if;
 if not exists(select 1 from public.products where id=product and grocery_master_id is not null) then return case when tg_op='DELETE' then old else new end;end if;
 select * into o from public.orders where id=order_key for update;
 select * into p from public.products where id=product for update;
 if not found or p.grocery_master_id is null then return case when tg_op='DELETE' then old else new end;end if;
 -- Lock order before reservation changes; cancellation holds this same order lock.
 if o.id is null then raise exception 'order not found';end if;
 if o.status='completed' then raise exception 'completed grocery item is immutable';end if;
 if tg_op='INSERT' and not p.available then raise exception 'grocery product unavailable';end if;
 if o.shop_id<>p.shop_id then raise exception 'grocery product/shop mismatch';end if;
 select * into r from public.grocery_stock_reservations where item_id=item for update;
 if o.status in ('cancelled','no_rider_available') then
  if tg_op='INSERT' then raise exception 'closed order cannot reserve grocery stock';end if;
  return case when tg_op='DELETE' then old else new end;
 end if;
 if tg_op<>'DELETE' and (new.quantity<1 or new.quantity<>trunc(new.quantity)) then raise exception 'grocery quantity must be a positive integer';end if;
 qty:=case when tg_op='DELETE' then 0 else new.quantity::integer end;
 -- Existing reservation remains authoritative if tracking is toggled during an order.
 if not p.grocery_stock_tracking and r.item_id is null then return case when tg_op='DELETE' then old else new end;end if;
 delta:=qty-case when coalesce(r.released,false) then 0 else coalesce(r.quantity,0) end;
 if delta<>0 then
  update public.products set stock=stock-delta where id=product and stock>=delta;
  if not found then raise exception 'insufficient grocery stock';end if;
 end if;
 insert into public.grocery_stock_reservations(item_id,order_id,product_id,quantity,released)
 values(item,order_key,product,qty,qty=0)
 on conflict(item_id) do update set quantity=excluded.quantity,released=excluded.released,updated_at=now();
 return case when tg_op='DELETE' then old else new end;
end $$;
create trigger grocery_item_stock after insert or delete or update of quantity,product_id,order_id on public.order_items for each row execute function public.qg_grocery_item_stock();
create function public.qg_grocery_cancel_stock() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare r record;
begin
 if new.status not in ('cancelled','no_rider_available') or old.status in ('cancelled','no_rider_available') then return new;end if;
 for r in select * from public.grocery_stock_reservations where order_id=new.id and not released order by product_id,item_id for update loop
  update public.products set stock=stock+r.quantity where id=r.product_id;
  update public.grocery_stock_reservations set released=true,updated_at=now() where item_id=r.item_id;
 end loop;
 return new;
end $$;
create trigger grocery_cancel_stock after update of status on public.orders for each row execute function public.qg_grocery_cancel_stock();
revoke all on function public.qg_grocery_item_stock(),public.qg_grocery_cancel_stock() from public,anon,authenticated;

-- Public storefront uses existing products RLS, shop approval and GP-derived Delivery price.
create function public.qg_grocery_customer_page(p_shop_id uuid,p_category text default '',p_offset integer default 0) returns jsonb language sql stable security invoker set search_path=public,pg_temp as $$
 select coalesce(jsonb_agg(to_jsonb(x)),'[]') from (
 select p.id,p.shop_id,p.name,p.category,p.image,p.description,p.delivery_price,p.price,p.available,p.delivery_available
 from public.products p join public.shop_profiles s on s.id=p.shop_id
 where p.shop_id=p_shop_id and s.public_category='grocery' and p.available and p.delivery_available and p.price>0
 and (p_category='' or p.category=p_category) and p_offset>=0
 order by p.name,p.id limit 51 offset greatest(p_offset,0)) x;
$$;
create function public.qg_grocery_customer_categories(p_shop_id uuid) returns table(category text) language sql stable security invoker set search_path=public,pg_temp as $$
 select distinct p.category from public.products p join public.shop_profiles s on s.id=p.shop_id
 where p.shop_id=p_shop_id and s.public_category='grocery' and p.available and p.delivery_available and p.price>0 and p.category is not null order by p.category;
$$;
revoke all on function public.qg_grocery_customer_page(uuid,text,integer),public.qg_grocery_customer_categories(uuid) from public;
grant execute on function public.qg_grocery_customer_page(uuid,text,integer),public.qg_grocery_customer_categories(uuid) to anon,authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 values('grocery-catalog','grocery-catalog',true,5242880,array['image/jpeg','image/png','image/webp']) on conflict(id) do nothing;
create policy grocery_image_public_read on storage.objects for select to anon,authenticated using(bucket_id='grocery-catalog');
create policy grocery_image_admin_insert on storage.objects for insert to authenticated with check(bucket_id='grocery-catalog' and public.is_active_admin());
