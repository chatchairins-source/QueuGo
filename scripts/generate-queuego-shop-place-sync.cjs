#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');

function renderQueueGoOperationalPlaceSync() {
  return `-- QueueGo Places verified operational runtime sync.
-- REVIEW-ONLY SOURCE: generate through the approved Supabase migration workflow before applying.
-- Requires QueueGo Places foundation + provenance unique-index migrations first.
-- One private upsert/retire authority is shared by Shop/Market triggers and bootstrap snapshots.
create schema if not exists qg_private;
revoke all on schema qg_private from public,anon,authenticated;

create or replace function qg_private.queuego_upsert_operational_place(
  p_source_record_id text,
  p_canonical_name text,
  p_category text,
  p_subcategory text,
  p_latitude double precision,
  p_longitude double precision,
  p_address text,
  p_subdistrict text,
  p_district text,
  p_province text,
  p_postcode text,
  p_source_updated_at timestamptz,
  p_verification_method text,
  p_source_payload_hash text
)
returns uuid
language plpgsql
security definer
set search_path=''
as $function$
declare
  v_place_id uuid;
  v_dedupe_key text:='queuego:'||p_source_record_id;
begin
  if p_source_record_id !~ '^(shop|market):[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
    raise exception 'invalid QueueGo operational source identity' using errcode='22023';
  end if;
  if nullif(btrim(p_canonical_name),'') is null or nullif(btrim(p_category),'') is null
     or nullif(btrim(p_subcategory),'') is null or nullif(btrim(p_verification_method),'') is null then
    raise exception 'incomplete QueueGo operational place' using errcode='22023';
  end if;
  if p_latitude is null or p_longitude is null
     or p_latitude not between 5 and 21
     or p_longitude not between 97 and 106 then
    raise exception 'QueueGo operational place requires valid Thailand coordinates' using errcode='22023';
  end if;

  insert into public.queuego_places(
    canonical_name,name_th,name_en,aliases,category,subcategory,
    latitude,longitude,address,village,moo,subdistrict,district,province,postcode,
    country,country_code,source,source_id,source_license,source_updated_at,
    verification_status,verification_method,confidence,dedupe_key,
    parent_place_id,admin_level,admin_code
  ) values (
    btrim(p_canonical_name),btrim(p_canonical_name),null,'{}'::text[],
    btrim(p_category),btrim(p_subcategory),p_latitude,p_longitude,
    nullif(btrim(coalesce(p_address,'')),''),
    null,null,
    nullif(btrim(coalesce(p_subdistrict,'')),''),
    nullif(btrim(coalesce(p_district,'')),''),
    nullif(btrim(coalesce(p_province,'')),''),
    nullif(btrim(coalesce(p_postcode,'')),''),
    'Thailand','TH','queuego_verified',p_source_record_id,'QueueGo-Internal',
    coalesce(p_source_updated_at,now()),'verified',btrim(p_verification_method),1.000,
    v_dedupe_key,null,null,null
  )
  on conflict(dedupe_key) where dedupe_key is not null do update set
    canonical_name=excluded.canonical_name,
    name_th=excluded.name_th,
    category=excluded.category,
    subcategory=excluded.subcategory,
    latitude=excluded.latitude,
    longitude=excluded.longitude,
    address=excluded.address,
    subdistrict=excluded.subdistrict,
    district=excluded.district,
    province=excluded.province,
    postcode=excluded.postcode,
    source='queuego_verified',
    source_id=excluded.source_id,
    source_license='QueueGo-Internal',
    source_updated_at=excluded.source_updated_at,
    verification_status='verified',
    verification_method=excluded.verification_method,
    confidence=1.000,
    updated_at=now()
  returning place_id into v_place_id;

  insert into public.queuego_place_source_records(
    source,source_record_id,place_id,source_license,source_updated_at,source_payload_hash
  ) values (
    'queuego_verified',p_source_record_id,v_place_id,'QueueGo-Internal',
    coalesce(p_source_updated_at,now()),p_source_payload_hash
  )
  on conflict(source,source_record_id) do update set
    place_id=excluded.place_id,
    source_license=excluded.source_license,
    source_updated_at=excluded.source_updated_at,
    source_payload_hash=coalesce(excluded.source_payload_hash,public.queuego_place_source_records.source_payload_hash),
    imported_at=now();

  insert into public.queuego_place_aliases(place_id,alias,language_code,source)
  values(v_place_id,btrim(p_canonical_name),'th','queuego_verified')
  on conflict(place_id,normalized_alias) do update set
    language_code=excluded.language_code,
    source=excluded.source;

  insert into public.queuego_place_field_provenance(
    place_id,field_name,source,source_record_id,observed_at,confidence,raw_value
  )
  select v_place_id,v.field_name,'queuego_verified',p_source_record_id,
         coalesce(p_source_updated_at,now()),1.000,v.raw_value
  from (values
    ('canonical_name',to_jsonb(btrim(p_canonical_name))),
    ('name_th',to_jsonb(btrim(p_canonical_name))),
    ('category',to_jsonb(btrim(p_category))),
    ('subcategory',to_jsonb(btrim(p_subcategory))),
    ('latitude',to_jsonb(p_latitude)),
    ('longitude',to_jsonb(p_longitude)),
    ('address',to_jsonb(nullif(btrim(coalesce(p_address,'')),''))),
    ('subdistrict',to_jsonb(nullif(btrim(coalesce(p_subdistrict,'')),''))),
    ('district',to_jsonb(nullif(btrim(coalesce(p_district,'')),''))),
    ('province',to_jsonb(nullif(btrim(coalesce(p_province,'')),''))),
    ('postcode',to_jsonb(nullif(btrim(coalesce(p_postcode,'')),''))),
    ('source_id',to_jsonb(p_source_record_id)),
    ('source_license',to_jsonb('QueueGo-Internal'::text)),
    ('verification_status',to_jsonb('verified'::text)),
    ('verification_method',to_jsonb(btrim(p_verification_method)))
  ) as v(field_name,raw_value)
  on conflict(place_id,field_name,source,source_record_id)
    where source_record_id is not null
  do update set
    observed_at=excluded.observed_at,
    confidence=excluded.confidence,
    raw_value=excluded.raw_value;

  return v_place_id;
end;
$function$;

revoke all on function qg_private.queuego_upsert_operational_place(
  text,text,text,text,double precision,double precision,text,text,text,text,text,timestamptz,text,text
) from public,anon,authenticated;

create or replace function qg_private.queuego_retire_operational_place(
  p_source_record_id text,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path=''
as $function$
begin
  if p_source_record_id !~ '^(shop|market):[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
    raise exception 'invalid QueueGo operational source identity' using errcode='22023';
  end if;
  update public.queuego_places
     set verification_status='rejected',
         verification_method=coalesce(nullif(btrim(p_reason),''),'queuego_operational_source_inactive'),
         updated_at=now()
   where source='queuego_verified'
     and source_id=p_source_record_id
     and dedupe_key='queuego:'||p_source_record_id;
end;
$function$;

revoke all on function qg_private.queuego_retire_operational_place(text,text)
from public,anon,authenticated;

create or replace function qg_private.queuego_sync_shop_place(p_shop_id uuid)
returns void
language plpgsql
security definer
set search_path=''
as $function$
declare
  s public.shop_profiles%rowtype;
  v_source_id text:='shop:'||p_shop_id::text;
  v_public_category text;
  v_category text;
  v_subcategory text;
begin
  select * into s from public.shop_profiles where id=p_shop_id;

  if not found
     or s.status <> 'active'
     or s.onboarding_status <> 'approved'
     or s.archived_at is not null
     or s.latitude is null
     or s.longitude is null
     or s.latitude not between 5 and 21
     or s.longitude not between 97 and 106 then
    perform qg_private.queuego_retire_operational_place(v_source_id,'queuego_shop_inactive_or_unmappable');
    return;
  end if;

  v_public_category:=coalesce(nullif(lower(btrim(s.public_category)),''),'other');
  if v_public_category='market' and s.market_membership_status <> 'approved' then
    perform qg_private.queuego_retire_operational_place(v_source_id,'queuego_market_shop_unapproved');
    return;
  end if;

  v_category:=case v_public_category
    when 'food' then 'food'
    when 'cafe' then 'food'
    when 'grocery' then 'grocery'
    when 'laundry' then 'laundry'
    when 'market' then 'market'
    when 'shopping' then 'shopping'
    else 'place'
  end;
  v_subcategory:=case v_public_category
    when 'food' then 'restaurant'
    when 'cafe' then 'cafe'
    when 'grocery' then 'grocery'
    when 'laundry' then 'laundry'
    when 'market' then 'fresh_market_shop'
    when 'shopping' then 'shopping'
    else 'shop'
  end;

  perform qg_private.queuego_upsert_operational_place(
    v_source_id,btrim(s.shop_name),v_category,v_subcategory,
    s.latitude,s.longitude,s.address,null,null,null,null,s.updated_at,
    'queuego_active_shop_profile',null
  );
end;
$function$;

revoke all on function qg_private.queuego_sync_shop_place(uuid)
from public,anon,authenticated;

create or replace function qg_private.queuego_sync_market_place(p_market_id uuid)
returns void
language plpgsql
security definer
set search_path=''
as $function$
declare
  m public.markets%rowtype;
  v_source_id text:='market:'||p_market_id::text;
begin
  select * into m from public.markets where id=p_market_id;

  if not found
     or m.active is not true
     or m.verified is not true
     or m.latitude is null
     or m.longitude is null
     or m.latitude not between 5 and 21
     or m.longitude not between 97 and 106 then
    perform qg_private.queuego_retire_operational_place(v_source_id,'queuego_market_inactive_or_unmappable');
    return;
  end if;

  perform qg_private.queuego_upsert_operational_place(
    v_source_id,btrim(m.name),'market','fresh_market',
    m.latitude,m.longitude,m.address,m.subdistrict,m.district,m.province,null,m.updated_at,
    'queuego_verified_market',null
  );
end;
$function$;

revoke all on function qg_private.queuego_sync_market_place(uuid)
from public,anon,authenticated;

create or replace function qg_private.queuego_sync_shop_place_trg()
returns trigger
language plpgsql
security definer
set search_path=''
as $function$
begin
  perform qg_private.queuego_sync_shop_place(case when tg_op='DELETE' then old.id else new.id end);
  return case when tg_op='DELETE' then old else new end;
end;
$function$;
revoke all on function qg_private.queuego_sync_shop_place_trg()
from public,anon,authenticated;

create or replace function qg_private.queuego_sync_market_place_trg()
returns trigger
language plpgsql
security definer
set search_path=''
as $function$
begin
  perform qg_private.queuego_sync_market_place(case when tg_op='DELETE' then old.id else new.id end);
  return case when tg_op='DELETE' then old else new end;
end;
$function$;
revoke all on function qg_private.queuego_sync_market_place_trg()
from public,anon,authenticated;

drop trigger if exists queuego_shop_place_sync_trg on public.shop_profiles;
create trigger queuego_shop_place_sync_trg
after insert or delete or update of
  shop_name,address,latitude,longitude,status,onboarding_status,archived_at,
  public_category,public_subcategories,market_membership_status
on public.shop_profiles
for each row execute function qg_private.queuego_sync_shop_place_trg();

drop trigger if exists queuego_market_place_sync_trg on public.markets;
create trigger queuego_market_place_sync_trg
after insert or delete or update of
  name,address,latitude,longitude,active,verified,subdistrict,district,province
on public.markets
for each row execute function qg_private.queuego_sync_market_place_trg();

-- Backfill through the exact same runtime authority used by future writes.
select qg_private.queuego_sync_shop_place(id) from public.shop_profiles;
select qg_private.queuego_sync_market_place(id) from public.markets;
`;
}

if (require.main === module) {
  const [output] = process.argv.slice(2);
  const sql = renderQueueGoOperationalPlaceSync();
  if (output) {
    fs.writeFileSync(path.resolve(output), sql, 'utf8');
    console.error('wrote review-only QueueGo operational Places runtime sync SQL');
  } else {
    process.stdout.write(sql);
  }
}

module.exports = { renderQueueGoOperationalPlaceSync };
