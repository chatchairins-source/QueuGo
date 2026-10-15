-- QueueGo Market Master Catalog foundation.
-- Additive only: existing market products/orders/stock remain authoritative.
-- Master identities contain no merchant price, cost, stock, route weight or fabricated image.

create table public.market_master_products (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$'),
  name_th text not null check (length(btrim(name_th)) between 2 and 120),
  category text not null check (category in (
    'ผักสด','ผลไม้','เนื้อหมู','เนื้อวัว','ไก่ / เป็ด','ปลา','อาหารทะเล','ไข่',
    'เต้าหู้ / เส้นสด / ลูกชิ้น','ของสดพร้อมปรุง','อาหารแช่เย็น / แช่แข็ง',
    'พริกแกง / เครื่องแกง','เครื่องปรุง / ซอส','ข้าวสาร / ธัญพืช','ของแห้ง',
    'อาหารปรุงสำเร็จ','ขนม / ของหวาน','เครื่องดื่ม','ของใช้ในครัวเรือน',
    'ดอกไม้ / ของไหว้','อื่น ๆ'
  )),
  suggested_unit text check (
    suggested_unit is null or suggested_unit in
    ('ชิ้น','กิโลกรัม','ขีด','กรัม','ถุง','แพ็ก','มัด','ลูก','ขวด','กล่อง')
  ),
  suggested_pack_size numeric(12,3),
  source_unit text not null check (length(btrim(source_unit)) between 1 and 80),
  source_url text not null check (source_url ~ '^https://'),
  source_name text not null check (length(btrim(source_name)) between 2 and 160),
  source_note text,
  retrieved_on date not null,
  image text,
  image_status text not null default 'pending' check (image_status in ('pending','approved','rejected')),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    (suggested_unit is null and suggested_pack_size is null)
    or
    (suggested_unit is not null and suggested_pack_size is not null and suggested_pack_size>0)
  )
);

alter table public.market_master_products enable row level security;
revoke all on table public.market_master_products from public,anon,authenticated;
grant select on table public.market_master_products to authenticated;
create policy market_master_authenticated_read
  on public.market_master_products
  for select to authenticated
  using (active or public.is_active_admin());

alter table public.market_products
  add column master_product_id uuid;

alter table public.market_products
  add constraint market_products_master_product_fk
  foreign key(master_product_id) references public.market_master_products(id);

create unique index market_products_shop_master_unique
  on public.market_products(shop_id,master_product_id)
  where master_product_id is not null;

create index market_master_category_name_idx
  on public.market_master_products(category,name_th,id);

create schema if not exists market_private;
revoke all on schema market_private from public,anon,authenticated;

create table market_private.catalog_add_receipts (
  shop_id uuid not null,
  request_id uuid not null,
  request_body jsonb not null,
  result jsonb not null,
  created_at timestamptz not null default now(),
  primary key(shop_id,request_id)
);
alter table market_private.catalog_add_receipts enable row level security;
revoke all on table market_private.catalog_add_receipts from public,anon,authenticated;

create or replace function public.qg_market_master_categories()
returns table(category text,n bigint)
language plpgsql
stable
security invoker
set search_path=public,pg_temp
as $$
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode='42501';
  end if;
  return query
  select m.category,count(*)::bigint
  from public.market_master_products m
  where m.active
  group by m.category
  order by m.category;
end
$$;
revoke all on function public.qg_market_master_categories() from public,anon;
grant execute on function public.qg_market_master_categories() to authenticated;

create or replace function public.qg_market_catalog_page(
  p_query text default '',
  p_category text default '',
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security invoker
set search_path=public,pg_temp
as $$
declare
  v_shop uuid;
  v_shop_category text;
  result jsonb;
begin
  v_shop:=public.pos_my_shop();
  select public_category into v_shop_category
  from public.shop_profiles
  where id=v_shop and archived_at is null;

  if v_shop is null
     or not public.pos_is_owner()
     or v_shop_category not in ('market','meat','fish','vegetable','fruit')
  then
    raise exception 'market seller access denied' using errcode='42501';
  end if;

  if p_offset<0
     or length(coalesce(p_query,''))>100
     or length(coalesce(p_category,''))>80
  then
    raise exception 'invalid query';
  end if;

  select coalesce(
    jsonb_agg(to_jsonb(x) order by x.has_approved_image desc,x.name_th,x.id),
    '[]'::jsonb
  )
  into result
  from (
    select
      m.id,
      m.slug,
      m.name_th,
      m.category,
      m.suggested_unit,
      m.suggested_pack_size,
      m.source_unit,
      case when m.image_status='approved' then m.image else null end as image,
      m.image_status,
      (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null) as has_approved_image,
      mp.product_id,
      p.updated_at as product_updated_at,
      p.price,
      p.delivery_price,
      p.available,
      mp.unit,
      mp.pack_size,
      mp.item_weight_kg,
      mp.stock_quantity,
      mp.min_stock,
      mp.cost_price
    from public.market_master_products m
    left join public.market_products mp
      on mp.master_product_id=m.id
     and mp.shop_id=v_shop
    left join public.products p
      on p.id=mp.product_id
     and p.shop_id=v_shop
    where m.active
      and (coalesce(p_category,'')='' or m.category=p_category)
      and (
        coalesce(p_query,'')=''
        or m.name_th ilike '%'||p_query||'%'
        or m.slug ilike '%'||lower(p_query)||'%'
      )
    order by
      (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null) desc,
      m.name_th,
      m.id
    limit 51
    offset p_offset
  ) x;

  return result;
end
$$;
revoke all on function public.qg_market_catalog_page(text,text,integer) from public,anon;
grant execute on function public.qg_market_catalog_page(text,text,integer) to authenticated;

create or replace function public.qg_market_catalog_add(
  p_master_product_id uuid,
  p_price numeric,
  p_unit text,
  p_pack_size numeric,
  p_weight_kg numeric,
  p_cost numeric,
  p_initial_stock numeric,
  p_min_stock numeric,
  p_available boolean,
  p_request_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path=public,market_private,pg_temp
as $$
declare
  v_shop uuid;
  v_shop_category text;
  v_master public.market_master_products%rowtype;
  v_product uuid;
  v_body jsonb;
  v_receipt market_private.catalog_add_receipts%rowtype;
  v_result jsonb;
begin
  if auth.uid() is null or p_request_id is null then
    raise exception 'authentication and request ID required' using errcode='42501';
  end if;

  v_shop:=public.pos_my_shop();
  select public_category into v_shop_category
  from public.shop_profiles
  where id=v_shop and archived_at is null;

  if v_shop is null
     or not public.pos_is_owner()
     or v_shop_category not in ('market','meat','fish','vegetable','fruit')
  then
    raise exception 'market seller access denied' using errcode='42501';
  end if;

  select * into strict v_master
  from public.market_master_products
  where id=p_master_product_id and active;

  v_body:=jsonb_build_object(
    'master_product_id',p_master_product_id,
    'price',p_price,
    'unit',p_unit,
    'pack_size',p_pack_size,
    'weight_kg',p_weight_kg,
    'cost',p_cost,
    'initial_stock',p_initial_stock,
    'min_stock',p_min_stock,
    'available',coalesce(p_available,true)
  );

  perform pg_advisory_xact_lock(hashtext(v_shop::text||':market-master:'||p_master_product_id::text));
  perform pg_advisory_xact_lock(hashtext(v_shop::text||':market-request:'||p_request_id::text));

  select * into v_receipt
  from market_private.catalog_add_receipts
  where shop_id=v_shop and request_id=p_request_id;

  if found then
    if v_receipt.request_body is distinct from v_body then
      raise exception 'request ID body mismatch';
    end if;
    return v_receipt.result;
  end if;

  select product_id into v_product
  from public.market_products
  where shop_id=v_shop and master_product_id=p_master_product_id
  for update;

  if found then
    v_result:=jsonb_build_object('product_id',v_product,'created',false);
    insert into market_private.catalog_add_receipts(shop_id,request_id,request_body,result)
    values(v_shop,p_request_id,v_body,v_result);
    return v_result;
  end if;

  v_product:=public.market_save_product(
    null,
    v_master.name_th,
    v_master.category,
    p_price,
    case when v_master.image_status='approved' then v_master.image else null end,
    p_unit,
    p_pack_size,
    p_weight_kg,
    p_cost,
    p_initial_stock,
    p_min_stock,
    coalesce(p_available,true)
  );

  update public.market_products
  set master_product_id=p_master_product_id,
      updated_at=now()
  where product_id=v_product and shop_id=v_shop;

  if not found then
    raise exception 'market product link failed';
  end if;

  v_result:=jsonb_build_object('product_id',v_product,'created',true);
  insert into market_private.catalog_add_receipts(shop_id,request_id,request_body,result)
  values(v_shop,p_request_id,v_body,v_result);
  return v_result;
end
$$;
revoke all on function public.qg_market_catalog_add(uuid,numeric,text,numeric,numeric,numeric,numeric,numeric,boolean,uuid) from public,anon;
grant execute on function public.qg_market_catalog_add(uuid,numeric,text,numeric,numeric,numeric,numeric,numeric,boolean,uuid) to authenticated;

insert into public.market_master_products(
  slug,name_th,category,suggested_unit,suggested_pack_size,source_unit,
  source_url,source_name,source_note,retrieved_on,image,image_status,active
)
values
  ('kale-mixed','ผักคะน้า คละ','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/272638/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT agricultural price report','2026-10-15'::date,null,'pending',true),
  ('chinese-cabbage','ผักกาดขาวปลี','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('choy-sum','ผักกวางตุ้ง','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('cabbage','กะหล่ำปลี','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('thai-basil','โหระพา','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetable price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('spring-onion','ต้นหอม','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetable price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('holy-basil-leaves','ใบกะเพรา','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetable price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('water-spinach','ผักบุ้งจีน','ผักสด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetable price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('large-cavendish-banana','กล้วยหอมขนาดใหญ่','ผลไม้',null,null,'หวี','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('pork-tenderloin-dressed','สุกรชำแหละ เนื้อสัน สันใน','เนื้อหมู','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/272638/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT agricultural price report','2026-10-15'::date,null,'pending',true),
  ('pork-lean','หมูเนื้อแดง','เนื้อหมู','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT pork price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('pork-belly','หมูสามชั้น','เนื้อหมู','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('native-chicken','ไก่บ้าน','ไก่ / เป็ด','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('whole-duck','เป็ดสดทั้งตัว','ไก่ / เป็ด','ชิ้น',1,'ชิ้น','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('egg-size-2-10','ไข่ไก่เบอร์ 2 (10 ฟอง)','ไข่','ชิ้น',10,'10 ฟอง','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('egg-size-3-10','ไข่ไก่เบอร์ 3 (10 ฟอง)','ไข่','ชิ้น',10,'10 ฟอง','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('dried-shiitake-small','เห็ดหอมจีนขนาดเล็ก','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetarian ingredient monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('dried-shiitake-large','เห็ดหอมจีนขนาดใหญ่','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetarian ingredient monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('dried-bamboo-shoot','หน่อไม้จีน','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('fresh-tofu-skin','ฟองเต้าหู้สด','เต้าหู้ / เส้นสด / ลูกชิ้น','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('dried-lily-flower','ดอกไม้จีน','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetarian ingredient monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('textured-vegetable-protein','โปรตีนเกษตร','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetarian ingredient monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('ginkgo-nut','แปะก๊วย','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT vegetarian ingredient monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('dried-squid-medium','ปลาหมึกแห้งขนาดกลาง','ของแห้ง','กิโลกรัม',1,'กิโลกรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true),
  ('mee-sua-400g','หมี่ซั่ว 400 กรัม','ของแห้ง','ถุง',1,'ถุง 400 กรัม','https://www.dit.go.th/th/news/press-releases/','กรมการค้าภายใน กระทรวงพาณิชย์','DIT market price monitoring 2569','2026-10-15'::date,null,'pending',true)
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
