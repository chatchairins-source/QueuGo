const fs=require('fs');
const assert=require('assert');

const sql=fs.readFileSync('ops/queuego-local-roads-schema-proposal.sql','utf8');

assert(sql.includes('PROPOSAL ONLY')&&sql.trim().endsWith('rollback;'),
  'Local Roads schema proposal must be non-persistent by default');
assert(sql.includes('create table if not exists public.qg_local_road_candidates')&&
  sql.includes('create table if not exists public.qg_local_roads')&&
  sql.includes('create table if not exists public.qg_local_road_feed_state'),
  'Local Roads must separate reviewed candidates, published roads and feed revision state');
assert(sql.includes("status in ('candidate','approved','rejected')")&&
  sql.includes("status in ('approved','rejected') and reviewed_at is not null and reviewed_by is not null"),
  'Reviewed candidates must have explicit approval/rejection evidence');
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
assert(sql.includes('with (security_invoker = true)')&&
  sql.includes('grant select on table public.qg_local_road_feed to authenticated'),
  'Verified Local Roads feed must use a security-invoker read-only view');
assert(!/postgis/i.test(sql.replace(/Deliberately no PostGIS dependency/g,'')),
  'Proposal must not silently introduce a PostGIS dependency');
assert(!/security\s+definer/i.test(sql),
  'Local Roads proposal must not add SECURITY DEFINER code');
assert(!/grab|competitor:/i.test(sql),
  'Local Roads schema must not encode competitor geometry/provenance');
assert(sql.includes('stores no raw Rider GPS trails'),
  'Proposal must explicitly preserve aggregate-only GPS evidence boundary');

console.log('QueueGo Local Roads schema proposal contracts passed');
