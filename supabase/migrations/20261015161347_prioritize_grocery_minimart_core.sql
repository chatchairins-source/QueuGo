-- Prioritize real-market Grocery core items ahead of long-tail catalog rows.
-- Applied to Production as migration 20261015161347_prioritize_grocery_minimart_core.
CREATE OR REPLACE FUNCTION public.qg_grocery_merchant_page(p_shop_id uuid, p_query text DEFAULT ''::text, p_category text DEFAULT ''::text, p_mode text DEFAULT 'all'::text, p_offset integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  result jsonb;
begin
  if coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1
       from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
     )
  then
    raise exception 'own grocery shop required';
  end if;

  if p_offset<0
     or length(p_query)>100
     or p_mode not in ('all','on','off','unpriced')
  then
    raise exception 'invalid query';
  end if;

  select coalesce(
    jsonb_agg(
      to_jsonb(x)
      order by
        (x.subcategory='สินค้าหลักร้านชำ') desc,
        (x.image_status='approved' and nullif(btrim(x.image),'') is not null) desc,
        x.product_name_th,
        x.id
    ),
    '[]'
  )
  into result
  from (
    select
      m.id,
      m.brand,
      m.product_name_th,
      m.variant,
      m.size_value,
      m.size_unit,
      m.pack_size,
      m.sales_unit,
      m.category,
      coalesce(nullif(btrim(m.image),''), nullif(btrim(m.image_source),'')) as image,
      m.image_status,
      p.id product_id,
      p.updated_at product_updated_at,
      coalesce(p.grocery_enabled,false) enabled,
      p.grocery_selling_price selling_price,
      coalesce(p.stock,0) stock_qty,
      coalesce(p.grocery_stock_tracking,false) stock_tracking,
      coalesce(p.pos_available,true) pos_available,
      coalesce(p.delivery_available,true) delivery_available,
      p.delivery_price
    from public.grocery_master_products m
    left join public.products p
      on p.grocery_master_id=m.id
     and p.shop_id=p_shop_id
    where m.active
      and m.merged_into is null
      and (p_category='' or m.category=p_category)
      and (
        p_query=''
        or to_tsvector(
          'simple',
          m.brand||' '||m.product_name_th||' '||coalesce(m.product_name_en,'')||' '||m.variant
        ) @@ plainto_tsquery('simple',p_query)
        or m.product_name_th ilike '%'||p_query||'%'
        or m.brand ilike '%'||p_query||'%'
      )
      and (
        p_mode='all'
        or p_mode='on' and p.grocery_enabled
        or p_mode='off' and not coalesce(p.grocery_enabled,false)
        or p_mode='unpriced' and p.grocery_selling_price is null
      )
    order by
      (m.subcategory='สินค้าหลักร้านชำ') desc,
      (m.image_status='approved' and nullif(btrim(m.image),'') is not null) desc,
      m.product_name_th,
      m.id
    limit 51
    offset p_offset
  ) x;

  return result;
end
$function$

