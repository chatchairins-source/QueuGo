create schema if not exists queuego_private;
revoke all on schema queuego_private from public,anon;
grant usage on schema queuego_private to authenticated,service_role;

create or replace function queuego_private.qg_chat_moderation_post_allowed(p_order_id uuid,p_sender_id uuid)
returns boolean
language plpgsql stable security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
  v_role text:=public.get_my_role();
  v_counterpart uuid;
begin
  if v_role='admin' then return true; end if;
  if v_actor is null or p_sender_id is distinct from v_actor then return false; end if;
  if not exists(select 1 from public.qg_ugc_terms_acceptances a where a.user_id=v_actor and a.version=1) then
    return false;
  end if;
  v_counterpart:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  if v_counterpart is null then return false; end if;
  if exists(
    select 1 from public.qg_user_blocks b
    where (b.blocker_user_id=v_actor and b.blocked_user_id=v_counterpart)
       or (b.blocker_user_id=v_counterpart and b.blocked_user_id=v_actor)
  ) then return false; end if;
  return true;
end $$;

revoke all on function queuego_private.qg_chat_moderation_post_allowed(uuid,uuid) from public,anon,authenticated;
grant execute on function queuego_private.qg_chat_moderation_post_allowed(uuid,uuid) to authenticated,service_role;

drop policy if exists order_chat_messages_insert on public.order_chat_messages;
create policy order_chat_messages_insert on public.order_chat_messages
for insert to authenticated
with check (
  sender_id=public.get_my_user_id()
  and public.qg_chat_postjob_allowed(order_id,sender_id)
  and queuego_private.qg_chat_moderation_post_allowed(order_id,sender_id)
);

do $$
begin
  if to_regprocedure('public.qg_chat_moderation_post_allowed(uuid,uuid)') is not null then
    execute 'revoke all on function public.qg_chat_moderation_post_allowed(uuid,uuid) from public,anon,authenticated,service_role';
  end if;
end $$;
drop function if exists public.qg_chat_moderation_post_allowed(uuid,uuid);
