-- POS sales contain storefront bills only. Delivery reporting keeps its existing APIs.
create or replace function public.pos_owner_dashboard(p_days integer default 1)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_result jsonb;
begin
 if not public.pos_is_owner() then raise exception 'owner only'; end if;
 select jsonb_build_object(
 'DINE_IN',coalesce(sum(total_amount) filter(where order_type='DINE_IN'),0),
 'TAKEAWAY',coalesce(sum(total_amount) filter(where order_type='TAKEAWAY'),0),
 'orders',count(*)) into v_result
 from public.orders where shop_id=public.pos_my_shop() and status='completed'
 and sales_channel='POS' and order_type in ('DINE_IN','TAKEAWAY') and payment_status='PAID'
 and created_at>=(((now() at time zone 'Asia/Bangkok')::date-(least(greatest(p_days,1),30)-1))::timestamp at time zone 'Asia/Bangkok');
 return v_result;
end $$;
-- CREATE OR REPLACE retains existing owner/EXECUTE ACLs; no new endpoint or ledger.
