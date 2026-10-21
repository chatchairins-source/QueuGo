-- Make Merchant revenue report match the unified-credit GP collection model.
-- Existing gp_due remains legacy/pre-credit outstanding GP only.
-- gp_credit_deducted is the net GP already captured from Merchant credit, including reversals.

drop function public.merchant_revenue_days(date,date);

create function public.merchant_revenue_days(p_from date,p_to date)
returns table(
  date_key date,
  order_count bigint,
  gross_sales numeric,
  gp_due numeric,
  gp_credit_deducted numeric,
  cash_received numeric,
  gp_status text
)
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_shop uuid;
  v_user uuid;
begin
  select p.id,p.user_id into v_shop,v_user
  from public.shop_profiles p
  join public.users u on u.id=p.user_id
  where u.auth_user_id=auth.uid()
    and u.role='shop'
    and u.status='active'
    and p.status='active';

  if v_shop is null or v_user is null then
    raise exception 'shop login required';
  end if;

  if p_from is null
     or p_to is null
     or p_from>p_to
     or p_to>(now() at time zone 'Asia/Bangkok')::date
     or p_to-p_from>366 then
    raise exception 'invalid date range';
  end if;

  return query
  with days as (
    select generate_series(p_from,p_to,interval '1 day')::date d
  ),
  sales as (
    select
      (o.created_at at time zone 'Asia/Bangkok')::date d,
      count(*) n,
      sum(o.subtotal) gross,
      sum(coalesce(o.gp_amount,round(o.subtotal*coalesce(o.gp_rate,10)/100,2)))
        filter(where not o.credit_gp_required) gp
    from public.orders o
    where o.shop_id=v_shop
      and o.status in ('picked_up','in_progress','completed')
      and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to
    group by 1
  ),
  credit_gp as (
    select
      (o.created_at at time zone 'Asia/Bangkok')::date d,
      round((-sum(e.cash_delta+e.promo_delta))::numeric/100,2) amount
    from qg_private.credit_entries e
    join public.orders o on o.id=e.order_id
    where e.user_id=v_user
      and e.kind='merchant'
      and e.event_type in ('capture','reversal')
      and o.shop_id=v_shop
      and o.credit_gp_required
      and (o.created_at at time zone 'Asia/Bangkok')::date between p_from and p_to
    group by 1
  ),
  cash as (
    select
      (r.received_at at time zone 'Asia/Bangkok')::date d,
      sum(r.amount) amount
    from public.merchant_cash_receipts r
    where r.shop_id=v_shop
      and (r.received_at at time zone 'Asia/Bangkok')::date between p_from and p_to
    group by 1
  )
  select
    days.d,
    coalesce(sales.n,0),
    coalesce(sales.gross,0),
    coalesce(sales.gp,0),
    coalesce(credit_gp.amount,0),
    coalesce(cash.amount,0),
    coalesce((
      select s.status
      from public.settlements s
      where s.shop_id=v_shop and s.date_key=days.d
      order by s.updated_at desc
      limit 1
    ),'pending')
  from days
  left join sales on sales.d=days.d
  left join credit_gp on credit_gp.d=days.d
  left join cash on cash.d=days.d
  order by days.d desc;
end
$$;

revoke all on function public.merchant_revenue_days(date,date) from public,anon;
grant execute on function public.merchant_revenue_days(date,date) to authenticated;
