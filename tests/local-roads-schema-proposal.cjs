const fs=require('fs');
const assert=require('assert');

const sql=fs.readFileSync('ops/queuego-local-roads-schema-proposal.sql','utf8');
const executableSql=sql.split('\n')
  .filter(line=>!line.trim().startsWith('--'))
  .join('\n');

assert((sql.match(/\brollback\s*;/gi)||[]).length===1,
  'Proposal must contain exactly one terminal rollback and no duplicated SQL tail');
assert(sql.includes('PROPOSAL ONLY')&&sql.trim().endsWith('rollback;'),
  'Local Roads schema proposal must be non-persistent by default');
assert(sql.includes('create table if not exists public.qg_local_road_candidates')&&
  sql.includes('create table if not exists public.qg_local_roads')&&
  sql.includes('create table if not exists public.qg_local_road_feed_state'),
  'Local Roads must separate reviewed candidates, published roads and feed revision state');
assert(/status\s+in\s*\(\s*'candidate'\s*,\s*'approved'\s*,\s*'rejected'\s*\)/i.test(sql)&&
  /status\s+in\s*\(\s*'approved'\s*,\s*'rejected'\s*\)[\s\S]{0,120}reviewed_at is not null[\s\S]{0,120}reviewed_by is not null/i.test(sql),
  'Reviewed candidates must have explicit approval/rejection evidence');
assert(sql.includes('candidate_version bigint not null default 1')&&
  sql.includes('unique (road_key, candidate_version)')&&
  sql.includes('published_revision bigint')&&
  sql.includes('published_at timestamptz'),
  'Candidate road history must be versioned and retain publication evidence');
assert(
  (sql.match(/and published_revision is null/g)||[]).length >= 3&&
  (sql.match(/and published_at is null/g)||[]).length >= 3,
  'Authenticated candidate insert/update/delete must not mutate already-published candidate versions');

assert(sql.includes("source_reference like 'osm:%'")&&
  sql.includes("source_reference like 'public:%'")&&
  sql.includes("source_reference like 'queuego-trace:%'")&&
  sql.includes("source_reference like 'merchant:%'")&&
  sql.includes("source_reference like 'field-survey:%'"),
  'Published/candidate roads must bind source references to the provenance allowlist');
assert(sql.includes("jsonb_array_length(points) between 2 and 10000"),
  'Road geometry must be bounded before later publication validation');
assert(sql.includes('enable row level security')&&
  sql.includes('qg_local_road_candidates_admin_select')&&
  sql.includes('qg_local_roads_verified_read')&&
  sql.includes('qg_local_road_feed_state_read'),
  'All exposed Local Roads tables must enable explicit RLS policies');
assert(sql.includes('revoke all on table public.qg_local_roads from anon, authenticated')&&
  sql.includes('grant select on table public.qg_local_roads to authenticated')&&
  !sql.includes('grant insert, update, delete\n  on table public.qg_local_roads to authenticated'),
  'Published Local Roads must be explicit read-only Data API state for authenticated clients');
assert(
  (sql.match(/access_type\s*<>\s*'private_restricted'/g)||[]).length >= 2,
  'Private/restricted Local Roads geometry must stay out of both authenticated table reads and the Rider feed view');
assert(sql.includes('with (security_invoker = true)')&&
  sql.includes('grant select on table public.qg_local_road_feed to authenticated'),
  'Verified Local Roads feed must use a security-invoker read-only view');
assert(sql.includes('create table if not exists public.qg_local_road_junction_candidates')&&
  sql.includes('create table if not exists public.qg_local_road_junctions')&&
  sql.includes('unique (junction_key, candidate_version)')&&
  sql.includes('jsonb_array_length(members) between 2 and 8'),
  'Local Roads schema v2 proposal must version reviewed junction candidates and bound published junction members');

assert(sql.includes('alter table public.qg_local_road_junction_candidates enable row level security')&&
  sql.includes('alter table public.qg_local_road_junctions enable row level security')&&
  sql.includes('qg_local_road_junction_candidates_admin_select')&&
  sql.includes('qg_local_road_junctions_verified_read'),
  'Local Roads junction candidate/published tables must enable explicit RLS');

assert(sql.includes('revoke all on table public.qg_local_road_junction_candidates from anon, authenticated')&&
  sql.includes('revoke all on table public.qg_local_road_junctions from anon, authenticated')&&
  sql.includes('grant select, insert, update, delete\n  on table public.qg_local_road_junction_candidates to authenticated')&&
  sql.includes('grant select on table public.qg_local_road_junctions to authenticated'),
  'Local Roads junction Data API exposure must use explicit least-privilege grants');

assert(
  (sql.match(/qg_local_road_junction_candidates[\s\S]{0,900}published_revision is null/g)||[]).length >= 3&&
  (sql.match(/qg_local_road_junction_candidates[\s\S]{0,900}published_at is null/g)||[]).length >= 3,
  'Published junction candidate versions must become immutable to authenticated admin mutation');

assert(sql.includes('create or replace view public.qg_local_road_junction_feed')&&
  sql.includes('with (security_invoker = true)')&&
  sql.includes('grant select on table public.qg_local_road_junction_feed to authenticated'),
  'Published junction feed must be a security-invoker authenticated read-only view');

assert(sql.includes("roads.road_key = member ->> 'road_key'")&&
  sql.includes("roads.access_type <> 'private_restricted'")&&
  sql.includes('where not exists ('),
  'Published junction reads must fail closed when any member road is missing, retired or private');

assert(sql.includes('validate junction member shape/reference/private-road/span constraints')&&
  sql.includes('increment ONE feed revision covering both roads and junctions')&&
  sql.includes('currently <=25m')&&
  sql.includes('No client may auto-snap nearby geometry'),
  'Server-only publication boundary must validate explicit junction evidence and share one monotonic roads+junctions revision');

assert(!/create\s+extension[\s\S]{0,120}postgis/i.test(executableSql),
  'Proposal must not silently introduce a PostGIS dependency');
assert(!/security\s+definer/i.test(executableSql),
  'Local Roads proposal must not add SECURITY DEFINER code');
assert(!/grab|competitor:/i.test(sql),
  'Local Roads schema must not encode competitor geometry/provenance');
assert(sql.includes('stores no raw Rider GPS trails'),
  'Proposal must explicitly preserve aggregate-only GPS evidence boundary');

console.log('QueueGo Local Roads schema proposal contracts passed');
