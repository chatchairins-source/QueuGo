-- POS cash shifts belong to the shop POS and reuse the existing orders/payments ledger.
begin;

create table public.pos_register_shifts (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shop_profiles(id),
  cashier_user_id uuid not null references auth.users(id),
  cashier_name text not null check (length(trim(cashier_name)) between 1 and 100),
  opening_float numeric(12,2) not null default 0 check (opening_float >= 0),
  expected_cash numeric(12,2),
  counted_cash numeric(12,2),
  variance numeric(12,2),
  opening_note text not null default '',
  closing_note text not null default '',
  opened_at timestamptz not null default now(),
  closed_at timestamptz,
  constraint pos_register_shift_close_fields check (
    (closed_at is null and expected_cash is null and counted_cash is null and variance is null)
    or (closed_at is not null and expected_cash is not null and counted_cash is not null and variance = counted_cash - expected_cash)
  )
);
create unique index pos_one_open_register_shift_per_cashier
  on public.pos_register_shifts(shop_id,cashier_user_id) where closed_at is null;
create index pos_register_shifts_shop_opened_idx on public.pos_register_shifts(shop_id,opened_at desc);
alter table public.pos_register_shifts enable row level security;
create policy pos_register_shifts_read on public.pos_register_shifts for select to authenticated
  using (shop_id=public.pos_my_shop() and (public.pos_is_owner() or cashier_user_id=(select auth.uid())));
grant select on public.pos_register_shifts to authenticated;

alter table public.orders add column pos_register_shift_id uuid references public.pos_register_shifts(id);
create index pos_orders_register_shift_idx on public.orders(pos_register_shift_id) where pos_register_shift_id is not null;

-- Shop-side cash refunds remain in the POS and are recorded as till outflows in the shift
-- when the refund is physically handed back. This is not an Admin/provider settlement flow.
create table public.pos_register_cash_events (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shop_profiles(id),
  shift_id uuid not null references public.pos_register_shifts(id),
  order_id uuid not null references public.orders(id),
  actor_id uuid not null references auth.users(id),
  amount numeric(12,2) not null check(amount<0),
  reason text not null check(length(trim(reason)) between 5 and 500),
  created_at timestamptz not null default now(),
  unique(order_id)
);
create index pos_register_cash_events_shift_idx on public.pos_register_cash_events(shift_id,created_at);
alter table public.pos_register_cash_events enable row level security;
create policy pos_register_cash_events_read on public.pos_register_cash_events for select to authenticated
  using (shop_id=public.pos_my_shop() and (public.pos_is_owner() or actor_id=(select auth.uid())));
grant select on public.pos_register_cash_events to authenticated;

create or replace function public.pos_current_shift()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_shift public.pos_register_shifts%rowtype; v_name text;
begin
  if not public.pos_allowed('close_bill') then raise exception 'cash register permission denied'; end if;
  select coalesce(s.display_name,'เจ้าของร้าน') into v_name from (select auth.uid() as id) a
    left join public.pos_staff s on s.user_id=a.id and s.shop_id=public.pos_my_shop() and s.active;
  select * into v_shift from public.pos_register_shifts
    where shop_id=public.pos_my_shop() and cashier_user_id=(select auth.uid()) and closed_at is null;
  if not found then return jsonb_build_object('open',false,'cashier_name',coalesce(v_name,'เจ้าของร้าน')); end if;
  return to_jsonb(v_shift)||jsonb_build_object('open',true);
end $$;

create or replace function public.pos_register_shift_history(p_limit integer default 20)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_rows jsonb;
begin
  if not public.pos_allowed('view_sales') and not public.pos_allowed('close_bill') then raise exception 'cash register history permission denied'; end if;
  select coalesce(jsonb_agg(to_jsonb(x) order by x.opened_at desc),'[]'::jsonb) into v_rows from (
    select id,cashier_name,opening_float,expected_cash,counted_cash,variance,opening_note,closing_note,opened_at,closed_at
    from public.pos_register_shifts where shop_id=public.pos_my_shop()
      and (public.pos_is_owner() or cashier_user_id=(select auth.uid()))
    order by opened_at desc limit least(greatest(coalesce(p_limit,20),1),50)
  ) x;
  return v_rows;
end $$;

create or replace function public.pos_open_shift(p_opening_float numeric default 0,p_note text default '')
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop uuid; v_name text; v_shift public.pos_register_shifts%rowtype;
begin
  if not public.pos_allowed('close_bill') then raise exception 'cash register permission denied'; end if;
  if p_opening_float is null or p_opening_float<0 or p_opening_float>999999999 then raise exception 'invalid opening cash'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'opening note too long'; end if;
  v_shop:=public.pos_my_shop();
  select coalesce(s.display_name,'เจ้าของร้าน') into v_name from (select auth.uid() as id) a
    left join public.pos_staff s on s.user_id=a.id and s.shop_id=v_shop and s.active;
  select * into v_shift from public.pos_register_shifts where shop_id=v_shop
    and cashier_user_id=(select auth.uid()) and closed_at is null for update;
  if found then
    if v_shift.opening_float=round(p_opening_float,2) and v_shift.opening_note=trim(coalesce(p_note,''))
    then return to_jsonb(v_shift)||jsonb_build_object('open',true); end if;
    raise exception 'มีการเปิดกะอยู่แล้ว กรุณาปิดกะเดิมก่อน';
  end if;
  insert into public.pos_register_shifts(shop_id,cashier_user_id,cashier_name,opening_float,opening_note)
    values(v_shop,(select auth.uid()),coalesce(v_name,'เจ้าของร้าน'),round(p_opening_float,2),trim(coalesce(p_note,'')))
    returning * into v_shift;
  return to_jsonb(v_shift)||jsonb_build_object('open',true);
exception when unique_violation then raise exception 'มีการเปิดกะอยู่แล้ว กรุณาปิดกะเดิมก่อน';
end $$;

create or replace function public.pos_close_shift(p_counted_cash numeric,p_note text default '')
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shift public.pos_register_shifts%rowtype; v_expected numeric(12,2);
begin
  if not public.pos_allowed('close_bill') then raise exception 'cash register permission denied'; end if;
  if p_counted_cash is null or p_counted_cash<0 or p_counted_cash>999999999 then raise exception 'invalid counted cash'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'closing note too long'; end if;
  select * into v_shift from public.pos_register_shifts where shop_id=public.pos_my_shop()
    and cashier_user_id=(select auth.uid()) and closed_at is null for update;
  if not found then
    select * into v_shift from public.pos_register_shifts where shop_id=public.pos_my_shop()
      and cashier_user_id=(select auth.uid()) order by opened_at desc limit 1;
    if found and v_shift.closed_at is not null and v_shift.counted_cash=round(p_counted_cash,2)
      and v_shift.closing_note=trim(coalesce(p_note,'')) then
      return to_jsonb(v_shift)||jsonb_build_object('open',false);
    end if;
    raise exception 'ไม่พบกะที่เปิดอยู่';
  end if;
  select round(v_shift.opening_float+
    coalesce((select sum(p.amount) from public.payments p join public.orders o on o.id=p.order_id
      where o.pos_register_shift_id=v_shift.id and o.sales_channel='POS'
        and p.payment_method='cash' and p.status='paid'),0)
    +coalesce((select sum(e.amount) from public.pos_register_cash_events e where e.shift_id=v_shift.id),0),2)
    into v_expected
    ;
  update public.pos_register_shifts set expected_cash=v_expected,counted_cash=round(p_counted_cash,2),
    variance=round(p_counted_cash,2)-v_expected,closing_note=trim(coalesce(p_note,'')),closed_at=now()
    where id=v_shift.id returning * into v_shift;
  return to_jsonb(v_shift)||jsonb_build_object('open',false);
end $$;

create or replace function public.pos_refund_bill(p_order uuid,p_reason text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;v_shift public.pos_register_shifts%rowtype;
begin
  if not public.pos_allowed('refund') then raise exception 'refund permission denied'; end if;
  if length(trim(coalesce(p_reason,'')))<5 or length(p_reason)>500 then raise exception 'refund reason required'; end if;
  select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop()
    and sales_channel='POS' for update;
  if not found or v_order.payment_status<>'PAID' then raise exception 'paid bill required'; end if;
  if v_order.payment_method='cash' then
    select * into v_shift from public.pos_register_shifts where shop_id=v_order.shop_id
      and cashier_user_id=(select auth.uid()) and closed_at is null for update;
    if not found then raise exception 'กรุณาเปิดกะก่อนบันทึกคืนเงินสด'; end if;
    insert into public.pos_register_cash_events(shop_id,shift_id,order_id,actor_id,amount,reason)
      values(v_order.shop_id,v_shift.id,v_order.id,(select auth.uid()),-v_order.total_amount,trim(p_reason));
  end if;
  perform set_config('queuego.pos_rpc','on',true);
  update public.payments set status='refunded',note=trim(p_reason),updated_at=now()
    where order_id=p_order and status='paid';
  update public.orders set payment_status='REFUNDED',status='cancelled',note=trim(p_reason),updated_at=now()
    where id=p_order;
  return (select to_jsonb(o) from public.orders o where o.id=p_order);
end $$;

-- Every payment is attached to the authenticated cashier's current shift. Retry of the same
-- acknowledged payment remains idempotent and returns the original bill.
create or replace function public.pos_take_payment(p_order uuid,p_method text,p_cash_received numeric default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype; v_shift public.pos_register_shifts%rowtype; v_result jsonb;
begin
  if not public.pos_allowed('close_bill') then raise exception 'cashier permission denied'; end if;
  select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop()
    and sales_channel='POS' for update;
  if not found then raise exception 'bill not found'; end if;
  if v_order.payment_status='PAID' and v_order.bill_status='CLOSED'
     and v_order.payment_method=p_method and v_order.cash_tendered is not distinct from p_cash_received
     and exists(select 1 from public.payments where order_id=p_order and status='paid' and payment_method=p_method)
  then return to_jsonb(v_order); end if;
  if v_order.payment_status<>'UNPAID' or v_order.status='cancelled' or v_order.kitchen_status not in ('READY','SERVED') then raise exception 'bill not ready for payment'; end if;
  if p_method not in ('cash','bank_transfer','promptpay','card','other') then raise exception 'invalid payment method'; end if;
  if p_method='cash' and (p_cash_received is null or p_cash_received<v_order.total_amount) then raise exception 'cash received is less than total'; end if;
  if p_method<>'cash' and p_cash_received is not null then raise exception 'cash received only for cash payment'; end if;
  select * into v_shift from public.pos_register_shifts where shop_id=v_order.shop_id
    and cashier_user_id=(select auth.uid()) and closed_at is null for update;
  if not found then raise exception 'กรุณาเปิดกะรับเงินก่อนคิดเงิน'; end if;
  perform set_config('queuego.pos_rpc','on',true);
  insert into public.payments(order_id,amount,payment_method,status,paid_at)
    values(p_order,v_order.total_amount,p_method,'paid',now());
  update public.orders set payment_status='PAID',payment_method=p_method,bill_status='CLOSED',
    cash_tendered=p_cash_received,cash_change=case when p_method='cash' then p_cash_received-v_order.total_amount else null end,
    paid_at=now(),status='completed',pos_register_shift_id=v_shift.id,updated_at=now() where id=p_order;
  select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
  return v_result;
end $$;

revoke all on function public.pos_current_shift(),public.pos_register_shift_history(integer),public.pos_open_shift(numeric,text),public.pos_close_shift(numeric,text),public.pos_take_payment(uuid,text,numeric),public.pos_refund_bill(uuid,text) from public,anon;
grant execute on function public.pos_current_shift(),public.pos_register_shift_history(integer),public.pos_open_shift(numeric,text),public.pos_close_shift(numeric,text),public.pos_take_payment(uuid,text,numeric),public.pos_refund_bill(uuid,text) to authenticated;

commit;
