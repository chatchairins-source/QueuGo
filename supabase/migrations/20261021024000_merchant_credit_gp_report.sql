-- Report Merchant GP that has already been deducted from the unified credit ledger.
-- This is read-only reporting. It does not change balances, holds, feature flags, or legacy settlement data.

create or replace function public.merchant_credit_gp_days(p_from date,p_to date)
returns table(date_key date,gp_credit_deducted numeric)
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
  ledger as (
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
  )
  select days.d,coalesce(ledger.amount,0)::numeric
  from days
  left join ledger on ledger.d=days.d
  order by days.d desc;
end
$$;

revoke all on function public.merchant_credit_gp_days(date,date) from public,anon,service_role;
grant execute on function public.merchant_credit_gp_days(date,date) to authenticated;
