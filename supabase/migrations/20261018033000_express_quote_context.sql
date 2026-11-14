-- QueueGo Express trusted quote context. Source-only staging; no Production enablement.
begin;

create or replace function public.queuego_express_quote_context(
 p_auth_user_id uuid,
 p_pickup_lat double precision,p_pickup_lng double precision,
 p_delivery_lat double precision,p_delivery_lng double precision
)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare v_customer uuid;c jsonb;a jsonb;
begin
 select id into v_customer
 from public.users
 where auth_user_id=p_auth_user_id and role='customer' and status='active';

 if v_customer is null then raise exception 'active customer required'; end if;

 c:=public.queuego_express_config();
 if not coalesce((c->>'enabled')::boolean,false)
    or not coalesce((c->>'configured')::boolean,false)
 then raise exception 'express unavailable'; end if;

 if exists(
  select 1 from public.orders
  where customer_id=v_customer
    and fulfillment_vertical='express'
    and status not in ('completed','cancelled','no_rider_available')
 ) then raise exception 'finish current Express first'; end if;

 if (
  select count(*) from qg_private.express_quotes
  where customer_id=v_customer and created_at>now()-interval '1 minute'
 )>=5 then raise exception 'wait before requesting another quote'; end if;

 a:=public.queuego_service_area_route_status(
  p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,'express'
 );
 if not coalesce((a->>'available')::boolean,false)
 then raise exception 'outside express service area'; end if;

 return jsonb_build_object('available',true,'service_area',a,'config',c);
end $$;

revoke all on function public.queuego_express_quote_context(uuid,double precision,double precision,double precision,double precision)
 from public,anon,authenticated;
grant execute on function public.queuego_express_quote_context(uuid,double precision,double precision,double precision,double precision)
 to service_role;

commit;
