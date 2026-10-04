-- QueueGo Laundry Rider active job summary for Rider UI.
create or replace function public.queuego_laundry_rider_active_job()
returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_job public.laundry_rider_jobs%rowtype;
  v_order public.laundry_orders%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_from_address text;v_to_address text;
  v_from_lat double precision;v_from_lng double precision;
  v_to_lat double precision;v_to_lng double precision;
  v_fee numeric;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active';
  if v_rider is null then return null; end if;

  select * into v_job
  from public.laundry_rider_jobs
  where rider_id=v_rider and status in ('assigned','accepted','arrived','collected')
  order by updated_at desc
  limit 1;
  if not found then return null; end if;

  select * into v_order from public.laundry_orders where id=v_job.laundry_order_id;
  select * into v_hub from public.laundry_hubs where id=v_order.hub_id;
  select * into v_shop from public.shop_profiles where id=v_hub.shop_id;

  if v_job.leg='pickup' then
    v_from_address:=v_order.pickup_address;v_from_lat:=v_order.pickup_latitude;v_from_lng:=v_order.pickup_longitude;
    v_to_address:=v_shop.address;v_to_lat:=v_shop.latitude;v_to_lng:=v_shop.longitude;
    v_fee:=coalesce(v_order.pickup_fee_snapshot,0);
  else
    v_from_address:=v_shop.address;v_from_lat:=v_shop.latitude;v_from_lng:=v_shop.longitude;
    v_to_address:=v_order.pickup_address;v_to_lat:=v_order.pickup_latitude;v_to_lng:=v_order.pickup_longitude;
    v_fee:=coalesce(v_order.return_fee_snapshot,0);
  end if;

  return jsonb_build_object(
    'job_id',v_job.id,'job_status',v_job.status,'leg',v_job.leg,
    'laundry_order_id',v_order.id,'order_number',v_order.order_number,'order_status',v_order.status,
    'hub_id',v_hub.id,'hub_name',v_hub.name,'shop_name',v_shop.shop_name,
    'service_name',v_order.service_name_snapshot,'actual_quantity',v_order.actual_quantity,
    'pricing_type',v_order.pricing_type_snapshot,'job_fee',v_fee,
    'from_address',v_from_address,'from_latitude',v_from_lat,'from_longitude',v_from_lng,
    'to_address',v_to_address,'to_latitude',v_to_lat,'to_longitude',v_to_lng,
    'customer_amount',coalesce(v_order.final_total_amount,v_order.estimated_total_amount)
  );
end $$;

revoke all on function public.queuego_laundry_rider_active_job() from public,anon;
grant execute on function public.queuego_laundry_rider_active_job() to authenticated,service_role;
