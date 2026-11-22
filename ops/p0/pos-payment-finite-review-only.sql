-- REVIEW ONLY: do not apply without approved backup/restore and financial rollout.
-- Forward candidate; no migration history, order or payment data is changed here.
-- CREATE OR REPLACE retains the observed function owner and EXECUTE ACL.
BEGIN;
DO $guard$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_catalog.pg_proc
    WHERE oid='public.pos_take_payment(uuid,text,numeric)'::regprocedure
      AND md5(prosrc)='439406595ff236408dd07a21fbd4afd3'
      AND prosecdef AND NOT proisstrict
      AND provolatile='v' AND proparallel='u'
      AND prorettype='jsonb'::regtype
      AND proconfig=ARRAY['search_path=public, pg_temp']::text[]
      AND pg_get_userbyid(proowner)='postgres'
  ) THEN
    RAISE EXCEPTION 'POS payment baseline changed; review exact current function before rollout';
  END IF;
END $guard$;
CREATE OR REPLACE FUNCTION public.pos_take_payment(p_order uuid, p_method text, p_cash_received numeric DEFAULT NULL::numeric)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_order public.orders%rowtype;
  v_shift public.pos_register_shifts%rowtype;
  v_result jsonb;
  v_kitchen_enabled boolean;
begin
  if not public.pos_allowed('close_bill') then raise exception 'cashier permission denied'; end if;
  if p_cash_received is not null and p_cash_received::text in ('NaN','Infinity','-Infinity') then
    raise exception 'finite POS cash received required';
  end if;
  select * into v_order from public.orders
  where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
  if not found then raise exception 'bill not found'; end if;
  if v_order.total_amount is null or v_order.total_amount::text in ('NaN','Infinity','-Infinity')
     or v_order.total_amount < 0 then
    raise exception 'finite nonnegative POS bill total required';
  end if;
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
  if p_method is null or p_method not in ('cash','bank_transfer','promptpay','card','other') then raise exception 'invalid payment method'; end if;
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
end $function$
;
COMMIT;

