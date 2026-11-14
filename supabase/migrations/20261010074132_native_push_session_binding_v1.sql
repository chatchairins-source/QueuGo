-- QueueGo native push session binding proposal.
-- PRE-DEPLOY ONLY: apply together with native clients that send sessionId and queuego-push Edge update.

alter table public.qg_native_push_tokens
  add column if not exists session_id uuid;

with live as (
  select distinct on (u.id)
    u.id as queuego_user_id,
    s.session_id
  from public.users u
  join public.user_active_sessions s on s.user_id=u.auth_user_id
  where s.revoked_at is null
  order by u.id,s.last_seen_at desc,s.created_at desc
)
update public.qg_native_push_tokens t
set session_id=live.session_id,
    updated_at=now()
from live
where t.user_id=live.queuego_user_id
  and t.session_id is null;

update public.qg_native_push_tokens
set enabled=false,updated_at=now()
where enabled
  and session_id is null;

alter table public.qg_native_push_tokens
  drop constraint if exists qg_native_push_tokens_enabled_session_check;
alter table public.qg_native_push_tokens
  add constraint qg_native_push_tokens_enabled_session_check
  check (not enabled or session_id is not null);

create index if not exists qg_native_push_tokens_user_session_enabled_idx
  on public.qg_native_push_tokens(user_id,session_id,enabled);

CREATE OR REPLACE FUNCTION public.qg_enqueue_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  insert into public.qg_push_outbox(notification_id,subscription_id)
  select new.id,s.id
  from public.qg_push_subscriptions s
  join public.users u on u.id=s.user_id
  where s.user_id=new.user_id
    and s.enabled
    and u.status='active'
    and u.role=s.role
    and u.role in ('customer','shop','rider')
  on conflict do nothing;

  insert into public.qg_push_outbox(notification_id,native_token_id)
  select new.id,t.id
  from public.qg_native_push_tokens t
  join public.users u on u.id=t.user_id
  join public.user_active_sessions a
    on a.user_id=u.auth_user_id
   and a.session_id=t.session_id
   and a.revoked_at is null
  where t.user_id=new.user_id
    and t.enabled
    and u.status='active'
    and u.role=t.role
    and u.role in ('customer','shop','rider')
  on conflict do nothing;

  begin
    perform public.qg_wake_push();
  exception when others then
    raise warning 'QueueGo push dispatch deferred';
  end;
  return new;
end $function$;
