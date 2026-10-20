alter function public.qg_admin_update_shop_category(uuid,text,text[],text) security invoker;
revoke all on function public.qg_admin_update_shop_category(uuid,text,text[],text) from public,anon;
grant execute on function public.qg_admin_update_shop_category(uuid,text,text[],text) to authenticated,service_role;
