-- Quality pass for Market Master catalog product imagery.
-- Visible Merchant UI continues to show only product images; source metadata stays backend-only.
-- Prefer clean retailer catalog shots over lifestyle/supplier photography.

begin;

with refs(slug,image_url,source_url,source_name) as (
values
  (
    'talaadthai-mud-crab-medium',
    'https://images.mango-prod.siammakro.cloud/SOURCE/6179a059dbef45e4bd7b885d6b915def',
    'https://www.makro.pro/th/p/754884-373775434540204',
    'Makro PRO'
  ),
  (
    'talaadthai-mud-crab-small',
    'https://images.mango-prod.siammakro.cloud/SOURCE/6179a059dbef45e4bd7b885d6b915def',
    'https://www.makro.pro/th/p/754884-373775434540204',
    'Makro PRO'
  ),
  (
    'lotus-ripe-namdokmai-mango-kg',
    'https://o2o-static.lotuss.com/products/107654/4723_1.jpg',
    'https://www.lotuss.com/th/product/ripe-namdokmai-mango-4723?openExternalBrowser=1',
    'Lotus''s'
  ),
  (
    'lotus-sweet-snail-kg',
    'https://o2o-static.lotuss.com/products/107486/52102814_1.jpg',
    'https://www.lotuss.com/th/product/52102814?openExternalBrowser=1',
    'Lotus''s'
  )
)
update public.market_master_products m
set catalog_preview_image=r.image_url,
    catalog_preview_source_url=r.source_url,
    catalog_preview_source_name=r.source_name,
    updated_at=now()
from refs r
where m.slug=r.slug
  and m.active;

do $$
declare
  v_missing integer;
begin
  select count(*) into v_missing
  from public.market_master_products
  where active
    and nullif(btrim(coalesce(catalog_preview_image,'')),'') is null;

  if v_missing <> 0 then
    raise exception 'Market catalog image quality pass left % active rows without previews',v_missing;
  end if;
end
$$;

commit;
