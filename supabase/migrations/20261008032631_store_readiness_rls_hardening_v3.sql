create or replace function public.queuego_shop_insert_review_guard()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
begin
  if not public.is_active_admin() then
    if new.status is distinct from 'pending'
       or coalesce(new.onboarding_status,'draft') <> 'draft'
       or new.submitted_for_review_at is not null
       or new.approved_at is not null
       or new.archived_at is not null
    then
      raise exception 'SHOP_APPLICATION_MUST_START_DRAFT' using errcode='42501';
    end if;
  end if;
  return new;
end
$$;
revoke all on function public.queuego_shop_insert_review_guard() from public,anon,authenticated;

drop trigger if exists queuego_shop_insert_review_guard_trg on public.shop_profiles;
create trigger queuego_shop_insert_review_guard_trg
before insert on public.shop_profiles
for each row execute function public.queuego_shop_insert_review_guard();

drop policy if exists shop_profiles_shop_delete on public.shop_profiles;

drop policy if exists products_customer_select on public.products;
create policy products_customer_select
on public.products
for select
to authenticated
using (
  available is true
  and exists (
    select 1
    from public.shop_profiles sp
    where sp.id=products.shop_id
      and sp.status='active'
      and sp.archived_at is null
      and sp.onboarding_status='approved'
  )
);

drop policy if exists users_customer_select_shops on public.users;
create policy users_customer_select_shops
on public.users
for select
to authenticated
using (
  role='shop'
  and status='active'
  and exists (
    select 1
    from public.shop_profiles sp
    where sp.user_id=users.id
      and sp.status='active'
      and sp.archived_at is null
      and sp.onboarding_status='approved'
  )
);

drop policy if exists users_guest_active_shops on public.users;
create policy users_guest_active_shops
on public.users
for select
to anon
using (
  role='shop'
  and status='active'
  and exists (
    select 1
    from public.shop_profiles sp
    where sp.user_id=users.id
      and sp.status='active'
      and sp.archived_at is null
      and sp.onboarding_status='approved'
  )
);

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

  update public.users
  set status='pending',updated_at=now()
  where id=v_uid and role='shop';

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
