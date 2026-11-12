'use strict';
const fs = require('node:fs');
const operations = ['select','insert','update','delete'];
const roles = ['anon','authenticated'];
function auditNoPolicyTables(rows, expectedCount) {
  if (!Number.isSafeInteger(expectedCount) || expectedCount < 0)
    throw new TypeError('Expected a nonnegative integer catalog count to verify snapshot completeness');
  if (!Array.isArray(rows) || rows.length !== expectedCount)
    throw new TypeError('Snapshot count differs from catalog count: expected ' + expectedCount + ', got ' + (Array.isArray(rows) ? rows.length : 'invalid'));

  if (!Array.isArray(rows) || rows.length === 0) throw new TypeError('Expected nonempty metadata rows; empty snapshots cannot prove no-policy grants are absent');
  const seen = new Set(), exposed = [];
  for (const [i,row] of rows.entries()) {
    if (!row || typeof row.schema_name !== 'string' || !row.schema_name ||
      typeof row.table_name !== 'string' || !row.table_name ||
      row.rls_enabled !== true || row.policy_count !== 0)
      throw new TypeError('Invalid no-policy metadata row at index ' + i);
    const name = row.schema_name + '.' + row.table_name;
    if (seen.has(name)) throw new TypeError('Duplicate table ' + name);
    seen.add(name);
    for (const role of roles) for (const op of operations) {
      const key = role + '_' + op;
      if (typeof row[key] !== 'boolean') throw new TypeError('Missing boolean ' + key + ' at index ' + i);
      if (row[key]) exposed.push({table:name,role,operation:op});
    }
  }
  return {
    assessment: exposed.length ? 'NO_POLICY_GRANTS_REQUIRE_REVIEW' : 'NO_POLICY_TABLE_GRANTS_ABSENT_NOT_SECURITY_CERTIFIED',
    counts: {noPolicyTables:rows.length,grantsFound:exposed.length},
    grants:exposed,
    warning:'RLS denies row access without policies, but direct grants still need review; do not change Production automatically.'
  };
}
module.exports={auditNoPolicyTables};
if (require.main===module) {
  const file=process.argv[2];
  if (!file) { console.error('Usage: node scripts/audit-supabase-no-policy-grants.cjs metadata.json'); process.exitCode=1; }
  else try {
    const data=JSON.parse(fs.readFileSync(file,'utf8'));
    const result=auditNoPolicyTables(data.tables,data.expected_catalog_count);
    console.log(JSON.stringify(result,null,2));
    if (result.counts.grantsFound) process.exitCode=2;
  } catch(e) { console.error('No-policy audit failed: '+e.message); process.exitCode=1; }
}
