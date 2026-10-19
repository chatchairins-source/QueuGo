-- Hotfix: public grocery RPCs read public.products.category as anon.
-- Keep RLS enabled and preserve the existing column-level guest allowlist.
-- This is intentionally narrower than GRANT SELECT ON TABLE public.products TO anon.

grant select (category) on table public.products to anon;
