-- Item sales for the shop's own POS. This is not a QueueGo GP report.
begin;

create index if not exists pos_paid_report_lookup_idx
  on public.orders(shop_id,(coalesce(paid_at,created_at)) desc)
  where sales_channel='POS' and payment_status='PAID' and status='completed';

create or replace function public.pos_owner_item_sales(p_days integer default 1)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  v_days integer:=least(greatest(coalesce(p_days,1),1),365);
  v_result jsonb;
begin
  if not public.pos_is_owner() then raise exception 'owner only'; end if;

  with item_totals as (
    select i.product_id,
      coalesce(nullif(trim(i.item_name),''),'(ไม่ระบุชื่อสินค้า)') as item_name,
      sum(i.quantity)::numeric as quantity,
      sum(round(i.total_price-case when coalesce(o.subtotal,0)>0 then
        coalesce(o.discount_amount,0)*i.total_price/o.subtotal else 0 end,2))::numeric(14,2) as sales,
      count(distinct o.id)::integer as bills
    from public.orders o
    join public.order_items i on i.order_id=o.id
    where o.shop_id=public.pos_my_shop()
      and o.sales_channel='POS'
      and o.payment_status='PAID'
      and o.status='completed'
      and coalesce(o.paid_at,o.created_at)>=
        (((now() at time zone 'Asia/Bangkok')::date-(v_days-1))::timestamp at time zone 'Asia/Bangkok')
    group by i.product_id,coalesce(nullif(trim(i.item_name),''),'(ไม่ระบุชื่อสินค้า)')
  ), ranked as (
    select * from item_totals order by sales desc,quantity desc,item_name asc limit 100
  )
  select jsonb_build_object(
    'days',v_days,
    'item_count',(select count(*) from item_totals),
    'items',coalesce((select jsonb_agg(to_jsonb(ranked) order by sales desc,quantity desc,item_name asc) from ranked),'[]'::jsonb)
  ) into v_result;
  return v_result;
end
$$;

revoke all on function public.pos_owner_item_sales(integer) from public,anon;
grant execute on function public.pos_owner_item_sales(integer) to authenticated;
commit;
