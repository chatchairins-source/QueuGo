'use strict';
// Transaction/ACL tests in disposable PGlite. Spatial functions are test doubles;
// actual pinned geometry validity/containment was separately checked with PostGIS.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {PGlite}=require('@electric-sql/pglite');
const root=path.resolve(__dirname,'..');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261025071356_queuego_detailed_thailand_service_boundary.sql'),'utf8');
const foundation=fs.readFileSync(path.join(root,'supabase/migrations/20261025070500_queuego_nationwide_order_service_area_v2.sql'),'utf8');
const schema=foundation.slice(foundation.indexOf('create table if not exists public.queuego_order_service_area_rules'),foundation.indexOf('insert into public.queuego_order_service_area_rules'));
const geometry=JSON.parse(sql.match(/v_geojson text := '([^']+)';/)[1]);
assert.equal(geometry.type,'MultiPolygon');assert.equal(geometry.coordinates.length,70);
const {PIN}=require('../scripts/audit-thailand-boundary.cjs');
assert(sql.includes(PIN.sha256));
assert(!/\b(drop|truncate)\b/i.test(sql));
const docs=fs.readFileSync(path.join(root,'docs/map-data.html'),'utf8');
assert(docs.includes(PIN.sha256)&&docs.includes('ODbL')&&docs.includes(PIN.url));

(async()=>{
 const db=new PGlite();let checks=5;
 const check=(condition,message)=>{assert.ok(condition,message);checks++;};
 try{
  await db.exec(`create role anon;create role authenticated;create schema extensions;
   create domain extensions.geometry as text;create domain extensions.geography as text;
   create function extensions.digest(text,text) returns bytea language sql as $$select sha256(convert_to($1,'UTF8'))$$;
   create function extensions.st_geomfromgeojson(text) returns extensions.geometry language sql as $$select $1::extensions.geometry$$;
   create function extensions.st_setsrid(extensions.geometry,integer) returns extensions.geometry language sql as $$select $1$$;
   create function extensions.st_isvalid(extensions.geometry) returns boolean language sql as $$select true$$;
   create function extensions.st_isempty(extensions.geometry) returns boolean language sql as $$select false$$;
   create function extensions.geometrytype(extensions.geometry) returns text language sql as $$select upper(($1::jsonb)->>'type')$$;
   create function extensions.st_numgeometries(extensions.geometry) returns integer language sql as $$select jsonb_array_length(($1::jsonb)->'coordinates')$$;
   create function extensions.st_npoints(extensions.geometry) returns integer language sql as $$select sum(jsonb_array_length(r))::int from jsonb_array_elements(($1::jsonb)->'coordinates') p cross join lateral jsonb_array_elements(p) r$$;
   create function extensions.st_area(extensions.geography) returns float8 language sql as $$select 514935290459.0::float8$$;
   create function extensions.st_makepoint(numeric,numeric) returns extensions.geometry language sql as $$select jsonb_build_array($1,$2)::text::extensions.geometry$$;
   create function extensions.st_covers(extensions.geometry,extensions.geometry) returns boolean language sql as $$select case when ($2::jsonb->>0)::numeric in(95,100.5,105.8,99.8,104.9282,101.6869) then false else true end$$;
   create table public.queuego_places(place_id uuid primary key);
   create table public.queuego_place_sources(source_key text primary key,source_name text,license_id text,license_url text,attribution_requirement text,update_policy text,allowed_storage boolean,allowed_redistribution boolean,metadata jsonb,updated_at timestamptz default now());
   create table public.audit_logs(id uuid default gen_random_uuid(),action text,entity_type text,entity_id uuid,description text,metadata jsonb);`);
  // PGlite's text stand-in for PostGIS has no typmod/GiST operator class.
  await db.exec(schema.replace('extensions.geography(multipolygon,4326)','extensions.geography').replace(/create index if not exists queuego_order_service_area_custom_boundary_gix[\s\S]*?where custom_boundary is not null;/,''));
  const seed=`insert into public.queuego_places values('00000000-0000-4000-8000-000000000001');
   insert into public.queuego_order_service_area_rules(name,country_code,verticals,mode,admin_place_id,specificity,max_direct_delivery_km,source,source_ref,source_license,effective_from)
   values('Thailand Nationwide Launch','TH',array['delivery','grocery','laundry'],'allow','00000000-0000-4000-8000-000000000001',1,15,'natural_earth','previous-source','Public-Domain',now()-interval '1 day');`;
  await db.exec(seed);
  await db.exec('begin;'+sql+'commit;');
  let rows=(await db.query('select * from public.queuego_order_service_area_rules order by revision')).rows;
  check(rows.length===2,'keep predecessor and insert one successor');
  check(rows[0].active===false&&rows[0].source==='natural_earth','preserve original rule/source');
  check(rows[1].active===true&&rows[1].revision===2,'new revision becomes active');
  check(rows[1].max_direct_delivery_km==='15','preserve original distance cap');
  check(rows[1].specificity===rows[0].specificity,'preserve deny/specificity ordering');
  check(JSON.stringify(rows[1].verticals)===JSON.stringify(rows[0].verticals),'preserve enabled services');
  check(rows[1].admin_place_id===null&&rows[1].custom_boundary,'do not overwrite public-domain master place');
  check(rows[1].metadata.supersedes_rule_id===rows[0].rule_id,'audit predecessor identity');
  check((await db.query('select count(*)::int n from public.audit_logs')).rows[0].n===1,'one immutable release receipt');
  await db.exec(sql);
  check((await db.query('select count(*)::int n from public.audit_logs')).rows[0].n===1,'rerun does not duplicate audit');
  await db.exec('update public.queuego_order_service_area_rules set active=false where revision=2;');
  await db.exec(sql);
  check((await db.query('select active from public.queuego_order_service_area_rules where revision=2')).rows[0].active===false,'rerun does not resurrect intentionally deactivated rule');
  await db.exec("update public.queuego_order_service_area_rules set source_ref='different-source' where revision=2;");
  await assert.rejects(()=>db.exec(sql),/BOUNDARY_REVISION_CONFLICT/);checks++;
  await db.exec("delete from public.queuego_order_service_area_rules where revision=2;update public.queuego_order_service_area_rules set active=true,effective_to=null where revision=1;delete from public.audit_logs;");
  await db.exec('create or replace function extensions.st_isvalid(extensions.geometry) returns boolean language sql as $$select false$$;');
  await assert.rejects(()=>db.exec('begin;'+sql+'commit;'),/INVALID_THAILAND_BOUNDARY/);checks++;
  await db.exec('rollback;');
  check((await db.query('select revision,active from public.queuego_order_service_area_rules')).rows[0].active===true,'invalid candidate preserves original live rule');
  check((await db.query('select count(*)::int n from public.audit_logs')).rows[0].n===0,'failed release has no success audit');
  check((await db.query("select relrowsecurity from pg_class where oid='public.queuego_order_service_area_rules'::regclass")).rows[0].relrowsecurity===true,'rules retain RLS');
  check((await db.query("select has_table_privilege('anon','public.queuego_order_service_area_rules','select') allowed")).rows[0].allowed===false,'no anonymous backing-table read');
  check((await db.query("select has_table_privilege('authenticated','public.queuego_order_service_area_rules','update') allowed")).rows[0].allowed===false,'no authenticated rule mutation');
  console.log(JSON.stringify({checks,failures:0,scope:'isolated rule revision/CAS/atomicity/audit/RLS/idempotency; spatial test doubles, actual spatial checks require recorded PostGIS evidence'}));
 }finally{await db.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
