CREATE OR REPLACE FUNCTION public.queuego_cancel_cash_dependents()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if old.status is distinct from 'cancelled' and new.status='cancelled'
    and exists(select 1 from public.queuego_cash_order_locks where order_id=new.id) then
    update public.payments set status='cancelled',updated_at=now()
      where order_id=new.id and payment_method='cash' and status='pending';
    update public.deliveries set status='cancelled',updated_at=now()
      where order_id=new.id and status not in ('delivered','cancelled');
  end if;
  return new;
end $function$
