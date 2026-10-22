-- Extend the existing POS-only owner dashboard; Delivery and GP stay in their own reports.
create or replace function public.pos_owner_dashboard(p_days integer default 1)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_days integer:=least(greatest(coalesce(p_days,1),1),365);
  v_start timestamptz;
  v_end timestamptz:=now();
  v_shop uuid:=public.pos_my_shop();
  v_result jsonb;
begin
  if not public.pos_is_owner() then raise exception 'owner only'; end if;
  if v_shop is null then raise exception 'POS access denied'; end if;

  v_start:=(((now() at time zone 'Asia/Bangkok')::date-(v_days-1))::timestamp at time zone 'Asia/Bangkok');

  with paid as (
    select o.id,o.order_type,o.total_amount,o.discount_amount,o.payment_method,
      coalesce(o.paid_at,o.created_at) as sale_at
    from public.orders o
    where o.shop_id=v_shop and o.sales_channel='POS'
      and o.order_type in ('DINE_IN','TAKEAWAY')
      and o.status='completed' and o.payment_status='PAID'
      and coalesce(o.paid_at,o.created_at)>=v_start
      and coalesce(o.paid_at,o.created_at)<v_end
  ), totals as (
    select
      coalesce(sum(total_amount) filter(where order_type='DINE_IN'),0)::numeric(14,2) as dine_in,
      coalesce(sum(total_amount) filter(where order_type='TAKEAWAY'),0)::numeric(14,2) as takeaway,
      count(*)::integer as bills,
      coalesce(round(sum(total_amount)/nullif(count(*),0),2),0)::numeric(14,2) as average_ticket,
      coalesce(sum(total_amount) filter(where payment_method='cash'),0)::numeric(14,2) as cash_sales,
      coalesce(sum(discount_amount),0)::numeric(14,2) as discounts,
      coalesce(sum(total_amount),0)::numeric(14,2) as total_sales
    from paid
  ), previous as (
    select coalesce(sum(o.total_amount),0)::numeric(14,2) as total_sales
    from public.orders o
    where o.shop_id=v_shop and o.sales_channel='POS'
      and o.order_type in ('DINE_IN','TAKEAWAY')
      and o.status='completed' and o.payment_status='PAID'
      and coalesce(o.paid_at,o.created_at)>=v_start-(v_days||' days')::interval
      and coalesce(o.paid_at,o.created_at)<v_start
  ), hourly as (
    select extract(hour from sale_at at time zone 'Asia/Bangkok')::integer as hour,
      sum(total_amount)::numeric(14,2) as sales,count(*)::integer as bills
    from paid group by 1
  )
  select jsonb_build_object(
    'DINE_IN',totals.dine_in,
    'TAKEAWAY',totals.takeaway,
    'orders',totals.bills,
    'average_ticket',totals.average_ticket,
    'cash_sales',totals.cash_sales,
    'discounts',totals.discounts,
    'refunds',coalesce((
      select sum(abs(e.amount))::numeric(14,2)
      from public.pos_register_cash_events e
      where e.shop_id=v_shop and e.event_type='refund'
        and e.created_at>=v_start and e.created_at<v_end
    ),0),
    'voids',coalesce((
      select count(*)::integer from public.orders o
      where o.shop_id=v_shop and o.sales_channel='POS'
        and o.order_type in ('DINE_IN','TAKEAWAY') and o.status='cancelled'
        and o.created_at>=v_start and o.created_at<v_end
    ),0),
    'previous_period_sales',previous.total_sales,
    'change_percent',case when previous.total_sales=0 then null
      else round((totals.total_sales-previous.total_sales)*100/previous.total_sales,1) end,
    'hourly_sales',coalesce((
      select jsonb_agg(jsonb_build_object('hour',h.hour,'sales',h.sales,'bills',h.bills) order by h.hour)
      from hourly h
    ),'[]'::jsonb)
  ) into v_result
  from totals cross join previous;

  return v_result;
end
$$;
-- CREATE OR REPLACE retains the existing owner-only EXECUTE grants.
