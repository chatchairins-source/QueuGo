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

    if old.onboarding_status='approved' then
      if old.status is distinct from 'suspended' or old.archived_at is not null then
        raise exception 'SHOP_RESTORE_REQUIRES_APPROVED_SUSPENSION';
      end if;
    elsif old.onboarding_status is distinct from 'pending_approval'
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

create or replace function public.users_status_cascade()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
begin
  if new.status is distinct from old.status then
    if new.status='suspended' then
      update public.shop_profiles
      set status='suspended',updated_at=now()
      where user_id=new.id and archived_at is null;

      update public.technician_profiles
      set status='suspended',updated_at=now()
      where user_id=new.id;

      update public.rider_profiles
      set status='suspended',updated_at=now(),
          metadata=coalesce(metadata,'{}'::jsonb)||'{"online":false,"available":false}'::jsonb
      where user_id=new.id;

    elsif new.status='rejected' then
      update public.shop_profiles
      set status='rejected',updated_at=now()
      where user_id=new.id and archived_at is null;

      update public.technician_profiles
      set status='rejected',updated_at=now()
      where user_id=new.id;

      update public.rider_profiles
      set status='rejected',updated_at=now(),
          metadata=coalesce(metadata,'{}'::jsonb)||'{"online":false,"available":false}'::jsonb
      where user_id=new.id;

    elsif new.status='active' and old.status in ('suspended','pending','rejected') then
      update public.shop_profiles
      set status=case
          when onboarding_status='approved' then 'active'
          when onboarding_status='rejected' then 'rejected'
          else 'pending'
        end,
        updated_at=now()
      where user_id=new.id
        and archived_at is null
        and status in ('suspended','pending','rejected');

      update public.technician_profiles
      set status='active',updated_at=now()
      where user_id=new.id and status in ('suspended','pending','rejected');

      update public.rider_profiles
      set status='active',updated_at=now()
      where user_id=new.id and status in ('suspended','pending','rejected');
    end if;
  end if;
  return new;
end
$$;
revoke all on function public.users_status_cascade() from public,anon,authenticated;
