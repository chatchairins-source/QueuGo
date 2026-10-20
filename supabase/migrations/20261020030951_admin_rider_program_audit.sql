-- Audited Admin controls for existing Rider Missions / Benefits / Insurance.
-- The trigger writes only redacted operational fields. It never records insurance
-- policy numbers, support contacts/URLs, benefit URLs, or free-text coverage details.

create or replace function qg_private.audit_admin_rider_program_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin uuid;
  v_before_raw jsonb := case when tg_op='INSERT' then null else to_jsonb(old) end;
  v_after_raw jsonb := case when tg_op='DELETE' then null else to_jsonb(new) end;
  v_before jsonb;
  v_after jsonb;
  v_row jsonb := coalesce(v_after_raw,v_before_raw);
  v_id uuid;
  v_description text;
begin
  select u.id into v_admin
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;

  -- Existing program RPCs already require a live Admin session. Keep system/non-Admin
  -- maintenance from being mislabeled as an Admin action.
  if v_admin is null then
    return case when tg_op='DELETE' then old else new end;
  end if;

  if tg_table_name='rider_missions' then
    if v_before_raw is not null then
      v_before=jsonb_build_object(
        'id',v_before_raw->'id','title',v_before_raw->'title',
        'target_jobs',v_before_raw->'target_jobs','starts_at',v_before_raw->'starts_at',
        'ends_at',v_before_raw->'ends_at','active',v_before_raw->'active'
      );
    end if;
    if v_after_raw is not null then
      v_after=jsonb_build_object(
        'id',v_after_raw->'id','title',v_after_raw->'title',
        'target_jobs',v_after_raw->'target_jobs','starts_at',v_after_raw->'starts_at',
        'ends_at',v_after_raw->'ends_at','active',v_after_raw->'active'
      );
    end if;
    v_description=coalesce(v_row->>'title','Rider mission');
  elsif tg_table_name='rider_benefits' then
    if v_before_raw is not null then
      v_before=jsonb_build_object(
        'id',v_before_raw->'id','category',v_before_raw->'category',
        'provider_name',v_before_raw->'provider_name','title',v_before_raw->'title',
        'starts_on',v_before_raw->'starts_on','expires_on',v_before_raw->'expires_on',
        'enabled',v_before_raw->'enabled'
      );
    end if;
    if v_after_raw is not null then
      v_after=jsonb_build_object(
        'id',v_after_raw->'id','category',v_after_raw->'category',
        'provider_name',v_after_raw->'provider_name','title',v_after_raw->'title',
        'starts_on',v_after_raw->'starts_on','expires_on',v_after_raw->'expires_on',
        'enabled',v_after_raw->'enabled'
      );
    end if;
    v_description=coalesce(v_row->>'title',v_row->>'provider_name','Rider benefit');
  elsif tg_table_name='rider_insurance_policies' then
    if v_before_raw is not null then
      v_before=jsonb_build_object(
        'id',v_before_raw->'id','rider_id',v_before_raw->'rider_id',
        'coverage_type',v_before_raw->'coverage_type','provider_name',v_before_raw->'provider_name',
        'product_name',v_before_raw->'product_name','starts_on',v_before_raw->'starts_on',
        'expires_on',v_before_raw->'expires_on'
      );
    end if;
    if v_after_raw is not null then
      v_after=jsonb_build_object(
        'id',v_after_raw->'id','rider_id',v_after_raw->'rider_id',
        'coverage_type',v_after_raw->'coverage_type','provider_name',v_after_raw->'provider_name',
        'product_name',v_after_raw->'product_name','starts_on',v_after_raw->'starts_on',
        'expires_on',v_after_raw->'expires_on'
      );
    end if;
    v_description=coalesce(v_row->>'product_name',v_row->>'provider_name','Rider insurance');
  else
    return case when tg_op='DELETE' then old else new end;
  end if;

  v_id=(v_row->>'id')::uuid;
  if v_before is not distinct from v_after then
    return case when tg_op='DELETE' then old else new end;
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    v_admin,
    'admin_rider_program_'||lower(tg_op),
    tg_table_name,
    v_id,
    v_description,
    jsonb_build_object('before',v_before,'after',v_after,'redacted',true)
  );

  return case when tg_op='DELETE' then old else new end;
end $$;

revoke all on function qg_private.audit_admin_rider_program_change() from public, anon, authenticated;
grant execute on function qg_private.audit_admin_rider_program_change() to service_role;

drop trigger if exists qg_admin_audit_rider_missions on qg_private.rider_missions;
create trigger qg_admin_audit_rider_missions
after insert or update or delete on qg_private.rider_missions
for each row execute function qg_private.audit_admin_rider_program_change();

drop trigger if exists qg_admin_audit_rider_benefits on qg_private.rider_benefits;
create trigger qg_admin_audit_rider_benefits
after insert or update or delete on qg_private.rider_benefits
for each row execute function qg_private.audit_admin_rider_program_change();

drop trigger if exists qg_admin_audit_rider_insurance on qg_private.rider_insurance_policies;
create trigger qg_admin_audit_rider_insurance
after insert or update or delete on qg_private.rider_insurance_policies
for each row execute function qg_private.audit_admin_rider_program_change();
