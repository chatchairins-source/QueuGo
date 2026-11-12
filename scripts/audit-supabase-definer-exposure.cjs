'use strict';
const fs = require('node:fs');

// Analyze a supplied read-only SQL snapshot, never connect to Production.
function auditDefinerExposure(rows, allowlist, historicalCount = null, expectedCatalogCount) {
  if (!Number.isSafeInteger(expectedCatalogCount) || expectedCatalogCount <= 0)
    throw new TypeError('Expected positive integer catalog count for snapshot completeness');
  if (!Array.isArray(rows) || rows.length !== expectedCatalogCount)
    throw new TypeError('Function snapshot count differs from catalog count');
  if (!Array.isArray(rows) || !allowlist || !Array.isArray(allowlist.rules))
    throw new TypeError('Expected function rows and reviewed allowlist');
  if (historicalCount !== null && (!Number.isInteger(historicalCount) || historicalCount < 0))
    throw new TypeError('Invalid historical count');
  const seen = new Set();
  for (const [i,row] of rows.entries()) {
    if (!row || typeof row.signature !== 'string' || !row.signature ||
      row.security_definer !== true || typeof row.anon_execute !== 'boolean' ||
      typeof row.authenticated_execute !== 'boolean' ||
      typeof row.search_path_pinned !== 'boolean')
      throw new TypeError('Invalid snapshot row at index ' + i);
    if (seen.has(row.signature)) throw new TypeError('Duplicate function ' + row.signature);
    seen.add(row.signature);
  }
  const names = allowlist.rules.map(x => x && x.signature);
  if (names.some(x => typeof x !== 'string' || !x) || new Set(names).size !== names.length)
    throw new TypeError('Invalid or duplicate approved signature');
  const approved = new Set(names);
  const anon = rows.filter(x => x.anon_execute);
  const observedAnon = new Set(anon.map(x => x.signature));
  const unexpectedAnonymous = anon.map(x => x.signature).filter(x => !approved.has(x));
  const approvedNotAnonymous = names.filter(x => !observedAnon.has(x));
  const anonWithoutSearchPath = anon.filter(x => !x.search_path_pinned).map(x => x.signature);
  const authenticatedExecutable = rows.filter(x => x.authenticated_execute).length;
  const authenticatedWithoutSearchPath = rows.filter(x => x.authenticated_execute && !x.search_path_pinned).map(x => x.signature);
  const historicalDrift = historicalCount !== null && historicalCount !== authenticatedExecutable;
  const requiresReview = !!(unexpectedAnonymous.length || approvedNotAnonymous.length ||
    anonWithoutSearchPath.length || authenticatedWithoutSearchPath.length || historicalDrift);
  return {
    assessment: requiresReview ? 'RPC_EXPOSURE_REVIEW_REQUIRED' : 'SNAPSHOT_MATCHES_ALLOWLIST_NOT_SECURITY_CERTIFIED',
    counts: {publicDefiner:rows.length,anonymousExecutable:anon.length,
      authenticatedExecutable,historicalCount,historicalDrift},
    unexpectedAnonymous,approvedNotAnonymous,anonWithoutSearchPath,authenticatedWithoutSearchPath,
    warning:'Do not automatically GRANT, REVOKE or modify Production from this report.'
  };
}
module.exports = { auditDefinerExposure };

if (require.main === module) {
  const [snapshotFile,allowlistFile,historicalFile] = process.argv.slice(2);
  if (!snapshotFile || !allowlistFile) {
    console.error('Usage: node scripts/audit-supabase-definer-exposure.cjs snapshot.json allowlist.json [historical-review.json]');
    process.exitCode = 1;
  } else {
    try {
      const load = filename => JSON.parse(fs.readFileSync(filename,'utf8'));
      const prior = historicalFile ? load(historicalFile).authenticated_security_definer_total : null;
      const result = auditDefinerExposure(load(snapshotFile).functions,load(allowlistFile),prior,load(snapshotFile).expected_catalog_count);
      process.stdout.write(JSON.stringify(result,null,2) + '\n');
      if (result.assessment === 'RPC_EXPOSURE_REVIEW_REQUIRED') process.exitCode = 2;
    } catch (error) {
      console.error('RPC exposure audit failed: ' + error.message);
      process.exitCode = 1;
    }
  }
}
