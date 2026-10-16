-- Sources can misclassify frozen products under dry-grocery. Retain facts but
-- deactivate cold rows; prevent import/finalize from making them active again.
create or replace function public.qg_grocery_source_is_cold(p_title text, p_title_en text, p_category text)
returns boolean language sql immutable parallel safe
set search_path=public,pg_temp
as $$
 select coalesce(p_title,'')||' '||coalesce(p_title_en,'') ~* '(แช่แข็ง|แช่เย็น|(^|[^a-z])(frozen|chilled|refrigerated)($|[^a-z]))'
     or coalesce(p_category,'') ~* '(^|/)(frozen|chilled|refrigerated)([-/]|$)';
$$;
revoke all on function public.qg_grocery_source_is_cold(text,text,text) from public,anon;
grant execute on function public.qg_grocery_source_is_cold(text,text,text) to authenticated;

update public.grocery_source_products set active=false,updated_at=now()
where active and public.qg_grocery_source_is_cold(title_th,title_en,deepest_category);

alter table public.grocery_source_products add constraint grocery_source_no_active_cold
check (not active or not public.qg_grocery_source_is_cold(title_th,title_en,deepest_category));
