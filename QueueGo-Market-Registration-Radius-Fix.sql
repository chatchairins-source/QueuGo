create or replace function public.queuego_nearby_markets(
  p_lat double precision,
  p_lng double precision,
  p_max_km numeric default 10
) returns table(
  market_id uuid,
  market_name text,
  distance_km numeric,
  address text,
  province text,
  district text,
  subdistrict text,
  verified boolean
)
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
 select m.id,m.name,
        public.queuego_market_distance_km(p_lat,p_lng,m.latitude,m.longitude) as distance_km,
        m.address,m.province,m.district,m.subdistrict,m.verified
 from public.markets m
 where m.active
   and m.latitude is not null
   and m.longitude is not null
   and public.queuego_market_distance_km(p_lat,p_lng,m.latitude,m.longitude)
       <= least(coalesce(p_max_km,10),coalesce(m.assignment_radius_km,3))
 order by distance_km,m.name
 limit 10;
$$;