#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');

function renderQueueGoVerifiedShopSync() {
  return `-- QueueGo Places operational shop sync.
-- REVIEW-ONLY SOURCE: generate through the approved Supabase migration workflow before applying.
-- Requires QueueGo Places foundation + source provenance migrations to be installed first.
create schema if not exists qg_private;
revoke all on schema qg_private from public;

create or replace function qg_private.queuego_sync_shop_place(p_shop_id uuid)
returns void
language plpgsql
security definer
set search_path=''
as $function$
declare
  s public.shop_profiles%rowtype;
  v_place_id uuid;
  v_category text;
begin
  select * into s from public.shop_profiles where id=p_shop_id;

  if not found
     or s.status <> 'active'
     or s.archived_at is not null
     or s.latitude is null
     or s.longitude is null
     or s.latitude not between -90 and 90
     or s.longitude not between -180 and 180 then
    update public.queuego_places
       set verification_status='rejected',
           verification_method='shop_profile_inactive',
           updated_at=now()
     where source='queuego_verified'
       and dedupe_key='queuego-shop:'||p_shop_id::text;
    return;
  end if;

  v_category:=coalesce(nullif(lower(btrim(s.public_category)),''),'shop');

  insert into public.queuego_places(
    canonical_name,name_th,name_en,aliases,category,subcategory,
    latitude,longitude,address,village,moo,subdistrict,district,province,postcode,
    country,country_code,source,source_id,source_license,source_updated_at,
    verification_status,verification_method,confidence,dedupe_key,
    parent_place_id,admin_level,admin_code
  ) values (
    btrim(s.shop_name),btrim(s.shop_name),null,'{}'::text[],v_category,
    case when coalesce(array_length(s.public_subcategories,1),0)>0 then s.public_subcategories[1] else null end,
    s.latitude,s.longitude,nullif(btrim(coalesce(s.address,'')),''),
    null,null,null,null,null,null,
    'Thailand','TH','queuego_verified',s.id::text,'QueueGo-Internal',s.updated_at,
    'verified','verified_shop_profile',1.000,'queuego-shop:'||s.id::text,
    null,null,null
  )
  on conflict(dedupe_key) where dedupe_key is not null do update set
    canonical_name=excluded.canonical_name,
    name_th=excluded.name_th,
    category=excluded.category,
    subcategory=excluded.subcategory,
    latitude=excluded.latitude,
    longitude=excluded.longitude,
    address=excluded.address,
    source=excluded.source,
    source_id=excluded.source_id,
    source_license=excluded.source_license,
    source_updated_at=excluded.source_updated_at,
    verification_status='verified',
    verification_method='verified_shop_profile',
    confidence=1.000,
    updated_at=now()
  returning place_id into v_place_id;

  insert into public.queuego_place_source_records(
    source,source_record_id,place_id,source_license,source_updated_at,source_payload_hash
  ) values (
    'queuego_verified',s.id::text,v_place_id,'QueueGo-Internal',s.updated_at,null
  )
  on conflict(source,source_record_id) do update set
    place_id=excluded.place_id,
    source_license=excluded.source_license,
    source_updated_at=excluded.source_updated_at,
    imported_at=now();

  insert into public.queuego_place_aliases(place_id,alias,language_code,source)
  values(v_place_id,btrim(s.shop_name),'th','queuego_verified')
  on conflict(place_id,normalized_alias) do update set
    language_code=excluded.language_code,
    source=excluded.source;

  insert into public.queuego_place_field_provenance(
    place_id,field_name,source,source_record_id,observed_at,confidence,raw_value
  )
  select v_place_id,v.field_name,'queuego_verified',s.id::text,
         coalesce(s.updated_at,now()),1.000,v.raw_value
  from (values
    ('canonical_name',to_jsonb(btrim(s.shop_name))),
    ('name_th',to_jsonb(btrim(s.shop_name))),
    ('category',to_jsonb(v_category)),
    ('subcategory',to_jsonb(case when coalesce(array_length(s.public_subcategories,1),0)>0 then s.public_subcategories[1] else null end)),
    ('latitude',to_jsonb(s.latitude)),
    ('longitude',to_jsonb(s.longitude)),
    ('address',to_jsonb(nullif(btrim(coalesce(s.address,'')),''))),
    ('source_id',to_jsonb(s.id::text)),
    ('verification_status',to_jsonb('verified'::text)),
    ('verification_method',to_jsonb('verified_shop_profile'::text))
  ) as v(field_name,raw_value)
  on conflict(place_id,field_name,source,source_record_id)
    where source_record_id is not null
  do update set
    observed_at=excluded.observed_at,
    confidence=excluded.confidence,
    raw_value=excluded.raw_value;
end;
$function$;

revoke all on function qg_private.queuego_sync_shop_place(uuid) from public,anon,authenticated;

create or replace function qg_private.queuego_sync_shop_place_trg()
returns trigger
language plpgsql
security definer
set search_path=''
as $function$
declare
  v_shop_id uuid;
begin
  if tg_op='DELETE' then
    v_shop_id:=old.id;
  else
    v_shop_id:=new.id;
  end if;

  perform qg_private.queuego_sync_shop_place(v_shop_id);

  if tg_op='DELETE' then
    return old;
  end if;
  return new;
end;
$function$;

revoke all on function qg_private.queuego_sync_shop_place_trg() from public,anon,authenticated;

drop trigger if exists queuego_shop_place_sync_trg on public.shop_profiles;
create trigger queuego_shop_place_sync_trg
after insert or delete or update of
  shop_name,address,latitude,longitude,status,archived_at,public_category,public_subcategories
on public.shop_profiles
for each row execute function qg_private.queuego_sync_shop_place_trg();

-- Backfill every current shop through the same authority used by future changes.
select qg_private.queuego_sync_shop_place(id)
from public.shop_profiles;
`;
}

if (require.main === module) {
  const [output] = process.argv.slice(2);
  const sql = renderQueueGoVerifiedShopSync();
  if (output) {
    fs.writeFileSync(path.resolve(output), sql, 'utf8');
    console.error('wrote review-only QueueGo verified shop sync SQL');
  } else {
    process.stdout.write(sql);
  }
}

module.exports = { renderQueueGoVerifiedShopSync };
