create unique index if not exists orders_active_visible_code_uq
  on public.orders ((right(order_number,4)))
  where order_number ~ '^QT-[0-9]{8}-[0-9]{4}$'
    and status not in ('completed','cancelled','no_rider_available');

create or replace function public.qg_next_order_number()
returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_today text;
  v_candidate integer;
  v_code text;
  v_number text;
  v_attempt integer := 0;
begin
  v_today := to_char(now() at time zone 'Asia/Bangkok','YYYYMMDD');

  perform pg_advisory_xact_lock(hashtextextended('queuego-visible-order-number',122));

  loop
    v_attempt := v_attempt + 1;
    if v_attempt > 2000 then
      raise exception 'active random order number space exhausted';
    end if;

    v_candidate := 1000 + floor(random() * 9000)::integer;
    v_code := lpad(v_candidate::text,4,'0');
    v_number := 'QT-'||v_today||'-'||v_code;

    exit when not exists (
      select 1
      from public.orders o
      where (
          o.order_number = v_number
          or (
            o.order_number ~ '^QT-[0-9]{8}-[0-9]{4}$'
            and right(o.order_number,4)=v_code
            and o.status not in ('completed','cancelled','no_rider_available')
          )
        )
    );
  end loop;

  return v_number;
end
$function$;
