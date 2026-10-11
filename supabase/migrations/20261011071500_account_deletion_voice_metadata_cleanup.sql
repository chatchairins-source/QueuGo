-- Ensure account deletion removes order-scoped voice metadata immediately.
-- The users row is tombstoned rather than deleted, so call-session FK cascades do not fire.
-- Bind the incoming-call notification UUID to the call UUID so counterpart cleanup is exact.

begin;

create table if not exists queuego_private.account_deletion_voice_rpc_backup_20261011(
  signature text primary key,
  definition text not null,
  acl text,
  captured_at timestamptz not null default now()
);
revoke all on queuego_private.account_deletion_voice_rpc_backup_20261011 from public, anon, authenticated;
alter table queuego_private.account_deletion_voice_rpc_backup_20261011 enable row level security;

insert into queuego_private.account_deletion_voice_rpc_backup_20261011(signature,definition,acl)
select p.oid::regprocedure::text, pg_get_functiondef(p.oid), p.proacl::text
from pg_proc p
join pg_namespace n on n.oid=p.pronamespace
where n.nspname='public'
  and p.oid in (
    'public.qg_call_start(uuid,text,uuid)'::regprocedure,
    'public.queuego_account_deletion_finalize(uuid,uuid)'::regprocedure
  )
on conflict(signature) do nothing;

do $call_notification_binding$
declare
  v_before text;
  v_after text;
  v_old_header text := 'insert into public.notifications(user_id,title,message,type,reference_id)';
  v_new_header text := 'insert into public.notifications(id,user_id,title,message,type,reference_id)';
  v_old_values text := E'  values(\n    v_callee_id,';
  v_new_values text := E'  values(\n    v_call_id,\n    v_callee_id,';
begin
  select pg_get_functiondef('public.qg_call_start(uuid,text,uuid)'::regprocedure)
    into v_before;

  if v_before is null then
    raise exception 'QG_CALL_START_NOT_FOUND';
  end if;

  if position(v_new_header in v_before) > 0
     and position(v_new_values in v_before) > 0 then
    return;
  end if;

  if (length(v_before)-length(replace(v_before,v_old_header,'')))/length(v_old_header) <> 1
     or (length(v_before)-length(replace(v_before,v_old_values,'')))/length(v_old_values) <> 1 then
    raise exception 'QG_CALL_START_UNEXPECTED_DEFINITION';
  end if;

  v_after := replace(v_before,v_old_header,v_new_header);
  v_after := replace(v_after,v_old_values,v_new_values);

  if v_after = v_before then
    raise exception 'QG_CALL_NOTIFICATION_BINDING_PATCH_NOT_APPLIED';
  end if;

  execute v_after;
end
$call_notification_binding$;

do $account_cleanup$
declare
  v_before text;
  v_after text;
  v_anchor text := 'delete from public.notifications where user_id=v_user.id;';
  v_notification_cleanup text := 'delete from public.notifications n using public.qg_call_sessions c where n.id=c.id and n.type=''voice_call'' and (c.caller_user_id=v_user.id or c.callee_user_id=v_user.id);';
  v_call_cleanup text := 'delete from public.qg_call_sessions where caller_user_id=v_user.id or callee_user_id=v_user.id;';
begin
  select pg_get_functiondef('public.queuego_account_deletion_finalize(uuid,uuid)'::regprocedure)
    into v_before;

  if v_before is null then
    raise exception 'ACCOUNT_DELETION_FINALIZE_NOT_FOUND';
  end if;

  if position(v_notification_cleanup in v_before) > 0
     and position(v_call_cleanup in v_before) > 0 then
    return;
  end if;

  if (length(v_before)-length(replace(v_before,v_anchor,'')))/length(v_anchor) <> 1 then
    raise exception 'ACCOUNT_DELETION_FINALIZE_UNEXPECTED_DEFINITION';
  end if;

  v_after := replace(
    v_before,
    v_anchor,
    v_anchor || E'\n  ' || v_notification_cleanup || E'\n  ' || v_call_cleanup
  );

  if v_after = v_before then
    raise exception 'ACCOUNT_DELETION_VOICE_CLEANUP_PATCH_NOT_APPLIED';
  end if;

  execute v_after;
end
$account_cleanup$;

do $verify$
declare
  v_call_start text;
  v_finalize text;
begin
  select pg_get_functiondef('public.qg_call_start(uuid,text,uuid)'::regprocedure)
    into v_call_start;
  select pg_get_functiondef('public.queuego_account_deletion_finalize(uuid,uuid)'::regprocedure)
    into v_finalize;

  if position(
    'insert into public.notifications(id,user_id,title,message,type,reference_id)'
    in v_call_start
  ) = 0
     or position(E'  values(\n    v_call_id,\n    v_callee_id,' in v_call_start) = 0 then
    raise exception 'QG_CALL_NOTIFICATION_BINDING_MISSING';
  end if;

  if position(
    'delete from public.notifications n using public.qg_call_sessions c where n.id=c.id and n.type=''voice_call'' and (c.caller_user_id=v_user.id or c.callee_user_id=v_user.id);'
    in v_finalize
  ) = 0 then
    raise exception 'ACCOUNT_DELETION_VOICE_NOTIFICATION_CLEANUP_MISSING';
  end if;

  if position(
    'delete from public.qg_call_sessions where caller_user_id=v_user.id or callee_user_id=v_user.id;'
    in v_finalize
  ) = 0 then
    raise exception 'ACCOUNT_DELETION_VOICE_CLEANUP_MISSING';
  end if;
end
$verify$;

commit;
