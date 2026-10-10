-- QueueGo native voice call backend proposal.
-- Isolated proposal only: DO NOT deploy until Native client + TURN + E2E gates pass.
-- Media is never stored here. Realtime carries signaling only; WebRTC carries audio.

do $$
declare
  v_table regclass;
begin
  v_table := to_regclass('public.qg_call_sessions');
  if v_table is not null
     and coalesce(obj_description(v_table,'pg_class'),'') <> 'queuego:native-voice-call-v1' then
    raise exception 'existing qg_call_sessions requires review';
  end if;
end $$;

create table if not exists public.qg_call_sessions (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  caller_user_id uuid not null references public.users(id) on delete cascade,
  callee_user_id uuid not null references public.users(id) on delete cascade,
  caller_session_id uuid not null,
  callee_session_id uuid,
  target text not null check (target in ('shop','rider','customer')),
  status text not null default 'ringing'
    check (status in ('ringing','accepted','declined','ended','missed')),
  topic text not null unique,
  created_at timestamptz not null default now(),
  answered_at timestamptz,
  ended_at timestamptz,
  ring_expires_at timestamptz not null default (now() + interval '45 seconds'),
  expires_at timestamptz not null default (now() + interval '2 hours'),
  check (caller_user_id <> callee_user_id),
  check (topic = 'qg-call:' || id::text),
  check (ring_expires_at > created_at),
  check (expires_at > created_at)
);
comment on table public.qg_call_sessions is 'queuego:native-voice-call-v1';

create index if not exists qg_call_sessions_order_created_idx
  on public.qg_call_sessions(order_id,created_at desc);
create index if not exists qg_call_sessions_caller_created_idx
  on public.qg_call_sessions(caller_user_id,created_at desc);
create index if not exists qg_call_sessions_callee_created_idx
  on public.qg_call_sessions(callee_user_id,created_at desc);
create unique index if not exists qg_call_sessions_active_pair_uq
  on public.qg_call_sessions(
    order_id,
    least(caller_user_id,callee_user_id),
    greatest(caller_user_id,callee_user_id)
  )
  where status in ('ringing','accepted');

alter table public.qg_call_sessions enable row level security;
revoke all on public.qg_call_sessions from public,anon,authenticated;
grant select,insert,update,delete on public.qg_call_sessions to service_role;

drop policy if exists qg_call_sessions_no_client_access on public.qg_call_sessions;
create policy qg_call_sessions_no_client_access on public.qg_call_sessions
for all to anon,authenticated using(false) with check(false);

create schema if not exists qg_private;
revoke all on schema qg_private from public,anon;
grant usage on schema qg_private to authenticated,service_role;

create or replace function qg_private.qg_voice_call_json(p_call_id uuid)
returns jsonb
language sql
stable
security definer
set search_path=''
as $$
  select jsonb_build_object(
    'id',c.id,
    'order_id',c.order_id,
    'topic',c.topic,
    'target',c.target,
    'status',c.status,
    'caller_user_id',c.caller_user_id,
    'callee_user_id',c.callee_user_id,
    'created_at',c.created_at,
    'expires_at',c.expires_at
  )
  from public.qg_call_sessions c
  where c.id=p_call_id;
$$;
revoke all on function qg_private.qg_voice_call_json(uuid) from public,anon,authenticated;
grant execute on function qg_private.qg_voice_call_json(uuid) to service_role;

create or replace function qg_private.qg_voice_realtime_allowed(p_topic text)
returns boolean
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_auth_user uuid:=auth.uid();
  v_queuego_user uuid;
  v_current_session uuid;
begin
  if v_auth_user is null or p_topic is null or p_topic !~ '^qg-call:[0-9a-f-]{36}$' then
    return false;
  end if;

  select u.id into v_queuego_user
  from public.users u
  where u.auth_user_id=v_auth_user
    and u.status='active'
  limit 1;
  if v_queuego_user is null then return false; end if;

  select s.session_id into v_current_session
  from public.user_active_sessions s
  where s.user_id=v_auth_user
    and s.revoked_at is null
  limit 1;
  if v_current_session is null then return false; end if;

  return exists(
    select 1
    from public.qg_call_sessions c
    where c.topic=p_topic
      and c.status='accepted'
      and c.expires_at>now()
      and exists(
        select 1 from public.orders o
        where o.id=c.order_id
          and lower(o.status) not in ('cancelled','completed','no_rider_available')
      )
      and (
        (c.caller_user_id=v_queuego_user and c.caller_session_id=v_current_session)
        or
        (c.callee_user_id=v_queuego_user and c.callee_session_id=v_current_session)
      )
  );
end;
$$;
revoke all on function qg_private.qg_voice_realtime_allowed(text) from public,anon;
grant execute on function qg_private.qg_voice_realtime_allowed(text) to authenticated,service_role;

create or replace function public.qg_call_start(
  p_order_id uuid,
  p_target text,
  p_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path='public','pg_temp'
as $$
declare
  v_actor_id uuid:=public.get_my_user_id();
  v_actor_role text;
  v_actor_name text;
  v_order public.orders%rowtype;
  v_callee_id uuid;
  v_call_id uuid:=gen_random_uuid();
  v_existing uuid;
begin
  if p_session_id is null
     or not coalesce(public.check_active_session(p_session_id),false) then
    raise exception 'active QueueGo session required' using errcode='42501';
  end if;
  if p_target not in ('shop','rider','customer') then
    raise exception 'invalid call target' using errcode='22023';
  end if;

  select u.role,u.name into v_actor_role,v_actor_name
  from public.users u
  where u.id=v_actor_id and u.status='active';
  if not found then
    raise exception 'active QueueGo account required' using errcode='42501';
  end if;

  select o.* into v_order
  from public.orders o
  where o.id=p_order_id
    and lower(o.status) not in ('cancelled','completed','no_rider_available');
  if not found then
    raise exception 'active order required' using errcode='42501';
  end if;

  if p_target='rider' then
    if v_order.customer_id is distinct from v_actor_id then
      raise exception 'call access denied' using errcode='42501';
    end if;
    select r.user_id into v_callee_id
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    where r.id=v_order.rider_id
      and r.status='active'
      and u.role='rider'
      and u.status='active';
  elsif p_target='shop' then
    if v_order.customer_id is distinct from v_actor_id then
      raise exception 'call access denied' using errcode='42501';
    end if;
    select s.user_id into v_callee_id
    from public.shop_profiles s
    join public.users u on u.id=s.user_id
    where s.id=v_order.shop_id
      and s.status='active'
      and u.role='shop'
      and u.status='active';
  else
    if v_order.customer_id is null then
      raise exception 'customer unavailable' using errcode='42501';
    end if;
    if exists(
      select 1 from public.rider_profiles r
      where r.id=v_order.rider_id and r.user_id=v_actor_id and r.status='active'
    ) then
      v_callee_id:=v_order.customer_id;
    elsif exists(
      select 1 from public.shop_profiles s
      where s.id=v_order.shop_id and s.user_id=v_actor_id and s.status='active'
    ) then
      v_callee_id:=v_order.customer_id;
    else
      raise exception 'call access denied' using errcode='42501';
    end if;
  end if;

  if v_callee_id is null
     or not exists(select 1 from public.users u where u.id=v_callee_id and u.status='active') then
    raise exception 'call counterpart unavailable' using errcode='42501';
  end if;

  if exists(
    select 1 from public.qg_user_blocks b
    where (b.blocker_user_id=v_actor_id and b.blocked_user_id=v_callee_id)
       or (b.blocker_user_id=v_callee_id and b.blocked_user_id=v_actor_id)
  ) then
    raise exception 'call blocked' using errcode='42501';
  end if;

  update public.qg_call_sessions
     set status='missed',ended_at=coalesce(ended_at,now())
   where order_id=p_order_id
     and status='ringing'
     and ring_expires_at<=now();

  update public.qg_call_sessions
     set status='ended',ended_at=coalesce(ended_at,now())
   where order_id=p_order_id
     and status='accepted'
     and expires_at<=now();

  select c.id into v_existing
  from public.qg_call_sessions c
  where c.order_id=p_order_id
    and c.status in ('ringing','accepted')
    and least(c.caller_user_id,c.callee_user_id)=least(v_actor_id,v_callee_id)
    and greatest(c.caller_user_id,c.callee_user_id)=greatest(v_actor_id,v_callee_id)
  limit 1;
  if v_existing is not null then
    raise exception 'call already active' using errcode='55000';
  end if;

  if (
    select count(*)
    from public.qg_call_sessions c
    where c.order_id=p_order_id
      and c.caller_user_id=v_actor_id
      and c.created_at>now()-interval '5 minutes'
  ) >= 3 then
    raise exception 'call rate limited' using errcode='42900';
  end if;

  insert into public.qg_call_sessions(
    id,order_id,caller_user_id,callee_user_id,caller_session_id,target,topic
  ) values(
    v_call_id,p_order_id,v_actor_id,v_callee_id,p_session_id,p_target,'qg-call:'||v_call_id::text
  );

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(
    v_callee_id,
    'สายเรียกเข้า QueueGo',
    coalesce(nullif(v_actor_name,''),'QueueGo')||' กำลังโทรผ่านออเดอร์ '||v_order.order_number,
    'voice_call',
    p_order_id
  );

  return qg_private.qg_voice_call_json(v_call_id);
end;
$$;

create or replace function public.qg_call_active(
  p_order_id uuid,
  p_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path='public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
  v_call_id uuid;
begin
  if p_session_id is null
     or not coalesce(public.check_active_session(p_session_id),false)
     or v_actor is null then
    raise exception 'active QueueGo session required' using errcode='42501';
  end if;

  update public.qg_call_sessions
     set status='missed',ended_at=coalesce(ended_at,now())
   where order_id=p_order_id
     and status='ringing'
     and ring_expires_at<=now();

  update public.qg_call_sessions
     set status='ended',ended_at=coalesce(ended_at,now())
   where order_id=p_order_id
     and status='accepted'
     and expires_at<=now();

  update public.qg_call_sessions c
     set status='ended',ended_at=coalesce(ended_at,now())
   where c.order_id=p_order_id
     and c.status in ('ringing','accepted')
     and not exists(
       select 1 from public.orders o
       where o.id=c.order_id
         and lower(o.status) not in ('cancelled','completed','no_rider_available')
     );

  select c.id into v_call_id
  from public.qg_call_sessions c
  where c.order_id=p_order_id
    and c.status in ('ringing','accepted')
    and (
      (c.caller_user_id=v_actor and c.caller_session_id=p_session_id)
      or
      (c.callee_user_id=v_actor and (
        (c.status='ringing' and c.callee_session_id is null)
        or c.callee_session_id=p_session_id
      ))
    )
  order by c.created_at desc
  limit 1;

  if v_call_id is null then return jsonb_build_object('none',true); end if;
  return qg_private.qg_voice_call_json(v_call_id);
end;
$$;

create or replace function public.qg_call_answer(
  p_call_id uuid,
  p_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path='public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
  v_call public.qg_call_sessions%rowtype;
begin
  if p_session_id is null
     or not coalesce(public.check_active_session(p_session_id),false)
     or v_actor is null then
    raise exception 'active QueueGo session required' using errcode='42501';
  end if;

  select * into v_call
  from public.qg_call_sessions
  where id=p_call_id and callee_user_id=v_actor
  for update;
  if not found or v_call.status<>'ringing' or v_call.ring_expires_at<=now() then
    if found and v_call.status='ringing' and v_call.ring_expires_at<=now() then
      update public.qg_call_sessions
         set status='missed',ended_at=coalesce(ended_at,now())
       where id=p_call_id;
    end if;
    raise exception 'call no longer available' using errcode='55000';
  end if;

  if exists(
    select 1 from public.qg_user_blocks b
    where (b.blocker_user_id=v_call.caller_user_id and b.blocked_user_id=v_call.callee_user_id)
       or (b.blocker_user_id=v_call.callee_user_id and b.blocked_user_id=v_call.caller_user_id)
  ) then
    raise exception 'call blocked' using errcode='42501';
  end if;

  if not exists(
    select 1 from public.orders o
    where o.id=v_call.order_id
      and lower(o.status) not in ('cancelled','completed','no_rider_available')
  ) then
    update public.qg_call_sessions
       set status='ended',ended_at=coalesce(ended_at,now())
     where id=p_call_id;
    raise exception 'active order required' using errcode='42501';
  end if;

  update public.qg_call_sessions
     set status='accepted',
         callee_session_id=p_session_id,
         answered_at=now(),
         expires_at=greatest(expires_at,now()+interval '2 hours')
   where id=p_call_id;

  return qg_private.qg_voice_call_json(p_call_id);
end;
$$;

create or replace function public.qg_call_decline(
  p_call_id uuid,
  p_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path='public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
begin
  if p_session_id is null
     or not coalesce(public.check_active_session(p_session_id),false)
     or v_actor is null then
    raise exception 'active QueueGo session required' using errcode='42501';
  end if;

  update public.qg_call_sessions
     set status='declined',ended_at=now(),callee_session_id=p_session_id
   where id=p_call_id
     and callee_user_id=v_actor
     and status='ringing'
     and ring_expires_at>now();
  if not found then
    raise exception 'call no longer available' using errcode='55000';
  end if;

  return qg_private.qg_voice_call_json(p_call_id);
end;
$$;

create or replace function public.qg_call_end(
  p_call_id uuid,
  p_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path='public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
begin
  if p_session_id is null
     or not coalesce(public.check_active_session(p_session_id),false)
     or v_actor is null then
    raise exception 'active QueueGo session required' using errcode='42501';
  end if;

  update public.qg_call_sessions c
     set status='ended',ended_at=now()
   where c.id=p_call_id
     and c.status in ('ringing','accepted')
     and (
       (c.caller_user_id=v_actor and c.caller_session_id=p_session_id)
       or
       (c.callee_user_id=v_actor and c.callee_session_id=p_session_id)
     );
  if not found then
    raise exception 'call access denied' using errcode='42501';
  end if;

  return qg_private.qg_voice_call_json(p_call_id);
end;
$$;

create or replace function public.qg_call_ice_config(
  p_call_id uuid,
  p_session_id uuid
) returns jsonb
language plpgsql
stable
security definer
set search_path='public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
begin
  if p_session_id is null
     or not coalesce(public.check_active_session(p_session_id),false)
     or v_actor is null then
    raise exception 'active QueueGo session required' using errcode='42501';
  end if;

  if not exists(
    select 1
    from public.qg_call_sessions c
    where c.id=p_call_id
      and c.status='accepted'
      and c.expires_at>now()
      and exists(
        select 1 from public.orders o
        where o.id=c.order_id
          and lower(o.status) not in ('cancelled','completed','no_rider_available')
      )
      and (
        (c.caller_user_id=v_actor and c.caller_session_id=p_session_id)
        or
        (c.callee_user_id=v_actor and c.callee_session_id=p_session_id)
      )
  ) then
    raise exception 'call access denied' using errcode='42501';
  end if;

  -- Public STUN is a real fallback only. Release certification still requires
  -- short-lived TURN credentials generated server-side; no TURN secret belongs in SQL/APK.
  return jsonb_build_object(
    'ice_servers',
    jsonb_build_array(
      jsonb_build_object('urls',jsonb_build_array('stun:stun.cloudflare.com:3478'))
    ),
    'turn_ready',false
  );
end;
$$;

revoke all on function public.qg_call_start(uuid,text,uuid) from public,anon;
revoke all on function public.qg_call_active(uuid,uuid) from public,anon;
revoke all on function public.qg_call_answer(uuid,uuid) from public,anon;
revoke all on function public.qg_call_decline(uuid,uuid) from public,anon;
revoke all on function public.qg_call_end(uuid,uuid) from public,anon;
revoke all on function public.qg_call_ice_config(uuid,uuid) from public,anon;

grant execute on function public.qg_call_start(uuid,text,uuid) to authenticated,service_role;
grant execute on function public.qg_call_active(uuid,uuid) to authenticated,service_role;
grant execute on function public.qg_call_answer(uuid,uuid) to authenticated,service_role;
grant execute on function public.qg_call_decline(uuid,uuid) to authenticated,service_role;
grant execute on function public.qg_call_end(uuid,uuid) to authenticated,service_role;
grant execute on function public.qg_call_ice_config(uuid,uuid) to authenticated,service_role;

drop policy if exists qg_voice_realtime_select on realtime.messages;
create policy qg_voice_realtime_select
on realtime.messages
for select
to authenticated
using (
  extension='broadcast'
  and realtime.topic() like 'qg-call:%'
  and qg_private.qg_voice_realtime_allowed(realtime.topic())
);

drop policy if exists qg_voice_realtime_insert on realtime.messages;
create policy qg_voice_realtime_insert
on realtime.messages
for insert
to authenticated
with check (
  extension='broadcast'
  and realtime.topic() like 'qg-call:%'
  and qg_private.qg_voice_realtime_allowed(realtime.topic())
);
