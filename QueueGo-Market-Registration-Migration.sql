-- QueueGo market merchant registration and explicit market membership
-- Additive: keeps existing approved market shops active, changes future auto-assignment into auto-suggestion.

alter table public.shop_profiles
  add column if not exists market_suggested_id uuid references public.markets(id),
  add column if not exists market_suggested_distance_km numeric,
  add column if not exists market_membership_status text not null default 'none',
  add column if not exists market_confirmed_at timestamptz,
  add column if not exists market_stall_no text,
  add column if not exists market_zone text,
  add column if not exists market_proof_path text,
  add column if not exists market_reviewed_by uuid references public.users(id) on delete set null,
  add column if not exists market_reviewed_at timestamptz,
  add column if not exists market_rejection_reason text;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.shop_profiles'::regclass
      and conname='shop_profiles_market_membership_status_check'
  ) then
    alter table public.shop_profiles
      add constraint shop_profiles_market_membership_status_check
      check (market_membership_status in ('none','suggested','pending','approved','rejected'));
  end if;
end $$;

create index if not exists shop_profiles_market_membership_idx
  on public.shop_profiles(market_id,market_membership_status,status);

create index if not exists shop_profiles_market_suggestion_idx
  on public.shop_profiles(market_suggested_id,market_membership_status);

-- Preserve existing market behavior only for shops that were already explicitly tied to a market.
update public.shop_profiles
set market_membership_status='approved',
    market_confirmed_at=coalesce(market_confirmed_at,updated_at,now()),
    market_reviewed_at=coalesce(market_reviewed_at,updated_at,now())
where market_id is not null
  and market_membership_status in ('none','suggested');

-- Launch seed requested by Product Owner.
-- Public listings confirm ตลาดสดสวายจีก in Sawai Chik, Mueang Buri Ram.
-- Coordinates are a provisional center for launch discovery and remain verified=false until Admin confirms the exact market pin.
insert into public.markets(
  name,address,latitude,longitude,active,province,district,subdistrict,source,verified,assignment_radius_km
)
select
  'ตลาดสดสวายจีก',
  'ต.สวายจีก อ.เมืองบุรีรัมย์ จ.บุรีรัมย์ 31000',
  14.90224,
  103.14414,
  true,
  'บุรีรัมย์',
  'เมืองบุรีรัมย์',
  'สวายจีก',
  'QueueGo launch seed; public market listing confirmed, provisional center pin',
  false,
  2
where not exists (
  select 1 from public.markets
  where lower(trim(name))=lower(trim('ตลาดสดสวายจีก'))
    and province='บุรีรัมย์'
);

create table if not exists public.market_requests (
  id uuid primary key default gen_random_uuid(),
  requester_shop_user_id uuid not null references public.users(id) on delete cascade,
  requested_name text not null,
  province text not null,
  district text,
  subdistrict text,
  address text,
  latitude double precision,
  longitude double precision,
  status text not null default 'pending',
  note text,
  reviewed_by uuid references public.users(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint market_requests_status_check check(status in ('pending','approved','rejected'))
);

create index if not exists market_requests_requester_idx
  on public.market_requests(requester_shop_user_id,created_at desc);
create index if not exists market_requests_status_idx
  on public.market_requests(status,created_at);

alter table public.market_requests enable row level security;

do $$
begin
  if not exists(select 1 from pg_policies where schemaname='public' and tablename='market_requests' and policyname='market_requests_shop_select') then
    create policy market_requests_shop_select on public.market_requests
      for select to authenticated
      using (requester_shop_user_id=public.get_my_user_id() or public.is_active_admin());
  end if;
  if not exists(select 1 from pg_policies where schemaname='public' and tablename='market_requests' and policyname='market_requests_shop_insert') then
    create policy market_requests_shop_insert on public.market_requests
      for insert to authenticated
      with check (
        requester_shop_user_id=public.get_my_user_id()
        and public.get_my_role()='shop'
      );
  end if;
  if not exists(select 1 from pg_policies where schemaname='public' and tablename='market_requests' and policyname='market_requests_admin_update') then
    create policy market_requests_admin_update on public.market_requests
      for update to authenticated
      using (public.is_active_admin())
      with check (public.is_active_admin());
  end if;
  if not exists(select 1 from pg_policies where schemaname='public' and tablename='market_requests' and policyname='market_requests_admin_delete') then
    create policy market_requests_admin_delete on public.market_requests
      for delete to authenticated
      using (public.is_active_admin());
  end if;
end $$;

revoke all on public.market_requests from public,anon,authenticated;
grant select,insert on public.market_requests to authenticated;
grant update,delete on public.market_requests to authenticated;
grant all on public.market_requests to service_role;

create or replace function public.queuego_market_distance_km(
  p_lat1 double precision,p_lng1 double precision,p_lat2 double precision,p_lng2 double precision
) returns numeric
language sql immutable
security invoker
set search_path to 'public','pg_temp'
as $$
 select (
   6371*2*asin(sqrt(least(1,greatest(0,
     power(sin(radians((p_lat2-p_lat1)/2)),2)
     + cos(radians(p_lat1))*cos(radians(p_lat2))*power(sin(radians((p_lng2-p_lng1)/2)),2)
   ))))
 )::numeric;
$$;

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
   and m.latitude is not null and m.longitude is not null
   and public.queuego_market_distance_km(p_lat,p_lng,m.latitude,m.longitude)<=coalesce(p_max_km,10)
 order by distance_km,m.name
 limit 10;
$$;

revoke all on function public.queuego_market_distance_km(double precision,double precision,double precision,double precision) from public;
revoke all on function public.queuego_nearby_markets(double precision,double precision,numeric) from public;
grant execute on function public.queuego_market_distance_km(double precision,double precision,double precision,double precision) to anon,authenticated,service_role;
grant execute on function public.queuego_nearby_markets(double precision,double precision,numeric) to anon,authenticated,service_role;

-- Existing helper name retained, but it now suggests rather than silently approving membership.
create or replace function public.queuego_assign_shop_nearest_market(
  p_shop_id uuid,
  p_max_km numeric default 5
) returns uuid
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_market uuid;v_dist numeric;v_lat double precision;v_lng double precision;
begin
 select latitude,longitude into v_lat,v_lng
 from public.shop_profiles where id=p_shop_id;
 if v_lat is null or v_lng is null then return null; end if;

 select market_id,distance_km into v_market,v_dist
 from public.queuego_nearby_markets(v_lat,v_lng,p_max_km)
 limit 1;

 update public.shop_profiles
 set market_suggested_id=v_market,
     market_suggested_distance_km=v_dist,
     market_membership_status=case
       when market_membership_status in ('pending','approved') then market_membership_status
       when v_market is null then 'none'
       else 'suggested'
     end,
     updated_at=now()
 where id=p_shop_id;

 return v_market;
end $$;

create or replace function public.qg_auto_assign_shop_market()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_market uuid;v_dist numeric;
begin
 if new.latitude is null or new.longitude is null then
   if new.market_membership_status not in ('pending','approved') then
     new.market_suggested_id:=null;
     new.market_suggested_distance_km:=null;
     new.market_membership_status:='none';
   end if;
   return new;
 end if;

 -- A confirmed or approved choice must never be overwritten by proximity.
 if new.market_id is not null and new.market_membership_status in ('pending','approved') then
   return new;
 end if;

 select market_id,distance_km into v_market,v_dist
 from public.queuego_nearby_markets(new.latitude,new.longitude,10)
 limit 1;

 new.market_suggested_id:=v_market;
 new.market_suggested_distance_km:=v_dist;
 if new.market_membership_status not in ('pending','approved') then
   new.market_membership_status:=case when v_market is null then 'none' else 'suggested' end;
 end if;
 return new;
end $$;

create or replace function public.queuego_market_membership_write_guard()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_role text;v_radius numeric;v_mlat double precision;v_mlng double precision;v_dist numeric;
begin
 v_role:=public.get_my_role();

 if v_role='shop' then
   if new.market_membership_status in ('approved','rejected') then
     raise exception 'market membership review is admin-only';
   end if;
   if tg_op='INSERT' then
     if new.market_reviewed_by is not null
        or new.market_reviewed_at is not null
        or new.market_rejection_reason is not null then
       raise exception 'market review fields are admin-only';
     end if;
   elsif new.market_reviewed_by is distinct from old.market_reviewed_by
      or new.market_reviewed_at is distinct from old.market_reviewed_at
      or new.market_rejection_reason is distinct from old.market_rejection_reason then
     raise exception 'market review fields are admin-only';
   end if;

   if new.market_id is not null then
     if new.latitude is null or new.longitude is null then
       raise exception 'shop location required before choosing market';
     end if;
     select latitude,longitude,assignment_radius_km
       into v_mlat,v_mlng,v_radius
     from public.markets
     where id=new.market_id and active=true;
     if not found then raise exception 'market unavailable'; end if;
     v_dist:=public.queuego_market_distance_km(new.latitude,new.longitude,v_mlat,v_mlng);
     if v_dist>coalesce(v_radius,3) then
       raise exception 'shop pin is outside selected market area';
     end if;
     if new.market_confirmed_at is null then
       raise exception 'merchant confirmation required';
     end if;
     new.market_membership_status:='pending';
   end if;

   if tg_op='UPDATE'
      and old.market_membership_status='approved'
      and (new.latitude is distinct from old.latitude or new.longitude is distinct from old.longitude) then
     new.market_membership_status:='pending';
     new.market_reviewed_by:=null;
     new.market_reviewed_at:=null;
   end if;
 end if;
 return new;
end $$;

drop trigger if exists queuego_market_membership_write_guard_trg on public.shop_profiles;
create trigger queuego_market_membership_write_guard_trg
before insert or update of market_id,market_membership_status,market_reviewed_by,market_reviewed_at,market_rejection_reason,market_confirmed_at,latitude,longitude
on public.shop_profiles
for each row execute function public.queuego_market_membership_write_guard();

create or replace function public.queuego_submit_market_membership(
  p_market_id uuid,
  p_stall_no text default null,
  p_zone text default null,
  p_confirmed boolean default false,
  p_proof_path text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_user uuid;v_shop public.shop_profiles%rowtype;v_market public.markets%rowtype;v_dist numeric;
begin
 if auth.uid() is null then raise exception 'login required'; end if;
 select id into v_user from public.users
 where auth_user_id=auth.uid() and role='shop' and status in ('pending','active');
 if v_user is null then raise exception 'shop account required'; end if;
 if not coalesce(p_confirmed,false) then raise exception 'merchant confirmation required'; end if;

 select * into v_shop from public.shop_profiles where user_id=v_user for update;
 if not found then raise exception 'shop profile not found'; end if;
 if v_shop.latitude is null or v_shop.longitude is null then raise exception 'shop location required'; end if;

 select * into v_market from public.markets where id=p_market_id and active=true;
 if not found then raise exception 'market unavailable'; end if;
 v_dist:=public.queuego_market_distance_km(v_shop.latitude,v_shop.longitude,v_market.latitude,v_market.longitude);
 if v_dist>coalesce(v_market.assignment_radius_km,3) then raise exception 'shop pin is outside selected market area'; end if;

 update public.shop_profiles
 set market_id=p_market_id,
     market_suggested_id=p_market_id,
     market_suggested_distance_km=v_dist,
     market_membership_status='pending',
     market_confirmed_at=now(),
     market_stall_no=nullif(left(trim(coalesce(p_stall_no,'')),80),''),
     market_zone=nullif(left(trim(coalesce(p_zone,'')),80),''),
     market_proof_path=nullif(left(trim(coalesce(p_proof_path,'')),500),''),
     market_reviewed_by=null,
     market_reviewed_at=null,
     market_rejection_reason=null,
     updated_at=now()
 where id=v_shop.id;

 return jsonb_build_object(
   'shop_id',v_shop.id,
   'market_id',p_market_id,
   'market_name',v_market.name,
   'distance_km',round(v_dist,3),
   'status','pending'
 );
end $$;

create or replace function public.queuego_request_market(
  p_name text,
  p_province text,
  p_district text default null,
  p_subdistrict text default null,
  p_address text default null,
  p_lat double precision default null,
  p_lng double precision default null,
  p_note text default null
) returns uuid
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_user uuid;v_id uuid;
begin
 if auth.uid() is null then raise exception 'login required'; end if;
 select id into v_user from public.users
 where auth_user_id=auth.uid() and role='shop' and status in ('pending','active');
 if v_user is null then raise exception 'shop account required'; end if;
 if length(trim(coalesce(p_name,'')))<2 then raise exception 'market name required'; end if;
 if length(trim(coalesce(p_province,'')))<2 then raise exception 'province required'; end if;
 if p_lat is not null and abs(p_lat)>90 then raise exception 'invalid latitude'; end if;
 if p_lng is not null and abs(p_lng)>180 then raise exception 'invalid longitude'; end if;

 insert into public.market_requests(
   requester_shop_user_id,requested_name,province,district,subdistrict,address,latitude,longitude,note
 ) values(
   v_user,left(trim(p_name),160),left(trim(p_province),100),
   nullif(left(trim(coalesce(p_district,'')),100),''),
   nullif(left(trim(coalesce(p_subdistrict,'')),100),''),
   nullif(left(trim(coalesce(p_address,'')),500),''),
   p_lat,p_lng,nullif(left(trim(coalesce(p_note,'')),1000),'')
 ) returning id into v_id;
 return v_id;
end $$;

create or replace function public.queuego_admin_review_market_membership(
  p_shop_id uuid,
  p_approve boolean,
  p_reason text default null
) returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_admin uuid;v_shop public.shop_profiles%rowtype;
begin
 if not public.is_active_admin() then raise exception 'admin required'; end if;
 v_admin:=public.get_my_user_id();
 select * into v_shop from public.shop_profiles where id=p_shop_id for update;
 if not found then raise exception 'shop not found'; end if;
 if v_shop.market_membership_status<>'pending' or v_shop.market_id is null then
   raise exception 'market membership is not pending';
 end if;

 if coalesce(p_approve,false) then
   update public.shop_profiles
   set market_membership_status='approved',
       market_reviewed_by=v_admin,
       market_reviewed_at=now(),
       market_rejection_reason=null,
       updated_at=now()
   where id=p_shop_id;
   return 'approved';
 else
   update public.shop_profiles
   set market_membership_status='rejected',
       market_id=null,
       market_reviewed_by=v_admin,
       market_reviewed_at=now(),
       market_rejection_reason=nullif(left(trim(coalesce(p_reason,'')),500),''),
       updated_at=now()
   where id=p_shop_id;
   return 'rejected';
 end if;
end $$;

revoke all on function public.queuego_submit_market_membership(uuid,text,text,boolean,text) from public,anon;
revoke all on function public.queuego_request_market(text,text,text,text,text,double precision,double precision,text) from public,anon;
revoke all on function public.queuego_admin_review_market_membership(uuid,boolean,text) from public,anon;
grant execute on function public.queuego_submit_market_membership(uuid,text,text,boolean,text) to authenticated,service_role;
grant execute on function public.queuego_request_market(text,text,text,text,text,double precision,double precision,text) to authenticated,service_role;
grant execute on function public.queuego_admin_review_market_membership(uuid,boolean,text) to authenticated,service_role;

-- Market orders can only reference approved market members.
create or replace function public.qg_market_membership_order_guard()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
begin
 if new.market_order_id is not null and not exists(
   select 1 from public.shop_profiles s
   where s.id=new.shop_id
     and s.market_id is not null
     and s.market_membership_status='approved'
 ) then
   raise exception 'shop market membership is not approved';
 end if;
 return new;
end $$;

drop trigger if exists qg_market_membership_order_guard_trg on public.orders;
create trigger qg_market_membership_order_guard_trg
before insert or update of market_order_id,shop_id on public.orders
for each row execute function public.qg_market_membership_order_guard();

-- Public market lists now reflect physical approved market membership.
create or replace function public.market_public_shops()
returns table(
 shop_id uuid,shop_user_id uuid,shop_name text,shop_category text,shop_logo text,shop_cover text,
 latitude double precision,longitude double precision,open_time text,close_time text,description text,delivery_enabled boolean
)
language sql stable
set search_path to 'public','pg_temp'
as $$
 select s.id,s.user_id,s.shop_name,coalesce(s.public_category,'market'),coalesce(s.public_logo,''),
        coalesce(s.public_cover,''),s.latitude,s.longitude,s.public_open_time,s.public_close_time,
        coalesce(s.public_description,''),coalesce(s.delivery_enabled,false)
 from public.shop_profiles s join public.users u on u.id=s.user_id
 where s.status='active' and u.role='shop' and u.status='active'
   and s.market_id is not null and s.market_membership_status='approved'
 order by s.shop_name
$$;

create or replace function public.market_public_catalog()
returns table(
 product_id uuid,shop_id uuid,shop_user_id uuid,shop_name text,shop_category text,shop_logo text,
 name text,category text,description text,image text,price numeric,unit text,pack_size numeric,available_packs numeric,weight_kg numeric
)
language sql stable security definer
set search_path to 'public','pg_temp'
as $$
 select p.id,p.shop_id,s.user_id,s.shop_name,s.public_category,s.public_logo,
 p.name,p.category,p.description,p.image,coalesce(p.delivery_price,p.price),m.unit,m.pack_size,
 floor(m.stock_quantity/m.pack_size),m.item_weight_kg
 from public.market_products m
 join public.products p on p.id=m.product_id and p.shop_id=m.shop_id
 join public.shop_profiles s on s.id=m.shop_id
 join public.users u on u.id=s.user_id
 where s.status='active' and s.delivery_enabled=true and u.status='active'
 and s.market_id is not null and s.market_membership_status='approved'
 and s.latitude between 5 and 21 and s.longitude between 97 and 106
 and nullif(trim(coalesce(s.metadata->>'logo',s.metadata->>'profileImage',s.metadata->>'profile_image','')),'') is not null
 and nullif(trim(coalesce(s.metadata->>'cover','')),'') is not null
 and p.available and p.delivery_available and m.stock_quantity>=m.pack_size
 order by s.shop_name,p.name limit 1000
$$;

create or replace function public.market_public_shops_v2()
returns table(
 shop_id uuid,shop_user_id uuid,market_id uuid,market_name text,shop_name text,shop_category text,
 shop_logo text,shop_cover text,latitude double precision,longitude double precision,
 open_time text,close_time text,description text,delivery_enabled boolean
)
language sql stable security invoker
set search_path to 'public','pg_temp'
as $$
 select s.id,s.user_id,s.market_id,m.name,s.shop_name,coalesce(s.public_category,'market'),
        coalesce(s.public_logo,''),coalesce(s.public_cover,''),s.latitude,s.longitude,
        s.public_open_time,s.public_close_time,coalesce(s.public_description,''),coalesce(s.delivery_enabled,false)
 from public.shop_profiles s
 join public.users u on u.id=s.user_id
 join public.markets m on m.id=s.market_id and m.active=true
 where s.status='active' and u.role='shop' and u.status='active'
   and s.market_membership_status='approved'
 order by m.name,s.shop_name
$$;

create or replace function public.market_public_catalog_v2()
returns table(
 product_id uuid,shop_id uuid,market_id uuid,market_name text,shop_user_id uuid,shop_name text,shop_category text,
 shop_logo text,name text,category text,description text,image text,price numeric,unit text,pack_size numeric,available_packs numeric,weight_kg numeric
)
language sql stable security definer
set search_path to 'public','pg_temp'
as $$
 select p.id,p.shop_id,s.market_id,mkt.name,s.user_id,s.shop_name,s.public_category,s.public_logo,
 p.name,p.category,p.description,p.image,coalesce(p.delivery_price,p.price),mp.unit,mp.pack_size,
 floor(mp.stock_quantity/mp.pack_size),mp.item_weight_kg
 from public.market_products mp
 join public.products p on p.id=mp.product_id and p.shop_id=mp.shop_id
 join public.shop_profiles s on s.id=mp.shop_id
 join public.users u on u.id=s.user_id
 join public.markets mkt on mkt.id=s.market_id and mkt.active=true
 where s.status='active' and s.delivery_enabled=true and u.status='active'
   and s.market_membership_status='approved'
   and p.available and p.delivery_available and mp.stock_quantity>=mp.pack_size
 order by mkt.name,s.shop_name,p.name limit 2000
$$;

revoke all on function public.market_public_shops_v2() from public;
revoke all on function public.market_public_catalog_v2() from public;
grant execute on function public.market_public_shops_v2() to anon,authenticated,service_role;
grant execute on function public.market_public_catalog_v2() to anon,authenticated,service_role;
