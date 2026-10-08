create or replace function public.queuego_admin_delete_empty_shop_account(
  p_user_id uuid,
  p_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_auth uuid;
  v_reason text:=nullif(trim(coalesce(p_reason,'')),'');
begin
  if not public.is_active_admin() then
    raise exception 'admin only' using errcode='42501';
  end if;

  select auth_user_id into v_auth
  from public.users
  where id=p_user_id and role='shop'
  for update;

  if not found then
    raise exception 'SHOP_ACCOUNT_NOT_FOUND' using errcode='P0002';
  end if;

  if exists(select 1 from public.shop_profiles sp where sp.user_id=p_user_id) then
    raise exception 'SHOP_ARCHIVE_REQUIRED';
  end if;

  if exists(select 1 from public.orders o where o.customer_id=p_user_id)
     or exists(select 1 from public.market_orders o where o.customer_id=p_user_id)
     or exists(select 1 from public.laundry_orders o where o.customer_id=p_user_id)
     or exists(select 1 from public.market_requests r where r.requester_shop_user_id=p_user_id)
     or exists(select 1 from public.shop_support_messages m where m.shop_user_id=p_user_id or m.sender_user_id=p_user_id)
     or exists(select 1 from public.qg_support_tickets t where t.user_id=p_user_id)
     or exists(select 1 from public.reviews r where r.customer_id=p_user_id)
     or exists(select 1 from public.qg_account_deletion_requests d where d.user_id=p_user_id)
     or exists(select 1 from public.shop_gp_rates g where g.shop_user_id=p_user_id)
     or exists(select 1 from public.shop_modules m where m.shop_user_id=p_user_id)
     or exists(select 1 from public.qg_merchant_action_receipts a where a.user_id=p_user_id)
     or exists(select 1 from public.qg_order_item_adjustments a where a.actor_user_id=p_user_id)
     or exists(select 1 from public.qg_customer_favorites f where f.user_id=p_user_id)
  then
    raise exception 'SHOP_ACCOUNT_HAS_HISTORY';
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    public.get_my_user_id(),
    'shop_empty_account_delete',
    'users',
    p_user_id,
    'deleted merchant account with no store profile or business history',
    jsonb_build_object('reason',v_reason,'can_register_again',true)
  );

  begin
    delete from public.users where id=p_user_id;
  exception
    when foreign_key_violation then
      raise exception 'SHOP_ACCOUNT_HAS_HISTORY';
  end;

  if v_auth is not null then
    delete from auth.users where id=v_auth;
  end if;

  return jsonb_build_object(
    'user_id',p_user_id,
    'deleted',true,
    'had_shop_profile',false,
    'history_preserved',true,
    'can_register_again',true
  );
end
$$;

revoke all on function public.queuego_admin_delete_empty_shop_account(uuid,text)
from public,anon;
grant execute on function public.queuego_admin_delete_empty_shop_account(uuid,text)
to authenticated;
