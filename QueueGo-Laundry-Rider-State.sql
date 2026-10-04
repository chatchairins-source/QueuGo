-- QueueGo Laundry Rider state RPC for mobile UI.
create or replace function public.queuego_laundry_rider_state()
returns jsonb
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider public.rider_profiles%rowtype;
  v_feature boolean;
  v_mode boolean:=false;
begin
  select r.* into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider' and u.status='active'
    and r.status='active'
  limit 1;
  if not found then raise exception 'active Rider required'; end if;

  v_feature:=public.queuego_feature_enabled('laundry',now());
  select coalesce(p.laundry_mode_enabled,false) into v_mode
  from public.laundry_rider_preferences p
  where p.rider_id=v_rider.id;
  v_mode:=coalesce(v_mode,false);

  return jsonb_build_object(
    'feature_enabled',v_feature,
    'mode_enabled',v_mode,
    'rider_id',v_rider.id,
    'invites',coalesce((
      select jsonb_agg(jsonb_build_object(
        'invite_id',i.id,'hub_id',i.hub_id,'hub_name',h.name,'shop_name',s.shop_name,
        'status',i.status,'created_at',i.created_at
      ) order by i.created_at desc)
      from public.laundry_rider_invites i
      join public.laundry_hubs h on h.id=i.hub_id
      join public.shop_profiles s on s.id=h.shop_id
      where i.rider_id=v_rider.id and i.status='pending'
    ),'[]'::jsonb),
    'active_jobs',coalesce((
      select jsonb_agg(jsonb_build_object(
        'job_id',j.id,'laundry_order_id',o.id,'order_number',o.order_number,
        'leg',j.leg,'job_status',j.status,'order_status',o.status,
        'hub_id',h.id,'hub_name',h.name,'shop_name',s.shop_name,
        'service_name',o.service_name_snapshot,'job_fee',
          case when j.leg='pickup' then coalesce(o.pickup_fee_snapshot,0) else coalesce(o.return_fee_snapshot,0) end,
        'from_address',case when j.leg='pickup' then o.pickup_address else s.address end,
        'from_latitude',case when j.leg='pickup' then o.pickup_latitude else s.latitude end,
        'from_longitude',case when j.leg='pickup' then o.pickup_longitude else s.longitude end,
        'to_address',case when j.leg='pickup' then s.address else o.pickup_address end,
        'to_latitude',case when j.leg='pickup' then s.latitude else o.pickup_latitude end,
        'to_longitude',case when j.leg='pickup' then s.longitude else o.pickup_longitude end,
        'created_at',j.created_at,'updated_at',j.updated_at
      ) order by j.created_at)
      from public.laundry_rider_jobs j
      join public.laundry_orders o on o.id=j.laundry_order_id
      join public.laundry_hubs h on h.id=o.hub_id
      join public.shop_profiles s on s.id=h.shop_id
      where j.rider_id=v_rider.id and j.status in ('assigned','accepted','arrived','collected')
    ),'[]'::jsonb),
    'pool',case when v_feature and v_mode then coalesce(
      (select jsonb_agg(to_jsonb(x) order by x.created_at) from public.queuego_laundry_rider_pool() x),
      '[]'::jsonb
    ) else '[]'::jsonb end
  );
end $$;

revoke all on function public.queuego_laundry_rider_state() from public,anon;
grant execute on function public.queuego_laundry_rider_state() to authenticated,service_role;
