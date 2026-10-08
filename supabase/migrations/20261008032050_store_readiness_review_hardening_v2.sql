create or replace function public.queuego_shop_readiness(p_shop_id uuid default null)
returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_shop public.shop_profiles%rowtype;
  v_user public.users%rowtype;
  v_category text;
  v_front text;
  v_cover text;
  v_info boolean:=false;
  v_hours boolean:=false;
  v_front_ok boolean:=false;
  v_cover_ok boolean:=false;
  v_location boolean:=false;
  v_catalog boolean:=false;
  v_catalog_count integer:=0;
  v_sample jsonb:=null;
  v_missing text[]:='{}'::text[];
begin
  if p_shop_id is null then
    select sp.* into v_shop
    from public.shop_profiles sp
    where sp.user_id=public.get_my_user_id() and sp.archived_at is null
    order by sp.created_at desc limit 1;
  else
    select sp.* into v_shop
    from public.shop_profiles sp
    where sp.id=p_shop_id
      and (sp.user_id=public.get_my_user_id() or public.is_active_admin())
    limit 1;
  end if;
  if not found then raise exception 'SHOP_NOT_FOUND' using errcode='P0002'; end if;

  select * into v_user from public.users where id=v_shop.user_id;
  v_category:=lower(trim(coalesce(v_shop.public_category,v_shop.metadata->>'category','')));
  v_front:=coalesce(
    nullif(trim(v_shop.public_logo),''),
    nullif(trim(v_shop.metadata->>'logo'),''),
    nullif(trim(v_shop.metadata->>'profileImage'),''),
    nullif(trim(v_shop.metadata->>'profile_image'),''),
    nullif(trim(v_shop.metadata->>'avatar'),'')
  );
  v_cover:=coalesce(
    nullif(trim(v_shop.public_cover),''),
    nullif(trim(v_shop.metadata->>'cover'),''),
    nullif(trim(v_shop.metadata->>'coverImage'),'')
  );

  v_hours:=(
    nullif(trim(coalesce(v_shop.public_open_time,v_shop.metadata->>'openTime','')),'') is not null
    and nullif(trim(coalesce(v_shop.public_close_time,v_shop.metadata->>'closeTime','')),'') is not null
  ) or exists(
    select 1 from public.shop_business_hours h
    where h.shop_id=v_shop.id and h.is_closed=false
      and h.opens_at is not null and h.closes_at is not null
  );

  v_info:=
    nullif(trim(coalesce(v_shop.shop_name,'')),'') is not null
    and nullif(trim(coalesce(v_user.name,'')),'') is not null
    and nullif(trim(coalesce(v_shop.phone,v_user.phone,'')),'') is not null
    and nullif(v_category,'') is not null
    and nullif(trim(coalesce(v_shop.address,'')),'') is not null
    and v_hours;

  v_front_ok:=coalesce(v_front,'') ~ '^https://.+/storage/v1/object/'
    and lower(v_front) not like '%placeholder%'
    and lower(v_front) not like '%default-shop%';
  v_cover_ok:=coalesce(v_cover,'') ~ '^https://.+/storage/v1/object/'
    and lower(v_cover) not like '%placeholder%'
    and lower(v_cover) not like '%default-shop%';

  v_location:=v_shop.latitude between 5 and 21 and v_shop.longitude between 97 and 106;

  if v_category='laundry' then
    select count(*)::int into v_catalog_count
    from public.laundry_services ls
    join public.laundry_hubs lh on lh.id=ls.hub_id
    where lh.shop_id=v_shop.id and lh.active=true
      and ls.active=true
      and nullif(trim(coalesce(ls.name,'')),'') is not null
      and coalesce(ls.price,0)>0;
    select jsonb_build_object('id',ls.id,'name',ls.name,'price',ls.price,'kind','service')
      into v_sample
    from public.laundry_services ls
    join public.laundry_hubs lh on lh.id=ls.hub_id
    where lh.shop_id=v_shop.id and lh.active=true
      and ls.active=true
      and nullif(trim(coalesce(ls.name,'')),'') is not null
      and coalesce(ls.price,0)>0
    order by ls.sort_order,ls.created_at
    limit 1;
  else
    select count(*)::int into v_catalog_count
    from public.products p
    where p.shop_id=v_shop.id and p.available is true
      and nullif(trim(coalesce(p.name,'')),'') is not null
      and coalesce(p.price,0)>0;
    select jsonb_build_object('id',p.id,'name',p.name,'price',p.price,'image',p.image,'kind','product')
      into v_sample
    from public.products p
    where p.shop_id=v_shop.id and p.available is true
      and nullif(trim(coalesce(p.name,'')),'') is not null
      and coalesce(p.price,0)>0
    order by p.created_at,p.id
    limit 1;
  end if;
  v_catalog:=v_catalog_count>0;

  if not v_info then v_missing:=array_append(v_missing,'shop_info'); end if;
  if not v_front_ok then v_missing:=array_append(v_missing,'storefront_image'); end if;
  if not v_cover_ok then v_missing:=array_append(v_missing,'cover_image'); end if;
  if not v_location then v_missing:=array_append(v_missing,'location'); end if;
  if not v_catalog then v_missing:=array_append(v_missing,'catalog'); end if;

  return jsonb_build_object(
    'shop_id',v_shop.id,
    'category',v_category,
    'catalog_kind',case when v_category='laundry' then 'service' else 'product' end,
    'onboarding_status',v_shop.onboarding_status,
    'submitted_for_review_at',v_shop.submitted_for_review_at,
    'approved_at',v_shop.approved_at,
    'complete',cardinality(v_missing)=0,
    'completed_count',
      v_info::int+v_front_ok::int+v_cover_ok::int+v_location::int+v_catalog::int,
    'total_count',5,
    'catalog_count',v_catalog_count,
    'product_count',case when v_category='laundry' then 0 else v_catalog_count end,
    'sample_item',v_sample,
    'missing_codes',to_jsonb(v_missing),
    'checks',jsonb_build_object(
      'shop_info',v_info,
      'hours',v_hours,
      'storefront_image',v_front_ok,
      'cover_image',v_cover_ok,
      'location',v_location,
      'product',v_catalog,
      'catalog',v_catalog
    )
  );
end
$$;

revoke all on function public.queuego_shop_readiness(uuid) from public,anon;
grant execute on function public.queuego_shop_readiness(uuid) to authenticated;

create or replace function public.queuego_submit_shop_for_review(p_shop_id uuid default null)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_shop_id uuid; v_ready jsonb; v_status text;
begin
  if public.get_my_role() is distinct from 'shop' then
    raise exception 'SHOP_LOGIN_REQUIRED' using errcode='42501';
  end if;
  if p_shop_id is null then
    select id into v_shop_id
    from public.shop_profiles
    where user_id=public.get_my_user_id() and archived_at is null
    order by created_at desc limit 1;
  else v_shop_id:=p_shop_id; end if;

  select onboarding_status into v_status
  from public.shop_profiles
  where id=v_shop_id and user_id=public.get_my_user_id() and archived_at is null
  for update;
  if not found then raise exception 'SHOP_NOT_FOUND' using errcode='P0002'; end if;

  if v_status in ('pending_approval','approved') then
    return public.queuego_shop_readiness(v_shop_id)
      ||jsonb_build_object('submitted',false,'duplicate',true);
  end if;

  v_ready:=public.queuego_shop_readiness(v_shop_id);
  if not coalesce((v_ready->>'complete')::boolean,false) then
    raise exception 'SHOP_NOT_READY';
  end if;

  perform set_config('queuego.store_review_context','on',true);
  update public.shop_profiles
  set onboarding_status='pending_approval',
      submitted_for_review_at=now(),
      metadata=coalesce(metadata,'{}'::jsonb)-'approval_note',
      updated_at=now()
  where id=v_shop_id;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(public.get_my_user_id(),'shop_submit_review','shop_profiles',v_shop_id,
         'merchant submitted store for review',jsonb_build_object('readiness',v_ready));

  return public.queuego_shop_readiness(v_shop_id)
    ||jsonb_build_object('submitted',true,'duplicate',false);
end
$$;
revoke all on function public.queuego_submit_shop_for_review(uuid) from public,anon;
grant execute on function public.queuego_submit_shop_for_review(uuid) to authenticated;

create or replace function public.queuego_shop_review_fields_guard()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
begin
  if not public.is_active_admin()
     and current_setting('queuego.store_review_context',true) is distinct from 'on'
     and (
       new.status is distinct from old.status
       or new.onboarding_status is distinct from old.onboarding_status
       or new.submitted_for_review_at is distinct from old.submitted_for_review_at
       or new.approved_at is distinct from old.approved_at
       or new.archived_at is distinct from old.archived_at
     )
  then
    raise exception 'SHOP_REVIEW_FIELDS_SERVER_ONLY' using errcode='42501';
  end if;
  return new;
end
$$;
revoke all on function public.queuego_shop_review_fields_guard() from public,anon,authenticated;

drop trigger if exists queuego_shop_review_fields_guard_trg on public.shop_profiles;
create trigger queuego_shop_review_fields_guard_trg
before update of status,onboarding_status,submitted_for_review_at,approved_at,archived_at
on public.shop_profiles
for each row execute function public.queuego_shop_review_fields_guard();

create or replace function public.queuego_shop_readiness_guard()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_ready jsonb;
begin
  if new.status='active' and old.status is distinct from 'active' then
    if not public.is_active_admin() then
      raise exception 'SHOP_APPROVAL_REQUIRES_ADMIN' using errcode='42501';
    end if;
    if old.onboarding_status is distinct from 'pending_approval'
       or old.submitted_for_review_at is null then
      raise exception 'SHOP_REVIEW_SUBMISSION_REQUIRED';
    end if;
    v_ready:=public.queuego_shop_readiness(new.id);
    if not coalesce((v_ready->>'complete')::boolean,false) then
      raise exception 'SHOP_NOT_READY';
    end if;
  elsif new.delivery_enabled=true and old.delivery_enabled is distinct from true then
    v_ready:=public.queuego_shop_readiness(new.id);
    if not coalesce((v_ready->>'complete')::boolean,false) then
      raise exception 'SHOP_NOT_READY';
    end if;
  end if;
  return new;
end
$$;
revoke all on function public.queuego_shop_readiness_guard() from public,anon,authenticated;

create or replace function public.queuego_admin_approve_shop(p_shop_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_uid uuid; v_ready jsonb;
begin
  if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;

  select user_id into v_uid
  from public.shop_profiles
  where id=p_shop_id and archived_at is null
    and onboarding_status='pending_approval'
    and submitted_for_review_at is not null
  for update;
  if not found then raise exception 'SHOP_NOT_PENDING'; end if;

  v_ready:=public.queuego_shop_readiness(p_shop_id);
  if not coalesce((v_ready->>'complete')::boolean,false) then raise exception 'SHOP_NOT_READY'; end if;

  update public.shop_profiles
  set status='active',onboarding_status='approved',approved_at=now(),
      metadata=coalesce(metadata,'{}'::jsonb)-'approval_note',
      updated_at=now()
  where id=p_shop_id;

  update public.users set status='active',updated_at=now()
  where id=v_uid and role='shop';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(public.get_my_user_id(),'shop_approve','shop_profiles',p_shop_id,
         'approved merchant store',jsonb_build_object('readiness',v_ready));

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_uid,'ร้านของคุณได้รับการอนุมัติแล้ว','QueueGo เปิดใช้งานร้านของคุณแล้ว','system',p_shop_id);

  return public.queuego_shop_readiness(p_shop_id);
end
$$;
revoke all on function public.queuego_admin_approve_shop(uuid) from public,anon;
grant execute on function public.queuego_admin_approve_shop(uuid) to authenticated;

create or replace function public.queuego_admin_request_shop_changes(p_shop_id uuid,p_reason text default null)
returns void
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_uid uuid; v_reason text:=nullif(trim(coalesce(p_reason,'')),'');
begin
  if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;
  if v_reason is null then raise exception 'CHANGE_REASON_REQUIRED'; end if;
  if length(v_reason)>1000 then raise exception 'CHANGE_REASON_TOO_LONG'; end if;

  select user_id into v_uid
  from public.shop_profiles
  where id=p_shop_id and archived_at is null and onboarding_status='pending_approval'
  for update;
  if not found then raise exception 'SHOP_NOT_PENDING'; end if;

  update public.shop_profiles
  set onboarding_status='needs_changes',status='pending',
      submitted_for_review_at=null,
      metadata=coalesce(metadata,'{}'::jsonb)
        ||jsonb_build_object('approval_note',v_reason,'reviewed_at',now()),
      updated_at=now()
  where id=p_shop_id;

  update public.users set status='pending',updated_at=now()
  where id=v_uid and role='shop';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(public.get_my_user_id(),'shop_request_changes','shop_profiles',p_shop_id,
         v_reason,jsonb_build_object('shop_user_id',v_uid));

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_uid,'กรุณาแก้ไขข้อมูลร้าน',v_reason,'system',p_shop_id);
end
$$;
revoke all on function public.queuego_admin_request_shop_changes(uuid,text) from public,anon;
grant execute on function public.queuego_admin_request_shop_changes(uuid,text) to authenticated;

create or replace function public.queuego_admin_reject_shop(p_shop_id uuid,p_reason text default null)
returns void
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_uid uuid; v_reason text:=nullif(trim(coalesce(p_reason,'')),'');
begin
  if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;
  if v_reason is null then raise exception 'REJECTION_REASON_REQUIRED'; end if;
  if length(v_reason)>1000 then raise exception 'REJECTION_REASON_TOO_LONG'; end if;

  select user_id into v_uid
  from public.shop_profiles
  where id=p_shop_id and archived_at is null and onboarding_status='pending_approval'
  for update;
  if not found then raise exception 'SHOP_NOT_PENDING'; end if;

  update public.shop_profiles
  set status='rejected',onboarding_status='rejected',
      metadata=coalesce(metadata,'{}'::jsonb)
        ||jsonb_build_object('approval_note',v_reason,'reviewed_at',now()),
      updated_at=now()
  where id=p_shop_id;

  update public.users set status='rejected',updated_at=now()
  where id=v_uid and role='shop';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(public.get_my_user_id(),'shop_reject','shop_profiles',p_shop_id,
         v_reason,jsonb_build_object('shop_user_id',v_uid));

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_uid,'ผลการตรวจร้านค้า',v_reason,'system',p_shop_id);
end
$$;
revoke all on function public.queuego_admin_reject_shop(uuid,text) from public,anon;
grant execute on function public.queuego_admin_reject_shop(uuid,text) to authenticated;

create or replace function public.queuego_admin_archive_shop(p_shop_id uuid,p_reason text default null)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_uid uuid; v_orders bigint; v_active bigint; v_reason text:=nullif(trim(coalesce(p_reason,'')),'');
begin
  if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;

  select user_id into v_uid
  from public.shop_profiles
  where id=p_shop_id and archived_at is null
  for update;
  if not found then raise exception 'SHOP_NOT_FOUND'; end if;

  select count(*) into v_orders from public.orders where shop_id=p_shop_id;
  select count(*) into v_active
  from public.orders
  where shop_id=p_shop_id
    and status not in ('completed','cancelled','no_rider_available');
  if v_active>0 then raise exception 'SHOP_HAS_ACTIVE_ORDERS'; end if;

  update public.shop_profiles
  set status='deleted',onboarding_status='archived',archived_at=now(),delivery_enabled=false,
      metadata=coalesce(metadata,'{}'::jsonb)
        ||jsonb_build_object('archive_reason',v_reason,'archived_by',public.get_my_user_id(),'archived_at',now()),
      updated_at=now()
  where id=p_shop_id;

  insert into public.shop_open_states(shop_id,is_open,resume_at,updated_at)
  values(p_shop_id,false,null,now())
  on conflict(shop_id) do update
    set is_open=false,resume_at=null,updated_at=excluded.updated_at;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(public.get_my_user_id(),'shop_archive','shop_profiles',p_shop_id,
         'archived merchant store',
         jsonb_build_object('order_count',v_orders,'reason',v_reason,'owner_user_id',v_uid));

  return jsonb_build_object(
    'shop_id',p_shop_id,'archived',true,'order_count',v_orders,
    'owner_user_id',v_uid,'history_preserved',true,'can_register_again',true
  );
end
$$;
revoke all on function public.queuego_admin_archive_shop(uuid,text) from public,anon;
grant execute on function public.queuego_admin_archive_shop(uuid,text) to authenticated;

create or replace function public.admin_delete_user(target_user_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare v_role text; v_auth uuid;
begin
  if public.get_my_role() is distinct from 'admin' then
    raise exception 'admin only' using errcode='42501';
  end if;
  select role,auth_user_id into v_role,v_auth
  from public.users where id=target_user_id;
  if not found then raise exception 'user not found'; end if;
  if v_role='admin' then raise exception 'cannot delete admin account'; end if;
  if v_role='shop' then raise exception 'SHOP_ARCHIVE_REQUIRED'; end if;
  delete from public.users where id=target_user_id;
  if v_auth is not null then delete from auth.users where id=v_auth; end if;
end
$$;

create or replace function public.queuego_admin_shop_application(p_shop_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_user public.users%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_ready jsonb;
begin
  if not public.is_active_admin() then raise exception 'admin only' using errcode='42501'; end if;

  select * into v_user from public.users where id=p_shop_user_id and role='shop';
  if not found then raise exception 'SHOP_USER_NOT_FOUND'; end if;

  select * into v_shop
  from public.shop_profiles
  where user_id=v_user.id
  order by (archived_at is null) desc,created_at desc
  limit 1;
  if not found then raise exception 'SHOP_NOT_FOUND'; end if;

  v_ready:=public.queuego_shop_readiness(v_shop.id);

  return jsonb_build_object(
    'user',jsonb_build_object(
      'id',v_user.id,'name',v_user.name,'phone',v_user.phone,
      'status',v_user.status,'created_at',v_user.created_at
    ),
    'shop',jsonb_build_object(
      'id',v_shop.id,'shop_name',v_shop.shop_name,'phone',v_shop.phone,
      'address',v_shop.address,'latitude',v_shop.latitude,'longitude',v_shop.longitude,
      'status',v_shop.status,'onboarding_status',v_shop.onboarding_status,
      'category',coalesce(v_shop.public_category,v_shop.metadata->>'category'),
      'front_image',coalesce(v_shop.public_logo,v_shop.metadata->>'logo',v_shop.metadata->>'profileImage',v_shop.metadata->>'profile_image',v_shop.metadata->>'avatar'),
      'cover_image',coalesce(v_shop.public_cover,v_shop.metadata->>'cover',v_shop.metadata->>'coverImage'),
      'open_time',coalesce(v_shop.public_open_time,v_shop.metadata->>'openTime'),
      'close_time',coalesce(v_shop.public_close_time,v_shop.metadata->>'closeTime'),
      'description',coalesce(v_shop.public_description,v_shop.metadata->>'description'),
      'market_membership_status',v_shop.market_membership_status,
      'created_at',v_shop.created_at,
      'submitted_for_review_at',v_shop.submitted_for_review_at,
      'approved_at',v_shop.approved_at,
      'archived_at',v_shop.archived_at,
      'approval_note',v_shop.metadata->>'approval_note'
    ),
    'readiness',v_ready,
    'order_count',(select count(*) from public.orders where shop_id=v_shop.id),
    'catalog_count',coalesce((v_ready->>'catalog_count')::int,0),
    'sample_item',v_ready->'sample_item'
  );
end
$$;
revoke all on function public.queuego_admin_shop_application(uuid) from public,anon;
grant execute on function public.queuego_admin_shop_application(uuid) to authenticated;

create or replace function public.queuego_laundry_merchant_setup(p_name text default null)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_user uuid:=public.get_my_user_id();
  v_shop public.shop_profiles%rowtype;
  v_hub public.laundry_hubs%rowtype;
begin
  if v_user is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;

  select * into v_shop
  from public.shop_profiles
  where user_id=v_user and archived_at is null
    and status in ('pending','rejected','active')
  order by created_at desc
  limit 1
  for update;
  if not found then raise exception 'shop profile required'; end if;
  if lower(coalesce(v_shop.public_category,v_shop.metadata->>'category',''))<>'laundry' then
    raise exception 'laundry shop category required';
  end if;

  select * into v_hub
  from public.laundry_hubs
  where shop_id=v_shop.id
  order by created_at limit 1
  for update;

  if v_hub.id is null then
    insert into public.laundry_hubs(shop_id,name,active)
    values(v_shop.id,left(coalesce(nullif(trim(p_name),''),v_shop.shop_name),160),true)
    returning * into v_hub;
  elsif nullif(trim(coalesce(p_name,'')),'') is not null then
    update public.laundry_hubs
    set name=left(trim(p_name),160)
    where id=v_hub.id returning * into v_hub;
  end if;

  insert into public.laundry_shop_settings(
    hub_id,enabled,accepts_pickup,accepts_return,minimum_order,
    base_pickup_fee,return_fee,round_trip_fee,delivery_fee_mode
  ) values(v_hub.id,false,true,true,0,0,0,0,'separate')
  on conflict(hub_id) do nothing;

  return jsonb_build_object('hub_id',v_hub.id,'hub_name',v_hub.name,'active',v_hub.active);
end
$$;
revoke all on function public.queuego_laundry_merchant_setup(text) from public,anon;
grant execute on function public.queuego_laundry_merchant_setup(text) to authenticated;
