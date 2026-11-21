-- Read-only Production metadata snapshot, 2026-11-21. No application rows.
-- Disposable checkout tests ONLY; never apply to Production.
-- qg_next_order_number pg_get_functiondef MD5 d6b34a0303aacce6fbcfd82b7c3c816e
CREATE OR REPLACE FUNCTION public.qg_next_order_number()
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
    )
    and not exists (
      select 1
      from public.laundry_orders l
      where l.status not in ('completed','cancelled')
        and (
          (
            l.order_number ~ '^QT-[0-9]{8}-[0-9]{4}$'
            and right(l.order_number,4)=v_code
          )
          or (
            l.order_number ~ '^LW-[0-9]{8}-[0-9]{6}-[0-9a-fA-F]{4}$'
            and lpad(((('x'||right(l.order_number,4))::bit(16)::int % 10000))::text,4,'0')=v_code
          )
        )
    );
  end loop;

  return v_number;
end
$function$
;

-- qg_table_distance_m pg_get_functiondef MD5 6ada408be6f231698e8ff0809d11d2d0
CREATE OR REPLACE FUNCTION public.qg_table_distance_m(p_lat double precision, p_lng double precision, v_lat double precision, v_lng double precision)
 RETURNS double precision
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'pg_catalog'
AS $function$
 select 6371000*2*asin(sqrt(least(1,power(sin(radians((p_lat-v_lat)/2)),2)+cos(radians(p_lat))*cos(radians(v_lat))*power(sin(radians((p_lng-v_lng)/2)),2))))
$function$
;

