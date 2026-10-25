-- Harden anonymous/authenticated read-only QueueGo RPCs without opening backing tables.
-- Privileged implementations live in a non-exposed schema; public functions are SECURITY INVOKER wrappers.
create schema if not exists qg_rpc_private;
revoke all on schema qg_rpc_private from public,anon,authenticated;
grant usage on schema qg_rpc_private to anon,authenticated,service_role;
alter default privileges in schema qg_rpc_private revoke execute on functions from public;

create or replace function qg_rpc_private.qg_search_places_impl(p_query text,p_latitude double precision default null,p_longitude double precision default null,p_category text default null,p_province text default null,p_limit integer default 20,p_radius_m integer default 50000)
returns table(place_id uuid,canonical_name text,name_th text,name_en text,aliases text[],category text,subcategory text,latitude double precision,longitude double precision,address text,village text,moo text,subdistrict text,district text,province text,postcode text,country text,verification_status text,confidence numeric,source text,source_license text,score real,distance_m double precision)
language plpgsql stable security definer set search_path=''
as $function$
declare v_query text:=regexp_replace(lower(btrim(coalesce(p_query,''))),'(จังหวัด|อำเภอ|เขต|ตำบล|แขวง)[[:space:]]*','','g');
begin
 if length(v_query)>200 then raise exception 'search query too long' using errcode='22023'; end if;
 if p_limit is null or p_limit<1 or p_limit>50 then raise exception 'invalid result limit' using errcode='22023'; end if;
 if p_radius_m is null or p_radius_m<100 or p_radius_m>100000 then raise exception 'invalid nearby radius' using errcode='22023'; end if;
 if (p_latitude is null)<>(p_longitude is null) or (p_latitude is not null and (p_latitude not between -90 and 90 or p_longitude not between -180 and 180)) then raise exception 'invalid nearby coordinates' using errcode='22023'; end if;
 if v_query='' and p_latitude is null then raise exception 'query or nearby coordinates required' using errcode='22023'; end if;
 return query
 with q as(select v_query term,p_category category_filter,p_province province_filter,case when p_latitude is null then null::extensions.geography else extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography end origin),
 ranked as(
  select p.place_id,p.canonical_name,p.name_th,p.name_en,p.aliases,p.category,p.subcategory,p.latitude,p.longitude,p.address,p.village,p.moo,p.subdistrict,p.district,p.province,p.postcode,p.country,p.verification_status,p.confidence,p.source,p.source_license,
   greatest(case when lower(p.canonical_name)=q.term or lower(coalesce(p.name_th,''))=q.term or lower(coalesce(p.name_en,''))=q.term then 1.0 else 0.0 end,
    case when left(lower(p.canonical_name),length(q.term))=q.term or left(lower(coalesce(p.name_th,'')),length(q.term))=q.term or left(lower(coalesce(p.name_en,'')),length(q.term))=q.term then 0.8 else 0.0 end,
    coalesce(extensions.similarity(lower(p.canonical_name),q.term),0.0),coalesce(extensions.similarity(lower(coalesce(p.name_th,'')),q.term),0.0),coalesce(extensions.similarity(lower(coalesce(p.name_en,'')),q.term),0.0),coalesce(max(extensions.similarity(a.normalized_alias,q.term)),0.0),coalesce((select max(extensions.similarity(lower(pa.alias),q.term)) from unnest(p.aliases) as pa(alias)),0.0),case when replace(lower(concat_ws(' ',p.canonical_name,p.name_th,p.name_en,array_to_string(p.aliases,' '),p.address,p.village,p.moo,p.subdistrict,p.district,p.province,p.postcode)),' ','') like '%'||replace(q.term,' ','')||'%' then 0.65 else 0.0 end)::real score,
   case when q.origin is null or p.location is null then null else extensions.st_distance(p.location,q.origin) end distance_m,q.term
  from public.queuego_places p cross join q left join public.queuego_place_aliases a on a.place_id=p.place_id
  join public.queuego_place_sources s on s.source_key=p.source and s.enabled and s.allowed_storage and s.allowed_redistribution
  where p.country_code='TH' and p.verification_status<>'rejected'
   and (q.category_filter is null or p.category=q.category_filter)
   and (q.province_filter is null or lower(coalesce(p.province,''))=lower(q.province_filter))
   and (q.term<>'' or (p.location is not null and extensions.st_dwithin(p.location,q.origin,p_radius_m)))
   and (q.term='' or lower(p.canonical_name)=q.term or lower(coalesce(p.name_th,''))=q.term or lower(coalesce(p.name_en,''))=q.term
    or left(lower(p.canonical_name),length(q.term))=q.term or left(lower(coalesce(p.name_th,'')),length(q.term))=q.term or left(lower(coalesce(p.name_en,'')),length(q.term))=q.term
    or extensions.similarity(lower(p.canonical_name),q.term)>=0.22 or extensions.similarity(lower(coalesce(p.name_th,'')),q.term)>=0.22 or extensions.similarity(lower(coalesce(p.name_en,'')),q.term)>=0.22
    or left(a.normalized_alias,length(q.term))=q.term or extensions.similarity(a.normalized_alias,q.term)>=0.22
    or exists(select 1 from unnest(p.aliases) as pa(alias) where left(lower(pa.alias),length(q.term))=q.term or extensions.similarity(lower(pa.alias),q.term)>=0.22)
    or replace(lower(concat_ws(' ',p.canonical_name,p.name_th,p.name_en,array_to_string(p.aliases,' '),p.address,p.village,p.moo,p.subdistrict,p.district,p.province,p.postcode)),' ','') like '%'||replace(q.term,' ','')||'%')
  group by p.place_id,p.canonical_name,p.name_th,p.name_en,p.aliases,p.category,p.subcategory,p.latitude,p.longitude,p.address,p.village,p.moo,p.subdistrict,p.district,p.province,p.postcode,p.country,p.verification_status,p.confidence,p.source,p.source_license,p.location,q.origin,q.term,q.category_filter,q.province_filter
 )
 select r.place_id,r.canonical_name,r.name_th,r.name_en,r.aliases,r.category,r.subcategory,r.latitude,r.longitude,r.address,r.village,r.moo,r.subdistrict,r.district,r.province,r.postcode,r.country,r.verification_status,r.confidence,r.source,r.source_license,r.score,r.distance_m
 from ranked r order by case when r.term<>'' and r.score>=1 then 0 when r.term<>'' and r.score>=0.8 then 1 else 2 end,r.score desc,case when r.term<>'' and lower(r.canonical_name)=r.term and r.category='administrative_area' then case r.subcategory when 'province' then 0 when 'district' then 1 when 'subdistrict' then 2 else 3 end else 3 end,r.distance_m asc nulls last,r.canonical_name asc limit p_limit;
end;
$function$;
revoke all on function qg_rpc_private.qg_search_places_impl(text,double precision,double precision,text,text,integer,integer) from public,anon,authenticated;
grant execute on function qg_rpc_private.qg_search_places_impl(text,double precision,double precision,text,text,integer,integer) to anon,authenticated,service_role;

create or replace function public.qg_search_places(
 p_query text,p_latitude double precision default null,p_longitude double precision default null,
 p_category text default null,p_province text default null,p_limit integer default 20,p_radius_m integer default 50000
) returns table(place_id uuid,canonical_name text,name_th text,name_en text,aliases text[],category text,subcategory text,latitude double precision,longitude double precision,address text,village text,moo text,subdistrict text,district text,province text,postcode text,country text,verification_status text,confidence numeric,source text,source_license text,score real,distance_m double precision)
language sql stable security invoker set search_path=''
as $function$
 select * from qg_rpc_private.qg_search_places_impl(p_query,p_latitude,p_longitude,p_category,p_province,p_limit,p_radius_m);
$function$;
revoke all on function public.qg_search_places(text,double precision,double precision,text,text,integer,integer) from public,anon,authenticated;
grant execute on function public.qg_search_places(text,double precision,double precision,text,text,integer,integer) to anon,authenticated;

create or replace function qg_rpc_private.queuego_order_service_route_status_v2_impl(
 p_pickup_lat double precision,p_pickup_lng double precision,
 p_delivery_lat double precision,p_delivery_lng double precision,
 p_vertical text default 'delivery'
) returns jsonb
language plpgsql stable security definer set search_path=''
as $function$
declare
 v_vertical text:=coalesce(nullif(lower(btrim(p_vertical)),''),'delivery');
 v_direct numeric;
 v_rule record;
begin
 if p_pickup_lat is null or p_pickup_lng is null or p_delivery_lat is null or p_delivery_lng is null
 or p_pickup_lat not between -90 and 90 or p_delivery_lat not between -90 and 90
 or p_pickup_lng not between -180 and 180 or p_delivery_lng not between -180 and 180
 or (p_pickup_lat=0 and p_pickup_lng=0) or (p_delivery_lat=0 and p_delivery_lng=0)
 then return jsonb_build_object('available',false,'capability','ORDERING_AVAILABLE','code','INVALID_LOCATION'); end if;

 v_direct:=extensions.st_distance(
  extensions.st_setsrid(extensions.st_makepoint(p_pickup_lng,p_pickup_lat),4326)::extensions.geography,
  extensions.st_setsrid(extensions.st_makepoint(p_delivery_lng,p_delivery_lat),4326)::extensions.geography
 )/1000.0;

 select r.rule_id,r.name,r.mode,r.max_direct_delivery_km,r.revision,r.specificity
 into v_rule
 from public.queuego_order_service_area_rules r
 left join public.queuego_places p on p.place_id=r.admin_place_id
 cross join lateral (select coalesce(r.custom_boundary,p.boundary) as boundary) b
 where r.active and r.country_code='TH'
  and r.effective_from<=now() and (r.effective_to is null or r.effective_to>now())
  and (v_vertical=any(r.verticals) or 'delivery'=any(r.verticals))
  and b.boundary is not null
  and extensions.st_covers(
    b.boundary::extensions.geometry,
    extensions.st_setsrid(extensions.st_makepoint(p_pickup_lng,p_pickup_lat),4326)
  )
  and extensions.st_covers(
    b.boundary::extensions.geometry,
    extensions.st_setsrid(extensions.st_makepoint(p_delivery_lng,p_delivery_lat),4326)
  )
 order by r.specificity desc,case r.mode when 'deny' then 0 else 1 end,r.revision desc,r.created_at desc
 limit 1;

 if v_rule.rule_id is null then
  return jsonb_build_object('available',false,'capability','ORDERING_AVAILABLE','code','OUTSIDE_SERVICE_AREA','distance_km',round(v_direct,3));
 end if;
 if v_rule.mode='deny' then
  return jsonb_build_object('available',false,'capability','ORDERING_AVAILABLE','code','SERVICE_AREA_DENIED','rule_id',v_rule.rule_id,'rule_name',v_rule.name,'rule_revision',v_rule.revision,'distance_km',round(v_direct,3));
 end if;
 if v_direct is null or v_direct>v_rule.max_direct_delivery_km then
  return jsonb_build_object('available',false,'capability','ORDERING_AVAILABLE','code','DELIVERY_DISTANCE_EXCEEDED','rule_id',v_rule.rule_id,'rule_name',v_rule.name,'rule_revision',v_rule.revision,'distance_km',round(v_direct,3),'max_delivery_km',v_rule.max_direct_delivery_km);
 end if;
 return jsonb_build_object('available',true,'capability','ORDERING_AVAILABLE','code','OK','rule_id',v_rule.rule_id,'rule_name',v_rule.name,'rule_revision',v_rule.revision,'distance_km',round(v_direct,3),'max_delivery_km',v_rule.max_direct_delivery_km);
end;
$function$;
revoke all on function qg_rpc_private.queuego_order_service_route_status_v2_impl(double precision,double precision,double precision,double precision,text) from public,anon,authenticated;
grant execute on function qg_rpc_private.queuego_order_service_route_status_v2_impl(double precision,double precision,double precision,double precision,text) to anon,authenticated,service_role;

create or replace function public.queuego_order_service_route_status_v2(
 p_pickup_lat double precision,p_pickup_lng double precision,
 p_delivery_lat double precision,p_delivery_lng double precision,p_vertical text default 'delivery'
) returns jsonb
language sql stable security invoker set search_path=''
as $function$
 select qg_rpc_private.queuego_order_service_route_status_v2_impl(p_pickup_lat,p_pickup_lng,p_delivery_lat,p_delivery_lng,p_vertical);
$function$;
revoke all on function public.queuego_order_service_route_status_v2(double precision,double precision,double precision,double precision,text) from public,anon,authenticated;
grant execute on function public.queuego_order_service_route_status_v2(double precision,double precision,double precision,double precision,text) to anon,authenticated,service_role;

create or replace function qg_rpc_private.queuego_country_capability_impl(p_country_code text default 'TH')
returns jsonb language sql stable security definer set search_path=''
as $function$
 select coalesce(
  (select jsonb_build_object(
    'country_code',c.country_code,'map_available',c.map_available and c.active,
    'registration_available',c.registration_available and c.active,'revision',c.revision
   ) from public.queuego_country_capabilities c where c.country_code=upper(btrim(p_country_code))),
  jsonb_build_object('country_code',upper(btrim(p_country_code)),'map_available',false,'registration_available',false,'revision',null)
 );
$function$;
revoke all on function qg_rpc_private.queuego_country_capability_impl(text) from public,anon,authenticated;
grant execute on function qg_rpc_private.queuego_country_capability_impl(text) to anon,authenticated,service_role;

create or replace function public.queuego_country_capability(p_country_code text default 'TH')
returns jsonb language sql stable security invoker set search_path=''
as $function$
 select qg_rpc_private.queuego_country_capability_impl(p_country_code);
$function$;
revoke all on function public.queuego_country_capability(text) from public,anon,authenticated;
grant execute on function public.queuego_country_capability(text) to anon,authenticated,service_role;
