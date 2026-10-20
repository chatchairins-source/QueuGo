create or replace function public.qg_admin_update_shop_category(
  p_shop_id uuid,
  p_category text,
  p_subcategories text[],
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path='public','pg_temp'
as $$
declare
  v_admin uuid;
  v_category text:=lower(trim(coalesce(p_category,'')));
  v_subcategories text[]:=coalesce(p_subcategories,'{}'::text[]);
  v_before jsonb;
  v_after jsonb;
begin
  perform qg_private.assert_live_session();
  if auth.uid() is null or public.is_active_admin() is not true then
    raise exception 'active admin required' using errcode='42501';
  end if;
  if v_category not in ('food','cafe','grocery','laundry','market','shopping','other') then
    raise exception 'unsupported shop category' using errcode='22023';
  end if;
  if length(trim(coalesce(p_reason,'')))<3 or length(p_reason)>500 then
    raise exception 'reason must be 3-500 characters' using errcode='22023';
  end if;
  if exists(
    select 1 from unnest(v_subcategories) s
    where s not in ('mobile_accessories','computer_it','automotive_car','automotive_motorcycle','fashion_accessories','toys','home_decor')
  ) then
    raise exception 'unsupported shopping subcategory' using errcode='22023';
  end if;
  if v_category='shopping' and cardinality(v_subcategories)=0 then
    raise exception 'shopping requires at least one subcategory' using errcode='22023';
  end if;
  if v_category<>'shopping' then v_subcategories:='{}'::text[]; end if;

  select jsonb_build_object('category',public_category,'subcategories',public_subcategories)
  into v_before
  from public.shop_profiles
  where id=p_shop_id and archived_at is null
  for update;
  if not found then raise exception 'shop not found' using errcode='P0002'; end if;

  v_admin:=public.get_my_user_id();

  update public.shop_profiles
  set public_category=v_category,
      metadata=jsonb_set(
        jsonb_set(coalesce(metadata,'{}'::jsonb),'{category}',to_jsonb(v_category),true),
        '{shoppingSubcategories}',to_jsonb(v_subcategories),true
      ),
      updated_at=now()
  where id=p_shop_id;

  select jsonb_build_object('category',public_category,'subcategories',public_subcategories)
  into v_after
  from public.shop_profiles
  where id=p_shop_id;

  if v_before is distinct from v_after then
    insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
    values(
      v_admin,'admin_shop_category_update','shop_profiles',p_shop_id,trim(p_reason),
      jsonb_build_object('before',v_before,'after',v_after,'actorId',v_admin,'actorRole','admin','reason',trim(p_reason))
    );
  end if;

  return v_after||jsonb_build_object('shop_id',p_shop_id,'changed',v_before is distinct from v_after);
end
$$;

revoke all on function public.qg_admin_update_shop_category(uuid,text,text[],text) from public,anon;
grant execute on function public.qg_admin_update_shop_category(uuid,text,text[],text) to authenticated,service_role;
