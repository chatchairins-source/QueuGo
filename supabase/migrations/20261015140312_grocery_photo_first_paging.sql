-- Order the complete filtered catalog before pagination, not just the current page.
-- Preserve invoker permissions, ownership checks, filters and the 51st-row sentinel.
create or replace function public.qg_grocery_merchant_page(p_shop_id uuid,p_query text default '',p_category text default '',p_mode text default 'all',p_offset integer default 0) returns jsonb language plpgsql stable security invoker set search_path=public,pg_temp as $$
declare result jsonb;
begin
 if coalesce(public.get_my_role(),'')<>'shop' or not exists(select 1 from public.shop_profiles where id=p_shop_id and user_id=public.get_my_user_id() and public_category='grocery') then raise exception 'own grocery shop required';end if;
 if p_offset<0 or length(p_query)>100 or p_mode not in ('all','on','off','unpriced') then raise exception 'invalid query';end if;
 select coalesce(jsonb_agg(to_jsonb(x) order by (x.image_status='approved' and nullif(btrim(x.image),'') is not null) desc,x.product_name_th,x.id),'[]') into result from (
 select m.id,m.brand,m.product_name_th,m.variant,m.size_value,m.size_unit,m.pack_size,m.sales_unit,m.category,m.image,m.image_status,
 p.id product_id,p.updated_at product_updated_at,coalesce(p.grocery_enabled,false) enabled,p.grocery_selling_price selling_price,p.grocery_promo_price promo_price,coalesce(p.stock,0) stock_qty,coalesce(p.grocery_stock_tracking,false) stock_tracking
 from public.grocery_master_products m left join public.products p on p.grocery_master_id=m.id and p.shop_id=p_shop_id
 where m.active and m.merged_into is null and (p_category='' or m.category=p_category)
 and (p_query='' or to_tsvector('simple',m.brand||' '||m.product_name_th||' '||coalesce(m.product_name_en,'')||' '||m.variant) @@ plainto_tsquery('simple',p_query) or m.product_name_th ilike '%'||p_query||'%' or m.brand ilike '%'||p_query||'%')
 and (p_mode='all' or p_mode='on' and p.grocery_enabled or p_mode='off' and not coalesce(p.grocery_enabled,false) or p_mode='unpriced' and p.grocery_selling_price is null)
 order by (m.image_status='approved' and nullif(btrim(m.image),'') is not null) desc,m.product_name_th,m.id limit 51 offset p_offset) x;
 return result;
end $$;
