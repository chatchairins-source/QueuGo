-- Multi-stop Return-to-Sender for QueueGo Market and single-shop cash delivery.
-- Production migration source mirror. Tables stay RPC-only.

alter table public.qg_rider_delivery_returns
  add column if not exists market_order_id uuid references public.market_orders(id) on delete cascade,
  add column if not exists return_stop_count integer not null default 1 check (return_stop_count >= 1);

create unique index if not exists qg_rider_delivery_returns_one_active_market_group
  on public.qg_rider_delivery_returns(market_order_id)
  where market_order_id is not null
    and status in ('reported','return_required','returning');

create table if not exists public.qg_rider_delivery_return_stops (
  id uuid primary key,
  return_case_id uuid not null references public.qg_rider_delivery_returns(id) on delete cascade,
  pickup_id uuid references public.market_order_pickups(id) on delete set null,
  order_id uuid not null references public.orders(id) on delete cascade,
  shop_id uuid not null references public.shop_profiles(id),
  return_sequence integer not null check (return_sequence >= 1),
  refund_amount numeric not null default 0 check (refund_amount >= 0),
  status text not null default 'pending' check (status in ('pending','returning','returned','cancelled')),
  proof_path text,
  started_at timestamptz,
  returned_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(return_case_id,order_id),
  unique(return_case_id,return_sequence)
);

create index if not exists qg_rider_delivery_return_stops_case_status_idx
  on public.qg_rider_delivery_return_stops(return_case_id,status,return_sequence);
create index if not exists qg_rider_delivery_return_stops_shop_idx
  on public.qg_rider_delivery_return_stops(shop_id);
create index if not exists qg_rider_delivery_return_stops_order_idx
  on public.qg_rider_delivery_return_stops(order_id);
create index if not exists qg_rider_delivery_return_stops_pickup_idx
  on public.qg_rider_delivery_return_stops(pickup_id);

alter table public.qg_rider_delivery_return_stops enable row level security;
revoke all on table public.qg_rider_delivery_return_stops from anon, authenticated;

create policy qg_rider_delivery_return_stops_rpc_only
on public.qg_rider_delivery_return_stops
for all to authenticated using(false) with check(false);

update public.qg_rider_delivery_returns c
set market_order_id=o.market_order_id
from public.orders o
where o.id=c.order_id
  and c.market_order_id is null
  and o.market_order_id is not null;

insert into public.qg_rider_delivery_return_stops(
  id,return_case_id,pickup_id,order_id,shop_id,return_sequence,refund_amount,status,proof_path,started_at,returned_at
)
select
  gen_random_uuid(),c.id,null,c.order_id,c.shop_id,1,
  coalesce(a.amount,c.expected_refund_amount,0),
  case
    when c.status='returned' then 'returned'
    when c.status='returning' then 'returning'
    when c.status in ('resolved_no_return','cancelled') then 'cancelled'
    else 'pending'
  end,
  c.return_proof_path,c.started_return_at,c.returned_at
from public.qg_rider_delivery_returns c
left join public.rider_cash_advances a on a.order_id=c.order_id
where not exists(
  select 1 from public.qg_rider_delivery_return_stops s where s.return_case_id=c.id
);

create or replace function public.qg_rider_report_delivery_issue(
  p_case_id uuid,p_ticket_id uuid,p_order_id uuid,p_reason text,p_note text default null
)
returns uuid
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider public.rider_profiles%rowtype;
  v_user public.users%rowtype;
  v_order public.orders%rowtype;
  v_existing uuid;
  v_details text;
  v_reason_label text;
  v_primary_shop uuid;
  v_expected numeric:=0;
  v_stop_count integer:=0;
begin
  select r.* into v_rider
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
  limit 1;
  if v_rider.id is null then raise exception 'active rider required'; end if;
  select * into v_user from public.users where id=v_rider.user_id;

  if p_case_id is null or p_ticket_id is null or p_order_id is null then raise exception 'invalid request'; end if;
  if p_reason not in ('customer_unreachable','recipient_refuses_delivery','recipient_refuses_cash','wrong_dropoff','unsafe_dropoff','other')
    then raise exception 'invalid reason'; end if;
  if char_length(trim(coalesce(p_note,'')))>1200 then raise exception 'note too long'; end if;

  select * into v_order
  from public.orders
  where id=p_order_id and rider_id=v_rider.id
  for update;
  if not found then raise exception 'order unavailable'; end if;
  if v_order.status<>'in_progress' then raise exception 'delivery is not in progress'; end if;
  if v_order.rider_arrived_customer_at is null then raise exception 'arrive at customer before reporting delivery issue'; end if;

  select c.id into v_existing
  from public.qg_rider_delivery_returns c
  where (c.order_id=p_order_id or (v_order.market_order_id is not null and c.market_order_id=v_order.market_order_id))
    and c.status in ('reported','return_required','returning')
  order by c.created_at desc limit 1;
  if v_existing is not null then return v_existing; end if;

  if v_order.market_order_id is not null then
    select coalesce(sum(mp.cash_paid_amount),0),count(*)::int
    into v_expected,v_stop_count
    from public.market_order_pickups mp
    join public.orders child on child.id=mp.order_id
    where mp.market_order_id=v_order.market_order_id
      and mp.status='PICKED_UP'
      and mp.cash_paid_amount>0
      and child.rider_id=v_rider.id;

    select mp.shop_id into v_primary_shop
    from public.market_order_pickups mp
    join public.orders child on child.id=mp.order_id
    where mp.market_order_id=v_order.market_order_id
      and mp.status='PICKED_UP'
      and mp.cash_paid_amount>0
      and child.rider_id=v_rider.id
    order by mp.pickup_sequence desc,mp.created_at desc
    limit 1;

    if v_stop_count=0 or v_primary_shop is null then raise exception 'market return has no paid pickup stops'; end if;
  else
    v_primary_shop:=v_order.shop_id;
    select coalesce(a.amount,v_order.subtotal,0)
    into v_expected
    from (select 1) seed
    left join public.rider_cash_advances a on a.order_id=v_order.id;
    v_stop_count:=1;
  end if;

  v_reason_label:=case p_reason
    when 'customer_unreachable' then 'ติดต่อลูกค้าไม่ได้'
    when 'recipient_refuses_delivery' then 'ลูกค้าปฏิเสธรับสินค้า'
    when 'recipient_refuses_cash' then 'ลูกค้าปฏิเสธชำระเงิน'
    when 'wrong_dropoff' then 'ที่อยู่หรือจุดส่งมีปัญหา'
    when 'unsafe_dropoff' then 'ไม่สามารถส่งได้อย่างปลอดภัย'
    else 'ปัญหาอื่น' end;

  v_details:='[DELIVERY_ISSUE:'||p_reason||'] '||coalesce(v_order.order_number,p_order_id::text)||' · '||v_reason_label||
    case when v_order.market_order_id is null then '' else ' · คืนสินค้า '||v_stop_count||' ร้าน' end||
    case when trim(coalesce(p_note,''))='' then '' else ' · หมายเหตุ: '||left(trim(p_note),1200) end;

  if char_length(v_details) not between 10 and 2000 then raise exception 'invalid details'; end if;

  insert into public.qg_support_tickets(id,user_id,role,order_id,category,details,evidence_path)
  values(p_ticket_id,v_user.id,'rider',p_order_id,'rider',v_details,null)
  on conflict(id) do nothing;

  if not exists(select 1 from public.qg_support_tickets where id=p_ticket_id and user_id=v_user.id)
    then raise exception 'ticket id already in use'; end if;

  if not exists(select 1 from public.qg_ticket_events where ticket_id=p_ticket_id) then
    insert into public.qg_ticket_events(ticket_id,actor_id,to_status,note)
    values(p_ticket_id,v_user.id,'OPEN','delivery issue reported');
  end if;

  insert into public.qg_rider_delivery_returns(
    id,order_id,ticket_id,rider_id,shop_id,customer_id,reason,status,expected_refund_amount,market_order_id,return_stop_count
  ) values(
    p_case_id,p_order_id,p_ticket_id,v_rider.id,v_primary_shop,v_order.customer_id,p_reason,'reported',
    v_expected,v_order.market_order_id,v_stop_count
  );

  if v_order.market_order_id is not null then
    insert into public.qg_rider_delivery_return_stops(
      id,return_case_id,pickup_id,order_id,shop_id,return_sequence,refund_amount,status
    )
    select gen_random_uuid(),p_case_id,x.id,x.order_id,x.shop_id,
           row_number() over(order by x.pickup_sequence desc,x.created_at desc)::int,
           x.cash_paid_amount,'pending'
    from public.market_order_pickups x
    join public.orders child on child.id=x.order_id
    where x.market_order_id=v_order.market_order_id
      and x.status='PICKED_UP'
      and x.cash_paid_amount>0
      and child.rider_id=v_rider.id
    order by x.pickup_sequence desc,x.created_at desc;
  else
    insert into public.qg_rider_delivery_return_stops(
      id,return_case_id,pickup_id,order_id,shop_id,return_sequence,refund_amount,status
    ) values(gen_random_uuid(),p_case_id,null,v_order.id,v_order.shop_id,1,v_expected,'pending');
  end if;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select u.id,'มีเคสจัดส่งไม่สำเร็จ',coalesce(v_order.order_number,p_order_id::text)||' · '||v_reason_label,'order',p_order_id
  from public.users u where u.role='admin' and u.status='active';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user.id,'rider_delivery_issue_reported','order',p_order_id,v_reason_label,
    jsonb_build_object('return_case_id',p_case_id,'ticket_id',p_ticket_id,'reason',p_reason,'rider_id',v_rider.id,
      'market_order_id',v_order.market_order_id,'return_stop_count',v_stop_count,'expected_refund_amount',v_expected));

  return p_case_id;
end
$$;

create or replace function public.qg_admin_delivery_return_action(
  p_case_id uuid,p_action text,p_note text default null
)
returns text
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_admin public.users%rowtype;
  v_case public.qg_rider_delivery_returns%rowtype;
  v_order public.orders%rowtype;
  v_rider_user uuid;
  v_action text:=lower(trim(coalesce(p_action,'')));
  v_next text;
begin
  select * into v_admin from public.users
  where auth_user_id=auth.uid() and role='admin' and status='active' limit 1;
  if v_admin.id is null then raise exception 'active admin required'; end if;
  if char_length(trim(coalesce(p_note,'')))>2000 then raise exception 'note too long'; end if;

  select * into v_case from public.qg_rider_delivery_returns where id=p_case_id for update;
  if not found then raise exception 'return case unavailable'; end if;
  select * into v_order from public.orders where id=v_case.order_id for update;
  select user_id into v_rider_user from public.rider_profiles where id=v_case.rider_id;

  if v_action='return_to_shop' then
    if v_case.status='return_required' then return 'return_required'; end if;
    if v_case.status<>'reported' then raise exception 'case is not waiting for return decision'; end if;
    if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;
    v_next:='return_required';
    update public.qg_rider_delivery_returns
    set status=v_next,admin_id=v_admin.id,admin_note=nullif(trim(coalesce(p_note,'')),''),
        return_required_at=coalesce(return_required_at,now()),updated_at=now()
    where id=p_case_id;
    update public.qg_support_tickets
    set status='IN_REVIEW',admin_id=v_admin.id,
        admin_note=coalesce(nullif(trim(coalesce(p_note,'')),''),'ให้ Rider คืนสินค้าไปยังร้าน'),updated_at=now()
    where id=v_case.ticket_id;
    insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
    select v_case.ticket_id,v_admin.id,t.status,'IN_REVIEW',
           coalesce(nullif(trim(coalesce(p_note,'')),''),'return to shop required')
    from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'IN_REVIEW';
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_rider_user,'ให้นำสินค้าคืนร้าน',
      coalesce(v_order.order_number,v_order.id::text)||' · คืนสินค้า '||v_case.return_stop_count||' จุด','order',v_order.id);

  elsif v_action='resolve_no_return' then
    if v_case.status='resolved_no_return' then return 'resolved_no_return'; end if;
    if v_case.status not in ('reported','return_required') then raise exception 'case cannot be resumed'; end if;
    if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;
    v_next:='resolved_no_return';
    update public.qg_rider_delivery_returns
    set status=v_next,admin_id=v_admin.id,admin_note=nullif(trim(coalesce(p_note,'')),''),
        resolved_at=now(),updated_at=now()
    where id=p_case_id;
    update public.qg_rider_delivery_return_stops
    set status='cancelled',updated_at=now()
    where return_case_id=p_case_id and status in ('pending','returning');
    update public.qg_support_tickets
    set status='RESOLVED',admin_id=v_admin.id,
        admin_note=coalesce(nullif(trim(coalesce(p_note,'')),''),'ให้ Rider ดำเนินการส่งต่อ'),updated_at=now()
    where id=v_case.ticket_id;
    insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
    select v_case.ticket_id,v_admin.id,t.status,'RESOLVED',
           coalesce(nullif(trim(coalesce(p_note,'')),''),'continue delivery')
    from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'RESOLVED';
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_rider_user,'ดำเนินการส่งต่อได้',
      coalesce(v_order.order_number,v_order.id::text)||' · ฝ่ายดูแลให้ดำเนินการจัดส่งต่อ','order',v_order.id);

  elsif v_action='cancel_case' then
    if v_case.status in ('returned','resolved_no_return','cancelled') then return v_case.status; end if;
    v_next:='cancelled';
    update public.qg_rider_delivery_returns
    set status=v_next,admin_id=v_admin.id,admin_note=nullif(trim(coalesce(p_note,'')),''),
        resolved_at=now(),updated_at=now()
    where id=p_case_id;
    update public.qg_rider_delivery_return_stops
    set status='cancelled',updated_at=now()
    where return_case_id=p_case_id and status in ('pending','returning');
    update public.qg_support_tickets
    set status='CLOSED',admin_id=v_admin.id,admin_note=nullif(trim(coalesce(p_note,'')),''),
        updated_at=now()
    where id=v_case.ticket_id;
    insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
    select v_case.ticket_id,v_admin.id,t.status,'CLOSED',
           coalesce(nullif(trim(coalesce(p_note,'')),''),'return case closed')
    from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'CLOSED';
  else
    raise exception 'invalid return action';
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_admin.id,'admin_delivery_return_action','delivery_return',p_case_id,v_action,
    jsonb_build_object('order_id',v_case.order_id,'market_order_id',v_case.market_order_id,'from',v_case.status,'to',v_next));
  return v_next;
end
$$;

create or replace function public.qg_rider_active_delivery_return_v2()
returns table(
  case_id uuid,order_id uuid,order_number text,market_order_id uuid,status text,reason text,admin_note text,
  expected_refund_amount numeric,return_stop_count integer,returned_stop_count integer,remaining_stop_count integer,
  stop_id uuid,stop_order_id uuid,stop_sequence integer,stop_refund_amount numeric,
  shop_id uuid,shop_name text,shop_address text,shop_latitude double precision,shop_longitude double precision,
  ticket_id uuid,reported_at timestamptz,return_required_at timestamptz,started_return_at timestamptz
)
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare v_rider uuid;
begin
  select r.id into v_rider
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
  limit 1;
  if v_rider is null then raise exception 'active rider required'; end if;

  return query
  select c.id,c.order_id,o.order_number,c.market_order_id,c.status,c.reason,c.admin_note,
         c.expected_refund_amount,c.return_stop_count,
         (select count(*)::int from public.qg_rider_delivery_return_stops d where d.return_case_id=c.id and d.status='returned'),
         (select count(*)::int from public.qg_rider_delivery_return_stops d where d.return_case_id=c.id and d.status in ('pending','returning')),
         st.id,st.order_id,st.return_sequence,st.refund_amount,st.shop_id,
         sh.shop_name,sh.address,sh.latitude,sh.longitude,c.ticket_id,c.reported_at,c.return_required_at,c.started_return_at
  from public.qg_rider_delivery_returns c
  join public.orders o on o.id=c.order_id
  left join lateral (
    select s.* from public.qg_rider_delivery_return_stops s
    where s.return_case_id=c.id and s.status in ('pending','returning')
    order by case when s.status='returning' then 0 else 1 end,s.return_sequence
    limit 1
  ) st on true
  left join public.shop_profiles sh on sh.id=st.shop_id
  where c.rider_id=v_rider and c.status in ('reported','return_required','returning')
  order by c.updated_at desc
  limit 1;
end
$$;

create or replace function public.qg_rider_start_delivery_return(p_case_id uuid)
returns text
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider uuid;
  v_case public.qg_rider_delivery_returns%rowtype;
  v_order public.orders%rowtype;
  v_stop public.qg_rider_delivery_return_stops%rowtype;
  v_shop_user uuid;
begin
  select r.id into v_rider
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
  limit 1;
  if v_rider is null then raise exception 'active rider required'; end if;

  select * into v_case from public.qg_rider_delivery_returns
  where id=p_case_id and rider_id=v_rider for update;
  if not found then raise exception 'return case unavailable'; end if;
  if v_case.status='returning' then return 'returning'; end if;
  if v_case.status<>'return_required' then raise exception 'return is not approved'; end if;

  select * into v_order from public.orders where id=v_case.order_id for update;
  if v_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;

  select * into v_stop from public.qg_rider_delivery_return_stops
  where return_case_id=p_case_id and status='pending'
  order by return_sequence limit 1 for update;
  if v_stop.id is null then raise exception 'return stop unavailable'; end if;

  update public.qg_rider_delivery_returns
  set status='returning',started_return_at=coalesce(started_return_at,now()),updated_at=now()
  where id=p_case_id;
  update public.qg_rider_delivery_return_stops
  set status='returning',started_at=coalesce(started_at,now()),updated_at=now()
  where id=v_stop.id;

  select user_id into v_shop_user from public.shop_profiles where id=v_stop.shop_id;
  if v_shop_user is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_shop_user,'Rider กำลังกลับมาที่ร้าน',
      coalesce(v_order.order_number,v_order.id::text)||' · เตรียมรับสินค้าคืนและคืนเงินสด '||
      to_char(v_stop.refund_amount,'FM999999990.00')||' บาท','order',v_stop.order_id);
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  select u.id,'rider_delivery_return_started','delivery_return',p_case_id,'returning',
         jsonb_build_object('order_id',v_case.order_id,'market_order_id',v_case.market_order_id,'rider_id',v_rider,
           'stop_id',v_stop.id,'stop_order_id',v_stop.order_id,'return_sequence',v_stop.return_sequence)
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where r.id=v_rider;

  return 'returning';
end
$$;

create or replace function public.qg_rider_complete_delivery_return(p_case_id uuid,p_proof_path text)
returns text
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider public.rider_profiles%rowtype;
  v_user public.users%rowtype;
  v_case public.qg_rider_delivery_returns%rowtype;
  v_case_order public.orders%rowtype;
  v_stop public.qg_rider_delivery_return_stops%rowtype;
  v_next public.qg_rider_delivery_return_stops%rowtype;
  v_shop_user uuid;
  v_customer uuid;
  v_clean_note text;
  v_total_refund numeric;
begin
  select r.* into v_rider
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
  limit 1;
  if v_rider.id is null then raise exception 'active rider required'; end if;
  select * into v_user from public.users where id=v_rider.user_id;

  if p_proof_path is null
     or p_proof_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
     or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_proof_path)
  then raise exception 'valid return proof required'; end if;

  select * into v_case from public.qg_rider_delivery_returns
  where id=p_case_id and rider_id=v_rider.id for update;
  if not found then raise exception 'return case unavailable'; end if;
  if v_case.status='returned' then return 'returned'; end if;
  if v_case.status<>'returning' then raise exception 'return is not active'; end if;

  select * into v_case_order from public.orders where id=v_case.order_id for update;
  if v_case_order.status<>'in_progress' then raise exception 'order is no longer in delivery'; end if;

  select * into v_stop from public.qg_rider_delivery_return_stops
  where return_case_id=p_case_id and status='returning'
  order by return_sequence limit 1 for update;
  if v_stop.id is null then raise exception 'current return stop unavailable'; end if;

  insert into public.qg_rider_cash_return_refunds(id,return_case_id,order_id,rider_id,shop_id,amount)
  values(gen_random_uuid(),v_case.id,v_stop.order_id,v_rider.id,v_stop.shop_id,v_stop.refund_amount)
  on conflict(order_id) do nothing;

  update public.qg_rider_delivery_return_stops
  set status='returned',proof_path=p_proof_path,returned_at=now(),updated_at=now()
  where id=v_stop.id;

  select user_id into v_shop_user from public.shop_profiles where id=v_stop.shop_id;
  if v_shop_user is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_shop_user,'รับสินค้าคืนเรียบร้อย',
      coalesce(v_case_order.order_number,v_case_order.id::text)||' · บันทึกเงินคืน Rider '||
      to_char(v_stop.refund_amount,'FM999999990.00')||' บาทแล้ว','order',v_stop.order_id);
  end if;

  select * into v_next from public.qg_rider_delivery_return_stops
  where return_case_id=p_case_id and status='pending'
  order by return_sequence limit 1 for update;

  if v_next.id is not null then
    update public.qg_rider_delivery_return_stops
    set status='returning',started_at=coalesce(started_at,now()),updated_at=now()
    where id=v_next.id;
    update public.qg_rider_delivery_returns
    set return_proof_path=p_proof_path,updated_at=now()
    where id=p_case_id;

    select user_id into v_shop_user from public.shop_profiles where id=v_next.shop_id;
    if v_shop_user is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_shop_user,'Rider กำลังนำสินค้าคืนร้าน',
        coalesce(v_case_order.order_number,v_case_order.id::text)||' · เตรียมคืนเงินสด '||
        to_char(v_next.refund_amount,'FM999999990.00')||' บาทให้ Rider','order',v_next.order_id);
    end if;

    insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
    values(v_user.id,'rider_delivery_return_stop_completed','delivery_return',p_case_id,'next_stop',
      jsonb_build_object('completed_stop_id',v_stop.id,'completed_order_id',v_stop.order_id,
        'refund_amount',v_stop.refund_amount,'next_stop_id',v_next.id,'next_order_id',v_next.order_id,
        'next_sequence',v_next.return_sequence));
    return 'returning';
  end if;

  update public.qg_rider_delivery_returns
  set status='returned',return_proof_path=p_proof_path,returned_at=now(),resolved_at=now(),updated_at=now()
  where id=p_case_id;
  update public.qg_support_tickets
  set status='RESOLVED',admin_note=coalesce(admin_note,'Rider คืนสินค้าให้ร้านครบทุกจุดเรียบร้อย'),updated_at=now()
  where id=v_case.ticket_id;
  insert into public.qg_ticket_events(ticket_id,actor_id,from_status,to_status,note)
  select v_case.ticket_id,v_user.id,t.status,'RESOLVED','return to sender completed'
  from public.qg_support_tickets t where t.id=v_case.ticket_id and t.status<>'RESOLVED';

  perform set_config('queuego.v22_transition','rpc',true);

  if v_case.market_order_id is not null then
    update public.orders o
    set status='cancelled',
        note='__QT_ORDER_STATUS__=cancelled'||E'\n'||'__QT_RETURNED_TO_SHOP__='||p_case_id::text||E'\n'||
             regexp_replace(coalesce(o.note,''),E'^(__QT_ORDER_STATUS__=[^\\n]*\\n?)+','','g'),
        cancelled_at=coalesce(o.cancelled_at,now()),updated_at=now()
    where o.market_order_id=v_case.market_order_id and o.status<>'cancelled';
  else
    v_clean_note:=regexp_replace(coalesce(v_case_order.note,''),E'^(__QT_ORDER_STATUS__=[^\\n]*\\n?)+','','g');
    update public.orders
    set status='cancelled',
        note='__QT_ORDER_STATUS__=cancelled'||E'\n'||'__QT_RETURNED_TO_SHOP__='||p_case_id::text||E'\n'||v_clean_note,
        cancelled_at=coalesce(cancelled_at,now()),updated_at=now()
    where id=v_case_order.id;
  end if;

  select customer_id into v_customer from public.orders where id=v_case.order_id;
  if v_customer is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_customer,'ออเดอร์ถูกยุติหลังจัดส่งไม่สำเร็จ',
      coalesce(v_case_order.order_number,v_case_order.id::text)||' · สินค้าถูกส่งคืนร้านครบแล้ว','order',v_case_order.id);
  end if;

  select coalesce(sum(amount),0) into v_total_refund
  from public.qg_rider_cash_return_refunds where return_case_id=p_case_id;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user.id,'rider_delivery_return_completed','delivery_return',p_case_id,'returned',
    jsonb_build_object('order_id',v_case_order.id,'market_order_id',v_case.market_order_id,'rider_id',v_rider.id,
      'return_stop_count',v_case.return_stop_count,'total_refund_amount',v_total_refund,'last_proof_path',p_proof_path));

  return 'returned';
end
$$;

create or replace function public.qg_admin_delivery_return_cases_v2()
returns table(
  case_id uuid,order_id uuid,order_number text,market_order_id uuid,ticket_id uuid,rider_id uuid,rider_name text,
  customer_id uuid,reason text,status text,admin_note text,expected_refund_amount numeric,return_stop_count integer,
  returned_stop_count integer,remaining_stop_count integer,current_shop_name text,current_refund_amount numeric,
  reported_at timestamptz,return_required_at timestamptz,started_return_at timestamptz,returned_at timestamptz,updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare v_admin uuid;
begin
  select id into v_admin from public.users
  where auth_user_id=auth.uid() and role='admin' and status='active'
  limit 1;
  if v_admin is null then raise exception 'active admin required'; end if;

  return query
  select c.id,c.order_id,o.order_number,c.market_order_id,c.ticket_id,c.rider_id,r.rider_name,c.customer_id,
         c.reason,c.status,c.admin_note,c.expected_refund_amount,c.return_stop_count,
         (select count(*)::int from public.qg_rider_delivery_return_stops d where d.return_case_id=c.id and d.status='returned'),
         (select count(*)::int from public.qg_rider_delivery_return_stops d where d.return_case_id=c.id and d.status in ('pending','returning')),
         sh.shop_name,st.refund_amount,c.reported_at,c.return_required_at,c.started_return_at,c.returned_at,c.updated_at
  from public.qg_rider_delivery_returns c
  join public.orders o on o.id=c.order_id
  join public.rider_profiles r on r.id=c.rider_id
  left join lateral (
    select s.* from public.qg_rider_delivery_return_stops s
    where s.return_case_id=c.id and s.status in ('pending','returning')
    order by case when s.status='returning' then 0 else 1 end,s.return_sequence
    limit 1
  ) st on true
  left join public.shop_profiles sh on sh.id=st.shop_id
  order by case c.status when 'reported' then 0 when 'return_required' then 1 when 'returning' then 2 else 3 end,
           c.updated_at desc
  limit 200;
end
$$;

revoke all on function public.qg_rider_active_delivery_return_v2() from public,anon;
revoke all on function public.qg_admin_delivery_return_cases_v2() from public,anon;
grant execute on function public.qg_rider_active_delivery_return_v2() to authenticated;
grant execute on function public.qg_admin_delivery_return_cases_v2() to authenticated;

revoke all on function public.qg_rider_report_delivery_issue(uuid,uuid,uuid,text,text) from public,anon;
revoke all on function public.qg_admin_delivery_return_action(uuid,text,text) from public,anon;
revoke all on function public.qg_rider_start_delivery_return(uuid) from public,anon;
revoke all on function public.qg_rider_complete_delivery_return(uuid,text) from public,anon;
grant execute on function public.qg_rider_report_delivery_issue(uuid,uuid,uuid,text,text) to authenticated;
grant execute on function public.qg_admin_delivery_return_action(uuid,text,text) to authenticated;
grant execute on function public.qg_rider_start_delivery_return(uuid) to authenticated;
grant execute on function public.qg_rider_complete_delivery_return(uuid,text) to authenticated;
