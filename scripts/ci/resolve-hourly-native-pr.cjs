#!/usr/bin/env node
'use strict';

// GitHub's recent pull list may contain closed intermediate PRs. Follow the
// actual base-branch lineage from Native #736 rather than a hard-coded PR list
// or the newest unrelated Android branch. Only same-repository heads qualify.
const fs = require('node:fs');

function resolveHourlyNativePr(root, pages, repository) {
  if (!repository || !root || root.number !== 736) {
    throw new Error('Native #736 root and repository are required');
  }
  const flattened = (Array.isArray(pages) ? pages.flat(2) : []).filter(Boolean);
  const unique = new Map();
  for (const pr of [root, ...flattened]) {
    if (!Number.isInteger(pr.number)) continue;
    if (pr.head?.repo?.full_name !== repository ||
        pr.base?.repo?.full_name !== repository ||
        !/^[a-f0-9]{40}$/.test(pr.head?.sha || '') ||
        !pr.head?.ref || !pr.base?.ref) continue;
    unique.set(pr.number, pr);
  }
  const native = unique.get(736);
  if (!native || native.head.ref !== 'work-native-rider-nav-unified-offline-20261030') {
    throw new Error('Trusted Native #736 lineage could not be verified');
  }
  const prs = [...unique.values()];
  let best = null;
  const visited = new Set();

  function walk(parent, path) {
    if (visited.has(parent.number)) return;
    visited.add(parent.number);
    const nextPath = [...path, parent.number];
    if (parent.state === 'open') {
      const candidate = {pr: parent, chain: nextPath};
      // An older, longer QA branch must not eclipse a newly opened Native
      // successor (e.g. #816's visual lineage vs #826's Rider fix).
      // Depth is only a tie-breaker for equal creation timestamps.
      const created = String(candidate.pr.created_at || '');
      const previousCreated = String(best?.pr.created_at || '');
      if (!best ||
          created > previousCreated ||
          (created === previousCreated && candidate.chain.length > best.chain.length) ||
          (created === previousCreated && candidate.chain.length === best.chain.length &&
           candidate.pr.number > best.pr.number)) {
        best = candidate;
      }
    }
    for (const child of prs) {
      if (child.number !== parent.number && child.base.ref === parent.head.ref) {
        walk(child, nextPath);
      }
    }
    visited.delete(parent.number);
  }
  walk(native, []);
  if (!best || best.chain.length < 2) {
    throw new Error('No trusted open Native successor found; refuse stale hourly CI');
  }
  return {
    pr: best.pr.number,
    sha: best.pr.head.sha,
    chain: best.chain
  };
}

if (require.main === module) {
  try {
    const [rootFile, recentFile, repository] = process.argv.slice(2);
    if (!rootFile || !recentFile || !repository) throw new Error('Usage: resolver ROOT RECENT OWNER/REPO');
    const root = JSON.parse(fs.readFileSync(rootFile, 'utf8'));
    const recent = JSON.parse(fs.readFileSync(recentFile, 'utf8'));
    const selected = resolveHourlyNativePr(root, recent, repository);
    process.stdout.write('sha=' + selected.sha + '\n');
    process.stdout.write('pr=' + selected.pr + '\n');
    process.stdout.write('chain=' + selected.chain.join('>') + '\n');
  } catch (error) {
    process.stderr.write('::error::' + error.message + '\n');
    process.exitCode = 1;
  }
}
module.exports = {resolveHourlyNativePr};
