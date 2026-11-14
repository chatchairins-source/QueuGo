create policy qg_rider_delivery_returns_rpc_only
on public.qg_rider_delivery_returns
for all to authenticated using(false) with check(false);

create policy qg_rider_cash_return_refunds_rpc_only
on public.qg_rider_cash_return_refunds
for all to authenticated using(false) with check(false);

create index if not exists qg_rider_delivery_returns_shop_fk_idx on public.qg_rider_delivery_returns(shop_id);
create index if not exists qg_rider_delivery_returns_customer_fk_idx on public.qg_rider_delivery_returns(customer_id);
create index if not exists qg_rider_delivery_returns_admin_fk_idx on public.qg_rider_delivery_returns(admin_id);
create index if not exists qg_rider_cash_return_refunds_shop_fk_idx on public.qg_rider_cash_return_refunds(shop_id);
