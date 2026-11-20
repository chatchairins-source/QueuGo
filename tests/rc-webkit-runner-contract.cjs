'use strict';
// CI infrastructure regression: keep Playwright WebKit on a supported runner,
// without weakening the existing end-to-end browser coverage.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const source = fs.readFileSync('.github/workflows/rc-tests.yml', 'utf8');
const job = source.split(/\n  test:\n/)[1];
assert.ok(job, 'RC test job must exist');
assert.match(job, /^    runs-on: ubuntu-24\.04\s*$/m,
  'RC WebKit must run on the pinned supported Ubuntu 24.04 image');
assert.doesNotMatch(job, /^    runs-on: ubuntu-latest\s*$/m,
  'do not silently return WebKit to unsupported ubuntu-latest');
for (const required of [
  'run: npm test',
  'npx playwright-core install --with-deps webkit',
  'node tests/admin-boot-browser.cjs',
  'npm run test:merchant:browser',
  'node tests/map-lab-browser.cjs',
  'node tests/map-view-search-browser.cjs',
  'npm run test:grocery:browser',
  'if: always()',
  'actions/upload-artifact@'
]) {
  assert.ok(job.includes(required), 'RC browser/evidence gate must remain enabled: ' + required);
}
assert.match(job, /for attempt in 1 2; do/, 'bounded installer retries must remain enabled');
assert.match(job, /timeout 600s npx playwright-core install --with-deps webkit/, 'retry must call original installer');
assert.match(job, /echo "::error::WebKit installer failed twice; browser gates remain blocking"\s*\n\s*exit 1/, 'terminal WebKit failure must fail RC');
assert.doesNotMatch(job, /continue-on-error:\s*true/, 'browser tests must never be bypassed');
console.log(JSON.stringify({checks: 15, failures: 0,
  scope: 'RC supported WebKit runner, bounded installer retries and fail-closed browser gates'}));
