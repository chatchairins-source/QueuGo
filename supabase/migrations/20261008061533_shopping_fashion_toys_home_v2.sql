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
      where value in (
        'mobile_accessories','computer_it','automotive_car','automotive_motorcycle',
        'fashion_accessories','toys','home_decor'
      )
    ) s;
  else
    new.public_subcategories := '{}'::text[];
  end if;
  return new;
end
$$;

update public.shop_profiles
set public_subcategories = case
  when jsonb_typeof(coalesce(metadata,'{}'::jsonb)->'shoppingSubcategories')='array'
  then coalesce((
    select array_agg(v order by v)
    from (
      select distinct value as v
      from jsonb_array_elements_text(metadata->'shoppingSubcategories')
      where value in (
        'mobile_accessories','computer_it','automotive_car','automotive_motorcycle',
        'fashion_accessories','toys','home_decor'
      )
    ) x
  ),'{}'::text[])
  else '{}'::text[]
end
where coalesce(public_category,metadata->>'category','')='shopping'
   or jsonb_typeof(coalesce(metadata,'{}'::jsonb)->'shoppingSubcategories')='array';

insert into public.system_settings(key,value,updated_at)
values('service_banners',jsonb_build_object('version','1'),now())
on conflict (key) do nothing;

update public.system_settings
set value =
  coalesce(value,'{}'::jsonb)
  || case when not coalesce(value,'{}'::jsonb) ? 'fashion_accessories'
       then jsonb_build_object('fashion_accessories',jsonb_build_object(
         'title','เสื้อผ้าและเครื่องประดับ',
         'subtitle','แฟชั่น เสื้อผ้า กระเป๋า รองเท้า และเครื่องประดับใกล้คุณ',
         'active',true
       )) else '{}'::jsonb end
  || case when not coalesce(value,'{}'::jsonb) ? 'toys'
       then jsonb_build_object('toys',jsonb_build_object(
         'title','ของเล่น',
         'subtitle','ของเล่นและสินค้าเด็กจากร้านใกล้คุณ',
         'active',true
       )) else '{}'::jsonb end
  || case when not coalesce(value,'{}'::jsonb) ? 'home_decor'
       then jsonb_build_object('home_decor',jsonb_build_object(
         'title','ตกแต่งบ้าน',
         'subtitle','ของแต่งบ้าน ของใช้ และไอเดียแต่งบ้านใกล้คุณ',
         'active',true
       )) else '{}'::jsonb end,
    updated_at=now()
where key='service_banners';
