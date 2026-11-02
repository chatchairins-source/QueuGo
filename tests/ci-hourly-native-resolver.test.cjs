'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {resolveHourlyNativePr} = require('../scripts/ci/resolve-hourly-native-pr.cjs');

const repository = 'chatchairins-source/QueuGo';
const nativeBranch = 'work-native-rider-nav-unified-offline-20261030';
function pr(number, branch, base, state = 'open', repo = repository, created = '2026-11-02T01:00:00Z') {
  return {
    number, state, created_at: created,
    head: {ref: branch, sha: number.toString(16).padStart(40, '0'), repo: {full_name: repo}},
    base: {ref: base, repo: {full_name: repository}}
  };
}
const root = pr(736, nativeBranch, 'work-native-pos-tax-foundation-20261029');
const p819 = pr(819, 'work-rider-native-map-readiness-20261102', nativeBranch);
const p821 = pr(821, 'fix-rider-availability-native-map-parity-20261102', p819.head.ref);
const p823 = pr(823, 'fix-rider-online-gps-status-parity-20261102', p821.head.ref);
const p824 = pr(824, 'fix-rider-three-role-parallel-ci-20261102', p823.head.ref);
const p826 = pr(826, 'fix-rider-retain-future-route-on-network-failure-20261102', p824.head.ref);

test('selects newest deep trusted Native successor rather than static #824', () => {
  const result = resolveHourlyNativePr(root, [[p819, p821], [p823, p824, p826]], repository);
  assert.equal(result.pr, 826);
  assert.equal(result.sha, p826.head.sha);
  assert.deepEqual(result.chain, [736, 819, 821, 823, 824, 826]);
});
test('new unrelated or older shallow sibling cannot replace latest Rider lineage', () => {
  const unrelated = pr(900, 'work-merchant-standalone', 'main', 'open', repository, '2026-11-03T01:00:00Z');
  const sibling = pr(901, 'work-native-unrelated-visual', nativeBranch, 'open', repository, '2026-11-01T02:00:00Z');
  assert.equal(resolveHourlyNativePr(root, [[unrelated, sibling, p819, p821, p823, p824, p826]], repository).pr, 826);
});
test('newest open Native successor beats older much deeper QA lineage', () => {
  const olderChain = [];
  let parent = nativeBranch;
  for (let i = 0; i < 12; i++) {
    const branch = 'old-visual-qa-' + i;
    olderChain.push(pr(770 + i, branch, parent, 'open', repository, '2026-11-01T00:00:00Z'));
    parent = branch;
  }
  const newest = {...p826, created_at: '2026-11-02T07:24:00Z'};
  const result = resolveHourlyNativePr(root, [[...olderChain, p819, p821, p823, p824, newest]], repository);
  assert.equal(result.pr, 826);
  assert.deepEqual(result.chain, [736, 819, 821, 823, 824, 826]);
});
test('rejects fork-supplied descendants even when deeper and newer', () => {
  const fork = pr(902, 'fork-route', p826.head.ref, 'open', 'attacker/QueuGo');
  assert.equal(resolveHourlyNativePr(root, [[p819, p821, p823, p824, p826, fork]], repository).pr, 826);
});
test('follows merged intermediate PRs to a still-open successor', () => {
  const merged = {...p824, state: 'closed'};
  assert.equal(resolveHourlyNativePr(root, [[p819, p821, p823, merged, p826]], repository).pr, 826);
});
test('fails closed when no open descendant exists instead of silently testing stale root', () => {
  assert.throws(() => resolveHourlyNativePr(root, [[{...p819, state: 'closed'}]], repository), /No trusted open Native successor/);
});
test('rejects wrong root branch or mismatched repository', () => {
  assert.throws(() => resolveHourlyNativePr({...root, head: {...root.head, ref: 'main'}}, [[p819]], repository), /Trusted Native/);
  assert.throws(() => resolveHourlyNativePr(root, [[p819]], 'different/repo'), /Trusted Native/);
});
