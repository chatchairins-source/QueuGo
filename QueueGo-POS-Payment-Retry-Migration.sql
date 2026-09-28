-- A timed-out cashier request can safely return the already paid bill.
begin;
create or replace function public.pos_take_payment(p_order uuid,p_method text,p_cash_received numeric default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;v_result jsonb;
begin
 if not public.pos_allowed('close_bill') then raise exception 'cashier permission denied'; end if;
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
 if not found then raise exception 'bill not found'; end if;
 if v_order.payment_status='PAID' and v_order.bill_status='CLOSED'
 and v_order.payment_method=p_method and v_order.cash_tendered is not distinct from p_cash_received
 and exists(select 1 from public.payments where order_id=p_order and status='paid' and payment_method=p_method)
 then return to_jsonb(v_order); end if;
 if v_order.payment_status<>'UNPAID' or v_order.status='cancelled' or v_order.kitchen_status not in ('READY','SERVED') then raise exception 'bill not ready for payment'; end if;
 if p_method not in ('cash','bank_transfer','promptpay','card','other') then raise exception 'invalid payment method'; end if;
 if p_method='cash' and (p_cash_received is null or p_cash_received<v_order.total_amount) then raise exception 'cash received is less than total'; end if;
 if p_method<>'cash' and p_cash_received is not null then raise exception 'cash received only for cash payment'; end if;
 perform set_config('queuego.pos_rpc','on',true);
 insert into public.payments(order_id,amount,payment_method,status,paid_at)
 values(p_order,v_order.total_amount,p_method,'paid',now());
 update public.orders set payment_status='PAID',payment_method=p_method,
 bill_status='CLOSED',cash_tendered=p_cash_received,
 cash_change=case when p_method='cash' then p_cash_received-v_order.total_amount else null end,
 paid_at=now(),status='completed',updated_at=now() where id=p_order;
 select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
 return v_result;
end $$;
commit;
