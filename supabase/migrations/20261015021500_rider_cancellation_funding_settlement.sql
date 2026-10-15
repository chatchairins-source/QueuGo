-- Rider cancellation funding settlement rail.
-- A reserved obligation is NOT a payout. Only an active Admin may mark it
-- settled after an external payment/reference actually exists.

alter table qg_private.rider_cancellation_funding
  add column if not exists settled_at timestamptz,
  add column if not exists settled_by uuid references public.users(id) on delete restrict,
  add column if not exists settlement_reference text,
  add column if not exists settlement_note text;

create or replace function qg_private.guard_cancellation_funding_settlement()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
begin
  if old.status='settled' and (
    new.status is distinct from old.status
    or new.amount is distinct from old.amount
    or new.replacement_target_amount is distinct from old.replacement_target_amount
    or new.protected_route_payout is distinct from old.protected_route_payout
    or new.replacement_order_id is distinct from old.replacement_order_id
    or new.reason is distinct from old.reason
    or new.order_id is distinct from old.order_id
    or new.rider_id is distinct from old.rider_id
    or new.bundle_id is distinct from old.bundle_id
    or new.settled_at is distinct from old.settled_at
    or new.settled_by is distinct from old.settled_by
    or new.settlement_reference is distinct from old.settlement_reference
    or new.settlement_note is distinct from old.settlement_note
  ) then
    raise exception 'settled Rider funding is immutable';
  end if;

  if new.status='settled' and old.status is distinct from new.status then
    if current_setting('queuego.funding_settle',true) is distinct from 'rpc' then
      raise exception 'Rider funding settlement requires authoritative RPC';
    end if;
    if new.amount<=0
       or new.settled_at is null
       or new.settled_by is null
       or length(trim(coalesce(new.settlement_reference,'')))<4 then
      raise exception 'settlement proof is incomplete';
    end if;
  end if;

  return new;
end $$;

revoke all on function qg_private.guard_cancellation_funding_settlement()
from public,anon,authenticated;

drop trigger if exists rider_cancellation_funding_settlement_guard
on qg_private.rider_cancellation_funding;
create trigger rider_cancellation_funding_settlement_guard
before update on qg_private.rider_cancellation_funding
for each row execute function qg_private.guard_cancellation_funding_settlement();

create or replace function public.qg_admin_rider_cancellation_funding_queue(
  p_status text default 'reserved'
) returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_status text:=lower(trim(coalesce(p_status,'reserved')));
  v_rows jsonb;
begin
  if not public.is_active_admin() then raise exception 'admin required'; end if;
  if v_status not in ('reserved','settled','all') then
    raise exception 'invalid funding status';
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'funding_id',f.id,
    'bundle_id',f.bundle_id,
    'order_id',f.order_id,
    'order_number',o.order_number,
    'rider_id',f.rider_id,
    'rider_name',coalesce(u.name,'Rider'),
    'amount',f.amount,
    'status',f.status,
    'replacement_target_amount',f.replacement_target_amount,
    'protected_route_payout',f.protected_route_payout,
    'replacement_order_id',f.replacement_order_id,
    'replacement_order_number',ro.order_number,
    'reason',f.reason,
    'created_at',f.created_at,
    'settled_at',f.settled_at,
    'settlement_reference',f.settlement_reference,
    'settlement_note',f.settlement_note
  ) order by
      case when f.status='reserved' then 0 else 1 end,
      f.created_at asc
  ),'[]'::jsonb)
  into v_rows
  from qg_private.rider_cancellation_funding f
  join public.orders o on o.id=f.order_id
  join public.rider_profiles rp on rp.id=f.rider_id
  join public.users u on u.id=rp.user_id
  left join public.orders ro on ro.id=f.replacement_order_id
  where f.status<>'void'
    and (v_status='all' or f.status=v_status);

  return v_rows;
end $$;

revoke all on function public.qg_admin_rider_cancellation_funding_queue(text)
from public,anon;
grant execute on function public.qg_admin_rider_cancellation_funding_queue(text)
to authenticated;

create or replace function public.qg_admin_settle_rider_cancellation_funding(
  p_funding_id uuid,
  p_reference text,
  p_note text default null
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_admin uuid;
  f qg_private.rider_cancellation_funding%rowtype;
  v_reference text:=trim(coalesce(p_reference,''));
  v_note text:=nullif(trim(coalesce(p_note,'')),'');
  v_rider_user uuid;
begin
  if not public.is_active_admin() then raise exception 'admin required'; end if;

  select id into v_admin
  from public.users
  where auth_user_id=auth.uid() and role='admin' and status='active'
  limit 1;
  if v_admin is null then raise exception 'active admin required'; end if;
  if p_funding_id is null then raise exception 'funding id required'; end if;
  if length(v_reference)<4 or length(v_reference)>200 then
    raise exception 'payment reference must be 4-200 characters';
  end if;
  if v_note is not null and length(v_note)>1000 then
    raise exception 'settlement note too long';
  end if;

  select * into f
  from qg_private.rider_cancellation_funding
  where id=p_funding_id
  for update;
  if not found then raise exception 'funding obligation unavailable'; end if;

  if f.status='settled' then
    if f.settlement_reference=v_reference then
      return jsonb_build_object(
        'funding_id',f.id,
        'order_id',f.order_id,
        'amount',f.amount,
        'status',f.status,
        'settled_at',f.settled_at,
        'settlement_reference',f.settlement_reference,
        'replayed',true
      );
    end if;
    raise exception 'funding obligation already settled';
  end if;

  if f.status<>'reserved' then raise exception 'funding obligation is not payable'; end if;
  if f.amount<=0 then
    raise exception 'no QueueGo cash funding is currently due; replacement tracking remains operational only';
  end if;

  perform set_config('queuego.funding_settle','rpc',true);

  update qg_private.rider_cancellation_funding
  set status='settled',
      settled_at=clock_timestamp(),
      settled_by=v_admin,
      settlement_reference=v_reference,
      settlement_note=v_note,
      updated_at=clock_timestamp()
  where id=f.id
  returning * into f;

  insert into public.audit_logs(
    user_id,action,entity_type,entity_id,description,metadata
  ) values(
    v_admin,'rider_cancellation_funding_settle','rider_cancellation_funding',f.id,
    'QueueGo Rider cancellation funding marked settled',
    jsonb_build_object(
      'order_id',f.order_id,
      'rider_id',f.rider_id,
      'amount',f.amount,
      'reference',f.settlement_reference
    )
  );

  select user_id into v_rider_user
  from public.rider_profiles
  where id=f.rider_id;

  if v_rider_user is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(
      v_rider_user,
      'QueueGo ชำระเงินคุ้มครองแล้ว',
      'ชำระเงินคุ้มครองจากการยกเลิก ฿'||trim(to_char(f.amount,'FM999999990.00'))||
        ' · อ้างอิง '||left(f.settlement_reference,80),
      'order',
      f.order_id
    );
  end if;

  return jsonb_build_object(
    'funding_id',f.id,
    'order_id',f.order_id,
    'amount',f.amount,
    'status',f.status,
    'settled_at',f.settled_at,
    'settlement_reference',f.settlement_reference,
    'replayed',false
  );
end $$;

revoke all on function public.qg_admin_settle_rider_cancellation_funding(uuid,text,text)
from public,anon;
grant execute on function public.qg_admin_settle_rider_cancellation_funding(uuid,text,text)
to authenticated;

-- Keep the existing Rider cash ledger as the single Rider read model.
-- "settled cash" is zero until Admin has a real external payment reference.
create or replace function public.qg_rider_cash_ledger(p_from date, p_to date)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider uuid;
  v_rows jsonb;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';

  if v_rider is null then raise exception 'active rider required'; end if;
  if p_from is null or p_to is null or p_to<p_from or p_to>p_from+31 then
    raise exception 'invalid date range';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'order_id',o.id,
        'order_number',o.order_number,
        'status',o.status,
        'customer_id',o.customer_id,
        'shop_id',o.shop_id,
        'rider_id',o.rider_id,
        'subtotal',o.subtotal,
        'delivery_fee',coalesce(o.rider_delivery_payout,o.delivery_fee),
        'customer_delivery_fee',o.delivery_fee,
        'gp_amount',o.gp_amount,
        'total',o.total_amount,
        'cash_paid_merchant',a.amount,
        'cash_paid_at',a.paid_at,
        'merchant_confirmed',mr.amount,
        'merchant_confirmed_at',mr.received_at,
        'cash_refunded_merchant',coalesce(rr.amount,0),
        'cash_refunded_at',rr.refunded_at,
        'cash_collected_customer',case when p.status='paid' then p.amount else 0 end,
        'customer_paid_at',p.paid_at,
        'payment_id',p.id,
        'payment_status',p.status,
        'proof_verified_at',d.verified_at,
        'platform_cancellation_funding',coalesce(cf.amount,0),
        'platform_cancellation_funding_cash',
          case when cf.status='settled' then coalesce(cf.amount,0) else 0 end,
        'platform_cancellation_replacement_target',coalesce(cf.replacement_target_amount,0),
        'platform_protected_route_payout',coalesce(cf.protected_route_payout,0),
        'platform_replacement_order_id',cf.replacement_order_id,
        'platform_cancellation_funding_status',cf.status,
        'platform_cancellation_funding_settled_at',cf.settled_at,
        'platform_cancellation_funding_reference',cf.settlement_reference,
        'created_at',o.created_at
      )
      order by o.created_at desc
    ),
    '[]'::jsonb
  )
  into v_rows
  from public.orders o
  left join public.rider_cash_advances a on a.order_id=o.id
  left join public.merchant_cash_receipts mr on mr.order_id=o.id
  left join public.qg_rider_cash_return_refunds rr on rr.order_id=o.id
  left join public.payments p on p.order_id=o.id and p.payment_method='cash'
  left join public.qg_delivery_proofs d on d.order_id=o.id
  left join qg_private.rider_cancellation_funding cf
    on cf.order_id=o.id and cf.rider_id=o.rider_id and cf.status<>'void'
  where o.rider_id=v_rider
    and o.order_type='shopping'
    and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to;

  return v_rows;
end $$;

revoke all on function public.qg_rider_cash_ledger(date,date) from public,anon;
grant execute on function public.qg_rider_cash_ledger(date,date) to authenticated;

create or replace function public.qg_rider_period_summary(p_days integer)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_rider uuid;
  v_start timestamptz;
  v_jobs int;
  v_income numeric;
  v_seconds numeric;
  v_pending numeric;
  v_settled numeric;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';

  if v_rider is null then raise exception 'active rider required'; end if;
  if p_days not in (1,7,30) then raise exception 'invalid range'; end if;

  v_start:=((now() at time zone 'Asia/Bangkok')::date-(p_days-1))::timestamp
    at time zone 'Asia/Bangkok';

  select
    count(*),
    coalesce(sum(coalesce(o.rider_delivery_payout,o.delivery_fee)),0),
    coalesce(sum(case when cf.status='reserved' then cf.amount else 0 end),0),
    coalesce(sum(case when cf.status='settled' then cf.amount else 0 end),0)
  into v_jobs,v_income,v_pending,v_settled
  from public.orders o
  join public.deliveries d on d.order_id=o.id
  left join qg_private.rider_cancellation_funding cf
    on cf.order_id=o.id and cf.rider_id=o.rider_id and cf.status<>'void'
  where o.rider_id=v_rider
    and o.status='completed'
    and d.delivered_at>=v_start;

  with events as (
    select online,occurred_at,
      lead(occurred_at,1,now()) over(order by occurred_at,id) next_at
    from public.qg_rider_presence_events
    where rider_id=v_rider
  )
  select coalesce(sum(
    extract(epoch from least(next_at,now())-greatest(occurred_at,v_start))
  ),0)
  into v_seconds
  from events
  where online and next_at>v_start;

  return jsonb_build_object(
    'days',p_days,
    'jobs',v_jobs,
    'income',v_income,
    'cash_realized_income',greatest(v_income-v_pending,0),
    'pending_platform_funding',v_pending,
    'settled_platform_funding',v_settled,
    'online_hours',round(v_seconds/3600,2),
    'income_per_hour',
      case when v_seconds>0 then round(v_income/(v_seconds/3600),2) else null end,
    'tracked_since',
      (select min(occurred_at) from public.qg_rider_presence_events where rider_id=v_rider)
  );
end $$;

revoke all on function public.qg_rider_period_summary(integer) from public,anon;
grant execute on function public.qg_rider_period_summary(integer) to authenticated;
