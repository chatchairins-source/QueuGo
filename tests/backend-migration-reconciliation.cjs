'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { reconcileMigrations } = require('../scripts/reconcile-supabase-migrations.cjs');

let count = 0;
function check(condition, message) { assert.ok(condition, message); count++; }
const match = reconcileMigrations(
  ['20261001000000_one.sql', '20261002000000_two.sql', 'README.md'],
  [{ version:'20261001000000', name:'one' }, { version:'20261002000000', name:'two' }]
);
check(match.assessment === 'HISTORY_RECORDS_MATCH_NOT_SCHEMA_CERTIFIED', 'matching records never claim schema certified');
check(match.summary.exactCount === 2, 'count exact matches');
check(match.summary.repositoryCount === 2, 'ignore non-SQL files');

const drift = reconcileMigrations(
  ['20261001000000_one.sql','20261002000000_two.sql','20261003000000_new.sql'],
  [{ version:'20261001000000',name:'one' },
   { version:'20261002999999',name:'two' },
   { version:'20261004000000',name:'old' }]
);
check(drift.assessment === 'HISTORY_REVIEW_REQUIRED', 'flag drift');
check(drift.summary.exactCount === 1, 'count exact migration');
check(drift.summary.sameNameDifferentVersionCount === 1, 'different timestamp is NOT missing SQL proof');
check(drift.summary.repositoryOnlyCount === 1, 'find repo-only names');
check(drift.summary.productionOnlyCount === 1, 'find production-only names');
check(drift.sameNameDifferentVersion[0].appliedVersions[0] === '20261002999999', 'report applied version for review');
check(drift.warning.includes('Never apply'), 'report must explicitly prohibit automatic migration replay');

const collisions = reconcileMigrations(
  ['20261002000000_two.sql','20261002000000_duplicate.sql','broken.sql'],
  [{version:'20261002000000',name:'other'}, {version:'20261003000000',name:'other'}]
);
check(collisions.summary.versionCollisionCount === 2, 'detect different names reusing version');
check(collisions.summary.repeatedRepositoryVersionsCount === 1, 'detect duplicate local versions');
check(collisions.summary.repeatedAppliedNamesCount === 1, 'detect repeated deployed names');
check(collisions.summary.invalidFilenameCount === 1, 'reject malformed migration filenames');

assert.throws(() => reconcileMigrations([], [{version:'drop table x',name:'wrong'}]), /Invalid applied/);
count++;
assert.throws(() => reconcileMigrations(null, []), /Expected arrays/);
count++;

const temp = fs.mkdtempSync(path.join(os.tmpdir(),'queuego-migrations-'));
try {
  const dir = path.join(temp,'migrations');
  fs.mkdirSync(dir);
  fs.writeFileSync(path.join(dir,'20261001000000_one.sql'),'-- test fixture\n');
  const snapshot = path.join(temp,'snapshot.json');
  fs.writeFileSync(snapshot,JSON.stringify({migrations:[{version:'20261001000000',name:'one'}]}));
  const cli = path.join(__dirname,'../scripts/reconcile-supabase-migrations.cjs');
  const run = (...args) => spawnSync(process.execPath,[cli,...args],{encoding:'utf8'});
  const good = run(dir,snapshot);
  check(good.status === 0, 'CLI exits 0 on same history');
  check(JSON.parse(good.stdout).summary.exactCount === 1,'CLI produces parseable JSON');
  fs.writeFileSync(snapshot,JSON.stringify({migrations:[{version:'20261002000000',name:'one'}]}));
  const driftRun = run(dir,snapshot);
  check(driftRun.status === 2, 'CLI uses distinct nonzero status for history review');
  check(JSON.parse(driftRun.stdout).assessment === 'HISTORY_REVIEW_REQUIRED','CLI reports review status');
  fs.writeFileSync(snapshot,'{ not json');
  check(run(dir,snapshot).status === 1, 'CLI exits 1 on invalid snapshot');
  check(run().status === 1, 'CLI fails without required arguments');
} finally { fs.rmSync(temp,{recursive:true,force:true}); }
console.log(JSON.stringify({checks:count,failures:0,scope:'Read-only Supabase migration history reconciliation'}));
