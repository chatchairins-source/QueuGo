-- READ-ONLY SNAPSHOT of Production qg_dispatch_rider_offers() definition, 2026-11-19.
-- Isolated PGlite regression fixture ONLY; never deploy or replay this snapshot as a migration.
-- Preserve newer destination, role, route and vehicle checks in all real migrations.
CREATE OR REPLACE FUNCTION public.qg_dispatch_rider_offers()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  j record;
  rr uuid;
  a int;
  n int:=0;
begin
  insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
  select f.order_id,f.rider_id,f.offered_at,now(),'expired',f.attempt
  from public.qg_rider_order_offers f
  join public.orders o on o.id=f.order_id
  where f.expires_at<=now()
    and o.status='searching_rider'
    and o.rider_id is null;

  delete from public.qg_rider_order_offers f
  using public.orders o
  where f.order_id=o.id
    and (f.expires_at<=now() or o.status<>'searching_rider' or o.rider_id is not null);

  for j in
    select o.*
    from public.orders o
    where (o.order_type='shopping' or (o.order_type='service' and o.fulfillment_vertical='ride' and public.queuego_feature_enabled('ride',now())))
      and o.status='searching_rider'
      and o.rider_id is null
      and (
        o.market_order_id is null
        or o.id=(
          select o2.id from public.orders o2
          where o2.market_order_id=o.market_order_id
            and o2.status<>'cancelled'
          order by o2.created_at,o2.id
          limit 1
        )
      )
      and (
        o.market_order_id is null
        or not exists(
          select 1 from public.orders o3
          where o3.market_order_id=o.market_order_id
            and o3.status not in('searching_rider','cancelled')
        )
      )
      and not exists(
        select 1 from public.qg_rider_order_offers f
        where f.order_id=o.id
      )
    order by o.created_at
    for update skip locked
  loop
    rr:=null;

    select r.id into rr
    from public.rider_profiles r
    join public.users u on u.id=r.user_id
    left join qg_private.rider_destination_preferences dp
      on dp.rider_id=r.id
     and dp.enabled=true
    where u.role='rider'
      and u.status='active'
      and r.status='active'
      and coalesce((r.metadata->>'online')::boolean,false)
      and coalesce((r.metadata->>'available')::boolean,false)
      and qg_private.rider_work_allows(r.id,j.id)
      and r.latitude is not null
      and r.longitude is not null
      and (j.fulfillment_vertical<>'ride' or qg_private.ride_owner_authorized(r.id))
      and not exists(
        select 1 from public.orders b
        where b.rider_id=r.id
          and b.status in('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
      )
      and not exists(
        select 1 from public.laundry_rider_jobs lj
        where lj.rider_id=r.id
          and lj.status in('assigned','accepted','arrived','collected')
      )
      and not exists(
        select 1 from public.qg_rider_order_offers h
        where h.rider_id=r.id
          and h.expires_at>now()
      )
      and not exists(
        select 1
        from public.qg_rider_offer_history h
        where h.order_id=j.id
          and h.rider_id=r.id
          and (
            (h.outcome='declined' and h.resolved_at>now()-interval '10 minutes')
            or
            (h.outcome='expired' and h.resolved_at>now()-interval '30 seconds')
            or
            (coalesce(h.outcome,'') not in ('declined','expired')
             and h.resolved_at>now()-interval '10 minutes')
          )
      )
      and (
        (j.market_order_id is null and j.fulfillment_vertical in ('food','ride'))
        or (
          j.market_order_id is not null
          and r.vehicle_type in('motorcycle','car','saleng')
          and r.vehicle_status='active'
          and r.vehicle_verified_at is not null
          and r.vehicle_capacity_kg >= (
            select coalesce(sum(public.market_order_weight(o4.id)),0)
            from public.orders o4
            where o4.market_order_id=j.market_order_id
              and o4.status<>'cancelled'
          )
        )
        or (
          j.market_order_id is null
          and j.fulfillment_vertical in('market','grocery')
          and r.vehicle_type in('motorcycle','car','saleng')
          and r.vehicle_status='active'
          and r.vehicle_verified_at is not null
          and r.vehicle_capacity_kg>=public.market_order_weight(j.id)
          and public.market_order_weight(j.id)>0
        )
      )
    order by
      floor(
        qg_private.approx_distance_km(
          r.latitude::double precision,r.longitude::double precision,
          j.pickup_latitude::double precision,j.pickup_longitude::double precision
        )
      ),
      case
        when dp.rider_id is not null
         and j.delivery_latitude is not null
         and j.delivery_longitude is not null
         and (
           qg_private.approx_distance_km(
             r.latitude::double precision,r.longitude::double precision,
             dp.latitude,dp.longitude
           )
           -
           qg_private.approx_distance_km(
             j.delivery_latitude::double precision,j.delivery_longitude::double precision,
             dp.latitude,dp.longitude
           )
         ) >= 1.0
        then 0 else 1
      end,
      case
        when dp.rider_id is not null
         and j.delivery_latitude is not null
         and j.delivery_longitude is not null
        then qg_private.approx_distance_km(
          j.delivery_latitude::double precision,j.delivery_longitude::double precision,
          dp.latitude,dp.longitude
        )
        else 1000000.0
      end,
      qg_private.approx_distance_km(
        r.latitude::double precision,r.longitude::double precision,
        j.pickup_latitude::double precision,j.pickup_longitude::double precision
      ),
      r.updated_at,
      r.id
    limit 1
    for update of r skip locked;

    if rr is not null then
      select coalesce(max(attempt),0)+1
      into a
      from public.qg_rider_offer_history
      where order_id=j.id;

      insert into public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt)
      values(j.id,rr,now(),now()+interval '30 seconds',a)
      on conflict(order_id) do nothing;

      if found then
        n:=n+1;

        insert into public.notifications(user_id,title,message,type,reference_id)
        select r.user_id,
               case when j.fulfillment_vertical='ride' then 'มีเที่ยวผู้โดยสารใหม่' else 'มีงานใหม่' end,
               case when j.fulfillment_vertical='ride' then 'มีคำขอรับส่งผู้โดยสารใกล้คุณ กรุณาตอบรับภายใน 30 วินาที' else 'มีออเดอร์ใหม่ใกล้คุณ กรุณาตอบรับภายใน 30 วินาที' end,
               'order',
               j.id
        from public.rider_profiles r
        where r.id=rr;
      end if;
    end if;
  end loop;

  return n;
end
$function$

