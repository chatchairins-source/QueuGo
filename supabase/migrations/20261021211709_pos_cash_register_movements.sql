-- Cash in/out during a shop POS shift. Delivery and QueueGo settlement are excluded.
begin;

alter table public.pos_register_cash_events
  add column event_type text not null default 'refund',
  add column idempotency_key uuid;

alter table public.pos_register_cash_events
  alter column order_id drop not null,
  drop constraint if exists pos_register_cash_events_order_id_key,
  drop constraint if exists pos_register_cash_events_amount_check,
  add constraint pos_register_cash_events_type_check
    check (event_type in ('refund','cash_in','cash_out')),
  add constraint pos_register_cash_events_nonzero_amount_check
    check (amount<>0),
  add constraint pos_register_cash_events_direction_check
    check ((event_type='cash_in' and amount>0) or (event_type in ('refund','cash_out') and amount<0));

create unique index pos_register_cash_events_order_unique
  on public.pos_register_cash_events(order_id) where order_id is not null;
create unique index pos_register_cash_events_request_unique
  on public.pos_register_cash_events(shop_id,actor_id,idempotency_key) where idempotency_key is not null;
create index pos_register_cash_events_register_history_idx
  on public.pos_register_cash_events(shop_id,shift_id,created_at desc);

-- Return the live, shop-scoped till total and adjustment totals with the cashier's shift.
create or replace function public.pos_current_shift()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare
  v_shift public.pos_register_shifts%rowtype;
  v_name text;
  v_expected numeric(12,2);
  v_cash_in numeric(12,2);
  v_cash_out numeric(12,2);
begin
  if not public.pos_allowed('close_bill') then raise exception 'cash register permission denied'; end if;
  select coalesce(s.display_name,'เจ้าของร้าน') into v_name from (select auth.uid() as id) a
    left join public.pos_staff s on s.user_id=a.id and s.shop_id=public.pos_my_shop() and s.active;
  select * into v_shift from public.pos_register_shifts
    where shop_id=public.pos_my_shop() and cashier_user_id=(select auth.uid()) and closed_at is null;
  if not found then return jsonb_build_object('open',false,'cashier_name',coalesce(v_name,'เจ้าของร้าน')); end if;
  select round(v_shift.opening_float
      +coalesce((select sum(p.amount) from public.payments p join public.orders o on o.id=p.order_id
        where o.pos_register_shift_id=v_shift.id and o.sales_channel='POS'
          and p.payment_method='cash' and p.status='paid'),0)
      +coalesce((select sum(e.amount) from public.pos_register_cash_events e where e.shift_id=v_shift.id),0),2),
    coalesce((select sum(e.amount) from public.pos_register_cash_events e where e.shift_id=v_shift.id and e.event_type='cash_in'),0),
    coalesce((select -sum(e.amount) from public.pos_register_cash_events e where e.shift_id=v_shift.id and e.event_type='cash_out'),0)
    into v_expected,v_cash_in,v_cash_out;
  return to_jsonb(v_shift)||jsonb_build_object('open',true,'expected_cash',v_expected,
    'cash_in_total',v_cash_in,'cash_out_total',v_cash_out);
end $$;

create or replace function public.pos_record_cash_movement(
  p_type text,
  p_amount numeric,
  p_reason text,
  p_request uuid
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_shop uuid;
  v_shift public.pos_register_shifts%rowtype;
  v_event public.pos_register_cash_events%rowtype;
  v_amount numeric(12,2);
begin
  if not public.pos_allowed('close_bill') then raise exception 'cash register permission denied'; end if;
  if p_type is null or p_type not in ('cash_in','cash_out') then raise exception 'invalid cash movement type'; end if;
  if p_amount is null or p_amount<=0 or p_amount>999999999 then raise exception 'invalid cash movement amount'; end if;
  if length(trim(coalesce(p_reason,'')))<5 or length(p_reason)>500 then raise exception 'cash movement reason required'; end if;
  if p_request is null then raise exception 'cash movement request id required'; end if;

  v_shop:=public.pos_my_shop();
  v_amount:=round(p_amount,2);
  if v_amount<=0 then raise exception 'cash movement amount must be at least 0.01'; end if;
  -- Serialize the same actor/shop/request key before checking the receipt. This closes
  -- the missing-row race between simultaneous retries; hash collisions only serialize
  -- unrelated requests and do not change their data or authorization scope.
  perform pg_advisory_xact_lock(hashtextextended(
    v_shop::text||':'||(select auth.uid())::text||':'||p_request::text,0));
  select * into v_event from public.pos_register_cash_events
    where shop_id=v_shop and actor_id=(select auth.uid()) and idempotency_key=p_request for update;
  if found then
    if v_event.event_type=p_type and abs(v_event.amount)=v_amount and v_event.reason=trim(p_reason)
      and v_event.actor_id=(select auth.uid())
    then return to_jsonb(v_event); end if;
    raise exception 'cash movement request was already used';
  end if;

  select * into v_shift from public.pos_register_shifts
    where shop_id=v_shop and cashier_user_id=(select auth.uid()) and closed_at is null for update;
  if not found then raise exception 'กรุณาเปิดกะก่อนบันทึกเงินเข้า/ออก'; end if;

  begin
    insert into public.pos_register_cash_events(shop_id,shift_id,order_id,actor_id,amount,reason,event_type,idempotency_key)
      values(v_shop,v_shift.id,null,(select auth.uid()),
        case when p_type='cash_in' then v_amount else -v_amount end,trim(p_reason),p_type,p_request)
      returning * into v_event;
  exception when unique_violation then
    -- A concurrent request with the same idempotency key may win after the pre-check.
    -- Return its receipt only when the payload matches; never turn a reused key into success.
    select * into v_event from public.pos_register_cash_events
      where shop_id=v_shop and actor_id=(select auth.uid()) and idempotency_key=p_request
      for update;
    if not found then raise; end if;
    if v_event.event_type=p_type and abs(v_event.amount)=v_amount and v_event.reason=trim(p_reason)
    then return to_jsonb(v_event); end if;
    raise exception 'cash movement request was already used';
  end;
  return to_jsonb(v_event);
end $$;

revoke all on function public.pos_record_cash_movement(text,numeric,text,uuid) from public,anon;
grant execute on function public.pos_record_cash_movement(text,numeric,text,uuid) to authenticated;

commit;
