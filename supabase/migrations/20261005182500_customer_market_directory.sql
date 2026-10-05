-- Public Customer market directory.
-- Keeps the market selector/store directory independent from sellable market stock.

begin;

create or replace function public.market_public_markets_v1()
returns table(
  market_id uuid,
  name text,
  province text,
  district text,
  subdistrict text,
  address text,
  latitude double precision,
  longitude double precision,
  verified boolean
)
language sql
stable
security definer
set search_path to 'public','pg_temp'
as $function$
  select m.id,m.name,m.province,m.district,m.subdistrict,m.address,
         m.latitude,m.longitude,coalesce(m.verified,false)
  from public.markets m
  where m.active=true
  order by m.province,m.district,m.name
$function$;

revoke all on function public.market_public_markets_v1() from public;
grant execute on function public.market_public_markets_v1() to anon,authenticated;

create or replace function public.market_public_shops_v2()
returns table(
  shop_id uuid,
  shop_user_id uuid,
  market_id uuid,
  market_name text,
  shop_name text,
  shop_category text,
  shop_logo text,
  shop_cover text,
  latitude double precision,
  longitude double precision,
  open_time text,
  close_time text,
  description text,
  delivery_enabled boolean
)
language sql
stable
security definer
set search_path to 'public','pg_temp'
as $function$
  select s.id,s.user_id,s.market_id,m.name,s.shop_name,coalesce(s.public_category,'market'),
         coalesce(s.public_logo,''),coalesce(s.public_cover,''),s.latitude,s.longitude,
         s.public_open_time,s.public_close_time,coalesce(s.public_description,''),
         coalesce(s.delivery_enabled,false)
  from public.shop_profiles s
  join public.users u on u.id=s.user_id
  join public.markets m on m.id=s.market_id and m.active=true
  where s.status='active'
    and u.role='shop'
    and u.status='active'
    and s.market_membership_status='approved'
  order by m.name,s.shop_name
$function$;

revoke all on function public.market_public_shops_v2() from public;
grant execute on function public.market_public_shops_v2() to anon,authenticated;

commit;
