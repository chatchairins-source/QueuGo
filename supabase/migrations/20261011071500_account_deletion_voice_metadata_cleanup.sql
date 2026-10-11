-- Ensure account deletion removes order-scoped voice metadata immediately.
-- The public.users row is tombstoned rather than deleted, so qg_call_sessions'
-- ON DELETE CASCADE foreign keys do not fire during normal account deletion.

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
  and p.oid='public.queuego_account_deletion_finalize(uuid,uuid)'::regprocedure
on conflict(signature) do nothing;

do $migration$
declare
  v_before text;
  v_after text;
  v_anchor text := 'delete from public.notifications where user_id=v_user.id;';
  v_notification_cleanup text := 'delete from public.notifications n using public.qg_call_sessions c where n.type=''voice_call'' and n.reference_id=c.order_id and n.user_id=c.callee_user_id and n.created_at=c.created_at and (c.caller_user_id=v_user.id or c.callee_user_id=v_user.id);';
  v_cleanup text := 'delete from public.qg_call_sessions where caller_user_id=v_user.id or callee_user_id=v_user.id;';
begin
  select pg_get_functiondef('public.queuego_account_deletion_finalize(uuid,uuid)'::regprocedure)
    into v_before;

  if v_before is null then
    raise exception 'ACCOUNT_DELETION_FINALIZE_NOT_FOUND';
  end if;

  if position(v_notification_cleanup in v_before) > 0
     and position(v_cleanup in v_before) > 0 then
    return;
  end if;

  if (length(v_before)-length(replace(v_before,v_anchor,'')))/length(v_anchor) <> 1 then
    raise exception 'ACCOUNT_DELETION_FINALIZE_UNEXPECTED_DEFINITION';
  end if;

  v_after := replace(
    v_before,
    v_anchor,
    v_anchor || E'\n  ' || v_notification_cleanup || E'\n  ' || v_cleanup
  );
  if v_after = v_before then
    raise exception 'ACCOUNT_DELETION_VOICE_CLEANUP_PATCH_NOT_APPLIED';
  end if;

  execute v_after;
end
$migration$;

do $verify$
declare
  v_definition text;
begin
  select pg_get_functiondef('public.queuego_account_deletion_finalize(uuid,uuid)'::regprocedure)
    into v_definition;

  if position(
    'delete from public.notifications n using public.qg_call_sessions c where n.type=''voice_call'' and n.reference_id=c.order_id and n.user_id=c.callee_user_id and n.created_at=c.created_at and (c.caller_user_id=v_user.id or c.callee_user_id=v_user.id);'
    in v_definition
  ) = 0 then
    raise exception 'ACCOUNT_DELETION_VOICE_NOTIFICATION_CLEANUP_MISSING';
  end if;

  if position(
    'delete from public.qg_call_sessions where caller_user_id=v_user.id or callee_user_id=v_user.id;'
    in v_definition
  ) = 0 then
    raise exception 'ACCOUNT_DELETION_VOICE_CLEANUP_MISSING';
  end if;
end
$verify$;

commit;
