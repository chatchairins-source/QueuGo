-- QueueGo Pilot Gate 1: Account Deletion + Privacy
-- Source-of-truth mirror for production Supabase.
-- Additive/idempotent: preserves non-identifying transactional history while removing direct PII.

create table if not exists public.qg_account_deletion_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete restrict,
  auth_user_id uuid,
  role text not null,
  previous_user_status text not null,
  previous_shop_status text,
  previous_rider_status text,
  state text not null default 'pending' check (state in ('pending','completed','failed')),
  requested_at timestamptz not null default now(),
  completed_at timestamptz,
  failed_at timestamptz
);

alter table public.qg_account_deletion_requests enable row level security;
drop policy if exists qg_account_deletion_requests_no_client_access on public.qg_account_deletion_requests;
create policy qg_account_deletion_requests_no_client_access
on public.qg_account_deletion_requests
for all to anon,authenticated
using(false) with check(false);

revoke all on public.qg_account_deletion_requests from anon,authenticated;
grant select,insert,update,delete on public.qg_account_deletion_requests to service_role;

CREATE OR REPLACE FUNCTION public.queuego_account_deletion_abort(p_request_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
  v_req public.qg_account_deletion_requests%rowtype;
  v_shop_id uuid;
  v_rider_id uuid;
begin
  select * into v_req
  from public.qg_account_deletion_requests r
  where r.id=p_request_id
  for update;
  if not found or v_req.state<>'pending' then return; end if;

  update public.users
  set status=v_req.previous_user_status,
      suspended_at=null,
      suspension_reason=null,
      suspended_by=null,
      updated_at=now()
  where id=v_req.user_id
    and suspension_reason='account_deletion_pending';

  select sp.id into v_shop_id
  from public.shop_profiles sp where sp.user_id=v_req.user_id limit 1;
  if v_shop_id is not null and v_req.previous_shop_status is not null then
    update public.shop_profiles
    set status=v_req.previous_shop_status,updated_at=now()
    where id=v_shop_id;
  end if;

  select rp.id into v_rider_id
  from public.rider_profiles rp where rp.user_id=v_req.user_id limit 1;
  if v_rider_id is not null and v_req.previous_rider_status is not null then
    update public.rider_profiles
    set status=v_req.previous_rider_status,
        vehicle_status=case when v_req.previous_rider_status='active'
          then 'active' else vehicle_status end,
        updated_at=now()
    where id=v_rider_id;
  end if;

  update public.qg_account_deletion_requests
  set state='failed',failed_at=now()
  where id=p_request_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.queuego_account_deletion_begin(p_auth_user_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
  v_user public.users%rowtype;
  v_shop_id uuid;
  v_rider_id uuid;
  v_shop_status text;
  v_rider_status text;
  v_check jsonb;
  v_request uuid;
begin
  select * into v_user
  from public.users u
  where u.auth_user_id=p_auth_user_id
  for update;

  if not found then
    raise exception 'ACCOUNT_NOT_FOUND' using errcode='P0001';
  end if;
  if v_user.role='admin' then
    raise exception 'ADMIN_ACCOUNT_CANNOT_SELF_DELETE' using errcode='42501';
  end if;

  v_check := public.queuego_account_deletion_eligibility(p_auth_user_id);
  if coalesce((v_check->>'eligible')::boolean,false) is not true then
    raise exception 'ACCOUNT_HAS_ACTIVE_WORK' using errcode='P0001',
      detail=v_check::text;
  end if;

  select sp.id,sp.status into v_shop_id,v_shop_status
  from public.shop_profiles sp where sp.user_id=v_user.id limit 1;
  select rp.id,rp.status into v_rider_id,v_rider_status
  from public.rider_profiles rp where rp.user_id=v_user.id limit 1;

  insert into public.qg_account_deletion_requests(
    user_id,auth_user_id,role,previous_user_status,previous_shop_status,previous_rider_status
  ) values (
    v_user.id,p_auth_user_id,v_user.role,v_user.status,v_shop_status,v_rider_status
  ) returning id into v_request;

  update public.users
  set status='suspended',
      suspended_at=now(),
      suspension_reason='account_deletion_pending',
      suspended_by=null,
      updated_at=now()
  where id=v_user.id;

  if v_shop_id is not null then
    update public.shop_profiles
    set status='suspended',delivery_enabled=false,updated_at=now()
    where id=v_shop_id;
    update public.shop_open_states
    set is_open=false,resume_at=null,updated_at=now()
    where shop_id=v_shop_id;
  end if;

  if v_rider_id is not null then
    update public.rider_profiles
    set status='suspended',vehicle_status='suspended',updated_at=now()
    where id=v_rider_id;
  end if;

  return jsonb_build_object(
    'request_id',v_request,
    'user_id',v_user.id,
    'role',v_user.role
  );
end;
$function$;

CREATE OR REPLACE FUNCTION public.queuego_account_deletion_eligibility(p_auth_user_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
  v_user_id uuid;
  v_role text;
  v_user_status text;
  v_shop_id uuid;
  v_rider_id uuid;
  v_orders integer := 0;
  v_market integer := 0;
  v_laundry integer := 0;
  v_routes integer := 0;
  v_offers integer := 0;
  v_tables integer := 0;
  v_total integer := 0;
begin
  select u.id,u.role,u.status
    into v_user_id,v_role,v_user_status
  from public.users u
  where u.auth_user_id = p_auth_user_id
  limit 1;

  if v_user_id is null then
    return jsonb_build_object('eligible',false,'code','ACCOUNT_NOT_FOUND','blockers',0);
  end if;
  if v_role = 'admin' then
    return jsonb_build_object('eligible',false,'code','ADMIN_ACCOUNT','blockers',1);
  end if;
  if v_user_status = 'deleted' then
    return jsonb_build_object('eligible',false,'code','ALREADY_DELETED','blockers',0);
  end if;

  select sp.id into v_shop_id
  from public.shop_profiles sp where sp.user_id=v_user_id limit 1;
  select rp.id into v_rider_id
  from public.rider_profiles rp where rp.user_id=v_user_id limit 1;

  select count(*) into v_orders
  from public.orders o
  where (
      (v_role='customer' and o.customer_id=v_user_id)
      or (v_role='shop' and v_shop_id is not null and o.shop_id=v_shop_id)
      or (v_role='rider' and v_rider_id is not null and o.rider_id=v_rider_id)
    )
    and lower(coalesce(o.status,'')) not in
      ('completed','cancelled','canceled','no_rider_available','rejected','failed');

  if v_role='customer' then
    select count(*) into v_market
    from public.market_orders mo
    where mo.customer_id=v_user_id
      and lower(coalesce(mo.status,'')) not in
        ('completed','cancelled','canceled','no_rider_available','rejected','failed');
  end if;

  if v_role='customer' then
    select count(*) into v_laundry
    from public.laundry_orders lo
    where lo.customer_id=v_user_id
      and lower(coalesce(lo.status,'')) not in
        ('completed','cancelled','canceled','rejected','failed');
  elsif v_role='shop' and v_shop_id is not null then
    select count(*) into v_laundry
    from public.laundry_orders lo
    where exists (
      select 1 from public.laundry_hubs h
      where h.id=lo.hub_id and h.shop_id=v_shop_id
    )
      and lower(coalesce(lo.status,'')) not in
        ('completed','cancelled','canceled','rejected','failed');
  elsif v_role='rider' and v_rider_id is not null then
    select count(*) into v_laundry
    from public.laundry_rider_jobs j
    where j.rider_id=v_rider_id
      and lower(coalesce(j.status,'')) not in
        ('completed','cancelled','canceled','rejected','failed','delivered');
  end if;

  if v_role='rider' and v_rider_id is not null then
    select count(*) into v_routes
    from public.route_bundles rb
    where rb.rider_id=v_rider_id
      and lower(coalesce(rb.status,'')) not in
        ('completed','cancelled','canceled','expired','failed');

    select count(*) into v_offers
    from public.qg_rider_order_offers ro
    where ro.rider_id=v_rider_id and ro.expires_at > now();
  end if;

  if v_role='shop' and v_shop_id is not null then
    select count(*) into v_tables
    from public.qg_table_sessions ts
    where ts.shop_id=v_shop_id
      and ts.revoked_at is null
      and ts.expires_at > now();
  end if;

  v_total := v_orders+v_market+v_laundry+v_routes+v_offers+v_tables;
  return jsonb_build_object(
    'eligible',v_total=0,
    'code',case when v_total=0 then 'OK' else 'ACTIVE_WORK' end,
    'blockers',v_total,
    'orders',v_orders,
    'market_orders',v_market,
    'laundry',v_laundry,
    'routes',v_routes,
    'live_offers',v_offers,
    'table_sessions',v_tables,
    'role',v_role
  );
end;
$function$;

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
  delete from public.qg_call_sessions where caller_user_id=v_user.id or callee_user_id=v_user.id;
  delete from public.qg_customer_favorites where user_id=v_user.id;
  delete from public.order_chat_messages where sender_id=v_user.id;
  delete from public.reviews where customer_id=v_user.id;
  delete from public.qg_ugc_terms_acceptances where user_id=v_user.id;
  delete from public.qg_user_blocks where blocker_user_id=v_user.id or blocked_user_id=v_user.id;
  update public.qg_ugc_reports
     set reporter_user_id=case when reporter_user_id=v_user.id then null else reporter_user_id end,
         reported_user_id=case when reported_user_id=v_user.id then null else reported_user_id end,
         details=case when reporter_user_id=v_user.id or reported_user_id=v_user.id then null else details end,
         content_snapshot=case when reporter_user_id=v_user.id or reported_user_id=v_user.id then null else content_snapshot end,
         updated_at=now()
   where reporter_user_id=v_user.id or reported_user_id=v_user.id;

  delete from public.qg_ticket_events
  where actor_id=v_user.id
     or ticket_id in (select id from public.qg_support_tickets where user_id=v_user.id);
  delete from public.qg_support_tickets where user_id=v_user.id;

  delete from public.qg_rider_push_subscriptions where user_id=v_user.id;
  delete from public.qg_push_subscriptions where user_id=v_user.id;
  delete from public.qg_native_push_tokens where user_id=v_user.id;
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

revoke all on function public.queuego_account_deletion_eligibility(uuid) from public,anon,authenticated;
revoke all on function public.queuego_account_deletion_begin(uuid) from public,anon,authenticated;
revoke all on function public.queuego_account_deletion_abort(uuid) from public,anon,authenticated;
revoke all on function public.queuego_account_deletion_finalize(uuid,uuid) from public,anon,authenticated;
grant execute on function public.queuego_account_deletion_eligibility(uuid) to service_role;
grant execute on function public.queuego_account_deletion_begin(uuid) to service_role;
grant execute on function public.queuego_account_deletion_abort(uuid) to service_role;
grant execute on function public.queuego_account_deletion_finalize(uuid,uuid) to service_role;
