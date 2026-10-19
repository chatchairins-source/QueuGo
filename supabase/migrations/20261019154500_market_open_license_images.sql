-- QueueGo Market: approve open-license/public-domain commodity images with explicit attribution.
-- This extends the existing strict approved-image rule only to Wikimedia-hosted files whose
-- Commons source page and rights evidence are stored on the master row. Retailer image candidates
-- remain pending and are never auto-approved.

do $$
declare r record;
begin
  for r in
    select conname
    from pg_constraint
    where conrelid='public.market_master_products'::regclass
      and contype='c'
      and pg_get_constraintdef(oid) like '%pkypiqhlrmzocysgeqew.supabase.co/storage/v1/object/public/%'
  loop
    execute format('alter table public.market_master_products drop constraint %I',r.conname);
  end loop;
end
$$;

alter table public.market_master_products
  add constraint market_master_products_image_rights_check
  check (
    (image_status in ('pending','rejected') and image is null)
    or (
      image_status='approved'
      and image is not null
      and (
        image like 'https://pkypiqhlrmzocysgeqew.supabase.co/storage/v1/object/public/%'
        or (
          image like 'https://upload.wikimedia.org/%'
          and image_source like 'https://commons.wikimedia.org/wiki/File:%'
        )
      )
      and image_source like 'https://%'
      and length(btrim(coalesce(image_rights_evidence,'')))>10
    )
  );

with approved(slug,image_url,source_page,rights_evidence) as (
values
  ('dried-lily-flower','https://upload.wikimedia.org/wikipedia/commons/7/7c/Dried_daylily.jpg','https://commons.wikimedia.org/wiki/File:Dried_daylily.jpg','Susan Slater · CC BY-SA 4.0 · Wikimedia Commons'),
  ('dried-squid-medium','https://upload.wikimedia.org/wikipedia/commons/9/99/Dried_squid.jpg','https://commons.wikimedia.org/wiki/File:Dried_squid.jpg','Enochlau · CC BY-SA 3.0 · Wikimedia Commons'),
  ('talaadthai-climbing-perch-small','https://upload.wikimedia.org/wikipedia/commons/b/b7/Anabas_testudineus_climbing_perch.jpg','https://commons.wikimedia.org/wiki/File:Anabas_testudineus_climbing_perch.jpg','Nayan j Nath · CC BY-SA 4.0 · Wikimedia Commons'),
  ('talaadthai-shrimp-chaebow-medium','https://upload.wikimedia.org/wikipedia/commons/c/ca/Jerbung_Pj_DSC_1970.jpg','https://commons.wikimedia.org/wiki/File:Jerbung_Pj_DSC_1970.jpg','Kembangraps · CC BY-SA 4.0 · Wikimedia Commons'),
  ('talaadthai-shrimp-chaebow-large','https://upload.wikimedia.org/wikipedia/commons/c/ca/Jerbung_Pj_DSC_1970.jpg','https://commons.wikimedia.org/wiki/File:Jerbung_Pj_DSC_1970.jpg','Kembangraps · CC BY-SA 4.0 · Wikimedia Commons'),
  ('talaadthai-mud-crab-small','https://upload.wikimedia.org/wikipedia/commons/b/b1/Mud_crab%2C_Scylla_serrate.jpg','https://commons.wikimedia.org/wiki/File:Mud_crab,_Scylla_serrate.jpg','Queensland State Archives · Public Domain Mark 1.0 · Wikimedia Commons'),
  ('talaadthai-mud-crab-medium','https://upload.wikimedia.org/wikipedia/commons/b/b1/Mud_crab%2C_Scylla_serrate.jpg','https://commons.wikimedia.org/wiki/File:Mud_crab,_Scylla_serrate.jpg','Queensland State Archives · Public Domain Mark 1.0 · Wikimedia Commons')
)
update public.market_master_products m
set image=a.image_url,
    image_status='approved',
    image_source=a.source_page,
    image_rights_evidence=a.rights_evidence,
    updated_at=now()
from approved a
where m.slug=a.slug;

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
      case when m.image_status='approved' then m.image_source else null end as image_source,
      case when m.image_status='approved' then m.image_rights_evidence else null end as image_rights_evidence,
      m.image_status,
      (m.image_status='approved' and nullif(btrim(coalesce(m.image,'')),'') is not null) as has_approved_image,
      (nullif(btrim(coalesce(m.image_source,'')),'') is not null) as has_image_reference,
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
