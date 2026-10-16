-- Categorize real source products before paging and expose owner-scoped category choices.
create or replace function public.qg_grocery_source_category_label(p_deepest_category text)
returns text language sql immutable set search_path=public,pg_temp as $$
 select case
 when p_deepest_category like '%/instant-noodles' then 'บะหมี่กึ่งสำเร็จรูป'
 when p_deepest_category like '%/instant-meals/%' then 'อาหารกึ่งสำเร็จรูป'
 when p_deepest_category like '%/grains-rice-cereal/cereal-%' then 'ซีเรียลและข้าวโอ๊ต'
 when p_deepest_category like '%/grains-rice-cereal/%' then 'ข้าวสารและธัญพืช'
 when p_deepest_category like '%/pasta-noodles/%' then 'เส้นและพาสต้า'
 when p_deepest_category like '%/canned-fish' then 'ปลากระป๋อง'
 when p_deepest_category like '%/coffee-creamers' or p_deepest_category like '%/condensed-milk' then 'นมข้นและครีมเทียม'
 when p_deepest_category like '%/canned-packaged-food/%' then 'อาหารกระป๋อง'
 when p_deepest_category like '%/cooking-oil-vinegar/%vinegar' then 'น้ำส้มสายชู'
 when p_deepest_category like '%/cooking-oil-vinegar/%' then 'น้ำมันพืช'
 when p_deepest_category like '%/sauce-seasoning/fish-sauce' then 'น้ำปลา'
 when p_deepest_category like '%/sauce-seasoning/soy-sauce' then 'ซีอิ๊ว'
 when p_deepest_category like '%/sauce-seasoning/%' then 'ซอสและน้ำจิ้ม'
 when p_deepest_category like '%/seasoning-and-spices/%' then 'เครื่องปรุงและเครื่องเทศ'
 when p_deepest_category like '%/sweet-condiment/%sugar' then 'น้ำตาลและสารให้ความหวาน'
 when p_deepest_category like '%/sweet-condiment/%' then 'น้ำเชื่อมและน้ำผึ้ง'
 when p_deepest_category like '%/flour/%' then 'แป้งประกอบอาหาร'
 when p_deepest_category like '%/bakery-ingredients/%' then 'วัตถุดิบทำขนม'
 when p_deepest_category like '%/dessert-and-dessert-ingredients/%' then 'ขนมและวัตถุดิบขนม'
 when p_deepest_category like '%/dried-fruits' then 'ผลไม้อบแห้ง'
 when p_deepest_category like '%/nuts-whole-grains/%' then 'ถั่วและเมล็ดธัญพืช'
 when p_deepest_category like '%/ready-to-eat/cookies' then 'คุกกี้'
 when p_deepest_category like '%/ready-to-eat/%' then 'ขนมอบและขนมพร้อมทาน'
 when p_deepest_category like '%/dips-spreads/%' then 'แยมและของทาขนมปัง'
 when p_deepest_category like '%/dairy-products/%' then 'ผลิตภัณฑ์นม'
 when p_deepest_category like '%/personal-care/haircare' then 'ผลิตภัณฑ์ดูแลเส้นผม'
 when p_deepest_category like '%/personal-care/feminine-hygiene-products' then 'ผ้าอนามัย'
 when p_deepest_category like '%/personal-care/bar-soap' or p_deepest_category like '%/personal-care/body-wash' then 'สบู่และผลิตภัณฑ์อาบน้ำ'
 when p_deepest_category like '%/personal-care/%' or p_deepest_category='health-beauty/personal-care' then 'ของใช้ส่วนตัว'
 else 'ของแห้งอื่น ๆ' end;
$$;
revoke all on function public.qg_grocery_source_category_label(text) from public,anon;
grant execute on function public.qg_grocery_source_category_label(text) to authenticated;

-- Replace the existing signature; old three-argument callers retain the empty-category default.
drop function public.qg_grocery_source_search(uuid,text,integer);
create or replace function public.qg_grocery_source_search(
  p_shop_id uuid,
  p_query text default '',
  p_offset integer default 0,
  p_category text default ''
)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  q text:=btrim(coalesce(p_query,''));
  result jsonb;
begin
  if auth.uid() is null
     or coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1 from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
         and archived_at is null
     )
  then
    raise exception 'own grocery shop required' using errcode='42501';
  end if;

  if length(coalesce(p_category,''))>100 or length(q)>100 or p_offset<0 or p_offset>20000 then
    raise exception 'invalid source search';
  end if;

  select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb)
  into result
  from (
    select
      s.source,
      s.source_product_id,
      s.source_rank,
      s.source_category,
      s.sku,
      s.barcode,
      s.brand,
      s.title_th,
      s.deepest_category,
      s.image_url,
      s.unit_size,
      s.unit_type,
      s.unit_factor,
      public.qg_grocery_source_category_label(s.deepest_category) as queuego_category,
      exists(
        select 1 from public.products p
        where p.shop_id=p_shop_id
          and p.metadata->>'makro_source_product_id'=s.source_product_id
      ) as in_shop
    from public.grocery_source_products s
    where s.active
      and (coalesce(p_category,'')='' or public.qg_grocery_source_category_label(s.deepest_category)=p_category)
      and (
        q=''
        or s.barcode=q
        or s.brand ilike '%'||q||'%'
        or s.title_th ilike '%'||q||'%'
        or coalesce(s.title_en,'') ilike '%'||q||'%'
        or to_tsvector('simple',
             coalesce(s.brand,'')||' '||
             coalesce(s.title_th,'')||' '||
             coalesce(s.title_en,'')||' '||
             coalesce(s.barcode,'')
           ) @@ plainto_tsquery('simple',q)
      )
    order by
      (s.barcode=q and q<>'') desc,
      (s.brand ilike q||'%' and q<>'') desc,
      (s.title_th ilike q||'%' and q<>'') desc,
      s.source_rank,
      s.brand,
      s.title_th,
      s.source_product_id
    limit 51 offset p_offset
  ) x;

  return result;
end;
$$;

create or replace function public.qg_grocery_source_categories(p_shop_id uuid)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare result jsonb;
begin
  if auth.uid() is null
     or coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1 from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
         and archived_at is null
     )
  then
    raise exception 'own grocery shop required' using errcode='42501';
  end if;

  select coalesce(jsonb_agg(to_jsonb(x) order by x.category),'[]'::jsonb) into result from (
    select public.qg_grocery_source_category_label(deepest_category) category,count(*) n
    from public.grocery_source_products where active group by 1
  ) x;
  return result;
end;
$$;
revoke all on function public.qg_grocery_source_search(uuid,text,integer,text) from public,anon;
grant execute on function public.qg_grocery_source_search(uuid,text,integer,text) to authenticated;
revoke all on function public.qg_grocery_source_categories(uuid) from public,anon;
grant execute on function public.qg_grocery_source_categories(uuid) to authenticated;

-- Reclassify sourced shop listings only; prices, stock, availability and manual products stay intact.
update public.products p set category=public.qg_grocery_source_category_label(s.deepest_category),
 metadata=coalesce(p.metadata,'{}'::jsonb)||jsonb_build_object('category',public.qg_grocery_source_category_label(s.deepest_category))
from public.grocery_source_products s
where p.metadata->>'catalog_source'='makro-pro'
 and p.metadata->>'makro_source_product_id'=s.source_product_id
 and p.category is distinct from public.qg_grocery_source_category_label(s.deepest_category);
