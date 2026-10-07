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
  delete from public.qg_ugc_terms_acceptances where user_id=v_user.id;
  delete from public.qg_user_blocks where blocker_user_id=v_user.id or blocked_user_id=v_user.id;
  update public.qg_ugc_reports
     set reporter_user_id=case when reporter_user_id=v_user.id then null else reporter_user_id end,
         reported_user_id=case when reported_user_id=v_user.id then null else reported_user_id end,
         details=case when reporter_user_id=v_user.id or reported_user_id=v_user.id then null else details end,
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
