create table if not exists public.qg_rider_delivery_returns (
  id uuid primary key,
  order_id uuid not null references public.orders(id) on delete cascade,
  ticket_id uuid unique references public.qg_support_tickets(id) on delete set null,
  rider_id uuid not null references public.rider_profiles(id),
  shop_id uuid not null references public.shop_profiles(id),
  customer_id uuid references public.users(id),
  reason text not null check (reason in (
    'customer_unreachable','recipient_refuses_delivery','recipient_refuses_cash',
    'wrong_dropoff','unsafe_dropoff','other'
  )),
  status text not null default 'reported' check (status in (
    'reported','return_required','returning','returned','resolved_no_return','cancelled'
  )),
  admin_id uuid references public.users(id),
  admin_note text check (admin_note is null or char_length(admin_note) <= 2000),
  return_proof_path text,
  expected_refund_amount numeric not null default 0 check (expected_refund_amount >= 0),
  reported_at timestamptz not null default now(),
  return_required_at timestamptz,
  started_return_at timestamptz,
  returned_at timestamptz,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists qg_rider_delivery_returns_one_active_per_order
  on public.qg_rider_delivery_returns(order_id)
  where status in ('reported','return_required','returning');

create index if not exists qg_rider_delivery_returns_rider_status_idx
  on public.qg_rider_delivery_returns(rider_id,status,updated_at desc);
create index if not exists qg_rider_delivery_returns_ticket_idx
  on public.qg_rider_delivery_returns(ticket_id);

alter table public.qg_rider_delivery_returns enable row level security;
revoke all on table public.qg_rider_delivery_returns from anon, authenticated;

create table if not exists public.qg_rider_cash_return_refunds (
  id uuid primary key,
  return_case_id uuid not null unique references public.qg_rider_delivery_returns(id) on delete cascade,
  order_id uuid not null unique references public.orders(id) on delete cascade,
  rider_id uuid not null references public.rider_profiles(id),
  shop_id uuid not null references public.shop_profiles(id),
  amount numeric not null check (amount >= 0),
  refunded_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists qg_rider_cash_return_refunds_rider_idx
  on public.qg_rider_cash_return_refunds(rider_id,refunded_at desc);

alter table public.qg_rider_cash_return_refunds enable row level security;
revoke all on table public.qg_rider_cash_return_refunds from anon, authenticated;

create or replace function public.qg_rider_report_delivery_issue(
  p_case_id uuid,p_ticket_id uuid,p_order_id uuid,p_reason text,p_note text default null
) returns uuid
language plpgsql security definer set search_path=public,pg_temp
as $$
declare
  v_rider public.rider_profiles%rowtype;
  v_user public.users%rowtype;
  v_order public.orders%rowtype;
  v_existing uuid;
  v_details text;
  v_reason_label text;
begin
  select r.*,u.id as user_id into v_rider
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' limit 1;
  if v_rider.id is null then raise exception 'active rider required'; end if;
  select * into v_user from public.users where id=v_rider.user_id;

  if p_case_id is null or p_ticket_id is null or p_order_id is null then raise exception 'invalid request'; end if;
  if p_reason not in ('customer_unreachable','recipient_refuses_delivery','recipient_refuses_cash','wrong_dropoff','unsafe_dropoff','other')
    then raise exception 'invalid reason'; end if;
  if char_length(trim(coalesce(p_note,'')))>1200 then raise exception 'note too long'; end if;

  select * into v_order from public.orders
  where id=p_order_id and rider_id=v_rider.id for update;
  if not found then raise exception 'order unavailable'; end if;
  if v_order.status<>'in_progress' then raise exception 'delivery is not in progress'; end if;
  if v_order.rider_arrived_customer_at is null then raise exception 'arrive at customer before reporting delivery issue'; end if;

  select id into v_existing from public.qg_rider_delivery_returns
  where order_id=p_order_id and status in ('reported','return_required','returning')
  order by created_at desc limit 1;
  if v_existing is not null then return v_existing; end if;

  v_reason_label:=case p_reason
    when 'customer_unreachable' then 'ติดต่อลูกค้าไม่ได้'
    when 'recipient_refuses_delivery' then 'ลูกค้าปฏิเสธรับสินค้า'
    when 'recipient_refuses_cash' then 'ลูกค้าปฏิเสธชำระเงิน'
    when 'wrong_dropoff' then 'ที่อยู่หรือจุดส่งมีปัญหา'
    when 'unsafe_dropoff' then 'ไม่สามารถส่งได้อย่างปลอดภัย'
    else 'ปัญหาอื่น' end;

  v_details:='[DELIVERY_ISSUE:'||p_reason||'] '||coalesce(v_order.order_number,p_order_id::text)||
    ' · '||v_reason_label||case when trim(coalesce(p_note,''))='' then '' else ' · หมายเหตุ: '||left(trim(p_note),1200) end;
  if char_length(v_details) not between 10 and 2000 then raise exception 'invalid details'; end if;

  insert into public.qg_support_tickets(id,user_id,role,order_id,category,details,evidence_path)
  values(p_ticket_id,v_user.id,'rider',p_order_id,'rider',v_details,null) on conflict(id) do nothing;
  if not exists(select 1 from public.qg_support_tickets where id=p_ticket_id and user_id=v_user.id)
    then raise exception 'ticket id already in use'; end if;
  if not exists(select 1 from public.qg_ticket_events where ticket_id=p_ticket_id) then
    insert into public.qg_ticket_events(ticket_id,actor_id,to_status,note)
    values(p_ticket_id,v_user.id,'OPEN','delivery issue reported');
  end if;

  insert into public.qg_rider_delivery_returns(
    id,order_id,ticket_id,rider_id,shop_id,customer_id,reason,status,expected_refund_amount
  ) values(
    p_case_id,p_order_id,p_ticket_id,v_rider.id,v_order.shop_id,v_order.customer_id,p_reason,'reported',coalesce(v_order.subtotal,0)
  );

  insert into public.notifications(user_id,title,message,type,reference_id)
  select u.id,'มีเคสจัดส่งไม่สำเร็จ',coalesce(v_order.order_number,p_order_id::text)||' · '||v_reason_label,'order',p_order_id
  from public.users u where u.role='admin' and u.status='active';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user.id,'rider_delivery_issue_reported','order',p_order_id,v_reason_label,
    jsonb_build_object('return_case_id',p_case_id,'ticket_id',p_ticket_id,'reason',p_reason,'rider_id',v_rider.id,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id));
  return p_case_id;
end $$;

create or replace function public.qg_admin_delivery_return_action(p_case_id uuid,p_action text,p_note text default null)
returns text language plpgsql security definer set search_path=public,pg_temp
as $$
declare
  v_admin public.users%rowtype;
  v_case public.qg_rider_delivery_returns%rowtype;
  v_order public.orders%rowtype;
  v_shop_user uuid;
  v_rider_user uuid;
  v_action text:=lower(trim(coalesce(p_action,'')));
  v_next text;
begin
  select * into v_admin from public.users where auth_user_id=auth.uid() and role='admin' and status='active' limit 1;
  if v_admin.id is null then raise exception 'active admin required'; end if;
  if char_length(trim(coalesce(p_note,'')))>2000 then raise exception 'note too long'; end if;

  select * into v_case from public.qg_rider_delivery_returns where id=p_case_id for update;
  if not found then raise exception 'return case unavailable'; end if;
  select * into v_order from public.orders where id=v_case.order_id for update;
  select user_id into v_shop_user from public.shop_profiles where id=v_case.shop_id;
  select user_id into v_rider_user from public.rider_profiles where id=v_case.rider_id;

  if v_action='return_to_shop' then
    if v_case.status='return_required' then return 'return_required'; end if;
    if v_case.status<>'reported' then raise exception 'case is not waiting for return decision'; end if;
    if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;
    v_next:='return_required';
    update public.qg_rider_delivery_returns set status=v_next,admin_id=v_admin.id,
      admin_note=nullif(trim(coalesce(p_note,'')),''),return_required_at=coalesce(return_required_at,now()),updated_at=now()
      where id=p_case_id;
    update public.qg_support_tickets set status='IN_REVIEW',admin_id=v_admin.id,
      admin_note=coalesce(nullif(trim(coalesce(p_note,'')),''),'ให้ Rider คืนสินค้าไปยังร้าน'),updated_at=now()
      where id=v_case.ticket_id;
    insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
    select v_case.ticket_id,v_admin.id,t.status,'IN_REVIEW',coalesce(nullif(trim(coalesce(p_note,'')),''),'return to shop required')
    from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'IN_REVIEW';
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_rider_user,'ให้นำสินค้าคืนร้าน',coalesce(v_order.order_number,v_order.id::text)||' · ระบบอนุมัติให้คืนสินค้าไปยังร้าน','order',v_order.id);
    if v_shop_user is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_shop_user,'Rider กำลังนำสินค้าคืนร้าน',coalesce(v_order.order_number,v_order.id::text)||' · กรุณารับสินค้าคืนและคืนเงินสดให้ Rider','order',v_order.id);
    end if;
    if v_case.customer_id is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_case.customer_id,'กำลังดำเนินการออเดอร์',coalesce(v_order.order_number,v_order.id::text)||' · ฝ่ายดูแลกำลังดำเนินการกรณีจัดส่งไม่สำเร็จ','order',v_order.id);
    end if;
  elsif v_action='resolve_no_return' then
    if v_case.status='resolved_no_return' then return 'resolved_no_return'; end if;
    if v_case.status not in ('reported','return_required') then raise exception 'case cannot be resumed'; end if;
    if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;
    v_next:='resolved_no_return';
    update public.qg_rider_delivery_returns set status=v_next,admin_id=v_admin.id,
      admin_note=nullif(trim(coalesce(p_note,'')),''),resolved_at=now(),updated_at=now() where id=p_case_id;
    update public.qg_support_tickets set status='RESOLVED',admin_id=v_admin.id,
      admin_note=coalesce(nullif(trim(coalesce(p_note,'')),''),'ให้ Rider ดำเนินการส่งต่อ'),updated_at=now() where id=v_case.ticket_id;
    insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
    select v_case.ticket_id,v_admin.id,t.status,'RESOLVED',coalesce(nullif(trim(coalesce(p_note,'')),''),'continue delivery')
    from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'RESOLVED';
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_rider_user,'ดำเนินการส่งต่อได้',coalesce(v_order.order_number,v_order.id::text)||' · ฝ่ายดูแลให้ดำเนินการจัดส่งต่อ','order',v_order.id);
  elsif v_action='cancel_case' then
    if v_case.status in ('returned','resolved_no_return','cancelled') then return v_case.status; end if;
    v_next:='cancelled';
    update public.qg_rider_delivery_returns set status=v_next,admin_id=v_admin.id,
      admin_note=nullif(trim(coalesce(p_note,'')),''),resolved_at=now(),updated_at=now() where id=p_case_id;
    update public.qg_support_tickets set status='CLOSED',admin_id=v_admin.id,
      admin_note=nullif(trim(coalesce(p_note,'')),''),updated_at=now() where id=v_case.ticket_id;
    insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
    select v_case.ticket_id,v_admin.id,t.status,'CLOSED',coalesce(nullif(trim(coalesce(p_note,'')),''),'return case closed')
    from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'CLOSED';
  else
    raise exception 'invalid return action';
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_admin.id,'admin_delivery_return_action','delivery_return',p_case_id,v_action,
    jsonb_build_object('order_id',v_case.order_id,'from',v_case.status,'to',v_next));
  return v_next;
end $$;

create or replace function public.qg_rider_active_delivery_return()
returns table(
  case_id uuid,order_id uuid,order_number text,status text,reason text,admin_note text,
  shop_id uuid,shop_name text,shop_address text,shop_latitude double precision,shop_longitude double precision,
  expected_refund_amount numeric,ticket_id uuid,reported_at timestamptz,return_required_at timestamptz,started_return_at timestamptz
)
language plpgsql security definer set search_path=public,pg_temp
as $$
declare v_rider uuid;
begin
  select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' limit 1;
  if v_rider is null then raise exception 'active rider required'; end if;
  return query
  select c.id,c.order_id,o.order_number,c.status,c.reason,c.admin_note,c.shop_id,s.shop_name,s.address,s.latitude,s.longitude,
    c.expected_refund_amount,c.ticket_id,c.reported_at,c.return_required_at,c.started_return_at
  from public.qg_rider_delivery_returns c
  join public.orders o on o.id=c.order_id join public.shop_profiles s on s.id=c.shop_id
  where c.rider_id=v_rider and c.status in ('reported','return_required','returning')
  order by c.updated_at desc limit 1;
end $$;

create or replace function public.qg_rider_start_delivery_return(p_case_id uuid)
returns text language plpgsql security definer set search_path=public,pg_temp
as $$
declare v_rider uuid;v_case public.qg_rider_delivery_returns%rowtype;v_order public.orders%rowtype;v_shop_user uuid;
begin
  select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' limit 1;
  if v_rider is null then raise exception 'active rider required'; end if;
  select * into v_case from public.qg_rider_delivery_returns where id=p_case_id and rider_id=v_rider for update;
  if not found then raise exception 'return case unavailable'; end if;
  if v_case.status='returning' then return 'returning'; end if;
  if v_case.status<>'return_required' then raise exception 'return is not approved'; end if;
  select * into v_order from public.orders where id=v_case.order_id for update;
  if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;
  update public.qg_rider_delivery_returns set status='returning',started_return_at=coalesce(started_return_at,now()),updated_at=now() where id=p_case_id;
  select user_id into v_shop_user from public.shop_profiles where id=v_case.shop_id;
  if v_shop_user is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_shop_user,'Rider กำลังกลับมาที่ร้าน',coalesce(v_order.order_number,v_order.id::text)||' · เตรียมรับสินค้าคืนและคืนเงินสดให้ Rider','order',v_order.id);
  end if;
  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  select u.id,'rider_delivery_return_started','delivery_return',p_case_id,'returning',jsonb_build_object('order_id',v_case.order_id,'rider_id',v_rider)
  from public.rider_profiles r join public.users u on u.id=r.user_id where r.id=v_rider;
  return 'returning';
end $$;

create or replace function public.qg_rider_complete_delivery_return(p_case_id uuid,p_proof_path text)
returns text language plpgsql security definer set search_path=public,pg_temp
as $$
declare
  v_rider public.rider_profiles%rowtype;v_user public.users%rowtype;v_case public.qg_rider_delivery_returns%rowtype;
  v_order public.orders%rowtype;v_shop_user uuid;v_clean_note text;
begin
  select r.* into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' limit 1;
  if v_rider.id is null then raise exception 'active rider required'; end if;
  select * into v_user from public.users where id=v_rider.user_id;

  if p_proof_path is null or p_proof_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
    or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_proof_path)
    then raise exception 'valid return proof required'; end if;

  select * into v_case from public.qg_rider_delivery_returns where id=p_case_id and rider_id=v_rider.id for update;
  if not found then raise exception 'return case unavailable'; end if;
  if v_case.status='returned' then return 'returned'; end if;
  if v_case.status not in ('return_required','returning') then raise exception 'return is not active'; end if;
  select * into v_order from public.orders where id=v_case.order_id for update;
  if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;

  insert into public.qg_rider_cash_return_refunds(id,return_case_id,order_id,rider_id,shop_id,amount)
  values(gen_random_uuid(),v_case.id,v_order.id,v_rider.id,v_case.shop_id,coalesce(v_case.expected_refund_amount,v_order.subtotal,0))
  on conflict(order_id) do nothing;

  update public.qg_rider_delivery_returns set status='returned',return_proof_path=p_proof_path,returned_at=now(),resolved_at=now(),updated_at=now()
  where id=p_case_id;
  update public.qg_support_tickets set status='RESOLVED',admin_note=coalesce(admin_note,'Rider คืนสินค้าให้ร้านเรียบร้อย'),updated_at=now()
  where id=v_case.ticket_id;
  insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
  select v_case.ticket_id,v_user.id,t.status,'RESOLVED','return to shop completed'
  from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'RESOLVED';

  v_clean_note:=regexp_replace(coalesce(v_order.note,''),E'^(__QT_ORDER_STATUS__=[^\\n]*\\n?)+','','g');
  perform set_config('queuego.v22_transition','rpc',true);
  update public.orders set status='cancelled',
    note='__QT_ORDER_STATUS__=cancelled'||E'\n'||'__QT_RETURNED_TO_SHOP__='||p_case_id::text||E'\n'||v_clean_note,
    cancelled_at=coalesce(cancelled_at,now()),updated_at=now() where id=v_order.id;

  select user_id into v_shop_user from public.shop_profiles where id=v_case.shop_id;
  if v_shop_user is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_shop_user,'รับสินค้าคืนเรียบร้อย',coalesce(v_order.order_number,v_order.id::text)||' · ระบบบันทึกการคืนสินค้าและเงินคืน Rider แล้ว','order',v_order.id);
  end if;
  if v_case.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_case.customer_id,'ออเดอร์ถูกยุติหลังจัดส่งไม่สำเร็จ',coalesce(v_order.order_number,v_order.id::text)||' · สินค้าถูกส่งคืนร้านแล้ว','order',v_order.id);
  end if;
  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user.id,'rider_delivery_return_completed','delivery_return',p_case_id,'returned',
    jsonb_build_object('order_id',v_order.id,'rider_id',v_rider.id,'shop_id',v_case.shop_id,
      'refund_amount',coalesce(v_case.expected_refund_amount,v_order.subtotal,0),'proof_path',p_proof_path));
  return 'returned';
end $$;

create or replace function public.qg_admin_delivery_return_cases()
returns table(
  case_id uuid,order_id uuid,order_number text,ticket_id uuid,rider_id uuid,rider_name text,
  shop_id uuid,shop_name text,customer_id uuid,reason text,status text,admin_note text,
  expected_refund_amount numeric,reported_at timestamptz,return_required_at timestamptz,
  started_return_at timestamptz,returned_at timestamptz,updated_at timestamptz
)
language plpgsql security definer set search_path=public,pg_temp
as $$
declare v_admin uuid;
begin
  select id into v_admin from public.users where auth_user_id=auth.uid() and role='admin' and status='active' limit 1;
  if v_admin is null then raise exception 'active admin required'; end if;
  return query
  select c.id,c.order_id,o.order_number,c.ticket_id,c.rider_id,r.rider_name,c.shop_id,s.shop_name,c.customer_id,
    c.reason,c.status,c.admin_note,c.expected_refund_amount,c.reported_at,c.return_required_at,c.started_return_at,c.returned_at,c.updated_at
  from public.qg_rider_delivery_returns c join public.orders o on o.id=c.order_id
  join public.rider_profiles r on r.id=c.rider_id join public.shop_profiles s on s.id=c.shop_id
  order by case c.status when 'reported' then 0 when 'return_required' then 1 when 'returning' then 2 else 3 end,c.updated_at desc limit 200;
end $$;

create or replace function public.qg_rider_cash_ledger(p_from date,p_to date)
returns jsonb language plpgsql security definer set search_path=public,pg_temp
as $$
declare v_rider uuid;v_rows jsonb;
begin
  select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
  if v_rider is null then raise exception 'active rider required'; end if;
  if p_from is null or p_to is null or p_to<p_from or p_to>p_from+31 then raise exception 'invalid date range'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'order_id',o.id,'order_number',o.order_number,'status',o.status,'customer_id',o.customer_id,'shop_id',o.shop_id,'rider_id',o.rider_id,
    'subtotal',o.subtotal,'delivery_fee',o.delivery_fee,'gp_amount',o.gp_amount,'total',o.total_amount,
    'cash_paid_merchant',a.amount,'cash_paid_at',a.paid_at,'merchant_confirmed',mr.amount,'merchant_confirmed_at',mr.received_at,
    'cash_refunded_merchant',coalesce(rr.amount,0),'cash_refunded_at',rr.refunded_at,
    'cash_collected_customer',case when p.status='paid' then p.amount else 0 end,'customer_paid_at',p.paid_at,
    'payment_id',p.id,'payment_status',p.status,'proof_verified_at',d.verified_at,'created_at',o.created_at
  ) order by o.created_at desc),'[]'::jsonb) into v_rows
  from public.orders o
  left join public.rider_cash_advances a on a.order_id=o.id
  left join public.merchant_cash_receipts mr on mr.order_id=o.id
  left join public.qg_rider_cash_return_refunds rr on rr.order_id=o.id
  left join public.payments p on p.order_id=o.id and p.payment_method='cash'
  left join public.qg_delivery_proofs d on d.order_id=o.id
  where o.rider_id=v_rider and o.order_type='shopping'
    and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to;
  return v_rows;
end $$;

revoke all on function public.qg_rider_report_delivery_issue(uuid,uuid,uuid,text,text) from public,anon;
revoke all on function public.qg_admin_delivery_return_action(uuid,text,text) from public,anon;
revoke all on function public.qg_rider_active_delivery_return() from public,anon;
revoke all on function public.qg_rider_start_delivery_return(uuid) from public,anon;
revoke all on function public.qg_rider_complete_delivery_return(uuid,text) from public,anon;
revoke all on function public.qg_admin_delivery_return_cases() from public,anon;
revoke all on function public.qg_rider_cash_ledger(date,date) from public,anon;

grant execute on function public.qg_rider_report_delivery_issue(uuid,uuid,uuid,text,text) to authenticated;
grant execute on function public.qg_admin_delivery_return_action(uuid,text,text) to authenticated;
grant execute on function public.qg_rider_active_delivery_return() to authenticated;
grant execute on function public.qg_rider_start_delivery_return(uuid) to authenticated;
grant execute on function public.qg_rider_complete_delivery_return(uuid,text) to authenticated;
grant execute on function public.qg_admin_delivery_return_cases() to authenticated;
grant execute on function public.qg_rider_cash_ledger(date,date) to authenticated;
