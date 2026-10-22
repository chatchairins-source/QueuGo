-- Kitchen permissions are a shop capability, not a staff or pricing tier.
-- Keep the existing POS RPCs and their grants; reject Kitchen access at the
-- authorization boundary when the merchant is not a food/cafe shop.
begin;

create or replace function public.pos_allowed(p_permission text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select
    (
      p_permission not in ('send_kitchen', 'cook_order', 'ready_order', 'serve_order')
      or exists (
        select 1
        from public.shop_profiles sp
        where sp.id = public.pos_my_shop()
          and coalesce(
            lower(coalesce(nullif(trim(sp.public_category), ''), nullif(trim(sp.metadata->>'category'), ''), ''))
              in ('food', 'cafe'),
            false
          )
      )
    )
    and (
      public.pos_is_owner()
      or exists (
        select 1
        from public.pos_staff s
        where s.user_id = (select auth.uid())
          and s.shop_id = public.pos_my_shop()
          and s.active
          and case
            when s.permissions ? p_permission then s.permissions->>p_permission = 'true'
            when p_permission in ('receive_order', 'send_kitchen', 'serve_order') then s.staff_role = 'WAITER'
            when p_permission in ('cook_order', 'ready_order') then s.staff_role = 'KITCHEN'
            when p_permission = 'close_bill' then s.staff_role = 'CASHIER'
            else false
          end
      )
    );
$$;

create or replace function public.pos_create_role_invite(p_secret text, p_staff_role text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_id uuid;
begin
  if not public.pos_is_owner() then
    raise exception 'owner permission required';
  end if;

  if p_staff_role is null or p_staff_role not in ('WAITER', 'CASHIER', 'KITCHEN')
     or p_secret is null or length(p_secret) < 32 then
    raise exception 'invalid role or secret';
  end if;

  if p_staff_role = 'KITCHEN' and not exists (
    select 1
    from public.shop_profiles sp
    where sp.id = public.pos_my_shop()
      and coalesce(
        lower(coalesce(nullif(trim(sp.public_category), ''), nullif(trim(sp.metadata->>'category'), ''), ''))
          in ('food', 'cafe'),
        false
      )
  ) then
    raise exception 'kitchen role is not available for this shop category';
  end if;

  insert into public.pos_invites(shop_id, secret_hash, staff_role)
  values (
    public.pos_my_shop(),
    encode(sha256(convert_to(p_secret, 'utf8')), 'hex'),
    p_staff_role
  )
  returning id into v_id;

  return v_id;
end;
$$;

create or replace function public.pos_join_shop(p_secret text, p_display_name text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_inv public.pos_invites%rowtype;
begin
  if auth.uid() is null or length(trim(p_display_name)) not between 1 and 100 then
    raise exception 'invalid staff account';
  end if;

  select * into v_inv
  from public.pos_invites
  where secret_hash = encode(sha256(convert_to(p_secret, 'utf8')), 'hex')
  for update;

  if not found or v_inv.used_by is not null or v_inv.expires_at < now() then
    raise exception 'invite invalid or expired';
  end if;

  if exists (select 1 from public.pos_staff where user_id = auth.uid()) or public.pos_is_owner() then
    raise exception 'account already linked';
  end if;

  if v_inv.staff_role = 'KITCHEN' and not exists (
    select 1
    from public.shop_profiles sp
    where sp.id = v_inv.shop_id
      and coalesce(
        lower(coalesce(nullif(trim(sp.public_category), ''), nullif(trim(sp.metadata->>'category'), ''), ''))
          in ('food', 'cafe'),
        false
      )
  ) then
    raise exception 'kitchen role is not available for this shop category';
  end if;

  insert into public.pos_staff(user_id, shop_id, display_name, permissions, staff_role)
  values (auth.uid(), v_inv.shop_id, trim(p_display_name), v_inv.permissions, v_inv.staff_role);

  update public.pos_invites set used_by = auth.uid() where id = v_inv.id;
  return v_inv.shop_id;
end;
$$;

create or replace function public.pos_set_staff_role(p_user uuid, p_role text, p_active boolean)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if not public.pos_is_owner() or p_role is null or p_role not in ('WAITER', 'CASHIER', 'KITCHEN') then
    raise exception 'owner permission required or invalid role';
  end if;

  if p_role = 'KITCHEN' and not exists (
    select 1
    from public.shop_profiles sp
    where sp.id = public.pos_my_shop()
      and coalesce(
        lower(coalesce(nullif(trim(sp.public_category), ''), nullif(trim(sp.metadata->>'category'), ''), ''))
          in ('food', 'cafe'),
        false
      )
  ) then
    raise exception 'kitchen role is not available for this shop category';
  end if;

  update public.pos_staff
  set staff_role = p_role, active = p_active
  where user_id = p_user and shop_id = public.pos_my_shop();

  if not found then
    raise exception 'staff not found';
  end if;
end;
$$;

commit;
