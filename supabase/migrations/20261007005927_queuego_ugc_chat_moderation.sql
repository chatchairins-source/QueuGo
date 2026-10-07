-- QueueGo UGC / chat moderation gate.
-- Google Play UGC compliance: Terms acceptance, report, block and admin moderation.

create table if not exists public.qg_ugc_terms_acceptances (
  user_id uuid primary key references public.users(id) on delete cascade,
  version integer not null check (version > 0),
  accepted_at timestamptz not null default now()
);
alter table public.qg_ugc_terms_acceptances enable row level security;
drop policy if exists qg_ugc_terms_no_client_access on public.qg_ugc_terms_acceptances;
create policy qg_ugc_terms_no_client_access on public.qg_ugc_terms_acceptances
for all to anon,authenticated using(false) with check(false);
revoke all on public.qg_ugc_terms_acceptances from anon,authenticated;
grant select,insert,update,delete on public.qg_ugc_terms_acceptances to service_role;

create table if not exists public.qg_user_blocks (
  blocker_user_id uuid not null references public.users(id) on delete cascade,
  blocked_user_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(blocker_user_id,blocked_user_id),
  check (blocker_user_id<>blocked_user_id)
);
create index if not exists qg_user_blocks_blocked_idx on public.qg_user_blocks(blocked_user_id);
alter table public.qg_user_blocks enable row level security;
drop policy if exists qg_user_blocks_no_client_access on public.qg_user_blocks;
create policy qg_user_blocks_no_client_access on public.qg_user_blocks
for all to anon,authenticated using(false) with check(false);
revoke all on public.qg_user_blocks from anon,authenticated;
grant select,insert,update,delete on public.qg_user_blocks to service_role;

create table if not exists public.qg_ugc_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_user_id uuid references public.users(id) on delete set null,
  reported_user_id uuid references public.users(id) on delete set null,
  order_id uuid references public.orders(id) on delete set null,
  message_id uuid references public.order_chat_messages(id) on delete set null,
  reason text not null check (reason in ('spam','harassment','inappropriate','fraud','safety','other')),
  details text,
  status text not null default 'pending' check (status in ('pending','under_review','resolved','dismissed')),
  admin_id uuid references public.users(id) on delete set null,
  admin_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists qg_ugc_reports_status_created_idx on public.qg_ugc_reports(status,created_at desc);
create index if not exists qg_ugc_reports_reported_idx on public.qg_ugc_reports(reported_user_id,created_at desc);
alter table public.qg_ugc_reports enable row level security;
drop policy if exists qg_ugc_reports_no_client_access on public.qg_ugc_reports;
create policy qg_ugc_reports_no_client_access on public.qg_ugc_reports
for all to anon,authenticated using(false) with check(false);
revoke all on public.qg_ugc_reports from anon,authenticated;
grant select,insert,update,delete on public.qg_ugc_reports to service_role;

CREATE OR REPLACE FUNCTION public.qg_accept_ugc_terms(p_version integer DEFAULT 1)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_user uuid:=public.get_my_user_id();
begin
  if v_user is null or p_version<>1 then raise exception 'active QueueGo account required'; end if;
  if not exists(select 1 from public.users u where u.id=v_user and u.status='active') then
    raise exception 'active QueueGo account required';
  end if;
  insert into public.qg_ugc_terms_acceptances(user_id,version,accepted_at)
  values(v_user,1,now())
  on conflict(user_id) do update set version=excluded.version,accepted_at=excluded.accepted_at;
  return jsonb_build_object('accepted',true,'version',1);
end $function$;

CREATE OR REPLACE FUNCTION public.qg_admin_ugc_report_action(p_report_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_admin uuid:=public.get_my_user_id();
begin
  if not public.is_active_admin() then raise exception 'active admin required'; end if;
  if p_status not in ('under_review','resolved','dismissed') then raise exception 'invalid report status'; end if;
  if char_length(coalesce(p_note,''))>1000 then raise exception 'admin note too long'; end if;
  update public.qg_ugc_reports
     set status=p_status,admin_id=v_admin,admin_note=nullif(trim(p_note),''),updated_at=now()
   where id=p_report_id;
  if not found then raise exception 'report not found'; end if;
  return jsonb_build_object('ok',true,'status',p_status);
end $function$;

CREATE OR REPLACE FUNCTION public.qg_admin_ugc_reports(p_status text DEFAULT 'pending'::text)
 RETURNS SETOF qg_ugc_reports
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if not public.is_active_admin() then raise exception 'active admin required'; end if;
  if p_status is not null and p_status not in ('pending','under_review','resolved','dismissed') then
    raise exception 'invalid report status';
  end if;
  return query
    select r.* from public.qg_ugc_reports r
    where p_status is null or r.status=p_status
    order by r.created_at desc
    limit 300;
end $function$;

CREATE OR REPLACE FUNCTION public.qg_block_chat_counterpart(p_order_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_actor uuid:=public.get_my_user_id();v_counterpart uuid;
begin
  if v_actor is null or not public.qg_chat_read_allowed(p_order_id) then raise exception 'chat unavailable'; end if;
  v_counterpart:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  if v_counterpart is null or v_counterpart=v_actor then raise exception 'counterpart unavailable'; end if;
  insert into public.qg_user_blocks(blocker_user_id,blocked_user_id)
  values(v_actor,v_counterpart) on conflict do nothing;
  return jsonb_build_object('blocked',true);
end $function$;

CREATE OR REPLACE FUNCTION public.qg_chat_counterpart_user(p_order_id uuid, p_actor uuid DEFAULT NULL::uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=coalesce(p_actor,public.get_my_user_id());
  v_order record;
  v_user uuid;
begin
  if v_actor is null then return null; end if;
  select o.customer_id,o.shop_id,o.technician_id,o.rider_id
  into v_order from public.orders o where o.id=p_order_id;
  if not found then return null; end if;

  if v_order.customer_id=v_actor then
    select r.user_id into v_user from public.rider_profiles r where r.id=v_order.rider_id;
    return v_user;
  end if;

  if exists(select 1 from public.rider_profiles r where r.id=v_order.rider_id and r.user_id=v_actor) then
    return v_order.customer_id;
  end if;

  if exists(select 1 from public.shop_profiles s where s.id=v_order.shop_id and s.user_id=v_actor) then
    return v_order.customer_id;
  end if;

  if exists(select 1 from public.technician_profiles t where t.id=v_order.technician_id and t.user_id=v_actor) then
    return v_order.customer_id;
  end if;

  return null;
end $function$;

CREATE OR REPLACE FUNCTION public.qg_chat_moderation_post_allowed(p_order_id uuid, p_sender_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
end $function$;

CREATE OR REPLACE FUNCTION public.qg_chat_moderation_state(p_order_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=public.get_my_user_id();
  v_counterpart uuid;
  v_accepted boolean:=false;
  v_blocked_by_me boolean:=false;
  v_blocked_me boolean:=false;
begin
  if v_actor is null or not public.qg_chat_read_allowed(p_order_id) then
    raise exception 'chat unavailable';
  end if;
  v_counterpart:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  select exists(select 1 from public.qg_ugc_terms_acceptances a where a.user_id=v_actor and a.version=1)
    into v_accepted;
  if v_counterpart is not null then
    select exists(select 1 from public.qg_user_blocks b where b.blocker_user_id=v_actor and b.blocked_user_id=v_counterpart)
      into v_blocked_by_me;
    select exists(select 1 from public.qg_user_blocks b where b.blocker_user_id=v_counterpart and b.blocked_user_id=v_actor)
      into v_blocked_me;
  end if;
  return jsonb_build_object(
    'accepted',v_accepted,
    'termsVersion',1,
    'hasCounterpart',v_counterpart is not null,
    'blockedByMe',v_blocked_by_me,
    'blockedMe',v_blocked_me,
    'canPost',v_accepted and v_counterpart is not null and not v_blocked_by_me and not v_blocked_me
  );
end $function$;

CREATE OR REPLACE FUNCTION public.qg_report_chat(p_order_id uuid, p_message_id uuid DEFAULT NULL::uuid, p_reason text DEFAULT 'other'::text, p_details text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_actor uuid:=public.get_my_user_id();
  v_reported uuid;
  v_id uuid;
begin
  if v_actor is null or not public.qg_chat_read_allowed(p_order_id) then raise exception 'chat unavailable'; end if;
  if p_reason not in ('spam','harassment','inappropriate','fraud','safety','other') then raise exception 'invalid report reason'; end if;
  if char_length(coalesce(p_details,''))>1000 then raise exception 'report details too long'; end if;

  if p_message_id is not null then
    select m.sender_id into v_reported
    from public.order_chat_messages m
    where m.id=p_message_id and m.order_id=p_order_id;
    if v_reported is null then raise exception 'message unavailable'; end if;
  else
    v_reported:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  end if;
  if v_reported is null or v_reported=v_actor then raise exception 'reported user unavailable'; end if;

  if exists(
    select 1 from public.qg_ugc_reports r
    where r.reporter_user_id=v_actor and r.reported_user_id=v_reported
      and r.order_id=p_order_id and r.created_at>now()-interval '5 minutes'
  ) then
    raise exception 'report already submitted';
  end if;

  insert into public.qg_ugc_reports(reporter_user_id,reported_user_id,order_id,message_id,reason,details)
  values(v_actor,v_reported,p_order_id,p_message_id,p_reason,nullif(trim(p_details),''))
  returning id into v_id;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select u.id,'รายงานแชตใหม่','มีรายงานเนื้อหาในแชตที่ต้องตรวจสอบ','admin_alert',p_order_id
  from public.users u where u.role='admin' and u.status='active';

  return v_id;
end $function$;

CREATE OR REPLACE FUNCTION public.qg_unblock_chat_counterpart(p_order_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_actor uuid:=public.get_my_user_id();v_counterpart uuid;
begin
  if v_actor is null or not public.qg_chat_read_allowed(p_order_id) then raise exception 'chat unavailable'; end if;
  v_counterpart:=public.qg_chat_counterpart_user(p_order_id,v_actor);
  if v_counterpart is null then raise exception 'counterpart unavailable'; end if;
  delete from public.qg_user_blocks where blocker_user_id=v_actor and blocked_user_id=v_counterpart;
  return jsonb_build_object('blocked',false);
end $function$;

drop policy if exists order_chat_messages_insert on public.order_chat_messages;
create policy order_chat_messages_insert on public.order_chat_messages
for insert to authenticated
with check (
  sender_id=public.get_my_user_id()
  and public.qg_chat_postjob_allowed(order_id,sender_id)
  and public.qg_chat_moderation_post_allowed(order_id,sender_id)
);

revoke all on function public.qg_chat_counterpart_user(uuid,uuid) from public,anon,authenticated;
grant execute on function public.qg_chat_counterpart_user(uuid,uuid) to service_role;
revoke all on function public.qg_chat_moderation_post_allowed(uuid,uuid) from public,anon,authenticated;
grant execute on function public.qg_chat_moderation_post_allowed(uuid,uuid) to authenticated,service_role;
revoke all on function public.qg_accept_ugc_terms(integer) from public,anon;
grant execute on function public.qg_accept_ugc_terms(integer) to authenticated,service_role;
revoke all on function public.qg_chat_moderation_state(uuid) from public,anon;
grant execute on function public.qg_chat_moderation_state(uuid) to authenticated,service_role;
revoke all on function public.qg_block_chat_counterpart(uuid) from public,anon;
grant execute on function public.qg_block_chat_counterpart(uuid) to authenticated,service_role;
revoke all on function public.qg_unblock_chat_counterpart(uuid) from public,anon;
grant execute on function public.qg_unblock_chat_counterpart(uuid) to authenticated,service_role;
revoke all on function public.qg_report_chat(uuid,uuid,text,text) from public,anon;
grant execute on function public.qg_report_chat(uuid,uuid,text,text) to authenticated,service_role;
revoke all on function public.qg_admin_ugc_reports(text) from public,anon;
grant execute on function public.qg_admin_ugc_reports(text) to authenticated,service_role;
revoke all on function public.qg_admin_ugc_report_action(uuid,text,text) from public,anon;
grant execute on function public.qg_admin_ugc_report_action(uuid,text,text) to authenticated,service_role;
