begin;
-- Cover the two child-side foreign keys used by product/auth user deletion.
create index if not exists pos_product_codes_product_id_idx
  on public.pos_product_codes(product_id);
create index if not exists pos_product_codes_created_by_idx
  on public.pos_product_codes(created_by);
commit;
