create or replace function public.queuego_admin_archive_shop(p_shop_id uuid,p_reason text default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_uid uuid; v_orders bigint;
begin
 if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;
 select user_id into v_uid from public.shop_profiles where id=p_shop_id and archived_at is null for update;
 if not found then raise exception 'SHOP_NOT_FOUND'; end if;
 select count(*) into v_orders from public.orders where shop_id=p_shop_id;
 update public.shop_profiles set status='deleted',onboarding_status='archived',archived_at=now(),delivery_enabled=false,
 metadata=coalesce(metadata,'{}'::jsonb)||jsonb_build_object('archive_reason',nullif(trim(coalesce(p_reason,'')),'')),updated_at=now()
 where id=p_shop_id;
 insert into public.shop_open_states(shop_id,is_open,resume_at,updated_at) values(p_shop_id,false,null,now())
 on conflict(shop_id) do update set is_open=false,resume_at=null,updated_at=excluded.updated_at;
 update public.users set status='pending',updated_at=now() where id=v_uid and role='shop';
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(public.get_my_user_id(),'shop_archive','shop_profiles',p_shop_id,'archived merchant store',jsonb_build_object('order_count',v_orders,'reason',p_reason));
 return jsonb_build_object('shop_id',p_shop_id,'archived',true,'order_count',v_orders,'owner_user_id',v_uid);
end $$;
revoke all on function public.queuego_admin_archive_shop(uuid,text) from public,anon;
grant execute on function public.queuego_admin_archive_shop(uuid,text) to authenticated;
