create or replace function public.queuego_start_new_shop_application(p_shop_name text,p_category text)
returns uuid
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_uid uuid:=public.get_my_user_id();
  v_id uuid;
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
  if public.get_my_role() is distinct from 'shop' then
    raise exception 'SHOP_LOGIN_REQUIRED' using errcode='42501';
  end if;
  if exists(select 1 from public.shop_profiles where user_id=v_uid and archived_at is null) then
    raise exception 'CURRENT_SHOP_EXISTS';
  end if;
  if nullif(trim(coalesce(p_shop_name,'')),'') is null then raise exception 'SHOP_NAME_REQUIRED'; end if;
  if p_category not in ('food','cafe','grocery','market','laundry','other') then raise exception 'SHOP_CATEGORY_REQUIRED'; end if;
  insert into public.shop_profiles(user_id,shop_name,status,onboarding_status,metadata)
  values(v_uid,trim(p_shop_name),'pending','draft',jsonb_build_object('shopName',trim(p_shop_name),'category',p_category))
  returning id into v_id;
  update public.users set status='pending',updated_at=now() where id=v_uid and role='shop';
  return v_id;
end
$$;
revoke all on function public.queuego_start_new_shop_application(text,text) from public,anon;
grant execute on function public.queuego_start_new_shop_application(text,text) to authenticated;
