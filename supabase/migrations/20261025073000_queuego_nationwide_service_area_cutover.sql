-- Cut QueueGo checkout service-area decisions over to the nationwide polygon authority.
-- The legacy Buriram table remains intact for rollback/audit; all existing checkout callers keep the same public function contract.
create or replace function public.queuego_service_area_route_status(
 p_pickup_lat double precision,p_pickup_lng double precision,
 p_delivery_lat double precision,p_delivery_lng double precision,
 p_vertical text default 'delivery'
) returns jsonb
language sql stable set search_path=''
as $function$
 with decision as (
  select public.queuego_order_service_route_status_v2(
   p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,p_vertical
  ) as v
 )
 select case
  when v ? 'rule_id' then v || jsonb_build_object('area_id',v->'rule_id','area_name',v->'rule_name')
  else v
 end
 from decision;
$function$;

revoke all on function public.queuego_service_area_route_status(double precision,double precision,double precision,double precision,text) from public,anon,authenticated;
grant execute on function public.queuego_service_area_route_status(double precision,double precision,double precision,double precision,text) to anon,authenticated,service_role;
