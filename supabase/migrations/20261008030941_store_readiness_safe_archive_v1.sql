alter table public.shop_profiles
  add column if not exists onboarding_status text not null default 'draft',
  add column if not exists submitted_for_review_at timestamptz,
  add column if not exists approved_at timestamptz,
  add column if not exists archived_at timestamptz;

do $$ begin
  if not exists (select 1 from pg_constraint where conname='shop_profiles_onboarding_status_check') then
    alter table public.shop_profiles add constraint shop_profiles_onboarding_status_check
      check (onboarding_status in ('draft','ready_for_review','pending_approval','approved','needs_changes','rejected','archived')) not valid;
  end if;
end $$;

create or replace function public.queuego_shop_readiness(p_shop_id uuid default null)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare
 v_shop public.shop_profiles%rowtype; v_phone text; v_product_count int:=0;
 v_logo text; v_cover text; v_info boolean; v_logo_ok boolean; v_cover_ok boolean; v_location boolean; v_product boolean;
begin
 if p_shop_id is null then
   select sp.* into v_shop from public.shop_profiles sp where sp.user_id=public.get_my_user_id() and sp.archived_at is null order by sp.created_at desc limit 1;
 else
   select sp.* into v_shop from public.shop_profiles sp where sp.id=p_shop_id
     and (sp.user_id=public.get_my_user_id() or public.is_active_admin()) limit 1;
 end if;
 if not found then raise exception 'SHOP_NOT_FOUND' using errcode='P0002'; end if;
 select nullif(trim(u.phone),'') into v_phone from public.users u where u.id=v_shop.user_id;
 v_logo:=coalesce(v_shop.metadata->>'logo',v_shop.metadata->>'profileImage',v_shop.metadata->>'profile_image');
 v_cover:=v_shop.metadata->>'cover';
 v_info:=nullif(trim(coalesce(v_shop.shop_name,'')),'') is not null
   and nullif(trim(coalesce(v_shop.public_category,v_shop.metadata->>'category','')),'') is not null
   and v_phone is not null
   and nullif(trim(coalesce(v_shop.metadata->>'openTime','')),'') is not null
   and nullif(trim(coalesce(v_shop.metadata->>'closeTime','')),'') is not null;
 v_logo_ok:=coalesce(v_logo,'') ~ '^https://.+/storage/v1/object/';
 v_cover_ok:=coalesce(v_cover,'') ~ '^https://.+/storage/v1/object/';
 v_location:=v_shop.latitude between 5 and 21 and v_shop.longitude between 97 and 106;
 select count(*) into v_product_count from public.products p
   where p.shop_id=v_shop.id and p.available is true and nullif(trim(coalesce(p.name,'')),'') is not null and coalesce(p.price,0)>0;
 v_product:=v_product_count>0;
 return jsonb_build_object(
  'shop_id',v_shop.id,'onboarding_status',v_shop.onboarding_status,
  'complete',(v_info and v_logo_ok and v_cover_ok and v_location and v_product),
  'completed_count',(v_info::int+v_logo_ok::int+v_cover_ok::int+v_location::int+v_product::int),
  'total_count',5,'product_count',v_product_count,
  'checks',jsonb_build_object('shop_info',v_info,'storefront_image',v_logo_ok,'cover_image',v_cover_ok,'location',v_location,'product',v_product)
 );
end $$;

create or replace function public.queuego_submit_shop_for_review(p_shop_id uuid default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop_id uuid; v_ready jsonb; v_status text;
begin
 if p_shop_id is null then
   select id into v_shop_id from public.shop_profiles where user_id=public.get_my_user_id() and archived_at is null order by created_at desc limit 1;
 else v_shop_id:=p_shop_id; end if;
 select onboarding_status into v_status from public.shop_profiles where id=v_shop_id and user_id=public.get_my_user_id() and archived_at is null for update;
 if not found then raise exception 'SHOP_NOT_FOUND' using errcode='P0002'; end if;
 if v_status in ('pending_approval','approved') then return public.queuego_shop_readiness(v_shop_id)||jsonb_build_object('submitted',false,'duplicate',true); end if;
 v_ready:=public.queuego_shop_readiness(v_shop_id);
 if not coalesce((v_ready->>'complete')::boolean,false) then raise exception 'SHOP_NOT_READY'; end if;
 update public.shop_profiles set onboarding_status='pending_approval',submitted_for_review_at=coalesce(submitted_for_review_at,now()),updated_at=now()
 where id=v_shop_id;
 return public.queuego_shop_readiness(v_shop_id)||jsonb_build_object('submitted',true,'duplicate',false);
end $$;

create or replace function public.queuego_admin_approve_shop(p_shop_id uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_uid uuid; v_ready jsonb;
begin
 if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;
 select user_id into v_uid from public.shop_profiles where id=p_shop_id and archived_at is null and onboarding_status='pending_approval' for update;
 if not found then raise exception 'SHOP_NOT_PENDING'; end if;
 v_ready:=public.queuego_shop_readiness(p_shop_id);
 if not coalesce((v_ready->>'complete')::boolean,false) then raise exception 'SHOP_NOT_READY'; end if;
 update public.shop_profiles set status='active',onboarding_status='approved',approved_at=now(),updated_at=now() where id=p_shop_id;
 update public.users set status='active',updated_at=now() where id=v_uid and role='shop';
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(public.get_my_user_id(),'shop_approve','shop_profiles',p_shop_id,'approved merchant store',jsonb_build_object('readiness',v_ready));
 return public.queuego_shop_readiness(p_shop_id);
end $$;

create or replace function public.queuego_admin_request_shop_changes(p_shop_id uuid,p_reason text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;
 update public.shop_profiles set onboarding_status='needs_changes',status='pending',
   metadata=coalesce(metadata,'{}'::jsonb)||jsonb_build_object('approval_note',nullif(trim(coalesce(p_reason,'')),'')),updated_at=now()
 where id=p_shop_id and archived_at is null;
 if not found then raise exception 'SHOP_NOT_FOUND'; end if;
end $$;

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
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(public.get_my_user_id(),'shop_archive','shop_profiles',p_shop_id,'archived merchant store',jsonb_build_object('order_count',v_orders,'reason',p_reason));
 return jsonb_build_object('shop_id',p_shop_id,'archived',true,'order_count',v_orders,'owner_user_id',v_uid);
end $$;

grant execute on function public.queuego_shop_readiness(uuid) to authenticated;
grant execute on function public.queuego_submit_shop_for_review(uuid) to authenticated;
grant execute on function public.queuego_admin_approve_shop(uuid) to authenticated;
grant execute on function public.queuego_admin_request_shop_changes(uuid,text) to authenticated;
grant execute on function public.queuego_admin_archive_shop(uuid,text) to authenticated;

create or replace function public.queuego_shop_readiness_guard()
returns trigger language plpgsql set search_path=public,pg_temp as $$
declare v_logo text; v_cover text; v_product boolean;
begin
 v_logo:=coalesce(new.metadata->>'logo',new.metadata->>'profileImage',new.metadata->>'profile_image');
 v_cover:=new.metadata->>'cover';
 if ((new.status='active' and old.status is distinct from 'active') or (new.delivery_enabled=true and old.delivery_enabled is distinct from true)) then
  if new.latitude is null or new.longitude is null or (new.latitude=0 and new.longitude=0) or new.latitude<5 or new.latitude>21 or new.longitude<97 or new.longitude>106 then raise exception 'SHOP_LOCATION_REQUIRED'; end if;
  if coalesce(v_logo,'') !~ '^https://.+/storage/v1/object/' or coalesce(v_cover,'') !~ '^https://.+/storage/v1/object/' then raise exception 'SHOP_IMAGES_REQUIRED'; end if;
  select exists(select 1 from public.products p where p.shop_id=new.id and p.available is true and nullif(trim(coalesce(p.name,'')),'') is not null and coalesce(p.price,0)>0) into v_product;
  if not v_product then raise exception 'SHOP_PRODUCT_REQUIRED'; end if;
 end if;
 return new;
end $$;

create or replace function public.queuego_public_shop_visible(p_shop public.shop_profiles)
returns boolean language sql stable as $$
 select p_shop.status='active' and p_shop.archived_at is null and p_shop.onboarding_status in ('approved','draft');
$$;
