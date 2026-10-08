alter table public.shop_profiles
  add column if not exists public_subcategories text[] not null default '{}'::text[];

create or replace function public.queuego_sync_shop_public_subcategories()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if jsonb_typeof(coalesce(new.metadata,'{}'::jsonb)->'shoppingSubcategories') = 'array' then
    select coalesce(array_agg(v order by v),'{}'::text[])
      into new.public_subcategories
    from (
      select distinct value as v
      from jsonb_array_elements_text(new.metadata->'shoppingSubcategories')
      where value in ('mobile_accessories','computer_it','automotive_car','automotive_motorcycle')
    ) s;
  else
    new.public_subcategories := '{}'::text[];
  end if;
  return new;
end
$$;

drop trigger if exists trg_queuego_shop_public_subcategories on public.shop_profiles;
create trigger trg_queuego_shop_public_subcategories
before insert or update of metadata
on public.shop_profiles
for each row execute function public.queuego_sync_shop_public_subcategories();

update public.shop_profiles
set public_subcategories = case
  when jsonb_typeof(coalesce(metadata,'{}'::jsonb)->'shoppingSubcategories')='array'
  then coalesce((
    select array_agg(v order by v)
    from (
      select distinct value as v
      from jsonb_array_elements_text(metadata->'shoppingSubcategories')
      where value in ('mobile_accessories','computer_it','automotive_car','automotive_motorcycle')
    ) x
  ),'{}'::text[])
  else '{}'::text[]
end;

create index if not exists shop_profiles_public_subcategories_gin_idx
  on public.shop_profiles using gin(public_subcategories);

insert into public.system_settings(key,value,updated_at)
values(
  'service_banners',
  jsonb_build_object(
    'version','1',
    'food',jsonb_build_object('title','อาหาร','subtitle','ร้านอาหารใกล้คุณ','active',true),
    'cafe',jsonb_build_object('title','เครื่องดื่ม','subtitle','ร้านเครื่องดื่มใกล้คุณ','active',true),
    'grocery',jsonb_build_object('title','ร้านขายของชำ','subtitle','ซื้อของใกล้บ้าน เงินหมุนเวียนในชุมชน','active',true),
    'market',jsonb_build_object('title','ตลาดสด','subtitle','ตลาดสดใกล้คุณ สดใหม่ทุกวัน','active',true),
    'shopping',jsonb_build_object('title','ช้อปปิ้ง','subtitle','สินค้าจากร้านใกล้คุณ ส่งได้ทันที','active',true),
    'mobile_accessories',jsonb_build_object('title','มือถือและอุปกรณ์','subtitle','มือถือ เคส ฟิล์ม สายชาร์จ และอุปกรณ์ใกล้คุณ','active',true),
    'computer_it',jsonb_build_object('title','คอมพิวเตอร์และไอที','subtitle','อุปกรณ์ไอทีจากร้านใกล้คุณ','active',true),
    'automotive',jsonb_build_object('title','อะไหล่ยานยนต์','subtitle','อะไหล่รถยนต์และมอเตอร์ไซค์ใกล้คุณ','active',true)
  ),
  now()
)
on conflict (key) do nothing;

drop policy if exists "system_settings_public_home_banner_read" on public.system_settings;
drop policy if exists "system_settings_public_banners_read" on public.system_settings;
create policy "system_settings_public_banners_read"
on public.system_settings
for select
to anon
using (key in ('home_service_banner','service_banners'));
