-- Additive authorization/cleanup fix; preserve the existing Voice RPC contracts.
-- Based on read-only Production definitions preserved in ops/native-voice-before-block-revocation-20261012.sql.
-- Refuse to overwrite a newer Production definition; allow an already-applied target.
DO $guard$
BEGIN
  IF md5(btrim(pg_get_functiondef('public.qg_call_ice_config(uuid,uuid)'::regprocedure),E' \n\r\t')) NOT IN ('83bfc37b1b8a09b2278026206d7b40ec','e39c4c5418362e6e5e72b8bea3371bf9') THEN
    RAISE EXCEPTION 'Voice definition changed; re-read Production before migrating: public.qg_call_ice_config';
  END IF;
  IF md5(btrim(pg_get_functiondef('public.qg_call_active(uuid,uuid)'::regprocedure),E' \n\r\t')) NOT IN ('a884fae008f41b75ce8089cea90bb27a','ec0af00bc08632975d4881161b2a3d22') THEN
    RAISE EXCEPTION 'Voice definition changed; re-read Production before migrating: public.qg_call_active';
  END IF;
  IF md5(btrim(pg_get_functiondef('qg_private.qg_voice_realtime_allowed(text)'::regprocedure),E' \n\r\t')) NOT IN ('d12ec74d4889e824c41f1513281b695a','5fe76e6da0fe3483a0db57d8a0b0bac7') THEN
    RAISE EXCEPTION 'Voice definition changed; re-read Production before migrating: qg_private.qg_voice_realtime_allowed';
  END IF;
  IF md5(btrim(pg_get_functiondef('public.qg_call_start(uuid,text,uuid)'::regprocedure),E' \n\r\t')) NOT IN ('762eac0ac91cc78d0cc32da152d0f064','5fcd2c026ca195c45cddcc8d60bedac4') THEN
    RAISE EXCEPTION 'Voice definition changed; re-read Production before migrating: public.qg_call_start';
  END IF;
END;
$guard$;

CREATE OR REPLACE FUNCTION qg_private.qg_voice_lock_pair(p_first uuid,p_second uuid)
RETURNS void LANGUAGE plpgsql VOLATILE
SET search_path TO ''
AS $function$
BEGIN
  IF p_first IS NULL OR p_second IS NULL THEN
    RAISE EXCEPTION 'voice participants required' USING errcode='22023';
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
    'qg-voice-pair:' || least(p_first,p_second)::text || ':' || greatest(p_first,p_second)::text,0));
END;
$function$;
REVOKE ALL ON FUNCTION qg_private.qg_voice_lock_pair(uuid,uuid) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION qg_private.qg_voice_revoke_on_block()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  PERFORM qg_private.qg_voice_lock_pair(NEW.blocker_user_id,NEW.blocked_user_id);
  UPDATE public.qg_call_sessions c
     SET status='ended',ended_at=coalesce(c.ended_at,now())
   WHERE c.status IN ('ringing','accepted')
     AND ((c.caller_user_id=NEW.blocker_user_id AND c.callee_user_id=NEW.blocked_user_id)
       OR (c.caller_user_id=NEW.blocked_user_id AND c.callee_user_id=NEW.blocker_user_id));
  RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION qg_private.qg_voice_revoke_on_block() FROM PUBLIC,anon,authenticated;

DO $migration$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_catalog.pg_trigger
    WHERE tgrelid='public.qg_user_blocks'::regclass AND tgname='qg_voice_revoke_on_block'
  ) THEN
    CREATE TRIGGER qg_voice_revoke_on_block
    BEFORE INSERT OR UPDATE ON public.qg_user_blocks
    FOR EACH ROW EXECUTE FUNCTION qg_private.qg_voice_revoke_on_block();
  END IF;
END;
$migration$;

CREATE OR REPLACE FUNCTION public.qg_call_ice_config(p_call_id uuid, p_session_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
      and not exists (
        select 1 from public.qg_user_blocks b
        where (b.blocker_user_id=c.caller_user_id and b.blocked_user_id=c.callee_user_id)
           or (b.blocker_user_id=c.callee_user_id and b.blocked_user_id=c.caller_user_id)
      )
      and exists(
        select 1 from public.orders o
        where o.id=c.order_id
          and lower(o.status) not in ('cancelled','completed','no_rider_available')
      )
      and exists(
        select 1
        from public.users cu
        join public.user_active_sessions cs on cs.user_id=cu.auth_user_id
        where cu.id=c.caller_user_id
          and cs.session_id=c.caller_session_id
          and cs.revoked_at is null
      )
      and exists(
        select 1
        from public.users ru
        join public.user_active_sessions rs on rs.user_id=ru.auth_user_id
        where ru.id=c.callee_user_id
          and rs.session_id=c.callee_session_id
          and rs.revoked_at is null
      )
      and (
        (c.caller_user_id=v_actor and c.caller_session_id=p_session_id)
        or
        (c.callee_user_id=v_actor and c.callee_session_id=p_session_id)
      )
  ) then
    raise exception 'call access denied' using errcode='42501';
  end if;

  -- Authorization only. TURN/STUN credentials are generated by queuego-turn
  -- with server-side secrets and must never be stored in Postgres.
  return jsonb_build_object('authorized',true);
end;
$function$;

CREATE OR REPLACE FUNCTION public.qg_call_active(p_order_id uuid, p_session_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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

  update public.qg_call_sessions c
     set status='ended',ended_at=coalesce(ended_at,now())
   where c.status='accepted'
     and (
       not exists(
         select 1
         from public.users cu
         join public.user_active_sessions cs on cs.user_id=cu.auth_user_id
         where cu.id=c.caller_user_id
           and cs.session_id=c.caller_session_id
           and cs.revoked_at is null
       )
       or not exists(
         select 1
         from public.users ru
         join public.user_active_sessions rs on rs.user_id=ru.auth_user_id
         where ru.id=c.callee_user_id
           and rs.session_id=c.callee_session_id
           and rs.revoked_at is null
       )
     );

  update public.qg_call_sessions c
     set status='ended',ended_at=coalesce(ended_at,now())
   where c.order_id=p_order_id
     and c.status in ('ringing','accepted')
     and not (not exists (
        select 1 from public.qg_user_blocks b
        where (b.blocker_user_id=c.caller_user_id and b.blocked_user_id=c.callee_user_id)
           or (b.blocker_user_id=c.callee_user_id and b.blocked_user_id=c.caller_user_id)
      ));

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
$function$;

CREATE OR REPLACE FUNCTION qg_private.qg_voice_realtime_allowed(p_topic text)
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
      and not exists (
        select 1 from public.qg_user_blocks b
        where (b.blocker_user_id=c.caller_user_id and b.blocked_user_id=c.callee_user_id)
           or (b.blocker_user_id=c.callee_user_id and b.blocked_user_id=c.caller_user_id)
      )
      and exists(
        select 1 from public.orders o
        where o.id=c.order_id
          and lower(o.status) not in ('cancelled','completed','no_rider_available')
      )
      and exists(
        select 1
        from public.users cu
        join public.user_active_sessions cs on cs.user_id=cu.auth_user_id
        where cu.id=c.caller_user_id
          and cs.session_id=c.caller_session_id
          and cs.revoked_at is null
      )
      and exists(
        select 1
        from public.users ru
        join public.user_active_sessions rs on rs.user_id=ru.auth_user_id
        where ru.id=c.callee_user_id
          and rs.session_id=c.callee_session_id
          and rs.revoked_at is null
      )
      and (
        (c.caller_user_id=v_queuego_user and c.caller_session_id=v_current_session)
        or
        (c.callee_user_id=v_queuego_user and c.callee_session_id=v_current_session)
      )
  );
end;
$function$;

CREATE OR REPLACE FUNCTION public.qg_call_start(p_order_id uuid, p_target text, p_session_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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

  -- Serialize call creation with block revocation before taking call row locks.
  perform qg_private.qg_voice_lock_pair(v_actor_id,v_callee_id);

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

  insert into public.notifications(id,user_id,title,message,type,reference_id)
  values(
    v_call_id,
    v_callee_id,
    'สายเรียกเข้า QueueGo',
    coalesce(nullif(v_actor_name,''),'QueueGo')||' กำลังโทรผ่านออเดอร์ '||v_order.order_number,
    'voice_call',
    p_order_id
  );

  return qg_private.qg_voice_call_json(v_call_id);
end;
$function$;

-- Reconcile only previously blocked live calls, retaining all call history.
UPDATE public.qg_call_sessions c
   SET status='ended',ended_at=coalesce(c.ended_at,now())
 WHERE c.status IN ('ringing','accepted')
   AND not (not exists (
        select 1 from public.qg_user_blocks b
        where (b.blocker_user_id=c.caller_user_id and b.blocked_user_id=c.callee_user_id)
           or (b.blocker_user_id=c.callee_user_id and b.blocked_user_id=c.caller_user_id)
      ));

