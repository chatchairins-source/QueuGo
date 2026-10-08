update public.users u
set status='pending',updated_at=now()
where u.role='shop'
  and u.status='active'
  and not exists(select 1 from public.shop_profiles sp where sp.user_id=u.id);

create or replace function public.queuego_admin_delete_unopened_shop(
  p_shop_user_id uuid,
  p_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_auth uuid;
  v_role text;
  v_reason text:=nullif(trim(coalesce(p_reason,'')),'');
begin
  if not public.is_active_admin() then
    raise exception 'admin only' using errcode='42501';
  end if;
  if v_reason is null then raise exception 'DELETE_REASON_REQUIRED'; end if;
  if length(v_reason)>1000 then raise exception 'DELETE_REASON_TOO_LONG'; end if;

  select role,auth_user_id into v_role,v_auth
  from public.users
  where id=p_shop_user_id
  for update;
  if not found then raise exception 'SHOP_USER_NOT_FOUND' using errcode='P0002'; end if;
  if v_role<>'shop' then raise exception 'SHOP_USER_REQUIRED'; end if;

  if exists(select 1 from public.shop_profiles where user_id=p_shop_user_id) then
    raise exception 'SHOP_ARCHIVE_REQUIRED';
  end if;

  if exists(select 1 from public.admin_order_delete_archive where deleted_by=p_shop_user_id)
     or exists(select 1 from public.laundry_orders where customer_id=p_shop_user_id)
     or exists(select 1 from public.market_orders where customer_id=p_shop_user_id)
     or exists(select 1 from public.merchant_cash_receipts where confirmed_by=p_shop_user_id)
     or exists(select 1 from public.qg_account_deletion_requests where user_id=p_shop_user_id)
     or exists(select 1 from public.qg_customer_favorites where user_id=p_shop_user_id)
     or exists(select 1 from public.qg_order_item_adjustments where actor_user_id=p_shop_user_id)
     or exists(select 1 from public.qg_rider_action_receipts where user_id=p_shop_user_id)
     or exists(select 1 from public.qg_rider_push_subscriptions where user_id=p_shop_user_id)
     or exists(select 1 from public.qg_support_tickets where user_id=p_shop_user_id or admin_id=p_shop_user_id)
     or exists(select 1 from public.qg_ticket_events where actor_id=p_shop_user_id)
     or exists(select 1 from public.reviews where customer_id=p_shop_user_id)
     or exists(select 1 from public.orders where customer_id=p_shop_user_id)
     or exists(select 1 from public.shop_support_messages where shop_user_id=p_shop_user_id or sender_user_id=p_shop_user_id)
  then
    raise exception 'SHOP_USER_HAS_HISTORY';
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    public.get_my_user_id(),
    'shop_unopened_delete',
    'users',
    p_shop_user_id,
    'deleted unopened merchant registration',
    jsonb_build_object('reason',v_reason,'can_register_again',true)
  );

  delete from public.users where id=p_shop_user_id;
  if v_auth is not null then
    delete from auth.users where id=v_auth;
  end if;

  return jsonb_build_object(
    'user_id',p_shop_user_id,
    'deleted',true,
    'unopened',true,
    'can_register_again',true
  );
end
$$;

revoke all on function public.queuego_admin_delete_unopened_shop(uuid,text) from public,anon;
grant execute on function public.queuego_admin_delete_unopened_shop(uuid,text) to authenticated;
