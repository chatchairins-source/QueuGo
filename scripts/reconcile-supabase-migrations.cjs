'use strict';

// Offline, read-only reconciliation of Supabase migration history.
// Name matches do NOT prove the SQL text or deployed schema matches.
const fs = require('node:fs');
const path = require('node:path');

function reconcileMigrations(fileNames, appliedRecords) {
  if (!Array.isArray(fileNames) || !Array.isArray(appliedRecords)) {
    throw new TypeError('Expected arrays of repository filenames and applied migration records');
  }
  const invalidFiles = [];
  const repository = fileNames.filter(f => typeof f === 'string' && f.endsWith('.sql')).map(file => {
    const match = /^(\d{14})_(.+)\.sql$/.exec(file);
    if (!match) { invalidFiles.push(file); return null; }
    return { file, version: match[1], name: match[2] };
  }).filter(Boolean);

  const applied = appliedRecords.map((record, i) => {
    if (!record || typeof record.version !== 'string' ||
        !/^\d{14}$/.test(record.version) ||
        typeof record.name !== 'string' || !record.name.trim()) {
      throw new TypeError('Invalid applied migration at index ' + i);
    }
    return { version: record.version, name: record.name };
  });
  const byName = new Map(), byVersion = new Map();
  for (const record of applied) {
    if (!byName.has(record.name)) byName.set(record.name, []);
    if (!byVersion.has(record.version)) byVersion.set(record.version, []);
    byName.get(record.name).push(record.version);
    byVersion.get(record.version).push(record.name);
  }
  const repositoryNames = new Set(repository.map(row => row.name));
  const repositoryVersions = new Map();
  for (const row of repository) {
    if (!repositoryVersions.has(row.version)) repositoryVersions.set(row.version, []);
    repositoryVersions.get(row.version).push(row.file);
  }
  const exact = [], sameNameDifferentVersion = [], repositoryOnly = [], versionCollisions = [];
  for (const row of repository) {
    const versions = byName.get(row.name) || [];
    if (versions.includes(row.version)) exact.push(row);
    else if (versions.length) sameNameDifferentVersion.push({ ...row, appliedVersions: versions });
    else repositoryOnly.push(row);
    const appliedNames = byVersion.get(row.version) || [];
    if (appliedNames.length && !appliedNames.includes(row.name)) {
      versionCollisions.push({ ...row, appliedNames });
    }
  }
  const productionOnly = applied.filter(row => !repositoryNames.has(row.name));
  const repeatedAppliedNames = [...byName.entries()]
    .filter(([,versions]) => versions.length > 1)
    .map(([name,versions]) => ({ name, versions }));
  const repeatedRepositoryVersions = [...repositoryVersions.entries()]
    .filter(([,files]) => files.length > 1)
    .map(([version,files]) => ({ version, files }));
  const summary = {
    repositoryCount: repository.length,
    appliedCount: applied.length,
    exactCount: exact.length,
    sameNameDifferentVersionCount: sameNameDifferentVersion.length,
    repositoryOnlyCount: repositoryOnly.length,
    productionOnlyCount: productionOnly.length,
    repeatedAppliedNamesCount: repeatedAppliedNames.length,
    repeatedRepositoryVersionsCount: repeatedRepositoryVersions.length,
    versionCollisionCount: versionCollisions.length,
    invalidFilenameCount: invalidFiles.length
  };
  const reviewRequired = Object.entries(summary)
    .some(([key,count]) => !['repositoryCount','appliedCount','exactCount'].includes(key) && count > 0);
  return {
    assessment: reviewRequired ? 'HISTORY_REVIEW_REQUIRED' : 'HISTORY_RECORDS_MATCH_NOT_SCHEMA_CERTIFIED',
    summary, exact, sameNameDifferentVersion, repositoryOnly, productionOnly,
    repeatedAppliedNames, repeatedRepositoryVersions, versionCollisions, invalidFiles,
    warning: 'Never apply, delete, mark, or roll back migrations using this report. Verify SQL content and deployed schema independently.'
  };
}

module.exports = { reconcileMigrations };

if (require.main === module) {
  const [migrationDirectory, snapshotPath] = process.argv.slice(2);
  if (!migrationDirectory || !snapshotPath) {
    console.error('Usage: node scripts/reconcile-supabase-migrations.cjs <migration-dir> <production-migrations.json>');
    process.exitCode = 1;
  } else {
    try {
      const files = fs.readdirSync(path.resolve(migrationDirectory));
      const snapshot = JSON.parse(fs.readFileSync(path.resolve(snapshotPath), 'utf8'));
      const applied = snapshot.migrations || snapshot.result?.migrations;
      const report = reconcileMigrations(files, applied);
      process.stdout.write(JSON.stringify(report, null, 2) + '\n');
      if (report.assessment === 'HISTORY_REVIEW_REQUIRED') process.exitCode = 2;
    } catch (error) {
      console.error('Could not reconcile migration history: ' + error.message);
      process.exitCode = 1;
    }
  }
}
