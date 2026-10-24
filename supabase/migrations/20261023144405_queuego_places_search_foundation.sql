-- QueueGo Places: nationwide, source-aware place authority and shared search RPC.
-- This schema migration intentionally imports no operational place data.
create schema if not exists extensions;
create extension if not exists postgis with schema extensions;
create extension if not exists pg_trgm with schema extensions;

create table if not exists public.queuego_place_sources (
  source_key text primary key check (source_key ~ '^[a-z][a-z0-9_]{1,63}$'),
  source_name text not null, license_id text not null, license_url text,
  attribution_requirement text not null, update_policy text not null,
  allowed_storage boolean not null, allowed_redistribution boolean not null,
  enabled boolean not null default true, metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  constraint queuego_place_source_redistribution_requires_storage check(not allowed_redistribution or allowed_storage)
);
insert into public.queuego_place_sources(source_key,source_name,license_id,license_url,attribution_requirement,update_policy,allowed_storage,allowed_redistribution,metadata) values
 ('openstreetmap','OpenStreetMap contributors','ODbL-1.0','https://www.openstreetmap.org/copyright','Credit OpenStreetMap contributors and provide the ODbL notice/link with database distributions and map outputs.','Refresh from a pinned regional extract; record extract timestamp and checksum for every import.',true,true,'{"attribution":"© OpenStreetMap contributors","license_url":"https://www.openstreetmap.org/copyright"}'::jsonb),
 ('queuego_verified','QueueGo verified operational records','QueueGo-Internal',null,'No external attribution; retain internal submitter and verification audit.','Update on verified registration or authorized operational correction.',true,true,'{"purpose":"public QueueGo place search only after verification and submitter authorization"}'::jsonb)
on conflict(source_key) do update set source_name=excluded.source_name,license_id=excluded.license_id,license_url=excluded.license_url,attribution_requirement=excluded.attribution_requirement,update_policy=excluded.update_policy,allowed_storage=excluded.allowed_storage,allowed_redistribution=excluded.allowed_redistribution,metadata=excluded.metadata,updated_at=now();

create table if not exists public.queuego_places (
 place_id uuid primary key default gen_random_uuid(),
 canonical_name text not null check(length(btrim(canonical_name)) between 1 and 240), name_th text, name_en text,
 aliases text[] not null default '{}'::text[], category text not null default 'place', subcategory text,
 latitude double precision, longitude double precision,
 location extensions.geography(point,4326) generated always as(case when latitude is null then null else extensions.st_setsrid(extensions.st_makepoint(longitude,latitude),4326)::extensions.geography end) stored,
 address text, village text, moo text, subdistrict text, district text, province text, postcode text,
 country text not null default 'Thailand' check(country='Thailand'), country_code text not null default 'TH' check(country_code='TH'),
 source text not null references public.queuego_place_sources(source_key), source_id text, source_license text not null, source_updated_at timestamptz,
 verification_status text not null default 'unverified' check(verification_status in('unverified','verified','needs_review','rejected')),
 verification_method text, confidence numeric(4,3) check(confidence is null or confidence between 0 and 1), dedupe_key text,
 parent_place_id uuid references public.queuego_places(place_id) on delete set null,
 admin_level text check(admin_level is null or admin_level in('country','province','district','subdistrict','village')), admin_code text,
 boundary extensions.geography(multipolygon,4326), created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 constraint queuego_places_lat_lng_pair check((latitude is null)=(longitude is null)),
 constraint queuego_places_latitude_range check(latitude is null or latitude between -90 and 90),
 constraint queuego_places_longitude_range check(longitude is null or longitude between -180 and 180),
 constraint queuego_places_source_id_pair check(source_id is null or length(btrim(source_id))>0),
 constraint queuego_places_admin_code_level check((admin_code is null)=(admin_level is null))
);
create unique index if not exists queuego_places_dedupe_key_uidx on public.queuego_places(dedupe_key) where dedupe_key is not null;
create unique index if not exists queuego_places_admin_code_uidx on public.queuego_places(country,admin_code) where admin_code is not null;
create index if not exists queuego_places_location_gix on public.queuego_places using gist(location);
create index if not exists queuego_places_boundary_gix on public.queuego_places using gist(boundary) where boundary is not null;
create index if not exists queuego_places_name_prefix_idx on public.queuego_places(country,lower(canonical_name) text_pattern_ops);
create index if not exists queuego_places_name_trgm_idx on public.queuego_places using gin(lower(canonical_name) extensions.gin_trgm_ops);
create index if not exists queuego_places_admin_path_idx on public.queuego_places(country,province,district,subdistrict,village);
create index if not exists queuego_places_parent_idx on public.queuego_places(parent_place_id) where parent_place_id is not null;

create table if not exists public.queuego_place_aliases (
 alias_id bigint generated always as identity primary key, place_id uuid not null references public.queuego_places(place_id) on delete cascade,
 alias text not null check(length(btrim(alias)) between 1 and 240), normalized_alias text generated always as(lower(btrim(alias))) stored,
 language_code text, source text not null references public.queuego_place_sources(source_key), created_at timestamptz not null default now(),
 unique(place_id,normalized_alias)
);
create index if not exists queuego_place_alias_prefix_idx on public.queuego_place_aliases(normalized_alias text_pattern_ops);
create index if not exists queuego_place_alias_trgm_idx on public.queuego_place_aliases using gin(normalized_alias extensions.gin_trgm_ops);
create table if not exists public.queuego_place_source_records (
 source text not null references public.queuego_place_sources(source_key), source_record_id text not null,
 place_id uuid not null references public.queuego_places(place_id) on delete cascade, source_license text not null,
 source_updated_at timestamptz, imported_at timestamptz not null default now(), source_payload_hash text,
 primary key(source,source_record_id)
);
create index if not exists queuego_place_source_records_place_idx on public.queuego_place_source_records(place_id);
create table if not exists public.queuego_place_field_provenance (
 place_id uuid not null references public.queuego_places(place_id) on delete cascade,
 field_name text not null check(field_name ~ '^[a-z][a-z0-9_]{0,63}$'), source text not null references public.queuego_place_sources(source_key),
 source_record_id text, observed_at timestamptz not null default now(), confidence numeric(4,3) check(confidence is null or confidence between 0 and 1), raw_value jsonb,
 primary key(place_id,field_name,source,observed_at)
);
create index if not exists queuego_place_field_provenance_source_idx on public.queuego_place_field_provenance(source,source_record_id);

alter table public.queuego_place_sources enable row level security;
alter table public.queuego_places enable row level security;
alter table public.queuego_place_aliases enable row level security;
alter table public.queuego_place_source_records enable row level security;
alter table public.queuego_place_field_provenance enable row level security;
revoke all on public.queuego_place_sources,public.queuego_places,public.queuego_place_aliases,public.queuego_place_source_records,public.queuego_place_field_provenance from anon,authenticated;

create or replace function public.qg_search_places(p_query text,p_latitude double precision default null,p_longitude double precision default null,p_category text default null,p_province text default null,p_limit integer default 20,p_radius_m integer default 50000)
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
 from ranked r order by case when r.term<>'' and r.score>=1 then 0 when r.term<>'' and r.score>=0.8 then 1 else 2 end,r.score desc,r.distance_m asc nulls last,r.canonical_name asc limit p_limit;
end;
$function$;
revoke all on function public.qg_search_places(text,double precision,double precision,text,text,integer,integer) from public,anon,authenticated;
grant execute on function public.qg_search_places(text,double precision,double precision,text,text,integer,integer) to anon,authenticated;
