create table if not exists qg_private.rider_destination_preferences (
  rider_id uuid primary key references public.rider_profiles(id) on delete cascade,
  label text not null,
  latitude double precision not null,
  longitude double precision not null,
  enabled boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint rider_destination_label_length check (char_length(btrim(label)) between 1 and 160),
  constraint rider_destination_thailand_bounds check (
    latitude between 5.0 and 21.0
    and longitude between 97.0 and 106.0
  )
);

revoke all on table qg_private.rider_destination_preferences from public, anon, authenticated;
grant all on table qg_private.rider_destination_preferences to service_role;

create or replace function qg_private.approx_distance_km(
  p_lat1 double precision,
  p_lng1 double precision,
  p_lat2 double precision,
  p_lng2 double precision
)
returns double precision
language sql
immutable
strict
set search_path = ''
as $$
  select sqrt(
    power((p_lat2-p_lat1)*111.32, 2) +
    power(
      (p_lng2-p_lng1)*111.32*cos(radians((p_lat1+p_lat2)/2.0)),
      2
    )
  );
$$;

revoke all on function qg_private.approx_distance_km(double precision,double precision,double precision,double precision)
from public, anon, authenticated;

create or replace function qg_private.rider_destination_preference()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_row qg_private.rider_destination_preferences%rowtype;
begin
  perform qg_private.assert_live_session();

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  select * into v_row
  from qg_private.rider_destination_preferences d
  where d.rider_id=v_rider;

  if not found then
    return jsonb_build_object('configured',false,'enabled',false);
  end if;

  return jsonb_build_object(
    'configured',true,
    'enabled',v_row.enabled,
    'label',v_row.label,
    'latitude',v_row.latitude,
    'longitude',v_row.longitude,
    'updated_at',v_row.updated_at
  );
end $$;

revoke all on function qg_private.rider_destination_preference() from public, anon;
grant execute on function qg_private.rider_destination_preference() to authenticated;

create or replace function public.qg_rider_destination_preference()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_destination_preference(); $$;

revoke all on function public.qg_rider_destination_preference() from public, anon;
grant execute on function public.qg_rider_destination_preference() to authenticated, service_role;

create or replace function qg_private.rider_set_destination_preference(
  p_label text,
  p_latitude double precision,
  p_longitude double precision,
  p_enabled boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_label text := nullif(btrim(coalesce(p_label,'')),'');
begin
  perform qg_private.assert_live_session();

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  if v_label is null or char_length(v_label)>160 then
    raise exception 'destination label required';
  end if;
  if p_latitude is null or p_longitude is null
     or p_latitude not between 5.0 and 21.0
     or p_longitude not between 97.0 and 106.0 then
    raise exception 'invalid destination coordinates';
  end if;

  insert into qg_private.rider_destination_preferences(
    rider_id,label,latitude,longitude,enabled,updated_at
  )
  values(v_rider,v_label,p_latitude,p_longitude,coalesce(p_enabled,false),now())
  on conflict(rider_id) do update
    set label=excluded.label,
        latitude=excluded.latitude,
        longitude=excluded.longitude,
        enabled=excluded.enabled,
        updated_at=now();

  return jsonb_build_object(
    'configured',true,
    'enabled',coalesce(p_enabled,false),
    'label',v_label,
    'latitude',p_latitude,
    'longitude',p_longitude
  );
end $$;

revoke all on function qg_private.rider_set_destination_preference(text,double precision,double precision,boolean)
from public, anon;
grant execute on function qg_private.rider_set_destination_preference(text,double precision,double precision,boolean)
to authenticated;

create or replace function public.qg_rider_set_destination_preference(
  p_label text,
  p_latitude double precision,
  p_longitude double precision,
  p_enabled boolean
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select qg_private.rider_set_destination_preference(
    p_label,p_latitude,p_longitude,p_enabled
  );
$$;

revoke all on function public.qg_rider_set_destination_preference(text,double precision,double precision,boolean)
from public, anon;
grant execute on function public.qg_rider_set_destination_preference(text,double precision,double precision,boolean)
to authenticated, service_role;

create or replace function public.qg_dispatch_rider_offers()
returns integer
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
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
    where o.order_type='shopping'
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
      and r.latitude is not null
      and r.longitude is not null
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
        (j.market_order_id is null and j.fulfillment_vertical='food')
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
               'มีงานใหม่',
               'มีออเดอร์ใหม่ใกล้คุณ กรุณาตอบรับภายใน 30 วินาที',
               'order',
               j.id
        from public.rider_profiles r
        where r.id=rr;
      end if;
    end if;
  end loop;

  return n;
end
$function$;

revoke all on function public.qg_dispatch_rider_offers() from public,anon,authenticated;