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
assert(!/create\s+extension[\s\S]{0,120}postgis/i.test(executableSql),
  'Proposal must not silently introduce a PostGIS dependency');
assert(!/security\s+definer/i.test(executableSql),
  'Local Roads proposal must not add SECURITY DEFINER code');
assert(!/grab|competitor:/i.test(sql),
  'Local Roads schema must not encode competitor geometry/provenance');
assert(sql.includes('stores no raw Rider GPS trails'),
  'Proposal must explicitly preserve aggregate-only GPS evidence boundary');

console.log('QueueGo Local Roads schema proposal contracts passed');
