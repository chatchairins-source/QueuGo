-- QueueGo POS tax checkout integration. Apply after QueueGo-POS-Tax-Foundation-Migration.sql.
-- Server-side trigger owns bill snapshots and totals; clients cannot submit tax totals.
begin;

create or replace function public.pos_save_tax_settings(
  p_tax_enabled boolean,
  p_tax_rate numeric,
  p_prices_include_tax boolean,
  p_registration_number text default null,
  p_branch_code text default null,
  p_invoice_address text default null
) returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop uuid; v_result jsonb;
begin
  v_shop := public.pos_my_shop();
  if v_shop is null or not public.pos_is_owner() then raise exception 'POS owner permission required'; end if;
  if p_tax_enabled is null or p_prices_include_tax is null or p_tax_rate is null or p_tax_rate < 0 or p_tax_rate > 100 then
    raise exception 'invalid tax settings';
  end if;
  if length(trim(coalesce(p_registration_number,''))) > 30 or length(trim(coalesce(p_branch_code,''))) > 20
    or length(trim(coalesce(p_invoice_address,''))) > 1000 then raise exception 'tax profile field too long'; end if;
  insert into public.pos_tax_settings(shop_id,tax_enabled,tax_rate,prices_include_tax,tax_registration_number,tax_branch_code,tax_invoice_address,updated_by,updated_at)
  values(v_shop,p_tax_enabled,round(p_tax_rate,2),p_prices_include_tax,nullif(trim(p_registration_number),''),nullif(trim(p_branch_code),''),nullif(trim(p_invoice_address),''),auth.uid(),now())
  on conflict(shop_id) do update set tax_enabled=excluded.tax_enabled,tax_rate=excluded.tax_rate,
    prices_include_tax=excluded.prices_include_tax,tax_registration_number=excluded.tax_registration_number,
    tax_branch_code=excluded.tax_branch_code,tax_invoice_address=excluded.tax_invoice_address,
    updated_by=auth.uid(),updated_at=now();
  select to_jsonb(s) into v_result from public.pos_tax_settings s where s.shop_id=v_shop;
  return v_result;
end $$;

create or replace function public.pos_apply_tax_snapshot()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_settings public.pos_tax_settings%rowtype; v_rate numeric; v_base numeric; v_tax numeric; v_net numeric;
begin
  if new.sales_channel is distinct from 'POS' then return new; end if;

  if tg_op = 'INSERT' then
    select * into v_settings from public.pos_tax_settings where shop_id=new.shop_id;
    new.pos_tax_enabled_snapshot := coalesce(v_settings.tax_enabled,false);
    new.pos_tax_rate_snapshot := coalesce(v_settings.tax_rate,7.00);
    new.pos_prices_include_tax_snapshot := coalesce(v_settings.prices_include_tax,true);
    new.pos_tax_registration_snapshot := v_settings.tax_registration_number;
    new.pos_tax_branch_snapshot := v_settings.tax_branch_code;
    new.pos_tax_address_snapshot := v_settings.tax_invoice_address;
  end if;

  -- Legacy POS bills retain their existing totals; only bills created with tax snapshots are recalculated.
  if new.pos_tax_enabled_snapshot is distinct from true then
    new.pos_taxable_base := null;
    new.pos_tax_amount := null;
    return new;
  end if;

  v_rate := coalesce(new.pos_tax_rate_snapshot,0) / 100.0;
  v_net := greatest(coalesce(new.subtotal,0) - coalesce(new.discount_amount,0),0);
  if coalesce(new.pos_prices_include_tax_snapshot,true) then
    v_tax := round(v_net * v_rate / (1 + v_rate),2);
    v_base := v_net - v_tax;
    new.total_amount := v_net;
  else
    v_base := v_net;
    v_tax := round(v_base * v_rate,2);
    new.total_amount := v_net + v_tax;
  end if;
  new.pos_taxable_base := v_base;
  new.pos_tax_amount := v_tax;
  return new;
end $$;

drop trigger if exists pos_apply_tax_snapshot on public.orders;
create trigger pos_apply_tax_snapshot
before insert or update of subtotal,discount_amount,sales_channel,pos_tax_enabled_snapshot,pos_tax_rate_snapshot,pos_prices_include_tax_snapshot
on public.orders for each row execute function public.pos_apply_tax_snapshot();

revoke all on function public.pos_save_tax_settings(boolean,numeric,boolean,text,text,text) from public,anon;
grant execute on function public.pos_save_tax_settings(boolean,numeric,boolean,text,text,text) to authenticated;
revoke all on function public.pos_apply_tax_snapshot() from public,anon,authenticated;
commit;
