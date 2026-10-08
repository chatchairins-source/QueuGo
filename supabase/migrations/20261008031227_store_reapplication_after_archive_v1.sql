do $$
declare r record;
begin
 for r in
  select c.conname
  from pg_constraint c
  where c.conrelid='public.shop_profiles'::regclass and c.contype='u'
    and (select array_agg(a.attname order by x.ord)
         from unnest(c.conkey) with ordinality x(attnum,ord)
         join pg_attribute a on a.attrelid=c.conrelid and a.attnum=x.attnum)=array['user_id']::name[]
 loop
  execute format('alter table public.shop_profiles drop constraint %I',r.conname);
 end loop;
end $$;
create unique index if not exists shop_profiles_one_current_per_owner
 on public.shop_profiles(user_id) where archived_at is null;

create or replace function public.queuego_start_new_shop_application(p_shop_name text,p_category text)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_uid uuid:=public.get_my_user_id(); v_id uuid;
begin
 if v_uid is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
 if exists(select 1 from public.shop_profiles where user_id=v_uid and archived_at is null) then raise exception 'CURRENT_SHOP_EXISTS'; end if;
 if nullif(trim(coalesce(p_shop_name,'')),'') is null then raise exception 'SHOP_NAME_REQUIRED'; end if;
 if p_category not in ('food','cafe','grocery','market','laundry','other') then raise exception 'SHOP_CATEGORY_REQUIRED'; end if;
 insert into public.shop_profiles(user_id,shop_name,public_category,status,onboarding_status,metadata)
 values(v_uid,trim(p_shop_name),p_category,'pending','draft',jsonb_build_object('shopName',trim(p_shop_name),'category',p_category))
 returning id into v_id;
 update public.users set status='pending',updated_at=now() where id=v_uid and role='shop';
 return v_id;
end $$;
grant execute on function public.queuego_start_new_shop_application(text,text) to authenticated;
