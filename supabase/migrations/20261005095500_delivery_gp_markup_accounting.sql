-- QueueGo: GP markup accounting for locked Delivery pricing.
-- Merchant sets storefront price. Delivery price = storefront + GP%.
-- GP amount equals the markup portion, not GP% of the already-marked-up subtotal.
-- Existing orders keep their historical GP snapshot; subtotal changes scale the existing GP proportion.

create or replace function public.orders_set_gp()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rate numeric;
begin
  if new.order_type in ('DINE_IN','TAKEAWAY') then
    new.gp_rate:=0;
    new.gp_amount:=0;
  elsif tg_op='INSERT' then
    v_rate:=coalesce(public.effective_gp_rate_at(new.shop_id,now()),0);
    if v_rate<0 or v_rate>100 then
      raise exception 'invalid effective GP rate';
    end if;
    new.gp_rate:=v_rate;
    new.gp_amount:=case
      when v_rate=0 then 0
      else round(coalesce(new.subtotal,0)*v_rate/(100+v_rate),2)
    end;
  else
    new.gp_rate:=old.gp_rate;
    if new.subtotal is distinct from old.subtotal then
      if coalesce(old.subtotal,0)>0 and old.gp_amount is not null then
        new.gp_amount:=round(coalesce(new.subtotal,0)*old.gp_amount/old.subtotal,2);
      else
        v_rate:=coalesce(old.gp_rate,0);
        new.gp_amount:=case
          when v_rate=0 then 0
          else round(coalesce(new.subtotal,0)*v_rate/(100+v_rate),2)
        end;
      end if;
    else
      new.gp_amount:=old.gp_amount;
    end if;
  end if;
  return new;
end
$$;

revoke all on function public.orders_set_gp() from public,anon,authenticated;
