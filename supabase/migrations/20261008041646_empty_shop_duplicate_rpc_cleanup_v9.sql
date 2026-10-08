revoke all on function public.queuego_admin_delete_empty_shop_account(uuid,text)
from public,anon,authenticated;

drop function if exists public.queuego_admin_delete_empty_shop_account(uuid,text);
