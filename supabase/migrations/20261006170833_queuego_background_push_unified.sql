-- QueueGo unified background Web Push (Customer / Merchant / Rider)
-- Production migration: 20261006170833_queuego_background_push_unified

create table if not exists public.qg_push_config (
  id boolean primary key default true check (id),
  public_key text not null,
  private_key text not null,
  worker_token text not null default encode(gen_random_bytes(32),'hex')
);
alter table public.qg_push_config enable row level security;
drop policy if exists qg_push_config_no_client_access on public.qg_push_config;
create policy qg_push_config_no_client_access on public.qg_push_config for all to anon,authenticated using(false) with check(false);
revoke all on public.qg_push_config from anon,authenticated;
grant select,insert,update,delete on public.qg_push_config to service_role;

create table if not exists public.qg_push_subscriptions (
  id uuid primary key,
  user_id uuid not null references public.users(id) on delete cascade,
  role text not null check (role in ('customer','shop','rider')),
  endpoint text not null unique,
  p256dh text not null,
  auth_key text not null,
  enabled boolean not null default true,
  updated_at timestamptz not null default now()
);
create index if not exists qg_push_subscriptions_user_enabled_idx on public.qg_push_subscriptions(user_id,enabled);
alter table public.qg_push_subscriptions enable row level security;
drop policy if exists qg_push_subscriptions_no_client_access on public.qg_push_subscriptions;
create policy qg_push_subscriptions_no_client_access on public.qg_push_subscriptions for all to anon,authenticated using(false) with check(false);
revoke all on public.qg_push_subscriptions from anon,authenticated;
grant select,insert,update,delete on public.qg_push_subscriptions to service_role;

create table if not exists public.qg_push_outbox (
  id uuid primary key default gen_random_uuid(),
  notification_id uuid not null references public.notifications(id) on delete cascade,
  subscription_id uuid not null references public.qg_push_subscriptions(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','leased','sent','dead')),
  attempts integer not null default 0 check (attempts>=0),
  retry_at timestamptz not null default now(),
  lease_id uuid,
  last_http_status integer,
  created_at timestamptz not null default now(),
  unique(notification_id,subscription_id)
);
create index if not exists qg_push_outbox_dispatch_idx on public.qg_push_outbox(status,retry_at,created_at);
alter table public.qg_push_outbox enable row level security;
drop policy if exists qg_push_outbox_no_client_access on public.qg_push_outbox;
create policy qg_push_outbox_no_client_access on public.qg_push_outbox for all to anon,authenticated using(false) with check(false);
revoke all on public.qg_push_outbox from anon,authenticated;
grant select,insert,update,delete on public.qg_push_outbox to service_role;

CREATE OR REPLACE FUNCTION public.qg_lease_push()
 RETURNS SETOF qg_push_outbox
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  update public.qg_push_outbox
  set status='leased',
      attempts=attempts+1,
      retry_at=now()+interval '2 minutes',
      lease_id=gen_random_uuid()
  where id in (
    select id from public.qg_push_outbox
    where status in ('pending','leased') and retry_at<=now()
    order by created_at
    limit 25
    for update skip locked
  )
  returning *;
$function$;
CREATE OR REPLACE FUNCTION public.qg_finish_push(p_id uuid, p_lease uuid, p_http integer)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_sub uuid;
begin
  update public.qg_push_outbox
  set status=case
      when p_http between 200 and 299 then 'sent'
      when p_http in (404,410) or attempts>=6 then 'dead'
      else 'pending'
    end,
    last_http_status=p_http,
    retry_at=now()+make_interval(secs=>least(3600,30*power(2,attempts)::int))
  where id=p_id and status='leased' and lease_id=p_lease
  returning subscription_id into v_sub;
  if p_http in (404,410) and v_sub is not null then
    update public.qg_push_subscriptions set enabled=false,updated_at=now() where id=v_sub;
  end if;
end $function$;
CREATE OR REPLACE FUNCTION public.qg_wake_push()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_token text;
begin
  if not exists(
    select 1 from public.qg_push_outbox
    where status in ('pending','leased') and retry_at<=now()
  ) then return; end if;
  select worker_token into v_token from public.qg_push_config where id;
  if v_token is null then return; end if;
  perform net.http_post(
    url:='https://pkypiqhlrmzocysgeqew.supabase.co/functions/v1/queuego-push',
    headers:=jsonb_build_object('Content-Type','application/json','x-queuego-worker',v_token),
    body:='{"action":"dispatch"}'::jsonb,
    timeout_milliseconds:=5000
  );
end $function$;
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
  begin
    perform public.qg_wake_push();
  exception when others then
    raise warning 'QueueGo push dispatch deferred';
  end;
  return new;
end $function$;

drop trigger if exists qg_notifications_rider_push on public.notifications;
drop trigger if exists qg_notifications_queuego_push on public.notifications;
create trigger qg_notifications_queuego_push after insert on public.notifications
for each row execute function public.qg_enqueue_push();

CREATE OR REPLACE FUNCTION public.queuego_account_deletion_finalize(p_request_id uuid, p_auth_user_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
  v_req public.qg_account_deletion_requests%rowtype;
  v_user public.users%rowtype;
  v_shop_id uuid;
  v_rider_id uuid;
  v_check jsonb;
begin
  select * into v_req
  from public.qg_account_deletion_requests r
  where r.id=p_request_id and r.auth_user_id=p_auth_user_id
  for update;
  if not found then raise exception 'DELETION_REQUEST_NOT_FOUND' using errcode='P0001'; end if;
  if v_req.state<>'pending' then
    return jsonb_build_object('ok',v_req.state='completed','state',v_req.state);
  end if;

  select * into v_user
  from public.users u
  where u.id=v_req.user_id and u.auth_user_id=p_auth_user_id
  for update;
  if not found then raise exception 'ACCOUNT_NOT_FOUND' using errcode='P0001'; end if;
  if v_user.role='admin' then raise exception 'ADMIN_ACCOUNT_CANNOT_SELF_DELETE' using errcode='42501'; end if;

  v_check := public.queuego_account_deletion_eligibility(p_auth_user_id);
  if coalesce((v_check->>'eligible')::boolean,false) is not true then
    raise exception 'ACCOUNT_HAS_ACTIVE_WORK' using errcode='P0001',
      detail=v_check::text;
  end if;

  select sp.id into v_shop_id
  from public.shop_profiles sp where sp.user_id=v_user.id limit 1;
  select rp.id into v_rider_id
  from public.rider_profiles rp where rp.user_id=v_user.id limit 1;

  -- Personal communications / preferences / device subscriptions.
  delete from public.notifications where user_id=v_user.id;
  delete from public.qg_customer_favorites where user_id=v_user.id;
  delete from public.order_chat_messages where sender_id=v_user.id;
  delete from public.reviews where customer_id=v_user.id;

  delete from public.qg_ticket_events
  where actor_id=v_user.id
     or ticket_id in (select id from public.qg_support_tickets where user_id=v_user.id);
  delete from public.qg_support_tickets where user_id=v_user.id;

  delete from public.qg_rider_push_subscriptions where user_id=v_user.id;
  delete from public.qg_push_subscriptions where user_id=v_user.id;
  delete from public.qg_rider_action_receipts where user_id=v_user.id;
  delete from public.qg_merchant_action_receipts where user_id=v_user.id;

  if v_rider_id is not null then
    delete from public.qg_rider_order_offers where rider_id=v_rider_id;
    delete from public.qg_rider_offer_history where rider_id=v_rider_id;
    delete from public.qg_rider_presence_events where rider_id=v_rider_id;
  end if;

  -- Remove personal addresses/coordinates from completed transactional history.
  if v_user.role='customer' then
    update public.orders
       set delivery_address=null,delivery_latitude=null,delivery_longitude=null,note=null
     where customer_id=v_user.id;

    update public.deliveries d
       set delivery_address=null,delivery_latitude=null,delivery_longitude=null,note=null
     where exists (
       select 1 from public.orders o
       where o.id=d.order_id and o.customer_id=v_user.id
     );

    update public.market_orders
       set delivery_address='ข้อมูลถูกลบตามคำขอเจ้าของบัญชี',
           delivery_latitude=0,
           delivery_longitude=0
     where customer_id=v_user.id;

    update public.laundry_orders
       set pickup_address=null,pickup_latitude=null,pickup_longitude=null,note=null
     where customer_id=v_user.id;
  elsif v_user.role='shop' and v_shop_id is not null then
    update public.orders
       set pickup_address=null,pickup_latitude=null,pickup_longitude=null
     where shop_id=v_shop_id;
    update public.deliveries d
       set pickup_address=null,pickup_latitude=null,pickup_longitude=null
     where exists (
       select 1 from public.orders o
       where o.id=d.order_id and o.shop_id=v_shop_id
     );
  end if;

  update public.audit_logs
  set user_id=null,description=null,metadata='{}'::jsonb
  where user_id=v_user.id;

  -- Remove direct auth-user references that would otherwise block Auth deletion.
  update public.orders set staff_id=null where staff_id=p_auth_user_id;
  update public.pos_invites set used_by=null where used_by=p_auth_user_id;
  delete from public.pos_staff where user_id=p_auth_user_id;
  delete from public.user_active_sessions where user_id=p_auth_user_id;

  -- Preserve non-identifying role/profile tombstones for accounting and order integrity.
  if v_shop_id is not null then
    update public.shop_profiles
    set shop_name='ร้านที่ปิดบัญชี',
        phone='',address=null,latitude=null,longitude=null,
        status='deleted',metadata='{}'::jsonb,
        public_logo=null,public_cover=null,public_open_time=null,public_close_time=null,
        public_description=null,delivery_enabled=false,
        market_id=null,market_stall_no=null,market_zone=null,
        market_membership_confirmed=false,market_membership_confirmed_at=null,
        market_suggested_id=null,market_suggested_distance_km=null,
        market_membership_status='none',market_confirmed_at=null,
        market_proof_path=null,market_reviewed_by=null,market_reviewed_at=null,
        market_rejection_reason=null,updated_at=now()
    where id=v_shop_id;
    update public.products
    set available=false,delivery_available=false,pos_available=false,updated_at=now()
    where shop_id=v_shop_id;
  end if;

  if v_rider_id is not null then
    update public.rider_profiles
    set rider_name='ไรเดอร์ที่ปิดบัญชี',
        phone='',vehicle_type=null,vehicle_plate=null,vehicle_name=null,
        address=null,latitude=null,longitude=null,status='deleted',
        metadata='{}'::jsonb,vehicle_status='suspended',
        vehicle_verified_at=null,updated_at=now()
    where id=v_rider_id;
  end if;

  update public.users
  set auth_user_id=null,
      name='ผู้ใช้ที่ลบบัญชี',
      phone='',
      metadata='{}'::jsonb,
      status='deleted',
      suspended_at=null,
      suspension_reason=null,
      suspended_by=null,
      updated_at=now()
  where id=v_user.id;

  update public.qg_account_deletion_requests
  set state='completed',completed_at=now()
  where id=p_request_id;

  return jsonb_build_object('ok',true,'request_id',p_request_id,'user_id',v_user.id);
end;
$function$;
