-- Retail merchants do not prepare POS goods in a kitchen. Keep existing POS
-- permissions, shift accounting and replay behavior while limiting kitchen
-- transitions to restaurant and cafe profiles.
begin;

create or replace function public.pos_bill_action(p_order uuid,p_action text,p_method text default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
  v_shop uuid;
  v_order public.orders%rowtype;
  v_next text;
  v_permission text;
  v_result jsonb;
  v_batch integer;
  v_kitchen_enabled boolean;
begin
  v_shop:=public.pos_my_shop();
  v_kitchen_enabled:=false;
  select coalesce(lower(coalesce(nullif(trim(sp.public_category),''),nullif(trim(sp.metadata->>'category'),''),'')) in ('food','cafe'),false)
    into v_kitchen_enabled
    from public.shop_profiles sp where sp.id=v_shop;

  select * into v_order from public.orders
  where id=p_order and shop_id=v_shop and sales_channel='POS' for update;
  if not found or v_order.status='cancelled' or v_order.payment_status<>'UNPAID' then
    raise exception 'open bill not found';
  end if;

  if p_action='cancel' then
    v_permission:='cancel_bill';
  elsif not v_kitchen_enabled then
    raise exception 'kitchen is not available for this shop category';
  elsif p_action='send' and v_order.kitchen_status='NEW' and v_order.subtotal>0 then
    v_next:='SENT_TO_KITCHEN';v_permission:='send_kitchen';
  elsif p_action='cook' and v_order.kitchen_status='SENT_TO_KITCHEN' then
    v_next:='COOKING';v_permission:='cook_order';
  elsif p_action='ready' and v_order.kitchen_status in ('SENT_TO_KITCHEN','COOKING') then
    v_next:='READY';v_permission:='ready_order';
  elsif p_action='serve' and v_order.kitchen_status='READY' then
    v_next:='SERVED';v_permission:='serve_order';
  else
    raise exception 'invalid POS transition';
  end if;

  if not public.pos_allowed(v_permission) then raise exception 'permission denied'; end if;
  perform set_config('queuego.pos_rpc','on',true);
  if p_action='cancel' then
    update public.orders set status='cancelled',updated_at=now() where id=p_order;
  else
    select max(pos_batch) into v_batch from public.order_items where order_id=p_order;
    update public.order_items set pos_kitchen_status=v_next where order_id=p_order and pos_batch=v_batch
      and (case p_action when 'send' then pos_kitchen_status='NEW'
        when 'cook' then pos_kitchen_status in ('NEW','SENT_TO_KITCHEN')
        when 'ready' then pos_kitchen_status in ('SENT_TO_KITCHEN','COOKING')
        when 'serve' then pos_kitchen_status='READY' else false end);
    update public.orders set kitchen_status=v_next,updated_at=now() where id=p_order;
  end if;
  select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
  return v_result;
end $$;

create or replace function public.pos_take_payment(p_order uuid,p_method text,p_cash_received numeric default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
  v_order public.orders%rowtype;
  v_shift public.pos_register_shifts%rowtype;
  v_result jsonb;
  v_kitchen_enabled boolean;
begin
  if not public.pos_allowed('close_bill') then raise exception 'cashier permission denied'; end if;
  select * into v_order from public.orders
  where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
  if not found then raise exception 'bill not found'; end if;
  if v_order.payment_status='PAID' and v_order.bill_status='CLOSED'
     and v_order.payment_method=p_method and v_order.cash_tendered is not distinct from p_cash_received
     and exists(select 1 from public.payments where order_id=p_order and status='paid' and payment_method=p_method)
  then return to_jsonb(v_order); end if;

  select coalesce(lower(coalesce(nullif(trim(sp.public_category),''),nullif(trim(sp.metadata->>'category'),''),'')) in ('food','cafe'),false)
    into v_kitchen_enabled from public.shop_profiles sp where sp.id=v_order.shop_id;
  if v_order.payment_status<>'UNPAID' or v_order.status='cancelled'
     or (v_kitchen_enabled and v_order.kitchen_status not in ('READY','SERVED'))
     or (not coalesce(v_kitchen_enabled,false) and v_order.kitchen_status not in ('NEW','SENT_TO_KITCHEN','COOKING','READY','SERVED'))
  then raise exception 'bill not ready for payment'; end if;
  if p_method not in ('cash','bank_transfer','promptpay','card','other') then raise exception 'invalid payment method'; end if;
  if p_method='cash' and (p_cash_received is null or p_cash_received<v_order.total_amount) then
    raise exception 'cash received is less than total';
  end if;
  if p_method<>'cash' and p_cash_received is not null then raise exception 'cash received only for cash payment'; end if;

  select * into v_shift from public.pos_register_shifts where shop_id=v_order.shop_id
    and cashier_user_id=(select auth.uid()) and closed_at is null for update;
  if not found then raise exception 'กรุณาเปิดกะรับเงินก่อนคิดเงิน'; end if;
  perform set_config('queuego.pos_rpc','on',true);
  insert into public.payments(order_id,amount,payment_method,status,paid_at)
    values(p_order,v_order.total_amount,p_method,'paid',now());
  update public.orders set payment_status='PAID',payment_method=p_method,bill_status='CLOSED',
    cash_tendered=p_cash_received,
    cash_change=case when p_method='cash' then p_cash_received-v_order.total_amount else null end,
    paid_at=now(),status='completed',pos_register_shift_id=v_shift.id,updated_at=now()
  where id=p_order;
  select to_jsonb(o) into v_result from public.orders o where id=p_order;
  return v_result;
end $$;

-- CREATE OR REPLACE preserves the production owner and existing EXECUTE ACLs.
commit;
