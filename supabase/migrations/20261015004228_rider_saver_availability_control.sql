-- Reuse feature.route_bundle. Availability is OFF by default; existing accepted work is untouched.
insert into public.queuego_platform_rules(rule_key,value,effective_from,note)
values('feature.route_bundle','false'::jsonb,'1970-01-01T00:00:00Z','Saver defaults OFF until ready')
on conflict(rule_key,effective_from) do nothing;

create or replace function public.queuego_admin_set_saver_enabled(p_enabled boolean)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_admin uuid;v_at timestamptz;v_previous boolean;v_cancelled integer;
begin
 select id into v_admin from public.users where auth_user_id=auth.uid() and role='admin' and status='active';
 if v_admin is null or p_enabled is null then raise exception 'active admin and explicit saver state required'; end if;
 -- Serialize with legacy direct rule writes as well as another switch request.
 lock table public.queuego_platform_rules in share row exclusive mode;
 v_at:=clock_timestamp();
 v_previous:=public.queuego_feature_enabled('route_bundle',v_at);
 delete from public.queuego_platform_rules where rule_key='feature.route_bundle' and effective_from>v_at;
 get diagnostics v_cancelled=row_count;
 if v_previous is not distinct from p_enabled and v_cancelled=0 then
  return jsonb_build_object('enabled',p_enabled,'changed',false,'cancelled_schedules',0,'effective_from',v_at);
 end if;
 insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
 values('feature.route_bundle',to_jsonb(p_enabled),v_at,'Admin immediate Saver availability',v_admin);
 insert into public.audit_logs(user_id,action,entity_type,description,metadata)
 values(v_admin,'platform_rules_update','system_settings','Saver availability',
  jsonb_build_object('rule_key','feature.route_bundle','previous',v_previous,'enabled',p_enabled,
   'effective_from',v_at,'cancelled_schedules',v_cancelled));
 return jsonb_build_object('enabled',p_enabled,'changed',true,'cancelled_schedules',v_cancelled,'effective_from',v_at);
end $$;
revoke all on function public.queuego_admin_set_saver_enabled(boolean) from public,anon;
grant execute on function public.queuego_admin_set_saver_enabled(boolean) to authenticated;
-- No order, payout, cash, route plan, continuous flag or capacity is changed by the switch.
